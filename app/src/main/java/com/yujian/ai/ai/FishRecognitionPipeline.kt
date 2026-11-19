package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.yujian.ai.model.RecognitionPrediction
import kotlinx.coroutines.CancellationException

enum class RecognitionPhase {
    CAPTURED,
    DETECTING,
    OUTLINE,
    CLASSIFYING,
    RESULT,
    FAILURE,
}

data class RecognitionProgress(
    val phase: RecognitionPhase,
    val assessment: FishInputAssessment? = null,
)

/** Detector-first production pipeline shared by the Android UI. */
data class ProductionRecognitionResult(
    val status: FishInputStatus,
    val detectorRun: FishDetectorEngine.DetectorRun,
    val assessment: FishInputAssessment,
    val prediction: RecognitionPrediction?,
    val cropPixels: IntArray?,
    val failureCode: RecognitionFailureCode? = null,
    val failureCause: Throwable? = null,
) {
    val ready: Boolean get() = assessment.isClassifierEligible && prediction != null
    val totalLatencyMs: Long get() = detectorRun.latencyMs + (prediction?.latencyMs ?: 0L)
}

class FishRecognitionPipeline(context: Context) : AutoCloseable {
    private val detector = FishDetectorEngine(context)
    private val classifier = FishRecognitionEngine(context)

    suspend fun recognize(bitmap: Bitmap): ProductionRecognitionResult =
        recognize(bitmap, onProgress = {})

    suspend fun recognize(
        bitmap: Bitmap,
        onProgress: (RecognitionProgress) -> Unit,
    ): ProductionRecognitionResult = recognize(bitmap, onProgress, modelIdentity = null)

    suspend fun recognize(
        bitmap: Bitmap,
        onProgress: (RecognitionProgress) -> Unit,
        modelIdentity: RecognitionModelIdentity?,
        onDiagnostic: (String, String, String, Map<String, Any?>, Throwable?) -> Unit = { _, _, _, _, _ -> },
    ): ProductionRecognitionResult {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw RecognitionStageFailureException(
                RecognitionFailureCode.INVALID_PHOTO_GEOMETRY,
                IllegalArgumentException("Recognition source bitmap must have positive dimensions"),
            )
        }
        val stateMachine = RecognitionStateMachine()
        fun advance(event: RecognitionEvent, assessment: FishInputAssessment? = null) {
            val state = stateMachine.dispatch(event)
            onProgress(RecognitionProgress(state.phase, assessment))
        }

        advance(RecognitionEvent.CaptureReady)
        advance(RecognitionEvent.DetectionStarted)
        val detectorStart = android.os.SystemClock.uptimeMillis()
        onDiagnostic("DETECTOR_START", "DETECTOR", "STARTED", mapOf("input_shape" to "[1,3,416,416]", "input_dtype" to "float32"), null)

        val detectorRun = try {
            RecognitionModelWatchdog.execute(
                modelName = "DETECTOR",
                identity = modelIdentity,
            ) { detector.detect(bitmap) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RecognitionStageFailureException) {
            onDiagnostic("DETECTOR_FAIL", "DETECTOR", "FAIL", mapOf("failure_code" to failure.failureCode.name), failure)
            throw failure
        } catch (error: Throwable) {
            onDiagnostic("DETECTOR_FAIL", "DETECTOR", "FAIL", emptyMap(), error)
            throw RecognitionStageFailureException(RecognitionFailureCode.MODEL_INFERENCE_FAILED, error)
        }
        onDiagnostic("DETECTOR_END", "DETECTOR", "PASS", mapOf("duration_ms" to (android.os.SystemClock.uptimeMillis() - detectorStart),
            "detections" to detectorRun.detections.size, "selected_attempt" to detectorRun.selectedAttempt,
            "actual_model_id" to detectorRun.modelVersion, "actual_model_sha256" to detectorRun.onnxSha256,
            "attempts" to detectorRun.attemptTrace.map { it.toString() }), null)
        val assessment = FishDetectionQualityGate.assess(detectorRun.detections)
        onDiagnostic("QUALITY_GATE_END", "QUALITY_GATE", assessment.status.name,
            mapOf("quality" to assessment.qualityLevel.name, "reason" to assessment.qualityReason,
                "bbox_area_ratio" to assessment.bboxAreaRatio, "bbox" to assessment.primary?.box.toString()), null)
        InferenceTrace.detectorOutcome(detectorRun, assessment.primary?.box)

        if (!assessment.isClassifierEligible) {
            if (assessment.status == FishInputStatus.NO_FISH) {
                Log.i("FishRecognitionPipeline", "RECOGNITION_TERMINAL failure_code=${RecognitionFailureCode.NO_FISH_DETECTED}")
            }
            val route = if (assessment.status == FishInputStatus.FISH_TOO_SMALL) {
                DetectionRoute.TOO_FAR
            } else {
                DetectionRoute.NO_FISH
            }
            advance(RecognitionEvent.DetectionFinished(route), assessment)
            val blocked = ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = null,
            )
            return blocked
        }

