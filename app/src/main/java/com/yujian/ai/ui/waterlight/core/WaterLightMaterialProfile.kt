package com.yujian.ai.ui.waterlight.core

/** Host-specific material values. Existing Orb values are held exactly at their old defaults. */
data class WaterLightMaterialProfile(
    val host: WaterLightHost,
    val goldColor: Long,
    val blueColor: Long,
    val greenColor: Long,
    val goldPeriodSeconds: Float,
    val bluePeriodSeconds: Float,
    val greenPeriodSeconds: Float,
    val goldCoreWidthDp: Float,
    val greenCoreWidthDp: Float,
    val goldGlowWidthDp: Float,
    val greenGlowWidthDp: Float,
    val blueCoreWidthDp: Float,
    val blueGlowWidthDp: Float,
    val opacity: Float = 1f,
    val motionScale: Float = 1f,
    val materialScale: Float = 1f,
) {
    companion object {
        val ORB_LEGACY = WaterLightMaterialProfile(
            host = WaterLightHost.ORB,
            goldColor = 0xFFD8BC87, blueColor = 0xFF348CA6, greenColor = 0xFF5B8F78,
            goldPeriodSeconds = 5.5f, bluePeriodSeconds = 4.5f, greenPeriodSeconds = 6.5f,
            goldCoreWidthDp = .9f, greenCoreWidthDp = 1.6f,
            goldGlowWidthDp = 4f, greenGlowWidthDp = 3.6f,
            blueCoreWidthDp = .9f, blueGlowWidthDp = 4f,
        )
    }
}
