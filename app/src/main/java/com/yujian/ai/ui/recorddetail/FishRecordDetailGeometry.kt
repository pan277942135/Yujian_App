package com.yujian.ai.ui.recorddetail

import com.yujian.ai.ui.adaptive.AdaptiveLayoutProfile
import kotlin.math.roundToInt

/**
 * Responsive mapping measured from fish_record_detail_v2.png (941 × 1672).
 * The source Hero frame is approximately x=50..891, y=159..699 px; page
 * content remains proportional and grows vertically by scrolling.
 */
data class FishRecordDetailGeometry(
    val horizontalMarginDp: Int,
    val heroWidthDp: Int,
    val heroHeightDp: Int,
    val sectionGapDp: Int = 12,
    val bottomContentPaddingDp: Int = 20,
)

object FishRecordDetailGeometryResolver {
    private const val SOURCE_HERO_WIDTH_PX = 841f
    private const val SOURCE_HERO_HEIGHT_PX = 540f
    private const val HERO_ASPECT = SOURCE_HERO_WIDTH_PX / SOURCE_HERO_HEIGHT_PX

    fun resolve(profile: AdaptiveLayoutProfile): FishRecordDetailGeometry = resolve(profile.safeWidthDp.roundToInt())

    fun resolve(safeWidthDp: Int): FishRecordDetailGeometry {
        val width = safeWidthDp.coerceAtLeast(1)
        val margin = when {
            width <= 320 -> 16
            width <= 393 -> 20
            else -> 24
        }
        val heroWidth = (width - 2 * margin).coerceAtLeast(1)
        return FishRecordDetailGeometry(
            horizontalMarginDp = margin,
            heroWidthDp = heroWidth,
            heroHeightDp = (heroWidth / HERO_ASPECT).roundToInt(),
        )
    }
}
