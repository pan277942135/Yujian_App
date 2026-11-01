package com.yujian.ai.ui.waterlight.core

enum class WaterLightBlendMode { SOURCE_OVER, SCREEN, OVERLAY_POSITIVE }

/** Host-specific visual inputs. B01 numeric roles outside ORB are candidates until device review. */
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
    val primaryColor: Long = goldColor,
    val secondaryColor: Long = blueColor,
    val highlightColor: Long = greenColor,
    val coreAlpha: Float = 1f,
    val glowAlpha: Float = .18f,
    val coreWidth: Float = goldCoreWidthDp,
    val glowWidth: Float = goldGlowWidthDp,
    val blurRadius: Float = 0f,
    val feather: Float = 0f,
    val speed: Float = 1f,
    val coverage: Float = 1f,
    val blendMode: WaterLightBlendMode = WaterLightBlendMode.SOURCE_OVER,
) {
    init {
        require(listOf(opacity, motionScale, materialScale, coreAlpha, glowAlpha, coreWidth,
            glowWidth, blurRadius, feather, speed, coverage).all { it.isFinite() && it >= 0f })
        require(opacity <= 1f && coreAlpha <= 1f && glowAlpha <= 1f && coverage <= 1f)
    }
    companion object {
        val ORB_LEGACY = WaterLightMaterialProfile(
            host = WaterLightHost.ORB,
            goldColor = 0xFFD8BC87, blueColor = 0xFF348CA6, greenColor = 0xFF5B8F78,
            goldPeriodSeconds = 5.5f, bluePeriodSeconds = 4.5f, greenPeriodSeconds = 6.5f,
            goldCoreWidthDp = .9f, greenCoreWidthDp = 1.6f,
            goldGlowWidthDp = 4f, greenGlowWidthDp = 3.6f,
            blueCoreWidthDp = .9f, blueGlowWidthDp = 4f,
        )

        private fun candidate(host: WaterLightHost, primary: Long, secondary: Long,
            highlight: Long, core: Float, glow: Float, blur: Float, opacity: Float,
            mode: WaterLightBlendMode) = WaterLightMaterialProfile(
            host = host, goldColor = primary, blueColor = secondary, greenColor = highlight,
            goldPeriodSeconds = 5.5f, bluePeriodSeconds = 4.5f, greenPeriodSeconds = 6.5f,
            goldCoreWidthDp = core, greenCoreWidthDp = core, goldGlowWidthDp = glow,
            greenGlowWidthDp = glow, blueCoreWidthDp = core, blueGlowWidthDp = glow,
            opacity = opacity, primaryColor = primary, secondaryColor = secondary,
            highlightColor = highlight, coreWidth = core, glowWidth = glow,
            blurRadius = blur, blendMode = mode,
        )

        val PERIMETER = candidate(WaterLightHost.PERIMETER, 0xFF0B6E69, 0xFF4DD0C7,
            0xFFDDF7F5, 1f, 36f, 0f, 1f, WaterLightBlendMode.OVERLAY_POSITIVE)
        val PATH = candidate(WaterLightHost.PATH, 0xFFC7EFE8, 0xFF1A525C,
            0xFFE1F6F3, 1.2f, 4f, 3f, .6f, WaterLightBlendMode.SCREEN)
        val CONTOUR = candidate(WaterLightHost.CONTOUR, 0xFFFFE3A6, 0xFFF6C771,
            0xFFDBA34D, 1.2f, 10f, 12f, 1f, WaterLightBlendMode.SCREEN)
        val HANDOFF = candidate(WaterLightHost.HANDOFF, 0xFFDCB05B, 0xFFFFE39C,
            0xFFFAE8BA, 1.2f, 8f, 8f, 1f, WaterLightBlendMode.SCREEN)
        val RESIDUE = candidate(WaterLightHost.RESIDUE, 0xFFDCAf5B, 0xFFFFE39C,
            0xFFFAE8BA, .8f, 4f, 4f, .4f, WaterLightBlendMode.SCREEN)

        fun forHost(host: WaterLightHost): WaterLightMaterialProfile = when (host) {
            WaterLightHost.ORB -> ORB_LEGACY
            WaterLightHost.PERIMETER -> PERIMETER
            WaterLightHost.PATH -> PATH
            WaterLightHost.CONTOUR -> CONTOUR
            WaterLightHost.HANDOFF -> HANDOFF
            WaterLightHost.RESIDUE -> RESIDUE
        }
    }
}
