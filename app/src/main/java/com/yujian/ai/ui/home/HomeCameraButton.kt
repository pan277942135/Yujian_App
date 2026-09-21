package com.yujian.ai.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.components.AssetImage

private const val LEGACY_CAMERA_OUTER = "home_empty_v1_3/assets/camera/camera_outer.png"
private const val LEGACY_CAMERA_INNER = "home_empty_v1_3/assets/camera/camera_inner.png"

/**
 * The camera hitbox remains fixed while the visual base, breath and rim sweep
 * share the Empty Home SceneClock.
 */
@Composable
fun HomeCameraButton(
    onClick: () -> Unit,
    motionState: HomeMotionState = rememberHomeMotionState(),
    runtimeAssets: EmptyHomeRuntimeAssets? = null,
) {
    val view = LocalView.current
    val cameraScale = if (!motionState.running) {
        1f
    } else {
        cameraBreathScale(motionState.sceneTimeSeconds)
    }
    val glowAlpha = if (runtimeAssets == null || !motionState.running) {
        0f
    } else {
        cameraBreathGlowAlpha(motionState.sceneTimeSeconds)
    }
    val sweep = if (runtimeAssets == null || !motionState.running) {
        CameraSweepState(rotationDegrees = 330f, alpha = 0f)
    } else {
        cameraSweepState(motionState.sceneTimeSeconds)
    }

    Box(
        Modifier
            .size(104.dp)
            .semantics {
                contentDescription = "开始识鱼"
                role = Role.Button
            }
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (runtimeAssets == null) {
            AssetImage(
                LEGACY_CAMERA_OUTER,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cameraScale
                        scaleY = cameraScale
                    },
                contentDescription = null,
            )
            AssetImage(
                LEGACY_CAMERA_INNER,
                Modifier.fillMaxSize(),
                contentDescription = null,
            )
        } else {
            Image(
                bitmap = runtimeAssets.cameraBase.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = cameraScale
                        scaleY = cameraScale
                    },
                contentScale = ContentScale.FillBounds,
            )
            Image(
                bitmap = runtimeAssets.cameraBreathGlow.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        alpha = glowAlpha
                        scaleX = cameraScale
                        scaleY = cameraScale
                    },
                contentScale = ContentScale.FillBounds,
            )
            Image(
                bitmap = runtimeAssets.cameraGoldRim.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        rotationZ = sweep.rotationDegrees
                        alpha = sweep.alpha
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}
