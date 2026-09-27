package com.yujian.ai.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterAssets
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButtonRasterMotion

/**
 * Home compatibility entry point.
 *
 * Empty Home, Normal Home, and the camera surface keep their existing
 * navigation callbacks while the visual control is provided by the shared
 * native Core UI V1 capture component. [runtimeAssets] remains in this small
 * adapter only so the V2 scene call site does not change behavior.
 */
@Composable
internal fun HomeCameraButton(
    onClick: () -> Unit,
    motionState: HomeMotionState = rememberHomeMotionState(),
    runtimeAssets: EmptyHomeRuntimeAssets? = null,
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
        motionEnabled = active,
        rasterAssets = rasterAssets,
        rasterMotion = YuJianCaptureButtonRasterMotion(
            breathScale = cameraBreathScale(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            breathGlowAlpha = cameraBreathGlowAlpha(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            sweepRotationDegrees = sweep.rotationDegrees,
            sweepAlpha = sweep.alpha,
        ),
    )
}
