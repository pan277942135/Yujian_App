package com.yujian.ai.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Apply only to Home information, never to scenic background or capture prompt. */
@Composable
internal fun Modifier.captureHomeExit(): Modifier {
    val progress by animateFloatAsState(
        if (CaptureNavigationTransition.active.value) 1f else 0f,
        tween(500), label = "capture-home-information-exit",
    )
    return this.graphicsLayer { alpha = 1f - progress }.blur((progress * 12f).dp)
}
