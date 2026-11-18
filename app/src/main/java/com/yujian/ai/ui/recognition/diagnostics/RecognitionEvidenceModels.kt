package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneModelGates
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageEvent
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageEventAction
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStageTraceEntry
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.SceneRunState
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class RecognitionEvidenceIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoWidthPx: Int = 0,
    val photoHeightPx: Int = 0,
    val orientationDegrees: Int = 0,
) {
    init {
        require(photoWidthPx >= 0 && photoHeightPx >= 0)
        require(orientationDegrees in 0..359)
    }

    fun matches(sceneIdentity: RecognitionSceneIdentity): Boolean =
        sessionId == sceneIdentity.sessionId && attemptId == sceneIdentity.attemptId &&
            imageId == sceneIdentity.imageId && photoSha256 == sceneIdentity.photoSha256
}

/** Stable, privacy-safe identity carried by every persisted Runtime and capture event. */
data class RecognitionSceneKey(
    val sessionIdHash: String,
    val attemptIdHash: String,
    val photoIdentityHash: String,
) {
    val value: String get() = "$sessionIdHash:$attemptIdHash:$photoIdentityHash"

    companion object {
        fun from(identity: RecognitionEvidenceIdentity) = RecognitionSceneKey(
            sessionIdHash = sha256(identity.sessionId),
            attemptIdHash = sha256(identity.attemptId),
            photoIdentityHash = sha256(identity.imageId),
        )
    }
}

enum class RecognitionEvidencePriority { CRITICAL, SAMPLE }

data class RecognitionEvidenceEvent(
    val fileName: String,
    val type: String,
    val payload: Map<String, Any?>,
    val priority: RecognitionEvidencePriority = RecognitionEvidencePriority.CRITICAL,
) {
    fun json(sequence: Long, timestampMs: Long): String = JSONObject()
        .put("schemaVersion", RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION)
        .put("sequence", sequence)
        .put("evidenceSequence", sequence)
        .put("timestampUptimeMs", timestampMs)
        .put("eventType", type)
        .also { row -> payload.forEach { (key, value) -> row.put(key, value) } }
        .toString()
}

data class RecognitionEvidenceQueueDiagnostics(
    val droppedCriticalEvents: Long,
    val droppedSampleEvents: Long,
    val writeFailures: Long,
    val incompleteReasons: List<String>,
    val eventCount: Long,
)

data class RecognitionEvidenceStageStatus(
    val stage: RecognitionSceneStage,
    val status: String,
    val enteredAtSceneMs: Long? = null,
    val exitedAtSceneMs: Long? = null,
    val lastWaitingReason: String? = null,
)

internal fun RecognitionSceneStageEvent.toEvidencePayload(): Map<String, Any?> = mapOf(
    "action" to action.name,
    "stage" to stage.name,
    "sceneElapsedMs" to sceneElapsedMs,
    "stageElapsedMs" to stageElapsedMs,
    "stageProgress" to stageProgress,
    "geometryVersion" to geometryVersion,
    "playbackMode" to playbackMode.name,
    "modelGates" to modelGates.toJsonObject(),
    "waitingReason" to waitingReason,
    "eventSequence" to eventSequence,
    "sessionIdHash" to sha256(sessionId),
    "attemptIdHash" to sha256(attemptId),
)

internal fun RecognitionSceneModelGates.toJsonObject(): JSONObject = JSONObject()
    .put("detector", detector.name)
    .put("classifier", classifier.name)
    .put("slimSam", slimSam.name)
    .put("mask", mask.name)
    .put("geometry", geometry.name)
    .put("runState", runState.name)
    .put("waitingReason", waitingReason)

internal fun RecognitionSceneStageTraceEntry.toJsonObject(): JSONObject = JSONObject()
    .put("stage", stage.name)
    .put("stageOrdinal", stageOrdinal)
    .put("stageStartTimestampMs", stageStartTimestampMs)
    .put("stageEndTimestampMs", stageEndTimestampMs)
    .put("stageDurationMs", stageDurationMs)
    .put("requiredDurationMs", requiredDurationMs)
    .put("gateWaitDurationMs", gateWaitDurationMs)
    .put("gateWaitReason", gateWaitReason)
    .put("localProgress", localProgress)
    .put("sessionIdHash", sha256(sessionId))
    .put("attemptIdHash", sha256(attemptId))
    .put("eventSequence", eventSequence)

internal fun SceneRunState.isWaiting(): Boolean = this == SceneRunState.WAITING

internal fun ScenePlaybackMode.isAutomatic(): Boolean = this == ScenePlaybackMode.AUTO

internal fun JSONArray.append(value: Any?) = put(value)

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
