package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.waterlight.BuildConfig
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.abs

internal class OrbStage02BoundedEventBuffer(private val capacity: Int) {
    private val rows = ArrayDeque<String>(capacity)
    var droppedCount: Long = 0L
        private set

    init {
        require(capacity > 0)
    }

    fun append(encodedRow: String) {
        if (rows.size >= capacity) {
            rows.removeFirst()
            droppedCount++
        }
        rows.addLast(encodedRow)
    }

    fun snapshot(): List<String> = rows.toList()

    fun clear() {
        rows.clear()
        droppedCount = 0L
    }

    val size: Int get() = rows.size
}

internal data class OrbStage02TraceFrameSample(
    val frameTraceId: String,
    val stageElapsedMs: Long,
)

/** Assigns checkpoints to existing AUTO frames; it never creates synthetic frame records. */
internal fun nearestOrbWakeCheckpointTargets(
    samples: List<OrbStage02TraceFrameSample>,
): Map<String, List<Long>> {
    if (samples.isEmpty()) return emptyMap()
    val targets = longArrayOf(0L, 80L, 125L, 220L, 280L, 350L)
    val byFrame = linkedMapOf<String, MutableList<Long>>()
    targets.forEach { targetMs ->
        val nearest = samples.minByOrNull { abs(it.stageElapsedMs - targetMs) } ?: return@forEach
        byFrame.getOrPut(nearest.frameTraceId) { mutableListOf() }.add(targetMs)
    }
    return byFrame.mapValues { (_, targetMs) -> targetMs.toList() }
}

internal fun manualPausedOrbFrameFingerprint(
    playbackMode: String,
    paused: Boolean,
    stage: String,
    stageProgress: Float,
    stageElapsedMs: Long,
    sceneElapsedMs: Long,
    opticalReferenceElapsedMs: Long,
    geometryVersion: Long,
    visualProfile: String,
): String? {
    if (playbackMode != "MANUAL" || !paused) return null
    return listOf(
        stage,
        stageProgress.toBits(),
        stageElapsedMs,
        sceneElapsedMs,
        opticalReferenceElapsedMs,
        geometryVersion,
        visualProfile,
    ).joinToString("|")
}

/** Bounded, privacy-safe Debug trace for the Orb render pipeline. */
object OrbStage02RenderTrace {
    private const val SCHEMA_VERSION = 1
    private const val MAX_EVENTS = 4_096
    private const val MAX_STAGE02_EVENTS = 4_096
    private const val MAX_RATE_LIMIT_KEYS = 256
    private val lock = Any()
    private val events = ArrayDeque<String>(MAX_EVENTS)
    // Independent per-Attempt retention keeps production evidence when later diagnostic captures flood the general ring.
    private val productionStage02Events = OrbStage02BoundedEventBuffer(MAX_STAGE02_EVENTS)
    private val manualStage02Events = OrbStage02BoundedEventBuffer(MAX_STAGE02_EVENTS)
    private val manualPausedEventKeys = HashSet<String>()
    private val recentIssueLogsNs = LinkedHashMap<String, Long>()
    private val captureMarkers = HashMap<String, CompletableDeferred<Unit>>()
    private val captureStates = HashMap<String, String>()
    private val capturePathEntriesById = HashMap<String, LinkedHashMap<String, String>>()
    private var droppedEvents = 0L
    private var overflowMarkerRecorded = false
    private var manualPausedFrameFingerprint: String? = null
    private var manualPausedFrameTraceId: String? = null
    private var manualPausedDuplicateFrameCount = 0L
    private var manualPausedDuplicateEventCount = 0L
    private var traceId = ""
    private var frameSequence = 0L
    private var currentContext: Map<String, Any?> = emptyMap()
    @Volatile private var currentFrameTraceId = ""

