package com.yujian.ai.ui.home

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.components.AssetImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CAMERA_OUTER = "home_empty_v1_3/assets/camera/camera_outer.png"
private const val CAMERA_INNER = "home_empty_v1_3/assets/camera/camera_inner.png"
private const val PRESS_DEBOUNCE_MS = 650L
private const val PRESS_DURATION_MS = 190

/** The single camera component shared by Empty and Normal Home. */
@Composable
fun HomeCameraButton(onClick: () -> Unit) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val motionState = rememberHomeMotionState()
    val scope = rememberCoroutineScope()
    val breathOuterScale = remember { Animatable(1f) }
    val breathInnerScale = remember { Animatable(1f) }
    val breathOuterAlpha = remember { Animatable(0.65f) }
    val pressProgress = remember { Animatable(0f) }
    var lastClickMs by remember { mutableStateOf(0L) }

    LaunchedEffect(motionState.running) {
        if (!motionState.running) {
            breathOuterScale.snapTo(1f)
            breathInnerScale.snapTo(1f)
            breathOuterAlpha.snapTo(0.65f)
            return@LaunchedEffect
        }
        while (isActive) {
            coroutineScope {
                launch {
                    breathOuterScale.animateTo(1f, keyframes {
                        durationMillis = 3000
                        1.04f at 1500
                    })
                }
                launch {
                    breathInnerScale.animateTo(1f, keyframes {
                        durationMillis = 3000
                        1.012f at 1500
                    })
                }
                launch {
                    breathOuterAlpha.animateTo(0.65f, keyframes {
                        durationMillis = 3000
                        0.88f at 1500
                    })
                }
            }
        }
    }

    val buttonSize = when {
        configuration.screenHeightDp < 720 -> 96.dp
        configuration.screenHeightDp <= 820 -> 100.dp
        else -> 104.dp
    }
    val iconSize = buttonSize * 0.5f
    val press = pressProgress.value
    val pressedOuterScale = breathOuterScale.value * (1f + 0.015f * press)
    val pressedInnerScale = breathInnerScale.value * (1f - 0.08f * press)
    val pressedIconScale = 1f - 0.05f * press
    val pressedOuterAlpha = (breathOuterAlpha.value * (1f + 0.12f * press)).coerceAtMost(1f)

    Box(
        Modifier
            .size(buttonSize)
            .semantics {
                contentDescription = "拍照识鱼"
                role = Role.Button
            }
            .clickable {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickMs < PRESS_DEBOUNCE_MS) return@clickable
                lastClickMs = now
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                scope.launch {
                    pressProgress.snapTo(0f)
                    pressProgress.animateTo(0f, keyframes {
                        durationMillis = PRESS_DURATION_MS
                        1f at 70
                        0f at PRESS_DURATION_MS
                    })
                }
                onClick()
            },
    ) {
        AssetImage(
            CAMERA_OUTER,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressedOuterScale
                    scaleY = pressedOuterScale
                    alpha = pressedOuterAlpha
                },
            contentDescription = null,
        )
        AssetImage(
            CAMERA_INNER,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressedInnerScale
                    scaleY = pressedInnerScale
                },
            contentDescription = null,
        )
        Image(
            painter = painterResource(R.drawable.camera_icon_v13),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = pressedIconScale
                    scaleY = pressedIconScale
                },
        )
    }
}
