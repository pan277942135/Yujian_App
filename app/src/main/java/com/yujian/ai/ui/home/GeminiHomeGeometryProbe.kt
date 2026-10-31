package com.yujian.ai.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import java.util.concurrent.ConcurrentHashMap

/**
 * Test-only geometry capture for NH01. It records both the full transformed
 * bounds and the bounds after Compose clipping; it never participates in
 * measurement or changes the page's layout.
 */
internal object GeminiHomeGeometryProbe {
    data class Bounds(
        val unclippedRoot: Rect,
        val visibleRoot: Rect,
        val unclippedWindow: Rect,
        val visibleWindow: Rect,
        val rootWindowOrigin: Offset,
    )

    data class PagerState(
        val currentPage: Int,
        val targetPage: Int,
        val settledPage: Int,
        val isScrollInProgress: Boolean,
        val offsetFraction: Float,
        val currentId: String?,
        val leftId: String?,
        val rightId: String?,
        val pageCount: Int,
    )

    data class ScrollState(val offsetPx: Int, val maximumPx: Int)

    @Volatile
    private var captureEnabled = false
    private val measured = ConcurrentHashMap<String, Bounds>()
    @Volatile
    private var pagerState: PagerState? = null
    @Volatile
    private var scrollState: ScrollState? = null

    fun beginCapture() {
        measured.clear()
        pagerState = null
        scrollState = null
        captureEnabled = true
    }

    fun endCapture() {
        captureEnabled = false
    }

    fun bounds(tag: String): Bounds = requireNotNull(measured[tag]) {
        "NH01 native LayoutCoordinates were not captured for $tag"
    }

    fun pagerState(): PagerState? = pagerState

    fun scrollState(): ScrollState? = scrollState

    internal fun recordPagerState(state: PagerState) {
        if (captureEnabled) pagerState = state
    }

    internal fun recordScrollState(state: ScrollState) {
        if (captureEnabled) scrollState = state
    }

    internal fun record(tag: String, coordinates: LayoutCoordinates) {
        if (!captureEnabled || !coordinates.isAttached) return
        val root = coordinates.findRootCoordinates()
        val rootWindowOrigin = root.positionInWindow()
        val unclippedRoot = root.localBoundingBoxOf(coordinates, clipBounds = false)
        val visibleRoot = root.localBoundingBoxOf(coordinates, clipBounds = true)
        measured[tag] = Bounds(
            // localBoundingBoxOf has supported clipBounds since Compose UI 1.0.
            // The project's pinned Compose version does not expose the newer
            // boundsInWindow(clipBounds=...) overload. Resolve in root space,
            // then translate by the measured ComposeView origin in the window.
            unclippedRoot = unclippedRoot,
            visibleRoot = visibleRoot,
            unclippedWindow = unclippedRoot.translate(rootWindowOrigin),
            visibleWindow = visibleRoot.translate(rootWindowOrigin),
            rootWindowOrigin = rootWindowOrigin,
        )
    }
}

private fun Rect.translate(offset: Offset): Rect =
    Rect(left + offset.x, top + offset.y, right + offset.x, bottom + offset.y)

internal fun Modifier.captureGeminiHomeBounds(tag: String): Modifier =
    onGloballyPositioned { coordinates -> GeminiHomeGeometryProbe.record(tag, coordinates) }
