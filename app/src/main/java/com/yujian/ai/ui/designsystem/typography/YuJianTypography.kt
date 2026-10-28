package com.yujian.ai.ui.designsystem.typography

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.designsystem.color.YuJianColors

/**
 * Runtime type roles mapped from typography_tokens.json.
 *
 * The source distinguishes BRAND, DISPLAY, and UI. This map keeps those roles
 * legible on Android's scalable text system without treating reference pixels
 * as literal device pixels.
 */
object YuJianTypography {
    private val DisplayFamily = FontFamily.SansSerif
    private val UiFamily = FontFamily.SansSerif

    val brand = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = YuJianColors.TextPrimary,
    )
    val pageTitle = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = YuJianColors.TextPrimary,
    )
    val sectionTitle = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = YuJianColors.TextPrimary,
    )
    val heroTitle = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        color = YuJianColors.OnDark,
    )
    val body = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = YuJianColors.TextPrimary,
    )
    val caption = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = YuJianColors.TextSecondary,
    )
    val dataNumber = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = YuJianColors.TextPrimary,
    )
    val buttonText = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = YuJianColors.OnDark,
    )
}
