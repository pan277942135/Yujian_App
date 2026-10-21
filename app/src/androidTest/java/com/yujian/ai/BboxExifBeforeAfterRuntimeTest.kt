package com.yujian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.media.RecognitionImageStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Real detector outputs on the same repository fixture with EXIF 2 ignored vs normalized. */
@RunWith(AndroidJUnit4::class)
class BboxExifBeforeAfterRuntimeTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun recordsBeforeAfterDetectorBoxesForMirroredExifInput() = runBlocking {
        val sourceBytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(CASE_B_SHA256, sourceBytes.sha256())
        val variant = File(appContext.cacheDir, "bbox_case_b_exif2_variant.jpg")
        var beforeBitmap: Bitmap? = null
        var afterBitmap: Bitmap? = null
        var normalizedPath: String? = null
        try {
            variant.writeBytes(sourceBytes)
            ExifInterface(variant).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString())
                saveAttributes()
            }
            val variantBytes = variant.readBytes()
            val variantOrientation = ExifInterface(variant).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            assertEquals(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, variantOrientation)

            // This is the previous production behavior for mirrored EXIF: decode the encoded pixels
            // and use rotationDegrees (zero for orientation 2), leaving the reflection unapplied.
            val before = requireNotNull(BitmapFactory.decodeByteArray(variantBytes, 0, variantBytes.size))
            beforeBitmap = before
            val beforeResult = FishRecognitionPipeline(appContext).use { pipeline ->
                pipeline.recognize(before)
            }

            val normalized = RecognitionImageStore.normalizeCameraFileWithDetails(appContext, variant)
            normalizedPath = normalized.selectedImage.filePath
            val after = normalized.selectedImage.bitmap
            afterBitmap = after
            assertEquals(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, normalized.exifOrientation)
            assertEquals(before.width, after.width)
            assertEquals(before.height, after.height)
            assertHorizontalMirror(before, after)
            val afterResult = FishRecognitionPipeline(appContext).use { pipeline ->
                pipeline.recognize(after)
            }
            assertEquals("12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f", beforeResult.detectorRun.onnxSha256)
            assertEquals(beforeResult.detectorRun.onnxSha256, afterResult.detectorRun.onnxSha256)

            writeEvidence(
                appContext.cacheDir,
                sourceBytes,
                variantBytes,
                variantOrientation,
                before,
                beforeResult,
                after,
                afterResult,
            )
        } finally {
            beforeBitmap?.let { if (!it.isRecycled) it.recycle() }
            afterBitmap?.let { if (!it.isRecycled) it.recycle() }
            normalizedPath?.let { File(it).delete() }
            variant.delete()
        }
    }

    private fun assertHorizontalMirror(before: Bitmap, after: Bitmap) {
        val sampleY = before.height / 2
        for (x in listOf(before.width / 4, before.width / 3, before.width / 2, before.width * 2 / 3)) {
            val expected = before.getPixel(before.width - 1 - x, sampleY)
            val actual = after.getPixel(x, sampleY)
            assertColorNear(expected, actual)
        }
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        val tolerance = 28
        assertTrue(abs(Color.red(expected) - Color.red(actual)) <= tolerance)
        assertTrue(abs(Color.green(expected) - Color.green(actual)) <= tolerance)
        assertTrue(abs(Color.blue(expected) - Color.blue(actual)) <= tolerance)
    }

    private fun writeEvidence(
        cacheDir: File,
        sourceBytes: ByteArray,
        variantBytes: ByteArray,
        orientation: Int,
        beforeBitmap: Bitmap,
        before: ProductionRecognitionResult,
        afterBitmap: Bitmap,
        after: ProductionRecognitionResult,
    ) {
        val evidence = JSONObject()
            .put("schema_version", "BBOX_EXIF_BEFORE_AFTER_V1")
            .put("status", "REPRODUCED")
            .put("fixture", FIXTURE)
            .put("fixture_sha256", sourceBytes.sha256())
            .put("exif_variant_sha256", variantBytes.sha256())
            .put("exif_orientation", orientation)
            .put("identity", "derived metadata-only EXIF 2 variant of the public repository Case B fixture; not a user photo")
            .put("environment", JSONObject()
                .put("api_level", Build.VERSION.SDK_INT)
                .put("android_release", Build.VERSION.RELEASE)
                .put("device_model", Build.MODEL)
                .put("runtime", "Android ONNX Runtime + TensorFlow Lite"))
            .put("parameters", JSONObject()
                .put("detector_input_size", FishDetectorEngine.EXPECTED_INPUT_SIZE)
                .put("weak_confidence", FishDetectionQualityGate.WEAK_CONFIDENCE.toDouble())
                .put("strong_confidence", FishDetectionQualityGate.STRONG_CONFIDENCE.toDouble())
                .put("nms_iou", FishDetectionQualityGate.NMS_IOU.toDouble())
                .put("crop_expand_ratio", FishDetectionQualityGate.CROP_EXPAND_RATIO.toDouble()))
            .put("ground_truth_status", "MISSING")
            .put("gt_iou", JSONObject.NULL)
            .put("before", resultJson(
                behavior = "prior production behavior: decode pixels, ignore mirror orientation because rotationDegrees is zero",
                bitmap = beforeBitmap,
                result = before,
            ))
            .put("after", resultJson(
                behavior = "current production behavior: apply EXIF orientation 2 before detector and display",
                bitmap = afterBitmap,
                result = after,
            ))
            .put("coordinate_mapping", JSONObject()
                .put("normalized_raw_file_to_upright_x1", "1 - upright_x2")
                .put("normalized_raw_file_to_upright_x2", "1 - upright_x1")
                .put("y1", "unchanged")
                .put("y2", "unchanged"))
        File(cacheDir, "bbox_before_after_metrics.json").writeText(evidence.toString(2) + "\n")
        writeComparisonPng(cacheDir, beforeBitmap, before, afterBitmap, after)
        println("BBOX_EXIF_BEFORE_AFTER=" + evidence.toString())
    }

    private fun resultJson(
        behavior: String,
        bitmap: Bitmap,
        result: ProductionRecognitionResult,
    ): JSONObject {
        val primary = result.assessment.primary
        val box = primary?.box?.normalized()
        val crop = result.assessment.cropBox?.normalized()
        return JSONObject()
            .put("behavior", behavior)
            .put("bitmap_dimensions", "${bitmap.width}x${bitmap.height}")
            .put("model_version", result.detectorRun.modelVersion)
            .put("onnx_sha256", result.detectorRun.onnxSha256)
            .put("orientation_attempt", result.detectorRun.orientationAttempt)
            .put("selected_attempt", result.detectorRun.selectedAttempt)
            .put("status", result.assessment.status.wireName)
            .put("confidence", primary?.confidence?.toDouble() ?: JSONObject.NULL)
            .put("bbox_xyxy_normalized_input_bitmap", box?.let {
                org.json.JSONArray(listOf(it.x1, it.y1, it.x2, it.y2))
            } ?: JSONObject.NULL)
            .put("crop_bbox_xyxy_normalized_input_bitmap", crop?.let {
                org.json.JSONArray(listOf(it.x1, it.y1, it.x2, it.y2))
            } ?: JSONObject.NULL)
            .put("crop_pixels_input_bitmap", result.cropPixels?.let { org.json.JSONArray(it.toList()) } ?: JSONObject.NULL)
            .put("classifier_top1", result.prediction?.top1?.speciesKey ?: JSONObject.NULL)
            .put("classifier_confidence", result.prediction?.top1?.confidence?.toDouble() ?: JSONObject.NULL)
    }

    private fun writeComparisonPng(
        cacheDir: File,
        beforeBitmap: Bitmap,
        before: ProductionRecognitionResult,
        afterBitmap: Bitmap,
        after: ProductionRecognitionResult,
    ) {
        val panelWidth = 576
        val panelHeight = 768
        val footerHeight = 44
        val output = Bitmap.createBitmap(panelWidth * 2, panelHeight + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        drawPanel(canvas, 0, panelWidth, panelHeight, beforeBitmap, before, "BEFORE · EXIF 2 ignored", imagePaint)
        drawPanel(canvas, panelWidth, panelWidth, panelHeight, afterBitmap, after, "AFTER · EXIF 2 applied", imagePaint)
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f }
        canvas.drawText("Case B derived fixture · model predictions only · Ground Truth missing · IoU not computed",
            12f, panelHeight + 29f, footer)
        File(cacheDir, "bbox_before_after_comparison.png").outputStream().use { stream ->
            assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        output.recycle()
    }

    private fun drawPanel(
        canvas: Canvas,
        left: Int,
        panelWidth: Int,
        panelHeight: Int,
        bitmap: Bitmap,
        result: ProductionRecognitionResult,
        title: String,
        imagePaint: Paint,
    ) {
        canvas.drawBitmap(bitmap, null, Rect(left, 0, left + panelWidth, panelHeight), imagePaint)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 0, 0, 0) }
        canvas.drawRect(left.toFloat(), 0f, (left + panelWidth).toFloat(), 48f, shade)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
        canvas.drawText("$title · ${result.assessment.status.wireName}", left + 12f, 33f, label)

        result.assessment.cropBox?.let { crop ->
            val b = crop.normalized()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFA726.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(
                left + b.x1 * panelWidth,
                b.y1 * panelHeight,
                left + b.x2 * panelWidth,
                b.y2 * panelHeight,
                paint,
            )
        }
        result.assessment.primary?.box?.normalized()?.let { b ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.CYAN
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRect(
                left + b.x1 * panelWidth,
                b.y1 * panelHeight,
                left + b.x2 * panelWidth,
                b.y2 * panelHeight,
                paint,
            )
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val CASE_B_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
