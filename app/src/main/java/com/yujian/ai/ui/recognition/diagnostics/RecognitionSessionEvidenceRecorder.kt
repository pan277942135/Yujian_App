package com.yujian.ai.ui.recognition.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Window
import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.EnergyEventRejectionReason
import com.yujian.ai.ui.waterlight.core.RecognitionTaskFilamentState
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneModelGates
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageEvent
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageEventAction
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageTraceEntry
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class RecognitionEvidenceExportSnapshot(
    val attemptDirectory: File,
    val identity: RecognitionEvidenceIdentity?,
    val sessionIdHash: String?,
    val attemptIdHash: String?,
    val photoSha256: String?,
    val recoveredAttempt: Boolean,
    val summary: JSONObject,
    val captureIndex: JSONArray,
    val queueDiagnostics: RecognitionEvidenceQueueDiagnostics,
    val degradedReasons: List<String>,
    val zipExportRequestEvidenceSequence: Long?,
)

private data class RecognitionEvidenceExportTarget(
    val directory: File,
    val identity: RecognitionEvidenceIdentity?,
    val sink: RecognitionEvidenceEventSink?,
    val recovered: RecognitionSessionEvidenceStore.RecoveredAttemptStore?,
)

/** One Session/Attempt evidence owner shared by Processing, Result, and Export. */
class RecognitionSessionEvidenceRecorder(
    context: Context,
    private val clockMs: () -> Long = { SystemClock.uptimeMillis() },
) {
    private val applicationContext = context.applicationContext
    private val store = RecognitionSessionEvidenceStore(applicationContext)
    private val pixelIo = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yujian-pixelcopy-evidence").apply { isDaemon = true }
    }
    private val maintenanceIo = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yujian-evidence-maintenance").apply { isDaemon = true }
    }
    private val captureLock = ReentrantLock()
    private val capturesIdle = captureLock.newCondition()
    private var pendingCaptures = 0
    private val lock = Any()
    private val pixelFileLock = Any()
    private var activeGeneration = 0L
    private var activeIdentity: RecognitionEvidenceIdentity? = null
    private var activeAttempt: RecognitionSessionEvidenceStore.AttemptStore? = null
    private var eventSink: RecognitionEvidenceEventSink? = null
    private val summaryWriteScheduled = AtomicBoolean(false)
    private var activeTerminalState: RecognitionAttemptTerminalState? = null
    private val recoveredAttempts = store.recoverUnfinishedAttempts()
    private val stageStatuses = linkedMapOf<RecognitionSceneStage, RecognitionEvidenceStageStatus>()
    private var recordedStageTimelineCount = 0
    private val entryCaptureRequested = linkedSetOf<String>()
    private val captureRows = mutableListOf<JSONObject>()
    private var lastMotionRequestMs = Long.MIN_VALUE
    private var lastHeartbeatMs = Long.MIN_VALUE
    private var frameSequence = 0L
    private var screenshotRequestCount = 0
    private var screenshotSuccessCount = 0
    private var screenshotFailureCount = 0
    private var motionRequestCount = 0
    private var resultStableCaptureRequested = false
    private var pixelCopyInFlight = false
    private var pixelCopySuspendedReason: String? = null
    private var firstFrameTimestampMs: Long? = null
    private var sceneClockStartTimestampMs: Long? = null
    private var orbFirstFrameTimestampMs: Long? = null
    private var gpuFirstFrameTimestampMs: Long? = null
    private var pixelCopyFirstFrameTimestampMs: Long? = null
    private var resultFirstFrameTimestampMs: Long? = null
    private var latestSceneElapsedMs = 0L
    private var latestSceneStage: RecognitionSceneStage? = null
    private var lastManualPixelCopySuppressionStage: RecognitionSceneStage? = null
    private var latestPlaybackMode = ScenePlaybackMode.AUTO
    private var latestStageProgress = 0f
    private var latestGeometryVersion = -1L
    private var lastRenderTraceTimestampMs = Long.MIN_VALUE
    private var lastRenderTraceStage: RecognitionSceneStage? = null
    private var lastSurfaceTraceStage: RecognitionSceneStage? = null
    private var latestWaitingReason: String? = null

    init { store.installCrashRecovery() }

    val recoveredAttemptCount: Int get() = synchronized(lock) { recoveredAttempts.size }
    fun recoveredAttemptIds(): List<String> = synchronized(lock) { recoveredAttempts.map { it.attemptIdHash } }

    fun beginAttempt(identity: RecognitionEvidenceIdentity) {
        val previousAttempt = synchronized(lock) { activeAttempt }
        if (previousAttempt != null && synchronized(lock) { activeTerminalState == null }) {
            finishAttempt(RecognitionAttemptTerminalState.INTERRUPTED, "SUPERSEDED_BY_NEW_ATTEMPT")
        }
        synchronized(lock) {
            activeGeneration++
            eventSink?.flush()
            eventSink?.dispose()
            activeIdentity = identity
            activeAttempt = store.begin(identity)
            activeTerminalState = null
            eventSink = RecognitionEvidenceEventSink(requireNotNull(activeAttempt).directory, clockMs)
            stageStatuses.clear()
            recordedStageTimelineCount = 0
            RecognitionSceneStage.entries.forEach { stage ->
                stageStatuses[stage] = RecognitionEvidenceStageStatus(stage, "NOT_REACHED")
            }
            entryCaptureRequested.clear()
            captureRows.clear()
            lastMotionRequestMs = Long.MIN_VALUE
            lastHeartbeatMs = Long.MIN_VALUE
            frameSequence = 0L
            screenshotRequestCount = 0
            screenshotSuccessCount = 0
            screenshotFailureCount = 0
            motionRequestCount = 0
            resultStableCaptureRequested = false
            pixelCopyInFlight = false
            pixelCopySuspendedReason = null
            firstFrameTimestampMs = null
            sceneClockStartTimestampMs = null
            orbFirstFrameTimestampMs = null
            gpuFirstFrameTimestampMs = null
            pixelCopyFirstFrameTimestampMs = null
            resultFirstFrameTimestampMs = null
            latestSceneElapsedMs = 0L
            latestSceneStage = null
            lastManualPixelCopySuppressionStage = null
            latestPlaybackMode = ScenePlaybackMode.AUTO
            latestStageProgress = 0f
            latestGeometryVersion = -1L
            lastRenderTraceTimestampMs = Long.MIN_VALUE
            lastRenderTraceStage = null
            lastSurfaceTraceStage = null
            latestWaitingReason = null
        }
        record(
            type = "SESSION_ATTEMPT_STARTED",
            fileName = "recognition_events.jsonl",
            payload = mapOf(
                "sessionIdHash" to sha256(identity.sessionId),
                "attemptIdHash" to sha256(identity.attemptId),
                "imageIdHash" to sha256(identity.imageId),
                "photoSha256" to identity.photoSha256,
                "sourceSha" to BuildConfig.SOURCE_REVISION,
                "buildType" to BuildConfig.BUILD_TYPE,
            ),
        )
        persistSummarySnapshot()
    }

    /** Ends an Attempt without manufacturing a Result state; terminal evidence is persisted first. */
    fun finishAttempt(
        terminalState: RecognitionAttemptTerminalState,
        reason: String? = null,
    ) {
        require(terminalState.isTerminal)
        val attempt = synchronized(lock) {
            val current = activeAttempt ?: return
            if (activeTerminalState != null) return
            activeTerminalState = terminalState
            current
        }
        record(
            "ATTEMPT_TERMINAL",
            "recognition_events.jsonl",
            mapOf(
                "terminalState" to terminalState.name,
                "terminalReason" to reason,
                "resultEvidenceStatus" to if (terminalState == RecognitionAttemptTerminalState.RESULT_STABLE) {
                    "RESULT_EVIDENCE_PENDING_PIXELCOPY"
                } else "NOT_REACHED",
                "sessionIdHash" to sha256(attempt.identity.sessionId),
                "attemptIdHash" to sha256(attempt.identity.attemptId),
            ),
        )
        val resultEvidenceAvailable = synchronized(lock) {
            captureRows.any {
                it.optString("captureType") == "RESULT_SCENE_STABLE" &&
                    it.optString("pixelCopyStatus") == "SUCCESS" &&
                    it.optString("evidenceBindingStatus") == "BOUND" &&
                    it.optString("relativePath").takeIf(String::isNotBlank)?.let(attempt::file)?.isFile == true
            }
        }
        store.finish(attempt, terminalState, reason, resultEvidenceAvailable)
        persistSummarySnapshot()
        maintenanceIo.execute { flush() }
    }

    fun recordLifecycleState(state: String) {
        record(
            "ACTIVITY_LIFECYCLE",
            "recognition_events.jsonl",
            mapOf(
                "state" to state,
                "terminalState" to synchronized(lock) { activeTerminalState?.name ?: "RUNNING" },
                "sourceDomain" to "SYSTEM",
            ),
        )
        record(
            "ACTIVITY_SURFACE_LIFECYCLE",
            "surface_trace.jsonl",
            mapOf(
                "activityState" to state,
                "surfaceState" to if (state == "ON_RESUME") "ATTACHED_EXPECTED" else "LIFECYCLE_NOT_RENDERING",
                "eglState" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                "contextState" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                "framebufferSize" to "NOT_RECORDED",
                "viewport" to "NOT_RECORDED",
                "swapStatus" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                "sourceDomain" to "SYSTEM",
            ),
        )
        persistSummarySnapshot()
    }

    fun flushAsync() { maintenanceIo.execute { flush() } }

    fun recordStageEvent(event: RecognitionSceneStageEvent) {
        val identity = activeIdentity ?: return
        if (identity.sessionId != event.sessionId || identity.attemptId != event.attemptId) return
        synchronized(lock) {
            latestSceneElapsedMs = maxOf(latestSceneElapsedMs, event.sceneElapsedMs)
            latestSceneStage = event.stage
            latestPlaybackMode = event.playbackMode
            latestStageProgress = event.stageProgress
            if (event.action == RecognitionSceneStageEventAction.BLOCKED) latestWaitingReason = event.waitingReason
            else if (event.action == RecognitionSceneStageEventAction.ENTERED) latestWaitingReason = null
            val previous = stageStatuses[event.stage] ?: RecognitionEvidenceStageStatus(event.stage, "NOT_REACHED")
            stageStatuses[event.stage] = when (event.action) {
                RecognitionSceneStageEventAction.ENTERED -> previous.copy(
                    status = "ENTERED",
                    enteredAtSceneMs = event.sceneElapsedMs,
                    lastWaitingReason = null,
                )
                RecognitionSceneStageEventAction.EXITED -> previous.copy(
                    status = "COMPLETED",
                    exitedAtSceneMs = event.sceneElapsedMs,
                    lastWaitingReason = null,
                )
                RecognitionSceneStageEventAction.BLOCKED -> previous.copy(
                    status = "BLOCKED",
                    lastWaitingReason = event.waitingReason,
                )
            }
        }
        val payload = event.toEvidencePayload()
        record("SCENE_STAGE_${event.action.name}", "recognition_events.jsonl", payload)
        record("SCENE_STAGE_${event.action.name}", "stage_timeline.jsonl", payload)
        persistSummarySnapshot()
    }

    fun recordStageTimeline(entries: List<RecognitionSceneStageTraceEntry>) {
        val pending = synchronized(lock) {
            val newEntries = entries.drop(recordedStageTimelineCount.coerceAtMost(entries.size))
            recordedStageTimelineCount = maxOf(recordedStageTimelineCount, entries.size)
            newEntries
        }
        pending.forEach { entry ->
            record(
                type = "SCENE_STAGE_TRACE_ENTRY",
                fileName = "stage_timeline.jsonl",
                payload = entry.toJsonObject().toMap(),
            )
        }
    }

    fun recordEnergyEvent(
        event: EnergyEvent,
        accepted: Boolean,
        taskEnergyMergedBeforeEvent: Boolean = false,
        sourceDomain: String? = null,
    ) {
        val merged = event.feedback == EnergyFeedback.RETURN && accepted && event.level >= .999f
        val taskState = RecognitionTaskFilamentState.fromEvent(
            event,
            taskEnergyMerged = taskEnergyMergedBeforeEvent || merged,
        )
        val payload = energyPayload(event) + mapOf(
            "controllerAccepted" to accepted,
            "sourceDomain" to sourceDomain,
            "producer" to event.sourceHost,
            "consumer" to event.targetHost,
            "accepted" to accepted,
            "rejected" to !accepted,
            "rejectionReason" to if (accepted) null else "SEE_ENERGY_EVENT_REJECTED_RECEIPT",
            "receiptStatus" to if (accepted) "ACCEPTED" else "REJECTED",
            "taskFilamentState" to taskState?.let {
                mapOf(
                    "phase" to it.phase.name,
                    "taskId" to it.taskId,
                    "taskPathId" to it.taskPathId,
                    "pStarPathId" to it.pStarPathId,
                    "sessionId" to it.sessionId,
                    "attemptId" to it.attemptId,
                    "imageId" to it.imageId,
                    "maskSha256" to it.maskSha256,
                    "maskVersion" to it.maskVersion,
                    "geometryVersion" to it.geometryVersion,
                    "pathGeometryVersion" to it.pathGeometryVersion,
                    "eventSequence" to it.eventSequence,
                    "releaseProgress" to it.releaseProgress,
                    "contactProgress" to it.contactProgress,
                    "returnProgress" to it.returnProgress,
                    "taskAlpha" to it.taskAlpha,
                )
            },
        )
        record(
            "ENERGY_EVENT_RECEIPT",
            "waterlight_paths.jsonl",
            payload + mapOf(
                "eventType" to event.feedback.name,
                "attemptId" to event.attemptId,
                "pathId" to event.pathId,
                "geometryVersion" to event.geometryVersion,
                "eventSequence" to event.eventSequence,
                "sourceDomain" to sourceDomain,
                "timestampMonotonicMs" to event.monotonicTimestampMs,
                "consumerReceipt" to if (accepted) "ACCEPTED" else "REJECTED",
            ),
        )
        record("TASK_WATERLIGHT_EVENT", "waterlight_paths.jsonl", payload)
        if (merged) {
            record(
                "TASK_ENERGY_MERGED",
                "recognition_events.jsonl",
                payload + mapOf(
                    "mergeEvent" to "TASK_ENERGY_MERGED",
                    "taskPathId" to event.pathId,
                    "pStarPathId" to event.pathId,
                    "maskSha256" to event.pathMaskSha256,
                    "geometryVersion" to event.pathGeometryVersion,
                    "eventSequence" to event.eventSequence,
                ),
            )
        }
        if (event.feedback == EnergyFeedback.TRANSLATE && accepted && taskEnergyMergedBeforeEvent) {
            record(
                "ORB_DISSOLVE",
                "recognition_events.jsonl",
                payload + mapOf(
                    "dissolveEvent" to "ORB_DISSOLVE",
                    "sourceEvent" to "ORB_TO_GLYPH_ACCEPTED",
                    "taskEnergyMerged" to taskEnergyMergedBeforeEvent,
                ),
            )
        }
    }

    fun recordEnergyEventRejection(
        frame: RecognitionSceneFrame,
        event: EnergyEvent,
        reason: EnergyEventRejectionReason,
        modelGates: RecognitionSceneModelGates,
    ) {
        record(
            type = "ENERGY_EVENT_REJECTED",
            fileName = "waterlight_paths.jsonl",
            payload = energyPayload(event) + mapOf(
                "stage" to frame.stage.name,
                "stageProgress" to frame.stageProgress,
                "feedback" to event.feedback.name,
                "pathId" to event.pathId,
                "geometryVersion" to event.geometryVersion,
                "maskSha256" to event.pathMaskSha256,
                "eventSequence" to event.eventSequence,
                "sessionIdHash" to sha256(event.sessionId),
                "attemptIdHash" to sha256(event.attemptId),
                "imageIdHash" to sha256(event.imageId),
                "modelGates" to mapOf(
                    "detector" to modelGates.detector.name,
                    "classifier" to modelGates.classifier.name,
                    "slimSam" to modelGates.slimSam.name,
                    "mask" to modelGates.mask.name,
                    "geometry" to modelGates.geometry.name,
                    "waitingReason" to modelGates.waitingReason,
                ),
                "rejectionReason" to reason.name,
                "sourceDomain" to frame.playbackMode.name,
            ),
        )
    }

    fun recordResultHandoff(event: RecognitionResultHandoffEvent, accepted: Boolean, reason: String?) {
        record(
            "RESULT_HANDOFF_${event.substage.name}",
            "result_handoff.jsonl",
            mapOf(
                "sessionIdHash" to sha256(event.identity.sessionId),
                "attemptIdHash" to sha256(event.identity.attemptId),
                "imageIdHash" to sha256(event.identity.imageId),
                "stage" to event.stage.name,
                "substage" to event.substage.name,
                "eventSequence" to event.eventSequence,
                "monotonicTimestampMs" to event.monotonicTimestampMs,
                "geometryVersion" to event.geometryVersion,
                "pathId" to event.pathId,
                "pStarNormalizedSource" to event.pStarNormalizedSource?.let { listOf(it.x, it.y) },
                "maskSha256" to event.maskSha256,
                "maskVersion" to event.maskVersion,
                "pathGeometryVersion" to event.pathGeometryVersion,
                "pathSegments" to event.pathSegments.map { segment ->
                    mapOf(
                        "start" to listOf(segment.start.x, segment.start.y),
                        "control1" to listOf(segment.control1.x, segment.control1.y),
                        "control2" to listOf(segment.control2.x, segment.control2.y),
                        "end" to listOf(segment.end.x, segment.end.y),
                        "coordinateSpace" to segment.coordinateSpace,
                    )
                },
                "source" to event.source,
                "target" to event.target,
                "producer" to event.source,
                "consumer" to event.target,
                "feedback" to event.feedback,
                "progress" to event.progress,
                "renderStatus" to event.renderStatus,
                "accepted" to accepted,
                "rejected" to !accepted,
                "consumerReceipt" to if (accepted) "ACCEPTED" else "REJECTED",
                "rejectionReason" to reason,
                "resultIdentity" to event.resultIdentity,
                "playbackMode" to event.playbackMode.name,
            ),
        )
        persistSummarySnapshot()
    }

    fun recordModelState(
        stage: RecognitionSceneStage,
        sceneElapsedMs: Long,
        detectorStatus: String,
        classifierStatus: String,
        slimSamStatus: String,
        maskStatus: String,
        waitingReason: String?,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        record(
            "MODEL_RUNTIME_STATE",
            "model_inference.jsonl",
            mapOf(
                "stage" to stage.name,
                "sceneElapsedMs" to sceneElapsedMs,
                "detectorStatus" to detectorStatus,
                "classifierStatus" to classifierStatus,
                "slimSamStatus" to slimSamStatus,
                "maskStatus" to maskStatus,
                "waitingReason" to waitingReason,
            ) + extra,
            RecognitionEvidencePriority.SAMPLE,
        )
        if (waitingReason != null) heartbeat(stage, sceneElapsedMs, waitingReason)
    }

    fun recordMaskRuntime(
        status: String,
        trusted: Boolean,
        maskSha256: String?,
        maskVersion: Long?,
        geometryVersion: Long?,
        fallbackReason: String?,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        record(
            "MASK_RUNTIME",
            "mask_runtime.jsonl",
            mapOf(
                "status" to status,
                "trusted" to trusted,
                "maskSha256" to maskSha256,
                "maskVersion" to maskVersion,
                "geometryVersion" to geometryVersion,
                "fallback" to if (trusted) "NONE" else "BBOX_ONLY",
                "fallbackReason" to fallbackReason,
            ) + extra,
        )
        persistSummarySnapshot()
    }

    fun recordOrbRuntime(fileName: String, type: String, payload: Map<String, Any?>) {
        record(type, fileName, payload, RecognitionEvidencePriority.SAMPLE)
    }

    fun recordHeartbeat(frame: RecognitionSceneFrame) {
        val waiting = frame.modelGates.waitingReason ?: return
        synchronized(lock) { latestWaitingReason = waiting }
        heartbeat(frame.stage, frame.sceneElapsedMs, waiting)
    }

    /** Called from drawWithContent after the actual Compose frame has been submitted. */
    fun onFramePresented(
        window: Window?,
        frame: RecognitionSceneFrame,
        latestFrame: () -> RecognitionSceneFrame?,
        resultSceneStable: Boolean = false,
        allowPixelCopyCapture: Boolean = false,
    ) {
        if (!allowPixelCopyCapture) return
        val isCurrentFrame = synchronized(lock) {
            activeIdentity?.matches(frame.identity) == true && pixelCopySuspendedReason == null
        }
        if (!isCurrentFrame) return
        if (frame.playbackMode != ScenePlaybackMode.AUTO) {
            val shouldRecordSuppression = synchronized(lock) {
                (lastManualPixelCopySuppressionStage != frame.stage).also {
                    if (it) lastManualPixelCopySuppressionStage = frame.stage
                }
            }
            if (shouldRecordSuppression) record(
                "PIXELCOPY_SUPPRESSED_NON_AUTO_FRAME",
                "recognition_events.jsonl",
                mapOf(
                    "sourceDomain" to frame.playbackMode.name,
                    "reason" to "AUTO_PIXELCOPY_CAPTURE_REQUIRES_AUTO_FRAME",
                    "stage" to frame.stage.name,
                    "sceneClockMs" to frame.sceneElapsedMs,
                    "eventSequence" to frame.eventSequence,
                    "geometryVersion" to frame.geometryVersion,
                ),
            )
            return
        }
        val firstFrameTimestamp = synchronized(lock) {
            latestSceneElapsedMs = maxOf(latestSceneElapsedMs, frame.sceneElapsedMs)
            latestSceneStage = frame.stage
            latestPlaybackMode = frame.playbackMode
            latestStageProgress = frame.stageProgress
            latestGeometryVersion = frame.geometryVersion
            if (frame.stage == RecognitionSceneStage.PHOTO_STABLE && firstFrameTimestampMs == null) {
                val presented = SystemClock.uptimeMillis()
                firstFrameTimestampMs = presented
                sceneClockStartTimestampMs = (frame.monotonicTimestampMs - frame.sceneElapsedMs).coerceAtLeast(0L)
                presented
            } else null
        }
        if (firstFrameTimestamp != null) {
            record(
                "PHOTO_FIRST_FRAME_PRESENTED",
                "recognition_events.jsonl",
                mapOf(
                    "firstFrameTimestampMs" to firstFrameTimestamp,
                    "sceneClockStartTimestampMs" to sceneClockStartTimestampMs,
                    "sceneElapsedMs" to frame.sceneElapsedMs,
                    "eventSequence" to frame.eventSequence,
                    "geometryVersion" to frame.geometryVersion,
                ),
            )
            persistSummarySnapshot()
        }
        if (frame.stage == RecognitionSceneStage.ORB_WAKE) {
            val orbFirstFrameTimestamp = synchronized(lock) {
                if (orbFirstFrameTimestampMs == null) {
                    orbFirstFrameTimestampMs = SystemClock.uptimeMillis()
                    orbFirstFrameTimestampMs
                } else null
            }
            if (orbFirstFrameTimestamp != null) record(
                "ORB_FIRST_FRAME_PRESENTED",
                "recognition_events.jsonl",
                mapOf("timestampMs" to orbFirstFrameTimestamp, "eventSequence" to frame.eventSequence),
            )
        }
        val renderTraceDue = synchronized(lock) {
            val now = frame.monotonicTimestampMs
            val due = frame.stage != lastRenderTraceStage || lastRenderTraceTimestampMs == Long.MIN_VALUE ||
                now - lastRenderTraceTimestampMs >= RENDER_TRACE_INTERVAL_MS
            if (due) {
                lastRenderTraceTimestampMs = now
                lastRenderTraceStage = frame.stage
            }
            due
        }
        val surfaceTraceDue = synchronized(lock) {
            val due = frame.stage != lastSurfaceTraceStage
            if (due) lastSurfaceTraceStage = frame.stage
            due
        }
        if (surfaceTraceDue) {
            val decor = window?.decorView
            val surfaceStatus = when {
                decor == null || !decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0 -> "SURFACE_NOT_READY"
                !decor.isHardwareAccelerated -> "GPU_RENDER_SUPPRESSED"
                else -> "GPU_RENDER_STARTED"
            }
            record(
                "SURFACE_RENDER_STATE",
                "surface_trace.jsonl",
                mapOf(
                    "status" to surfaceStatus,
                    "evidenceLevel" to "STATE_ONLY",
                    "renderer" to "ANDROID_COMPOSE_HWUI",
                    "glVersion" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                    "eglState" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                    "contextState" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                    "framebufferWidthPx" to decor?.width,
                    "framebufferHeightPx" to decor?.height,
                    "viewport" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                    "drawPass" to "COMPOSE_DRAW_WITH_CONTENT",
                    "compositePass" to "ANDROID_WINDOW_COMPOSITION",
                    "swapStatus" to "NOT_EXPOSED_BY_COMPOSE_HOST",
                    "stage" to frame.stage.name,
                    "sceneClockMs" to frame.sceneElapsedMs,
                    "eventSequence" to frame.eventSequence,
                ),
            )
        }
        if (renderTraceDue) record(
            "RENDER_FRAME_SUBMITTED",
            "gpu_trace.jsonl",
            mapOf(
                "evidenceLevel" to "RUNTIME_TRACE_CONFIRMED",
                "renderOwner" to if (frame.resultHandoffSubstage == null) "PROCESSING_HOST" else "RESULT_HOST",
                "stage" to frame.stage.name,
                "stageProgress" to frame.stageProgress,
                "sceneClockMs" to frame.sceneElapsedMs,
                "eventSequence" to frame.eventSequence,
                "geometryVersion" to frame.geometryVersion,
                "monotonicTimestampMs" to frame.monotonicTimestampMs,
                "drawWithContentSubmitted" to true,
                "confirmationPath" to "PIXELCOPY",
            ),
            RecognitionEvidencePriority.SAMPLE,
        )
        if (renderTraceDue) {
            val gpuFirstFrameTimestamp = synchronized(lock) {
                if (gpuFirstFrameTimestampMs == null) {
                    gpuFirstFrameTimestampMs = SystemClock.uptimeMillis()
                    gpuFirstFrameTimestampMs
                } else null
            }
            if (gpuFirstFrameTimestamp != null) record(
                "GPU_FIRST_FRAME_SUBMITTED",
                "gpu_trace.jsonl",
                mapOf(
                    "timestampMs" to gpuFirstFrameTimestamp,
                    "evidenceLevel" to "RUNTIME_TRACE_CONFIRMED",
                    "gpuDisplayConfirmed" to false,
                    "confirmationPath" to "PIXELCOPY",
                    "eventSequence" to frame.eventSequence,
                    "stage" to frame.stage.name,
                ),
            )
        }
        val stageName = frame.stage.name
        val enterRequest = synchronized(lock) { entryCaptureRequested.add(stageName) }
        if (enterRequest) requestCapture(window, frame, latestFrame, "STAGE_ENTRY", "stage_entry", priority = "KEY")
        if (resultSceneStable && synchronized(lock) { !resultStableCaptureRequested.also { if (!it) resultStableCaptureRequested = true } }) {
            requestCapture(window, frame, latestFrame, "RESULT_SCENE_STABLE", "stage_entry", priority = "KEY")
        }
        // Motion screenshots are reserved for visible AUTO progress. Model waits are
        // represented by the one-second heartbeat and must not trigger high-frequency capture.
        val visibleMotion = RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
            paused = frame.paused,
            runState = frame.modelGates.runState,
            waitingReason = frame.modelGates.waitingReason,
            stage = frame.stage,
        )
        val now = frame.monotonicTimestampMs
        if (visibleMotion && now >= 0L) {
            val shouldRequest = synchronized(lock) {
                if (motionRequestCount >= MAX_MOTION_SAMPLES ||
                    (lastMotionRequestMs != Long.MIN_VALUE && now - lastMotionRequestMs < MOTION_INTERVAL_MS)
                ) false else {
                    lastMotionRequestMs = now
                    motionRequestCount++
                    true
                }
            }
            if (shouldRequest) requestCapture(window, frame, latestFrame, "MOTION_SAMPLE", "motion_samples", priority = "SAMPLE")
        }
    }

    fun recordResultFirstPresented(sceneElapsedMs: Long, geometryVersion: Long, resultIdentity: String?) {
        val timestamp = SystemClock.uptimeMillis()
        synchronized(lock) { if (resultFirstFrameTimestampMs == null) resultFirstFrameTimestampMs = timestamp }
        record(
            "RESULT_FIRST_PRESENTED",
            "result_handoff.jsonl",
            mapOf(
                "sceneElapsedMs" to sceneElapsedMs,
                "geometryVersion" to geometryVersion,
                "resultIdentity" to resultIdentity,
                "timestampMs" to timestamp,
            ),
        )
        persistSummarySnapshot()
    }

    fun activeAttemptDirectory(): File? = synchronized(lock) { activeAttempt?.directory }
    fun exportIdentity(): RecognitionEvidenceIdentity? = synchronized(lock) { activeIdentity }

    fun flush(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        val clampedTimeoutMs = timeoutMs.coerceIn(1L, MAX_FLUSH_TIMEOUT_MS)
        val deadline = clockMs() + clampedTimeoutMs
        val pixelCopyDeadline = clockMs() + minOf(clampedTimeoutMs, MAX_PIXELCOPY_FLUSH_WAIT_MS)
        val pixelCopyDrained = captureLock.withLock {
            var remaining = pixelCopyDeadline - clockMs()
            while (pendingCaptures > 0 && remaining > 0L) {
                capturesIdle.awaitNanos(TimeUnit.MILLISECONDS.toNanos(remaining))
                remaining = pixelCopyDeadline - clockMs()
            }
            pendingCaptures == 0
        }
        if (!pixelCopyDrained) {
            eventSink?.markIncomplete("PIXELCOPY_FLUSH_TIMEOUT")
        }
        persistSummarySnapshot()
        val pixelIoWaitMs = (deadline - clockMs()).coerceAtLeast(DEFAULT_TAIL_FLUSH_WAIT_MS)
            .coerceAtMost(MAX_PIXEL_WRITE_FLUSH_WAIT_MS)
        val pixelWritesDrained = runCatching {
            pixelIo.submit {}.get(pixelIoWaitMs, TimeUnit.MILLISECONDS)
            true
        }.getOrElse { failure ->
            val reason = if (failure is java.util.concurrent.TimeoutException) {
                "PIXEL_WRITE_FLUSH_TIMEOUT"
            } else {
                "PIXEL_WRITE_FLUSH_FAILED:${failure.javaClass.simpleName}"
            }
            eventSink?.markIncomplete(reason)
            false
        }
        val sinkWaitMs = (deadline - clockMs()).coerceAtLeast(DEFAULT_TAIL_FLUSH_WAIT_MS)
            .coerceAtMost(RecognitionEvidenceEventSink.DEFAULT_FLUSH_TIMEOUT_MS)
        val eventsDrained = eventSink?.flush(sinkWaitMs) ?: true
        persistSummarySnapshot()
        val finalSummaryDrained = runCatching {
            pixelIo.submit {}.get(sinkWaitMs, TimeUnit.MILLISECONDS)
            true
        }.getOrElse { failure ->
            eventSink?.markIncomplete("SUMMARY_FLUSH_FAILED:${failure.javaClass.simpleName}")
            false
        }
        return pixelCopyDrained && pixelWritesDrained && eventsDrained && finalSummaryDrained
    }

    /**
     * Produces a frozen, isolated directory snapshot of logs, frames, and summary metadata.
     * Pending PixelCopy / EventSink timeouts are degraded cleanly into [degradedReasons] without failing.
     */
    internal fun createImmutableExportSnapshot(
        stagingDirectory: File,
        flushTimeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS,
        recoveredAttemptIdHash: String? = null,
    ): RecognitionEvidenceExportSnapshot? {
        var target = synchronized(lock) {
            when {
                recoveredAttemptIdHash != null -> recoveredAttempts
                    .firstOrNull { it.attemptIdHash == recoveredAttemptIdHash }
                    ?.let { RecognitionEvidenceExportTarget(it.directory, null, null, it) }
                activeAttempt != null -> RecognitionEvidenceExportTarget(
                    directory = activeAttempt!!.directory,
                    identity = activeIdentity,
                    sink = eventSink,
                    recovered = null,
                )
                else -> recoveredAttempts.firstOrNull()?.let {
                    RecognitionEvidenceExportTarget(it.directory, null, null, it)
                }
            }
        } ?: return null
        val degradedReasons = mutableListOf<String>()
        val zipExportRequestEvidenceSequence = recordArchiveLifecycleEvent(
            target = target,
            eventType = "EVIDENCE_ZIP_EXPORT_REQUESTED",
            payload = mapOf("exportType" to "RECOGNITION_EVIDENCE_ZIP"),
        )
        if (zipExportRequestEvidenceSequence == null) degradedReasons += "ZIP_EXPORT_REQUEST_EVENT_NOT_PERSISTED"
        if (target.recovered == null) {
            flush(flushTimeoutMs)
            target = synchronized(lock) {
                RecognitionEvidenceExportTarget(
                    directory = activeAttempt?.directory ?: target.directory,
                    identity = activeIdentity,
                    sink = eventSink,
                    recovered = null,
                )
            }
        }

        val targetLogs = File(stagingDirectory, "logs").apply { mkdirs() }
        val logSnapshot = target.sink?.snapshotLogsTo(
            targetLogsDir = targetLogs,
            flushTimeoutMs = minOf(flushTimeoutMs.coerceAtLeast(1L), RecognitionEvidenceEventSink.DEFAULT_FLUSH_TIMEOUT_MS),
        )
        if (logSnapshot?.sizeCapReached == true) {
            degradedReasons += "LOG_SNAPSHOT_SIZE_CAP_REACHED:${logSnapshot.truncatedFiles.joinToString(",")}"
        }
        if (target.sink == null) {
            val copiedLogs = copyBoundedTree(
                File(target.directory, "logs"),
                targetLogs,
                RecognitionEvidenceEventSink.MAX_SNAPSHOT_LOG_BYTES,
            )
            if (copiedLogs) degradedReasons += "RECOVERED_LOG_SNAPSHOT_TRUNCATED"
        }

        return synchronized(pixelFileLock) {
            val identity = target.identity
            val metadata = runCatching {
                JSONObject(File(target.directory, "attempt.json").readText(Charsets.UTF_8))
            }.getOrDefault(JSONObject())
            val persistedSummary = runCatching {
                JSONObject(File(target.directory, "session/session_summary.json").readText(Charsets.UTF_8))
            }.getOrNull()
            val summary = if (target.recovered == null) {
                synchronized(lock) { summaryJson() }
            } else {
                (persistedSummary ?: JSONObject()
                    .put("evidenceSchemaVersion", metadata.optInt("evidenceSchemaVersion", RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION))
                    .put("runtimeContractVersion", metadata.optString("runtimeContractVersion", RecognitionEvidenceSchema.RUNTIME_CONTRACT_VERSION))
                    .put("diagnosticBuildVersion", metadata.optString("diagnosticBuildVersion", RecognitionEvidenceSchema.DIAGNOSTIC_BUILD_VERSION))
                    .put("sessionIdHash", metadata.optString("sessionIdHash", "NOT_RECORDED"))
                    .put("attemptIdHash", metadata.optString("attemptIdHash", "NOT_RECORDED"))
                    .put("photoSha256", metadata.optString("photoSha256", "NOT_RECORDED"))
                    .put("stageStatuses", JSONArray())
                    .put("screenshotRequestCount", 0)
                    .put("screenshotSuccessCount", 0)
                    .put("screenshotFailureCount", 0)
                    .put("incompleteReasons", JSONArray().put("RECOVERED_SUMMARY_NOT_PERSISTED")))
                    .put("terminalState", metadata.optString("terminalState", "UNKNOWN_TERMINATION"))
                    .put("recovered", true)
                    .put("resultEvidenceStatus", "NOT_REACHED")
                    .put("resultEvidenceMissingReason", "RECOVERED_ATTEMPT_HAS_NO_RESULT_HANDOFF")
            }
            val captureIndex = if (target.recovered == null) captureIndexJson()
                else captureIndexFromDisk(File(target.directory, "frames/pixelcopy_manifest.jsonl"))
            copyAttemptMetadata(target.directory, stagingDirectory)
            File(stagingDirectory, "session/session_summary.json").apply {
                parentFile?.mkdirs()
                writeText(summary.toString(2), Charsets.UTF_8)
            }
            File(stagingDirectory, "session/stage_timeline.json").apply {
                parentFile?.mkdirs()
                writeText(
                    JSONObject()
                        .put("source", "RecognitionSceneAnimationController")
                        .put("stages", summary.optJSONArray("stageStatuses") ?: JSONArray())
                        .toString(2),
                    Charsets.UTF_8,
                )
            }
            File(stagingDirectory, "frames/frame_index.json").apply {
                parentFile?.mkdirs()
                writeText(captureIndex.toString(2), Charsets.UTF_8)
            }
            if (target.recovered != null) degradedReasons += "RECOVERED_ATTEMPT:${target.recovered.terminalState}"
            val framesSourceRoot = File(target.directory, "frames")
            var copiedFrameBytes = 0L
            var frameCapHit = false
            val frameFiles = framesSourceRoot.walkTopDown().filter(File::isFile).sortedBy(File::getPath).toList()
            for (sourceFrame in frameFiles) {
                val rel = sourceFrame.relativeTo(target.directory).invariantSeparatorsPath
                val size = sourceFrame.length()
                if (copiedFrameBytes + size > MAX_EXPORT_FRAMES_BYTES) {
                    frameCapHit = true
                    continue
                }
                val destFrame = File(stagingDirectory, rel)
                destFrame.parentFile?.mkdirs()
                sourceFrame.copyTo(destFrame, overwrite = true)
                copiedFrameBytes += size
            }
            if (frameCapHit) {
                target.sink?.markIncomplete("FRAME_SNAPSHOT_SIZE_CAP_REACHED")
                degradedReasons += "FRAME_SNAPSHOT_SIZE_CAP_REACHED"
            }
            val finalDiagnostics = target.sink?.diagnostics() ?: RecognitionEvidenceQueueDiagnostics(
                droppedCriticalEvents = summary.optLong("droppedCriticalEventCount"),
                droppedSampleEvents = summary.optLong("droppedSampleEventCount"),
                writeFailures = summary.optLong("eventWriteFailureCount"),
                incompleteReasons = summary.optJSONArray("incompleteReasons")?.let { rows ->
                    (0 until rows.length()).mapNotNull { rows.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty(),
                eventCount = summary.optLong("eventCount"),
            )
            val sessionIdHash = identity?.let { sha256(it.sessionId) }
                ?: target.recovered?.sessionIdHash
                ?: metadata.optString("sessionIdHash").takeIf(String::isNotBlank)
            val attemptIdHash = identity?.let { sha256(it.attemptId) }
                ?: target.recovered?.attemptIdHash
                ?: metadata.optString("attemptIdHash").takeIf(String::isNotBlank)
            val photoSha256 = identity?.photoSha256
                ?: target.recovered?.photoSha256
                ?: metadata.optString("photoSha256").takeIf(String::isNotBlank)
            RecognitionEvidenceExportSnapshot(
                attemptDirectory = target.directory,
                identity = identity,
                sessionIdHash = sessionIdHash,
                attemptIdHash = attemptIdHash,
                photoSha256 = photoSha256,
                recoveredAttempt = target.recovered != null,
                summary = summary,
                captureIndex = captureIndex,
                queueDiagnostics = finalDiagnostics,
                degradedReasons = (degradedReasons + finalDiagnostics.incompleteReasons).distinct(),
                zipExportRequestEvidenceSequence = zipExportRequestEvidenceSequence,
            )
        }
    }

    internal fun recordArchiveExportOutcome(
        snapshot: RecognitionEvidenceExportSnapshot,
        succeeded: Boolean,
        details: Map<String, Any?>,
    ): Long? = recordArchiveLifecycleEvent(
        target = RecognitionEvidenceExportTarget(
            directory = snapshot.attemptDirectory,
            identity = snapshot.identity,
            sink = null,
            recovered = null,
        ),
        eventType = if (succeeded) "EVIDENCE_ZIP_EXPORT_COMPLETED" else "EVIDENCE_ZIP_EXPORT_FAILED",
        payload = details,
    )

    private fun recordArchiveLifecycleEvent(
        target: RecognitionEvidenceExportTarget,
        eventType: String,
        payload: Map<String, Any?>,
    ): Long? {
        synchronized(lock) {
            if (activeAttempt?.directory == target.directory && activeIdentity != null) {
                return record(
                    eventType,
                    "recognition_events.jsonl",
                    payload + ("sourceDomain" to "SYSTEM"),
                )
            }
        }

        return runCatching {
            val metadata = JSONObject(File(target.directory, "attempt.json").readText(Charsets.UTF_8))
            val identity = JSONObject(File(target.directory, "session/session_identity.json").readText(Charsets.UTF_8))
            val sessionHash = identity.optString("sessionIdHash", metadata.optString("sessionIdHash", "NOT_RECORDED"))
            val attemptHash = identity.optString("attemptIdHash", metadata.optString("attemptIdHash", "NOT_RECORDED"))
            val photoHash = identity.optString("imageIdHash", metadata.optString("imageIdHash", "NOT_RECORDED"))
            val sceneKey = "${sessionHash}:${attemptHash}:$photoHash"
            val nextSequence = lastPersistedRecognitionEvidenceSequence(target.directory)
            val recoveredSink = RecognitionEvidenceEventSink(
                attemptDirectory = target.directory,
                clockMs = clockMs,
                initialSequence = nextSequence,
            )
            try {
                val sequence = recoveredSink.recordSequenced(
                    RecognitionEvidenceEvent(
                        fileName = "recognition_events.jsonl",
                        type = eventType,
                        payload = payload + mapOf(
                            "sceneKey" to sceneKey,
                            "sessionIdHash" to sessionHash,
                            "attemptIdHash" to attemptHash,
                            "photoIdentityHash" to photoHash,
                            "photoSha256" to identity.optString("photoSha256", metadata.optString("photoSha256", "NOT_RECORDED")),
                            "sourceDomain" to "SYSTEM",
                            "identityMatch" to listOf(sessionHash, attemptHash, photoHash).all { it.length == 64 },
                            "runtimeEventSequence" to JSONObject.NULL,
                            "stage" to "NOT_RECORDED",
                            "sceneClockMs" to JSONObject.NULL,
                            "geometryVersion" to JSONObject.NULL,
                        ),
                    ),
                )
                check(recoveredSink.flush()) { "RECOVERED_EXPORT_EVENT_FLUSH_FAILED" }
                requireNotNull(sequence)
            } finally {
                recoveredSink.dispose()
            }
        }.getOrNull()
    }

    private fun copyAttemptMetadata(sourceAttempt: File, stagingDirectory: File) {
        listOf(
            "attempt.json",
            "crash_report.json",
            "recovery_report.json",
            "session/session.json",
            "session/session_identity.json",
            "session/session_summary.json",
            "session/stage_timeline.json",
        ).forEach { relative ->
            val source = File(sourceAttempt, relative)
            if (!source.isFile) return@forEach
            val destination = File(stagingDirectory, relative)
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
        }
    }

    private fun captureIndexFromDisk(manifestFile: File): JSONArray = JSONArray().also { rows ->
        if (!manifestFile.isFile) return@also
        manifestFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEach { line -> runCatching { rows.put(JSONObject(line)) } }
        }
    }

    /** Copies persisted logs with a total byte cap; returns true when files were truncated. */
    private fun copyBoundedTree(sourceRoot: File, targetRoot: File, maxBytes: Long): Boolean {
        if (!sourceRoot.isDirectory) return false
        var remaining = maxBytes
        var truncated = false
        sourceRoot.walkTopDown().filter(File::isFile).sortedBy(File::getPath).forEach { source ->
            val target = File(targetRoot, source.relativeTo(sourceRoot).invariantSeparatorsPath)
            target.parentFile?.mkdirs()
            if (remaining <= 0L) {
                target.writeText("", Charsets.UTF_8)
                truncated = true
            } else if (source.length() <= remaining) {
                source.copyTo(target, overwrite = true)
                remaining -= source.length()
            } else {
                copyTail(source, target, remaining)
                remaining -= target.length()
                truncated = true
            }
        }
        return truncated
    }

    private fun copyTail(source: File, target: File, maxBytes: Long) {
        java.io.RandomAccessFile(source, "r").use { input ->
            input.seek((input.length() - maxBytes).coerceAtLeast(0L))
            if (input.filePointer > 0L) while (input.filePointer < input.length() && input.read() != '\n'.code) { }
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    fun diagnostics(): RecognitionEvidenceQueueDiagnostics = eventSink?.diagnostics()
        ?: RecognitionEvidenceQueueDiagnostics(0, 0, 0, listOf("ATTEMPT_NOT_STARTED"), 0)

    fun summaryJson(): JSONObject = synchronized(lock) {
        val sinkStatus = eventSink?.diagnostics()
        val resultEvidenceStatus = resultEvidenceStatusSnapshotLocked()
        val stageArray = JSONArray()
        stageStatuses.values.forEach { status ->
            stageArray.put(
                JSONObject()
                    .put("stage", status.stage.name)
                    .put("status", status.status)
                    .put("enteredAtSceneMs", status.enteredAtSceneMs)
                    .put("exitedAtSceneMs", status.exitedAtSceneMs)
                    .put("lastWaitingReason", status.lastWaitingReason),
            )
        }
        JSONObject()
            .put("schemaVersion", RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION)
            .put("evidenceSchemaVersion", RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION)
            .put("runtimeContractVersion", RecognitionEvidenceSchema.RUNTIME_CONTRACT_VERSION)
            .put("diagnosticBuildVersion", RecognitionEvidenceSchema.DIAGNOSTIC_BUILD_VERSION)
            .put("sessionIdHash", activeIdentity?.let { sha256(it.sessionId) })
            .put("attemptIdHash", activeIdentity?.let { sha256(it.attemptId) })
            .put("imageIdHash", activeIdentity?.let { sha256(it.imageId) })
            .put("photoSha256", activeIdentity?.photoSha256)
            .put("photoWidthPx", activeIdentity?.photoWidthPx ?: 0)
            .put("photoHeightPx", activeIdentity?.photoHeightPx ?: 0)
            .put("orientationDegrees", activeIdentity?.orientationDegrees ?: 0)
            .put("terminalState", activeTerminalState?.name ?: "RUNNING")
            .put("waitingReason", latestWaitingReason ?: JSONObject.NULL)
            .put("resultEvidenceStatus", resultEvidenceStatus.status)
            .put("resultEvidenceMissingReason", resultEvidenceStatus.missingReason ?: JSONObject.NULL)
            .put("firstFrameTimestampMs", firstFrameTimestampMs ?: -1L)
            .put("sceneClockStartTimestampMs", sceneClockStartTimestampMs ?: -1L)
            .put("orbFirstFrameTimestampMs", orbFirstFrameTimestampMs ?: -1L)
            .put("gpuFirstFrameTimestampMs", gpuFirstFrameTimestampMs ?: -1L)
            .put("pixelCopyFirstFrameTimestampMs", pixelCopyFirstFrameTimestampMs ?: -1L)
            .put("resultFirstFrameTimestampMs", resultFirstFrameTimestampMs ?: -1L)
            .put("recognitionDurationMs", latestSceneElapsedMs)
            .put("stageStatuses", stageArray)
            .put("completedStageCount", stageStatuses.values.count { it.status == "COMPLETED" })
            .put("stageCount", RecognitionSceneStage.entries.size)
            .put("screenshotRequestCount", screenshotRequestCount)
            .put("screenshotSuccessCount", screenshotSuccessCount)
            .put("screenshotFailureCount", screenshotFailureCount)
            .put("eventCount", sinkStatus?.eventCount ?: 0L)
            .put("droppedCriticalEventCount", sinkStatus?.droppedCriticalEvents ?: 0L)
            .put("droppedSampleEventCount", sinkStatus?.droppedSampleEvents ?: 0L)
            .put("eventWriteFailureCount", sinkStatus?.writeFailures ?: 0L)
            .put("incompleteReasons", JSONArray(sinkStatus?.incompleteReasons.orEmpty()))
    }

    private fun resultEvidenceStatusSnapshotLocked(): RecognitionEvidenceResultStatusSnapshot =
        RecognitionEvidenceResultStatusPolicy.fromCaptureSnapshots(
            captureRows.map { row ->
                RecognitionEvidenceCaptureSnapshot(
                    stage = row.optString("stage"),
                    captureType = row.optString("captureType"),
                    pixelCopyStatus = row.optString("pixelCopyStatus"),
                    evidenceBindingStatus = row.optString("evidenceBindingStatus"),
                )
            },
        )

    fun captureIndexJson(): JSONArray = synchronized(lock) { JSONArray(captureRows.map { JSONObject(it.toString()) }) }

    private fun heartbeat(stage: RecognitionSceneStage, sceneElapsedMs: Long, waitingReason: String) {
        val now = SystemClock.uptimeMillis()
        val write = synchronized(lock) {
            if (lastHeartbeatMs != Long.MIN_VALUE && now - lastHeartbeatMs < WAIT_HEARTBEAT_INTERVAL_MS) false
            else { lastHeartbeatMs = now; true }
        }
        if (write) record(
            "MODEL_WAIT_HEARTBEAT",
            "recognition_events.jsonl",
            mapOf(
                "stage" to stage.name,
                "sceneElapsedMs" to sceneElapsedMs,
                "waitingReason" to waitingReason,
            ),
            RecognitionEvidencePriority.SAMPLE,
        )
        if (write) persistSummarySnapshot()
    }

    private fun requestCapture(
        window: Window?,
        frame: RecognitionSceneFrame,
        latestFrame: () -> RecognitionSceneFrame?,
        captureType: String,
        directoryName: String,
        priority: String,
    ) {
        val setup = synchronized(lock) {
            if (activeIdentity?.matches(frame.identity) != true) null
            else activeAttempt?.let { it to activeGeneration }
        } ?: return
        val attempt = setup.first
        val generation = setup.second
        val captureId = UUID.randomUUID().toString()
        val drawFrameSequence: Long
        val requestTimestamp = SystemClock.uptimeMillis()
        synchronized(lock) {
            if (generation != activeGeneration) return
            if (pixelCopySuspendedReason != null || pixelCopyInFlight) {
                if (captureType == "STAGE_ENTRY") entryCaptureRequested.remove(frame.stage.name)
                if (captureType == "RESULT_SCENE_STABLE") resultStableCaptureRequested = false
                return
            }
            if (screenshotRequestCount >= MAX_TOTAL_SCREENSHOTS) {
                screenshotFailureCount++
                eventSink?.markIncomplete("CAPTURE_LIMIT_REACHED")
                record(
                    "CAPTURE_LIMIT_REACHED",
                    "recognition_events.jsonl",
                    mapOf("captureId" to captureId, "stage" to frame.stage.name),
                    generation = generation,
                )
                return
            }
            pixelCopyInFlight = true
            screenshotRequestCount++
            drawFrameSequence = ++frameSequence
        }
        captureLock.withLock { pendingCaptures++ }
        val sessionHash = sha256(frame.identity.sessionId)
        val attemptHash = sha256(frame.identity.attemptId)
        val imageHash = sha256(frame.identity.imageId)
        val requestEvidenceSequence = record(
            "PIXELCOPY_REQUESTED",
            "recognition_events.jsonl",
            mapOf(
                "captureId" to captureId,
                "captureType" to captureType,
                "sourceDomain" to frame.playbackMode.name,
                "priority" to priority,
                "stage" to frame.stage.name,
                "sceneClockMs" to frame.sceneElapsedMs,
                "eventSequence" to frame.eventSequence,
                "geometryVersion" to frame.geometryVersion,
                "pathId" to (frame.energyEvent?.pathId ?: "NOT_CREATED"),
                "sessionIdHash" to sessionHash,
                "attemptIdHash" to attemptHash,
                "photoIdentityHash" to imageHash,
                "timestampMs" to requestTimestamp,
                "frameSequence" to drawFrameSequence,
                "sceneElapsedMs" to frame.sceneElapsedMs,
                "stageProgress" to frame.stageProgress,
            ),
        )
        RecognitionPixelCopyCapture.request(window, frame, latestFrame) { result ->
            synchronized(lock) {
                if (generation == activeGeneration) {
                    pixelCopyInFlight = false
                }
            }
            if (!isActiveGeneration(generation)) {
                result.bitmap?.recycle()
                completeCapture()
                return@request
            }
            val status = RecognitionEvidenceCapturePolicy.pixelCopyStatus(
                pixelCopyStatus = result.status,
                requestedStage = frame.stage.name,
                completedStage = result.stageAtCompletion,
                sameSceneIdentityAndGeometry = result.sameSceneIdentity,
            )
            if (status != "SUCCESS" || result.bitmap == null) {
                result.bitmap?.recycle()
                runCatching {
                    pixelIo.execute {
                        try {
                            val completionEvidenceSequence = record(
                                status,
                                "recognition_events.jsonl",
                                mapOf(
                                    "captureId" to captureId,
                                    "stage" to frame.stage.name,
                                    "stageAtCompletion" to result.stageAtCompletion,
                                    "geometryVersionAtRequest" to frame.geometryVersion,
                                    "geometryVersionAtCompletion" to result.geometryVersionAtCompletion,
                                    "eventSequenceAtRequest" to frame.eventSequence,
                                    "eventSequenceAtCompletion" to result.eventSequenceAtCompletion,
                                    "pathIdAtRequest" to (frame.energyEvent?.pathId ?: "NOT_CREATED"),
                                    "pathIdAtCompletion" to result.pathIdAtCompletion,
                                    "sameSceneIdentity" to result.sameSceneIdentity,
                                    "requestTimestamp" to requestTimestamp,
                                    "completionTimestamp" to result.completionTimestampMs,
                                    "frameSequence" to drawFrameSequence,
                                    "requestEvidenceSequence" to requestEvidenceSequence,
                                ),
                                generation = generation,
                            )
                            synchronized(lock) {
                                if (generation == activeGeneration) {
                                    if (status == "PIXELCOPY_ERROR" || status == "PIXELCOPY_TIMEOUT") {
                                        pixelCopySuspendedReason = status
                                    }
                                    screenshotFailureCount++
                                    eventSink?.markIncomplete(status)
                                    appendCaptureRow(
                                        failureRow(
                                            captureId, frame, captureType, status, requestTimestamp,
                                            result.completionTimestampMs, result.stageAtCompletion, result.geometryVersionAtCompletion,
                                            result.eventSequenceAtCompletion, result.pathIdAtCompletion,
                                            requestEvidenceSequence,
                                        ).put("completionEvidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                                            .put("evidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                                            .put("evidenceBindingStatus", if (requestEvidenceSequence != null && completionEvidenceSequence != null) "BOUND" else "EVENT_NOT_QUEUED"),
                                    )
                                }
                            }
                        } finally {
                            completeCapture()
                        }
                    }
                }.onFailure {
                    synchronized(lock) {
                        if (generation == activeGeneration) {
                            screenshotFailureCount++
                            eventSink?.markIncomplete("PIXELCOPY_FAILURE_WRITE_QUEUE_FAILED:${it.javaClass.simpleName}")
                        }
                    }
                    completeCapture()
                }
                return@request
            }
            val bitmap = result.bitmap
            runCatching {
                pixelIo.execute {
                    saveCapturedBitmap(
                        attempt = attempt,
                        bitmap = bitmap,
                        captureId = captureId,
                        captureType = captureType,
                        directoryName = directoryName,
                        frame = frame,
                        generation = generation,
                        requestTimestamp = requestTimestamp,
                        completionTimestamp = result.completionTimestampMs,
                        drawFrameSequence = drawFrameSequence,
                        eventSequenceAtCompletion = result.eventSequenceAtCompletion,
                        pathIdAtCompletion = result.pathIdAtCompletion,
                        requestEvidenceSequence = requestEvidenceSequence,
                        sessionHash = sessionHash,
                        attemptHash = attemptHash,
                        imageHash = imageHash,
                    )
                    completeCapture()
                }
            }.onFailure {
                bitmap.recycle()
                if (isActiveGeneration(generation)) synchronized(lock) {
                    screenshotFailureCount++
                    eventSink?.markIncomplete("CAPTURE_IO_QUEUE_FAILED")
                }
                completeCapture()
            }
        }
    }

    private fun completeCapture() {
        captureLock.withLock {
            pendingCaptures = (pendingCaptures - 1).coerceAtLeast(0)
            if (pendingCaptures == 0) capturesIdle.signalAll()
        }
    }

    private fun saveCapturedBitmap(
        attempt: RecognitionSessionEvidenceStore.AttemptStore,
        bitmap: Bitmap,
        captureId: String,
        captureType: String,
        directoryName: String,
        generation: Long,
        frame: RecognitionSceneFrame,
        requestTimestamp: Long,
        completionTimestamp: Long,
        drawFrameSequence: Long,
        eventSequenceAtCompletion: Long?,
        pathIdAtCompletion: String?,
        requestEvidenceSequence: Long?,
        sessionHash: String,
        attemptHash: String,
        imageHash: String,
    ) {
        try {
            val fileName = "${frame.stage.name}_${captureId}.png"
            val file = attempt.file("frames/$directoryName/$fileName")
            val (accepted, digest) = synchronized(pixelFileLock) {
                file.parentFile?.mkdirs()
                val compressed = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!compressed || !file.isFile) error("PNG_WRITE_FAILED")
                val hash = file.sha256()
                val row = JSONObject()
                    .put("captureId", captureId)
                    .put("sceneKey", RecognitionSceneKey.from(attempt.identity).value)
                    .put("sessionIdHash", sessionHash)
                    .put("attemptIdHash", attemptHash)
                    .put("imageIdHash", imageHash)
                    .put("stage", frame.stage.name)
                    .put("stageProgress", frame.stageProgress)
                    .put("sceneElapsedMs", frame.sceneElapsedMs)
                    .put("sceneClockMs", frame.sceneElapsedMs)
                    .put("frameSequence", drawFrameSequence)
                    .put("eventSequence", frame.eventSequence)
                    .put("pathId", frame.energyEvent?.pathId ?: "NOT_CREATED")
                    .put("eventSequenceAtCompletion", eventSequenceAtCompletion)
                    .put("pathIdAtCompletion", pathIdAtCompletion ?: "NOT_RECORDED")
                    .put("geometryVersion", frame.geometryVersion)
                    .put("geometryVersionAtCompletion", frame.geometryVersion)
                    .put("requestTimestamp", requestTimestamp)
                    .put("completionTimestamp", completionTimestamp)
                    .put("timestamp", completionTimestamp)
                    .put("stageAtRequest", frame.stage.name)
                    .put("stageAtCompletion", frame.stage.name)
                    .put("pixelCopyStatus", "SUCCESS")
                    .put("captureType", captureType)
                    .put("sourceDomain", frame.playbackMode.name)
                    .put("requestEvidenceSequence", requestEvidenceSequence ?: JSONObject.NULL)
                    .put("priority", if (captureType == "STAGE_ENTRY" || captureType == "RESULT_SCENE_STABLE") "KEY" else "SAMPLE")
                    .put("relativePath", "frames/$directoryName/$fileName")
                    .put("sha256", hash)
                    .put("width", bitmap.width)
                    .put("height", bitmap.height)
                val completionEvidenceSequence = record(
                    "PIXELCOPY_SAVED",
                    "recognition_events.jsonl",
                    mapOf("captureId" to captureId, "relativePath" to "frames/$directoryName/$fileName", "sha256" to hash),
                    generation = generation,
                )
                row.put("completionEvidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                    .put("evidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                    .put("evidenceBindingStatus", if (requestEvidenceSequence != null && completionEvidenceSequence != null) "BOUND" else "EVENT_NOT_QUEUED")
                if (requestEvidenceSequence == null || completionEvidenceSequence == null) {
                    eventSink?.markIncomplete("PIXELCOPY_EVIDENCE_EVENT_NOT_QUEUED")
                }
                val stored = synchronized(lock) {
                    if (generation != activeGeneration) false else {
                        val manifestPersisted = appendCaptureRow(row)
                        if (manifestPersisted) {
                            screenshotSuccessCount++
                            if (pixelCopyFirstFrameTimestampMs == null) pixelCopyFirstFrameTimestampMs = completionTimestamp
                        }
                        if (captureType == "RESULT_SCENE_STABLE" && manifestPersisted &&
                            row.optString("evidenceBindingStatus") == "BOUND"
                        ) store.markResultEvidenceAvailable(attempt)
                        true
                    }
                }
                if (!stored) file.delete()
                stored to row
            }
            if (!accepted) eventSink?.markIncomplete("PIXELCOPY_CAPTURE_STALE_GENERATION")
        } catch (failure: Throwable) {
            if (isActiveGeneration(generation)) {
                val completionEvidenceSequence = record(
                    "REQUEST_FAILED",
                    "recognition_events.jsonl",
                    mapOf(
                        "captureId" to captureId,
                        "error" to failure.javaClass.simpleName,
                        "requestEvidenceSequence" to requestEvidenceSequence,
                    ),
                    generation = generation,
                )
                synchronized(lock) {
                    screenshotFailureCount++
                    eventSink?.markIncomplete("CAPTURE_WRITE_FAILED:${failure.javaClass.simpleName}")
                    appendCaptureRow(
                        failureRow(
                            captureId, frame, captureType, "REQUEST_FAILED", requestTimestamp, completionTimestamp,
                            frame.stage.name, frame.geometryVersion, eventSequenceAtCompletion, pathIdAtCompletion,
                            requestEvidenceSequence,
                        ).put("completionEvidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                            .put("evidenceSequence", completionEvidenceSequence ?: JSONObject.NULL)
                            .put("evidenceBindingStatus", if (requestEvidenceSequence != null && completionEvidenceSequence != null) "BOUND" else "EVENT_NOT_QUEUED"),
                    )
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun failureRow(
        captureId: String,
        frame: RecognitionSceneFrame,
        captureType: String,
        status: String,
        requestTimestamp: Long,
        completionTimestamp: Long,
        stageAtCompletion: String,
        geometryVersionAtCompletion: Long? = null,
        eventSequenceAtCompletion: Long? = null,
        pathIdAtCompletion: String? = null,
        requestEvidenceSequence: Long? = null,
    ) = JSONObject()
        .put("captureId", captureId)
        .put("sceneKey", RecognitionSceneKey.from(RecognitionEvidenceIdentity(
            sessionId = frame.identity.sessionId,
            attemptId = frame.identity.attemptId,
            imageId = frame.identity.imageId,
            photoSha256 = frame.identity.photoSha256.orEmpty(),
        )).value)
        .put("sessionIdHash", sha256(frame.identity.sessionId))
        .put("attemptIdHash", sha256(frame.identity.attemptId))
        .put("imageIdHash", sha256(frame.identity.imageId))
        .put("stage", frame.stage.name)
        .put("stageProgress", frame.stageProgress)
        .put("sceneElapsedMs", frame.sceneElapsedMs)
        .put("sceneClockMs", frame.sceneElapsedMs)
        .put("frameSequence", frameSequence)
        .put("eventSequence", frame.eventSequence)
        .put("pathId", frame.energyEvent?.pathId ?: "NOT_CREATED")
        .put("eventSequenceAtCompletion", eventSequenceAtCompletion)
        .put("pathIdAtCompletion", pathIdAtCompletion ?: "NOT_RECORDED")
        .put("geometryVersion", frame.geometryVersion)
        .put("geometryVersionAtCompletion", geometryVersionAtCompletion)
        .put("requestTimestamp", requestTimestamp)
        .put("completionTimestamp", completionTimestamp)
        .put("timestamp", completionTimestamp)
        .put("stageAtRequest", frame.stage.name)
        .put("stageAtCompletion", stageAtCompletion)
        .put("pixelCopyStatus", status)
        .put("captureType", captureType)
        .put("sourceDomain", frame.playbackMode.name)
        .put("requestEvidenceSequence", requestEvidenceSequence ?: JSONObject.NULL)
        .put("evidenceSequence", JSONObject.NULL)
        .put("evidenceBindingStatus", if (requestEvidenceSequence != null) "REQUEST_BOUND" else "REQUEST_EVENT_NOT_QUEUED")

    private fun appendCaptureRow(row: JSONObject): Boolean {
        val persisted = runCatching {
            requireNotNull(activeAttempt) { "ATTEMPT_NOT_ACTIVE" }
                .append("frames/pixelcopy_manifest.jsonl", row.toString() + "\n")
        }.isSuccess
        if (persisted) {
            captureRows += JSONObject(row.toString())
        } else {
            screenshotFailureCount++
            eventSink?.markIncomplete("PIXELCOPY_MANIFEST_WRITE_FAILED")
        }
        return persisted
    }

    private fun record(
        type: String,
        fileName: String,
        payload: Map<String, Any?>,
        priority: RecognitionEvidencePriority = RecognitionEvidencePriority.CRITICAL,
        generation: Long? = null,
    ): Long? {
        val result = synchronized(lock) {
            if (generation != null && generation != activeGeneration) return null
            val sink = eventSink ?: return null
            val identity = activeIdentity ?: return null
            val sceneKey = RecognitionSceneKey.from(identity)
            val runtimeSequence = payload["runtimeEventSequence"] ?: payload["eventSequence"]
            val reportedSessionHash = payload["sessionIdHash"]?.toString()
            val reportedAttemptHash = payload["attemptIdHash"]?.toString()
            val reportedPhotoHash = (payload["photoIdentityHash"] ?: payload["imageIdHash"])?.toString()
            val reportedPhotoSha = payload["photoSha256"]?.toString()
            val identityMismatchReasons = buildList {
                if (reportedSessionHash != null && reportedSessionHash != sceneKey.sessionIdHash) add("SESSION_ID_MISMATCH")
                if (reportedAttemptHash != null && reportedAttemptHash != sceneKey.attemptIdHash) add("ATTEMPT_ID_MISMATCH")
                if (reportedPhotoHash != null && reportedPhotoHash != sceneKey.photoIdentityHash) add("PHOTO_IDENTITY_MISMATCH")
                if (reportedPhotoSha != null && reportedPhotoSha != identity.photoSha256) add("PHOTO_SHA256_MISMATCH")
            }
            val eventPayload = payload + mapOf(
                "sceneKey" to sceneKey.value,
                "sessionIdHash" to sceneKey.sessionIdHash,
                "attemptIdHash" to sceneKey.attemptIdHash,
                "photoIdentityHash" to sceneKey.photoIdentityHash,
                "photoSha256" to identity.photoSha256,
                "reportedSessionIdHash" to reportedSessionHash,
                "reportedAttemptIdHash" to reportedAttemptHash,
                "reportedPhotoIdentityHash" to reportedPhotoHash,
                "reportedPhotoSha256" to reportedPhotoSha,
                "identityMatch" to identityMismatchReasons.isEmpty(),
                "identityMismatchReasons" to identityMismatchReasons,
                "sceneClockMs" to (payload["sceneClockMs"] ?: payload["sceneElapsedMs"] ?: latestSceneElapsedMs),
                "stage" to (payload["stage"] ?: latestSceneStage?.name ?: "NOT_REACHED"),
                "stageProgress" to (payload["stageProgress"] ?: latestStageProgress),
                "geometryVersion" to (payload["geometryVersion"] ?: latestGeometryVersion.takeIf { it >= 0L }),
                "runtimeEventSequence" to runtimeSequence,
                "sourceDomain" to (payload["sourceDomain"] ?: payload["playbackMode"]?.toString()
                    ?: latestPlaybackMode.name),
            )
            sink to sink.recordSequenced(RecognitionEvidenceEvent(fileName, type, eventPayload, priority))
        }
        if (result.second == null) {
            result.first.markIncomplete("EVENT_QUEUE_OVERFLOW")
        } else if (priority == RecognitionEvidencePriority.CRITICAL) {
            persistSummarySnapshot()
        }
        return result.second
    }

    private fun persistSummarySnapshot() {
        if (!summaryWriteScheduled.compareAndSet(false, true)) return
        val attempt = synchronized(lock) { activeAttempt }
        if (attempt == null) {
            summaryWriteScheduled.set(false)
            return
        }
        runCatching {
            pixelIo.execute {
                try {
                    val stillCurrent = synchronized(lock) { activeAttempt?.directory == attempt.directory }
                    if (!stillCurrent) return@execute
                    val summary = summaryJson()
                    val stageTimeline = JSONObject()
                        .put("source", "RecognitionSceneAnimationController")
                        .put("stages", summary.optJSONArray("stageStatuses") ?: JSONArray())
                    synchronized(pixelFileLock) {
                        atomicWrite(attempt.file("session/session_summary.json"), summary.toString(2))
                        atomicWrite(attempt.file("session/stage_timeline.json"), stageTimeline.toString(2))
                        atomicWrite(attempt.file("frames/frame_index.json"), captureIndexJson().toString(2))
                    }
                } catch (failure: Throwable) {
                    eventSink?.markIncomplete("SUMMARY_PERSIST_FAILED:${failure.javaClass.simpleName}")
                } finally {
                    summaryWriteScheduled.set(false)
                }
            }
        }.onFailure {
            summaryWriteScheduled.set(false)
            eventSink?.markIncomplete("SUMMARY_PERSIST_QUEUE_FAILED:${it.javaClass.simpleName}")
        }
    }

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun isActiveGeneration(generation: Long): Boolean = synchronized(lock) {
        generation == activeGeneration
    }

    private fun energyPayload(event: EnergyEvent): Map<String, Any?> = mapOf(
        "sessionIdHash" to sha256(event.sessionId),
        "attemptIdHash" to sha256(event.attemptId),
        "imageIdHash" to sha256(event.imageId),
        "photoSha256" to event.photoSha256,
        "feedback" to event.feedback.name,
        "source" to event.source.name,
        "target" to event.target.name,
        "level" to event.level,
        "stage" to event.stage.name,
        "eventSequence" to event.eventSequence,
        "geometryVersion" to event.geometryVersion,
        "taskPathId" to event.pathId,
        "pStarPathId" to event.pathId,
        "taskPathGeometry" to event.pathGeometry?.toEvidenceMap(),
        "returnPathGeometry" to event.pathSegments.map { it.toEvidenceMap() },
        "maskSha256" to event.pathMaskSha256,
        "maskVersion" to event.pathMaskVersion,
        "pathGeometryVersion" to event.pathGeometryVersion,
        "pStarNormalizedSource" to event.pathContactPointNormalizedSource?.let { listOf(it.x, it.y) },
        "referenceMs" to event.referenceMs,
        "renderStatus" to event.renderStatus,
        "sourceHost" to event.sourceHost,
        "targetHost" to event.targetHost,
        "pathSegments" to event.pathSegments.size,
    )

    private fun com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry.toEvidenceMap(): Map<String, Any?> = mapOf(
        "start" to listOf(start.x, start.y),
        "control1" to listOf(control1.x, control1.y),
        "control2" to listOf(control2.x, control2.y),
        "end" to listOf(end.x, end.y),
        "coordinateSpace" to coordinateSpace,
    )

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { get(it) }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal companion object {
        const val MAX_TOTAL_SCREENSHOTS = 64
        const val MAX_MOTION_SAMPLES = 32
        const val MOTION_INTERVAL_MS = 250L
        const val WAIT_HEARTBEAT_INTERVAL_MS = 1_000L
        const val RENDER_TRACE_INTERVAL_MS = 1_000L
        const val DEFAULT_FLUSH_TIMEOUT_MS = 2_500L
        const val MAX_FLUSH_TIMEOUT_MS = 5_000L
        const val MAX_PIXELCOPY_FLUSH_WAIT_MS = 1_500L
        const val MAX_PIXEL_WRITE_FLUSH_WAIT_MS = 1_500L
        const val DEFAULT_TAIL_FLUSH_WAIT_MS = 400L
        const val MAX_EXPORT_FRAMES_BYTES = 24L * 1024L * 1024L
    }
}
