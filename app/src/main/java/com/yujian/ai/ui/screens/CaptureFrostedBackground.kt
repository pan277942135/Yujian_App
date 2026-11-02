package com.yujian.ai.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yujian.ai.ui.designsystem.lakeglass.LakeGlassBackgroundHost

/** Shared Lake Glass environment behind the protected live preview and capture controls. */
@Composable
internal fun CaptureFrostedBackground(modifier: Modifier = Modifier) {
    LakeGlassBackgroundHost(modifier = modifier)
}
