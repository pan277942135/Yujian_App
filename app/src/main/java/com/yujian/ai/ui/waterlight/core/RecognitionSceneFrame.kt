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

/**
 * Playback controls project the authoritative Recognition clock. Manual diagnostics time-warp
 * the same scene timeline; they do not create another frame clock or alter production timing.
 */
class SceneMotionCoordinator {
    var mode: ScenePlaybackMode = ScenePlaybackMode.AUTO
        private set
    var paused: Boolean = false
        private set
    var currentStage: RecognitionSceneStage = RecognitionSceneStage.PHOTO_STABLE
        private set
    var previewCompleted: Boolean = false
        private set

    private var frozenReferenceMs: Long? = null
    private var previewStartedAtMs: Long? = null
    private var previewElapsedBeforePauseMs = 0L
    private var autoOffsetMs = 0L
    private var autoStageStartedAtMs: Long? = null
    private var autoStageElapsedBeforePauseMs = 0L
    private var diagnosticAuto = false

    fun sample(authoritativeReferenceMs: Long, nowMs: Long): Long {
        if (mode == ScenePlaybackMode.MANUAL) {
            updatePreviewCompletion(nowMs)
            return RecognitionSceneCoordinator.previewReferenceMs(currentStage, previewElapsedMsWithoutUpdate(nowMs))
        }
        if (paused) return frozenReferenceMs ?: authoritativeReferenceMs.coerceAtLeast(0L)

        if (!diagnosticAuto) {
            val reference = (authoritativeReferenceMs + autoOffsetMs).coerceAtLeast(0L)
            currentStage = RecognitionSceneCoordinator.stageAt(reference)
            return reference
        }

        // Diagnostic AUTO slows the same authoritative scene projection to at least
        // three seconds per semantic stage. It can never run ahead of the stage
        // reached by the production controller, so real model gates remain in force.
        val elapsed = autoStageElapsedMs(nowMs)
        val authoritativeStage = RecognitionSceneCoordinator.stageAt(authoritativeReferenceMs)
        var remaining = elapsed
        while (remaining >= DIAGNOSTIC_STEP_DURATION_MS && currentStage.ordinal < authoritativeStage.ordinal) {
            remaining -= DIAGNOSTIC_STEP_DURATION_MS
            currentStage = RecognitionSceneStage.entries[currentStage.ordinal + 1]
        }
        autoStageElapsedBeforePauseMs = remaining.coerceAtMost(DIAGNOSTIC_STEP_DURATION_MS)
        autoStageStartedAtMs = nowMs
        if (remaining >= DIAGNOSTIC_STEP_DURATION_MS && currentStage == authoritativeStage) {
            autoStageElapsedBeforePauseMs = DIAGNOSTIC_STEP_DURATION_MS
        }
        return RecognitionSceneCoordinator.previewReferenceMs(currentStage, autoStageElapsedBeforePauseMs)
    }

    /** Prepare stage 01 before the photo's real first stable frame; its 3 s begins on presentation. */
    fun prepareInitialStepPreview() {
        mode = ScenePlaybackMode.MANUAL
        currentStage = RecognitionSceneStage.PHOTO_STABLE
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        frozenReferenceMs = 0L
        autoOffsetMs = 0L
        diagnosticAuto = false
        autoStageStartedAtMs = null
        autoStageElapsedBeforePauseMs = 0L
    }

