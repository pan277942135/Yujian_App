package com.yujian.ai

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.DetectorOrientationDiagnostic
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.InferenceTrace
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.RecognitionPhase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

/** Held-out Case B detector regression; the source file is copied byte-for-byte. */
@RunWith(AndroidJUnit4::class)
class DetectorOrientationRetryRuntimeTest {
    @Test
    fun caseBNightFlashSelectsCcwMapsBackAndReachesClassifier() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val bytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(CASE_B_SOURCE_SHA256, bytes.sha256())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        val source = requireNotNull(bitmap)
        assertEquals(1152, source.width)
        assertEquals(1536, source.height)
        val sourceExifOrientation = ByteArrayInputStream(bytes).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        assertEquals(ExifInterface.ORIENTATION_NORMAL, sourceExifOrientation)

        val phases = mutableListOf<RecognitionPhase>()
        val pipeline = FishRecognitionPipeline(targetContext)
        val result = try {
            pipeline.recognize(source) { phases += it.phase }
        } finally {
            pipeline.close()
        }

        val diagnostics = FishDetectorEngine(targetContext).use { engine ->
            runBlocking { engine.diagnoseAllOrientations(source) }
        }
        assertEquals(listOf("ORIGINAL", "CW90", "CCW90"), diagnostics.map { it.orientationAttempt })
        assertTrue(diagnostics.all {
            it.onnxSha256 == result.detectorRun.onnxSha256 &&
                it.inputSize == 416 &&
                it.inputTensorChannelStats.size == 3 &&
                it.inputTensorSha256.matches(Regex("[0-9a-f]{64}")) &&
                it.rawOutputShape == listOf(1, 3549, 6) &&
                it.rawOutputRows.size == 3549 &&
                it.rawOutputRows.all { row -> row.size == 6 } &&
                it.rawOutputSha256.matches(Regex("[0-9a-f]{64}")) &&
                it.topRawCandidates.size == 20
        })
        assertEquals(FishInputStatus.NO_FISH, diagnostics.first().status)
        assertEquals(FishInputStatus.READY, diagnostics.last().status)
        val ccwDiagnostic = diagnostics.last()
        val ccwBox = requireNotNull(result.assessment.primary).box.normalized()
        val diagnosticBox = requireNotNull(ccwDiagnostic.primaryBox).normalized()
        assertEquals(ccwBox.x1, diagnosticBox.x1, 1e-5f)
        assertEquals(ccwBox.y1, diagnosticBox.y1, 1e-5f)
        assertEquals(ccwBox.x2, diagnosticBox.x2, 1e-5f)
        assertEquals(ccwBox.y2, diagnosticBox.y2, 1e-5f)
        writeBboxEvidence(targetContext.cacheDir, bytes, source, diagnostics)

        assertEquals("CCW90", result.detectorRun.selectedAttempt)
        assertEquals(
            listOf("ORIGINAL", "CW90", "CCW90"),
            result.detectorRun.attemptTrace.map { it.orientationAttempt },
        )
        assertEquals(0, result.detectorRun.attemptTrace[0].detectionCount)
        assertTrue(result.detectorRun.attemptTrace[2].detectionCount > 0)
        assertTrue(result.detectorRun.selectionReason.isNotBlank())
        assertTrue(result.detectorRun.attemptTrace.all { it.qualityStatus.isNotBlank() && it.qualityLevel.isNotBlank() })
        assertEquals(
            "12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f",
            result.detectorRun.onnxSha256,
        )

        val box = requireNotNull(result.assessment.primary).box.normalized()
        // Acceptance tolerance: each normalized coordinate ±0.015, area ±0.02,
        // and each original-source crop edge ±24 pixels.
        assertEquals(0.342930f, box.x1, 0.015f)
        assertEquals(0.363650f, box.y1, 0.015f)
        assertEquals(0.605436f, box.x2, 0.015f)
        assertEquals(0.924975f, box.y2, 0.015f)
        assertEquals(0.147351f, requireNotNull(result.assessment.bboxAreaRatio), 0.02f)
        val actualCrop = requireNotNull(result.cropPixels)
        intArrayOf(349, 429, 743, 1536).forEachIndexed { index, expected ->
            assertTrue(abs(expected - actualCrop[index]) <= 24)
        }
        assertTrue("Classifier pipeline did not reach CLASSIFYING", phases.contains(RecognitionPhase.CLASSIFYING))

