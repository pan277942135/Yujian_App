package com.yujian.ai

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.InferenceTrace
import com.yujian.ai.ai.RecognitionPhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import kotlin.math.abs

/** Held-out Case B detector regression; the source file is copied byte-for-byte. */
@RunWith(AndroidJUnit4::class)
class DetectorOrientationRetryRuntimeTest {
    @Test
    fun caseBNightFlashSelectsCcwMapsBackAndReachesClassifier() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val bytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(CASE_B_SOURCE_SHA256, bytes.sha256())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        val source = requireNotNull(bitmap)
        assertEquals(1152, source.width)
        assertEquals(1536, source.height)

        val phases = mutableListOf<RecognitionPhase>()
        val pipeline = FishRecognitionPipeline(targetContext)
        val result = try {
            pipeline.recognize(source) { phases += it.phase }
        } finally {
            pipeline.close()
        }

        assertEquals("CCW90", result.detectorRun.selectedAttempt)
        assertEquals(
            listOf("ORIGINAL", "CW90", "CCW90"),
            result.detectorRun.attemptTrace.map { it.orientationAttempt },
        )
        assertEquals(0, result.detectorRun.attemptTrace[0].detectionCount)
        assertTrue(result.detectorRun.attemptTrace[2].detectionCount > 0)
        assertTrue(result.detectorRun.selectionReason.isNotBlank())
        assertTrue(result.detectorRun.attemptTrace.all { it.qualityStatus.isNotBlank() && it.qualityLevel.isNotBlank() })
        assertEquals(
            "12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f",
            result.detectorRun.onnxSha256,
        )

        val box = requireNotNull(result.assessment.primary).box.normalized()
        // Acceptance tolerance: each normalized coordinate ±0.015, area ±0.02,
        // and each original-source crop edge ±24 pixels.
        assertEquals(0.342930f, box.x1, 0.015f)
        assertEquals(0.363650f, box.y1, 0.015f)
        assertEquals(0.605436f, box.x2, 0.015f)
        assertEquals(0.924975f, box.y2, 0.015f)
        assertEquals(0.147351f, requireNotNull(result.assessment.bboxAreaRatio), 0.02f)
        val actualCrop = requireNotNull(result.cropPixels)
        intArrayOf(349, 429, 743, 1536).forEachIndexed { index, expected ->
            assertTrue(abs(expected - actualCrop[index]) <= 24)
        }
        assertTrue("Classifier pipeline did not reach CLASSIFYING", phases.contains(RecognitionPhase.CLASSIFYING))

        val trace = InferenceTrace.lastReport
        assertTrue(trace.contains("original_size=1152x1536"))
        assertTrue(trace.contains("selected_attempt=CCW90"))
        assertTrue(trace.contains("detector_bbox_normalized="))
        assertTrue(trace.contains("crop_pixels="))
        assertTrue(trace.contains("classifier_source=DETECTOR_CROP"))
        source.recycle()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val CASE_B_SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
