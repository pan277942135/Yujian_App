package com.yujian.ai.ui.home

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

internal const val REFERENCE_SCENE_WIDTH = 1080f
internal const val REFERENCE_SCENE_HEIGHT = 1920f
internal const val EMPTY_HOME_V2_BOBBER_X = 518f
internal const val EMPTY_HOME_V2_BOBBER_Y = 1084f
internal const val EMPTY_HOME_V2_RIPPLE_X = 411f
internal const val EMPTY_HOME_V2_RIPPLE_Y = 1127f
internal const val EMPTY_HOME_V2_WATER_CONTACT_X = 530f
internal const val EMPTY_HOME_V2_WATER_CONTACT_Y = 1168f

// Shared Empty Home fishing contact geometry in reference-canvas pixels.
// The supplied bobber is rendered by its transparent canvas, while its
// visible alpha bounds remain approximately 15 x 49 px.
internal const val WATER_CONTACT_X = 750f
internal const val WATER_CONTACT_Y = 1158f
internal const val BOBBER_RENDER_X = 739.9f
internal const val BOBBER_RENDER_Y = 1105.24f
internal const val BOBBER_RENDER_WIDTH = 21.21f
internal const val BOBBER_RENDER_HEIGHT = 53.75f

/**
 * One frame clock shared by the Empty Home scene and its camera action layer.
 * The clock intentionally stops while the host lifecycle is paused.
 */
@Stable
internal class HomeMotionState {
    var running by mutableStateOf(false)
        internal set
    var reduceMotion by mutableStateOf(false)
        internal set
    var sceneTimeNanos by mutableLongStateOf(0L)
        internal set

    val sceneTimeSeconds: Float get() = sceneTimeNanos / 1_000_000_000f
}

@Composable
internal fun rememberHomeMotionState(): HomeMotionState {
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    val motionState = remember { HomeMotionState() }
    var lifecycleResumed by remember(view) { mutableStateOf(true) }

    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        if (lifecycle == null) {
            onDispose { }
        } else {
            lifecycleResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> lifecycleResumed = true
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    -> lifecycleResumed = false
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    val reduceMotion = remember(context) {
        runCatching {
            val resolver = context.contentResolver
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f || Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }

    LaunchedEffect(lifecycleResumed, reduceMotion) {
        if (!lifecycleResumed || reduceMotion) {
            if (reduceMotion) {
                motionState.sceneTimeNanos = 0L
            }
            return@LaunchedEffect
        }

        var originNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (originNanos == 0L) {
                    originNanos = frameNanos - motionState.sceneTimeNanos
                }
                val next = max(0L, frameNanos - originNanos)
                motionState.sceneTimeNanos = next
            }
        }
    }

    motionState.running = lifecycleResumed && !reduceMotion
    motionState.reduceMotion = reduceMotion
    return motionState
}

@Composable
internal fun rememberHomeMotionRunning(): Boolean = rememberHomeMotionState().running

internal data class ReferenceSceneTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun calculateReferenceSceneTransform(
    containerWidthPx: Float,
    containerHeightPx: Float,
): ReferenceSceneTransform {
    val scale = max(
        containerWidthPx / REFERENCE_SCENE_WIDTH,
        containerHeightPx / REFERENCE_SCENE_HEIGHT,
    )
    return ReferenceSceneTransform(
        scale = scale,
        offsetX = (containerWidthPx - REFERENCE_SCENE_WIDTH * scale) / 2f,
        offsetY = (containerHeightPx - REFERENCE_SCENE_HEIGHT * scale) / 2f,
    )
}

internal fun bobberOffsetPx(timeSeconds: Float): Float =
    -3f * sin((timeSeconds % 4.6f) / 4.6f * (2f * PI.toFloat()))

internal fun rippleProgress(timeSeconds: Float): Float =
    (timeSeconds % 3.2f) / 3.2f

internal fun rippleScale(timeSeconds: Float): Float =
    1f + 0.22f * rippleProgress(timeSeconds)

internal fun rippleAlpha(timeSeconds: Float): Float =
    0.30f * (1f - rippleProgress(timeSeconds))

internal fun cloudOffsetPx(timeSeconds: Float): Float =
    (timeSeconds % 60f) * 0.2f

internal fun sunBeamEnvelope(timeSeconds: Float): Float {
    val phase = (timeSeconds % 4.8f) / 4.8f
    return sin(phase * PI.toFloat()).coerceAtLeast(0f)
}

internal fun sunGlowAlpha(timeSeconds: Float): Float {
    val phase = (timeSeconds % 20f) / 20f
    return 0.975f + 0.025f * cos(phase * (2f * PI.toFloat()))
}

internal fun sunGlowScale(timeSeconds: Float): Float {
    val phase = (timeSeconds % 20f) / 20f
    return 1f + 0.01f * (1f - cos(phase * (2f * PI.toFloat())))
}

internal fun cameraBreathScale(timeSeconds: Float): Float {
    val phase = (timeSeconds % 5f) / 5f
    return 1f + 0.015f * (0.5f - 0.5f * cos(phase * (2f * PI.toFloat())))
}

internal fun cameraBreathGlowAlpha(timeSeconds: Float): Float {
    val phase = (timeSeconds % 5f) / 5f
    return 0.006f * (0.5f - 0.5f * cos(phase * (2f * PI.toFloat())))
}

internal data class CameraSweepState(
    val rotationDegrees: Float,
    val alpha: Float,
)

internal fun cameraSweepState(timeSeconds: Float): CameraSweepState {
    if (timeSeconds < 3f) return CameraSweepState(rotationDegrees = 330f, alpha = 0f)
    val elapsed = (timeSeconds - 3f) % 9f
    if (elapsed > 1.4f) return CameraSweepState(rotationDegrees = 330f, alpha = 0f)
    val progress = elapsed / 1.4f
    return CameraSweepState(
        rotationDegrees = 330f + progress * 360f,
        alpha = 0.38f * sin(progress * PI.toFloat()).coerceAtLeast(0f),
    )
}

internal data class SunParticleSpec(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val driftX: Float,
    val travelY: Float,
    val phase: Float,
)

internal fun createSunParticleSpecs(
    seed: Int = 20260920,
    count: Int = 10,
): List<SunParticleSpec> {
    val random = Random(seed)
    return List(count) {
        SunParticleSpec(
            x = 760f + random.nextFloat() * 170f,
            y = 720f + random.nextFloat() * 260f,
            radius = 1.5f + random.nextFloat() * 2.5f,
            alpha = 0.035f + random.nextFloat() * 0.085f,
            driftX = -8f + random.nextFloat() * 16f,
            travelY = 6f + random.nextFloat() * 18f,
            phase = random.nextFloat(),
        )
    }
}
