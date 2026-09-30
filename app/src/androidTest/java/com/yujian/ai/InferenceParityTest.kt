package com.yujian.ai

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Golden-image parity for the currently published mobile-model-v0.2 classifier.
 * Bootstrap packages the Release parity report alongside the Android test APK so
 * this gate follows the production model without hardcoding a previous release's
 * logits or SHA in source.
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

        val releaseParity = testContext.assets.open("model_release_parity_report.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        assertEquals("numerical_runtime_parity", releaseParity.getString("gate_type"))

        val engine = FishRecognitionEngine(targetContext)
        val expectedModelSha = engine.modelSha256
        val expectedModelVersion = engine.modelVersion
        val expectedClassCount = engine.modelClassCount
        val prediction = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
            bitmap.recycle()
        }

        assertEquals(expectedModelVersion, prediction.modelVersion)
        assertEquals(expectedModelSha, prediction.modelSha256)
        assertNotNull(prediction.modelInputBitmap)
        assertEquals(224, requireNotNull(prediction.modelInputBitmap).width)
        assertEquals(224, requireNotNull(prediction.modelInputBitmap).height)
        assertEquals(expectedClassCount, prediction.candidates.size)

        val expectedTop3 = releaseParity.getJSONArray("tflite_top3")
        val top3 = prediction.candidates.take(expectedTop3.length())
        assertEquals(expectedTop3.length(), top3.size)
        top3.forEachIndexed { index, candidate ->
            val expected = expectedTop3.getJSONObject(index)
            assertEquals(expected.getInt("index"), candidate.classIndex)
            assertEquals(expected.getDouble("probability").toFloat(), candidate.confidence, 0.0005f)
        }
        assertTrue(prediction.candidates.all { it.confidence.isFinite() && it.confidence in 0f..1f })
    }
}
