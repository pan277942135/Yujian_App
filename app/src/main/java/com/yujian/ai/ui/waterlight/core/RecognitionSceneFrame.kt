package com.yujian.ai.ui.waterlight.core

/** The optical timeline is sampled from Recognition's existing monotonic clock. */
enum class RecognitionSceneStage(val startMs: Long, val endMs: Long, val displayName: String) {
    PHOTO_STABLE(0L, 0L, "照片稳定"),
    ORB_WAKE(0L, 350L, "光球苏醒"),
    ORB_RELEASE(350L, 950L, "光球释放水光"),
    PERIMETER_ARRIVAL(950L, 1_075L, "照片外围激活"),
    PATH_TO_FISH(1_075L, 1_200L, "水光寻鱼"),
    CONTOUR_A(1_200L, 1_450L, "鱼体轮廓初显"),
    CONTOUR_B(1_450L, 1_700L, "鱼体轮廓形成"),
    CONTOUR_C(1_700L, 1_950L, "鱼体轮廓闭合"),
    FISH_GOLD_PEAK(1_950L, 2_800L, "鱼体金光稳定"),
    ORB_RETURN(2_800L, 3_180L, "能量返回光球"),
    ORB_TO_NAME(3_180L, 3_350L, "光球舒展凝字"),
    SPECIES_NAME_REVEAL(3_350L, 3_480L, "鱼种名称揭晓"),
    RESIDUE_SETTLE(3_480L, 3_650L, "水光余辉收束");

    val durationMs: Long get() = endMs - startMs

    /** The zero-time PHOTO_STABLE marker precedes the first animated frame at 1 ms. */
    val seekTimeMs: Long get() = if (this == ORB_WAKE) 1L else startMs

    val timeAnchorLabel: String get() = if (durationMs == 0L) "0ms" else "$startMs–${endMs}ms"
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
) {
    init {
        require(sceneElapsedMs >= 0L && opticalReferenceElapsedMs >= 0L && stageElapsedMs >= 0L)
        require(stageProgress in 0f..1f)
        require(eventSequence >= 0L && monotonicTimestampMs >= 0L)
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
        modelGates: RecognitionSceneModelGates = RecognitionSceneModelGates(),
        eventSequence: Long = 0L,
        monotonicTimestampMs: Long = 0L,
        energyEvent: EnergyEvent? = null,
    ): RecognitionSceneFrame {
        val reference = opticalReferenceElapsedMs.coerceAtLeast(0L)
        val segment = stageAt(reference)
        return RecognitionSceneFrame(
            identity = identity,
            sceneElapsedMs = sceneElapsedMs.coerceAtLeast(0L),
            opticalReferenceElapsedMs = reference,
            stage = segment,
            stageElapsedMs = (reference - segment.startMs).coerceAtLeast(0L),
            stageProgress = if (segment.durationMs == 0L) 1f else
                ((reference - segment.startMs).toFloat() / segment.durationMs).coerceIn(0f, 1f),
            playbackMode = playbackMode,
            paused = paused,
            reducedMotion = reducedMotion,
            geometryVersion = geometryVersion,
            modelGates = modelGates,
            eventSequence = eventSequence,
            monotonicTimestampMs = monotonicTimestampMs,
            energyEvent = energyEvent,
        )
    }

    /** Production projection: stage identity and local progress come from the event-driven controller. */
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
    )

    fun stageAt(t: Long): RecognitionSceneStage = when {
        t == 0L -> RecognitionSceneStage.PHOTO_STABLE
        else -> RecognitionSceneStage.entries.drop(1).firstOrNull { t < it.endMs }
            ?: RecognitionSceneStage.RESIDUE_SETTLE
    }

    fun previewReferenceMs(stage: RecognitionSceneStage, elapsedMs: Long): Long {
        if (stage == RecognitionSceneStage.PHOTO_STABLE) return 0L
        val progress = (elapsedMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS).toDouble() /
            DIAGNOSTIC_STEP_DURATION_MS.toDouble())
        val lastSample = if (stage == RecognitionSceneStage.RESIDUE_SETTLE) stage.endMs else stage.endMs - 1L
        val firstSample = stage.seekTimeMs
        return (firstSample + ((lastSample - firstSample) * progress).toLong())
            .coerceIn(firstSample, lastSample)
    }
}

/** Parallel page-entry projections share the same sample from RecognitionSceneFrame. */
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
