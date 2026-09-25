package com.yujian.ai.ui.designsystem.motion

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

/**
 * Shared runtime motion values from motion_tokens.json.
 *
 * This foundation intentionally provides primitives for InfiniteTransition,
 * Animatable, and AnimatedVisibility. Page-wide entrance motion is not used in
 * V1; only capture, glass interaction, and press feedback consume it.
 */
object YuJianMotion {
    const val CaptureRimSweepFirstDelayMillis = 3_000
    const val CaptureRimSweepDurationMillis = 1_400
    const val CaptureRimSweepRepeatIntervalMillis = 9_000
    const val CaptureBreathingDurationMillis = 5_000
    const val PressFeedbackDurationMillis = 140
    const val GlassInteractionDurationMillis = 180

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
