package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionImageTransformTest {
    @Test
    fun fitMapsPortraitImageWithoutStretching() {
        val transform = DisplayedImageTransform.fit(100, 200, 200f, 200f)
        val rect = transform.map(NormalizedFishBox(0.1f, 0.2f, 0.5f, 0.8f))
        assertEquals(50f, transform.left, 0.001f)
        assertEquals(60f, rect.left, 0.001f)
        assertEquals(100f, rect.right, 0.001f)
        assertEquals(40f, rect.top, 0.001f)
    }

    @Test
    fun cropMapsLandscapeImageWithCenteredOverflow() {
        val transform = DisplayedImageTransform.crop(200, 100, 100f, 200f)
        val rect = transform.map(NormalizedFishBox(0.25f, 0.1f, 0.5f, 0.4f))
        assertEquals(-150f, transform.left, 0.001f)
        assertEquals(-50f, rect.left, 0.001f)
        assertEquals(50f, rect.right, 0.001f)
        assertEquals(20f, rect.top, 0.001f)
        assertEquals(80f, rect.bottom, 0.001f)
    }

    @Test
    fun rotationContractCoversPortraitLandscapeAndQuarterTurns() {
        val box = NormalizedFishBox(0.1f, 0.2f, 0.4f, 0.8f)
        assertBoxEquals(NormalizedFishBox(0.2f, 0.1f, 0.8f, 0.4f), box.rotateClockwise(90).normalized())
        assertBoxEquals(NormalizedFishBox(0.6f, 0.2f, 0.9f, 0.8f), box.rotateClockwise(180).normalized())
        assertBoxEquals(NormalizedFishBox(0.2f, 0.6f, 0.8f, 0.9f), box.rotateClockwise(270).normalized())
    }

    private fun assertBoxEquals(expected: NormalizedFishBox, actual: NormalizedFishBox) {
        assertEquals(expected.x1, actual.x1, 0.001f)
        assertEquals(expected.y1, actual.y1, 0.001f)
        assertEquals(expected.x2, actual.x2, 0.001f)
        assertEquals(expected.y2, actual.y2, 0.001f)
    }
}
