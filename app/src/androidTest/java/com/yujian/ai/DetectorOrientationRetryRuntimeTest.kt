package com.yujian.ai

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.InferenceTrace
import com.yujian.ai.ai.RecognitionPhase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

/** Real DET_FISH v0.3 Android ORT + Classifier v0.2 inference and Python CPU parity. */
@RunWith(AndroidJUnit4::class)
class DetectorOrientationRetryRuntimeTest {
    @Test
    fun caseBNightFlashRunsV03DetectorClassifierAndMatchesPythonCpuReference() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val bytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(CASE_B_SOURCE_SHA256, bytes.sha256())
        val reference = JSONObject(
            testContext.assets.open(REFERENCE).use { it.readBytes().toString(Charsets.UTF_8) },
        )
        assertEquals(MODEL_VERSION, reference.getString("model_version"))
        assertEquals(DETECTOR_ONNX_SHA256, reference.getString("onnx_sha256"))
        assertEquals(CASE_B_SOURCE_SHA256, reference.getString("fixture_sha256"))

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        val source = requireNotNull(bitmap)
        assertEquals(1152, source.width)
        assertEquals(1536, source.height)

        try {
            // One original-orientation raw inference is compared element-by-element
            // with ONNX Runtime CPU 1.20.1. The Android app uses ONNX Runtime Android
            // 1.29.0; explicit tolerances account for CPU kernel/version accumulation
            // differences, not model quantization or preprocessing drift.
            val raw = FishDetectorEngine(targetContext).use { it.debugRawInference(source) }
            assertEquals(MODEL_VERSION, raw.modelVersion)
            assertEquals(DETECTOR_ONNX_SHA256, raw.onnxSha256)
            assertEquals(listOf(1, 3, 416, 416), raw.inputShape)
            assertEquals(listOf(1, 3549, 6), raw.outputShape)

            val expectedInputBytes = Base64.decode(
                reference.getString("input_tensor_f32_le_base64"),
                Base64.DEFAULT,
            )
            val expectedInput = expectedInputBytes.toFloatArrayLittleEndian()
            assertEquals(expectedInput.size, raw.inputTensor.size)
            val inputMaxAbs = expectedInput.indices.maxOf { abs(expectedInput[it] - raw.inputTensor[it]) }
            val inputMeanAbs = expectedInput.indices.sumOf {
                abs(expectedInput[it] - raw.inputTensor[it]).toDouble()
            } / expectedInput.size
            // JPEG decoding can differ slightly between Pillow/libjpeg and Android/Skia.
            // BGR planar ordering, letterbox geometry, and all tensor values are compared.
            assertTrue("preprocess max abs error=" + inputMaxAbs, inputMaxAbs <= INPUT_MAX_ABS_TOLERANCE)
            assertTrue("preprocess mean abs error=" + inputMeanAbs, inputMeanAbs <= INPUT_MEAN_ABS_TOLERANCE.toDouble())

            val expectedOutputBytes = Base64.decode(
                reference.getString("output_tensor_f32_le_base64"),
                Base64.DEFAULT,
            )
            val expectedOutput = expectedOutputBytes.toFloatArrayLittleEndian()
            assertEquals(expectedOutput.size, raw.outputTensor.size)
            val outputDiffs = FloatArray(expectedOutput.size) { index ->
                abs(expectedOutput[index] - raw.outputTensor[index])
            }
            val outputMaxAbs = outputDiffs.maxOrNull() ?: 0f
            val outputMeanAbs = outputDiffs.average().toFloat()
            assertTrue("raw ONNX tensor max abs error=" + outputMaxAbs, outputMaxAbs <= OUTPUT_MAX_ABS_TOLERANCE)
            assertTrue("raw ONNX tensor mean abs error=" + outputMeanAbs, outputMeanAbs <= OUTPUT_MEAN_ABS_TOLERANCE)

            val phases = mutableListOf<RecognitionPhase>()
            val result = FishRecognitionPipeline(targetContext).use { pipeline ->
                pipeline.recognize(source) { phases += it.phase }
            }
            val prediction = requireNotNull(result.prediction) {
                "v0.3 detector did not produce a classifier-eligible result: status=" + result.status
            }
            assertEquals(MODEL_VERSION, result.detectorRun.modelVersion)
            assertEquals(DETECTOR_ONNX_SHA256, result.detectorRun.onnxSha256)
            assertEquals(CLASSIFIER_MODEL_VERSION, prediction.modelVersion)
            assertEquals(CLASSIFIER_TFLITE_SHA256, prediction.modelSha256)
            assertTrue(result.ready)
            assertTrue(phases.contains(RecognitionPhase.CLASSIFYING))
            assertTrue(phases.contains(RecognitionPhase.RESULT))
            assertTrue(result.detectorRun.attemptTrace.isNotEmpty())
            assertEquals("ORIGINAL", result.detectorRun.attemptTrace.first().orientationAttempt)
            assertTrue(result.detectorRun.selectedAttempt in setOf("ORIGINAL", "CW90", "CCW90"))
            if (result.detectorRun.selectedAttempt == "ORIGINAL") {
                assertEquals(listOf("ORIGINAL"), result.detectorRun.attemptTrace.map { it.orientationAttempt })
            } else {
                assertEquals(
                    listOf("ORIGINAL", "CW90", "CCW90"),
                    result.detectorRun.attemptTrace.map { it.orientationAttempt },
                )
            }

            val primary = requireNotNull(result.assessment.primary)
            val box = primary.box.normalized()
            assertTrue(box.x1 >= 0f && box.y1 >= 0f && box.x2 <= 1f && box.y2 <= 1f)
            assertTrue(box.x2 > box.x1 && box.y2 > box.y1)
            assertTrue(primary.confidence in 0f..1f)
            val crop = requireNotNull(result.cropPixels)
            assertTrue(crop[0] >= 0 && crop[1] >= 0 && crop[2] <= source.width && crop[3] <= source.height)
            assertTrue(crop[2] > crop[0] && crop[3] > crop[1])
            assertTrue(prediction.top1.speciesKey.isNotBlank())
            assertTrue(prediction.top1.speciesName.isNotBlank())
            assertTrue(prediction.top1.confidence.isFinite())

            println(
                "DET_FISH_V03_REAL_INFERENCE " + JSONObject()
                    .put("detector_version", result.detectorRun.modelVersion)
                    .put("onnx_sha256", result.detectorRun.onnxSha256)
                    .put("input_tensor_sha256_android", raw.inputTensorSha256)
                    .put("input_max_abs_error", inputMaxAbs.toDouble())
                    .put("input_mean_abs_error", inputMeanAbs.toDouble())
                    .put("output_tensor_sha256_python", reference.getString("output_tensor_sha256"))
                    .put("output_tensor_max_abs_error", outputMaxAbs.toDouble())
                    .put("output_tensor_mean_abs_error", outputMeanAbs.toDouble())
                    .put("output_shape", org.json.JSONArray(raw.outputShape))
                    .put("selected_orientation", result.detectorRun.selectedAttempt)
                    .put("attempt_trace", org.json.JSONArray(result.detectorRun.attemptTrace.map {
                        JSONObject()
                            .put("orientation", it.orientationAttempt)
                            .put("detection_count", it.detectionCount)
                            .put("top_confidence", it.topConfidence?.toDouble())
                            .put("quality_status", it.qualityStatus)
                            .put("quality_level", it.qualityLevel)
                    }))
                    .put("bbox_normalized", org.json.JSONArray(listOf(box.x1, box.y1, box.x2, box.y2)))
                    .put("confidence", primary.confidence.toDouble())
                    .put("crop_pixels", org.json.JSONArray(crop.toList()))
                    .put("classifier_version", prediction.modelVersion)
                    .put("classifier_tflite_sha256", prediction.modelSha256)
                    .put("classifier_top1_species_key", prediction.top1.speciesKey)
                    .put("classifier_top1_species_name", prediction.top1.speciesName)
                    .put("classifier_top1_confidence", prediction.top1.confidence.toDouble())
                    .put("detector_latency_ms", result.detectorRun.latencyMs)
                    .put("classifier_latency_ms", prediction.latencyMs)
                    .put("total_latency_ms", result.totalLatencyMs)
                    .put("input_parity_tolerance", JSONObject()
                        .put("max_abs", INPUT_MAX_ABS_TOLERANCE.toDouble())
                        .put("mean_abs", INPUT_MEAN_ABS_TOLERANCE.toDouble()))
                    .put("output_parity_tolerance", JSONObject()
                        .put("max_abs", OUTPUT_MAX_ABS_TOLERANCE.toDouble())
                        .put("mean_abs", OUTPUT_MEAN_ABS_TOLERANCE.toDouble()))
                    .toString(),
            )
            println("PIPELINE_INFERENCE_REPORT\n" + InferenceTrace.lastReport)
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun ByteArray.toFloatArrayLittleEndian(): FloatArray {
        require(size % 4 == 0)
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val REFERENCE = "detector_case_b_v03_reference.json"
        const val CASE_B_SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
        const val MODEL_VERSION = "DET_FISH_v0.3"
        const val DETECTOR_ONNX_SHA256 = "98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1"
        const val CLASSIFIER_MODEL_VERSION = "MODEL_CROP_M1_v0.2"
        const val CLASSIFIER_TFLITE_SHA256 = "29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18"
        const val INPUT_MAX_ABS_TOLERANCE = 2.0f
        const val INPUT_MEAN_ABS_TOLERANCE = 0.05f
        const val OUTPUT_MAX_ABS_TOLERANCE = 0.05f
        const val OUTPUT_MEAN_ABS_TOLERANCE = 0.001f
    }
}
