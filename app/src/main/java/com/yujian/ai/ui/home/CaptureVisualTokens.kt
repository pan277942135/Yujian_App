package com.yujian.ai.ui.home

/** Frozen 01–03 contract. Timings are independent of camera initialization. */
object CaptureVisualTokens {
    const val ActivationMs = 750L
    const val HandoffMs = 180
    const val IdleCycleMs = 3000
    const val IdleGlintMs = 600
    const val ButtonDiameterDp = 94f
    const val CameraExpansionDp = 6f
    const val ActivationExpansionDp = 12f
    fun buttonDiameter(widthDp: Float) = (widthDp * .244f).coerceIn(88f, 96f)
    fun activationProgress(elapsedMs: Long) = (elapsedMs / ActivationMs.toFloat()).coerceIn(0f, 1f)
}
