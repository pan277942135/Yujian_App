package com.yujian.ai.ui.recognition

/**
 * Source-backed optical parameters for the photo perimeter shader.
 * Size values are in the Demo's WebGL backing-store units. The shader receives
 * a measured source-to-Android mapping; they are never interpreted as Android dp.
 */
internal data class RecognitionPerimeterOpticalProfileV1(
    val version: Version,
    val intensity: Float,
    val speed: Float,
    val marginRatio: Float,
    val cornerRadiusRatio: Float,
    val releaseExpansionShare: Float,
    val wave1Frequency: Float,
    val wave1AmplitudeSourcePx: Float,
    val wave1Speed: Float,
    val wave2Frequency: Float,
    val wave2AmplitudeSourcePx: Float,
    val wave2Speed: Float,
    val glowSizeSourcePx: Float,
    val wideHaloDecay: Float,
    val wideHaloGlowFactor: Float,
    val wideHaloWeight: Float,
    val mediumBloomDecay: Float,
    val mediumBloomGlowFactor: Float,
    val mediumBloomWeight: Float,
    val sharpCoreNear: Float,
    val sharpCoreFar: Float,
    val sharpCoreWeight: Float,
    val bottomMaskStrength: Float,
    val blendRatio: Float,
    val bottomMaskFloor: Float,
    val bottomGradientLift: Float,
    val bottomGradientExponent: Float,
    val causticsUvScale: Float,
    val bottomContributionScale: Float,
    val perimeterContributionScale: Float,
    val specularStart: Float,
    val specularEnd: Float,
    val specularMix: Float,
    val specularBoost: Float,
    val sparkleBaseSizeCssPx: Float,
    val sparkleSizeStepCssPx: Float,
    val sparkleHaloScale: Float,
    val sparkleCoreScale: Float,
    val sparkleHaloOpacity: Float,
    val sparkleCoreOpacity: Float,
    val flourishHalfWidthCssPx: Float,
    val flourishPeakHeightCssPx: Float,
    val blendMode: BlendMode = BlendMode.OVERLAY,
    val goldColor: Long,
    val cyanColor: Long,
    val greenColor: Long,
    val sparkleGoldCoreColor: Long,
    val sparkleCyanCoreColor: Long,
    val causticsDensityReferenceOnly: Float,
) {
    enum class BlendMode { OVERLAY }
    enum class Version { EFFECT_02, EFFECT_01 }

    init {
        require(intensity.isFinite() && intensity >= 0f)
        require(speed.isFinite() && speed >= 0f)
        require(marginRatio in 0f..0.1f && cornerRadiusRatio in 0f..0.5f)
        require(releaseExpansionShare in 0f..1f)
        require(glowSizeSourcePx.isFinite() && glowSizeSourcePx > 0f)
        require(bottomMaskStrength in 0f..1f && blendRatio in 0f..1f)
        require(bottomMaskFloor in 0f..1f && bottomGradientLift in 0f..1f)
        require(bottomGradientExponent.isFinite() && bottomGradientExponent > 0f)
        require(causticsUvScale.isFinite() && causticsUvScale > 0f)
        require(bottomContributionScale.isFinite() && bottomContributionScale >= 0f)
        require(perimeterContributionScale.isFinite() && perimeterContributionScale >= 0f)
        require(specularStart.isFinite() && specularEnd > specularStart)
        require(specularMix in 0f..1f && specularBoost >= 0f)
        require(sparkleBaseSizeCssPx > 0f && sparkleSizeStepCssPx >= 0f)
        require(sparkleHaloScale > 0f && sparkleCoreScale > 0f)
        require(sparkleHaloOpacity in 0f..1f && sparkleCoreOpacity in 0f..1f)
        require(flourishHalfWidthCssPx > 0f && flourishPeakHeightCssPx >= 0f)
        require(causticsDensityReferenceOnly in 0f..1f)
    }

    companion object {
        /** Default frozen Demo Effect 02. */
        val EFFECT_02 = RecognitionPerimeterOpticalProfileV1(
            version = Version.EFFECT_02,
            intensity = 1.80f,
            speed = 1.00f,
            marginRatio = .015f,
            cornerRadiusRatio = .12f,
            releaseExpansionShare = .72f,
            wave1Frequency = 5f,
            wave1AmplitudeSourcePx = 4.5f,
            wave1Speed = 2.2f,
            wave2Frequency = 9f,
            wave2AmplitudeSourcePx = 2.6f,
            wave2Speed = -1.5f,
            glowSizeSourcePx = 45f,
            wideHaloDecay = .07f,
            wideHaloGlowFactor = .12f,
            wideHaloWeight = .35f,
            mediumBloomDecay = .16f,
            mediumBloomGlowFactor = .10f,
            mediumBloomWeight = .65f,
            sharpCoreNear = .3f,
            sharpCoreFar = 2.8f,
            sharpCoreWeight = .95f,
            bottomMaskStrength = 1.00f,
            blendRatio = .80f,
            bottomMaskFloor = .04f,
            bottomGradientLift = .85f,
            bottomGradientExponent = 1.5f,
            causticsUvScale = 1.85f,
            bottomContributionScale = .68f,
            perimeterContributionScale = .86f,
            specularStart = .85f,
            specularEnd = 1.75f,
            specularMix = .48f,
            specularBoost = .30f,
            sparkleBaseSizeCssPx = 2.2f,
            sparkleSizeStepCssPx = .7f,
            sparkleHaloScale = 3.2f,
            sparkleCoreScale = 1.15f,
            sparkleHaloOpacity = .42f,
            sparkleCoreOpacity = .92f,
            flourishHalfWidthCssPx = 38f,
            flourishPeakHeightCssPx = 1.1f,
            goldColor = 0xFF4D9B91,
            cyanColor = 0xFFD89913,
            greenColor = 0xFF719FBC,
            sparkleGoldCoreColor = 0xFFFEF08A,
            sparkleCyanCoreColor = 0xFFE0F2FE,
            // The Demo configuration exposes this value, but its final shader has no uniform
            // or use site for it. Keep it as audit metadata and do not invent semantics.
            causticsDensityReferenceOnly = .85f,
        )

        /** Effect 01 is retained for internal regression comparisons only. */
        val EFFECT_01 = EFFECT_02.copy(
            version = Version.EFFECT_01,
            intensity = 1.50f,
            glowSizeSourcePx = 48f,
            blendRatio = .75f,
            goldColor = 0xFFFEF08A,
            cyanColor = 0xFF38BDF8,
            greenColor = 0xFF2DD4BF,
        )
    }
}