    fun beginFrame(frame: RecognitionSceneFrame, profile: OrbStage02VisualProfile): String {
        if (!BuildConfig.DEBUG) return ""
        val nextTraceId = digest(
            "${frame.identity.sessionId}|${frame.identity.attemptId}|${frame.identity.imageId}",
        ).take(20)
        var shouldRecordFrame = true
        val frameId = synchronized(lock) {
            if (traceId != nextTraceId) {
                traceId = nextTraceId
                frameSequence = 0L
                droppedEvents = 0L
                overflowMarkerRecorded = false
                events.clear()
                productionStage02Events.clear()
                manualStage02Events.clear()
                recentIssueLogsNs.clear()
                manualPausedFrameFingerprint = null
                manualPausedFrameTraceId = null
                manualPausedDuplicateFrameCount = 0L
                manualPausedDuplicateEventCount = 0L
                manualPausedEventKeys.clear()
            }
            val nextContext = mapOf(
                "sessionIdHash" to digest(frame.identity.sessionId).take(20),
                "attemptIdHash" to digest(frame.identity.attemptId).take(20),
                "imageIdHash" to digest(frame.identity.imageId).take(20),
                "stage" to frame.stage.name,
                "stageProgress" to frame.stageProgress,
                "stageElapsedMs" to frame.stageElapsedMs,
                "sceneElapsedMs" to frame.sceneElapsedMs,
                "opticalReferenceElapsedMs" to frame.opticalReferenceElapsedMs,
                "geometryVersion" to frame.geometryVersion,
                "playbackMode" to frame.playbackMode.name,
                "manualPlaybackState" to (frame.playbackMode.name == "MANUAL"),
                "paused" to frame.paused,
            )
            val fingerprint = manualPausedOrbFrameFingerprint(
                playbackMode = frame.playbackMode.name,
                paused = frame.paused,
                stage = frame.stage.name,
                stageProgress = frame.stageProgress,
                stageElapsedMs = frame.stageElapsedMs,
                sceneElapsedMs = frame.sceneElapsedMs,
                opticalReferenceElapsedMs = frame.opticalReferenceElapsedMs,
                geometryVersion = frame.geometryVersion,
                visualProfile = profile.name,
            )
            if (fingerprint != null && fingerprint == manualPausedFrameFingerprint &&
                !manualPausedFrameTraceId.isNullOrBlank()
            ) {
                currentContext = nextContext
                currentFrameTraceId = requireNotNull(manualPausedFrameTraceId)
                manualPausedDuplicateFrameCount++
                shouldRecordFrame = false
                currentFrameTraceId
            } else {
                frameSequence += 1L
                currentFrameTraceId = "$traceId-$frameSequence"
                currentContext = nextContext
                manualPausedFrameFingerprint = fingerprint
                manualPausedFrameTraceId = if (fingerprint != null) currentFrameTraceId else null
                manualPausedEventKeys.clear()
                currentFrameTraceId
            }
        }
        if (shouldRecordFrame) {
            record(
                eventType = "ORB_SCENE_FRAME",
                dataSource = "RUNTIME_STATE",
                fields = mapOf(
                    "stage" to frame.stage.name,
                    "stageProgress" to frame.stageProgress,
                    "stageElapsedMs" to frame.stageElapsedMs,
                    "sceneElapsedMs" to frame.sceneElapsedMs,
                    "opticalReferenceElapsedMs" to frame.opticalReferenceElapsedMs,
                    "playbackMode" to frame.playbackMode.name,
                    "paused" to frame.paused,
                    "geometryVersion" to frame.geometryVersion,
                    "visualProfile" to profile.name,
                ),
            )
        }
        return frameId
    }

