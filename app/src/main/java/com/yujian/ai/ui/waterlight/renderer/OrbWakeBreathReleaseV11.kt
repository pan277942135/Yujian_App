package com.yujian.ai.ui.waterlight.renderer

import kotlin.math.PI
import kotlin.math.sin

/** Pure projections from the two time values already carried by the shared Recognition frame. */
internal object OrbWakeBreathReleaseV11 {
    const val WAKE_DURATION_MS = 350L
    const val FILAMENT_TRANSITION_MS = 450L
    const val RELEASE_START_MS = 350L
    const val RELEASE_RETURN_MS = 690L
    const val RELEASE_END_MS = 950L
    const val RELEASE_DIRECTION_RAMP_MS = 260L
    const val R06_ALPHA_GAIN = 1.28f
    const val R06_ALPHA_CAP = .82f
    const val R06_MOVING_HIGHLIGHT_GAIN = 1.15f
    const val R06_RELEASE_HEAD_BRIGHTNESS_GAIN = 1.18f
    const val R06_MAX_EXTENSION_DP = 14f

    data class Wake(val optics: Float, val waterCore: Float, val grassLight: Float)
    enum class LocalRelease { AMBIENT, OUTWARD, RETURN }

    /** Smoothstep cubic-bezier equivalent with zero endpoint velocity. */
    fun smoothstep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Frozen ease-out cubic-bezier(0.16, 1.0, 0.30, 1.0), solved for x. */
    fun wakeEase(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        var lo = 0f
        var hi = 1f
        repeat(18) {
            val t = (lo + hi) * .5f
            val bx = cubic(t, .16f, .30f)
            if (bx < x) lo = t else hi = t
        }
        val t = (lo + hi) * .5f
        return cubic(t, 1f, 1f)
    }

    private fun cubic(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
    }

    /** Stage envelopes use opticalReferenceElapsedMs; all returned layers remain clock-free. */
    fun wake(opticalReferenceElapsedMs: Long): Wake {
        val t = opticalReferenceElapsedMs.coerceAtLeast(0L)
        val optics = if (t < 80L) 0f else wakeEase((t - 80L).toFloat() / (WAKE_DURATION_MS - 80L))
        val water = if (t < 220L) 0f else wakeEase((t - 220L).toFloat() / (WAKE_DURATION_MS - 220L))
        val grass = if (t < 220L) 0f else wakeEase((t - 220L).toFloat() / (WAKE_DURATION_MS - 220L))
        return Wake(optics, water, grass)
    }

    fun s2Progress(opticalReferenceElapsedMs: Long): Float =
        smoothstep(opticalReferenceElapsedMs.coerceAtLeast(0L).toFloat() / FILAMENT_TRANSITION_MS)

    /** A single sine phase from sceneElapsedMs; state changes alter amplitude/period without resetting time. */
    fun breathScale(sceneElapsedMs: Long, opticalReferenceElapsedMs: Long, reducedMotion: Boolean): Float {
        if (reducedMotion) return 1f
        val p = s2Progress(opticalReferenceElapsedMs)
        val amplitude = .008f + (.018f - .008f) * p
        val periodMs = 6000f + (4800f - 6000f) * p
        val phase = 2f * PI.toFloat() * sceneElapsedMs.coerceAtLeast(0L) / periodMs
        return 1f + amplitude * sin(phase)
    }

    fun localRelease(opticalReferenceElapsedMs: Long): LocalRelease = when (opticalReferenceElapsedMs) {
        in RELEASE_START_MS until RELEASE_RETURN_MS -> LocalRelease.OUTWARD
        in RELEASE_RETURN_MS until RELEASE_END_MS -> LocalRelease.RETURN
        else -> LocalRelease.AMBIENT
    }

    /** Zero at both ends; the return shoulder is a 260 ms C1 envelope. */
    fun releaseOffset(opticalReferenceElapsedMs: Long, peakArcFraction: Float): Float {
        val t = opticalReferenceElapsedMs
        return when {
            t < RELEASE_START_MS || t >= RELEASE_END_MS -> 0f
            t < RELEASE_RETURN_MS -> peakArcFraction * smoothstep(
                (t - RELEASE_START_MS).toFloat() / (RELEASE_RETURN_MS - RELEASE_START_MS),
            )
            else -> peakArcFraction * (1f - smoothstep(
                (t - RELEASE_RETURN_MS).toFloat() / RELEASE_DIRECTION_RAMP_MS,
            ))
        }
    }

    fun returnDirectionProgress(opticalReferenceElapsedMs: Long): Float =
        smoothstep((opticalReferenceElapsedMs - RELEASE_RETURN_MS).toFloat() / RELEASE_DIRECTION_RAMP_MS)
}
