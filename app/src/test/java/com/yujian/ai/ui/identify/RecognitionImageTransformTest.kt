package com.yujian.ai.ui.identify

import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionImageTransformTest {
    @Test
    fun cropTransformMapsPortraitBoxWithLetterboxOffset() {
        val transform = calculateRecognitionImageTransform(1000f, 1000f, 1000, 2000)
        val center = transform.mapBox(NormalizedFishBox(0.2f, 0.25f, 0.6f, 0.75f))

        assertEquals(400f, center.x, 0.01f)
        assertEquals(500f, center.y, 0.01f)
        assertEquals(0f, transform.offsetX, 0.01f)
        assertEquals(-500f, transform.offsetY, 0.01f)
    }

    @Test
    fun cropTransformMapsLandscapeBoxWithVerticalOffset() {
        val transform = calculateRecognitionImageTransform(1000f, 1000f, 2000, 1000)
        val center = transform.mapBox(NormalizedFishBox(0.25f, 0.25f, 0.75f, 0.75f))

        assertEquals(500f, center.x, 0.01f)
        assertEquals(500f, center.y, 0.01f)
        assertEquals(-500f, transform.offsetX, 0.01f)
        assertEquals(0f, transform.offsetY, 0.01f)
    }
}
