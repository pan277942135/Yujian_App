package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.FishQualityLevel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DetectorGoldenParityTest {
    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun productionDetectorMatchesBackendGoldenCases() {
        val manifest = JSONObject(readTestAssetText("detector/golden_cases.json"))
        assertEquals("DET_FISH_GOLDEN_CASES_v1", manifest.getString("schema_version"))
        assertEquals(FishDetectorEngine.MODEL_VERSION, manifest.getString("model_version"))
        assertEquals(FishDetectorEngine.DATASET_VERSION, manifest.getString("dataset_version"))
        val bboxTolerance = manifest.getDouble("bbox_tolerance").toFloat()
        val cropTolerance = manifest.getInt("crop_pixel_tolerance")
        val cases = manifest.getJSONArray("cases")
        assertEquals(5, cases.length())

        val failures = mutableListOf<String>()
        val seen = mutableSetOf<FishInputStatus>()
        FishDetectorEngine(appContext).use { engine ->
            repeat(cases.length()) { index ->
                val case = cases.getJSONObject(index)
                val expectedStatus = FishInputStatus.valueOf(case.getString("expected_status"))
                val caseId = case.getString("id")
                val uri = case.getString("golden_gcs_uri")
                val extension = uri.substringAfterLast('.', "jpg").lowercase()
                val bitmap = decodeOrientedTestAsset("detector/golden/$caseId.$extension")
                try {
                    val expectedDimensions = case.getJSONObject("source_dimensions")
                    val expectedInput = case.getJSONObject("detector_input")
                    if (expectedDimensions.getInt("width") != bitmap.width || expectedDimensions.getInt("height") != bitmap.height) {
                        failures += "$caseId source dimensions expected=${expectedDimensions.getInt("width")}x${expectedDimensions.getInt("height")} actual=${bitmap.width}x${bitmap.height}"
                        return@repeat
                    }

                    val run = kotlinx.coroutines.runBlocking { engine.detect(bitmap) }
                    val assessment = FishDetectionQualityGate.assess(run.detections)
                    seen += assessment.status
                    val expectedDetections = case.getJSONArray("detections")
                    val diagnostic = buildString {
                        append("case=$caseId expectedStatus=$expectedStatus actualStatus=${assessment.status}")
                        append(" expectedInputScale=${expectedInput.getDouble("scale")}")
                        append(" expectedDraw=${expectedInput.getInt("draw_width")}x${expectedInput.getInt("draw_height")}")
                        append(" actualInputScale=${run.inputScale}")
                        append(" actualDraw=${run.inputDrawWidth}x${run.inputDrawHeight}")
                        append(" expectedDetections=${expectedDetections}")
                        append(" actualDetections=")
                        append(run.detections.joinToString(prefix = "[", postfix = "]") { detection ->
                            "{confidence=${detection.confidence},bbox=[${detection.box.x1},${detection.box.y1},${detection.box.x2},${detection.box.y2}]}"
                        })
                    }
                    println("DETECTOR_GOLDEN_DIAGNOSTIC $diagnostic")

                    if (manifest.getString("onnx_sha256") != run.onnxSha256) {
                        failures += "$caseId detector SHA mismatch expected=${manifest.getString("onnx_sha256")} actual=${run.onnxSha256}"
                    }
                    if (assessment.status != expectedStatus) failures += "$caseId status mismatch: $diagnostic"
                    if (expectedDetections.length() != run.detections.size) {
                        failures += "$caseId detection count expected=${expectedDetections.length()} actual=${run.detections.size}"
                    }

                    val compareCount = minOf(expectedDetections.length(), run.detections.size)
                    repeat(compareCount) { detectionIndex ->
                        val expected = expectedDetections.getJSONObject(detectionIndex)
                        val expectedBox = expected.getJSONArray("bbox")
                        val actual = run.detections[detectionIndex]
                        val expectedConfidence = expected.getDouble("confidence").toFloat()
                        if (abs(expectedConfidence - actual.confidence) > 0.05f) {
                            failures += "$caseId[$detectionIndex] confidence expected=$expectedConfidence actual=${actual.confidence} delta=${abs(expectedConfidence - actual.confidence)}"
                        }
                        val expectedCoords = floatArrayOf(
                            expectedBox.getDouble(0).toFloat(), expectedBox.getDouble(1).toFloat(),
                            expectedBox.getDouble(2).toFloat(), expectedBox.getDouble(3).toFloat(),
                        )
                        val actualCoords = floatArrayOf(actual.box.x1, actual.box.y1, actual.box.x2, actual.box.y2)
                        expectedCoords.indices.forEach { coord ->
                            if (abs(expectedCoords[coord] - actualCoords[coord]) > bboxTolerance) {
                                failures += "$caseId[$detectionIndex].bbox[$coord] expected=${expectedCoords[coord]} actual=${actualCoords[coord]} tolerance=$bboxTolerance"
                            }
                        }
                    }

                    val expectedCrop = if (case.isNull("crop_pixels")) null else case.getJSONArray("crop_pixels")
                    val actualCrop = assessment.cropBox?.let { FishDetectionQualityGate.cropBoxPixels(it, bitmap.width, bitmap.height) }
                    val v11WarningCrop = expectedCrop == null && assessment.qualityLevel == FishQualityLevel.WARNING && actualCrop != null
                    if (expectedCrop == null && actualCrop != null && !v11WarningCrop) {
                        failures += "$caseId unexpected crop=${actualCrop.contentToString()}"
                    } else if (expectedCrop != null && actualCrop == null) {
                        failures += "$caseId missing crop expected=${expectedCrop}"
                    } else if (expectedCrop != null && actualCrop != null) {
                        repeat(actualCrop.size) { coord ->
                            val expectedValue = expectedCrop.getInt(coord)
                            if (kotlin.math.abs(expectedValue - actualCrop[coord]) > cropTolerance) {
                                failures += "$caseId crop[$coord] expected=$expectedValue actual=${actualCrop[coord]} tolerance=$cropTolerance"
                            }
                        }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }

        val expectedStatuses = setOf(
            FishInputStatus.READY,
            FishInputStatus.NO_FISH,
            FishInputStatus.INCOMPLETE_FISH,
            FishInputStatus.FISH_TOO_SMALL,
            FishInputStatus.MULTIPLE_FISH,
        )
        if (seen != expectedStatuses) failures += "status coverage expected=$expectedStatuses actual=$seen"
        assertTrue("Detector golden parity failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun readTestAssetBytes(path: String): ByteArray = testContext.assets.open(path).use { it.readBytes() }
    private fun readTestAssetText(path: String): String = readTestAssetBytes(path).toString(Charsets.UTF_8)

    private fun decodeOrientedTestAsset(path: String): Bitmap {
        val bytes = readTestAssetBytes(path)
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cannot decode $path" }
        val orientation = ByteArrayInputStream(bytes).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
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
