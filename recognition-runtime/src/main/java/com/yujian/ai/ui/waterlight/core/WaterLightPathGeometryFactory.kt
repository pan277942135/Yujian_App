package com.yujian.ai.ui.waterlight.core

import kotlin.math.hypot

/** Deterministic curved path fitted to two measured runtime anchors. */
object WaterLightPathGeometryFactory {
    fun between(
        start: WaterLightPathPoint,
        end: WaterLightPathPoint,
        seed: Int,
        bendFraction: Float = .12f,
    ): WaterLightPathGeometry? {
        if (!start.isValid() || !end.isValid() || !bendFraction.isFinite() || bendFraction < 0f) return null
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        if (!length.isFinite() || length < 1f) return null
        val bend = (length * bendFraction.coerceAtMost(.25f)).coerceAtMost(52f)
        val sign = if ((seed and 1) == 0) 1f else -1f
        val normalX = -dy / length * bend * sign
        val normalY = dx / length * bend * sign
        return WaterLightPathGeometry(
            start = start,
            control1 = WaterLightPathPoint(start.x + dx * .28f + normalX, start.y + dy * .28f + normalY),
            control2 = WaterLightPathPoint(start.x + dx * .72f + normalX, start.y + dy * .72f + normalY),
            end = end,
        )
    }
}
