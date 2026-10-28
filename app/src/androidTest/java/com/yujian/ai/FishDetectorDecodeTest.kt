package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishDetectorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishDetectorDecodeTest {
    @Test
    fun decodeUsesObjectnessTimesFishProbability() {
        val rows = arrayOf(floatArrayOf(208f, 208f, 100f, 100f, .9f, .9f))
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        val detection = detections.single()
        assertEquals(.81f, detection.confidence, 1e-6f)
        assertEquals(158f / 416f, detection.box.x1, 1e-6f)
        assertEquals(258f / 416f, detection.box.x2, 1e-6f)
    }

    @Test
    fun weakThresholdFiltersRowsBeforeNms() {
        val rows = arrayOf(
            floatArrayOf(208f, 208f, 100f, 100f, .9f, .9f),
            floatArrayOf(120f, 120f, 50f, 50f, .2f, .5f),
        )
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        assertTrue(detections.single().confidence >= .2f)
    }

    @Test
    fun nmsKeepsHigherConfidenceOverlappingFish() {
        val rows = arrayOf(
            floatArrayOf(208f, 208f, 120f, 120f, .95f, .95f),
            floatArrayOf(211f, 211f, 120f, 120f, .80f, .90f),
        )
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        assertEquals(.9025f, detections.single().confidence, 1e-6f)
    }
}
