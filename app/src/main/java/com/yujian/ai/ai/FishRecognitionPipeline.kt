package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
import com.yujian.ai.model.RecognitionPrediction

/** Detector-first production pipeline shared by the Android UI. */
data class ProductionRecognitionResult(
    val status: FishInputStatus,
    val detectorRun: FishDetectorEngine.DetectorRun,
    val assessment: FishInputAssessment,
    val prediction: RecognitionPrediction?,
    val cropPixels: IntArray?,
) {
    val ready: Boolean get() = status == FishInputStatus.READY && prediction != null
    val totalLatencyMs: Long get() = detectorRun.latencyMs + (prediction?.latencyMs ?: 0L)
}

class FishRecognitionPipeline(context: Context) : AutoCloseable {
    private val detector = FishDetectorEngine(context)
    private val classifier = FishRecognitionEngine(context)

    suspend fun recognize(bitmap: Bitmap): ProductionRecognitionResult {
        val detectorRun = detector.detect(bitmap)
        val assessment = FishDetectionQualityGate.assess(detectorRun.detections)
        if (assessment.status != FishInputStatus.READY || assessment.cropBox == null) {
            return ProductionRecognitionResult(
                status = assessment.status,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = null,
                cropPixels = null,
            )
        }

        val pixels = FishDetectionQualityGate.cropBoxPixels(
            assessment.cropBox,
            bitmap.width,
            bitmap.height,
        )
        val left = pixels[0]
        val top = pixels[1]
        val right = pixels[2]
        val bottom = pixels[3]
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val primary = requireNotNull(assessment.primary) { "READY assessment must contain a primary fish detection" }
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
        )
        return try {
            val prediction = classifier.recognize(crop, traceContext)
            ProductionRecognitionResult(
                status = FishInputStatus.READY,
                detectorRun = detectorRun,
                assessment = assessment,
                prediction = prediction,
                cropPixels = pixels,
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
