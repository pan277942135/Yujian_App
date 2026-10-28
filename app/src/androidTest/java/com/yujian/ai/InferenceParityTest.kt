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

/** Exercises the exact packaged production model and checks its dynamic class contract. */
@RunWith(AndroidJUnit4::class)
class InferenceParityTest {

    @Test
    fun currentProductionReleaseRunsAndMapsEveryOutputToItsPackagedClassOrder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val bitmap = testContext.assets.open("golden_yellow_catfish_224.jpg").use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) { "golden image decode failed" }
        }
        val engine = FishRecognitionEngine(targetContext)
        val modelInfo = engine.modelInfo
        val prediction = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
            bitmap.recycle()
        }

        assertTrue(modelInfo.modelId.isNotBlank())
        assertTrue(modelInfo.datasetId.isNotBlank())
        assertTrue(modelInfo.releaseTag.isNotBlank())
        assertEquals(modelInfo.sha256, prediction.modelSha256)
        assertEquals(modelInfo.modelId, prediction.modelVersion)
        assertNotNull(prediction.modelInputBitmap)
        val expectedInputHeight = if (modelInfo.inputShape[1] == 3) modelInfo.inputShape[2] else modelInfo.inputShape[1]
        val expectedInputWidth = if (modelInfo.inputShape.last() == 3) modelInfo.inputShape[2] else modelInfo.inputShape.last()
        assertEquals(expectedInputWidth, requireNotNull(prediction.modelInputBitmap).width)
        assertEquals(expectedInputHeight, requireNotNull(prediction.modelInputBitmap).height)
        assertEquals(modelInfo.classCount, prediction.candidates.size)
        assertEquals(modelInfo.classOrder, prediction.candidates.sortedBy { it.classIndex }.map { it.speciesKey })
        assertEquals(modelInfo.classes.map { it.classIndex }, prediction.candidates.sortedBy { it.classIndex }.map { it.classIndex })
        assertTrue(prediction.candidates.all { it.confidence.isFinite() && it.confidence in 0f..1f })
        assertEquals(1f, prediction.candidates.sumOf { it.confidence.toDouble() }.toFloat(), 0.001f)
    }
}
