package com.yujian.ai.ui.waterlight.renderer

/**
 * Candidate-only parameters for the additive fluid/refraction renderer.
 * Frozen R03, R01B, and R06 values remain owned by their existing profiles.
 */
internal object OrbFluidOpticalProfileV1 {
    const val PROFILE_VERSION = "ORB-FLUID-OPTICS-CANDIDATE-V1"
    const val PARAMETER_STATUS = "CALIBRATION_CANDIDATE"

    // Low-frequency, two-field water motion. Distances are fractions of visible Orb D.
    const val FLOW_SCALE_A = 1.16f
    const val FLOW_SCALE_B = 1.48f
    const val FLOW_DOMAIN_WARP = 0.28f
    const val FLOW_MAX_DISPLACEMENT_D = 0.0045f
    const val FLOW_TIME_RATE_A = 0.052f
    const val FLOW_TIME_RATE_B = 0.041f

    // Keep the glass edge response local and preserve the original alpha silhouette.
    const val REFRACTION_MAX_DISPLACEMENT_D = 0.008f
    const val REFRACTION_EDGE_BAND_D = 0.035f
    const val REFRACTION_NORMAL_STRENGTH = 0.85f
    const val WATER_MIX_MAX = 0.055f
    const val EDGE_REFRACTION_MIX_MAX = 0.055f
    const val TOTAL_SAMPLE_MIX_MAX = 0.11f
    const val SUNLIGHT_MIX_MAX = 0.035f

    // The auxiliary field is composited under the existing R03 caustic ceiling.
    const val CAUSTIC_AUX_ALPHA_MAX = 0.035f
    const val CAUSTIC_AUX_INTENSITY = 0.56
    const val CAUSTIC_DOMAIN_WARP = 0.26
    const val CAUSTIC_SOFT_EDGE_LOW = 0.42
    const val CAUSTIC_SOFT_EDGE_HIGH = 0.82
    const val CAUSTIC_FLOW_SCALE = 1.12
    const val CAUSTIC_TIME_RATE = 0.075

    // Reuse the existing fixed source and refraction budget for two local soft rays.
    const val SUNLIGHT_RAY_ALPHA_SHARE = 0.28f
    const val SUNLIGHT_RAY_WIDTH_D = 0.0018f
    const val SUNLIGHT_RAY_FADE_MIDPOINT = 0.42f
    const val SUNLIGHT_RAY_FADE_MID_ALPHA = 0.38f
    const val SUNLIGHT_RAY_FADE_END_ALPHA = 0.02f

    // Shared-current weights sum to one, so V1.2 never exceeds the V1.1 leaf amplitude.
    const val GRASS_LOCAL_WEIGHT = 0.78f
    const val GRASS_SHARED_WEIGHT = 0.17f
    const val GRASS_FLUTTER_WEIGHT = 0.05f
    const val GRASS_SHARED_ANGULAR_FREQUENCY = 0.97f
    const val GRASS_SECONDARY_FREQUENCY_MULTIPLIER = 2.63f
    const val GRASS_SHARED_X_SCALE = 2.2f
    const val GRASS_SHARED_Y_SCALE = 1.6f
    const val GRASS_FLUTTER_Y_SCALE = 9.0f
    const val GRASS_FLUTTER_PHASE_SCALE = 2.7f

    fun visibleDiameterPx(surfaceSizePx: Float): Float =
        surfaceSizePx.coerceAtLeast(1f) * OrbGrassMotionProfileV11.VISIBLE_DIAMETER_TO_SOURCE_UV

    fun refractionMaxPx(surfaceSizePx: Float): Float =
        visibleDiameterPx(surfaceSizePx) * REFRACTION_MAX_DISPLACEMENT_D

    fun flowMaxPx(surfaceSizePx: Float): Float =
        visibleDiameterPx(surfaceSizePx) * FLOW_MAX_DISPLACEMENT_D

    fun edgeBandPx(surfaceSizePx: Float): Float =
        visibleDiameterPx(surfaceSizePx) * REFRACTION_EDGE_BAND_D

    fun validateBudgets() {
        require(FLOW_MAX_DISPLACEMENT_D >= 0f && REFRACTION_MAX_DISPLACEMENT_D >= 0f)
        require(CAUSTIC_AUX_INTENSITY in 0.0..1.0)
        require(CAUSTIC_SOFT_EDGE_LOW < CAUSTIC_SOFT_EDGE_HIGH)
        require(TOTAL_SAMPLE_MIX_MAX <= OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX)
        require(WATER_MIX_MAX + EDGE_REFRACTION_MIX_MAX <= TOTAL_SAMPLE_MIX_MAX)
        require(SUNLIGHT_MIX_MAX <= OrbRuntimeProfileV1.R03_GLASS_SPECULAR_ALPHA_MAX)
        require(SUNLIGHT_RAY_ALPHA_SHARE * OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX <= SUNLIGHT_MIX_MAX)
        require(CAUSTIC_AUX_ALPHA_MAX <= OrbRuntimeProfileV1.R03_CAUSTIC_RESPONSE_ALPHA_MAX)
        require(kotlin.math.abs(GRASS_LOCAL_WEIGHT + GRASS_SHARED_WEIGHT + GRASS_FLUTTER_WEIGHT - 1f) < 0.00001f)
        require(SUNLIGHT_RAY_ALPHA_SHARE in 0f..1f)
        require(SUNLIGHT_RAY_FADE_MIDPOINT in 0f..1f)
        require(SUNLIGHT_RAY_FADE_MID_ALPHA in SUNLIGHT_RAY_FADE_END_ALPHA..1f)
        require(SUNLIGHT_RAY_FADE_END_ALPHA in 0f..1f)
    }
}
