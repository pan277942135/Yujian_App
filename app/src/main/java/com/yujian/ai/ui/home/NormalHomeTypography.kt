package com.yujian.ai.ui.home

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Normal Home typography is authored in the same 1080px-wide reference space
 * as its geometry. The reference size is first converted to dp-space by the
 * width ratio; Android then applies density and the user's fontScale as usual.
 *
 * A physical-pixel floor keeps supporting text readable on narrow viewports.
 * It is converted to sp using density only. fontScale is deliberately not
 * cancelled, so accessibility text scaling remains in effect.
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

/**
 * [referenceScale] is usable Normal Home width in dp divided by 1080 reference
 * pixels. [density] is Android's physical px-per-dp factor. With fontScale 1,
 * a reference text size maps to `referenceSp * referenceScale * density` px,
 * matching the width-first geometry rule. Android's fontScale multiplies that
 * result independently.
 */
internal fun normalHomeTypographyContract(
    referenceScale: Float,
    density: Float,
): NormalHomeTypographyContract {
    require(referenceScale.isFinite() && referenceScale > 0f)
    require(density.isFinite() && density > 0f)

    fun type(referenceFontSp: Float, referenceLineHeightSp: Float, minimumFontPx: Float): NormalHomeTypeSize {
        val font = maxOf(referenceFontSp * referenceScale, minimumFontPx / density)
        val line = maxOf(referenceLineHeightSp * referenceScale, minimumFontPx * 1.25f / density, font * 1.15f)
        return NormalHomeTypeSize(font.sp, line.sp)
    }

    return NormalHomeTypographyContract(
        brand = type(32f, 40f, minimumFontPx = 18f),
        statValue = type(22f, 28f, minimumFontPx = 16f),
        statLabel = type(12f, 18f, minimumFontPx = 12f),
        sectionTitle = type(24f, 30f, minimumFontPx = 16f),
        action = type(18f, 24f, minimumFontPx = 14f),
        heroTitle = type(32f, 38f, minimumFontPx = 18f),
        measurement = type(22f, 28f, minimumFontPx = 16f),
        metadata = type(16f, 22f, minimumFontPx = 14f),
        captureCta = type(20f, 28f, minimumFontPx = 16f),
    )
}
