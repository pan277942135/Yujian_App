package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureButtonMotionTest {
    @Test
    fun frozenCaptureBreathUsesFiveSecondCycleAndCapsAtOnePointZeroOneFive() {
        assertEquals(5_000, CaptureVisualTokens.IdleBreathCycleMs)
        assertEquals(1f, captureIdleBreathScale(0f), 0.0001f)
        assertEquals(1.015f, captureIdleBreathScale(0.5f), 0.0001f)
        assertEquals(1f, captureIdleBreathScale(1f), 0.0001f)

        (0..100).forEach { step ->
            val scale = captureIdleBreathScale(step / 100f)
            assertTrue("breath scale below rest size", scale >= 1f)
            assertTrue("breath scale exceeds the frozen maximum", scale <= 1.015f)
        }
    }

    @Test
    fun shutterDiameterStaysInsideTheFrozenResponsiveRange() {
        listOf(300f, 360f, 420f, 520f).forEach { viewportWidth ->
            val diameter = CaptureVisualTokens.buttonDiameter(viewportWidth)
            assertTrue(diameter >= 88f)
            assertTrue(diameter <= 96f)
        }
    }
}
