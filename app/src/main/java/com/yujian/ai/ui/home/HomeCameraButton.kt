package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    val scope = rememberCoroutineScope()
    val activationJob = remember { arrayOfNulls<Job>(1) }
    val transitionActive = CaptureNavigationTransition.active.value
    var clickElapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(transitionActive) {
        while (transitionActive) {
            withFrameNanos { clickElapsedMs = CaptureNavigationTransition.elapsedMs() }
        }
    }
    val context = LocalContext.current
    val view = LocalView.current
    val resolvedSize = visualSize ?: CaptureVisualTokens.ButtonDiameterDp.dp
    fun activate() {
        if (CaptureNavigationTransition.active.value) return
        CaptureNavigationTransition.homeDiameterDp = resolvedSize.value
        CaptureNavigationTransition.begin()
        activationJob[0] = scope.launch {
            delay(CaptureVisualTokens.ActivationMs)
            CaptureNavigationTransition.entering.value = true
            CaptureNavigationTransition.log("CAMERA_ENTERING")
            onClick()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) activate() else onClick()
    }
    BackHandler(enabled = transitionActive && !CaptureNavigationTransition.entering.value) {
        activationJob[0]?.cancel()
        CaptureNavigationTransition.finish()
    }
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
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) activate()
            else permission.launch(Manifest.permission.CAMERA)
        },
        modifier = modifier.alpha(if (CaptureNavigationTransition.entering.value) 0f else 1f)
            .then(if (CaptureNavigationTransition.entering.value) Modifier.clearAndSetSemantics {} else Modifier)
            .onGloballyPositioned {
            val bounds = it.boundsInWindow()
            CaptureNavigationTransition.homeCenter = bounds.center
        },
        enabled = enabled,
        motionEnabled = active,
        rasterAssets = rasterAssets,
        rasterMotion = YuJianCaptureButtonRasterMotion(
            breathScale = cameraBreathScale(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            breathGlowAlpha = cameraBreathGlowAlpha(sceneTime + CAMERA_BREATH_PHASE_OFFSET_SECONDS),
            sweepRotationDegrees = sweep.rotationDegrees,
            sweepAlpha = sweep.alpha,
        ),
        visualSize = resolvedSize,
        touchTargetSize = maxOf(resolvedSize, touchTargetSize ?: resolvedSize),
        visualModifier = Modifier.testTag("gemini-home-camera-art"),
        frozenVisual = true,
        onCaptureHaptic = { if (!transitionActive) captureClickHaptic(context, view) },
        clickElapsedMs = if (transitionActive) clickElapsedMs else null,
    )
}
