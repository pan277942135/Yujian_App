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
    val attemptId: String = "",
    val imageId: String = "",
    val photoContentVersion: Long = 0L,
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
    private var attemptId: String? = null
    private var imageId: String? = null
    private var photoSha256: String? = null
    private var photoContentVersion: Long = Long.MIN_VALUE
    private var geometryVersion: Long = Long.MIN_VALUE

    fun activate(frame: WaterLightFrame) {
        sessionId = frame.sessionId
        attemptId = frame.attemptId
        imageId = frame.imageId
        photoSha256 = frame.photoSha256
        photoContentVersion = frame.photoContentVersion
        geometryVersion = frame.geometryVersion
    }

    fun accepts(frame: WaterLightFrame): Boolean =
        sessionId == frame.sessionId && attemptId == frame.attemptId && imageId == frame.imageId &&
            photoSha256 == frame.photoSha256 && photoContentVersion == frame.photoContentVersion &&
            geometryVersion == frame.geometryVersion

    fun release() {
        sessionId = null
        attemptId = null
        imageId = null
        photoSha256 = null
        photoContentVersion = Long.MIN_VALUE
        geometryVersion = Long.MIN_VALUE
    }
}
