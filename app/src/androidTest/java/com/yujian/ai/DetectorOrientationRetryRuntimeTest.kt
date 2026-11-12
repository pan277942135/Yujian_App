package com.yujian.ai

import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

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
            if (result.detectorRun.selectedAttempt == "ORIGINAL") {
                val pythonTop = reference.getJSONArray("top10").getJSONObject(0)
                val pythonBox = pythonTop.getJSONArray("bbox_normalized_unclipped")
                val expectedBox = FloatArray(4) { pythonBox.getDouble(it).toFloat() }
                val actualBox = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
                expectedBox.indices.forEach { index ->
                    assertTrue(
                        "Python→Android restored BBox mismatch at coordinate $index: expected=${expectedBox[index]} actual=${actualBox[index]}",
                        abs(expectedBox[index] - actualBox[index]) <= BBOX_MAX_ABS_TOLERANCE,
                    )
                }
                val expectedConfidence = pythonTop.getDouble("confidence_objectness_times_class").toFloat()
                assertTrue(
                    "Python→Android detector confidence mismatch: expected=${expectedConfidence} actual=${primary.confidence}",
                    abs(expectedConfidence - primary.confidence) <= DETECTOR_CONFIDENCE_MAX_ABS_TOLERANCE,
                )
                val expandX = (expectedBox[2] - expectedBox[0]) * FishDetectionQualityGate.CROP_EXPAND_RATIO
                val expandY = (expectedBox[3] - expectedBox[1]) * FishDetectionQualityGate.CROP_EXPAND_RATIO
                val expectedCrop = intArrayOf(
                    floor(maxOf(0f, expectedBox[0] - expandX) * source.width).toInt().coerceIn(0, source.width - 1),
                    floor(maxOf(0f, expectedBox[1] - expandY) * source.height).toInt().coerceIn(0, source.height - 1),
                    ceil(minOf(1f, expectedBox[2] + expandX) * source.width).toInt().coerceIn(1, source.width),
                    ceil(minOf(1f, expectedBox[3] + expandY) * source.height).toInt().coerceIn(1, source.height),
                )
                expectedCrop.indices.forEach { index ->
                    assertTrue(
                        "Python→Android classifier crop mismatch at edge $index: expected=${expectedCrop[index]} actual=${crop[index]}",
                        kotlin.math.abs(expectedCrop[index] - crop[index]) <= CROP_EDGE_MAX_DELTA_PX,
                    )
                }
            }
            assertTrue(prediction.top1.speciesKey.isNotBlank())
            assertTrue(prediction.top1.speciesName.isNotBlank())
            assertTrue(prediction.top1.confidence.isFinite())
            assertEquals("yellow_catfish", prediction.top1.speciesKey)
            assertTrue("registered night-catch Top1 must remain HIGH", prediction.top1.confidence >= 0.55f)
            assertTrue("registered night-catch must not be low-confidence", !prediction.lowConfidence)
            assertTrue("classifier must return at least five ranked candidates", prediction.candidates.size >= 5)
            assertTrue(
                "classifier candidates must be ranked by descending probability",
                prediction.candidates.zipWithNext().all { (a, b) -> a.confidence >= b.confidence },
            )

            val top5 = prediction.candidates.take(5)
            val classifierInputBitmap = requireNotNull(prediction.modelInputBitmap)
            val classifierInputTensor = classifierInputTensorFromBitmap(classifierInputBitmap)
            val classifierInputBytes = classifierInputTensor.toLittleEndianBytes()
            val classifierTensorShaFromTrace = Regex("tensor_sha256_f32le=([0-9a-f]{64})")
                .find(InferenceTrace.lastReport)?.groupValues?.get(1)
                ?: error("Classifier tensor SHA is missing from InferenceTrace")
            val classifierTensorSha = classifierInputBytes.sha256()
            assertEquals("Reconstructed classifier tensor must match the production engine tensor SHA",
                classifierTensorShaFromTrace, classifierTensorSha)

            val evidenceBase = requireNotNull(targetContext.getExternalFilesDir(null)) {
                "External app-specific storage is required to export Android inference evidence"
            }
            val evidenceDir = File(evidenceBase, "det-fish-v03-evidence")
            evidenceDir.mkdirs()
            evidenceDir.listFiles()?.forEach { it.deleteRecursively() }

            val sourceFile = File(evidenceDir, "source_fixture.jpg").apply { writeBytes(bytes) }
            val detectorInputAndroidFile = File(evidenceDir, "detector_input_android_f32_le.bin")
                .apply { writeBytes(raw.inputTensor.toLittleEndianBytes()) }
            val detectorInputPythonFile = File(evidenceDir, "detector_input_python_f32_le.bin")
                .apply { writeBytes(expectedInputBytes) }
            val detectorOutputAndroidFile = File(evidenceDir, "detector_output_android_f32_le.bin")
                .apply { writeBytes(raw.outputTensor.toLittleEndianBytes()) }
            val detectorOutputPythonFile = File(evidenceDir, "detector_output_python_f32_le.bin")
                .apply { writeBytes(expectedOutputBytes) }
            val classifierInputTensorFile = File(evidenceDir, "classifier_input_android_f32_le.bin")
                .apply { writeBytes(classifierInputBytes) }
            val referenceFile = File(evidenceDir, "python_detector_reference.json")
                .apply { writeBytes(testContext.assets.open(REFERENCE).use { it.readBytes() }) }
            val cropPixels = requireNotNull(result.cropPixels)
            val cropBitmap = Bitmap.createBitmap(
                source,
                cropPixels[0],
                cropPixels[1],
                cropPixels[2] - cropPixels[0],
                cropPixels[3] - cropPixels[1],
            )
            val cropFile = File(evidenceDir, "detector_classifier_crop.png")
            cropBitmap.writePng(cropFile)
            cropBitmap.recycle()
            val classifierInputBitmapFile = File(evidenceDir, "classifier_model_input_224.png")
            classifierInputBitmap.writePng(classifierInputBitmapFile)
            File(evidenceDir, "classifier_inference_report.txt").writeText(InferenceTrace.lastReport)

            val attemptsJson = org.json.JSONArray()
            result.detectorRun.attemptTrace.forEach { attempt ->
                attemptsJson.put(
                    JSONObject()
                        .put("orientation", attempt.orientationAttempt)
                        .put("detection_count", attempt.detectionCount)
                        .put("top_confidence", attempt.topConfidence?.toDouble())
                        .put("quality_status", attempt.qualityStatus)
                        .put("quality_level", attempt.qualityLevel),
                )
            }
            val top5Json = org.json.JSONArray()
            top5.forEach { candidate ->
                top5Json.put(
                    JSONObject()
                        .put("rank", top5Json.length() + 1)
                        .put("class_index", candidate.classIndex)
                        .put("species_key", candidate.speciesKey)
                        .put("species_name", candidate.speciesName)
                        .put("probability", candidate.confidence.toDouble()),
                )
            }
            val allCandidatesJson = org.json.JSONArray()
            prediction.candidates.forEach { candidate ->
                allCandidatesJson.put(
                    JSONObject()
                        .put("class_index", candidate.classIndex)
                        .put("species_key", candidate.speciesKey)
                        .put("species_name", candidate.speciesName)
                        .put("probability", candidate.confidence.toDouble()),
                )
            }
            val bbox = requireNotNull(result.assessment.primary).box.normalized()
            val evidence = JSONObject()
                .put("schema_version", 1)
                .put("build_sha", BuildConfig.SOURCE_REVISION)
                .put("package_name", targetContext.packageName)
                .put("device_manufacturer", android.os.Build.MANUFACTURER)
                .put("device_model", android.os.Build.MODEL)
                .put("android_api", android.os.Build.VERSION.SDK_INT)
                .put("fixture", FIXTURE)
                .put("fixture_sha256", CASE_B_SOURCE_SHA256)
                .put("fixture_dimensions", org.json.JSONArray(listOf(source.width, source.height)))
                .put("detector", JSONObject()
                    .put("model_version", result.detectorRun.modelVersion)
                    .put("onnx_sha256", result.detectorRun.onnxSha256)
                    .put("input_shape", org.json.JSONArray(raw.inputShape))
                    .put("output_shape", org.json.JSONArray(raw.outputShape))
                    .put("raw_input_android_file", detectorInputAndroidFile.name)
                    .put("raw_input_android_sha256", raw.inputTensorSha256)
                    .put("raw_input_python_file", detectorInputPythonFile.name)
                    .put("raw_output_android_file", detectorOutputAndroidFile.name)
                    .put("raw_output_android_sha256", detectorOutputAndroidFile.readBytes().sha256())
                    .put("raw_output_python_file", detectorOutputPythonFile.name)
                    .put("raw_output_python_sha256", reference.getString("output_tensor_sha256"))
                    .put("input_max_abs_error", inputMaxAbs.toDouble())
                    .put("input_mean_abs_error", inputMeanAbs)
                    .put("input_diff", tensorDiffJson(expectedInput, raw.inputTensor))
                    .put("output_max_abs_error", outputMaxAbs.toDouble())
                    .put("output_mean_abs_error", outputMeanAbs.toDouble())
                    .put("output_diff", tensorDiffJson(expectedOutput, raw.outputTensor))
                    .put("raw_inference_latency_ms", raw.latencyMs)
                    .put("pipeline_latency_ms", result.detectorRun.latencyMs)
                    .put("orientation_retry_policy", result.detectorRun.retryPolicyVersion)
                    .put("orientation_attempts", attemptsJson)
                    .put("selected_orientation", result.detectorRun.selectedAttempt)
                    .put("selection_reason", result.detectorRun.selectionReason)
                    .put("bbox_normalized", org.json.JSONArray(listOf(bbox.x1, bbox.y1, bbox.x2, bbox.y2)))
                    .put("confidence", requireNotNull(result.assessment.primary).confidence.toDouble())
                    .put("crop_pixels_xyxy", org.json.JSONArray(cropPixels.toList()))
                    .put("crop_sha256", cropFile.readBytes().sha256()))
                .put("classifier", JSONObject()
                    .put("model_version", prediction.modelVersion)
                    .put("tflite_sha256", prediction.modelSha256)
                    .put("input_shape", org.json.JSONArray(listOf(1, 3, 224, 224)))
                    .put("input_tensor_file", classifierInputTensorFile.name)
                    .put("input_tensor_sha256", classifierTensorSha)
                    .put("input_bitmap_file", classifierInputBitmapFile.name)
                    .put("top1_species_key", prediction.top1.speciesKey)
                    .put("top1_species_name", prediction.top1.speciesName)
                    .put("top1_probability", prediction.top1.confidence.toDouble())
                    .put("top5", top5Json)
                    .put("all_15_candidates", allCandidatesJson)
                    .put("latency_ms", prediction.latencyMs)
                    .put("total_pipeline_latency_ms", result.totalLatencyMs))
                .put("python_reference_file", referenceFile.name)
                .put("classifier_report_file", "classifier_inference_report.txt")
                .put("source_file", sourceFile.name)
            File(evidenceDir, "android_inference_evidence.json").writeText(evidence.toString(2) + "\n")
            println("DET_FISH_V03_EVIDENCE_DIR=${evidenceDir.absolutePath}")
            println("DET_FISH_V03_EVIDENCE_JSON=${evidence.toString()}")

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

    private fun FloatArray.toLittleEndianBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in this) buffer.putFloat(value)
        return buffer.array()
    }

    private fun classifierInputTensorFromBitmap(bitmap: Bitmap): FloatArray {
        require(bitmap.width == 224 && bitmap.height == 224)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pixelCount = pixels.size
        val values = FloatArray(pixelCount * 3)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            values[index] = (red / 255f - 0.485f) / 0.229f
            values[pixelCount + index] = (green / 255f - 0.456f) / 0.224f
            values[pixelCount * 2 + index] = (blue / 255f - 0.406f) / 0.225f
        }
        return values
    }

    private fun Bitmap.writePng(file: File) {
        FileOutputStream(file).use { output ->
            check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to write ${file.name}" }
        }
    }

    private fun tensorDiffJson(expected: FloatArray, actual: FloatArray): JSONObject {
        require(expected.size == actual.size)
        val differences = expected.indices.map { index -> index to abs(expected[index] - actual[index]) }
        val largest = org.json.JSONArray()
        differences.sortedByDescending { it.second }.take(20).forEach { (index, difference) ->
            largest.put(JSONObject()
                .put("index", index)
                .put("absolute_error", difference.toDouble())
                .put("expected", expected[index].toDouble())
                .put("actual", actual[index].toDouble()))
        }
        return JSONObject()
            .put("element_count", expected.size)
            .put("max_abs_error", differences.maxOfOrNull { it.second }?.toDouble() ?: 0.0)
            .put("mean_abs_error", differences.sumOf { it.second.toDouble() } / expected.size)
            .put("largest_20_differences", largest)
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
        const val BBOX_MAX_ABS_TOLERANCE = 0.015f
        const val DETECTOR_CONFIDENCE_MAX_ABS_TOLERANCE = 0.02f
        const val CROP_EDGE_MAX_DELTA_PX = 24
        const val INPUT_MAX_ABS_TOLERANCE = 2.0f
        const val INPUT_MEAN_ABS_TOLERANCE = 0.05f
        const val OUTPUT_MAX_ABS_TOLERANCE = 0.05f
        const val OUTPUT_MEAN_ABS_TOLERANCE = 0.001f
    }
}
