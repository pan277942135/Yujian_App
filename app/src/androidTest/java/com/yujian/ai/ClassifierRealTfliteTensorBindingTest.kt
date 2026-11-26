package com.yujian.ai

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.recognition.domain.CanonicalPhotoPixelsV1
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** API36 gate: run the packaged classifier through the production Detector → Crop → Classifier path. */
class ClassifierRealTfliteTensorBindingTest {
    @Test
    fun packagedClassifierRunsWithFrozenTensorBinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val photoBytes = testContext.assets.open(SOURCE_ASSET).use { it.readBytes() }
        val sourceSha = photoBytes.sha256()
        assertEquals(SOURCE_SHA256, sourceSha)

        val modelBytes = targetContext.assets.open(FishRecognitionEngine.MODEL_FILE).use { it.readBytes() }
        assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256, modelBytes.sha256())

        val selectedImage = runBlocking { RecognitionImageStore.normalizeAsset(testContext, SOURCE_ASSET) }
        val bitmap = selectedImage.bitmap
        val photoIdentity = requireNotNull(selectedImage.photoIdentity)
        val photoId = selectedImage.imageId
        val canonicalPixelSha = selectedImage.photoSha256
        assertEquals(sourceSha, photoIdentity.sourceBytesSha256)
        val rawExif = ExifInterface(ByteArrayInputStream(photoBytes))
        Log.i("YujianExifAudit", "classifier raw_orientation=${rawExif.getAttribute(ExifInterface.TAG_ORIENTATION)} parsed_orientation=${rawExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)} normalized_orientation=${selectedImage.sourceExifOrientation}")
        assertEquals(ExifInterface.ORIENTATION_NORMAL, selectedImage.sourceExifOrientation)
        assertEquals(selectedImage.sourceExifOrientation, photoIdentity.sourceOrientation)
        assertEquals(bitmap.width, photoIdentity.width)
        assertEquals(bitmap.height, photoIdentity.height)
        assertEquals(canonicalPixelSha, CanonicalPhotoPixelsV1.sha256(bitmap.width, bitmap.height) { row, pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, row, bitmap.width, 1)
        })
        assertTrue("fixture PhotoIdentity must be valid", photoIdentity.isValid())
        val identity = RecognitionModelIdentity(
            sessionId = "classifier-real-tflite-session",
            attemptId = "classifier-real-tflite-attempt",
            imageId = photoId,
            photoSha256 = canonicalPixelSha,
            photoIdentity = photoIdentity,
        )
        val diagnosticEvents = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()
        val pipeline = FishRecognitionPipeline(targetContext)
        try {
            val result = runBlocking {
                pipeline.recognize(
                    bitmap = bitmap,
                    onProgress = {},
                    modelIdentity = identity,
                    onDiagnostic = { eventId, _, status, facts, _ ->
                        if (status == "PASS") diagnosticEvents += eventId to facts
                    },
                )
            }

            assertTrue("production Detector must return a fish", result.detectorRun.detections.isNotEmpty())
            assertNotNull("production Crop must return crop pixels", result.cropPixels)
            assertEquals("production Crop identity records four pixel coordinates", 4, result.cropPixels?.size)
            assertTrue("quality gate must authorize the production classifier", result.assessment.isClassifierEligible)
            assertTrue("production pipeline must return a completed classifier result", result.ready)

            val prediction = requireNotNull(result.prediction)
            assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256, prediction.modelSha256)
            assertEquals("yellow_catfish", prediction.top1.speciesKey)
            assertTrue("top1 index must map to one of the 15 frozen classes", prediction.top1.classIndex in 0 until 15)
            assertTrue("night-catch confidence must remain HIGH", prediction.top1.confidence >= .55f)
            assertTrue("production result must not be low confidence", !prediction.lowConfidence)
            val modelInput = requireNotNull(prediction.modelInputBitmap)
            val sampledColors = (0 until modelInput.height step 16).flatMap { y ->
                (0 until modelInput.width step 16).map { x -> modelInput.getPixel(x, y) }
            }.toSet()
            assertTrue("classifier input must come from the real cropped photo, not a zero-filled tensor", sampledColors.size > 1)

            val inputReceipt = requireNotNull(prediction.classifierInputReceipt) {
                "production classifier did not return its validated Input Receipt"
            }
            val outputReceipt = requireNotNull(prediction.classifierOutputReceipt) {
                "production classifier did not return its validated Output Receipt"
            }
            assertTrue(inputReceipt.isValid())
            assertTrue(outputReceipt.isValid())
            assertEquals(identity.sessionId, inputReceipt.recognitionSessionId)
            assertEquals(identity.attemptId, inputReceipt.attemptId)
            assertEquals(identity.imageId, inputReceipt.photoId)
            assertEquals(canonicalPixelSha, inputReceipt.canonicalPixelSha256)
            assertEquals(inputReceipt.inferenceId, outputReceipt.inferenceId)
            assertEquals(inputReceipt.modelIdentity, outputReceipt.modelIdentity)
            assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256, inputReceipt.modelIdentity.runtimeVerifiedModelSha256)
            assertEquals("serving_default_args_0", inputReceipt.tensorName)
            assertEquals(listOf(1L, 3L, 224L, 224L), inputReceipt.shape)
            assertEquals("FLOAT32", inputReceipt.dataType)
            assertEquals(602112L, inputReceipt.byteSize)
            assertEquals("serving_default_output_0_output", outputReceipt.tensorName)
            assertEquals(listOf(1L, 15L), outputReceipt.shape)
            assertEquals("FLOAT32", outputReceipt.dataType)
            assertEquals(60L, outputReceipt.byteSize)
            assertTrue("Input Receipt must contain a SHA256 tensor content hash",
                "[a-fA-F0-9]{64}".toRegex().matches(inputReceipt.contentHash))
            assertTrue("Output Receipt must contain a SHA256 tensor content hash",
                "[a-fA-F0-9]{64}".toRegex().matches(outputReceipt.contentHash))

            val receiptAccepted = diagnosticEvents.singleOrNull { it.first == "CLASSIFIER_RECEIPTS_ACCEPTED" }
            assertNotNull("production result consumer must accept the exact classifier receipt pair", receiptAccepted)
            assertEquals(inputReceipt.inferenceId, receiptAccepted?.second?.get("inference_id"))
            assertEquals(inputReceipt.contentHash, receiptAccepted?.second?.get("input_content_sha256"))
            assertEquals(outputReceipt.contentHash, receiptAccepted?.second?.get("output_content_sha256"))
            assertTrue(diagnosticEvents.any { it.first == "DETECTOR_END" })
            assertTrue(diagnosticEvents.any { it.first == "CROP_SUCCESS" })
            assertTrue(diagnosticEvents.any { it.first == "CLASSIFIER_END" })
        } finally {
            pipeline.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ASSET = "detector_case_b_night_flash.jpg"
        const val SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
