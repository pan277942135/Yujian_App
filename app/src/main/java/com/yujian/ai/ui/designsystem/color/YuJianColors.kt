package com.yujian.ai.ui.designsystem.color

import androidx.compose.ui.graphics.Color

/**
 * Compose mapping of design/system/core_visual_v1/tokens/color_tokens.json.
 *
 * Keep page code on these semantic tokens instead of introducing page-local
 * [Color] literals. Gold remains reserved for brand and meaningful moments.
 */
object YuJianColors {
    val DeepLakeBlue = Color(0xFF0B2D4B)
    val LakeBlue = Color(0xFF34556F)
    val MistBlueGray = Color(0xFF748897)
    val LakeWhite = Color(0xFFF7FAFB)
    val MistWhite = Color(0xADFFFFFF)
    val MorningGold = Color(0xFFD6A541)
    val SoftGold = Color(0xFFE5C77C)
    val DeepOverlay = Color(0x42081926)

    val GlassWhite = Color(0xC2FFFFFF)
    val GlassBorder = Color(0x8AFFFFFF)
    val DeepInk = DeepLakeBlue
    val TextPrimary = DeepLakeBlue
    val TextSecondary = MistBlueGray
    val OnDark = LakeWhite
}
