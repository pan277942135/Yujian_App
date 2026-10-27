package com.yujian.ai.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.util.concurrent.ConcurrentHashMap

/**
 * Test-only geometry capture for NH01. It records both the full transformed
 * bounds and the bounds after Compose clipping; it never participates in
 * measurement or changes the page's layout.
 */
internal object GeminiHomeGeometryProbe {
    data class Bounds(val unclippedWindow: Rect, val visibleWindow: Rect)

    @Volatile
    private var captureEnabled = false
    private val measured = ConcurrentHashMap<String, Bounds>()

    fun beginCapture() {
        measured.clear()
        captureEnabled = true
    }

    fun endCapture() {
        captureEnabled = false
    }

    fun bounds(tag: String): Bounds = requireNotNull(measured[tag]) {
        "NH01 native LayoutCoordinates were not captured for $tag"
    }

    internal fun record(tag: String, coordinates: LayoutCoordinates) {
        if (!captureEnabled || !coordinates.isAttached) return
        measured[tag] = Bounds(
            unclippedWindow = coordinates.boundsInWindow(clipBounds = false),
            visibleWindow = coordinates.boundsInWindow(clipBounds = true),
        )
    }
}

internal fun Modifier.captureGeminiHomeBounds(tag: String): Modifier =
    onGloballyPositioned { coordinates -> GeminiHomeGeometryProbe.record(tag, coordinates) }