    fun startPreparedStepPreview(nowMs: Long) {
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted || previewStartedAtMs != null) return
        previewStartedAtMs = nowMs
    }

    fun beginStepPreview(
        stage: RecognitionSceneStage,
        nowMs: Long,
        startImmediately: Boolean = true,
    ) {
        mode = ScenePlaybackMode.MANUAL
        currentStage = stage
        paused = !startImmediately
        previewCompleted = false
        previewElapsedBeforePauseMs = 0L
        previewStartedAtMs = nowMs.takeIf { startImmediately }
        frozenReferenceMs = RecognitionSceneCoordinator.previewReferenceMs(stage, 0L)
        autoOffsetMs = 0L
        diagnosticAuto = false
        autoStageStartedAtMs = null
        autoStageElapsedBeforePauseMs = 0L
    }

    fun advanceToNextStage(nowMs: Long): RecognitionSceneStage? {
        if (currentStage == RecognitionSceneStage.entries.last()) return null
        val next = RecognitionSceneStage.entries[currentStage.ordinal + 1]
        beginStepPreview(next, nowMs)
        return next
    }

    fun returnToPreviousStage(nowMs: Long): RecognitionSceneStage? {
        if (currentStage == RecognitionSceneStage.entries.first()) return null
        val previous = RecognitionSceneStage.entries[currentStage.ordinal - 1]
        beginStepPreview(previous, nowMs)
        return previous
    }

    fun restartCurrentStage(nowMs: Long) = beginStepPreview(currentStage, nowMs)

    fun previewElapsedMs(nowMs: Long): Long {
        updatePreviewCompletion(nowMs)
        return previewElapsedMsWithoutUpdate(nowMs)
    }

    fun previewProgress(nowMs: Long): Float =
        (previewElapsedMs(nowMs).toFloat() / DIAGNOSTIC_STEP_DURATION_MS).coerceIn(0f, 1f)

    fun previewRemainingMs(nowMs: Long): Long =
        (DIAGNOSTIC_STEP_DURATION_MS - previewElapsedMs(nowMs)).coerceAtLeast(0L)

    fun seek(stage: RecognitionSceneStage, nowMs: Long) {
        beginStepPreview(stage, nowMs)
    }

    fun step(delta: Int, currentStage: RecognitionSceneStage, nowMs: Long) {
        val current = if (mode == ScenePlaybackMode.MANUAL) this.currentStage else currentStage
        val nextIndex = (current.ordinal + delta).coerceIn(0, RecognitionSceneStage.entries.lastIndex)
        beginStepPreview(RecognitionSceneStage.entries[nextIndex], nowMs)
    }

    fun pause(authoritativeReferenceMs: Long, nowMs: Long) {
        if (paused) return
        if (mode == ScenePlaybackMode.MANUAL) {
            val elapsed = previewElapsedMsWithoutUpdate(nowMs).coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
            previewElapsedBeforePauseMs = elapsed
            previewStartedAtMs = null
            frozenReferenceMs = RecognitionSceneCoordinator.previewReferenceMs(currentStage, elapsed)
            previewCompleted = elapsed >= DIAGNOSTIC_STEP_DURATION_MS
            paused = true
            return
        }
        frozenReferenceMs = sample(authoritativeReferenceMs, nowMs)
        if (diagnosticAuto) {
            autoStageElapsedBeforePauseMs = autoStageElapsedMs(nowMs).coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
            autoStageStartedAtMs = null
        }
        paused = true
    }

    fun resume(authoritativeReferenceMs: Long, nowMs: Long) {
        if (!paused) return
        if (mode == ScenePlaybackMode.MANUAL) {
            if (previewCompleted) return
            previewStartedAtMs = nowMs
            paused = false
            frozenReferenceMs = null
            return
        }
        if (diagnosticAuto) autoStageStartedAtMs = nowMs
        else {
            val frozen = frozenReferenceMs ?: authoritativeReferenceMs.coerceAtLeast(0L)
            autoOffsetMs = frozen - authoritativeReferenceMs
        }
        frozenReferenceMs = null
        paused = false
    }

    fun playAuto(authoritativeReferenceMs: Long, nowMs: Long) {
        playProductionTimeline(authoritativeReferenceMs, nowMs)
    }

    fun playProductionTimeline(authoritativeReferenceMs: Long, nowMs: Long) {
        mode = ScenePlaybackMode.AUTO
        diagnosticAuto = false
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        frozenReferenceMs = null
        autoOffsetMs = 0L
        currentStage = RecognitionSceneCoordinator.stageAt(authoritativeReferenceMs)
        val stage = currentStage
        val progress = if (stage.durationMs == 0L) 0f else
            ((authoritativeReferenceMs - stage.startMs).toFloat() / stage.durationMs).coerceIn(0f, 1f)
        autoStageElapsedBeforePauseMs = (progress * DIAGNOSTIC_STEP_DURATION_MS).toLong()
        autoStageStartedAtMs = null
    }

    /** Debug-only AUTO: preserve each diagnostic stage for at least three seconds. */
    fun playDiagnosticAuto(authoritativeReferenceMs: Long, nowMs: Long) {
        mode = ScenePlaybackMode.AUTO
        diagnosticAuto = true
        paused = false
        previewCompleted = false
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        frozenReferenceMs = null
        autoOffsetMs = 0L
        currentStage = RecognitionSceneCoordinator.stageAt(authoritativeReferenceMs)
        val stage = currentStage
        val progress = if (stage.durationMs == 0L) 0f else
            ((authoritativeReferenceMs - stage.startMs).toFloat() / stage.durationMs).coerceIn(0f, 1f)
        autoStageElapsedBeforePauseMs = (progress * DIAGNOSTIC_STEP_DURATION_MS).toLong()
        autoStageStartedAtMs = nowMs
    }

    fun switchToStepDiagnostic(authoritativeReferenceMs: Long, nowMs: Long) {
        val reference = sample(authoritativeReferenceMs, nowMs)
        val stage = RecognitionSceneCoordinator.stageAt(reference)
        beginStepPreview(stage, nowMs)
        if (stage != RecognitionSceneStage.PHOTO_STABLE) {
            val first = stage.seekTimeMs
            val last = if (stage == RecognitionSceneStage.RESIDUE_SETTLE) stage.endMs else stage.endMs - 1L
            val fraction = ((reference - first).toDouble() / (last - first).coerceAtLeast(1L))
                .coerceIn(0.0, 1.0)
            previewElapsedBeforePauseMs = (fraction * DIAGNOSTIC_STEP_DURATION_MS).toLong()
            previewStartedAtMs = nowMs
        }
    }

    /** Start a Result RR09 step only after its shared photo/hero/title geometry is ready. */
    fun holdCurrentPreviewAtStart() {
        if (mode != ScenePlaybackMode.MANUAL || previewCompleted) return
        previewStartedAtMs = null
        previewElapsedBeforePauseMs = 0L
        paused = true
        frozenReferenceMs = RecognitionSceneCoordinator.previewReferenceMs(currentStage, 0L)
    }

    private fun updatePreviewCompletion(nowMs: Long) {
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted || previewStartedAtMs == null) return
        if (previewElapsedMsWithoutUpdate(nowMs) >= DIAGNOSTIC_STEP_DURATION_MS) {
            previewElapsedBeforePauseMs = DIAGNOSTIC_STEP_DURATION_MS
            previewStartedAtMs = null
            paused = true
            previewCompleted = true
            frozenReferenceMs = RecognitionSceneCoordinator.previewReferenceMs(currentStage, DIAGNOSTIC_STEP_DURATION_MS)
        }
    }

    private fun autoStageElapsedMs(nowMs: Long): Long {
        val startedAt = autoStageStartedAtMs ?: return autoStageElapsedBeforePauseMs
        return (autoStageElapsedBeforePauseMs + (nowMs - startedAt).coerceAtLeast(0L))
            .coerceAtLeast(0L)
    }

    private fun previewElapsedMsWithoutUpdate(nowMs: Long): Long {
        if (mode != ScenePlaybackMode.MANUAL || paused || previewCompleted) {
            return previewElapsedBeforePauseMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
        }
        val startedAt = previewStartedAtMs ?: return previewElapsedBeforePauseMs.coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
        return (previewElapsedBeforePauseMs + (nowMs - startedAt).coerceAtLeast(0L))
            .coerceIn(0L, DIAGNOSTIC_STEP_DURATION_MS)
    }
}
