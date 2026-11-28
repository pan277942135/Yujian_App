package com.yujian.ai.ui.home

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import java.util.UUID

/** Shared, monotonic Home → Camera hero handoff clock. */
object CaptureNavigationTransition {
    const val HeroExpandMs = 300L
    const val HeroShrinkMs = 280L
    const val HeroTotalTransitionMs = 580L
    const val HeroPeakScale = 1.22f

    var homeCenter: Offset = Offset.Unspecified
    var homeDiameterDp: Float = CaptureVisualTokens.ButtonDiameterDp
    var sessionId: String = ""
        private set
    val active = mutableStateOf(false)
    val entering = mutableStateOf(false)
    val cameraCenter = mutableStateOf(Offset.Unspecified)
    val cameraDiameterDp = mutableStateOf(CaptureVisualTokens.ButtonDiameterDp)
    var startedAtMs: Long = 0L
        private set

    internal fun currentUptimeMillis(): Long = SystemClock.uptimeMillis()

    /** Monotonic scene time shared by the shutter and its optical layers. */
    fun continuousSceneTimeMs(): Long = currentUptimeMillis()

    fun begin() {
        if (active.value) return
        entering.value = false
        cameraCenter.value = Offset.Unspecified
        sessionId = UUID.randomUUID().toString()
        startedAtMs = currentUptimeMillis()
        active.value = homeCenter.x.isFinite() && homeCenter.y.isFinite()
        log("HOME_ACTIVATING")
    }

    fun elapsedMs(): Long = (currentUptimeMillis() - startedAtMs).coerceAtLeast(0L)

    /**
     * 0–300ms: EaseInQuad 1.00x → 1.22x.
     * 300–580ms: EaseOutQuad 1.22x → 1.00x.
     * After 580ms the normal idle breathing owns the scale again.
     */
    fun transitionScale(elapsed: Long = elapsedMs()): Float {
        val peakDelta = HeroPeakScale - 1f
        return when {
            elapsed <= 0L -> 1f
            elapsed <= HeroExpandMs -> {
                val p = (elapsed / HeroExpandMs.toFloat()).coerceIn(0f, 1f)
                1f + peakDelta * p * p
            }
            elapsed <= HeroTotalTransitionMs -> {
                val p = ((elapsed - HeroExpandMs) / HeroShrinkMs.toFloat()).coerceIn(0f, 1f)
                val easeOut = 1f - (1f - p) * (1f - p)
                HeroPeakScale - peakDelta * easeOut
            }
            else -> 1f
        }
    }

    fun log(state: String, fields: String = "") {
        Log.d(
            "CaptureEntry",
            "capture_session_id=" + sessionId + " page_state=" + state +
                " elapsed_ms=" + elapsedMs() + " " + fields,
        )
    }

    fun finish() {
        if (active.value) log("CAMERA_ENTERED")
        active.value = false
        entering.value = false
    }

    fun resetAnchor() {
        homeCenter = Offset.Unspecified
        homeDiameterDp = CaptureVisualTokens.ButtonDiameterDp
    }
}