    fun record(
        eventType: String,
        dataSource: String,
        captureId: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            val context = currentContext
            val manualPaused = context["playbackMode"]?.toString() == "MANUAL" &&
                context["paused"] == true
            if (manualPaused && captureId == null) {
                val dedupeKey = listOf(
                    currentFrameTraceId,
                    eventType,
                    fields["pathId"]?.toString().orEmpty(),
                    fields["layerId"]?.toString().orEmpty(),
                    fields["renderPass"]?.toString().orEmpty(),
                    fields["front"]?.toString().orEmpty(),
                ).joinToString("|")
                if (!manualPausedEventKeys.add(dedupeKey)) {
                    manualPausedDuplicateEventCount++
                    return
                }
            }
            val row = JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                .put("traceId", traceId)
                .put("frameTraceId", currentFrameTraceId)
                .put("captureId", captureId)
                .put("timestampNs", System.nanoTime())
                .put("eventType", eventType)
                .put("dataSource", dataSource)
            context.forEach { (key, value) -> row.put(key, value) }
            fields.forEach { (key, value) -> row.put(key, value) }
            val encodedRow = row.toString()

            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents++
            }
            if (droppedEvents > 0L && !overflowMarkerRecorded) {
                if (events.size >= MAX_EVENTS) {
                    events.removeFirst()
                    droppedEvents++
                }
                val overflow = JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                    .put("traceId", traceId)
                    .put("frameTraceId", currentFrameTraceId)
                    .put("timestampNs", System.nanoTime())
                    .put("eventType", "TRACE_BUFFER_OVERFLOW")
                    .put("dataSource", "RUNTIME_STATE")
                    .put("droppedEventCount", droppedEvents)
                context.forEach { (key, value) -> overflow.put(key, value) }
                events.addLast(overflow.toString())
                overflowMarkerRecorded = true
            }
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents++
            }
            events.addLast(encodedRow)

            if (context["stage"] == "ORB_WAKE") {
                when {
                    context["playbackMode"] == "AUTO" && context["paused"] == false ->
                        productionStage02Events.append(encodedRow)
                    context["playbackMode"] == "MANUAL" ->
                        manualStage02Events.append(encodedRow)
                }
            }
        }
    }

    /** Keeps recurring renderer defects observable without writing one log row per frame. */
    fun recordRateLimited(
        throttleKey: String,
        eventType: String,
        dataSource: String,
        captureId: String? = null,
        minimumIntervalMs: Long = 1_000L,
        fields: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (!BuildConfig.DEBUG) return false
        val nowNs = System.nanoTime()
        val accepted = synchronized(lock) {
            val previous = recentIssueLogsNs[throttleKey]
            if (previous != null && nowNs - previous < minimumIntervalMs.coerceAtLeast(0L) * 1_000_000L) {
                false
            } else {
                recentIssueLogsNs.remove(throttleKey)
                recentIssueLogsNs[throttleKey] = nowNs
                while (recentIssueLogsNs.size > MAX_RATE_LIMIT_KEYS) {
                    recentIssueLogsNs.remove(recentIssueLogsNs.keys.first())
                }
                true
            }
        }
        if (accepted) record(eventType, dataSource, captureId, fields)
        return accepted
    }

    fun registerCaptureMarker(captureId: String): CompletableDeferred<Unit> = synchronized(lock) {
        capturePathEntriesById[captureId] = linkedMapOf()
        CompletableDeferred<Unit>().also { captureMarkers[captureId] = it }
    }

    /** Captures current-frame renderer metrics under the pending PixelCopy identity. */
    fun recordCapturePathDiagnostics(
        captureId: String,
        renderPass: String,
        front: Boolean,
        excludedPathId: String?,
        paths: List<Map<String, Any?>>,
    ) {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            val entries = capturePathEntriesById[captureId] ?: return
            paths.forEach { fields ->
                val pathId = fields["pathId"]?.toString() ?: return@forEach
                val key = "$renderPass|$front|$pathId"
                val row = JSONObject()
                    .put("dataSource", "CURRENT_CAPTURE_CANVAS_DRAW_METRICS_NOT_PIXEL_READBACK")
                    .put("renderPass", renderPass)
                    .put("front", front)
                    .put("pathId", pathId)
                    .put("excludedByCapture", pathId == excludedPathId)
                fields.forEach { (name, value) -> row.put(name, value) }
                entries[key] = row.toString()
            }
        }
    }

    fun capturePathDiagnostics(captureId: String): JSONArray = synchronized(lock) {
        JSONArray().also { result ->
            capturePathEntriesById[captureId]?.values?.forEach { result.put(JSONObject(it)) }
        }
    }

    fun hasCaptureEvent(captureId: String, eventType: String): Boolean = synchronized(lock) {
        events.any { encoded ->
            val event = JSONObject(encoded)
            event.optString("captureId") == captureId && event.optString("eventType") == eventType
        }
    }

    fun markCaptureDrawn(captureId: String, fields: Map<String, Any?> = emptyMap()) {
        val (waiter, previouslyObserved, context) = synchronized(lock) {
            val previous = captureStates[captureId]?.let { JSONObject(it).optBoolean("markerObserved") } == true
            Triple(captureMarkers.remove(captureId), previous, currentContext)
        }
        val markerObserved = waiter != null || previouslyObserved
        record(
            eventType = "ORB_CAPTURE_MARKER",
            dataSource = "CANVAS_DRAW_SUBMISSION",
            captureId = captureId,
            fields = fields + ("markerObserved" to markerObserved),
        )
        val state = JSONObject()
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("frameTraceId", currentFrameTraceId)
            .put("captureId", captureId)
            .put("markerObserved", markerObserved)
        context.forEach { (key, value) -> state.put(key, value) }
        fields.forEach { (key, value) -> state.put(key, value) }
        synchronized(lock) {
            if (captureStates.size >= MAX_EVENTS) captureStates.remove(captureStates.keys.first())
            captureStates[captureId] = state.toString()
        }
        waiter?.complete(Unit)
    }

    fun captureState(captureId: String): JSONObject? = synchronized(lock) {
        captureStates[captureId]?.let(::JSONObject)
    }

    fun clearCaptureState(captureId: String) {
        synchronized(lock) {
            captureMarkers.remove(captureId)
            captureStates.remove(captureId)
            capturePathEntriesById.remove(captureId)
        }
    }

    /** Export the active Attempt's general trace plus independently retained Stage02 streams. */
    fun snapshotJsonLines(): String = synchronized(lock) {
        val activeTrace = traceId
        val encodedRows = (events.toList() + productionStage02Events.snapshot() + manualStage02Events.snapshot())
            .distinct()
        val activeRows = encodedRows.mapNotNull { encoded ->
            val row = JSONObject(encoded)
            if (row.optString("traceId") == activeTrace) encoded to row else null
        }
        val productionSamples = activeRows.mapNotNull { (_, row) ->
            if (row.optString("eventType") == "ORB_SCENE_FRAME" &&
                row.optString("stage") == "ORB_WAKE" &&
                row.optString("playbackMode") == "AUTO" &&
                !row.optBoolean("paused")
            ) {
                OrbStage02TraceFrameSample(
                    row.optString("frameTraceId"),
                    row.optLong("stageElapsedMs"),
                )
            } else null
        }
        val checkpointTargets = nearestOrbWakeCheckpointTargets(productionSamples)
        activeRows.map { (encoded, row) ->
            val targets = checkpointTargets[row.optString("frameTraceId")]
            if (targets.isNullOrEmpty()) encoded else {
                val targetArray = JSONArray()
                targets.forEach(targetArray::put)
                row.put("nearestProductionStage02CheckpointMs", targetArray).toString()
            }
        }.sortedBy { JSONObject(it).optLong("timestampNs") }
            .joinToString("\n", postfix = if (activeRows.isEmpty()) "" else "\n")
    }

    private fun productionStage02FrameSamplesLocked(): List<OrbStage02TraceFrameSample> =
        productionStage02Events.snapshot().mapNotNull { encoded ->
            val row = JSONObject(encoded)
            if (row.optString("eventType") == "ORB_SCENE_FRAME") {
                OrbStage02TraceFrameSample(
                    row.optString("frameTraceId"),
                    row.optLong("stageElapsedMs"),
                )
            } else null
        }

    fun productionStage02FrameCount(): Int = synchronized(lock) {
        productionStage02FrameSamplesLocked().size
    }

    fun manualStage02FrameCount(): Int = synchronized(lock) {
        manualStage02Events.snapshot().count { JSONObject(it).optString("eventType") == "ORB_SCENE_FRAME" }
    }

    fun retentionDiagnosticsJson(): JSONObject = synchronized(lock) {
        val productionSamples = productionStage02FrameSamplesLocked()
        val checkpointTargets = nearestOrbWakeCheckpointTargets(productionSamples)
        val byFrame = productionSamples.associateBy { it.frameTraceId }
        val assignments = JSONArray()
        checkpointTargets.forEach { (frameId, targets) ->
            val sample = byFrame[frameId] ?: return@forEach
            targets.forEach { targetMs ->
                assignments.put(JSONObject()
                    .put("targetStageElapsedMs", targetMs)
                    .put("actualStageElapsedMs", sample.stageElapsedMs)
                    .put("absoluteDeltaMs", abs(sample.stageElapsedMs - targetMs))
                    .put("frameTraceId", frameId)
                    .put("dataSource", "RUNTIME_STATE"))
            }
        }
        JSONObject()
            .put("activeTraceId", traceId)
            .put("productionStage02EventCount", productionStage02Events.size)
            .put("productionStage02FrameCount", productionSamples.size)
            .put("productionStage02DroppedEventCount", productionStage02Events.droppedCount)
            .put("productionStage02CheckpointSource", "NEAREST_RETAINED_REAL_AUTO_ORB_WAKE_FRAME")
            .put("productionStage02CheckpointAssignments", assignments)
            .put("manualStage02EventCount", manualStage02Events.size)
            .put("manualStage02FrameCount", manualStage02Events.snapshot()
                .count { JSONObject(it).optString("eventType") == "ORB_SCENE_FRAME" })
            .put("manualStage02DroppedEventCount", manualStage02Events.droppedCount)
            .put("manualPausedDuplicateFrameCount", manualPausedDuplicateFrameCount)
            .put("manualPausedDuplicateEventCount", manualPausedDuplicateEventCount)
            .put("stage02BufferCapacityPerPlaybackMode", MAX_STAGE02_EVENTS)
    }

    fun droppedEventCount(): Long = synchronized(lock) { droppedEvents }

    fun activeTraceId(): String = synchronized(lock) { traceId }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
