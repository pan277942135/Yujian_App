package com.yujian.ai.ui.home

import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A single button above both fading pages. Neither page draws a duplicate in flight. */
@Composable
fun CaptureEntryButtonHandoff() {
    val active = CaptureNavigationTransition.active.value && CaptureNavigationTransition.entering.value
    val target = CaptureNavigationTransition.cameraCenter.value
    val progress = remember(CaptureNavigationTransition.sessionId) { Animatable(0f) }
    LaunchedEffect(active, target) {
        if (active && target.x.isFinite()) {
            progress.animateTo(1f, tween(CaptureVisualTokens.HandoffMs))
            CaptureNavigationTransition.finish()
        }
    }
    if (!active) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val start = CaptureNavigationTransition.homeCenter
    val end = target.takeIf { it.x.isFinite() } ?: start
    val t = progress.value
    val center = start + (end - start) * t - origin
    val diameter = (CaptureNavigationTransition.homeDiameterDp +
        (CaptureNavigationTransition.cameraDiameterDp.value - CaptureNavigationTransition.homeDiameterDp) * t).dp
    val half = with(LocalDensity.current) { (diameter + 28.dp).toPx() / 2f }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }
        .clearAndSetSemantics { }) {
        Box(Modifier.offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(diameter + 28.dp), contentAlignment = Alignment.Center) {
            AIOrbCore(state = AIOrbVisualState.CAPTURE_BUTTON_IDLE, onClick = {}, visualSize = diameter, touchTargetSize = diameter,
                frozenVisual = true, cameraReady = true, onCaptureHaptic = {})
        }
    }
}
