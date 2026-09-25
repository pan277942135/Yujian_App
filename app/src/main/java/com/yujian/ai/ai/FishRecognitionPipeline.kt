package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
import com.yujian.ai.model.RecognitionPrediction
import kotlinx.coroutines.CancellationException

enum class RecognitionPhase {
    CAPTURED,
    DETECTING,
    OUTLINE,
    CLASSIFYING,
    RESULT,
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
) {
    val ready: Boolean get() = assessment.isClassifierEligible && prediction != null
    val totalLatencyMs: Long get() = detectorRun.latencyMs + (prediction?.latencyMs ?: 0L)
}

class FishRecognitionPipeline(context: Context) : AutoCloseable {
    private val detector = FishDetectorEngine(context)
    private val classifier = FishRecognitionEngine(context)

    suspend fun recognize(bitmap: Bitmap): ProductionRecognitionResult =
        recognize(bitmap) {}

    suspend fun recognize(
        bitmap: Bitmap,
        onProgress: (RecognitionProgress) -> Unit,
    ): ProductionRecognitionResult {
        val stateMachine = RecognitionStateMachine()
        fun advance(event: RecognitionEvent, assessment: FishInputAssessment? = null) {
            val state = stateMachine.dispatch(event)
            onProgress(RecognitionProgress(state.phase, assessment))
        }

        advance(RecognitionEvent.CaptureReady)
        advance(RecognitionEvent.DetectionStarted)

        val detectorRun = detector.detect(bitmap)
        val assessment = FishDetectionQualityGate.assess(detectorRun.detections)

        if (!assessment.isClassifierEligible) {
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
            onProgress(RecognitionProgress(RecognitionPhase.RESULT, assessment))
            return blocked
        }

        advance(RecognitionEvent.DetectionFinished(DetectionRoute.OUTLINE), assessment)
        val cropBox = requireNotNull(assessment.cropBox)
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
            stateMachine.dispatch(RecognitionEvent.Failed(RecognitionFailureCode.INVALID_CROP))
            onProgress(RecognitionProgress(RecognitionPhase.RESULT, assessment))
            return ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = null,
                failureCode = RecognitionFailureCode.INVALID_CROP,
            )
        }
        val primary = requireNotNull(assessment.primary)
        val box = primary.box.normalized()
        val traceContext = InferenceTrace.PipelineContext(
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            detectorModelVersion = detectorRun.modelVersion,
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
        return try {
            val prediction = classifier.recognize(crop, traceContext)
            val route = when {
                prediction.top1.confidence < 0.45f -> ClassificationRoute.UNKNOWN
                prediction.candidates.getOrNull(1)?.let {
                    prediction.top1.confidence - it.confidence < 0.12f
                } == true -> ClassificationRoute.CONFIRM
                else -> ClassificationRoute.SUCCESS
            }
            advance(RecognitionEvent.ClassificationFinished(route), assessment)
            val ready = ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = prediction,
                cropPixels = pixels,
            )
            onProgress(RecognitionProgress(RecognitionPhase.RESULT, assessment))
            ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            stateMachine.dispatch(RecognitionEvent.Failed(RecognitionFailureCode.CLASSIFIER_FAILED))
            onProgress(RecognitionProgress(RecognitionPhase.RESULT, assessment))
            ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = pixels,
                failureCode = RecognitionFailureCode.CLASSIFIER_FAILED,
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
