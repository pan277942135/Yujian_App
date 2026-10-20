package com.yujian.ai.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GeminiHomeLayoutVariant {
    V3_BASELINE,
    ADAPTIVE_V1,
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
)

private const val GEMINI_CARD_ASPECT_RATIO = 247f / 293f
private const val V3_CARD_WIDTH_FRACTION = 0.686f
private const val ADAPTIVE_CARD_WIDTH_FRACTION = 0.76f
private val V3_CARD_MAX_WIDTH = 337.2.dp
private val ADAPTIVE_CARD_MAX_WIDTH = 360.dp
private val V3_CARD_MAX_HEIGHT = 400.dp
private val ADAPTIVE_CARD_MAX_HEIGHT = (360f / GEMINI_CARD_ASPECT_RATIO).dp
private val V3_MIN_READABLE_CARD_HEIGHT = 220.dp
private val ADAPTIVE_MIN_READABLE_CARD_HEIGHT = (200f / GEMINI_CARD_ASPECT_RATIO).dp

/**
 * Resolves bounded NH01 spacing from the actual safe window and system font scale.
 * The V3 option exists only to make same-device, same-data comparison captures reproducible.
 */
internal fun resolveGeminiHomeLayoutSpec(
    safeWidthDp: Float,
    safeHeightDp: Float,
    fontScale: Float,
    variant: GeminiHomeLayoutVariant = GeminiHomeLayoutVariant.ADAPTIVE_V1,
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
        cardWidthFraction = ADAPTIVE_CARD_WIDTH_FRACTION,
        maxCardWidth = ADAPTIVE_CARD_MAX_WIDTH,
        maxCardHeight = ADAPTIVE_CARD_MAX_HEIGHT,
        minReadableCardHeight = ADAPTIVE_MIN_READABLE_CARD_HEIGHT,
        brandToStatsGap = brandToStats,
        statsToTitleGap = statsToTitle,
        titleToCardGap = titleToCard,
        cardToFooterGap = cardToFooter,
        topAlignMiddleGroup = true,
    )
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
