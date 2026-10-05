package com.yujian.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YujianColorScheme = lightColorScheme(
    primary = WaterTeal,
    onPrimary = Color.White,
    primaryContainer = SoftWater,
    onPrimaryContainer = DeepInk,
    secondary = WarmOrange,
    background = WarmBackground,
    onBackground = DeepInk,
    surface = CardWhite,
    onSurface = DeepInk,
    outline = Hairline,
)

@Composable
fun YujianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YujianColorScheme,
        content = content,
    )
}
