package com.yujian.ai.ui.waterlight.renderer

object OrbRuntimeProfileV1 {
    enum class LifeState { S1_IDLE, S2_ACTIVE }
    enum class MotionEvent { NONE, RELEASE, RETURN }

    data class MotionKey(val progress: Float, val distance: Float)

    const val PROFILE_VERSION = "R02-R06-V1.2"
    const val CORE_SHA256 = "daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09"
    const val R02_JSON_SHA256 = "cdb546dd9e0fcb3dd0844a6e068c9ed45763a3dd60ef5fca8e74d63757e619a6"
    const val R03_JSON_SHA256 = "c47d88eeb1840e25e18e211b4c22287e94b22cd379f8a00f239ee0f069f8756f"
    const val R04_JSON_SHA256 = "0613345f1253e02d3d5c3d17ca981515f253e066ea776733c49e76fc6adb1c71"
    const val R04_TRANSITION_JSON_SHA256 = "3b68c4a123039f7c3d7eed74371e75ca0ce8d8336f5bf0f0c09b64b7a5e9f7d2"
    const val R05_JSON_SHA256 = "e90f2c2f8a789a2744bb4605c0e279c42640043766e9c81b39cf979be72ce30d"
    const val R06_JSON_SHA256 = "8c7f998b043ddca8363054b75a93dbe6fab72cbdf31424b4fb71c4b502f21c10"
    const val GEOMETRY_JSON_SHA256 = "1dfc85e651440d88c18d80daf51bbaa081e058fa60061bed6c8801aededf4cdc"
    const val R04_ACTIVE_TRANSITION_MS = 450L
    const val R04_RETURN_TRANSITION_MS = 620L
    const val R04_ENTRANCE_MS = 900L
    const val R02_LOW_PERFORMANCE_COUNT = 1
    const val R02_VERTICAL_DRIFT_MAX_D = 0.012f
    const val R02_LATERAL_DRIFT_MAX_D = 0.007f
    const val R02_FIXED_AZIMUTH = true
    const val R03_SUN_SOURCE_U = 0.24f
    const val R03_SUN_SOURCE_V = 0.2f
    const val R03_GLASS_SPECULAR_ALPHA_MAX = 0.11f
    const val R03_GLASS_SPECULAR_PERIOD_MS = 6400L
    const val R03_GLASS_MAX_TRANSLATION_D = 0.02f
    const val R03_CORE_REFRACTION_ALPHA_MAX = 0.11f
    const val R03_CORE_REFRACTION_PERIOD_MS = 5200L
    const val R03_CAUSTIC_RESPONSE_ALPHA_MAX = 0.14f
    const val R03_GRASS_RESPONSE_ALPHA_MAX = 0.06f
    const val R03_DROPLET_SPECULAR_ALPHA_MAX = 0.32f
    const val R03_MAX_EXTRA_ADDITIVE_ALPHA = 0.22f
    const val R03_FIXED_AZIMUTH = true
    val sunColor: Long = 0xD8BC87L

    const val CAUSTIC_INNER_RADIUS_D = 0.385f
    const val CAUSTIC_EDGE_FADE_D = 0.035f
    const val CAUSTIC_NORMAL_TEXTURE_PX_80DP = 96
    const val CAUSTIC_LOW_TEXTURE_PX_80DP = 64
    const val CAUSTIC_NORMAL_MIN_INTERVAL_MS = 66L
    const val CAUSTIC_LOW_MIN_INTERVAL_MS = 120L
    const val CAUSTIC_BUFFER_COUNT = 2
    const val R06_MAX_OUTSIDE_DP = 14.0f
    const val R06_NORMAL_STEPS_PER_PATH = 96
    const val R06_LOW_STEPS_PER_PATH = 48
    const val R06_REDUCED_MOTION_PHASE = 0.37f
    const val R06_EVENT_SWITCH_MS = 260L
    const val R06_EXIT_TAIL_FADE = true
    val causticLayers: List<OrbCausticLayerV1> = listOf(
        OrbCausticLayerV1("deep_flow", 6500L, 1.65f, 0.07f, 0.015f, 0.004f),
        OrbCausticLayerV1("surface_caustic", 4100L, 3.05f, 0.12f, -0.006f, 0.011f),
    )

    val droplets: List<OrbDropletSpecV1> = listOf(
        OrbDropletSpecV1("D1", 0.32f, 0.36f, 1.8f, 0.23f, 0.38f, 5200L, 0.12f),
        OrbDropletSpecV1("D2", 0.68f, 0.56f, 2.2f, 0.26f, 0.44f, 6100L, 0.49f),
        OrbDropletSpecV1("D3", 0.49f, 0.73f, 1.4f, 0.19f, 0.34f, 4800L, 0.83f),
    )

