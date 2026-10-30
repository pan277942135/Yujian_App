package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.identify.DisplayRect
import org.junit.Assert.*
import org.junit.Test

class ProcessingLakePresentationTest {
    @Test fun photosAndOrbStayInsideSixSupportedScreens() {
        val screens = listOf(320 to 480, 360 to 640, 390 to 844, 412 to 915, 430 to 932, 800 to 360)
        val sources = listOf(1200 to 1800, 4000 to 3000, 3000 to 1000, 1000 to 4000, 1200 to 1200)
        for ((w, h) in screens) for ((sw, sh) in sources) for (fontScale in listOf(1f, 1.5f)) {
            val p = planRecognitionPhotoFlow(sw, sh, w.toFloat(), h.toFloat(), 1f, 0f, 24f, 0f, 24f, false, fontScale = fontScale)
            val r = p.targetPhotoRect
            assertEquals("full source $w x $h", 0f, p.targetTransform.visibleSourceRect.left, .001f)
            assertEquals(1f, p.targetTransform.visibleSourceRect.bottom, .001f)
            assertEquals(sw.toFloat() / sh, r.width / r.height, .002f)
            // FIT uses Float arithmetic; 0.001px is below a drawable pixel at every density.
            assertTrue("photo margins $w x $h source=$sw x $sh rect=$r", r.left >= 12f - .001f && r.right <= w - 12f + .001f)
            assertTrue(r.top >= 24f + 44f)
            val halfOrb = p.orbCoreSizePx / 2f + 14f
            assertTrue("orb bottom $w x $h", p.orbCenterYPx + halfOrb <= h - 24f)
            if (!p.landscape) assertTrue(p.statusPanelTopPx >= r.bottom + 20f)
            else assertTrue(p.statusCenterXPx - halfOrb > r.right)
        }
    }

    @Test fun cameraStartsAtMeasuredPreviewAndKeepsSameSourceAtMidpoint() {
        val preview = DisplayRect(0f, 24f, 390f, 650f)
        val p = planRecognitionPhotoFlow(1170, 1878, 390f, 844f, 1f, 0f, 24f, 0f, 24f, true, preview)
        assertEquals(preview.left, p.startTransform.translationX, .01f)
        assertEquals(preview.top, p.startTransform.translationY, .01f)
        assertEquals(preview.width, p.startTransform.drawnWidth, .01f)
        assertEquals(preview.height, p.startTransform.drawnHeight, .01f)
        val mid = interpolateRecognitionPhotoTransform(p.startTransform, p.targetTransform, .5f)
        assertEquals(1170, mid.sourceWidthPx)
        assertEquals((p.startTransform.translationY + p.targetTransform.translationY) / 2f, mid.translationY, .001f)
    }

    @Test fun buttonWaterAndRibbonUseOneEntranceClockAndRespectReducedMotion() {
        val start = processingOrbMotionFrame(0, true, false, 0f)
        assertEquals(0f, start.waterAlpha, 0f)
        assertEquals(0f, start.destinationProgress, 0f)
        assertEquals(1f, processingOrbMotionFrame(350, true, false, 0f).waterAlpha, 0f)
        assertEquals(1f, processingOrbMotionFrame(650, true, false, 0f).ribbonOpening, 0f)
        assertEquals(.5f, processingOrbMotionFrame(450, true, false, 0f).destinationProgress, .001f)
        assertEquals(1f, processingOrbMotionFrame(900, true, false, 0f).destinationProgress, 0f)
        assertEquals(0f, processingOrbMotionFrame(900, true, false, 1f).completionAlpha, 0f)
        val reduced = processingOrbMotionFrame(0, true, true, 0f)
        assertEquals(1f, reduced.waterAlpha, 0f)
        assertEquals(1f, reduced.destinationProgress, 0f)
    }
}
