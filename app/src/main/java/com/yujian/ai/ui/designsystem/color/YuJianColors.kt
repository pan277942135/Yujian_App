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

    // Frozen Account/Auth action-state colors, shared by public controls.
    val ActionPrimary = Color(0xFF0F7A78)
    val ActionPrimaryPressed = Color(0xFF0C6D6B)
    val ActiveAccent = Color(0xFF168B88)
    val ActionDisabledSurface = Color(0xFFF2F5F5)
    val ActionDisabledContent = Color(0xFFA0ADAF)
    val PrimaryActionDisabledSurface = Color(0xFFBFD8D4)
    val PrimaryActionDisabledContent = Color(0xFF355C5B)
    val PrimaryActionDisabledBorder = Color(0xFF8EAFAB)
    val ActionDisabledBorder = Color(0xFFE3E9E9)

    val GlassWhite = Color(0xC2FFFFFF)
    val GlassBorder = Color(0x8AFFFFFF)
    val DeepInk = DeepLakeBlue
    val TextPrimary = DeepLakeBlue
    val TextSecondary = MistBlueGray
    val OnDark = LakeWhite
}
