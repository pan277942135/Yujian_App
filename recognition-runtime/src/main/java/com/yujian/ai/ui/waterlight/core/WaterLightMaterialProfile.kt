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
    // EFFECT_02 algorithm inputs from the Web reference; visual colors remain the frozen Android palette.
    val perimeterIntensity: Float = 1.8f,
    val perimeterMarginRatio: Float = .015f,
    val perimeterBlendRatio: Float = .8f,
    val perimeterBottomMaskStrength: Float = 1f,
    val perimeterCausticsDensity: Float = .85f,
    val perimeterGlowSizePx: Float = 45f,
    val perimeterSpreadDurationMs: Long = 1_200L,
) {
    init {
        require(listOf(opacity, motionScale, materialScale, coreAlpha, glowAlpha, coreWidth,
            glowWidth, blurRadius, feather, speed, coverage, perimeterIntensity,
            perimeterMarginRatio, perimeterBlendRatio, perimeterBottomMaskStrength,
            perimeterCausticsDensity, perimeterGlowSizePx).all { it.isFinite() && it >= 0f })
        require(opacity <= 1f && coreAlpha <= 1f && glowAlpha <= 1f && coverage <= 1f)
        require(perimeterMarginRatio <= .1f && perimeterBlendRatio <= 1f &&
            perimeterBottomMaskStrength <= 1f && perimeterCausticsDensity <= 1f &&
            perimeterSpreadDurationMs > 0L)
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

        // Frozen perimeter palette A: lake-teal lead, warm-gold support, ice-cyan highlight.
        // Web EFFECT_02 geometry/caustics values are retained below; Web's mislabeled raw
        // color triplet is not allowed to override the already-frozen Android visual palette.
        val PERIMETER = candidate(WaterLightHost.PERIMETER, 0xFF2E8B83, 0xFFD8B36A,
            0xFFDDFBFF, 1f, 45f, 0f, 1f, WaterLightBlendMode.OVERLAY_POSITIVE).copy(
            goldColor = 0xFFD8B36A,
            blueColor = 0xFF7DD3FC,
            greenColor = 0xFF2E8B83,
            primaryColor = 0xFF2E8B83,
            secondaryColor = 0xFFD8B36A,
            highlightColor = 0xFFDDFBFF,
            perimeterIntensity = 1.8f,
            perimeterMarginRatio = .015f,
            perimeterBlendRatio = .8f,
            perimeterBottomMaskStrength = 1f,
            perimeterCausticsDensity = .85f,
            perimeterGlowSizePx = 45f,
            perimeterSpreadDurationMs = 1_200L,
        )
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
