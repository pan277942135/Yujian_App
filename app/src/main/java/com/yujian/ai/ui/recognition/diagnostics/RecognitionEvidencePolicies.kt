package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneRunState

internal data class RecognitionEvidenceStageSnapshot(val stage: String, val status: String)

internal data class RecognitionEvidenceCaptureSnapshot(
    val stage: String,
    val captureType: String,
    val pixelCopyStatus: String,
)

internal data class RecognitionEvidenceResultStatusSnapshot(
    val status: String,
    val missingReason: String?,
    val resultStablePixelCopySucceeded: Boolean,
) {
    fun toJsonString(): String {
        val missingReasonJson = missingReason?.let { "\"$it\"" } ?: "null"
        return """{"status":"$status","missingReason":$missingReasonJson,"resultStablePixelCopySucceeded":$resultStablePixelCopySucceeded}"""
    }
}

internal object RecognitionEvidenceResultStatusPolicy {
    fun fromCaptureSnapshots(captures: List<RecognitionEvidenceCaptureSnapshot>): RecognitionEvidenceResultStatusSnapshot =
        fromStatus(
            if (captures.any {
                    it.captureType == "RESULT_SCENE_STABLE" && it.pixelCopyStatus == "SUCCESS"
                }
            ) "AVAILABLE" else "RESULT_EVIDENCE_NOT_AVAILABLE",
        )

    fun fromStatus(status: String?): RecognitionEvidenceResultStatusSnapshot =
        if (status == "AVAILABLE") {
            RecognitionEvidenceResultStatusSnapshot("AVAILABLE", null, true)
        } else {
            RecognitionEvidenceResultStatusSnapshot(
                "RESULT_EVIDENCE_NOT_AVAILABLE",
                "RESULT_EVIDENCE_NOT_AVAILABLE",
                false,
            )
        }
}

internal object RecognitionEvidenceCapturePolicy {
    fun sameSceneIdentityAndGeometry(
        requestedIdentity: RecognitionSceneIdentity,
        completedIdentity: RecognitionSceneIdentity?,
        requestedGeometryVersion: Long,
        completedGeometryVersion: Long?,
    ): Boolean = completedIdentity == requestedIdentity && completedGeometryVersion == requestedGeometryVersion

    fun sameSceneFrameIdentity(
        requestedIdentity: RecognitionSceneIdentity,
        completedIdentity: RecognitionSceneIdentity?,
        requestedGeometryVersion: Long,
        completedGeometryVersion: Long?,
        requestedEventSequence: Long,
        completedEventSequence: Long?,
        requestedPathId: String,
        completedPathId: String?,
    ): Boolean = sameSceneIdentityAndGeometry(
        requestedIdentity,
        completedIdentity,
        requestedGeometryVersion,
        completedGeometryVersion,
    ) && requestedEventSequence == completedEventSequence && requestedPathId == completedPathId

    fun shouldCaptureMotion(
        paused: Boolean,
        runState: SceneRunState,
        waitingReason: String?,
        stage: RecognitionSceneStage,
    ): Boolean = !paused && runState == SceneRunState.RUNNING && waitingReason == null &&
        stage != RecognitionSceneStage.PHOTO_STABLE

    fun pixelCopyStatus(
        pixelCopyStatus: String,
        requestedStage: String,
        completedStage: String,
        sameSceneIdentityAndGeometry: Boolean,
    ): String = when {
        pixelCopyStatus != "SUCCESS" -> pixelCopyStatus
        requestedStage != completedStage || !sameSceneIdentityAndGeometry -> "STAGE_IDENTITY_UNVERIFIED"
        else -> "SUCCESS"
    }
}

internal object RecognitionEvidenceIntegrityPolicy {
    val REQUIRED_NON_EMPTY_LOGS = setOf(
        "orb_render_trace.jsonl", "model_inference.jsonl", "mask_runtime.jsonl",
        "waterlight_paths.jsonl", "result_handoff.jsonl",
    )

    fun incompleteReasons(
        hasEventLog: Boolean,
        hasStageLog: Boolean,
        nonEmptyFileNames: Set<String>,
        stageStatuses: List<RecognitionEvidenceStageSnapshot>,
        captures: List<RecognitionEvidenceCaptureSnapshot>,
        screenshotFailureCount: Int,
        droppedCriticalEventCount: Long,
        droppedSampleEventCount: Long,
        writeFailures: Long,
        queueIncompleteReasons: List<String>,
    ): List<String> = buildList {
        if (!hasEventLog) add("RECOGNITION_EVENTS_MISSING")
        if (!hasStageLog) add("STAGE_TIMELINE_MISSING")
        REQUIRED_NON_EMPTY_LOGS.filterNot(nonEmptyFileNames::contains).forEach { add("REQUIRED_LOG_EMPTY:$it") }

        val successfulEntries = captures.asSequence()
            .filter { it.captureType == "STAGE_ENTRY" && it.pixelCopyStatus == "SUCCESS" }
            .map(RecognitionEvidenceCaptureSnapshot::stage)
            .toSet()
        stageStatuses.asSequence()
            .filter { it.status in setOf("ENTERED", "COMPLETED", "BLOCKED") }
            .map(RecognitionEvidenceStageSnapshot::stage)
            .filterNot(successfulEntries::contains)
            .distinct()
            .forEach { add("STAGE_ENTRY_CAPTURE_MISSING:$it") }

        if (captures.none { it.captureType == "RESULT_SCENE_STABLE" && it.pixelCopyStatus == "SUCCESS" }) {
            add("RESULT_SCENE_STABLE_CAPTURE_MISSING")
        }
        if (screenshotFailureCount > 0) add("SCREENSHOT_FAILURES:$screenshotFailureCount")
        if (droppedCriticalEventCount > 0) add("CRITICAL_EVENTS_DROPPED:$droppedCriticalEventCount")
        if (droppedSampleEventCount > 0) add("SAMPLE_EVENTS_DROPPED:$droppedSampleEventCount")
        if (writeFailures > 0) add("EVENT_WRITE_FAILURES:$writeFailures")
        queueIncompleteReasons.forEach { add("QUEUE_INCOMPLETE:$it") }
    }.distinct()
}
