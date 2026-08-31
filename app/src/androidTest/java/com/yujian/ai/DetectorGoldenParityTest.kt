package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishInputStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DetectorGoldenParityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun productionDetectorMatchesBackendGoldenCases() {
        val manifest = JSONObject(readAssetText("detector/golden_cases.json"))
        assertEquals("DET_FISH_GOLDEN_CASES_v1", manifest.getString("schema_version"))
        assertEquals(FishDetectorEngine.MODEL_VERSION, manifest.getString("model_version"))
        assertEquals(FishDetectorEngine.DATASET_VERSION, manifest.getString("dataset_version"))
        val bboxTolerance = manifest.getDouble("bbox_tolerance").toFloat()
        val cropTolerance = manifest.getInt("crop_pixel_tolerance")
        val cases = manifest.getJSONArray("cases")
        assertEquals(5, cases.length())

        val seen = mutableSetOf<FishInputStatus>()
        FishDetectorEngine(context).use { engine ->
            repeat(cases.length()) { index ->
                val case = cases.getJSONObject(index)
                val expectedStatus = FishInputStatus.valueOf(case.getString("expected_status"))
                val caseId = case.getString("id")
                val uri = case.getString("golden_gcs_uri")
                val extension = uri.substringAfterLast('.', "jpg").lowercase()
                val bitmap = decodeOrientedAsset("detector/golden/$caseId.$extension")
                try {
                    val expectedDimensions = case.getJSONObject("source_dimensions")
                    assertEquals("$caseId width", expectedDimensions.getInt("width"), bitmap.width)
                    assertEquals("$caseId height", expectedDimensions.getInt("height"), bitmap.height)

                    val run = kotlinx.coroutines.runBlocking { engine.detect(bitmap) }
                    assertEquals("$caseId detector SHA", manifest.getString("onnx_sha256"), run.onnxSha256)
                    val assessment = FishDetectionQualityGate.assess(run.detections)
                    assertEquals("$caseId status", expectedStatus, assessment.status)
                    seen += assessment.status

                    val expectedDetections = case.getJSONArray("detections")
                    assertEquals("$caseId detection count", expectedDetections.length(), run.detections.size)
                    repeat(expectedDetections.length()) { detectionIndex ->
                        val expected = expectedDetections.getJSONObject(detectionIndex)
                        val expectedBox = expected.getJSONArray("bbox")
                        val actual = run.detections[detectionIndex]
                        assertClose("$caseId[$detectionIndex].x1", expectedBox.getDouble(0).toFloat(), actual.box.x1, bboxTolerance)
                        assertClose("$caseId[$detectionIndex].y1", expectedBox.getDouble(1).toFloat(), actual.box.y1, bboxTolerance)
                        assertClose("$caseId[$detectionIndex].x2", expectedBox.getDouble(2).toFloat(), actual.box.x2, bboxTolerance)
                        assertClose("$caseId[$detectionIndex].y2", expectedBox.getDouble(3).toFloat(), actual.box.y2, bboxTolerance)
                    }

                    if (expectedStatus == FishInputStatus.READY) {
                        val expectedCrop = case.getJSONArray("crop_pixels")
                        assertNotNull("$caseId cropBox", assessment.cropBox)
                        val actualCrop = FishDetectionQualityGate.cropBoxPixels(
                            requireNotNull(assessment.cropBox), bitmap.width, bitmap.height,
                        )
                        assertIntArrayClose(caseId, expectedCrop, actualCrop, cropTolerance)
                    } else {
                        assertTrue("$caseId classifier must be gated", assessment.cropBox == null)
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }

        assertEquals(
            setOf(
                FishInputStatus.READY,
                FishInputStatus.NO_FISH,
                FishInputStatus.INCOMPLETE_FISH,
                FishInputStatus.FISH_TOO_SMALL,
                FishInputStatus.MULTIPLE_FISH,
            ),
            seen,
        )
    }

    private fun readAssetBytes(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }
    private fun readAssetText(path: String): String = readAssetBytes(path).toString(Charsets.UTF_8)

    private fun decodeOrientedAsset(path: String): Bitmap {
        val bytes = readAssetBytes(path)
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

    private fun assertClose(label: String, expected: Float, actual: Float, tolerance: Float) {
        assertTrue("$label expected=$expected actual=$actual tolerance=$tolerance", abs(expected - actual) <= tolerance)
    }

    private fun assertIntArrayClose(label: String, expected: JSONArray, actual: IntArray, tolerance: Int) {
        assertEquals("$label crop length", expected.length(), actual.size)
        repeat(actual.size) { index ->
            val expectedValue = expected.getInt(index)
            assertTrue(
                "$label crop[$index] expected=$expectedValue actual=${actual[index]} tolerance=$tolerance",
                kotlin.math.abs(expectedValue - actual[index]) <= tolerance,
            )
        }
    }
}
