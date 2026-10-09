package com.yujian.ai.ui.recorddetail

import com.yujian.ai.ui.adaptive.AdaptiveLayoutProfile
import kotlin.math.roundToInt

/**
 * Frozen source measurements from fish_record_detail_v2.png (941 × 1672).
 * These are source pixels. Runtime layout resolves them proportionally into dp.
 */
object FishRecordDetailFrozenGeometry {
    const val canvasWidthPx = 941
    const val canvasHeightPx = 1672
    const val heroLeftPx = 50
    const val heroTopPx = 159
    const val heroRightPx = 891
    const val heroBottomPx = 699
    const val heroWidthPx = heroRightPx - heroLeftPx
    const val heroHeightPx = heroBottomPx - heroTopPx
    val heroAspectRatio: Float
        get() = heroWidthPx.toFloat() / heroHeightPx.toFloat()
}

/**
 * Responsive mapping measured from the frozen reference. Runtime content remains proportional
 * and grows vertically by scrolling; source pixels are never treated as device dp directly.
 */
data class FishRecordDetailGeometry(
    val horizontalMarginDp: Int,
    val heroWidthDp: Int,
    val heroHeightDp: Int,
    val sectionGapDp: Int = 12,
    val bottomContentPaddingDp: Int = 20,
)

object FishRecordDetailGeometryResolver {
    private val HERO_ASPECT = FishRecordDetailFrozenGeometry.heroAspectRatio

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
