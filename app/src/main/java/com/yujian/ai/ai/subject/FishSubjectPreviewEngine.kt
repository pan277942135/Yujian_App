package com.yujian.ai.ai.subject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

data class SubjectCropConfig(val expandRatio: Float = 0.12f) {
    init { require(expandRatio >= 0f && expandRatio <= 1f) }
}

/** Post-recognition preview only. It never runs detection or changes the classifier input. */
class FishSubjectPreviewEngine(
    context: Context,
    private val config: SubjectCropConfig = SubjectCropConfig(),
) : AutoCloseable {
    private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
    )
    private val outputDir = File(context.cacheDir, "fish_subject_previews").apply { mkdirs() }

    suspend fun generate(source: Bitmap, detectorBbox: NormalizedFishBox): FishSubjectResult = withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        var roi: Bitmap? = null
        try {
            val pixels = FishDetectionQualityGate.cropBoxPixels(
                detectorBbox.expand(config.expandRatio), source.width, source.height,
            )
            roi = Bitmap.createBitmap(source, pixels[0], pixels[1], pixels[2] - pixels[0], pixels[3] - pixels[1])
            val segmentation = Tasks.await(segmenter.process(InputImage.fromBitmap(roi, 0)))
            val values = segmentation.foregroundConfidenceMask
                ?: return@withContext failed("MASK_UNAVAILABLE", started)
            val quality = FishSubjectQualityGate.assess(values, roi.width, roi.height)
            val area = values.count { it >= FishSubjectQualityGate.MASK_THRESHOLD }.toFloat() / values.size
            if (quality == FishSubjectQuality.INVALID) return@withContext failed("SUBJECT_QUALITY_INVALID", started, area, quality)

            val subject = Bitmap.createBitmap(roi.width, roi.height, Bitmap.Config.ARGB_8888)
            val colors = IntArray(values.size) { index ->
                val alpha = (values[index].coerceIn(0f, 1f) * 255f).roundToInt()
                val pixel = roi.getPixel(index % roi.width, index / roi.width)
                Color.argb(alpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
            subject.setPixels(colors, 0, roi.width, 0, 0, roi.width, roi.height)
            val file = File(outputDir, "subject_${UUID.randomUUID()}.png")
            file.outputStream().use { output -> check(subject.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            subject.recycle()
            FishSubjectResult(SubjectStatus.READY, file.absolutePath, roi.width, roi.height, processingMs = elapsed(started), maskAreaRatio = area, quality = quality)
        } catch (error: Exception) {
            failed(error::class.java.simpleName.ifBlank { "SEGMENTATION_FAILED" }, started)
        } finally {
            roi?.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }

    private fun failed(code: String, started: Long, area: Float = 0f, quality: FishSubjectQuality? = null) =
        FishSubjectResult(SubjectStatus.FAILED, errorCode = code, processingMs = elapsed(started), maskAreaRatio = area, quality = quality)

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000L

    override fun close() { segmenter.close() }
}
