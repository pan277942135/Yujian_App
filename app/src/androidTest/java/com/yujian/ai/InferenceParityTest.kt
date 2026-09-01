package com.yujian.ai

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Golden-image parity for the approved MODEL_M1_v0.2 Android preprocessing contract.
 * The fixture is already 224×224; production still executes its whole-image letterbox
 * path, NCHW RGB packing, and ImageNet normalization before these asserted logits.
 */
@RunWith(AndroidJUnit4::class)
class InferenceParityTest {

    @Test
    fun modelM1GoldenYellowCatfishParityPasses() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val bitmap = testContext.assets.open("golden_yellow_catfish_224.jpg").use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) { "golden image decode failed" }
        }
        require(bitmap.width == 224 && bitmap.height == 224) {
            "golden image must be 224x224, got ${bitmap.width}x${bitmap.height}"
        }

        val engine = FishRecognitionEngine(targetContext)
        val prediction = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
            bitmap.recycle()
        }

        assertEquals(FishRecognitionEngine.MODEL_VERSION, prediction.modelVersion)
        assertEquals(FishRecognitionEngine.MODEL_SHA256, prediction.modelSha256)
        assertNotNull(prediction.modelInputBitmap)
        assertEquals(224, requireNotNull(prediction.modelInputBitmap).width)
        assertEquals(224, requireNotNull(prediction.modelInputBitmap).height)
        assertEquals(9, prediction.candidates.size)
        val top3 = prediction.candidates.take(3)
        assertEquals(listOf(2, 4, 1), top3.map { it.classIndex })
        assertEquals(listOf("silver_carp", "crucian_carp", "bighead_carp"), top3.map { it.speciesKey })
        val expected = floatArrayOf(0.137937f, 0.132004f, 0.111724f)
        top3.zip(expected.asList()).forEach { (candidate, expectedConfidence) ->
            assertEquals(expectedConfidence, candidate.confidence, 0.0002f)
        }
        assertTrue(prediction.candidates.all { it.confidence.isFinite() && it.confidence in 0f..1f })
    }
}
