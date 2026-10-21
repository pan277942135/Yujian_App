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

        assertTrue(normal.minReadableCardHeight.value >= 148f)
        assertTrue(normal.minReadableCardHeight.value < 180f)
        assertTrue(large.minReadableCardHeight.value > normal.minReadableCardHeight.value)
        assertTrue(largest.minReadableCardHeight.value >= large.minReadableCardHeight.value)
        assertTrue(largest.minReadableCardHeight.value <= 220f)
    }

    @Test
    fun cardShrinksToReadableMinimumInsteadOfRestoringOversizedTarget() {
        assertEquals(168f, geminiHomeResolvedCardHeight(320f, 110f, 168f), 0.001f)
        assertEquals(250f, geminiHomeResolvedCardHeight(320f, 250f, 168f), 0.001f)
        assertEquals(168f, geminiHomeResolvedCardHeight(190f, 110f, 168f), 0.001f)
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
        assertTrue(adaptive.contentFirstSizing)
        assertFalse(baseline.contentFirstSizing)
        assertFalse(legacy.contentFirstSizing)
        assertFalse(baseline.topAlignMiddleGroup)
        assertTrue(legacy.topAlignMiddleGroup)
        assertEquals(0.70f, adaptive.cardWidthFraction, 0.0001f)
        assertEquals(0.686f, baseline.cardWidthFraction, 0.0001f)
        assertEquals(0.76f, legacy.cardWidthFraction, 0.0001f)
        assertEquals(13f, baseline.titleToCardGap.value, 0.001f)
    }

    @Test
    fun cameraArtworkScalesWithCanvasWidthAndHeightWithoutGrowingUnbounded() {
        val compact = geminiHomeCameraVisualSize(320f, 568f)
        val phone = geminiHomeCameraVisualSize(393f, 780f)
        val tallPhone = geminiHomeCameraVisualSize(411f, 843f)
        val wideCanvas = geminiHomeCameraVisualSize(560f, 928f)
        val shortViewport = geminiHomeCameraVisualSize(393f, 432f)
        val extraWideViewport = geminiHomeCameraVisualSize(900f, 1200f)

        assertTrue("phone camera art grows beyond compact size", phone > compact)
        assertTrue("taller/wider phone camera art grows proportionally", tallPhone > phone)
        assertTrue("large canvas receives larger camera art", wideCanvas > tallPhone)
        assertEquals(80f, wideCanvas, 0.001f)
        assertEquals(80f, extraWideViewport, 0.001f)
        assertEquals("short viewports retain a visible minimum", 56f, shortViewport, 0.001f)
    }

    @Test
    fun compactViewportDoesNotOverReserveTwoMetadataLinesAtNormalFontScale() {
        val normal = resolve(320f, 432f, 1.0f)
        val accessible = resolve(320f, 432f, 1.3f)

        assertTrue("compact card budget includes narrow-card wrapping", normal.minReadableCardHeight.value >= 148f)
        assertTrue("larger system fonts reserve additional metadata space",
            accessible.minReadableCardHeight.value > normal.minReadableCardHeight.value)
    }

    @Test
    fun responsiveTypographyGrowsWithLargerAndTallerViewports() {
        val compact = geminiHomeResponsiveFontScale(360f, 568f)
        val phone = geminiHomeResponsiveFontScale(390f, 796f)
        val tallPhone = geminiHomeResponsiveFontScale(430f, 884f)
        val compactShort = geminiHomeResponsiveFontScale(320f, 432f)

        assertEquals(1f, compact, 0.001f)
        assertTrue("larger phone must not retain compact typography", phone > compact)
        assertTrue("wider and taller phone must have larger type", tallPhone > phone)
        assertEquals("short compact viewports must not shrink text below baseline", 1f, compactShort, 0.001f)
    }

    @Test
    fun neighborCardsStaySmallerEvenWhenHeroShrinksForHeight() {
        val profiles = listOf(
            Triple(320f, 150f, 10f),
            Triple(360f, 252f, 11.52f),
            Triple(390f, 273f, 12.48f),
            Triple(430f, 301f, 13.76f),
        )
        profiles.forEach { (trackWidth, cardWidth, spacing) -> assertAdaptivePeek(trackWidth, cardWidth, spacing) }
    }

    @Test
    fun cappedHeroKeepsAnEdgePeekWhenFeasibleAndAnInTrackFallbackOtherwise() {
        val profiles = listOf(
            Triple(320f, 224f, 10.24f),
            Triple(393f, 275.1f, 12.576f),
            Triple(411f, 287.7f, 13.152f),
            Triple(480f, 336f, 15.36f),
            Triple(560f, 337.2f, 16f),
            Triple(320f, 100f, 10f),
            Triple(320f, 120f, 10f),
        )
        profiles.forEach { (trackWidth, cardWidth, spacing) -> assertAdaptivePeek(trackWidth, cardWidth, spacing) }
    }

    @Test
    fun adaptiveV2WrapsAuxiliaryTextForNarrowCardsWithoutChangingV3BaselinePolicy() {
        assertTrue(geminiHomeShouldWrapAuxiliaryText(239f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertFalse(geminiHomeShouldWrapAuxiliaryText(240f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertEquals(2, geminiHomeMetadataMaxLines(31, 206f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertEquals(2, geminiHomeMetadataMaxLines(31, 301f, 1.5f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertEquals(2, geminiHomeMetadataMaxLines(31, 301f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertEquals(2, geminiHomeMetadataMaxLines(31, 301f, 1.3f, GeminiHomeLayoutVariant.V3_BASELINE))
        assertEquals(1, geminiHomeMetadataMaxLines(12, 301f, 1.0f, GeminiHomeLayoutVariant.V3_BASELINE))
        assertFalse(geminiHomeShouldWrapAuxiliaryText(170f, 1.0f, GeminiHomeLayoutVariant.V3_BASELINE))
        assertTrue(geminiHomeShouldWrapAuxiliaryText(240f, 1.3f, GeminiHomeLayoutVariant.V3_BASELINE))
        assertTrue(geminiHomeShouldWrapSpeciesTitle(239f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertFalse(geminiHomeShouldWrapSpeciesTitle(240f, 1.0f, GeminiHomeLayoutVariant.ADAPTIVE_V2))
        assertFalse(geminiHomeShouldWrapSpeciesTitle(170f, 1.0f, GeminiHomeLayoutVariant.V3_BASELINE))
    }

    @Test
    fun adaptiveV2FixesEdgePeekWhenTheHeightConstrainedHeroBecomesNarrow() {
        assertEquals(16f, geminiHomeNeighborWidth(320f, 120f, 10f), 0.001f)
        assertEquals(60f, geminiHomeNeighborWidthAdaptiveV2(320f, 120f, 10f), 0.001f)
    }

    private fun assertAdaptivePeek(trackWidth: Float, cardWidth: Float, spacing: Float) {
        val neighborWidth = geminiHomeNeighborWidthAdaptiveV2(trackWidth, cardWidth, spacing)
        val targetPeek = (trackWidth * 0.05f).coerceIn(12f, 28f)
        val widthForTargetEdgePeek = 2f * targetPeek + 2f * spacing + 2f * cardWidth - trackWidth
        val minimumWidthToTouchEdge = (trackWidth - 2f * cardWidth - 2f * spacing).coerceAtLeast(0f)
        val requiredWidth = maxOf(targetPeek, widthForTargetEdgePeek, minimumWidthToTouchEdge)
        val edgePeekFeasible = requiredWidth <= cardWidth * 0.82f + 0.01f
        val activeRight = (trackWidth + cardWidth) / 2f
        val neighborCenter = trackWidth / 2f + cardWidth + spacing
        val neighborLeft = neighborCenter - neighborWidth / 2f
        val neighborRight = neighborCenter + neighborWidth / 2f
        val visibleInTrack = (minOf(neighborRight, trackWidth) - maxOf(neighborLeft, 0f)).coerceAtLeast(0f)
        val visibleAtEdge = if (neighborRight >= trackWidth - 1f) {
            (trackWidth - maxOf(neighborLeft, 0f)).coerceAtLeast(0f)
        } else 0f

        assertTrue("neighbor stays smaller at $trackWidth dp", neighborWidth < cardWidth)
        assertTrue("neighbor width remains within 82% at $trackWidth dp", neighborWidth <= cardWidth * 0.82f + 0.01f)
        assertTrue("neighbor cannot overlap the active card at $trackWidth dp", neighborLeft >= activeRight - 1f)
        assertTrue("neighbor remains visible within track at $trackWidth dp", visibleInTrack >= minOf(12f, neighborWidth) - 1f)
        if (edgePeekFeasible) {
            assertTrue("edge peek reaches target at $trackWidth dp: visible=$visibleAtEdge target=$targetPeek",
                visibleAtEdge >= targetPeek - 1f)
        }
    }

    private fun resolve(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float = 1.0f,
        variant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V2,
    ) = resolveGeminiHomeLayoutSpec(widthDp, heightDp, fontScale, variant)
}
