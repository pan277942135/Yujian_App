package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID

/** Bounded, privacy-safe Debug trace for the Orb render pipeline. */
internal object OrbStage02RenderTrace {
    private const val SCHEMA_VERSION = 1
    private const val MAX_EVENTS = 4_096
    private const val MAX_RATE_LIMIT_KEYS = 256
    private val lock = Any()
    private val events = ArrayDeque<String>(MAX_EVENTS)
    private val recentIssueLogsNs = LinkedHashMap<String, Long>()
    private val captureMarkers = HashMap<String, CompletableDeferred<Unit>>()
    private val captureStates = HashMap<String, String>()
    private val capturePathEntriesById = HashMap<String, LinkedHashMap<String, String>>()
    private var droppedEvents = 0L
    private var overflowMarkerRecorded = false
    private var traceId = ""
    private var frameSequence = 0L
    private var currentContext: Map<String, Any?> = emptyMap()
    @Volatile private var currentFrameTraceId = ""

    fun beginFrame(frame: RecognitionSceneFrame, profile: OrbStage02VisualProfile): String {
        if (!BuildConfig.DEBUG) return ""
        val nextTraceId = digest(
            "${frame.identity.sessionId}|${frame.identity.attemptId}|${frame.identity.imageId}",
        ).take(20)
        synchronized(lock) {
            if (traceId != nextTraceId) {
                traceId = nextTraceId
                frameSequence = 0L
                droppedEvents = 0L
                overflowMarkerRecorded = false
                recentIssueLogsNs.clear()
            }
            frameSequence += 1L
            currentFrameTraceId = "$traceId-$frameSequence"
            currentContext = mapOf(
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
        }
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
        return currentFrameTraceId
    }

    fun record(
        eventType: String,
        dataSource: String,
        captureId: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (!BuildConfig.DEBUG) return
        val context = synchronized(lock) { currentContext }
        val row = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("traceId", synchronized(lock) { traceId })
            .put("frameTraceId", currentFrameTraceId)
            .put("captureId", captureId)
            .put("timestampNs", System.nanoTime())
            .put("eventType", eventType)
            .put("dataSource", dataSource)
        context.forEach { (key, value) -> row.put(key, value) }
        fields.forEach { (key, value) -> row.put(key, value) }
        synchronized(lock) {
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
            events.addLast(row.toString())
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

    /** Export only the active Attempt; older Attempt events must never leak into a later ZIP. */
    fun snapshotJsonLines(): String = synchronized(lock) {
        val activeTrace = traceId
        val activeEvents = events.filter { JSONObject(it).optString("traceId") == activeTrace }
        activeEvents.joinToString("\n", postfix = if (activeEvents.isEmpty()) "" else "\n")
    }

    fun productionStage02FrameCount(): Int = synchronized(lock) {
        val activeTrace = traceId
        events.count { encoded ->
            val event = JSONObject(encoded)
            event.optString("traceId") == activeTrace &&
                event.optString("eventType") == "ORB_SCENE_FRAME" &&
                event.optString("stage") == "ORB_WAKE" &&
                event.optString("playbackMode") == "AUTO" &&
                !event.optBoolean("paused")
        }
    }

    fun droppedEventCount(): Long = synchronized(lock) { droppedEvents }

    fun activeTraceId(): String = synchronized(lock) { traceId }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