    val motionKeys: List<MotionKey> = listOf(
        MotionKey(0.0f, 0.0f),
        MotionKey(0.18f, 0.105f),
        MotionKey(0.45f, 0.43f),
        MotionKey(0.75f, 0.8f),
        MotionKey(0.92f, 0.95f),
        MotionKey(1.0f, 1.0f),
    )

    val materials: Map<String, OrbFilamentMaterialV1> = mapOf(
        "green_water_ribbon" to OrbFilamentMaterialV1("green_water_ribbon", 0x5B8F78L, 0xA9D9C5L, 0x326F67L, 1.75f, listOf(OrbMaterialLayerV1("outer_feather", 1.8f, 0.1f, 1.4f, 0x326F67L, null, null, false), OrbMaterialLayerV1("translucent_body", 1.0f, 0.6f, null, 0x5B8F78L, null, null, false), OrbMaterialLayerV1("inner_refractive_band", 0.48f, 0.34f, null, 0x326F67L, -0.1f, null, false), OrbMaterialLayerV1("specular_highlight", 0.24f, 0.68f, null, 0xA9D9C5L, null, 0.13f, true)), listOf(OrbCurvePointV1(-1.0f, 0.02f), OrbCurvePointV1(-0.62f, 0.19f), OrbCurvePointV1(-0.22f, 0.38f), OrbCurvePointV1(0.0f, 0.46f), OrbCurvePointV1(0.28f, 0.34f), OrbCurvePointV1(0.72f, 0.15f), OrbCurvePointV1(1.0f, 0.02f)), listOf(OrbCurvePointV1(0.0f, 0.28f), OrbCurvePointV1(0.1f, 0.55f), OrbCurvePointV1(0.32f, 0.82f), OrbCurvePointV1(0.58f, 0.92f), OrbCurvePointV1(0.82f, 0.72f), OrbCurvePointV1(1.0f, 0.46f)), listOf(OrbCurvePointV1(0.0f, 0.1f), OrbCurvePointV1(0.12f, 0.5f), OrbCurvePointV1(0.45f, 0.9f), OrbCurvePointV1(0.76f, 0.78f), OrbCurvePointV1(1.0f, 0.36f)), 1.35f, 0.82f, 0.22f, "elliptical_head_plus_moving_band", 0.55f, 0.95f, 0.72f, 0.13f, 1.4f, 0.1f, 0x326F67L, 0.23f, 0.006f),
        "cyan_water_ribbon" to OrbFilamentMaterialV1("cyan_water_ribbon", 0x348CA6L, 0x79D7E4L, 0xB4F1E8L, 1.5f, listOf(OrbMaterialLayerV1("outer_feather", 1.55f, 0.09f, 1.2f, 0x348CA6L, null, null, false), OrbMaterialLayerV1("translucent_body", 1.0f, 0.54f, null, 0x348CA6L, null, null, false), OrbMaterialLayerV1("inner_refractive_band", 0.38f, 0.38f, null, 0xB4F1E8L, 0.14f, null, false), OrbMaterialLayerV1("specular_highlight", 0.2f, 0.72f, null, 0x79D7E4L, null, 0.11f, true)), listOf(OrbCurvePointV1(-1.0f, 0.01f), OrbCurvePointV1(-0.58f, 0.18f), OrbCurvePointV1(-0.25f, 0.38f), OrbCurvePointV1(0.0f, 0.41f), OrbCurvePointV1(0.3f, 0.3f), OrbCurvePointV1(0.72f, 0.13f), OrbCurvePointV1(1.0f, 0.01f)), listOf(OrbCurvePointV1(0.0f, 0.22f), OrbCurvePointV1(0.14f, 0.52f), OrbCurvePointV1(0.36f, 0.92f), OrbCurvePointV1(0.6f, 0.86f), OrbCurvePointV1(0.84f, 0.63f), OrbCurvePointV1(1.0f, 0.4f)), listOf(OrbCurvePointV1(0.0f, 0.09f), OrbCurvePointV1(0.15f, 0.46f), OrbCurvePointV1(0.43f, 0.88f), OrbCurvePointV1(0.73f, 0.7f), OrbCurvePointV1(1.0f, 0.3f)), 1.42f, 0.76f, 0.18f, "moving_water_glint", 0.42f, 0.78f, 0.78f, 0.11f, 1.2f, 0.09f, 0x348CA6L, 0.2f, 0.006f),
        "warm_gold_filament" to OrbFilamentMaterialV1("warm_gold_filament", 0xD8BC87L, 0xFFE6B0L, 0x8D754BL, 1.0f, listOf(OrbMaterialLayerV1("soft_tail", 1.9f, 0.12f, 0.75f, 0xD8BC87L, null, null, false), OrbMaterialLayerV1("fine_filament", 1.0f, 0.78f, null, 0xD8BC87L, null, null, false), OrbMaterialLayerV1("sun_glint", 0.34f, 0.92f, null, 0xFFE6B0L, null, 0.04f, true)), listOf(OrbCurvePointV1(-1.0f, 0.02f), OrbCurvePointV1(-0.45f, 0.38f), OrbCurvePointV1(0.0f, 0.9f), OrbCurvePointV1(0.45f, 0.38f), OrbCurvePointV1(1.0f, 0.02f)), listOf(OrbCurvePointV1(0.0f, 0.34f), OrbCurvePointV1(0.15f, 0.62f), OrbCurvePointV1(0.43f, 0.48f), OrbCurvePointV1(0.7f, 0.77f), OrbCurvePointV1(0.91f, 1.0f), OrbCurvePointV1(1.0f, 0.38f)), listOf(OrbCurvePointV1(0.0f, 0.12f), OrbCurvePointV1(0.14f, 0.52f), OrbCurvePointV1(0.42f, 0.8f), OrbCurvePointV1(0.74f, 0.56f), OrbCurvePointV1(1.0f, 0.36f)), 1.3f, 0.68f, 0.14f, "small_irregular_glint_cluster", 0.22f, 0.44f, 0.95f, 0.045f, 0.75f, 0.12f, 0xD8BC87L, 0.15f, 0.004f),
    )