        val trace = InferenceTrace.lastReport
        assertTrue(trace.contains("original_size=1152x1536"))
        assertTrue(trace.contains("selected_attempt=CCW90"))
        assertTrue(trace.contains("detector_bbox_normalized="))
        assertTrue(trace.contains("crop_pixels="))
        assertTrue(trace.contains("classifier_source=DETECTOR_CROP"))
        source.recycle()
    }

    private fun writeBboxEvidence(
        cacheDir: File,
        sourceBytes: ByteArray,
        source: Bitmap,
        diagnostics: List<DetectorOrientationDiagnostic>,
    ) {
        val directions = JSONArray()
        val outputBytes = java.io.ByteArrayOutputStream()
        val manifest = JSONObject()
            .put("schema_version", "BBOX_ANDROID_DIRECTION_REPLAY_V1")
            .put("status", "REPRODUCED")
            .put("experiment", "ANDROID_API28_CASE_B_ORIENTATION_REPLAY")
            .put("environment", JSONObject()
                .put("api_level", Build.VERSION.SDK_INT)
                .put("device_model", Build.MODEL)
                .put("runtime", "Android ONNX Runtime"))
            .put("source", JSONObject()
                .put("filename", FIXTURE)
                .put("sha256", sourceBytes.sha256())
                .put("format", "JPEG")
                .put("width", source.width)
                .put("height", source.height)
                .put("exif_orientation", ExifInterface.ORIENTATION_NORMAL)
                .put("identity", "repository Case B fixture; separate from the user Case A candidate")
                .put("ground_truth_status", "MISSING")
                .put("ground_truth_xyxy", JSONObject.NULL)
                .put("iou", JSONObject.NULL))
            .put("model", JSONObject()
                .put("model_version", FishDetectorEngine.MODEL_VERSION)
                .put("onnx_sha256", "12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f")
                .put("output_contract", "decoded_xywh_objectness_class_probability"))
            .put("parameters", JSONObject()
                .put("weak_confidence", FishDetectionQualityGate.WEAK_CONFIDENCE.toDouble())
                .put("strong_confidence", FishDetectionQualityGate.STRONG_CONFIDENCE.toDouble())
                .put("nms_iou", FishDetectionQualityGate.NMS_IOU.toDouble())
                .put("min_primary_area_ratio", FishDetectionQualityGate.MIN_PRIMARY_AREA_RATIO.toDouble())
                .put("crop_expand_ratio", FishDetectionQualityGate.CROP_EXPAND_RATIO.toDouble()))

        diagnostics.forEach { diagnostic ->
            val rawBuffer = ByteBuffer.allocate(diagnostic.rawOutputRows.sumOf { it.size } * Float.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            diagnostic.rawOutputRows.forEach { row -> row.forEach { value -> rawBuffer.putFloat(value) } }
            val raw = rawBuffer.array()
            val offset = outputBytes.size()
            outputBytes.write(raw)

            val candidates = JSONArray()
            diagnostic.topRawCandidates.forEach { row ->
                candidates.put(JSONArray().apply { row.forEach { put(it.toDouble()) } })
            }
            val detections = JSONArray()
            diagnostic.mappedDetections.forEach { detection ->
                val box = detection.box.normalized()
                detections.put(JSONObject()
                    .put("confidence", detection.confidence.toDouble())
                    .put("bbox_xyxy_normalized_original", JSONArray(listOf(box.x1, box.y1, box.x2, box.y2)))
                    .put("class_name", detection.className))
            }
            val channels = JSONArray()
            diagnostic.inputTensorChannelStats.forEach { stats ->
                channels.put(JSONObject()
                    .put("channel", stats.channel)
                    .put("min", stats.min.toDouble())
                    .put("max", stats.max.toDouble())
                    .put("mean", stats.mean.toDouble())
                    .put("std", stats.standardDeviation.toDouble()))
            }
            directions.put(JSONObject()
                .put("orientation", diagnostic.orientationAttempt)
                .put("view_dimensions", if (diagnostic.orientationAttempt == "ORIGINAL") {
                    JSONArray(listOf(source.width, source.height))
                } else {
                    JSONArray(listOf(source.height, source.width))
                })
                .put("input", JSONObject()
                    .put("shape", JSONArray(listOf(1, 3, diagnostic.inputSize, diagnostic.inputSize)))
                    .put("dtype", "float32")
                    .put("layout", "NCHW")
                    .put("channel_order", "BGR")
                    .put("byte_order", ByteOrder.nativeOrder().toString())
                    .put("sha256", diagnostic.inputTensorSha256)
                    .put("scale", diagnostic.inputScale.toDouble())
                    .put("draw_width", diagnostic.inputDrawWidth)
                    .put("draw_height", diagnostic.inputDrawHeight)
                    .put("padding_value", FishDetectorEngine.YOLOX_FILL)
                    .put("channel_statistics", channels))
                .put("raw_model_output", JSONObject()
                    .put("shape", JSONArray(diagnostic.rawOutputShape))
                    .put("dtype", "float32")
                    .put("sha256", diagnostic.rawOutputSha256)
                    .put("binary_file", "bbox_case_b_direction_replay_raw_outputs.bin")
                    .put("binary_offset", offset)
                    .put("binary_bytes", raw.size)
                    .put("top_20_rows_by_objectness_times_class_probability", candidates))
                .put("detections_after_decode_nms_mapped_to_original", detections)
                .put("primary_bbox_xyxy_normalized_original", diagnostic.primaryBox?.normalized()?.let {
                    JSONArray(listOf(it.x1, it.y1, it.x2, it.y2))
                } ?: JSONObject.NULL)
                .put("status", diagnostic.status.wireName)
                .put("crop_pixels_original", diagnostic.cropPixels?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
                .put("gt_iou", JSONObject.NULL))
        }

        manifest.put("directions", directions)
        val jsonText = manifest.toString(2) + "\n"
        File(cacheDir, "bbox_case_b_direction_replay.json").writeText(jsonText)
        File(cacheDir, "bbox_case_b_direction_replay_raw_outputs.bin").writeBytes(outputBytes.toByteArray())
        File(cacheDir, "bbox_test_manifest.json").writeText(manifest.toString(2) + "\n")
        File(cacheDir, "bbox_metrics_v1.json").writeText(JSONObject()
            .put("schema_version", "BBOX_METRICS_V1")
            .put("status", "INSUFFICIENT_GROUND_TRUTH")
            .put("source_sha256", sourceBytes.sha256())
            .put("onnx_sha256", "12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f")
            .put("gt_box_count", 0)
            .put("iou_eligible_directions", 0)
            .put("aggregate_iou", JSONObject.NULL)
            .put("directions", directions)
            .toString(2) + "\n")
        File(cacheDir, "bbox_coordinate_regression_report.md").writeText(buildString {
            appendLine("# Android BBox coordinate regression evidence")
            appendLine()
            appendLine("- Exact Android runtime: API ${Build.VERSION.SDK_INT}, ${Build.MODEL}.")
            appendLine("- Fixture: `$FIXTURE`; SHA-256 `${sourceBytes.sha256()}`; ${source.width}×${source.height}; EXIF 1.")
            appendLine("- Detector: `DET_FISH_v0.1`; ONNX SHA-256 `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f`.")
            appendLine("- Ground Truth is missing. The report records no IoU and does not claim localization accuracy.")
            appendLine("- ORIGINAL, CW90 and CCW90 used the frozen input contract, confidence thresholds, NMS and crop expansion.")
            diagnostics.forEach { diagnostic ->
                appendLine("- ${diagnostic.orientationAttempt}: gate `${diagnostic.status.wireName}`, detections ${diagnostic.mappedDetections.size}, scale ${diagnostic.inputScale}, draw ${diagnostic.inputDrawWidth}×${diagnostic.inputDrawHeight}, tensor SHA `${diagnostic.inputTensorSha256}`, raw output SHA `${diagnostic.rawOutputSha256}`.")
            }
        })
        writeDirectionReplayPng(cacheDir, source, diagnostics)
        println("BBOX_ANDROID_DIRECTION_REPLAY=" + manifest.toString())
    }

    private fun writeDirectionReplayPng(cacheDir: File, source: Bitmap, diagnostics: List<DetectorOrientationDiagnostic>) {
        val panelWidth = 384
        val panelHeight = 512
        val footerHeight = 36
        val output = Bitmap.createBitmap(panelWidth * diagnostics.size, panelHeight + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 0, 0, 0) }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f }
        val directionColors = listOf(Color.RED, Color.YELLOW, Color.CYAN)

        diagnostics.forEachIndexed { index, diagnostic ->
            val left = index * panelWidth
            val panel = Rect(left, 0, left + panelWidth, panelHeight)
            canvas.drawBitmap(source, null, panel, imagePaint)
            canvas.drawRect(left.toFloat(), 0f, (left + panelWidth).toFloat(), 36f, labelBackground)
            canvas.drawText("${diagnostic.orientationAttempt} · ${diagnostic.status.wireName}", left + 10f, 25f, labelPaint)

            val scaleX = panelWidth.toFloat() / source.width
            val scaleY = panelHeight.toFloat() / source.height
            diagnostic.cropPixels?.let { crop ->
                val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFA726.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }
                canvas.drawRect(
                    left + crop[0] * scaleX,
                    crop[1] * scaleY,
                    left + crop[2] * scaleX,
                    crop[3] * scaleY,
                    cropPaint,
                )
            }
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = directionColors[index % directionColors.size]
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            diagnostic.mappedDetections.forEach { detection ->
                val box = detection.box.normalized()
                canvas.drawRect(
                    left + box.x1 * panelWidth,
                    box.y1 * panelHeight,
                    left + box.x2 * panelWidth,
                    box.y2 * panelHeight,
                    boxPaint,
                )
            }
        }
        canvas.drawText("Case B repository fixture · predictions only · no Ground Truth / no IoU", 10f,
            panelHeight + 25f, footerPaint)
        File(cacheDir, "bbox_case_b_direction_replay.png").outputStream().use { outputStream ->
            assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, outputStream))
        }
        output.recycle()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val CASE_B_SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
