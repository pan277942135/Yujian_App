package com.yujian.ai.ui.home

/** Frozen 01–03 contract. Timings are independent of camera initialization. */
object CaptureVisualTokens {
    const val ActivationMs = 750L
    const val HandoffMs = 180
    const val IdleCycleMs = 3000
    const val IdleGlintMs = 600
    const val GuideWidthFraction = .90f
    const val GuideHeightToWidth = .80f
    const val ButtonDiameterDp = 94f
    const val CameraExpansionDp = 6f
    const val ActivationExpansionDp = 12f
    fun buttonDiameter(widthDp: Float) = (widthDp * .244f).coerceIn(88f, 96f)
    fun activationProgress(elapsedMs: Long) = (elapsedMs / ActivationMs.toFloat()).coerceIn(0f, 1f)
}

internal data class CaptureHomeActionY(val cameraTop: Float, val promptTop: Float)

/** Keep frozen center when it fits; otherwise compress only action-group whitespace. */
internal fun resolveCaptureHomeActionY(
    referenceCenter: Float, diameter: Float, albumTop: Float,
    referencePromptTop: Float, promptHeight: Float,
): CaptureHomeActionY {
    val cameraTop = minOf(referenceCenter - diameter / 2f, albumTop - 8f - diameter)
    return CaptureHomeActionY(cameraTop, minOf(referencePromptTop, cameraTop - 8f - promptHeight))
}

/** Align icon centers, accounting for the caption below each side icon. */
internal fun captureSideActionBottomOffset(shutterDiameter: Float, groupSize: Float, captionHeight: Float): Float =
    (shutterDiameter - groupSize - captionHeight) / 2f

/** 16dp below hint + measured hint +16dp above hint, minus pill's4dp optical inset. */
internal fun captureZoomEntryBottomOffset(hintHeight: Float): Float = hintHeight + 28f
