package com.yujian.ai.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.components.AssetImage

private const val CAMERA_OUTER = "home_empty_v1_2/assets/camera/camera_outer.png"
private const val CAMERA_INNER = "home_empty_v1_2/assets/camera/camera_inner.png"

/** The single camera component shared by Empty and Normal Home. */
@Composable
fun HomeCameraButton(onClick: () -> Unit) {
    val view = LocalView.current
    val running = rememberHomeMotionRunning()
    val outerScale = remember { Animatable(1f) }
    val innerScale = remember { Animatable(1f) }
    val outerAlpha = remember { Animatable(0.65f) }

    LaunchedEffect(running) {
        if (!running) {
            outerScale.snapTo(1f)
            innerScale.snapTo(1f)
            outerAlpha.snapTo(0.65f)
            return@LaunchedEffect
        }
        while (isActive) {
            coroutineScope {
                launch {
                    outerScale.animateTo(1f, keyframes {
                        durationMillis = 3000
                        1.04f at 1500
                    })
                }
                launch {
                    innerScale.animateTo(1f, keyframes {
                        durationMillis = 3000
                        1.015f at 1500
                    })
                }
                launch {
                    outerAlpha.animateTo(0.65f, keyframes {
                        durationMillis = 3000
                        0.9f at 1500
                    })
                }
            }
        }
    }

    Box(
        Modifier
            .size(104.dp)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
    ) {
        AssetImage(
            CAMERA_OUTER,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = outerScale.value
                    scaleY = outerScale.value
                    alpha = outerAlpha.value
                },
            contentDescription = null,
        )
        AssetImage(
            CAMERA_INNER,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = innerScale.value
                    scaleY = innerScale.value
                },
            contentDescription = null,
        )
        Image(
            painter = painterResource(R.drawable.camera_icon_v12),
            contentDescription = "开始识鱼",
            modifier = Modifier.size(54.dp).align(Alignment.Center),
        )
    }
}
