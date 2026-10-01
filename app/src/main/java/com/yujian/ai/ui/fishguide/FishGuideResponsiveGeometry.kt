package com.yujian.ai.ui.fishguide

/** Responsive proportions measured from fish_guide_v2.png at 941 × 1672. */
data class FishGuideCarouselGeometry(
    val viewportWidthDp: Float,
    val cardWidthDp: Float,
    val cardHeightDp: Float,
    val pageSpacingDp: Float,
    val sidePaddingDp: Float,
    val adjacentVisibleDp: Float,
)

data class SpeciesDetailCarouselGeometry(
    val viewportWidthDp: Float,
    val cardWidthDp: Float,
    val pageSpacingDp: Float,
    val sidePaddingDp: Float,
    val adjacentVisibleDp: Float,
)

object FishGuideResponsiveGeometryResolver {
    // Frozen Home card bounds: x≈134..807 (673px), y≈442..1365 (923px).
    private const val HOME_CARD_WIDTH_FRACTION = 673f / 941f
    private const val HOME_CARD_HEIGHT_TO_WIDTH = 923f / 673f
    private const val HOME_PAGE_GAP_FRACTION = 33f / 941f

    // Frozen Species Detail page-level target allows an 82–86% centered active card.
    const val SPECIES_DETAIL_CARD_WIDTH_FRACTION = 0.84f

    fun resolveHome(viewportWidthDp: Float): FishGuideCarouselGeometry {
        require(viewportWidthDp > 0f)
        val cardWidth = viewportWidthDp * HOME_CARD_WIDTH_FRACTION
        val sidePadding = (viewportWidthDp - cardWidth) / 2f
        val pageSpacing = viewportWidthDp * HOME_PAGE_GAP_FRACTION
        return FishGuideCarouselGeometry(
            viewportWidthDp = viewportWidthDp,
            cardWidthDp = cardWidth,
            cardHeightDp = cardWidth * HOME_CARD_HEIGHT_TO_WIDTH,
            pageSpacingDp = pageSpacing,
            sidePaddingDp = sidePadding,
            adjacentVisibleDp = (sidePadding - pageSpacing).coerceAtLeast(0f),
        )
    }

    fun resolveSpeciesDetail(
        viewportWidthDp: Float,
        pageSpacingDp: Float = 8f,
    ): SpeciesDetailCarouselGeometry {
        require(viewportWidthDp > 0f && pageSpacingDp >= 0f)
        val cardWidth = viewportWidthDp * SPECIES_DETAIL_CARD_WIDTH_FRACTION
        val sidePadding = (viewportWidthDp - cardWidth) / 2f
        return SpeciesDetailCarouselGeometry(
            viewportWidthDp = viewportWidthDp,
            cardWidthDp = cardWidth,
            pageSpacingDp = pageSpacingDp,
            sidePaddingDp = sidePadding,
            adjacentVisibleDp = (sidePadding - pageSpacingDp).coerceAtLeast(0f),
        )
    }
}
