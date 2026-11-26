package com.yujian.ai

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.ai.RecognitionModelIdentity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/** Device gate: the packaged classifier must execute through its actual TFLite tensor names. */
class ClassifierRealTfliteTensorBindingTest {
    @Test
    fun packagedClassifierRunsWithFrozenTensorBinding() {
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("fish_classifier.tflite").use { it.readBytes() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals("29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18", sha)
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        Interpreter(model).use { interpreter ->
            val input = interpreter.getInputTensor(0)
            val output = interpreter.getOutputTensor(0)
            assertEquals("serving_default_args_0", input.name())
            assertEquals("serving_default_output_0_output", output.name())
            assertTrue(input.shape().contentEquals(intArrayOf(1, 3, 224, 224)))
            assertTrue(output.shape().contentEquals(intArrayOf(1, 15)))
            assertEquals(DataType.FLOAT32, input.dataType())
            assertEquals(DataType.FLOAT32, output.dataType())
            val inputBytes = ByteBuffer.allocateDirect(1 * 3 * 224 * 224 * 4).order(ByteOrder.nativeOrder())
            val outputBytes = ByteBuffer.allocateDirect(15 * 4).order(ByteOrder.nativeOrder())
            interpreter.run(inputBytes, outputBytes)
            outputBytes.rewind()
            val logits = FloatArray(15) { outputBytes.float }
            assertTrue(logits.all(Float::isFinite))
            assertTrue(logits.indices.maxBy { logits[it] } in 0..14)
        }
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val pixels = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        val photoSha = MessageDigest.getInstance("SHA-256").digest(pixels.array())
            .joinToString("") { "%02x".format(it) }
        FishRecognitionEngine(InstrumentationRegistry.getInstrumentation().targetContext).use { engine ->
            val prediction = runBlocking {
                engine.recognize(
                    bitmap,
                    modelIdentity = RecognitionModelIdentity(
                        sessionId = "classifier-binding-test-session",
                        attemptId = "classifier-binding-test-attempt",
                        imageId = "classifier-binding-test-photo",
                        photoSha256 = photoSha,
                    ),
                )
            }
            assertTrue(prediction.top1.classIndex in 0..14)
        }
    }
}
