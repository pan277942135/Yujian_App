package com.yujian.ai.ui.waterlight.renderer

internal object OrbGrassMotionProfileV11 {
    data class Leaf(
        val id: String,
        val rootU: Float,
        val rootV: Float,
        val c1U: Float,
        val c1V: Float,
        val c2U: Float,
        val c2V: Float,
        val tipU: Float,
        val tipV: Float,
        val periodMs: Long,
        val phaseDegrees: Float,
        val amplitudeD: Float,
        val rootMaxD: Float,
        val maskAsset: String,
    )

    const val MASTER_SIZE = 1254f
    const val RUNTIME_SIZE = 384f
    /** Content mapping verified against processing_water_core_v1.webp; path UV covers the full square. */
    const val SOURCE_TO_RUNTIME_SCALE = RUNTIME_SIZE / MASTER_SIZE
    /** Existing Core host sizes the full source square at coreSize / .84; coreSize is contract D. */
    const val VISIBLE_DIAMETER_TO_SOURCE_UV = .84f
    const val AGSL_ASSET = "orb_r01b_v1_1/orb_grass_warp_v1_1.agsl"
    const val ASSET_DIR = "orb_r01b_v1_1/"

    val leaves = listOf(
        Leaf("BACK_B1", .689793f, .527113f, .720893f, .438596f, .666667f, .296651f, .602073f, .259968f,
            6200L, 0f, .018f, 0f, "orb_grass_back_b1_mask_v1_1.png"),
        Leaf("BACK_B2", .650718f, .757576f, .746411f, .635566f, .767145f, .491228f, .747209f, .384370f,
            6200L, 100f, .018f, 0f, "orb_grass_back_b2_mask_v1_1.png"),
        Leaf("FRONT_F1", .401914f, .749601f, .279904f, .637959f, .185008f, .524721f, .164274f, .396332f,
            4800L, 120f, .030f, .003f, "orb_grass_front_f1_mask_v1_1.png"),
        Leaf("FRONT_F2", .491228f, .775917f, .382775f, .735247f, .275120f, .649123f, .208931f, .573365f,
            4800L, 240f, .027f, .003f, "orb_grass_front_f2_mask_v1_1.png"),
    )
}