        advance(RecognitionEvent.DetectionFinished(DetectionRoute.OUTLINE), assessment)
        val cropBox = requireNotNull(assessment.cropBox)
        val cropStart = android.os.SystemClock.uptimeMillis()
        onDiagnostic("CROP_START", "CROP", "STARTED", mapOf("bbox" to cropBox.toString(), "expand_ratio" to FishDetectionQualityGate.CROP_EXPAND_RATIO), null)
        val pixels: IntArray
        val crop: Bitmap
        try {
            pixels = FishDetectionQualityGate.cropBoxPixels(cropBox, bitmap.width, bitmap.height)
            crop = Bitmap.createBitmap(
                bitmap,
                pixels[0],
                pixels[1],
                pixels[2] - pixels[0],
                pixels[3] - pixels[1],
            )
        } catch (error: IllegalArgumentException) {
            onDiagnostic("CROP_FAIL", "CROP", "FAIL", emptyMap(), error)
            stateMachine.dispatch(RecognitionEvent.Failed(RecognitionFailureCode.INVALID_CROP))
            return ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = null,
                failureCode = RecognitionFailureCode.INVALID_CROP,
            )
        }
        onDiagnostic("CROP_SUCCESS", "CROP", "PASS", mapOf("pixels" to pixels.toList(), "width" to crop.width,
            "height" to crop.height, "duration_ms" to (android.os.SystemClock.uptimeMillis() - cropStart)), null)
        val primary = requireNotNull(assessment.primary)
        val box = primary.box.normalized()
        val traceContext = InferenceTrace.PipelineContext(
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            detectorModelVersion = detectorRun.modelVersion,
            detectorOnnxSha256 = detectorRun.onnxSha256,
            detectorRetryPolicyVersion = detectorRun.retryPolicyVersion,
            detectorAttempts = detectorRun.attemptTrace,
            detectorSelectedAttempt = detectorRun.selectedAttempt,
            detectorSelectionReason = detectorRun.selectionReason,
            detectorConfidence = primary.confidence,
            detectorBox = floatArrayOf(box.x1, box.y1, box.x2, box.y2),
            cropExpandRatio = FishDetectionQualityGate.CROP_EXPAND_RATIO,
            cropPixels = pixels.copyOf(),
            cropWidth = crop.width,
            cropHeight = crop.height,
            qualityLevel = assessment.qualityLevel.name,
            qualityReason = assessment.qualityReason,
            bboxAreaRatio = requireNotNull(assessment.bboxAreaRatio),
        )

        advance(RecognitionEvent.ClassificationStarted, assessment)
        val classifierStart = android.os.SystemClock.uptimeMillis()
        onDiagnostic("CLASSIFIER_START", "CLASSIFIER", "STARTED", mapOf("input_shape" to "[1,3,224,224]", "input_dtype" to "float32"), null)
        return try {
            val prediction = try {
                RecognitionModelWatchdog.execute(
                    modelName = "CLASSIFIER",
                    identity = modelIdentity,
                ) { classifier.recognize(crop, traceContext) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RecognitionStageFailureException) {
                throw failure
            } catch (error: Throwable) {
                throw RecognitionStageFailureException(RecognitionFailureCode.MODEL_INFERENCE_FAILED, error)
            }
            val route = when {
                prediction.top1.confidence < 0.45f -> ClassificationRoute.UNKNOWN
                prediction.candidates.getOrNull(1)?.let {
                    prediction.top1.confidence - it.confidence < 0.12f
                } == true -> ClassificationRoute.CONFIRM
                else -> ClassificationRoute.SUCCESS
            }
            advance(RecognitionEvent.ClassificationFinished(route), assessment)
            onDiagnostic("CLASSIFIER_END", "CLASSIFIER", "PASS", mapOf("duration_ms" to (android.os.SystemClock.uptimeMillis() - classifierStart),
                "top1" to prediction.top1.toString(), "top3" to prediction.candidates.take(3).map { it.toString() },
                "route" to route.name), null)
            val ready = ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = prediction,
                cropPixels = pixels,
            )
            ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RecognitionStageFailureException) {
            onDiagnostic("CLASSIFIER_FAIL", if (failure.failureCode == RecognitionFailureCode.MODEL_LOAD_FAILED) "CLASSIFIER_MODEL_LOAD" else "CLASSIFIER",
                "FAIL", mapOf("failure_code" to failure.failureCode.name), failure)
            stateMachine.dispatch(RecognitionEvent.Failed(failure.failureCode))
            ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = pixels,
                failureCode = failure.failureCode,
                failureCause = failure,
            )
        } catch (error: Exception) {
            onDiagnostic("CLASSIFIER_FAIL", "CLASSIFIER", "FAIL", mapOf("failure_code" to RecognitionFailureCode.MODEL_INFERENCE_FAILED.name), error)
            stateMachine.dispatch(RecognitionEvent.Failed(RecognitionFailureCode.MODEL_INFERENCE_FAILED))
            ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = pixels,
                failureCode = RecognitionFailureCode.MODEL_INFERENCE_FAILED,
                failureCause = error,
            )
        } finally {
            if (crop !== bitmap && !crop.isRecycled) crop.recycle()
        }
    }

    override fun close() {
        detector.close()
        classifier.close()
    }
}
