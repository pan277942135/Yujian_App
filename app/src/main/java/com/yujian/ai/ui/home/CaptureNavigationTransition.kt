package com.yujian.ai.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import android.os.SystemClock
import android.util.Log
import java.util.UUID

/** One-shot handoff of the actual home capture center to the camera route. */
object CaptureNavigationTransition {
    var homeCenter = Offset.Unspecified
    var homeDiameterDp = CaptureVisualTokens.ButtonDiameterDp
    var sessionId: String = ""
        private set
    val active = mutableStateOf(false)
    var startedAtMs: Long = 0L
        private set
    fun begin() {
        if (active.value) return
        sessionId = UUID.randomUUID().toString()
        startedAtMs = SystemClock.uptimeMillis()
        active.value = homeCenter.x.isFinite() && homeCenter.y.isFinite()
        log("HOME_ACTIVATING")
    }
    fun elapsedMs(): Long = (SystemClock.uptimeMillis() - startedAtMs).coerceAtLeast(0L)
    fun log(state: String, fields: String = "") {
        Log.d("CaptureEntry", "capture_session_id=$sessionId page_state=$state elapsed_ms=${elapsedMs()} $fields")
    }
    fun finish() {
        if (active.value) log("CAMERA_ENTERED")
        active.value = false
    }
}
