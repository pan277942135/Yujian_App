package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.feedback.FeedbackRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProductionE2ESmokeTest {

    @Test
    fun productionModelInferenceAndFeedbackTransport_areLive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(72, 118, 94))
        }
        val engine = FishRecognitionEngine(context)
        val prediction = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
        }

        assertEquals(FishRecognitionEngine.MODEL_SHA256, prediction.modelSha256)
        assertEquals(FishRecognitionEngine.MODEL_VERSION, prediction.modelVersion)
        assertEquals(9, prediction.candidates.size)
        assertTrue(prediction.top1.classIndex in 0..8)
        assertTrue(prediction.top1.confidence.isFinite())
        assertTrue(prediction.top1.confidence in 0f..1f)
        assertTrue(prediction.candidates.all { it.confidence.isFinite() && it.confidence in 0f..1f })

        val image = File(context.cacheDir, "uat_feedback_smoke_${System.currentTimeMillis()}.jpg")
        try {
            image.outputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
            }
            val feedback = FeedbackRepository(context)
            assertTrue("UAT feedback BuildConfig must be injected", feedback.isNetworkConfigured())
            assertTrue(
                "Authenticated Android multipart feedback smoke must reach the UAT backend",
                feedback.submitUatSmoke(image, "android_uat_${System.currentTimeMillis()}"),
            )
        } finally {
            image.delete()
            bitmap.recycle()
        }
    }
}
