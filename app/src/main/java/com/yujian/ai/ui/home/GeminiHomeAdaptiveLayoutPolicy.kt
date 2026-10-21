package com.yujian.ai.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GeminiHomeLayoutVariant {
    V3_BASELINE,
    /** Legacy adaptive policy retained only for reproducible regression comparisons. */
    ADAPTIVE_V1,
    /** Content-first adaptive policy; this is the production default. */
    ADAPTIVE_V2,
}

internal enum class GeminiHomeScreenBand {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class GeminiHomeLayoutSpec(
    val band: GeminiHomeScreenBand,
    val cardWidthFraction: Float,
    val maxCardWidth: Dp,
    val maxCardHeight: Dp,
    val minReadableCardHeight: Dp,
    val brandToStatsGap: Dp?,
    val statsToTitleGap: Dp?,
    val titleToCardGap: Dp,
    val cardToFooterGap: Dp?,
    val topAlignMiddleGroup: Boolean,
    val contentFirstSizing: Boolean = false,
)

private const val GEMINI_CARD_ASPECT_RATIO = 247f / 293f
private const val V3_CARD_WIDTH_FRACTION = 0.686f
private const val ADAPTIVE_V1_CARD_WIDTH_FRACTION = 0.76f
private const val ADAPTIVE_V2_CARD_WIDTH_FRACTION = 0.70f
private val V3_CARD_MAX_WIDTH = 337.2.dp
private val ADAPTIVE_V1_CARD_MAX_WIDTH = 360.dp
private val ADAPTIVE_V2_CARD_MAX_WIDTH = 337.2.dp
private val V3_CARD_MAX_HEIGHT = 400.dp
private val ADAPTIVE_V1_CARD_MAX_HEIGHT = (360f / GEMINI_CARD_ASPECT_RATIO).dp
private val ADAPTIVE_V2_CARD_MAX_HEIGHT = 400.dp
private val V3_MIN_READABLE_CARD_HEIGHT = 220.dp
private val ADAPTIVE_V1_MIN_READABLE_CARD_HEIGHT = (200f / GEMINI_CARD_ASPECT_RATIO).dp

/**
 * Resolves bounded NH01 spacing from the actual safe window and system font scale.
 * The V3 option exists only to make same-device, same-data comparison captures reproducible.
 */
internal fun resolveGeminiHomeLayoutSpec(
    safeWidthDp: Float,
    safeHeightDp: Float,
    fontScale: Float,
    variant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V2,
): GeminiHomeLayoutSpec {
    val band = when {
        safeWidthDp < 360f || safeHeightDp < 700f -> GeminiHomeScreenBand.COMPACT
        safeWidthDp < 400f -> GeminiHomeScreenBand.MEDIUM
        else -> GeminiHomeScreenBand.EXPANDED
    }
    if (variant == GeminiHomeLayoutVariant.V3_BASELINE) {
        return GeminiHomeLayoutSpec(
            band = band,
            cardWidthFraction = V3_CARD_WIDTH_FRACTION,
            maxCardWidth = V3_CARD_MAX_WIDTH,
            maxCardHeight = V3_CARD_MAX_HEIGHT,
            minReadableCardHeight = V3_MIN_READABLE_CARD_HEIGHT,
            brandToStatsGap = null,
            statsToTitleGap = null,
            titleToCardGap = 13.dp,
            cardToFooterGap = null,
            topAlignMiddleGroup = false,
        )
    }

    if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) {
        val compact = band == GeminiHomeScreenBand.COMPACT
        val expanded = band == GeminiHomeScreenBand.EXPANDED
        val highFontScale = fontScale >= 1.5f
        val largeFontScale = fontScale >= 1.3f

        val brandToStats = when {
            compact && highFontScale -> 12.dp
            compact && largeFontScale -> 14.dp
            compact -> 16.dp
            expanded && highFontScale -> 18.dp
            expanded && largeFontScale -> 20.dp
            expanded -> 24.dp
            highFontScale -> 16.dp
            largeFontScale -> 18.dp
            else -> 20.dp
        }
        val statsToTitle = when {
            compact && highFontScale -> 16.dp
            compact && largeFontScale -> 18.dp
            compact -> 20.dp
            expanded && highFontScale -> 20.dp
            expanded && largeFontScale -> 22.dp
            expanded -> 26.dp
            highFontScale -> 20.dp
            largeFontScale -> 22.dp
            else -> 24.dp
        }
        val titleToCard = when {
            compact && highFontScale -> 10.dp
            compact && largeFontScale -> 11.dp
            compact -> 12.dp
            expanded && highFontScale -> 14.dp
            expanded && largeFontScale -> 16.dp
            expanded -> 18.dp
            highFontScale -> 12.dp
            largeFontScale -> 14.dp
            else -> 16.dp
        }
        val cardToFooter = when {
            compact && highFontScale -> 12.dp
            compact && largeFontScale -> 14.dp
            compact -> 16.dp
            expanded && highFontScale -> 16.dp
            expanded && largeFontScale -> 20.dp
            expanded -> 24.dp
            highFontScale -> 16.dp
            largeFontScale -> 18.dp
            else -> 20.dp
        }

        return GeminiHomeLayoutSpec(
            band = band,
            cardWidthFraction = ADAPTIVE_V1_CARD_WIDTH_FRACTION,
            maxCardWidth = ADAPTIVE_V1_CARD_MAX_WIDTH,
            maxCardHeight = ADAPTIVE_V1_CARD_MAX_HEIGHT,
            minReadableCardHeight = ADAPTIVE_V1_MIN_READABLE_CARD_HEIGHT,
            brandToStatsGap = brandToStats,
            statsToTitleGap = statsToTitle,
            titleToCardGap = titleToCard,
            cardToFooterGap = cardToFooter,
            topAlignMiddleGroup = true,
        )
    }

