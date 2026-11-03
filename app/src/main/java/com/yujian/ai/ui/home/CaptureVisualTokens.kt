package com.yujian.ai.ui.home

/** Frozen Home / Camera shutter geometry and optical timing contract. */
object CaptureVisualTokens {
    const val ActivationMs = 750L
    const val HandoffMs = 580
    const val IdleCycleMs = 10_500
    const val IdleBreathCycleMs = 5_000
    const val IdleBreathMaxScale = 1.015f
    const val IdleGlintMs = 2_500
    const val ClickRippleDurationMs = 600L
    const val ClickRippleSpreadDp = 36f
    const val GuideWidthFraction = .90f
    const val GuideHeightToWidth = .80f
    const val ButtonDiameterDp = 94f
    const val CameraExpansionDp = 6f
    const val ActivationExpansionDp = 12f

    fun buttonDiameter(widthDp: Float) = (widthDp * .244f).coerceIn(88f, 96f)
    fun activationProgress(elapsedMs: Long) =
        (elapsedMs / ActivationMs.toFloat()).coerceIn(0f, 1f)
    fun clickRippleProgress(elapsedMs: Long) =
        (elapsedMs / ClickRippleDurationMs.toFloat()).coerceIn(0f, 1f)
}

/** One eased 360° sweep in 2.5s, then an 8s rest at the 1:30 light-entry point. */
internal fun captureIdleFlareAngleDegrees(elapsedMs: Long): Float {
    val cycleMs = CaptureVisualTokens.IdleCycleMs.toLong()
    val cycle = ((elapsedMs % cycleMs) + cycleMs) % cycleMs
    if (cycle >= CaptureVisualTokens.IdleGlintMs) return -45f
    val linear = cycle / CaptureVisualTokens.IdleGlintMs.toFloat()
    val eased = 0.5f - 0.5f * kotlin.math.cos(linear * kotlin.math.PI.toFloat())
    return -45f + eased * 360f
}

internal fun captureIdleBreathScale(progress: Float): Float {
    if (!progress.isFinite()) return 1f
    val phase = progress.coerceIn(0f, 1f)
    return (1f + (CaptureVisualTokens.IdleBreathMaxScale - 1f) *
        (0.5f - 0.5f * kotlin.math.cos(phase * 2f * kotlin.math.PI.toFloat())))
        .coerceIn(1f, CaptureVisualTokens.IdleBreathMaxScale)
}

internal data class CaptureHomeActionY(val cameraTop: Float, val promptTop: Float)

internal fun resolveCaptureHomeActionY(
    referenceCenter: Float, diameter: Float, albumTop: Float,
    referencePromptTop: Float, promptHeight: Float,
): CaptureHomeActionY {
    val cameraTop = minOf(referenceCenter - diameter / 2f, albumTop - 8f - diameter)
    return CaptureHomeActionY(cameraTop, minOf(referencePromptTop, cameraTop - 8f - promptHeight))
}

internal fun captureSideActionBottomOffset(shutterDiameter: Float, groupSize: Float, captionHeight: Float): Float =
    (shutterDiameter - groupSize - captionHeight) / 2f

internal fun captureZoomEntryBottomOffset(hintHeight: Float): Float = hintHeight + 28f
