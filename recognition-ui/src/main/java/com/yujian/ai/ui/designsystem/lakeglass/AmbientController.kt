package com.yujian.ai.ui.designsystem.lakeglass

/** Independent, monotonic ambient timeline; it never reads recognition or WaterLight clocks. */
internal class AmbientController {
    private var originNanos: Long? = null
    private var pausedAtNanos: Long? = null
    private var pausedAccumulatedNanos = 0L

    fun elapsedSeconds(frameTimeNanos: Long, motionEnabled: Boolean): Float {
        if (!motionEnabled) return 0f
        val origin = originNanos ?: frameTimeNanos.also { originNanos = it }
        val effectiveNow = pausedAtNanos ?: frameTimeNanos
        return ((effectiveNow - origin - pausedAccumulatedNanos).coerceAtLeast(0L) / 1_000_000_000f)
    }

    fun pause(frameTimeNanos: Long) {
        if (pausedAtNanos == null) pausedAtNanos = frameTimeNanos
    }

    fun resume(frameTimeNanos: Long) {
        val pausedAt = pausedAtNanos ?: return
        pausedAccumulatedNanos += (frameTimeNanos - pausedAt).coerceAtLeast(0L)
        pausedAtNanos = null
    }
}
