package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneRunState
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage

internal data class RecognitionEvidenceStageSnapshot(val stage: String, val status: String)

internal data class RecognitionEvidenceCaptureSnapshot(
    val stage: String,
    val captureType: String,
    val pixelCopyStatus: String,
    val checkpointId: String? = null,
)

internal data class RecognitionEvidenceCheckpointSignal(
    val id: String,
    val stage: RecognitionSceneStage,
    val eventSequence: Long,
)

internal data class RecognitionEvidenceCheckpointSnapshot(
    val id: String,
    val stage: String,
    val status: String,
    val reason: String? = null,
    val eventSequence: Long? = null,
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
                    it.pixelCopyStatus == "SUCCESS" &&
                        (it.captureType == "RESULT_SCENE_STABLE" || it.checkpointId == "KF24")
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
    const val MAX_UNFINISHED_PIXEL_WRITES = 1
    /** Frozen KF00–KF25 ids from docs/visual/v3/26kf/26KF_RUNTIME_TRACE_MATRIX_V2.json. */
    val checkpointIds: List<String> = (0..25).map { "KF${it.toString().padStart(2, '0')}" }

    private val checkpointStages = mapOf(
        "KF02" to RecognitionSceneStage.PHOTO_STABLE,
        "KF03" to RecognitionSceneStage.ORB_WAKE,
        "KF04" to RecognitionSceneStage.ORB_RELEASE,
        "KF05" to RecognitionSceneStage.ORB_RELEASE,
        "KF06" to RecognitionSceneStage.PERIMETER_ARRIVAL,
        "KF07" to RecognitionSceneStage.PERIMETER_ARRIVAL,
        "KF08" to RecognitionSceneStage.PATH_TO_FISH,
        "KF09" to RecognitionSceneStage.PATH_TO_FISH,
        "KF10" to RecognitionSceneStage.CONTOUR_A,
        "KF11" to RecognitionSceneStage.CONTOUR_B,
        "KF12" to RecognitionSceneStage.CONTOUR_C,
        "KF13" to RecognitionSceneStage.FISH_GOLD_PEAK,
        "KF14" to RecognitionSceneStage.ORB_RETURN,
        "KF15" to RecognitionSceneStage.ORB_TO_NAME,
        "KF16" to RecognitionSceneStage.ORB_TO_NAME,
        "KF17" to RecognitionSceneStage.ORB_TO_NAME,
        "KF18" to RecognitionSceneStage.SPECIES_NAME_REVEAL,
        "KF19" to RecognitionSceneStage.SPECIES_NAME_REVEAL,
        "KF20" to RecognitionSceneStage.SPECIES_NAME_REVEAL,
        "KF21" to RecognitionSceneStage.RESIDUE_SETTLE,
        "KF22" to RecognitionSceneStage.RESIDUE_SETTLE,
        "KF23" to RecognitionSceneStage.RESIDUE_SETTLE,
        "KF24" to RecognitionSceneStage.RESIDUE_SETTLE,
        "KF25" to RecognitionSceneStage.RESIDUE_SETTLE,
    )

    /**
     * Returns only semantic checkpoints whose visual/event contract is present in the
     * already-drawn scene frame. A repeated terminal frame resolves to the same KF id.
     */
    fun checkpointsPresented(frame: RecognitionSceneFrame): List<RecognitionEvidenceCheckpointSignal> = buildList {
        fun emit(id: String) {
            this.add(RecognitionEvidenceCheckpointSignal(id, checkpointStage(id) ?: frame.stage, frame.eventSequence))
        }
        when (frame.stage) {
            // PHOTO_STABLE is the clock anchor; capture its first measured, actually drawn photo frame.
            RecognitionSceneStage.PHOTO_STABLE -> if (frame.geometryVersion > 0L) emit("KF02")
            RecognitionSceneStage.ORB_WAKE -> if (frame.stageEventComplete) emit("KF03")
            RecognitionSceneStage.ORB_RELEASE -> {
                if (frame.stageElapsedMs <= CHECKPOINT_ENTRY_WINDOW_MS) emit("KF04")
                if (frame.stageEventComplete) emit("KF05")
            }
            RecognitionSceneStage.PERIMETER_ARRIVAL -> {
                if (frame.stageElapsedMs <= CHECKPOINT_ENTRY_WINDOW_MS) emit("KF06")
                if (frame.stageProgress in ARRIVAL_COVERAGE_PROGRESS) emit("KF07")
            }
            RecognitionSceneStage.PATH_TO_FISH -> {
                if (frame.stageElapsedMs <= CHECKPOINT_ENTRY_WINDOW_MS) emit("KF08")
                if (frame.stageEventComplete) emit("KF09")
            }
            RecognitionSceneStage.CONTOUR_A -> if (frame.stageEventComplete) emit("KF10")
            RecognitionSceneStage.CONTOUR_B -> if (frame.stageEventComplete) emit("KF11")
            RecognitionSceneStage.CONTOUR_C -> if (frame.stageEventComplete) emit("KF12")
            RecognitionSceneStage.FISH_GOLD_PEAK -> if (frame.stageEventComplete) emit("KF13")
            RecognitionSceneStage.ORB_RETURN -> if (frame.stageEventComplete) emit("KF14")
            RecognitionSceneStage.ORB_TO_NAME -> {
                if (frame.stageElapsedMs <= CHECKPOINT_ENTRY_WINDOW_MS && frame.taskEnergyMerged) emit("KF15")
                if (frame.stageElapsedMs in ORB_STRETCH_CHECKPOINT_MS) emit("KF16")
                if (frame.stageEventComplete) emit("KF17")
            }
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> {
                if (frame.stageElapsedMs <= CHECKPOINT_ENTRY_WINDOW_MS) emit("KF18")
                if (frame.stageEventComplete) emit("KF19")
                if (frame.resultHandoffSubstage == ResultHandoffSubstage.NAME_STABLE) emit("KF20")
            }
            RecognitionSceneStage.RESIDUE_SETTLE -> {
                when (frame.resultHandoffSubstage) {
                    ResultHandoffSubstage.PHOTO_SETTLE -> emit("KF21")
                    ResultHandoffSubstage.RESULT_SCENE_HANDOFF -> emit("KF22")
                    ResultHandoffSubstage.RESULT_INFO_ARRIVAL -> {
                        if (frame.resultInfoArrivalProgress >= .999f) emit("KF23")
                    }
                    ResultHandoffSubstage.RESULT_SCENE_STABLE -> if (frame.resultSceneStable) emit("KF24")
                    else -> Unit
                }
            }
        }
    }

    fun checkpointStage(id: String): RecognitionSceneStage? = checkpointStages[id]

    fun shouldQueueCheckpoint(status: String): Boolean = status == "NOT_REACHED"

    fun isFirstBlockedFrame(frame: RecognitionSceneFrame, alreadyCaptured: Boolean): Boolean =
        !alreadyCaptured && frame.stageProgress >= .999f && !frame.stageEventComplete

    fun checkpointStatusAfterExport(
        checkpoint: RecognitionEvidenceCheckpointSnapshot,
        stageStatuses: List<RecognitionEvidenceStageSnapshot>,
    ): RecognitionEvidenceCheckpointSnapshot {
        if (checkpoint.status != "NOT_REACHED") return checkpoint
        if (checkpoint.id == "KF25") return checkpoint
        val expected = checkpointStage(checkpoint.id) ?: return checkpoint
        val reached = stageStatuses.any {
            it.stage == expected.name && it.status in setOf("ENTERED", "COMPLETED", "BLOCKED")
        }
        return if (reached) checkpoint.copy(
            status = "MISSING",
            reason = "CHECKPOINT_EVENT_OR_PRESENTED_FRAME_NOT_OBSERVED",
        ) else checkpoint
    }

    fun screenshotEncoding(captureType: String, lossless: Boolean): RecognitionEvidenceScreenshotEncoding = when {
        lossless || captureType == "GATE_BLOCKED" -> RecognitionEvidenceScreenshotEncoding("PNG", null, "image/png")
        captureType == "MOTION_SAMPLE" -> RecognitionEvidenceScreenshotEncoding("WEBP", 90, "image/webp")
        else -> RecognitionEvidenceScreenshotEncoding("WEBP", 92, "image/webp")
    }

    fun sameSceneIdentityAndGeometry(
        requestedIdentity: RecognitionSceneIdentity,
        completedIdentity: RecognitionSceneIdentity?,
        requestedGeometryVersion: Long,
        completedGeometryVersion: Long?,
    ): Boolean = completedIdentity == requestedIdentity && completedGeometryVersion == requestedGeometryVersion

    fun shouldCaptureMotion(
        paused: Boolean,
        runState: SceneRunState,
        waitingReason: String?,
        stage: RecognitionSceneStage,
        stageProgress: Float = 0f,
    ): Boolean = !paused && runState == SceneRunState.RUNNING && waitingReason == null &&
        stageProgress in MOTION_CHECKPOINT_PROGRESS &&
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

    private const val CHECKPOINT_ENTRY_WINDOW_MS = 48L
    private val ARRIVAL_COVERAGE_PROGRESS = .45f..0.55f
    private val ORB_STRETCH_CHECKPOINT_MS = 300L..430L
    private val MOTION_CHECKPOINT_PROGRESS = .45f..0.55f
}

