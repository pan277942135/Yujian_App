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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

internal const val REFERENCE_SCENE_WIDTH = 1080f
internal const val REFERENCE_SCENE_HEIGHT = 1920f
// V2.2 Fishing Composition anchors with the approved V2.3 physical-device
// correction for the line, bobber and open-water contact point.
internal const val EMPTY_HOME_V2_ROD_X = 0f
internal const val EMPTY_HOME_V2_ROD_Y = 1180f
internal const val EMPTY_HOME_V2_ROD_TIP_X = 335f
internal const val EMPTY_HOME_V2_ROD_TIP_Y = 1180f
internal const val EMPTY_HOME_V2_LINE_START_X = 335f
internal const val EMPTY_HOME_V2_LINE_START_Y = 1180f
internal const val EMPTY_HOME_V2_LINE_C1_X = 390f
internal const val EMPTY_HOME_V2_LINE_C1_Y = 1218f
internal const val EMPTY_HOME_V2_LINE_C2_X = 470f
internal const val EMPTY_HOME_V2_LINE_C2_Y = 1090f
internal const val EMPTY_HOME_V2_LINE_END_X = 560f
internal const val EMPTY_HOME_V2_LINE_END_Y = 1128f
internal const val EMPTY_HOME_V2_BOBBER_X = 548f
internal const val EMPTY_HOME_V2_BOBBER_Y = 1036f
internal const val EMPTY_HOME_V2_RIPPLE_X = 441f
internal const val EMPTY_HOME_V2_RIPPLE_Y = 1079f
internal const val EMPTY_HOME_V2_WATER_CONTACT_X = 560f
internal const val EMPTY_HOME_V2_WATER_CONTACT_Y = 1120f
internal const val EMPTY_HOME_V2_FISHING_PROTECTED_LEFT = 0f
internal const val EMPTY_HOME_V2_FISHING_PROTECTED_RIGHT = 660f

// Environment loops deliberately start on distinct phases. Their periods and
// amplitudes remain frozen; only page-entry synchronization is avoided.
internal const val CLOUD_PHASE_OFFSET_SECONDS = 17.6f
internal const val SUN_BEAM_PHASE_OFFSET_SECONDS = 2.08f
internal const val CAMERA_BREATH_PHASE_OFFSET_SECONDS = 1.71f
private val BOBBER_RIPPLE_BURST_STARTS_SECONDS = floatArrayOf(1.5f, 6.8f, 13.6f, 22.4f, 28.2f)
private const val BOBBER_EVENT_START_FLOAT_TOLERANCE_SECONDS = 0.0001f

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
    val centeredOffsetX = (containerWidthPx - REFERENCE_SCENE_WIDTH * scale) / 2f
    val protectedMinOffsetX = -EMPTY_HOME_V2_FISHING_PROTECTED_LEFT * scale
    val protectedMaxOffsetX = containerWidthPx - EMPTY_HOME_V2_FISHING_PROTECTED_RIGHT * scale
    val offsetX = if (protectedMaxOffsetX >= protectedMinOffsetX) {
        centeredOffsetX.coerceIn(protectedMinOffsetX, protectedMaxOffsetX)
    } else {
        centeredOffsetX.coerceAtLeast(protectedMinOffsetX)
    }
    return ReferenceSceneTransform(
        scale = scale,
        // Keep the measured rod-to-ripple area on screen. Any excess Cover crop
        // comes from the right-side decorative environment, never from the rod.
        offsetX = offsetX,
        offsetY = (containerHeightPx - REFERENCE_SCENE_HEIGHT * scale) / 2f,
    )
}

internal data class PeriodicBobberRippleBurst(
    val cycleIndex: Long,
    val burstIndex: Int,
    val elapsedSeconds: Float,
) {
    val eventId: Long get() = cycleIndex * 5L + burstIndex.toLong()
}

internal data class PeriodicBobberMotion(
    val eventId: Long?,
    val burstIndex: Int,
    val burstElapsedSeconds: Float,
    val dipOffsetPx: Float,
    val burstActive: Boolean,
)

internal fun calculateClickBobberDip(elapsedSeconds: Float): Float =
    if (elapsedSeconds.isFinite() && elapsedSeconds in 0f..0.7f) {
        5f * exp(-elapsedSeconds * 6f) * cos(elapsedSeconds * 24f)
    } else {
        0f
    }

/** ZIP-derived, deterministic Y-only nibble motion. Rotation stays disabled pending D01. */
internal fun calculatePeriodicBobberMotion(timeSeconds: Float): PeriodicBobberMotion {
    val burst = calculatePeriodicBobberRippleBurst(timeSeconds)
        ?: return PeriodicBobberMotion(
            eventId = null,
            burstIndex = -1,
            burstElapsedSeconds = 0f,
            dipOffsetPx = 0f,
            burstActive = false,
        )
    val elapsed = burst.elapsedSeconds
    val dip = if (elapsed < 1.4f) {
        4f * exp(-elapsed * 3.6f) * cos(elapsed * 15f)
    } else {
        0f
    }
    return PeriodicBobberMotion(
        eventId = burst.eventId,
        burstIndex = burst.burstIndex,
        burstElapsedSeconds = elapsed,
        dipOffsetPx = dip,
        burstActive = true,
    )
}

internal fun shouldEmitBobberNibbleHaptic(
    hasObservedMotionEvent: Boolean,
    previousMotionEventId: Long?,
    currentMotionEventId: Long?,
    lastHapticEventId: Long?,
    runtimeAssetsAvailable: Boolean,
    running: Boolean,
    reduceMotion: Boolean,
    currentMotionElapsedSeconds: Float,
): Boolean = hasObservedMotionEvent && currentMotionEventId != null &&
    currentMotionEventId != previousMotionEventId && currentMotionEventId != lastHapticEventId &&
    currentMotionElapsedSeconds in 0f..0.12f && runtimeAssetsAvailable && running && !reduceMotion

internal fun calculatePeriodicBobberRippleBurst(
    timeSeconds: Float,
): PeriodicBobberRippleBurst? {
    if (!timeSeconds.isFinite() || timeSeconds < 0f) return null
    val cycleDurationSeconds = 36f
    val burstDurationSeconds = 3.6f
    val cycleIndex = floor(timeSeconds / cycleDurationSeconds).toLong()
    val cycleTime = timeSeconds - cycleIndex * cycleDurationSeconds
    BOBBER_RIPPLE_BURST_STARTS_SECONDS.forEachIndexed { index, start ->
        val elapsed = cycleTime - start
        // sceneTimeSeconds is a Float; adding the 36s period can place an
        // exact nominal event time a few microseconds before its start value.
        // Snap that representation error to the event's zero-time boundary.
        if (
            elapsed >= -BOBBER_EVENT_START_FLOAT_TOLERANCE_SECONDS &&
            elapsed < burstDurationSeconds
        ) {
            return PeriodicBobberRippleBurst(
                cycleIndex = cycleIndex,
                burstIndex = index,
                elapsedSeconds = elapsed.coerceAtLeast(0f),
            )
        }
    }
    return null
}

internal fun emptyHomeMotionActive(
    running: Boolean,
    reduceMotion: Boolean,
): Boolean = running && !reduceMotion

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
        // The native mask's alpha is more restrained than the Canvas arc it
        // replaces. This calibrated value preserves the frozen cadence while
        // making one sweep legible in a normal 11–12 s runtime recording.
        alpha = 0.45f * sin(progress * PI.toFloat()).coerceAtLeast(0f),
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