    val paths: List<OrbFilamentSpecV1> = listOf(
        OrbFilamentSpecV1("G01", "green_water_ribbon", false, OrbPointV1(-0.55f, 0.29f), listOf(OrbCubicV1(OrbPointV1(-0.61f, -0.17f), OrbPointV1(-0.43f, -0.55f), OrbPointV1(-0.16f, -0.51f)), OrbCubicV1(OrbPointV1(0.15f, -0.62f), OrbPointV1(0.51f, -0.2f), OrbPointV1(0.44f, 0.22f))), 2.118582f, 0.621772f, 0.675f, 6100L, 0.07f, 1.5f, 3.2f, 0.18f, 0.35f, 0.007f, 10065L, 0.006f),
        OrbFilamentSpecV1("G02", "green_water_ribbon", true, OrbPointV1(-0.58f, 0.17f), listOf(OrbCubicV1(OrbPointV1(-0.39f, 0.42f), OrbPointV1(-0.07f, 0.42f), OrbPointV1(0.14f, 0.22f)), OrbCubicV1(OrbPointV1(0.37f, 0.04f), OrbPointV1(0.58f, 0.22f), OrbPointV1(0.45f, 0.45f))), 1.430731f, 0.636396f, 0.675f, 5300L, 0.34f, 1.4f, 2.7f, 0.18f, 0.38f, 0.007f, 8745L, 0.006f),
        OrbFilamentSpecV1("A01", "warm_gold_filament", false, OrbPointV1(-0.54f, -0.2f), listOf(OrbCubicV1(OrbPointV1(-0.25f, -0.58f), OrbPointV1(0.19f, -0.6f), OrbPointV1(0.53f, -0.26f)), OrbCubicV1(OrbPointV1(0.36f, -0.15f), OrbPointV1(0.41f, 0.25f), OrbPointV1(0.56f, 0.32f))), 1.894683f, 0.644981f, 0.675f, 3700L, 0.59f, 0.55f, 0.92f, 0.45f, 0.69f, 0.004f, 6105L, 0.006f),
        OrbFilamentSpecV1("A02", "warm_gold_filament", true, OrbPointV1(-0.55f, 0.25f), listOf(OrbCubicV1(OrbPointV1(-0.3f, 0.05f), OrbPointV1(-0.07f, 0.48f), OrbPointV1(0.2f, 0.47f)), OrbCubicV1(OrbPointV1(0.44f, 0.51f), OrbPointV1(0.53f, 0.2f), OrbPointV1(0.59f, -0.04f))), 1.539713f, 0.604152f, 0.675f, 2900L, 0.81f, 0.55f, 1.0f, 0.45f, 0.72f, 0.004f, 4785L, 0.006f),
        OrbFilamentSpecV1("C01", "cyan_water_ribbon", true, OrbPointV1(-0.52f, -0.13f), listOf(OrbCubicV1(OrbPointV1(-0.31f, 0.25f), OrbPointV1(-0.19f, 0.34f), OrbPointV1(0.03f, 0.35f)), OrbCubicV1(OrbPointV1(0.3f, 0.48f), OrbPointV1(0.48f, 0.28f), OrbPointV1(0.56f, 0.02f))), 1.49934f, 0.560357f, 0.675f, 4400L, 0.23f, 1.4f, 2.8f, 0.18f, 0.4f, 0.007f, 7260L, 0.006f),
    )
}
