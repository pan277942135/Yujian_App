package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.waterlight.renderer.GlBloomExecution
import com.yujian.ai.ui.waterlight.renderer.RenderPassStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage045CompositeContractTest {
    @Test fun surfaceViewportSurvivesDownsampleForPortraitAndLandscape() {
        assertEquals(1080 to 2400, stage045OutputViewport(1080, 2400))
        assertEquals(2400 to 1080, stage045OutputViewport(2400, 1080))
    }

    @Test fun gpuPassRequiresRealCompositeAndBlurOnlyWhenRequested() {
        val pass = GlBloomExecution(RenderPassStatus.PASS, RenderPassStatus.PASS,
            RenderPassStatus.PASS, RenderPassStatus.PASS)
        assertTrue(stage045CompositeSucceeded(pass, false))
        assertFalse(stage045CompositeSucceeded(pass.copy(composite = RenderPassStatus.FAIL), false))
        assertFalse(stage045CompositeSucceeded(pass.copy(blurHorizontal = RenderPassStatus.FAIL), false))
        val skipped = GlBloomExecution(RenderPassStatus.NOT_RUN, RenderPassStatus.NOT_RUN,
            RenderPassStatus.NOT_RUN, RenderPassStatus.PASS)
        assertTrue(stage045CompositeSucceeded(skipped, true))
        assertFalse(stage045CompositeSucceeded(skipped.copy(failureReason = "COMPOSITE_ERROR"), true))
    }
}
