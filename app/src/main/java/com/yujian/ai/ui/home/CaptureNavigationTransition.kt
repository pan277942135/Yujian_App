package com.yujian.ai.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset

/** One-shot handoff of the actual home capture center to the camera route. */
object CaptureNavigationTransition {
    var homeCenter = Offset.Unspecified
    val active = mutableStateOf(false)
    fun begin() { active.value = homeCenter.isSpecified }
    fun finish() { active.value = false }
}
