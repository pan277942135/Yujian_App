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
            stage = segment,
            stageElapsedMs = (reference - segment.startMs).coerceAtLeast(0L),
            stageProgress = if (segment.durationMs == 0L) 1f else
                ((reference - segment.startMs).toFloat() / segment.durationMs).coerceIn(0f, 1f),
            playbackMode = playbackMode,
            paused = paused,
            reducedMotion = reducedMotion,
            geometryVersion = geometryVersion,
        )
    }

    private fun segmentAt(t: Long): RecognitionSceneStage = when {
        t == 0L -> RecognitionSceneStage.PHOTO_STABLE
        else -> RecognitionSceneStage.entries.drop(1).firstOrNull { t < it.endMs }
            ?: RecognitionSceneStage.RESIDUE_SETTLE
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

/** Playback controls project the authoritative Recognition clock; they never start a clock. */
class SceneMotionCoordinator {
    var mode: ScenePlaybackMode = ScenePlaybackMode.AUTO
        private set
    var paused: Boolean = false
        private set

    private var frozenReferenceMs: Long? = null
    private var manualReferenceMs = 0L
    private var manualStartedAtMs = 0L
    private var autoOffsetMs: Long? = null

    fun sample(authoritativeReferenceMs: Long, nowMs: Long): Long = when {
        paused -> frozenReferenceMs ?: authoritativeReferenceMs.coerceAtLeast(0L)
        mode == ScenePlaybackMode.MANUAL ->
            (manualReferenceMs + (nowMs - manualStartedAtMs).coerceAtLeast(0L)).coerceAtMost(RecognitionSceneCoordinator.RR09_END_MS)
        else -> (authoritativeReferenceMs + (autoOffsetMs ?: 0L)).coerceAtLeast(0L)
    }

    fun seek(stage: RecognitionSceneStage, nowMs: Long) {
        mode = ScenePlaybackMode.MANUAL
        paused = true
        manualReferenceMs = stage.seekTimeMs
        manualStartedAtMs = nowMs
        frozenReferenceMs = stage.seekTimeMs
        autoOffsetMs = null
    }

    fun step(delta: Int, currentStage: RecognitionSceneStage, nowMs: Long) {
        val nextIndex = (currentStage.ordinal + delta).coerceIn(0, RecognitionSceneStage.entries.lastIndex)
        seek(RecognitionSceneStage.entries[nextIndex], nowMs)
    }

    fun pause(authoritativeReferenceMs: Long, nowMs: Long) {
        if (paused) return
        frozenReferenceMs = sample(authoritativeReferenceMs, nowMs)
        if (mode == ScenePlaybackMode.MANUAL) manualReferenceMs = requireNotNull(frozenReferenceMs)
        paused = true
    }

    fun resume(authoritativeReferenceMs: Long, nowMs: Long) {
        if (!paused) return
        val frozen = frozenReferenceMs ?: authoritativeReferenceMs.coerceAtLeast(0L)
        if (mode == ScenePlaybackMode.AUTO) {
            autoOffsetMs = frozen - authoritativeReferenceMs
        } else {
            manualReferenceMs = frozen
            manualStartedAtMs = nowMs
        }
        frozenReferenceMs = null
        paused = false
    }

    fun playAuto(authoritativeReferenceMs: Long, nowMs: Long) {
        val current = sample(authoritativeReferenceMs, nowMs)
        mode = ScenePlaybackMode.AUTO
        paused = false
        frozenReferenceMs = null
        autoOffsetMs = current - authoritativeReferenceMs
    }
}
