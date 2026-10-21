package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiHomeAdaptiveLayoutPolicyTest {
    @Test
    fun screenBandRemainsDiagnosticWhileAdaptiveSpacingUsesContinuousInputs() {
        assertEquals(GeminiHomeScreenBand.COMPACT, resolve(320f, 915f).band)
        assertEquals(GeminiHomeScreenBand.COMPACT, resolve(393f, 640f).band)
        assertEquals(GeminiHomeScreenBand.MEDIUM, resolve(393f, 852f).band)
        assertEquals(GeminiHomeScreenBand.EXPANDED, resolve(411f, 915f).band)

        val normal = resolve(393f, 852f, 1.0f)
        val mediumScale = resolve(393f, 852f, 1.25f)
        val largeScale = resolve(393f, 852f, 1.5f)
        val short = resolve(393f, 500f, 1.0f)

        assertTrue(normal.brandToStatsGap!!.value > mediumScale.brandToStatsGap!!.value)
        assertTrue(mediumScale.brandToStatsGap!!.value > largeScale.brandToStatsGap!!.value)
        assertTrue(normal.titleToCardGap.value > mediumScale.titleToCardGap.value)
        assertTrue(mediumScale.titleToCardGap.value > largeScale.titleToCardGap.value)
        assertTrue(short.brandToStatsGap!!.value < normal.brandToStatsGap!!.value)
    }

    @Test
    fun adaptiveV2UsesV3LikeCardWidthCapAndFrozenAspectRatio() {
        val compact = resolve(320f, 640f)
        val medium = resolve(393f, 852f)
        val expanded = resolve(411f, 915f)

        assertEquals(0.70f, compact.cardWidthFraction, 0.0001f)
        assertEquals(337.2f, expanded.maxCardWidth.value, 0.001f)
        assertEquals(320f * 0.70f, geminiHomeCardWidth(320f, compact), 0.001f)
        assertEquals(393f * 0.70f, geminiHomeCardWidth(393f, medium), 0.001f)
        assertEquals(411f * 0.70f, geminiHomeCardWidth(411f, expanded), 0.001f)
        assertEquals(247f / 293f, 337.2f / expanded.maxCardHeight.value, 0.001f)
    }

    @Test
    fun minimumReadableCardHeightAccountsForSystemFontScaleAndBoundedMetadataLines() {
        val normal = resolve(393f, 852f, 1.0f)
        val large = resolve(393f, 852f, 1.3f)
        val largest = resolve(393f, 852f, 1.5f)

        assertEquals(148f, normal.minReadableCardHeight.value, 0.001f)
        assertTrue(large.minReadableCardHeight.value > normal.minReadableCardHeight.value)
        assertTrue(largest.minReadableCardHeight.value > large.minReadableCardHeight.value)
        assertTrue(largest.minReadableCardHeight.value <= 220f)
    }

    @Test
    fun cardShrinksToReadableMinimumInsteadOfRestoringOversizedTarget() {
        assertEquals(168f, geminiHomeResolvedCardHeight(320f, 110f, 168f), 0.001f)
        assertEquals(250f, geminiHomeResolvedCardHeight(320f, 250f, 168f), 0.001f)
        assertEquals(190f, geminiHomeResolvedCardHeight(190f, 110f, 168f), 0.001f)
    }

    @Test
    fun adaptiveV2CentersWhenItFitsAndScrollsWhenTheReadableMinimumCannotFit() {
        val adaptive = resolve(393f, 852f, 1.0f)
        val baseline = resolve(
            widthDp = 393f,
            heightDp = 852f,
            fontScale = 1.0f,
            variant = GeminiHomeLayoutVariant.V3_BASELINE,
        )
        val legacy = resolve(
            widthDp = 393f,
            heightDp = 852f,
            fontScale = 1.0f,
            variant = GeminiHomeLayoutVariant.ADAPTIVE_V1,
        )

        assertFalse(adaptive.topAlignMiddleGroup)
        assertFalse(baseline.topAlignMiddleGroup)
        assertTrue(legacy.topAlignMiddleGroup)
        assertEquals(0.70f, adaptive.cardWidthFraction, 0.0001f)
        assertEquals(0.686f, baseline.cardWidthFraction, 0.0001f)
        assertEquals(0.76f, legacy.cardWidthFraction, 0.0001f)
        assertEquals(13f, baseline.titleToCardGap.value, 0.001f)
    }

    @Test
    fun cappedHeroKeepsAVisibleRealNeighborPreviewAcrossWidths() {
        val profiles = listOf(
            Triple(320f, 224f, 10.24f),
            Triple(393f, 275.1f, 12.576f),
            Triple(411f, 287.7f, 13.152f),
            Triple(480f, 336f, 15.36f),
            Triple(560f, 337.2f, 16f),
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
        variant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V2,
    ) = resolveGeminiHomeLayoutSpec(widthDp, heightDp, fontScale, variant)
}
