package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ai.RecognitionPhase

/** A snapshot of the existing recognition scene clock. Renderers never advance it. */
data class WaterLightFrame(
    val sessionId: String,
    val photoSha256: String?,
    val elapsedMs: Long,
    val opticalReferenceElapsedMs: Long,
    val phase: RecognitionPhase,
    val completion: Float,
    val reducedMotion: Boolean,
    val geometryVersion: Long,
) {
    init {
        require(elapsedMs >= 0 && opticalReferenceElapsedMs >= 0)
        require(completion in 0f..1f)
    }

    val timeSeconds: Float get() = if (reducedMotion) 0f else elapsedMs / 1000f
}

/** One owner per scene; old session frames and stale geometry are never rendered. */
class UnifiedWaterLightEngine {
    private var sessionId: String? = null
    private var geometryVersion: Long = Long.MIN_VALUE

    fun activate(sessionId: String, geometryVersion: Long) {
        this.sessionId = sessionId
        this.geometryVersion = geometryVersion
    }

    fun accepts(frame: WaterLightFrame): Boolean =
        sessionId == frame.sessionId && geometryVersion == frame.geometryVersion

    fun release() {
        sessionId = null
        geometryVersion = Long.MIN_VALUE
    }
}
