package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffContract
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
enum class SceneRunState { RUNNING, PAUSED, WAITING, FAILED, COMPLETE }

/** Optical settle is a stage milestone; full completion also requires all accepted Result events. */
object RecognitionSceneCompletionContract {
    fun isComplete(
        opticalResidueComplete: Boolean,
        completedResultHandoffEvents: Set<ResultHandoffSubstage>,
        resultSceneStable: Boolean,
    ): Boolean = opticalResidueComplete && resultSceneStable &&
        RecognitionResultHandoffContract.isComplete(completedResultHandoffEvents)
}

/** Attempt-local explanation for a trusted Mask → P* → Fish Path join. */
enum class RecognitionFishPathBindingStatus {
    WAITING_FOR_PHOTO_GEOMETRY,
    WAITING_FOR_MASK_BINDING,
    WAITING_FOR_PATH_REGISTRATION,
    PATH_IDENTITY_MISMATCH,
    PATH_TERMINAL_UNAVAILABLE,
    PATH_BOUND,
    NOT_APPLICABLE,
}

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

/** Receipt emitted only after the final trusted Mask contour and Gold layers were drawn. */
data class RecognitionFishGoldRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val stage: RecognitionSceneStage,
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
    val fishPathId: String,
    val renderedProgress: Float,
    val contourDrawn: Boolean,
    val goldCoreDrawn: Boolean,
    val softGlowDrawn: Boolean,
    val outerHaloDrawn: Boolean,
    val glintDrawn: Boolean,
    val drawPassSequence: Long,
)

object RecognitionFishGoldRenderContract {
    fun isComplete(
        identity: RecognitionSceneIdentity,
        maskSha256: String,
        maskVersion: Long,
        geometryVersion: Long,
        fishPathId: String,
        receipt: RecognitionFishGoldRenderReceipt,
    ): Boolean = receipt.identity == identity && receipt.stage == RecognitionSceneStage.FISH_GOLD_PEAK &&
        receipt.maskSha256 == maskSha256 && maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
        receipt.maskVersion == maskVersion && maskVersion >= 0L &&
        receipt.geometryVersion == geometryVersion && geometryVersion > 0L &&
        receipt.fishPathId == fishPathId && fishPathId.isNotBlank() &&
        receipt.renderedProgress.isFinite() && receipt.renderedProgress >= .999f &&
        receipt.contourDrawn && receipt.goldCoreDrawn && receipt.softGlowDrawn &&
        receipt.outerHaloDrawn && receipt.glintDrawn && receipt.drawPassSequence > 0L
}

/** Renderer receipt for the actual Result-host Orb RETURN path and measured viewport. */
data class RecognitionOrbReturnRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val stage: RecognitionSceneStage,
    val pathId: String,
    val maskSha256: String?,
    val maskVersion: Long?,
    val pathGeometryVersion: Long?,
    val resultGeometryVersion: Long,
    val pathGeometry: WaterLightPathGeometry?,
    val pathSegments: List<WaterLightPathGeometry>,
    val renderedSegmentCount: Int,
    val renderedProgress: Float,
    val renderedPathLength: Float,
    val pathCanvasSubmitted: Boolean,
    val drawPassSequence: Long,
)

object RecognitionOrbReturnRenderContract {
    fun isComplete(
        identity: RecognitionSceneIdentity,
        snapshot: RecognitionFishPathSnapshot?,
        resultGeometryVersion: Long,
        receipt: RecognitionOrbReturnRenderReceipt,
    ): Boolean {
        if (receipt.identity != identity || receipt.stage != RecognitionSceneStage.ORB_RETURN ||
            receipt.pathId.isBlank() || receipt.resultGeometryVersion != resultGeometryVersion ||
            resultGeometryVersion <= 0L || receipt.renderedProgress.isFinite().not() ||
            receipt.renderedProgress < .999f || receipt.renderedPathLength.isFinite().not() ||
            receipt.renderedPathLength <= 1f || !receipt.pathCanvasSubmitted || receipt.drawPassSequence <= 0L
        ) return false
        return if (snapshot != null) {
            snapshot.isValid() && receipt.pathId == snapshot.returnPathId &&
                receipt.maskSha256 == snapshot.maskSha256 && receipt.maskVersion == snapshot.maskVersion &&
                receipt.pathGeometryVersion == snapshot.geometryVersion &&
                receipt.pathSegments == snapshot.returnPathSegments &&
                receipt.renderedSegmentCount == snapshot.returnPathSegments.size &&
                receipt.renderedSegmentCount == 3
        } else {
            receipt.maskSha256 == null && receipt.maskVersion == null && receipt.pathGeometryVersion == null &&
                receipt.pathSegments.isEmpty() && receipt.renderedSegmentCount == 1 &&
                receipt.pathGeometry?.isValid() == true
        }
    }
}

/** Accumulates the portions of the real projected RETURN path submitted to Canvas. */
class RecognitionOrbReturnPathDrawCoverage(segmentLengthsPx: List<Float>) {
    private data class Range(val start: Float, val end: Float)

    private val segmentLengths = segmentLengthsPx.toList()
    private val drawnRanges = mutableListOf<Range>()
    val totalLengthPx: Float = segmentLengths.sum()

    init {
        require(segmentLengths.isNotEmpty() && segmentLengths.all { it.isFinite() && it > 0f })
    }

