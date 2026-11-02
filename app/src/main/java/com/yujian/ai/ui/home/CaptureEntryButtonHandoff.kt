package com.yujian.ai.ui.home

import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCaptureVisualAssets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** One topmost render owner keeps the exact Home physical center through the 580ms handoff. */
@Composable
fun CaptureEntryButtonHandoff() {
    val active = CaptureNavigationTransition.active.value
    val sessionId = CaptureNavigationTransition.sessionId
    var elapsedMs by remember(sessionId) {
        mutableLongStateOf(CaptureNavigationTransition.elapsedMs())
    }

    LaunchedEffect(active, sessionId) {
        while (active && CaptureNavigationTransition.active.value) {
            withFrameNanos {
                elapsedMs = CaptureNavigationTransition.elapsedMs()
                if (elapsedMs >= CaptureNavigationTransition.HeroTotalTransitionMs) {
                    CaptureNavigationTransition.finish()
                }
            }
        }
    }
    if (!active) return

    var origin by remember { mutableStateOf(Offset.Zero) }
    val center = CaptureNavigationTransition.homeCenter - origin
    val diameter = CaptureNavigationTransition.homeDiameterDp.dp
    // 1.22x scale plus 36dp outward click water-light has reserved transparent bounds.
    val containerSize = diameter * 1.45f + 48.dp
    val half = with(LocalDensity.current) { containerSize.toPx() / 2f }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f)
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
                .size(containerSize),
            contentAlignment = Alignment.Center,
        ) {
            AIOrbCore(
                state = AIOrbVisualState.CAPTURE_BUTTON_IDLE,
                onClick = {},
                motionEnabled = true,
                visualSize = diameter,
                touchTargetSize = diameter,
                frozenVisual = true,
                frozenVisualResource = AIOrbCaptureVisualAssets.HOME_IDLE,
                cameraReady = true,
                onCaptureHaptic = {},
                clickElapsedMs = elapsedMs,
            )
        }
    }
}
