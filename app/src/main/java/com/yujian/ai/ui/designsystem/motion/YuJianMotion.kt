package com.yujian.ai.ui.designsystem.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Shared runtime motion values from motion_tokens.json.
 *
 * This foundation intentionally provides primitives for InfiniteTransition,
 * Animatable, and AnimatedVisibility. Page-wide entrance motion is not used in
 * V1; capture, glass interaction, press feedback, and the one-time Fish Guide
 * discover hint consume these values.
 */
object YuJianMotion {
    const val CaptureRimSweepFirstDelayMillis = 3_000
    const val CaptureRimSweepDurationMillis = 1_400
    const val CaptureRimSweepRepeatIntervalMillis = 9_000
    const val CaptureBreathingDurationMillis = 5_000
    const val CaptureBreathingMaxScale = 1.015f
    const val PressFeedbackDurationMillis = 140
    const val GlassInteractionDurationMillis = 180
    const val CarouselDiscoverHintDelayMillis = 600
    const val CarouselDiscoverHintOffsetDp = 14f
    const val CarouselDiscoverHintOutDurationMillis = 180
    const val CarouselDiscoverHintReturnDurationMillis = 260

    val calmEasing = FastOutSlowInEasing

    fun captureRimSweepSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = keyframes {
            durationMillis = CaptureRimSweepRepeatIntervalMillis
            0f at 0
            0f at CaptureRimSweepFirstDelayMillis
            1f at CaptureRimSweepFirstDelayMillis + CaptureRimSweepDurationMillis
            1f at CaptureRimSweepRepeatIntervalMillis
        },
    )

    fun captureBreathingSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = keyframes {
            durationMillis = CaptureBreathingDurationMillis
            1f at 0
            1.015f at CaptureBreathingDurationMillis / 2
            1f at CaptureBreathingDurationMillis
        },
    )

    fun pressFeedbackSpec() = tween<Float>(
        durationMillis = PressFeedbackDurationMillis,
        easing = calmEasing,
    )

    fun glassInteractionSpec() = tween<Float>(
        durationMillis = GlassInteractionDurationMillis,
        easing = calmEasing,
    )
}

@Composable
fun rememberYuJianInfiniteTransition(label: String = "YuJianMotion"): InfiniteTransition =
    rememberInfiniteTransition(label = label)

/** Tracks the system animator scale so shared spatial feedback honors reduced motion. */
@Composable
fun rememberYuJianReduceMotion(): Boolean {
    val appContext = LocalContext.current.applicationContext
    val state = remember(appContext) { animatorScaleState(appContext) }
    return state.value
}

private val animatorScaleStates = WeakHashMap<android.content.Context, MutableState<Boolean>>()

private fun animatorScaleState(context: android.content.Context): MutableState<Boolean> =
    synchronized(animatorScaleStates) {
        animatorScaleStates.getOrPut(context) {
            mutableStateOf(readAnimatorScaleDisabled(context)).also { state ->
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        state.value = readAnimatorScaleDisabled(context)
                    }
                }
                context.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false,
                    observer,
                )
            }
        }
    }

private fun readAnimatorScaleDisabled(context: android.content.Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

suspend fun Animatable<Float, AnimationVector1D>.animateYuJianPress(target: Float) {
    animateTo(targetValue = target, animationSpec = YuJianMotion.pressFeedbackSpec())
}

@Composable
fun YuJianAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition = fadeIn(animationSpec = YuJianMotion.glassInteractionSpec()),
    exit: ExitTransition = fadeOut(animationSpec = YuJianMotion.glassInteractionSpec()),
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, content = { content() })
}
