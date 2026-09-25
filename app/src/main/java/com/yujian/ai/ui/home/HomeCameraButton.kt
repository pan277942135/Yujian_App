package com.yujian.ai.ui.home

import androidx.compose.runtime.Composable
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton

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
    @Suppress("UNUSED_PARAMETER") runtimeAssets: EmptyHomeRuntimeAssets? = null,
) {
    YuJianCaptureButton(
        onClick = onClick,
        motionEnabled = emptyHomeMotionActive(
            running = motionState.running,
            reduceMotion = motionState.reduceMotion,
        ),
    )
}
