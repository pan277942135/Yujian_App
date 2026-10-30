package com.yujian.ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.components.CaptureButtonOptics

/** The shared click light follows the button across the home/camera handoff. */
@Composable
fun CaptureTransitionLightField(progress: Float, modifier: Modifier = Modifier) {
    CaptureButtonOptics(visualSize = 76.dp, progress = progress, click = true, modifier = modifier)
}
