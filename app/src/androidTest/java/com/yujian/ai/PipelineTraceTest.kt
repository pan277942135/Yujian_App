package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.FishQualityLevel
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.InferenceTrace
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class PipelineTraceTest {
    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun readyGoldenReportsDetectorCropAsClassifierSource() = runBlocking {
        val manifest = JSONObject(readTestAssetText("detector/golden_cases.json"))
        val cases = manifest.getJSONArray("cases")
        val readyCase = (0 until cases.length())
            .map { cases.getJSONObject(it) }
            .first { it.getString("expected_status") == FishInputStatus.READY.name }
        val caseId = readyCase.getString("id")
        val uri = readyCase.getString("golden_gcs_uri")
        val extension = uri.substringAfterLast('.', "jpg").lowercase()
        val bitmap = decodeOrientedTestAsset("detector/golden/$caseId.$extension")

        try {
            val result = FishRecognitionPipeline(appContext).use { pipeline ->
                pipeline.recognize(bitmap)
            }
            assertEquals(FishInputStatus.READY, result.status)
            assertTrue(result.ready)
            val cropPixels = requireNotNull(result.cropPixels) { "READY pipeline result must expose crop pixels" }
            val cropWidth = cropPixels[2] - cropPixels[0]
            val cropHeight = cropPixels[3] - cropPixels[1]
            val primary = requireNotNull(result.assessment.primary)
            val report = InferenceTrace.lastReport
            println("PIPELINE_TRACE_REPORT\n$report")

            assertReportContains(report, "pipeline=DETECTOR_CROP_CLASSIFIER")
            assertReportContains(report, "original_size=${bitmap.width}x${bitmap.height}")
            assertReportContains(report, "detector_model_version=${result.detectorRun.modelVersion}")
            assertReportContains(report, "detector_confidence=")
            assertReportContains(report, "detector_bbox_normalized=")
            assertReportContains(report, "quality_gate_version=QUALITY_GATE_v1.1")
            assertReportContains(report, "quality_level=GOOD")
            assertReportContains(report, "quality_reason=single_complete_fish_ready_for_classifier")
            assertReportContains(report, "bbox_area_ratio=")
            assertReportContains(report, "crop_expand_ratio=0.15")
            assertReportContains(report, "crop_pixels=${cropPixels.contentToString()}")
            assertReportContains(report, "crop_size=${cropWidth}x${cropHeight}")
            assertReportContains(report, "classifier_source=DETECTOR_CROP")
            assertReportContains(report, "classifier_source_size=${cropWidth}x${cropHeight}")
            assertReportContains(report, "preprocess=FISH_CROP_LETTERBOX")
            assertTrue(primary.confidence >= FishDetectionQualityGate.STRONG_CONFIDENCE)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    @Test
    fun incompleteFishWarningStillRunsClassifier() = runBlocking {
        val manifest = JSONObject(readTestAssetText("detector/golden_cases.json"))
        val cases = manifest.getJSONArray("cases")
        val warningCase = (0 until cases.length())
            .map { cases.getJSONObject(it) }
            .first { it.getString("expected_status") == FishInputStatus.INCOMPLETE_FISH.name }
        val uri = warningCase.getString("golden_gcs_uri")
        val extension = uri.substringAfterLast('.', "jpg").lowercase()
        val bitmap = decodeOrientedTestAsset("detector/golden/${warningCase.getString("id")}.${extension}")

        try {
            val result = FishRecognitionPipeline(appContext).use { pipeline ->
                pipeline.recognize(bitmap)
            }
            assertEquals(FishInputStatus.INCOMPLETE_FISH, result.status)
            assertEquals(FishQualityLevel.WARNING, result.assessment.qualityLevel)
            assertTrue(result.ready)
            assertTrue(result.prediction != null)
            val cropPixels = requireNotNull(result.cropPixels)
            val report = InferenceTrace.lastReport

            assertReportContains(report, "quality_gate_version=QUALITY_GATE_v1.1")
            assertReportContains(report, "quality_level=WARNING")
            assertReportContains(report, "quality_reason=primary_fish_bbox_touches_image_edge")
            assertReportContains(report, "bbox_area_ratio=")
            assertReportContains(report, "crop_pixels=${cropPixels.contentToString()}")
            assertReportContains(report, "classifier_source=DETECTOR_CROP")
            assertReportContains(report, "preprocess=FISH_CROP_LETTERBOX")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun assertReportContains(report: String, expected: String) {
        assertTrue(
            "Expected inference report to contain '$expected'.\nActual report:\n$report",
            report.contains(expected),
        )
    }

    private fun readTestAssetBytes(path: String): ByteArray = testContext.assets.open(path).use { it.readBytes() }
    private fun readTestAssetText(path: String): String = readTestAssetBytes(path).toString(Charsets.UTF_8)

    private fun decodeOrientedTestAsset(path: String): Bitmap {
        val bytes = readTestAssetBytes(path)
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cannot decode $path" }
        val orientation = ByteArrayInputStream(bytes).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source && !source.isRecycled) source.recycle()
        }
    }
}
