package com.yujian.ai.ai.subject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
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
        var roiWidth = 0
        var roiHeight = 0
        try {
            val pixels = FishDetectionQualityGate.cropBoxPixels(
                detectorBbox.expand(config.expandRatio), source.width, source.height,
            )
            roi = Bitmap.createBitmap(source, pixels[0], pixels[1], pixels[2] - pixels[0], pixels[3] - pixels[1])
            roiWidth = roi.width
            roiHeight = roi.height
            val expectedMaskSize = roiWidth * roiHeight
            val segmentation = Tasks.await(segmenter.process(InputImage.fromBitmap(roi, 0)))
            val mask = segmentation.foregroundConfidenceMask
                ?: return@withContext failed(
                    code = "MASK_UNAVAILABLE",
                    started = started,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    expectedMaskSize = expectedMaskSize,
                )

            // ML Kit's foreground mask is consumed as a FloatBuffer. Record and validate
            // the actual element count before mapping it to pixels; never index by an
            // assumed ROI size when the returned buffer is inconsistent.
            val maskSize = mask.remaining()
            if (maskSize != expectedMaskSize) {
                return@withContext failed(
                    code = "MASK_SIZE_MISMATCH",
                    started = started,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    maskSize = maskSize,
                    expectedMaskSize = expectedMaskSize,
                )
            }
            if (maskSize == 0) {
                return@withContext failed(
                    code = "MASK_EMPTY",
                    started = started,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    maskSize = maskSize,
                    expectedMaskSize = expectedMaskSize,
                )
            }

            val values = FloatArray(maskSize)
            mask.get(values)
            val quality = FishSubjectQualityGate.assess(values, roiWidth, roiHeight)
            val area = values.count { it >= FishSubjectQualityGate.MASK_THRESHOLD }.toFloat() / values.size
            if (quality == FishSubjectQuality.INVALID) {
                return@withContext failed(
                    code = "SUBJECT_QUALITY_INVALID",
                    started = started,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    maskSize = maskSize,
                    expectedMaskSize = expectedMaskSize,
                    area = area,
                    quality = quality,
                )
            }

            val subject = Bitmap.createBitmap(roiWidth, roiHeight, Bitmap.Config.ARGB_8888)
            val colors = IntArray(values.size) { index ->
                val alpha = (values[index].coerceIn(0f, 1f) * 255f).roundToInt()
                val pixel = roi.getPixel(index % roiWidth, index / roiWidth)
                Color.argb(alpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
            subject.setPixels(colors, 0, roiWidth, 0, 0, roiWidth, roiHeight)
            val file = File(outputDir, "subject_${UUID.randomUUID()}.png")
            file.outputStream().use { output -> check(subject.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            subject.recycle()
            FishSubjectResult(
                status = SubjectStatus.READY,
                bitmapPath = file.absolutePath,
                width = roiWidth,
                height = roiHeight,
                processingMs = elapsed(started),
                roiWidth = roiWidth,
                roiHeight = roiHeight,
                maskSize = maskSize,
                expectedMaskSize = expectedMaskSize,
                maskAreaRatio = area,
                quality = quality,
            )
        } catch (error: Exception) {
            val mlKitError = error as? MlKitException
            val root = generateSequence(error as Throwable?) { it.cause }.lastOrNull()
            failed(
                code = if (mlKitError != null) "MLKIT_ERROR" else error::class.java.simpleName.ifBlank { "SEGMENTATION_FAILED" },
                started = started,
                roiWidth = roiWidth,
                roiHeight = roiHeight,
                exceptionClass = error::class.java.name,
                mlKitErrorCode = mlKitError?.errorCode,
                errorMessage = error.message,
                rootCause = root?.let { "${it::class.java.name}: ${it.message}" },
            )
        } finally {
            roi?.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }

    private fun failed(
        code: String,
        started: Long,
        roiWidth: Int = 0,
        roiHeight: Int = 0,
        maskSize: Int = 0,
        expectedMaskSize: Int = 0,
        area: Float = 0f,
        quality: FishSubjectQuality? = null,
        exceptionClass: String? = null,
        mlKitErrorCode: Int? = null,
        errorMessage: String? = null,
        rootCause: String? = null,
    ) = FishSubjectResult(
        status = SubjectStatus.FAILED,
        errorCode = code,
        errorMessage = errorMessage,
        exceptionClass = exceptionClass,
        mlKitErrorCode = mlKitErrorCode,
        rootCause = rootCause,
        processingMs = elapsed(started),
        roiWidth = roiWidth,
        roiHeight = roiHeight,
        maskSize = maskSize,
        expectedMaskSize = expectedMaskSize,
        maskAreaRatio = area,
        quality = quality,
    )

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000L

    override fun close() { segmenter.close() }
}
