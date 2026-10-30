package com.yujian.ai.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import android.os.SystemClock

/** One-shot handoff of the actual home capture center to the camera route. */
object CaptureNavigationTransition {
    var homeCenter = Offset.Unspecified
    val active = mutableStateOf(false)
    var startedAtMs: Long = 0L
        private set
    fun begin() {
        startedAtMs = SystemClock.uptimeMillis()
        active.value = homeCenter.x.isFinite() && homeCenter.y.isFinite()
    }
    fun elapsedMs(): Long = (SystemClock.uptimeMillis() - startedAtMs).coerceAtLeast(0L)
    fun finish() { active.value = false }
}
