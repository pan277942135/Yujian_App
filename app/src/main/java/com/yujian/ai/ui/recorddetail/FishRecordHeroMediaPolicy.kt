package com.yujian.ai.ui.recorddetail

import kotlin.math.max
import kotlin.math.min

enum class FishRecordHeroImageMode {
    EVIDENCE_FIT,
    COVER,
}

/** Image fitting contract for the two sides of the detail Hero. */
object FishRecordHeroMediaPolicy {
    fun mode(showBside: Boolean): FishRecordHeroImageMode =
        if (showBside) FishRecordHeroImageMode.COVER else FishRecordHeroImageMode.EVIDENCE_FIT

    /** Compose ContentScale.Fit's uniform scale factor, expressed as a pure geometry helper. */
    fun fitScale(sourceWidth: Float, sourceHeight: Float, viewportWidth: Float, viewportHeight: Float): Float {
        require(sourceWidth > 0f && sourceHeight > 0f && viewportWidth > 0f && viewportHeight > 0f)
        return min(viewportWidth / sourceWidth, viewportHeight / sourceHeight)
    }

    /** Compose ContentScale.Crop's uniform scale factor, expressed as a pure geometry helper. */
    fun coverScale(sourceWidth: Float, sourceHeight: Float, viewportWidth: Float, viewportHeight: Float): Float {
        require(sourceWidth > 0f && sourceHeight > 0f && viewportWidth > 0f && viewportHeight > 0f)
        return max(viewportWidth / sourceWidth, viewportHeight / sourceHeight)
    }
}
