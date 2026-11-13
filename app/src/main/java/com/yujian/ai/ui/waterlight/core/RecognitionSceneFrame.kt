package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage

/** Semantic scene states. Durations are local minimum visibility targets, never a global timeline. */
enum class RecognitionSceneStage(val minimumVisibleMs: Long, val displayName: String) {
    PHOTO_STABLE(0L, "照片稳定"),
    ORB_WAKE(350L, "光球苏醒"),
    ORB_RELEASE(600L, "光球释放水光"),
    PERIMETER_ARRIVAL(125L, "照片外围激活"),
    PATH_TO_FISH(125L, "水光寻鱼"),
    CONTOUR_A(450L, "鱼体轮廓初显"),
    CONTOUR_B(450L, "鱼体轮廓形成"),
    CONTOUR_C(450L, "鱼体轮廓闭合"),
    FISH_GOLD_PEAK(1_000L, "鱼体金光稳定"),
    ORB_RETURN(350L, "能量返回光球"),
    ORB_TO_NAME(850L, "光球舒展凝字"),
    SPECIES_NAME_REVEAL(800L, "鱼种名称揭晓"),
    RESIDUE_SETTLE(450L, "水光余辉收束");

    /** Compatibility alias for consumers that need a local envelope length. */
    val durationMs: Long get() = minimumVisibleMs
    val timeAnchorLabel: String get() = "≥${minimumVisibleMs}ms / 本阶段"
}

enum class ScenePlaybackMode { AUTO, MANUAL }

enum class RecognitionProgressDisplayMode { STEP_DIAGNOSTIC, NORMAL_PROGRESS }

enum class SceneResourceState { WAITING, READY, FAILED }
enum class SceneMaskState { NONE, PENDING, TRUSTED, UNTRUSTED, FAILED }
enum class SceneGeometryState { PENDING, READY, INVALID }
enum class SceneRunState { RUNNING, PAUSED, WAITING, FAILED }

data class RecognitionSceneModelGates(
    val detector: SceneResourceState = SceneResourceState.WAITING,
    val classifier: SceneResourceState = SceneResourceState.WAITING,
    val slimSam: SceneMaskState = SceneMaskState.PENDING,
    val mask: SceneMaskState = SceneMaskState.NONE,
    val geometry: SceneGeometryState = SceneGeometryState.PENDING,
    val runState: SceneRunState = SceneRunState.RUNNING,
    val waitingReason: String? = null,
)

const val DIAGNOSTIC_STEP_DURATION_MS = 3_000L

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
    /** Scene-wide monotonic sample for ambient motion only; never selects or completes a stage. */
    val opticalReferenceElapsedMs: Long,
    val stage: RecognitionSceneStage,
    val stageElapsedMs: Long,
    val stageProgress: Float,
    val playbackMode: ScenePlaybackMode,
    val paused: Boolean,
    val reducedMotion: Boolean,
    val geometryVersion: Long,
    val modelGates: RecognitionSceneModelGates = RecognitionSceneModelGates(),
    val eventSequence: Long = 0L,
    val monotonicTimestampMs: Long = 0L,
    val energyEvent: EnergyEvent? = null,
    val stageEventComplete: Boolean = false,
    val resultHandoffSubstage: ResultHandoffSubstage? = null,
    val resultInfoArrivalProgress: Float = 0f,
    val resultSceneStable: Boolean = false,
) {
    init {
        require(sceneElapsedMs >= 0L && opticalReferenceElapsedMs >= 0L && stageElapsedMs >= 0L)
        require(stageProgress in 0f..1f)
        require(resultInfoArrivalProgress in 0f..1f)
        require(eventSequence >= 0L && monotonicTimestampMs >= 0L)
    }

    val isIdentityValid: Boolean get() = identity.sessionId.isNotBlank() &&
        identity.attemptId.isNotBlank() && identity.imageId.isNotBlank() &&
        !identity.photoSha256.isNullOrBlank() && identity.photoContentVersion >= 0L
}

/** Stateless projection only: stage identity and local progress must be supplied by the controller. */
object RecognitionSceneCoordinator {
    fun frameForStage(
        identity: RecognitionSceneIdentity,
        sceneElapsedMs: Long,
        opticalReferenceElapsedMs: Long,
        geometryVersion: Long,
        stage: RecognitionSceneStage,
        stageElapsedMs: Long,
        stageProgress: Float,
        playbackMode: ScenePlaybackMode = ScenePlaybackMode.AUTO,
        paused: Boolean = false,
        reducedMotion: Boolean = false,
        modelGates: RecognitionSceneModelGates = RecognitionSceneModelGates(),
        eventSequence: Long = 0L,
        monotonicTimestampMs: Long = 0L,
        energyEvent: EnergyEvent? = null,
        stageEventComplete: Boolean = false,
        resultHandoffSubstage: ResultHandoffSubstage? = null,
        resultInfoArrivalProgress: Float = 0f,
        resultSceneStable: Boolean = false,
    ): RecognitionSceneFrame = RecognitionSceneFrame(
        identity = identity,
        sceneElapsedMs = sceneElapsedMs.coerceAtLeast(0L),
        opticalReferenceElapsedMs = opticalReferenceElapsedMs.coerceAtLeast(0L),
        stage = stage,
        stageElapsedMs = stageElapsedMs.coerceAtLeast(0L),
        stageProgress = stageProgress.coerceIn(0f, 1f),
        playbackMode = playbackMode,
        paused = paused,
        reducedMotion = reducedMotion,
        geometryVersion = geometryVersion,
        modelGates = modelGates,
        eventSequence = eventSequence,
        monotonicTimestampMs = monotonicTimestampMs,
        energyEvent = energyEvent,
        stageEventComplete = stageEventComplete,
        resultHandoffSubstage = resultHandoffSubstage,
        resultInfoArrivalProgress = resultInfoArrivalProgress.coerceIn(0f, 1f),
        resultSceneStable = resultSceneStable,
    )
}

/** Page-entry projections keep their original 700/900 ms behavior, separate from recognition gates. */
object RecognitionSceneTimelineProjection {
    const val PHOTO_SETTLE_DURATION_MS = 700L
    const val ORB_ENTRANCE_DURATION_MS = 900L

    fun photoEntryProgress(sceneElapsedMs: Long, settled: Boolean = false): Float =
        if (settled) 1f else (sceneElapsedMs.coerceAtLeast(0L).toFloat() / PHOTO_SETTLE_DURATION_MS).coerceIn(0f, 1f)

    fun orbEntranceElapsedMs(sceneElapsedMs: Long): Long =
        sceneElapsedMs.coerceAtLeast(0L).coerceAtMost(ORB_ENTRANCE_DURATION_MS)
}

/** Source-compatibility name; all scene playback and semantic stage state lives in one controller. */
typealias SceneMotionCoordinator = RecognitionSceneAnimationController
