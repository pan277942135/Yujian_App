package com.yujian.ai.ui.waterlight.core

/** The optical timeline is sampled from Recognition's existing monotonic clock. */
enum class RecognitionSceneStage {
    PHOTO_STABLE,
    ORB_WAKE,
    ORB_RELEASE,
    PERIMETER_ARRIVAL,
    PATH_TO_FISH,
    CONTOUR_A,
    CONTOUR_B,
    CONTOUR_C,
    FISH_GOLD_PEAK,
    ORB_RETURN,
    ORB_TO_NAME,
    SPECIES_NAME_REVEAL,
    RESIDUE_SETTLE,
}

enum class ScenePlaybackMode { AUTO, MANUAL }

data class RecognitionSceneIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String?,
    val photoContentVersion: Long,
)

data class RecognitionSceneFrame(
    val identity: RecognitionSceneIdentity,
    val sceneElapsedMs: Long,
    val opticalReferenceElapsedMs: Long,
    val stage: RecognitionSceneStage,
    val stageElapsedMs: Long,
    val stageProgress: Float,
    val playbackMode: ScenePlaybackMode,
    val paused: Boolean,
    val reducedMotion: Boolean,
    val geometryVersion: Long,
) {
    init {
        require(sceneElapsedMs >= 0L && opticalReferenceElapsedMs >= 0L && stageElapsedMs >= 0L)
        require(stageProgress in 0f..1f)
    }

    val isIdentityValid: Boolean get() = identity.sessionId.isNotBlank() &&
        identity.attemptId.isNotBlank() && identity.imageId.isNotBlank() &&
        !identity.photoSha256.isNullOrBlank() && identity.photoContentVersion >= 0L
}
/** Stateless projection only: this class never advances or owns a clock. */
object RecognitionSceneCoordinator {
    const val PROCESSING_END_MS = 2_800L
    const val RR09_END_MS = 3_650L

    fun frameAt(
        identity: RecognitionSceneIdentity,
        sceneElapsedMs: Long,
        opticalReferenceElapsedMs: Long,
        geometryVersion: Long,
        playbackMode: ScenePlaybackMode = ScenePlaybackMode.AUTO,
        paused: Boolean = false,
        reducedMotion: Boolean = false,
    ): RecognitionSceneFrame {
        val reference = opticalReferenceElapsedMs.coerceAtLeast(0L)
        val segment = segmentAt(reference)
        return RecognitionSceneFrame(
            identity = identity,
            sceneElapsedMs = sceneElapsedMs.coerceAtLeast(0L),
            opticalReferenceElapsedMs = reference,
            stage = segment.stage,
            stageElapsedMs = (reference - segment.startMs).coerceAtLeast(0L),
            stageProgress = if (segment.durationMs == 0L) 1f else
                ((reference - segment.startMs).toFloat() / segment.durationMs).coerceIn(0f, 1f),
            playbackMode = playbackMode,
            paused = paused,
            reducedMotion = reducedMotion,
            geometryVersion = geometryVersion,
        )
    }

    private data class Segment(val stage: RecognitionSceneStage, val startMs: Long, val durationMs: Long)

    private fun segmentAt(t: Long): Segment = when {
        t == 0L -> Segment(RecognitionSceneStage.PHOTO_STABLE, 0L, 0L)
        t < 350L -> Segment(RecognitionSceneStage.ORB_WAKE, 0L, 350L)
        t < 950L -> Segment(RecognitionSceneStage.ORB_RELEASE, 350L, 600L)
        t < 1_075L -> Segment(RecognitionSceneStage.PERIMETER_ARRIVAL, 950L, 125L)
        t < 1_200L -> Segment(RecognitionSceneStage.PATH_TO_FISH, 1_075L, 125L)
        t < 1_450L -> Segment(RecognitionSceneStage.CONTOUR_A, 1_200L, 250L)
        t < 1_700L -> Segment(RecognitionSceneStage.CONTOUR_B, 1_450L, 250L)
        t < 1_950L -> Segment(RecognitionSceneStage.CONTOUR_C, 1_700L, 250L)
        t < 2_800L -> Segment(RecognitionSceneStage.FISH_GOLD_PEAK, 1_950L, 850L)
        t < 3_180L -> Segment(RecognitionSceneStage.ORB_RETURN, 2_800L, 380L)
        t < 3_350L -> Segment(RecognitionSceneStage.ORB_TO_NAME, 3_180L, 170L)
        t < 3_480L -> Segment(RecognitionSceneStage.SPECIES_NAME_REVEAL, 3_350L, 130L)
        else -> Segment(RecognitionSceneStage.RESIDUE_SETTLE, 3_480L, 170L)
    }
}
