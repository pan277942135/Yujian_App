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
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class RecognitionEvidenceExportSnapshot(
    val attemptDirectory: File,
    val identity: RecognitionEvidenceIdentity?,
    val summary: JSONObject,
    val captureIndex: JSONArray,
    val queueDiagnostics: RecognitionEvidenceQueueDiagnostics,
    val degradedReasons: List<String>,
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
    private val captureLock = ReentrantLock()
    private val capturesIdle = captureLock.newCondition()
    private var pendingCaptures = 0
    private val lock = Any()
    private val pixelFileLock = Any()
    private var activeGeneration = 0L
    private var activeIdentity: RecognitionEvidenceIdentity? = null
    private var activeAttempt: RecognitionSessionEvidenceStore.AttemptStore? = null
    private var eventSink: RecognitionEvidenceEventSink? = null
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
    private var latestSceneElapsedMs = 0L
    private var latestGeometryVersion = -1L

    fun beginAttempt(identity: RecognitionEvidenceIdentity) {
        synchronized(lock) {
            activeGeneration++
            eventSink?.flush()
            eventSink?.dispose()
            activeIdentity = identity
            activeAttempt = store.begin(identity)
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
            latestSceneElapsedMs = 0L
            latestGeometryVersion = -1L
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
    }

    fun recordStageEvent(event: RecognitionSceneStageEvent) {
        val identity = activeIdentity ?: return
        if (identity.sessionId != event.sessionId || identity.attemptId != event.attemptId) return
        synchronized(lock) {
            latestSceneElapsedMs = maxOf(latestSceneElapsedMs, event.sceneElapsedMs)
            latestGeometryVersion = event.geometryVersion
            if (event.stage == RecognitionSceneStage.PHOTO_STABLE &&
                event.action == RecognitionSceneStageEventAction.ENTERED && firstFrameTimestampMs == null
            ) firstFrameTimestampMs = SystemClock.uptimeMillis()
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
    ) {
        val merged = event.feedback == EnergyFeedback.RETURN && accepted && event.level >= .999f
        val taskState = RecognitionTaskFilamentState.fromEvent(
            event,
            taskEnergyMerged = taskEnergyMergedBeforeEvent || merged,
        )
        val payload = energyPayload(event) + mapOf(
            "controllerAccepted" to accepted,
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
                "feedback" to event.feedback,
                "progress" to event.progress,
                "renderStatus" to event.renderStatus,
                "accepted" to accepted,
                "rejectionReason" to reason,
                "resultIdentity" to event.resultIdentity,
            ),
        )
    }

    fun recordResultInfoEntranceCheckpoint(
        checkpoint: String,
        identity: RecognitionSceneIdentity,
        resultIdentity: String,
        stage: RecognitionSceneStage,
        stageProgress: Float,
        entranceProgress: Float,
        geometryVersion: Long,
        drawPassSequence: Long? = null,
        renderStatus: String,
    ) {
        record(
            type = checkpoint,
            fileName = "result_handoff.jsonl",
            payload = mapOf(
                "sessionIdHash" to sha256(identity.sessionId),
                "attemptIdHash" to sha256(identity.attemptId),
                "imageIdHash" to sha256(identity.imageId),
                "resultIdentity" to resultIdentity,
                "stage" to stage.name,
                "stageProgress" to stageProgress,
                "infoEntranceProgress" to entranceProgress,
                "geometryVersion" to geometryVersion,
                "drawPassSequence" to drawPassSequence,
                "renderStatus" to renderStatus,
            ),
        )
    }

    fun recordResultGeometryDiagnostic(
        identity: RecognitionSceneIdentity,
        failureReason: String?,
        geometryReady: Boolean,
        geometryVersion: Long,
        pathId: String?,
        maskSha256: String?,
        maskVersion: Long,
        resultIdentity: String?,
        gates: Map<String, Any?> = emptyMap(),
    ) {
        record(
            "RESULT_GEOMETRY_RESOLVED",
            "result_geometry_diagnostics.jsonl",
            mapOf(
                "sessionIdHash" to sha256(identity.sessionId),
                "attemptIdHash" to sha256(identity.attemptId),
                "imageIdHash" to sha256(identity.imageId),
                "photoSha256" to identity.photoSha256,
                "failureReason" to failureReason,
                "geometryReady" to geometryReady,
                "geometryVersion" to geometryVersion,
                "pathId" to pathId,
                "maskSha256" to maskSha256,
                "maskVersion" to maskVersion,
                "resultIdentity" to resultIdentity,
                "gates" to gates,
            ),
        )
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
    }

    /** Preserve the exact decoded SlimSAM PNG and its alpha distribution for postmortem QA. */
    fun recordMaskArtifact(
        source: File, bitmap: Bitmap, expectedSha256: String,
        sceneIdentity: RecognitionSceneIdentity,
    ): Boolean {
        if (!source.isFile || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        val bytes = source.readBytes()
        val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        if (!actualSha.equals(expectedSha256, ignoreCase = true)) return false
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val histogram = IntArray(256)
        pixels.forEach { histogram[it ushr 24]++ }
        synchronized(pixelFileLock) {
            val attempt = synchronized(lock) {
                if (activeIdentity?.matches(sceneIdentity) == true) activeAttempt else null
            } ?: return false
            val folder = File(attempt.directory, "mask").apply { mkdirs() }
            File(folder, "original_mask.png").writeBytes(bytes)
            File(folder, "alpha_statistics.json").writeText(
                JSONObject().put("maskSha256", actualSha)
                    .put("width", bitmap.width).put("height", bitmap.height)
                    .put("alphaMin", histogram.indexOfFirst { it > 0 })
                    .put("alphaMax", histogram.indexOfLast { it > 0 })
                    .put("alphaNonZeroCount", pixels.size - histogram[0])
                    .put("alphaAbove36Count", histogram.slice(37..255).sum())
                    .put("alphaAbove128Count", histogram.slice(129..255).sum())
                    .put("alphaAt128Count", histogram[128])
                    .put("alphaAtLeast128Count", histogram.slice(128..255).sum())
                    .put("alphaAt255Count", histogram[255])
                    .put("histogram", JSONArray(histogram.toList()))
                    .toString(2), Charsets.UTF_8,
            )
        }
        return true
    }

    fun recordOrbRuntime(fileName: String, type: String, payload: Map<String, Any?>) {
        record(type, fileName, payload, RecognitionEvidencePriority.SAMPLE)
    }

    /** Stores the GPU host receipt in the existing bounded evidence stream. */
    fun recordGpuRenderResult(
        identity: RecognitionSceneIdentity,
        payload: Map<String, Any?>,
    ): Boolean {
        val geometry = (payload["geometryVersion"] as? Number)?.toLong()
        val (identityMatches, expectedGeometry) = synchronized(lock) {
            (activeIdentity?.matches(identity) == true) to latestGeometryVersion
        }
        val reason = when {
            !identityMatches -> "SCENE_IDENTITY_NOT_ACTIVE"
            expectedGeometry >= 0L && geometry != expectedGeometry -> "GEOMETRY_VERSION_NOT_ACTIVE"
            else -> null
        }
        val reasonPayload = if (reason == null) emptyMap() else mapOf(
            "receiptAccepted" to false,
            "receiptRejectionReason" to reason,
            "receivedSessionIdHash" to sha256(identity.sessionId),
            "receivedAttemptIdHash" to sha256(identity.attemptId),
            "receivedPhotoIdentityHash" to sha256(identity.imageId),
        )
        val ownerAccepted = payload["ownerAccepted"] == true
        val failureReason = payload["failureReason"]?.toString()?.takeIf(String::isNotBlank)
        val eventType = payload["eventType"]?.toString()?.takeIf(String::isNotBlank) ?: when {
            reason != null -> "GPU_RENDER_RECEIPT_REJECTED"
            failureReason?.contains("CONTEXT_LOST", ignoreCase = true) == true -> "GPU_CONTEXT_LOST"
            failureReason?.contains("SURFACE", ignoreCase = true) == true -> "SURFACE_NOT_READY"
            ownerAccepted -> "GPU_RENDER_ACTIVE"
            payload.values.any { it == "FAIL" } -> "GPU_RENDER_FAILED"
            else -> "GPU_RENDER_SUPPRESSED"
        }
        val priority = if (
            reason != null || failureReason != null || payload.values.any { it == "FAIL" } ||
            eventType in setOf("FIRST_FRAME_ACCEPTED", "FIRST_FRAME_REJECTED", "FALLBACK_ACTIVE", "GPU_CONTEXT_LOST", "SURFACE_NOT_READY")
        ) RecognitionEvidencePriority.CRITICAL else RecognitionEvidencePriority.SAMPLE
        val queued = record(
            type = eventType,
            fileName = "gpu_trace.jsonl",
            payload = payload + reasonPayload + mapOf("receiptAccepted" to (reason == null)),
            priority = priority,
        )
        return queued != null
    }

    fun recordHeartbeat(frame: RecognitionSceneFrame) {
        val waiting = frame.modelGates.waitingReason ?: return
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
        if (!allowPixelCopyCapture || window == null || frame.geometryVersion <= 0L) return
        val isCurrentFrame = synchronized(lock) {
            activeIdentity?.matches(frame.identity) == true && pixelCopySuspendedReason == null
        }
        if (!isCurrentFrame) return
        synchronized(lock) {
            latestGeometryVersion = frame.geometryVersion
            latestSceneElapsedMs = maxOf(latestSceneElapsedMs, frame.sceneElapsedMs)
        }
        val stageName = frame.stage.name
        val enterRequest = synchronized(lock) { entryCaptureRequested.add(stageName) }
        if (enterRequest) requestCapture(window, frame, latestFrame, "STAGE_ENTRY", "stage_entry", priority = "KEY")
        if (resultSceneStable && synchronized(lock) { !resultStableCaptureRequested.also { if (!it) resultStableCaptureRequested = true } }) {
            requestCapture(window, frame, latestFrame, "RESULT_SCENE_STABLE", "stage_entry", priority = "KEY")
        }
        // Motion screenshots are reserved for visible AUTO progress. Model waits are
        // represented by the one-second heartbeat and must not trigger high-frequency capture.
        val visibleMotion = !enterRequest && RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
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
        synchronized(lock) { latestGeometryVersion = geometryVersion }
        record(
            "RESULT_FIRST_PRESENTED",
            "result_handoff.jsonl",
            mapOf(
                "sceneElapsedMs" to sceneElapsedMs,
                "geometryVersion" to geometryVersion,
                "resultIdentity" to resultIdentity,
            ),
        )
    }

    fun activeAttemptDirectory(): File? = synchronized(lock) { activeAttempt?.directory }
    fun exportIdentity(): RecognitionEvidenceIdentity? = synchronized(lock) { activeIdentity }

    fun flush(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        val clampedTimeoutMs = timeoutMs.coerceIn(1L, MAX_FLUSH_TIMEOUT_MS)
        val deadline = clockMs() + clampedTimeoutMs
        val pixelCopyDeadline = deadline
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
        val pixelIoWaitMs = (deadline - clockMs()).coerceAtLeast(DEFAULT_TAIL_FLUSH_WAIT_MS)
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
        return pixelCopyDrained && pixelWritesDrained && eventsDrained
    }

    /**
     * Produces a frozen, isolated directory snapshot of logs, frames, and summary metadata.
     * Pending PixelCopy / EventSink timeouts are degraded cleanly into [degradedReasons] without failing.
     */
    internal fun createImmutableExportSnapshot(
        stagingDirectory: File,
        flushTimeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS,
    ): RecognitionEvidenceExportSnapshot? {
        flush(flushTimeoutMs)
        val (attempt, sink) = synchronized(lock) { activeAttempt to eventSink }
        if (attempt == null) return null

        val degradedReasons = mutableListOf<String>()
        val targetLogs = File(stagingDirectory, "logs").apply { mkdirs() }
        val logSnapshot = sink?.snapshotLogsTo(
            targetLogsDir = targetLogs,
            flushTimeoutMs = minOf(flushTimeoutMs.coerceAtLeast(1L), RecognitionEvidenceEventSink.DEFAULT_FLUSH_TIMEOUT_MS),
        )
        if (logSnapshot?.sizeCapReached == true) {
            degradedReasons += "LOG_SNAPSHOT_SIZE_CAP_REACHED:${logSnapshot.truncatedFiles.joinToString(",")}"
        }

        return synchronized(pixelFileLock) {
            val (identity, summary, captureIndex) = synchronized(lock) {
                Triple(activeIdentity, summaryJson(), captureIndexJson())
            }
            val sessionIdentitySource = attempt.file("session/session_identity.json")
            if (sessionIdentitySource.isFile) {
                val targetIdentity = File(stagingDirectory, "session/session_identity.json")
                targetIdentity.parentFile?.mkdirs()
                sessionIdentitySource.copyTo(targetIdentity, overwrite = true)
            }
            val framesSourceRoot = File(attempt.directory, "frames")
            var copiedFrameBytes = 0L
            var frameCapHit = false
            val frameFiles = framesSourceRoot.walkTopDown().filter(File::isFile).sortedBy(File::getPath).toList()
            for (sourceFrame in frameFiles) {
                val rel = sourceFrame.relativeTo(attempt.directory).invariantSeparatorsPath
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
                sink?.markIncomplete("FRAME_SNAPSHOT_SIZE_CAP_REACHED")
                degradedReasons += "FRAME_SNAPSHOT_SIZE_CAP_REACHED"
            }
            val maskSource = File(attempt.directory, "mask")
            maskSource.listFiles()?.filter(File::isFile)?.forEach { sourceMask ->
                val destMask = File(stagingDirectory, "mask/${sourceMask.name}")
                destMask.parentFile?.mkdirs()
                sourceMask.copyTo(destMask, overwrite = true)
            }
            val finalDiagnostics = diagnostics()
            val refreshedSummary = synchronized(lock) { summaryJson() }
            RecognitionEvidenceExportSnapshot(
                attemptDirectory = attempt.directory,
                identity = identity,
                summary = refreshedSummary,
                captureIndex = captureIndex,
                queueDiagnostics = finalDiagnostics,
                degradedReasons = (degradedReasons + finalDiagnostics.incompleteReasons).distinct(),
            )
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
            .put("schemaVersion", 1)
            .put("sessionIdHash", activeIdentity?.let { sha256(it.sessionId) })
            .put("attemptIdHash", activeIdentity?.let { sha256(it.attemptId) })
            .put("imageIdHash", activeIdentity?.let { sha256(it.imageId) })
            .put("photoSha256", activeIdentity?.photoSha256)
            .put("resultEvidenceStatus", resultEvidenceStatus.status)
            .put("resultEvidenceMissingReason", resultEvidenceStatus.missingReason ?: JSONObject.NULL)
            .put("firstFrameTimestampMs", firstFrameTimestampMs ?: -1L)
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
                appendCaptureRow(
                    failureRow(captureId, frame, captureType, "CAPTURE_LIMIT_REACHED", requestTimestamp, requestTimestamp, frame.stage.name),
                )
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
        record(
            "PIXELCOPY_REQUESTED",
            "recognition_events.jsonl",
            mapOf(
                "captureId" to captureId,
                "captureType" to captureType,
                "priority" to priority,
                "stage" to frame.stage.name,
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
                synchronized(lock) {
                    if (generation == activeGeneration) {
                        if (status == "STAGE_IDENTITY_UNVERIFIED") {
                            if (captureType == "STAGE_ENTRY") entryCaptureRequested.remove(frame.stage.name)
                            if (captureType == "RESULT_SCENE_STABLE") resultStableCaptureRequested = false
                        }
                        if (status == "PIXELCOPY_ERROR" || status == "PIXELCOPY_TIMEOUT") {
                            pixelCopySuspendedReason = status
                        }
                        screenshotFailureCount++
                        eventSink?.markIncomplete(status)
                        appendCaptureRow(
                            failureRow(
                                captureId, frame, captureType, status, requestTimestamp,
                                result.completionTimestampMs, result.stageAtCompletion, result.geometryVersionAtCompletion,
                            ),
                        )
                    }
                }
                record(
                    status,
                    "recognition_events.jsonl",
                    mapOf(
                        "captureId" to captureId,
                        "stage" to frame.stage.name,
                        "stageAtCompletion" to result.stageAtCompletion,
                        "geometryVersionAtRequest" to frame.geometryVersion,
                        "geometryVersionAtCompletion" to result.geometryVersionAtCompletion,
                        "sameSceneIdentity" to result.sameSceneIdentity,
                        "requestTimestamp" to requestTimestamp,
                        "completionTimestamp" to result.completionTimestampMs,
                        "frameSequence" to drawFrameSequence,
                    ),
                    generation = generation,
                )
                completeCapture()
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
                    .put("sessionIdHash", sessionHash)
                    .put("attemptIdHash", attemptHash)
                    .put("imageIdHash", imageHash)
                    .put("stage", frame.stage.name)
                    .put("stageProgress", frame.stageProgress)
                    .put("sceneElapsedMs", frame.sceneElapsedMs)
                    .put("frameSequence", drawFrameSequence)
                    .put("geometryVersion", frame.geometryVersion)
                    .put("geometryVersionAtCompletion", frame.geometryVersion)
                    .put("requestTimestamp", requestTimestamp)
                    .put("completionTimestamp", completionTimestamp)
                    .put("stageAtRequest", frame.stage.name)
                    .put("stageAtCompletion", frame.stage.name)
                    .put("pixelCopyStatus", "SUCCESS")
                    .put("captureType", captureType)
                    .put("priority", if (captureType == "STAGE_ENTRY" || captureType == "RESULT_SCENE_STABLE") "KEY" else "SAMPLE")
                    .put("relativePath", "frames/$directoryName/$fileName")
                    .put("sha256", hash)
                    .put("width", bitmap.width)
                    .put("height", bitmap.height)
                val stored = synchronized(lock) {
                    if (generation != activeGeneration) false else {
                        screenshotSuccessCount++
                        appendCaptureRow(row)
                        true
                    }
                }
                if (!stored) file.delete()
                stored to row
            }
            if (accepted) {
                record("PIXELCOPY_SAVED", "recognition_events.jsonl", digest.toMap(), generation = generation)
            }
        } catch (failure: Throwable) {
            if (isActiveGeneration(generation)) synchronized(lock) {
                screenshotFailureCount++
                eventSink?.markIncomplete("CAPTURE_WRITE_FAILED:${failure.javaClass.simpleName}")
                appendCaptureRow(
                    failureRow(captureId, frame, captureType, "REQUEST_FAILED", requestTimestamp, completionTimestamp, frame.stage.name),
                )
            }
            if (isActiveGeneration(generation)) {
                record(
                    "REQUEST_FAILED",
                    "recognition_events.jsonl",
                    mapOf("captureId" to captureId, "error" to failure.javaClass.simpleName),
                    generation = generation,
                )
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
    ) = JSONObject()
        .put("captureId", captureId)
        .put("sessionIdHash", sha256(frame.identity.sessionId))
        .put("attemptIdHash", sha256(frame.identity.attemptId))
        .put("imageIdHash", sha256(frame.identity.imageId))
        .put("stage", frame.stage.name)
        .put("stageProgress", frame.stageProgress)
        .put("sceneElapsedMs", frame.sceneElapsedMs)
        .put("frameSequence", frameSequence)
        .put("geometryVersion", frame.geometryVersion)
        .put("geometryVersionAtCompletion", geometryVersionAtCompletion)
        .put("requestTimestamp", requestTimestamp)
        .put("completionTimestamp", completionTimestamp)
        .put("stageAtRequest", frame.stage.name)
        .put("stageAtCompletion", stageAtCompletion)
        .put("pixelCopyStatus", status)
        .put("captureType", captureType)

    private fun appendCaptureRow(row: JSONObject) {
        captureRows += JSONObject(row.toString())
    }

    private fun record(
        type: String,
        fileName: String,
        payload: Map<String, Any?>,
        priority: RecognitionEvidencePriority = RecognitionEvidencePriority.CRITICAL,
        generation: Long? = null,
    ) {
        val result = synchronized(lock) {
            if (generation != null && generation != activeGeneration) return
            val sink = eventSink ?: return
            sink to sink.record(RecognitionEvidenceEvent(fileName, type, payload, priority))
        }
        if (!result.second) {
            result.first.markIncomplete("EVENT_QUEUE_OVERFLOW")
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
        const val DEFAULT_FLUSH_TIMEOUT_MS = 20_000L
        const val MAX_FLUSH_TIMEOUT_MS = 30_000L
        const val DEFAULT_TAIL_FLUSH_WAIT_MS = 400L
        // The 26 actual full-resolution PixelCopy PNGs are copied on disk, not
        // retained as Bitmaps. The former 24 MiB cap dropped valid captures.
        const val MAX_EXPORT_FRAMES_BYTES = 128L * 1024L * 1024L
    }
}
