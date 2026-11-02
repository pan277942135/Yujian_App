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
    fun heroTransitionExpandsAndSettlesOnTheShared580msClock() {
        assertEquals(300L, CaptureNavigationTransition.HeroExpandMs)
        assertEquals(280L, CaptureNavigationTransition.HeroShrinkMs)
        assertEquals(580L, CaptureNavigationTransition.HeroTotalTransitionMs)
        assertEquals(1.22f, CaptureNavigationTransition.HeroPeakScale, 0.0001f)
        assertEquals(1f, CaptureNavigationTransition.transitionScale(0L), 0.0001f)
        assertEquals(1.055f, CaptureNavigationTransition.transitionScale(150L), 0.0001f)
        assertEquals(1.22f, CaptureNavigationTransition.transitionScale(300L), 0.0001f)
        assertEquals(1.055f, CaptureNavigationTransition.transitionScale(440L), 0.0001f)
        assertEquals(1f, CaptureNavigationTransition.transitionScale(580L), 0.0001f)
        assertEquals(1f, CaptureNavigationTransition.transitionScale(700L), 0.0001f)

        val expand = (0L..300L step 10L).map(CaptureNavigationTransition::transitionScale)
        assertTrue(expand.zipWithNext().all { (a, b) -> b >= a })
        val shrink = (300L..580L step 10L).map(CaptureNavigationTransition::transitionScale)
        assertTrue(shrink.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun idleGoldFlareSweepsOnceThenRestsAtTheMorningLightEntry() {
        assertEquals(10_500, CaptureVisualTokens.IdleCycleMs)
        assertEquals(2_500, CaptureVisualTokens.IdleGlintMs)
        assertEquals(-45f, captureIdleFlareAngleDegrees(0L), 0.0001f)
        assertEquals(135f, captureIdleFlareAngleDegrees(1_250L), 0.0001f)
        assertEquals(-45f, captureIdleFlareAngleDegrees(2_500L), 0.0001f)
        assertEquals(-45f, captureIdleFlareAngleDegrees(10_500L), 0.0001f)
    }

    @Test
    fun clickWaterRippleUses600msAnd36dpOutwardTravel() {
        assertEquals(600L, CaptureVisualTokens.ClickRippleDurationMs)
        assertEquals(36f, CaptureVisualTokens.ClickRippleSpreadDp, 0.0001f)
        assertEquals(0f, CaptureVisualTokens.activationProgress(0L), 0.0001f)
        assertEquals(.5f, CaptureVisualTokens.activationProgress(300L), 0.0001f)
        assertEquals(1f, CaptureVisualTokens.activationProgress(600L), 0.0001f)
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
