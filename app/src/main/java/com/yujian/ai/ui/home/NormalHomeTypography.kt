package com.yujian.ai.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Normal Home typography sizes are measured from the 1080 px Frozen raster.
 * They are physical reference pixels, never Android sp values. Compose still
 * applies the device's `fontScale` after this conversion.
 */
internal data class NormalHomeTypeSize(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
)

internal data class NormalHomeTypographyContract(
    val brand: NormalHomeTypeSize,
    val statValue: NormalHomeTypeSize,
    val statLabel: NormalHomeTypeSize,
    val sectionTitle: NormalHomeTypeSize,
    val action: NormalHomeTypeSize,
    val heroTitle: NormalHomeTypeSize,
    val measurement: NormalHomeTypeSize,
    val metadata: NormalHomeTypeSize,
    val captureCta: NormalHomeTypeSize,
)

/** Reference-space gaps were measured independently from the typography roles. */
internal data class NormalHomeSpacingContract(
    val recentHeaderHorizontalInset: Dp,
    val statVerticalPadding: Dp,
    val statLabelSpacing: Dp,
    val heroMetadataSpacing: Dp,
    val pagerSpacing: Dp,
    val recentActionSpacing: Dp,
    val recentChevronSize: Dp,
)

/**
 * Width-first geometry conversion. `usableWidthDp` is Android's logical
 * content width; density converts the reference px to physical px only once.
 */
internal fun normalHomeReferenceDp(referencePx: Float, usableWidthDp: Float): Float {
    require(referencePx.isFinite() && referencePx >= 0f)
    require(usableWidthDp.isFinite() && usableWidthDp > 0f)
    return referencePx * usableWidthDp / NormalHomeReferenceWidthPx
}

/**
 * Convert Frozen reference pixels into sp without losing or duplicating the
 * density conversion:
 *
 * `fontSizeSp = max(referenceFontPx × usableWidthPx / 1080, minimumPx) / density`
 *
 * Since `usableWidthPx = usableWidthDp × density`, density cancels once in
 * this expression. Android then applies density and `fontScale` to sp. Thus,
 * at fontScale 1, a 1080 px viewport receives the measured reference px at
 * any density; accessibility scaling remains independent.
 */
internal fun normalHomeTypographyContract(
    usableWidthDp: Float,
    density: Float,
): NormalHomeTypographyContract {
    require(usableWidthDp.isFinite() && usableWidthDp > 0f)
    require(density.isFinite() && density > 0f)

    val widthScale = usableWidthDp * density / NormalHomeReferenceWidthPx

    fun type(referenceFontPx: Float, referenceLineHeightPx: Float, minimumFontPx: Float): NormalHomeTypeSize {
        val fontPx = maxOf(referenceFontPx * widthScale, minimumFontPx)
        val lineHeightPx = maxOf(
            referenceLineHeightPx * widthScale,
            minimumFontPx * 1.2f,
            fontPx * 1.08f,
        )
        return NormalHomeTypeSize(
            fontSize = (fontPx / density).sp,
            lineHeight = (lineHeightPx / density).sp,
        )
    }

    return NormalHomeTypographyContract(
        // Raster glyph bounds: brand ≈63 px at 1080 px width.
        brand = type(referenceFontPx = 72f, referenceLineHeightPx = 80f, minimumFontPx = 18f),
        // Values ≈30 px; labels ≈23 px. Their independent leading and gap
        // are carried by this role sizing and NormalHomeSpacingContract.
        statValue = type(referenceFontPx = 36f, referenceLineHeightPx = 42f, minimumFontPx = 16f),
        statLabel = type(referenceFontPx = 28f, referenceLineHeightPx = 32f, minimumFontPx = 16f),
        // Frozen visible glyphs: section title ≈40 px; action ≈33 px.
        sectionTitle = type(referenceFontPx = 48f, referenceLineHeightPx = 52f, minimumFontPx = 18f),
        action = type(referenceFontPx = 40f, referenceLineHeightPx = 44f, minimumFontPx = 16f),
        // Hero title ≈49 px, measurement ≈40 px, metadata ≈32 px.
        heroTitle = type(referenceFontPx = 56f, referenceLineHeightPx = 64f, minimumFontPx = 18f),
        measurement = type(referenceFontPx = 46f, referenceLineHeightPx = 52f, minimumFontPx = 16f),
        metadata = type(referenceFontPx = 36f, referenceLineHeightPx = 42f, minimumFontPx = 16f),
        // Frozen CTA glyph height is about 32 px.
        captureCta = type(referenceFontPx = 40f, referenceLineHeightPx = 44f, minimumFontPx = 16f),
    )
}

/**
 * Spacing values below are reference pixels measured against the Frozen
 * 1080-wide frame, then converted to dp with the same width-first rule.
 */
internal fun normalHomeSpacingContract(usableWidthDp: Float): NormalHomeSpacingContract =
    NormalHomeSpacingContract(
        recentHeaderHorizontalInset = normalHomeReferenceDp(108f, usableWidthDp).dp,
        statVerticalPadding = normalHomeReferenceDp(17f, usableWidthDp).dp,
        statLabelSpacing = normalHomeReferenceDp(8f, usableWidthDp).dp,
        heroMetadataSpacing = normalHomeReferenceDp(20f, usableWidthDp).dp,
        // The 16 px neighbor spacing is retained; only the incorrect dp/px
        // interpretation is corrected through this reference conversion.
        pagerSpacing = normalHomeReferenceDp(16f, usableWidthDp).dp,
        recentActionSpacing = normalHomeReferenceDp(8f, usableWidthDp).dp,
        recentChevronSize = normalHomeReferenceDp(24f, usableWidthDp).dp,
    )

private const val NormalHomeReferenceWidthPx = 1080f