    fun recordDrawnRange(startPx: Float, endPx: Float) {
        if (!startPx.isFinite() || !endPx.isFinite() || endPx <= startPx) return
        val start = startPx.coerceIn(0f, totalLengthPx)
        val end = endPx.coerceIn(0f, totalLengthPx)
        if (end <= start) return
        val sorted = (drawnRanges + Range(start, end)).sortedBy(Range::start)
        drawnRanges.clear()
        sorted.forEach { next ->
            val previous = drawnRanges.lastOrNull()
            if (previous != null && next.start <= previous.end + COVERAGE_JOIN_TOLERANCE_PX) {
                drawnRanges[drawnRanges.lastIndex] = Range(previous.start, maxOf(previous.end, next.end))
            } else {
                drawnRanges += next
            }
        }
    }

    /** Counts only route segments whose entire measured distance has actually been drawn. */
    fun fullyCoveredSegmentCount(): Int {
        var segmentStart = 0f
        var covered = 0
        segmentLengths.forEach { length ->
            val segmentEnd = segmentStart + length
            if (drawnRanges.any { range ->
                    range.start <= segmentStart + COVERAGE_JOIN_TOLERANCE_PX &&
                        range.end >= segmentEnd - COVERAGE_JOIN_TOLERANCE_PX
                }
            ) covered++
            segmentStart = segmentEnd
        }
        return covered
    }

    fun isComplete(): Boolean = fullyCoveredSegmentCount() == segmentLengths.size

    companion object {
        private const val COVERAGE_JOIN_TOLERANCE_PX = .75f
    }
}

/** Receipt proving that a real, ordered contour slice reached the target Canvas frame. */
data class RecognitionContourRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val stage: RecognitionSceneStage,
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
    val fishPathId: String,
    val stageProgress: Float,
    val renderedContourProgress: Float,
    val contourStrokeDrawn: Boolean,
    val drawPassSequence: Long,
)

object RecognitionContourRenderContract {
    fun targetProgress(stage: RecognitionSceneStage): Float? = when (stage) {
        RecognitionSceneStage.CONTOUR_A -> 1f / 3f
        RecognitionSceneStage.CONTOUR_B -> 2f / 3f
        RecognitionSceneStage.CONTOUR_C -> 1f
        else -> null
    }

    fun isComplete(receipt: RecognitionContourRenderReceipt): Boolean {
        val target = targetProgress(receipt.stage) ?: return false
        return receipt.identity.sessionId.isNotBlank() && receipt.identity.attemptId.isNotBlank() &&
            receipt.identity.imageId.isNotBlank() && receipt.identity.photoSha256?.isNotBlank() == true &&
            receipt.maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) && receipt.maskVersion >= 0L &&
            receipt.geometryVersion > 0L && receipt.fishPathId.isNotBlank() &&
            receipt.stageProgress.isFinite() && receipt.stageProgress >= .999f &&
            receipt.renderedContourProgress.isFinite() && receipt.renderedContourProgress + .0001f >= target &&
            receipt.contourStrokeDrawn && receipt.drawPassSequence > 0L
    }
}

/** A real final optical frame showing that the shared afterglow reached zero. */
data class RecognitionResidueRenderReceipt(
    val identity: RecognitionSceneIdentity,
    val stage: RecognitionSceneStage,
    val stageProgress: Float,
    val afterglowAlpha: Float,
    val residueCanvasSubmitted: Boolean,
    val drawPassSequence: Long,
)

object RecognitionResidueRenderContract {
    fun isComplete(receipt: RecognitionResidueRenderReceipt): Boolean =
        receipt.identity.sessionId.isNotBlank() && receipt.identity.attemptId.isNotBlank() &&
            receipt.identity.imageId.isNotBlank() && receipt.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
            receipt.stageProgress.isFinite() && receipt.stageProgress >= .999f &&
            receipt.afterglowAlpha.isFinite() && receipt.afterglowAlpha <= .001f &&
            receipt.residueCanvasSubmitted && receipt.drawPassSequence > 0L
}

/** One closed interval from the production scene controller's monotonic uptime clock. */
data class RecognitionSceneStageTraceEntry(
    val stage: RecognitionSceneStage,
    val stageOrdinal: Int,
    val stageStartTimestampMs: Long,
    val stageEndTimestampMs: Long,
    val stageDurationMs: Long,
    val requiredDurationMs: Long,
    val gateWaitDurationMs: Long,
    val gateWaitReason: String?,
    val localProgress: Float,
    val sessionId: String,
    val attemptId: String,
    val eventSequence: Long,
) {
    val activeDurationMs: Long get() = (stageDurationMs - gateWaitDurationMs).coerceAtLeast(0L)
}

enum class RecognitionSceneStageEventAction { ENTERED, EXITED, BLOCKED }

/** Controller-authored event. Screens may consume it, but never infer stage changes themselves. */
data class RecognitionSceneStageEvent(
    val action: RecognitionSceneStageEventAction,
    val stage: RecognitionSceneStage,
    val sceneElapsedMs: Long,
    val stageElapsedMs: Long,
    val stageProgress: Float,
    val geometryVersion: Long,
    val playbackMode: ScenePlaybackMode,
    val modelGates: RecognitionSceneModelGates,
    val waitingReason: String?,
    val eventSequence: Long,
    val sessionId: String,
    val attemptId: String,
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
    val taskEnergyMerged: Boolean = false,
    val orbToGlyphAccepted: Boolean = false,
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
        taskEnergyMerged: Boolean = false,
        orbToGlyphAccepted: Boolean = false,
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
        taskEnergyMerged = taskEnergyMerged,
        orbToGlyphAccepted = orbToGlyphAccepted,
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