    // Adaptive V2 removes discontinuous width/font-scale lookup tables. It responds
    // continuously to font pressure and short available height while preserving the
    // V3-like card width target. The card's final height is resolved separately from
    // the measured space left by the real Compose Top and Footer sections.
    val fontPressure = ((fontScale - 1f) / 0.5f).coerceIn(0f, 1f)
    val shortHeightPressure = ((620f - safeHeightDp) / 220f).coerceIn(0f, 1f)
    val brandToStats = maxOf(
        9f,
        safeHeightDp.times(0.035f).coerceIn(14f, 28f) - fontPressure * 3f - shortHeightPressure * 2f,
    )
    val statsToTitle = maxOf(
        2f,
        safeHeightDp.times(0.015f).coerceIn(6f, 14f) - fontPressure * 3f - shortHeightPressure * 1.5f,
    )
    val titleToCard = maxOf(
        4f,
        safeHeightDp.times(0.014f).coerceIn(7f, 12f) - fontPressure * 2f - shortHeightPressure * 1.5f,
    )
    val cardToFooter = maxOf(
        3f,
        safeHeightDp.times(0.018f).coerceIn(8f, 14f) - fontPressure * 3f - shortHeightPressure * 2f,
    )

    return GeminiHomeLayoutSpec(
        band = band,
        cardWidthFraction = ADAPTIVE_V2_CARD_WIDTH_FRACTION,
        maxCardWidth = ADAPTIVE_V2_CARD_MAX_WIDTH,
        maxCardHeight = ADAPTIVE_V2_CARD_MAX_HEIGHT,
        minReadableCardHeight = adaptiveV2MinimumCardHeightDp(safeHeightDp, fontScale).dp,
        brandToStatsGap = brandToStats.dp,
        statsToTitleGap = statsToTitle.dp,
        titleToCardGap = titleToCard.dp,
        cardToFooterGap = cardToFooter.dp,
        topAlignMiddleGroup = false,
        contentFirstSizing = true,
    )
}

private fun adaptiveV2MinimumCardHeightDp(safeHeightDp: Float, fontScale: Float): Float {
    val speciesLine = safeHeightDp.times(0.036f).coerceIn(20f, 26f) * 1.2f * fontScale
    val measurementLines = safeHeightDp.times(0.023f).coerceIn(13f, 16f) * 1.2f * fontScale * 2f
    val metadataLines = safeHeightDp.times(0.019f).coerceIn(11f, 13f) * 1.3f * fontScale * 2f
    val textGaps = safeHeightDp.times(0.006f).coerceIn(2f, 5f) * 2f
    // Reserve two footer padding edges plus a small font-metric safety allowance.
    return maxOf(148f, speciesLine + measurementLines + metadataLines + textGaps + 20f + 16f)
        .coerceAtMost(220f)
}

internal fun geminiHomeResolvedCardHeight(
    desiredCardHeightDp: Float,
    availableCardHeightDp: Float,
    minReadableCardHeightDp: Float,
): Float {
    require(desiredCardHeightDp > 0f)
    require(availableCardHeightDp >= 0f)
    require(minReadableCardHeightDp > 0f)
    return minOf(desiredCardHeightDp, maxOf(minReadableCardHeightDp, availableCardHeightDp))
}

internal fun geminiHomeCardWidth(trackWidthDp: Float, spec: GeminiHomeLayoutSpec): Float =
    minOf(trackWidthDp * spec.cardWidthFraction, spec.maxCardWidth.value)

/** Keeps a real side-card sliver visible when the adaptive Hero reaches its width cap. */
internal fun geminiHomeNeighborWidth(trackWidthDp: Float, cardWidthDp: Float, pageSpacingDp: Float): Float {
    val targetPeekWidth = (trackWidthDp * 0.05f).coerceIn(12f, 28f)
    val existingNeighborWidth = minOf(trackWidthDp * 0.62f, 260f)
    val minimumWidthForPeek = 2f * targetPeekWidth + 2f * pageSpacingDp + 2f * cardWidthDp - trackWidthDp
    return maxOf(existingNeighborWidth, minimumWidthForPeek).coerceIn(0f, cardWidthDp)
}

/**
 * Keeps the camera artwork proportional to the usable Home canvas on larger devices,
 * while constraining it by both horizontal and vertical space. The footer's touch
 * target is resolved separately and must never be smaller than 72dp.
 */
internal fun geminiHomeCameraVisualSize(safeWidthDp: Float, safeHeightDp: Float): Float {
    require(safeWidthDp > 0f)
    require(safeHeightDp > 0f)
    val contentWidthDp = minOf(safeWidthDp, 560f)
    val widthBound = contentWidthDp * 0.18f
    val heightBound = safeHeightDp * 0.10f
    return minOf(widthBound, heightBound, 80f).coerceAtLeast(56f)
}
