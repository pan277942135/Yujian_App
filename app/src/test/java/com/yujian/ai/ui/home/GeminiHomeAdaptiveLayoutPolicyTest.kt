package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiHomeAdaptiveLayoutPolicyTest {
    @Test
    fun candidateBreakpointsEvaluateWidthAndHeightIndependently() {
        assertEquals(GeminiHomeScreenBand.COMPACT, resolve(320f, 915f).band)
        assertEquals(GeminiHomeScreenBand.COMPACT, resolve(393f, 640f).band)
        assertEquals(GeminiHomeScreenBand.MEDIUM, resolve(393f, 852f).band)
        assertEquals(GeminiHomeScreenBand.EXPANDED, resolve(411f, 915f).band)
        assertEquals(GeminiHomeScreenBand.EXPANDED, resolve(560f, 900f).band)
    }

    @Test
    fun adaptiveCardUsesCandidateWidthCapAndFrozenAspectRatio() {
        val compact = resolve(320f, 640f)
        val medium = resolve(393f, 852f)
        val expanded = resolve(411f, 915f)

        assertEquals(0.76f, compact.cardWidthFraction, 0.0001f)
        assertEquals(360f, expanded.maxCardWidth.value, 0.001f)
        assertEquals(320f * 0.76f, geminiHomeCardWidth(320f, compact), 0.001f)
        assertEquals(393f * 0.76f, geminiHomeCardWidth(393f, medium), 0.001f)
        assertEquals(411f * 0.76f, geminiHomeCardWidth(411f, expanded), 0.001f)
        assertEquals(247f / 293f, 360f / expanded.maxCardHeight.value, 0.001f)
    }

    @Test
    fun fontScaleReducesOnlyBoundedSpacingAndKeepsSystemTextScaleAvailable() {
        val compactDefault = resolve(320f, 640f, 1.0f)
        val compactLarge = resolve(320f, 640f, 1.3f)
        val compactLargest = resolve(320f, 640f, 1.5f)
        val mediumLargest = resolve(393f, 852f, 1.5f)
        val expandedLargest = resolve(411f, 915f, 1.5f)

        assertTrue(compactDefault.brandToStatsGap!!.value > compactLarge.brandToStatsGap!!.value)
        assertTrue(compactLarge.statsToTitleGap!!.value > compactLargest.statsToTitleGap!!.value)
        assertEquals(12f, compactLargest.brandToStatsGap!!.value, 0.001f)
        assertEquals(10f, compactLargest.titleToCardGap.value, 0.001f)
        assertEquals(16f, mediumLargest.brandToStatsGap!!.value, 0.001f)
        assertEquals(12f, mediumLargest.titleToCardGap.value, 0.001f)
        assertEquals(18f, expandedLargest.brandToStatsGap!!.value, 0.001f)
        assertEquals(14f, expandedLargest.titleToCardGap.value, 0.001f)
    }

    @Test
    fun adaptiveLayoutAnchorsTheMiddleGroupAndBaselineRemainsComparable() {
        val adaptive = resolve(393f, 852f, 1.0f)
        val baseline = resolve(
            widthDp = 393f,
            heightDp = 852f,
            fontScale = 1.0f,
            variant = GeminiHomeLayoutVariant.V3_BASELINE,
        )

        assertTrue(adaptive.topAlignMiddleGroup)
        assertFalse(baseline.topAlignMiddleGroup)
        assertEquals(0.76f, adaptive.cardWidthFraction, 0.0001f)
        assertEquals(0.686f, baseline.cardWidthFraction, 0.0001f)
        assertEquals(13f, baseline.titleToCardGap.value, 0.001f)
    }

    @Test
    fun cappedHeroKeepsAVisibleRealNeighborPreviewAcrossWidths() {
        val profiles = listOf(
            Triple(320f, 243.2f, 10.24f),
            Triple(393f, 298.68f, 12.576f),
            Triple(411f, 312.36f, 13.152f),
            Triple(480f, 360f, 15.36f),
            Triple(560f, 360f, 16f),
        )

        profiles.forEach { (trackWidth, cardWidth, pageSpacing) ->
            val neighborWidth = geminiHomeNeighborWidth(trackWidth, cardWidth, pageSpacing)
            val visiblePeek = ((trackWidth - 2f * cardWidth + neighborWidth) / 2f - pageSpacing)
                .coerceAtLeast(0f)
            assertTrue("neighbor preview remains visible at ${trackWidth}dp: $visiblePeek dp", visiblePeek >= 12f)
            assertTrue("neighbor remains no wider than the main card", neighborWidth <= cardWidth)
        }
    }

    private fun resolve(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float = 1.0f,
        variant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V1,
    ) = resolveGeminiHomeLayoutSpec(widthDp, heightDp, fontScale, variant)
}
