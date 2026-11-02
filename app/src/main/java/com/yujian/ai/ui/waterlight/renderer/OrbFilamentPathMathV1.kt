package com.yujian.ai.ui.waterlight.renderer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure deterministic geometry and motion math shared by the renderer and profile tests. */
internal object OrbFilamentPathMathV1 {
    const val ARC_SAMPLES = 128
    data class ArcLengthLut(val x: FloatArray, val y: FloatArray, val cumulativeD: FloatArray)
    data class Position(val x: Float, val y: Float)
    data class Movement(val u: Float, val unclampedU: Float, val direction: Float)

    fun newLut() = ArcLengthLut(FloatArray(ARC_SAMPLES), FloatArray(ARC_SAMPLES), FloatArray(ARC_SAMPLES))

    /** Samples two official cubics into 128 points and records cumulative normalized arc length. */
    fun fillLut(spec: OrbFilamentSpecV1, elapsedMs: Long, lut: ArcLengthLut) {
        require(lut.x.size == ARC_SAMPLES && lut.y.size == ARC_SAMPLES && lut.cumulativeD.size == ARC_SAMPLES)
        val tSeconds = elapsedMs.toDouble() / spec.deformationPeriodMs
        for (index in 0 until ARC_SAMPLES) {
            val progress = index.toFloat() / (ARC_SAMPLES - 1)
            val compositeT = progress * spec.segments.size
            val segmentIndex = min(spec.segments.lastIndex, compositeT.toInt())
            val localT = if (progress >= 1f) 1f else compositeT - segmentIndex
            val segment = spec.segments[segmentIndex]
            val startX = if (segmentIndex == 0) spec.start.x else spec.segments[segmentIndex - 1].end.x
            val startY = if (segmentIndex == 0) spec.start.y else spec.segments[segmentIndex - 1].end.y
            val endX = segment.end.x
            val endY = segment.end.y
            val dx = endX - startX
            val dy = endY - startY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
            val nx = -dy / length
            val ny = dx / length
            val phase = (2.0 * PI * (tSeconds + spec.phase + segmentIndex * .37)).toFloat()
            val shift1 = sin(phase) * spec.deformationAmplitudeD
            val shift2 = sin(phase + 1.13f) * spec.deformationAmplitudeD
            val c1x = segment.c1.x + nx * shift1
            val c1y = segment.c1.y + ny * shift1
            val c2x = segment.c2.x + nx * shift2
            val c2y = segment.c2.y + ny * shift2
            val inv = 1f - localT
            val t2 = localT * localT
            val inv2 = inv * inv
            var px = inv2 * inv * startX + 3f * inv2 * localT * c1x + 3f * inv * t2 * c2x + t2 * localT * endX
            var py = inv2 * inv * startY + 3f * inv2 * localT * c1y + 3f * inv * t2 * c2y + t2 * localT * endY
            val wave = if (index == 0 || index == ARC_SAMPLES - 1) 0f else sin(
                (2.0 * PI * (tSeconds + spec.phase + progress * 1.7)).toFloat(),
            ) * sin(PI.toFloat() * progress) * spec.normalAmplitudeD
            px += nx * wave
            py += ny * wave
            lut.x[index] = px
            lut.y[index] = py
            if (index > 0) {
                val ddx = px - lut.x[index - 1]
                val ddy = py - lut.y[index - 1]
                lut.cumulativeD[index] = lut.cumulativeD[index - 1] + sqrt(ddx * ddx + ddy * ddy)
            } else lut.cumulativeD[index] = 0f
        }
    }

    /** Inverts the cumulative arc-length table; `u` is distance fraction, never Bezier t. */
    fun sampleAt(lut: ArcLengthLut, u: Float, out: FloatArray, offset: Int) {
        val target = lut.cumulativeD.last() * u.coerceIn(0f, 1f)
        var low = 0
        var high = lut.cumulativeD.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (lut.cumulativeD[mid] < target) low = mid else high = mid
        }
        val span = lut.cumulativeD[high] - lut.cumulativeD[low]
        val fraction = if (span <= .000001f) 0f else (target - lut.cumulativeD[low]) / span
        out[offset] = lut.x[low] + (lut.x[high] - lut.x[low]) * fraction
        out[offset + 1] = lut.y[low] + (lut.y[high] - lut.y[low]) * fraction
    }

    fun mapSpeedDistance(progress: Float): Float {
        val keys = OrbRuntimeProfileV1.motionKeys
        val p = progress.coerceIn(0f, 1f)
        val index = (0 until keys.lastIndex).firstOrNull { p <= keys[it + 1].progress } ?: keys.lastIndex - 1
        val from = keys[index]
        val to = keys[index + 1]
        val linear = ((p - from.progress) / (to.progress - from.progress)).coerceIn(0f, 1f)
        val eased = linear * linear * (3f - 2f * linear)
        return from.distance + (to.distance - from.distance) * eased
    }

    fun profileAt(profile: List<OrbCurvePointV1>, u: Float): Float {
        if (profile.isEmpty()) return 1f
        if (u <= profile.first().u) return profile.first().value
        if (u >= profile.last().u) return profile.last().value
        val index = (0 until profile.lastIndex).firstOrNull { u <= profile[it + 1].u } ?: profile.lastIndex - 1
        val a = profile[index]
        val b = profile[index + 1]
        val amount = (u - a.u) / (b.u - a.u)
        return a.value + (b.value - a.value) * amount
    }

    fun movement(
        spec: OrbFilamentSpecV1,
        opticalMs: Long,
        reduced: Boolean,
        event: OrbRuntimeProfileV1.MotionEvent,
        eventStartMs: Long,
    ): Movement {
        fun regular(atMs: Long): Pair<Float, Float> {
            val phase = if (reduced) .37f else (spec.phase + atMs.toDouble() / spec.periodMs).let { it - floor(it) }.toFloat()
            val rawDistance = .5f - .5f * cos((2.0 * PI * phase).toFloat())
            val distance = mapSpeedDistance(rawDistance)
            val direction = if (sin((2.0 * PI * phase).toFloat()) >= 0f) 1f else -1f
            return distance to direction
        }
        if (event == OrbRuntimeProfileV1.MotionEvent.NONE || reduced || opticalMs < eventStartMs) {
            val (u, direction) = regular(opticalMs)
            return Movement(u.coerceIn(0f, 1f), u, direction)
        }
        val (startU, startDirection) = regular(eventStartMs)
        val targetDirection = if (event == OrbRuntimeProfileV1.MotionEvent.RELEASE) 1f else -1f
        val elapsed = (opticalMs - eventStartMs).toFloat().coerceAtLeast(0f)
        val rampDuration = OrbRuntimeProfileV1.R06_EVENT_SWITCH_MS.toFloat()
        val rampMs = min(elapsed, rampDuration)
        val s = rampMs / rampDuration
        val integratedRampDirectionMs = startDirection * rampMs + (targetDirection - startDirection) * rampDuration * (s * s * s - .5f * s * s * s * s)
        val integratedMs = if (elapsed <= rampDuration) integratedRampDirectionMs else {
            integratedRampDirectionMs + (elapsed - rampDuration) * targetDirection
        }
        val unclamped = startU + (PI.toFloat() / spec.periodMs) * integratedMs
        return Movement(unclamped.coerceIn(0f, 1f), unclamped, targetDirection)
    }

    fun smoothTransitionAlpha(from: Float, to: Float, elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return to.coerceIn(0f, 1f)
        val p = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val smooth = p * p * (3f - 2f * p)
        return (from + (to - from) * smooth).coerceIn(0f, 1f)
    }
}
