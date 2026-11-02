package com.yujian.ai.ui.waterlight.core


/** Generic WaterLight lifecycle; recognition business phases are mapped at the screen adapter. */
enum class WaterLightPhase { IDLE, PHOTO_ENTRY, PROCESSING, HANDOFF, RESIDUE, SETTLED }

/** A snapshot of the existing recognition scene clock. Renderers never advance it. */
data class WaterLightFrame(
    val sessionId: String,
    val photoSha256: String?,
    val elapsedMs: Long,
    val opticalReferenceElapsedMs: Long,
    val phase: WaterLightPhase,
    val completion: Float,
    val reducedMotion: Boolean,
    val geometryVersion: Long,
    val attemptId: String = "",
    val imageId: String = "",
    val photoContentVersion: Long = 0L,
    val playbackMode: ScenePlaybackMode = ScenePlaybackMode.AUTO,
    val playbackPaused: Boolean = false,
    /** Non-null only for production event-driven stages; diagnostics keep their preview projection. */
    val sceneStageOverride: RecognitionSceneStage? = null,
    val sceneStageElapsedMsOverride: Long? = null,
    val sceneStageProgressOverride: Float? = null,
) {
    init {
        require(elapsedMs >= 0 && opticalReferenceElapsedMs >= 0)
        require(completion in 0f..1f)
        require(sceneStageElapsedMsOverride == null || sceneStageElapsedMsOverride >= 0L)
        require(sceneStageProgressOverride == null || sceneStageProgressOverride in 0f..1f)
    }

    val timeSeconds: Float get() = if (reducedMotion) 0f else elapsedMs / 1000f
    /** All visual hosts derive from one frame; production stage identity is supplied by the lifecycle controller. */
    val sceneFrame: RecognitionSceneFrame get() {
        val identity = RecognitionSceneIdentity(
            sessionId = sessionId,
            attemptId = attemptId,
            imageId = imageId,
            photoSha256 = photoSha256,
            photoContentVersion = photoContentVersion,
        )
        val stageOverride = sceneStageOverride
        return if (stageOverride != null) RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = elapsedMs,
            opticalReferenceElapsedMs = opticalReferenceElapsedMs,
            geometryVersion = geometryVersion,
            stage = stageOverride,
            stageElapsedMs = sceneStageElapsedMsOverride ?: 0L,
            stageProgress = sceneStageProgressOverride ?: 0f,
            playbackMode = playbackMode,
            paused = playbackPaused,
            reducedMotion = reducedMotion,
        ) else RecognitionSceneCoordinator.frameAt(
            identity = identity,
            sceneElapsedMs = elapsedMs,
            opticalReferenceElapsedMs = opticalReferenceElapsedMs,
            geometryVersion = geometryVersion,
            playbackMode = playbackMode,
            paused = playbackPaused,
            reducedMotion = reducedMotion,
        )
    }
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