internal data class RecognitionEvidenceScreenshotEncoding(
    val format: String,
    val quality: Int?,
    val mimeType: String,
)

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
        checkpoints: List<RecognitionEvidenceCheckpointSnapshot> = emptyList(),
    ): List<String> = buildList {
        if (!hasEventLog) add("RECOGNITION_EVENTS_MISSING")
        if (!hasStageLog) add("STAGE_TIMELINE_MISSING")
        REQUIRED_NON_EMPTY_LOGS.filterNot(nonEmptyFileNames::contains).forEach { add("REQUIRED_LOG_EMPTY:$it") }

        val successfulStages = captures.asSequence()
            .filter { it.pixelCopyStatus == "SUCCESS" }
            .map(RecognitionEvidenceCaptureSnapshot::stage)
            .toSet()
        stageStatuses.asSequence()
            .filter { it.status in setOf("ENTERED", "COMPLETED", "BLOCKED") }
            .map(RecognitionEvidenceStageSnapshot::stage)
            .filterNot(successfulStages::contains)
            .distinct()
            .forEach { add("STAGE_VISUAL_CAPTURE_MISSING:$it") }

        if (captures.none { it.captureType == "RESULT_SCENE_STABLE" && it.pixelCopyStatus == "SUCCESS" }) {
            add("RESULT_SCENE_STABLE_CAPTURE_MISSING")
        }
        if (screenshotFailureCount > 0) add("SCREENSHOT_FAILURES:$screenshotFailureCount")
        if (droppedCriticalEventCount > 0) add("CRITICAL_EVENTS_DROPPED:$droppedCriticalEventCount")
        if (droppedSampleEventCount > 0) add("SAMPLE_EVENTS_DROPPED:$droppedSampleEventCount")
        if (writeFailures > 0) add("EVENT_WRITE_FAILURES:$writeFailures")
        queueIncompleteReasons.forEach { add("QUEUE_INCOMPLETE:$it") }
        checkpoints.filter { it.status != "CAPTURED" }.forEach { checkpoint ->
            add("CHECKPOINT_${checkpoint.status}:${checkpoint.id}${checkpoint.reason?.let { ":$it" }.orEmpty()}")
        }
    }.distinct()
}
