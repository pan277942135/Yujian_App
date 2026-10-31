package com.yujian.ai.ui.waterlight.core

/** Host-specific material values. Existing Orb values are held exactly at their old defaults. */
data class WaterLightMaterialProfile(
    val goldColor: Long,
    val greenColor: Long,
    val goldPeriodSeconds: Float,
    val greenPeriodSeconds: Float,
    val goldCoreWidthDp: Float,
    val greenCoreWidthDp: Float,
    val goldGlowWidthDp: Float,
    val greenGlowWidthDp: Float,
    val opacity: Float = 1f,
    val motionScale: Float = 1f,
    val materialScale: Float = 1f,
) {
    companion object {
        val ORB_LEGACY = WaterLightMaterialProfile(
            goldColor = 0xFFD8BC87, greenColor = 0xFF5B8F78,
            goldPeriodSeconds = 5.5f, greenPeriodSeconds = 6.5f,
            goldCoreWidthDp = .9f, greenCoreWidthDp = 1.6f,
            goldGlowWidthDp = 4f, greenGlowWidthDp = 3.6f,
        )
    }
}
