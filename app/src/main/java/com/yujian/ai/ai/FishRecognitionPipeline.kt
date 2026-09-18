package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
import com.yujian.ai.model.RecognitionPrediction

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
        onProgress(RecognitionProgress(RecognitionPhase.CAPTURED))
        onProgress(RecognitionProgress(RecognitionPhase.DETECTING))

        val detectorRun = detector.detect(bitmap)
        val assessment = FishDetectionQualityGate.assess(detectorRun.detections)
        onProgress(RecognitionProgress(RecognitionPhase.OUTLINE, assessment))

        if (!assessment.isClassifierEligible) {
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

        val cropBox = requireNotNull(assessment.cropBox)
        val pixels = FishDetectionQualityGate.cropBoxPixels(cropBox, bitmap.width, bitmap.height)
        val crop = Bitmap.createBitmap(
            bitmap,
            pixels[0],
            pixels[1],
            pixels[2] - pixels[0],
            pixels[3] - pixels[1],
        )
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

        onProgress(RecognitionProgress(RecognitionPhase.CLASSIFYING, assessment))
        return try {
            val prediction = classifier.recognize(crop, traceContext)
            val ready = ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = prediction,
                cropPixels = pixels,
            )
            onProgress(RecognitionProgress(RecognitionPhase.RESULT, assessment))
            ready
        } finally {
            if (crop !== bitmap && !crop.isRecycled) crop.recycle()
        }
    }

    override fun close() {
        detector.close()
        classifier.close()
    }
}
