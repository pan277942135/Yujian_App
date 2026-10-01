package com.yujian.ai.ui.identify

import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecognitionImageTransformTest {
    @Test
    fun cropTransformMapsPortraitBoxWithLetterboxOffset() {
        val transform = calculateRecognitionImageTransform(
            containerWidth = 1000f,
            containerHeight = 1000f,
            imageWidth = 1000,
            imageHeight = 2000,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )
        val center = transform.mapBox(NormalizedFishBox(0.2f, 0.25f, 0.6f, 0.75f))

        assertEquals(400f, center.x, 0.01f)
        assertEquals(500f, center.y, 0.01f)
        assertEquals(0f, transform.offsetX, 0.01f)
        assertEquals(-500f, transform.offsetY, 0.01f)
        assertEquals(NormalizedSourceRect(0f, .25f, 1f, .75f), transform.visibleSourceRect)
        assertEquals(1000, transform.sourceWidthPx)
        assertEquals(2000, transform.sourceHeightPx)
    }

    @Test
    fun cropTransformMapsLandscapeBoxWithVerticalOffset() {
        val transform = calculateRecognitionImageTransform(
            1000f,
            1000f,
            2000,
            1000,
            RecognitionContentScaleMode.CROP,
        )
        val center = transform.mapBox(NormalizedFishBox(0.25f, 0.25f, 0.75f, 0.75f))

        assertEquals(500f, center.x, 0.01f)
        assertEquals(500f, center.y, 0.01f)
        assertEquals(-500f, transform.offsetX, 0.01f)
        assertEquals(0f, transform.offsetY, 0.01f)
        assertEquals(NormalizedSourceRect(.25f, 0f, .75f, 1f), transform.visibleSourceRect)
    }

    @Test
    fun handoffAndProcessingShareTheSameSourceTransformForIdenticalViewport() {
        val handoff = calculateRecognitionImageTransform(
            containerWidth = 1080f,
            containerHeight = 2340f,
            imageWidth = 1440,
            imageHeight = 2560,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )
        val processing = calculateRecognitionImageTransform(
            containerWidth = 1080f,
            containerHeight = 2340f,
            imageWidth = 1440,
            imageHeight = 2560,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )

        assertEquals(handoff, processing)
        assertEquals(1080f, handoff.viewportWidthPx, 0.01f)
        assertEquals(2340f, handoff.viewportHeightPx, 0.01f)
        assertEquals(RecognitionContentScaleMode.CROP, handoff.contentScaleMode)
    }

    @Test
    fun fitModePreservesFullSourceRectAndDiffersFromFrozenProcessingCrop() {
        val fit = calculateRecognitionImageTransform(
            containerWidth = 1000f,
            containerHeight = 1000f,
            imageWidth = 2000,
            imageHeight = 1000,
            contentScaleMode = RecognitionContentScaleMode.FIT,
        )
        val processing = calculateRecognitionImageTransform(
            containerWidth = 1000f,
            containerHeight = 1000f,
            imageWidth = 2000,
            imageHeight = 1000,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )

        assertEquals(NormalizedSourceRect(0f, 0f, 1f, 1f), fit.visibleSourceRect)
        assertEquals(250f, fit.translationY, 0.01f)
        assertEquals(0.5f, fit.scale, 0.01f)
        assertNotEquals(fit, processing)
    }
}
