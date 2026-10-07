package com.yujian.ai.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterAssets
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterMotion

internal interface HomeCameraRasterAssets {
    val cameraBase: Bitmap
    val cameraGoldRim: Bitmap
    val cameraBreathGlow: Bitmap
}

/**
 * Home compatibility entry point.
 *
 * Empty Home may pass the Frozen V2 reference-space visual size while shared
 * callers retain the design-system defaults.
 */
@Composable
internal fun HomeCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    motionState: HomeMotionState = rememberHomeMotionState(),
    runtimeAssets: HomeCameraRasterAssets? = null,
    visualSize: Dp? = null,
    touchTargetSize: Dp? = null,
) {
    val active = emptyHomeMotionActive(
        running = motionState.running,
        reduceMotion = motionState.reduceMotion,
    )
    val rasterAssets = remember(runtimeAssets) {
        runtimeAssets?.let {
            YuJianCaptureButtonRasterAssets(
                base = it.cameraBase.asImageBitmap(),
                goldRimSweep = it.cameraGoldRim.asImageBitmap(),
                breathGlow = it.cameraBreathGlow.asImageBitmap(),
            )
        }
    }
    val sceneTime = if (active) motionState.sceneTimeSeconds else 0f
    val sweep = cameraSweepState(sceneTime)
    YuJianCaptureButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        motionEnabled = active,
        rasterAssets = rasterAssets,
        rasterMotion = YuJianCaptureButtonRasterMotion(
            breathScale = cameraBreathScale(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            breathGlowAlpha = cameraBreathGlowAlpha(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            sweepRotationDegrees = sweep.rotationDegrees,
            sweepAlpha = sweep.alpha,
        ),
        visualSize = visualSize,
        touchTargetSize = touchTargetSize,
    )
}
