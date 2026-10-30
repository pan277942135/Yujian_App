package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionPhotoFlowGeometryTest {
    @Test
    fun galleryPortraitStartsContainedNearFortyFourPercentAndSettlesToCompactFit() {
        val plan = planRecognitionPhotoFlow(
            sourceWidth = 1200,
            sourceHeight = 1800,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2340f,
            density = 3f,
            safeLeftPx = 0f,
            safeTopPx = 72f,
            safeRightPx = 0f,
            safeBottomPx = 72f,
            cameraSource = false,
        )
        val contentHeight = 2340f - 72f - 72f
        val initial = plan.startTransform.photoContentRect
        val target = plan.targetTransform.photoContentRect

        assertEquals(RecognitionContentScaleMode.FIT, plan.startTransform.contentScaleMode)
        assertEquals(RecognitionContentScaleMode.FIT, plan.targetTransform.contentScaleMode)
        assertEquals(1200, plan.targetTransform.sourceWidthPx)
        assertEquals(1800, plan.targetTransform.sourceHeightPx)
        assertTrue(initial.width <= (1080f - 96f) * .88f + 1f)
        assertTrue(initial.height <= contentHeight * .68f + 1f)
        assertEquals(72f + contentHeight * .44f, initial.center.y, 1f)
        assertEquals(540f, initial.center.x, 1f)
        assertTrue(target.height <= contentHeight * .56f + 1f)
        assertEquals(72f + 48f * 3f + 16f * 3f, target.top, 1f)
        assertEquals(0f, plan.targetTransform.visibleSourceRect.left, 0f)
        assertEquals(0f, plan.targetTransform.visibleSourceRect.top, 0f)
        assertEquals(1f, plan.targetTransform.visibleSourceRect.right, 0f)
        assertEquals(1f, plan.targetTransform.visibleSourceRect.bottom, 0f)
        assertTrue(plan.statusPanelTopPx >= target.bottom + 20f * 3f - 1f)
    }

    @Test
    fun cameraEntryUsesFullPreviewFillCropThenTransitionsToTheSameSourceFit() {
        val plan = planRecognitionPhotoFlow(
            sourceWidth = 1600,
            sourceHeight = 1200,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2340f,
            density = 3f,
            safeLeftPx = 0f,
            safeTopPx = 72f,
            safeRightPx = 0f,
            safeBottomPx = 72f,
            cameraSource = true,
        )

        assertEquals(RecognitionContentScaleMode.CROP, plan.startTransform.contentScaleMode)
        assertEquals(1080f, plan.startTransform.photoContentRect.width, .5f)
        assertEquals(2340f, plan.startTransform.photoContentRect.height, .5f)
        assertEquals(RecognitionContentScaleMode.FIT, plan.targetTransform.contentScaleMode)
        assertEquals(0f, plan.targetTransform.visibleSourceRect.left, 0f)
        assertEquals(1f, plan.targetTransform.visibleSourceRect.right, 0f)
    }

    @Test
    fun entryGeometryInterpolatesOnePhotoContinuouslyThroughTheExactHalfwayFrame() {
        val plan = planRecognitionPhotoFlow(
            sourceWidth = 4000,
            sourceHeight = 3000,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2340f,
            density = 3f,
            safeLeftPx = 0f,
            safeTopPx = 72f,
            safeRightPx = 0f,
            safeBottomPx = 72f,
            cameraSource = false,
        )
        val start = plan.startTransform
        val target = plan.targetTransform
        val halfway = interpolateRecognitionPhotoTransform(start, target, .5f)

        assertEquals((start.translationX + target.translationX) * .5f, halfway.translationX, .5f)
        assertEquals((start.translationY + target.translationY) * .5f, halfway.translationY, .5f)
        assertEquals((start.scale + target.scale) * .5f, halfway.scale, .0001f)
        assertEquals(start.translationX, interpolateRecognitionPhotoTransform(start, target, 0f).translationX, 0f)
        assertEquals(target.translationX, interpolateRecognitionPhotoTransform(start, target, 1f).translationX, 0f)
    }
}
