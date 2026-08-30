package com.yujian.ai

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InferenceParityTest {

    @Test
    fun goldenYellowCatfish_matchesModelM1V02MobileBaseline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val bitmap = testContext.assets.open("golden_yellow_catfish_224.jpg").use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) { "golden image decode failed" }
        }

        val engine = FishRecognitionEngine(targetContext)
        val prediction = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
            bitmap.recycle()
        }

        val top3 = prediction.candidates.take(3)
        Log.i(
            "YujianParity",
            "model=${prediction.modelVersion} sha=${prediction.modelSha256} " +
                "top3=${top3.joinToString { "${it.classIndex}:${it.speciesKey}:${it.confidence}" }}",
        )

        assertEquals("MODEL_M1_v0.2", prediction.modelVersion)
        assertEquals(
            "9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e",
            prediction.modelSha256,
        )
        assertEquals(9, prediction.candidates.size)
        assertEquals(7, prediction.top1.classIndex)
        assertEquals("yellow_catfish", prediction.top1.speciesKey)
        assertEquals("黄骨鱼", prediction.top1.speciesName)

        // TorchScript MODEL_M1_v0.2 baseline for this exact 224x224 golden JPEG is 0.784299.
        // Android Canvas/Bitmap and LiteRT may introduce small interpolation/runtime deltas,
        // so this first device parity gate keeps a deliberately bounded tolerance.
        assertTrue(
            "yellow_catfish confidence outside parity band: ${prediction.top1.confidence}",
            prediction.top1.confidence in 0.70f..0.86f,
        )
        assertEquals(
            listOf("yellow_catfish", "snakehead", "largemouth_bass"),
            top3.map { it.speciesKey },
        )
    }
}
