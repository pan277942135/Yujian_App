package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ui.identify.RecognitionImageTransform

/** A geometry fingerprint; content generationId belongs to photo identity instead. */
object WaterLightGeometryVersion {
    fun of(transform: RecognitionImageTransform): Long {
        val rect = transform.photoContentRect
        val values = floatArrayOf(transform.viewportWidthPx, transform.viewportHeightPx,
            transform.scale, transform.translationX, transform.translationY,
            transform.drawnWidth, transform.drawnHeight,
            rect.left, rect.top, rect.right, rect.bottom)
        var hash = 1125899906842597L
        for (value in values) hash = 31L * hash + value.toRawBits()
        hash = 31L * hash + transform.sourceWidthPx
        hash = 31L * hash + transform.sourceHeightPx
        return hash
    }

    fun withHostBounds(base: Long, left: Float, top: Float, right: Float, bottom: Float): Long =
        listOf(left, top, right, bottom).fold(base) { hash, value -> 31L * hash + value.toRawBits() }
}
