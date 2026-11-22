package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneRunState
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionSessionEvidenceRecorderTest {
    @Test fun processingAndResultEvidenceRequireTheSameSessionAndAttempt() {
        val identity = RecognitionEvidenceIdentity("session-1", "attempt-1", "image-1", "photo-sha")
        val processingIdentity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", "photo-sha", 2L)
        val resultIdentity = processingIdentity.copy()
        val staleAttempt = resultIdentity.copy(attemptId = "attempt-older")

        assertTrue(identity.matches(processingIdentity))
        assertTrue(identity.matches(resultIdentity))
        assertFalse(identity.matches(staleAttempt))
    }

    @Test fun motionScreenshotsStopWhileModelGateWaitsOrSceneIsPaused() {
        assertTrue(
            RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
                paused = false,
                runState = SceneRunState.RUNNING,
                waitingReason = null,
                stage = RecognitionSceneStage.ORB_RELEASE,
                stageProgress = .5f,
            ),
        )
        assertFalse(
            RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
                paused = false,
                runState = SceneRunState.WAITING,
                waitingReason = "WAITING_FOR_MASK",
                stage = RecognitionSceneStage.CONTOUR_A,
            ),
        )
        assertFalse(
            RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
                paused = true,
                runState = SceneRunState.PAUSED,
                waitingReason = null,
                stage = RecognitionSceneStage.ORB_RELEASE,
            ),
        )
    }

    @Test fun requiredLogsEntriesAndResultStableCaptureFailClosed() {
        val logs = RecognitionEvidenceIntegrityPolicy.REQUIRED_NON_EMPTY_LOGS +
            setOf("recognition_events.jsonl", "stage_timeline.jsonl")
        val stages = listOf(RecognitionEvidenceStageSnapshot("ORB_RELEASE", "COMPLETED"))
        val stageEntry = RecognitionEvidenceCaptureSnapshot("ORB_RELEASE", "STAGE_ENTRY", "SUCCESS")
        val resultStable = RecognitionEvidenceCaptureSnapshot("RESIDUE_SETTLE", "RESULT_SCENE_STABLE", "SUCCESS")

        val complete = RecognitionEvidenceIntegrityPolicy.incompleteReasons(
            hasEventLog = true,
            hasStageLog = true,
            nonEmptyFileNames = logs,
            stageStatuses = stages,
            captures = listOf(stageEntry, resultStable),
            screenshotFailureCount = 0,
            droppedCriticalEventCount = 0L,
            droppedSampleEventCount = 0L,
            writeFailures = 0L,
            queueIncompleteReasons = emptyList(),
        )
        assertTrue(complete.isEmpty())

        val missingStableAndSampleDrop = RecognitionEvidenceIntegrityPolicy.incompleteReasons(
            hasEventLog = true,
            hasStageLog = true,
            nonEmptyFileNames = logs - "model_inference.jsonl",
            stageStatuses = stages,
            captures = listOf(stageEntry),
            screenshotFailureCount = 0,
            droppedCriticalEventCount = 0L,
            droppedSampleEventCount = 1L,
            writeFailures = 0L,
            queueIncompleteReasons = emptyList(),
        )
        assertTrue(missingStableAndSampleDrop.contains("RESULT_SCENE_STABLE_CAPTURE_MISSING"))
        assertTrue(missingStableAndSampleDrop.contains("REQUIRED_LOG_EMPTY:model_inference.jsonl"))
        assertTrue(missingStableAndSampleDrop.contains("SAMPLE_EVENTS_DROPPED:1"))
    }

    @Test fun frozen26KfPolicyCapturesOnlyPresentedSemanticCheckpoints() {
        assertEquals(26, RecognitionEvidenceCapturePolicy.checkpointIds.size)
        assertEquals(
            listOf("KF02"),
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(RecognitionSceneStage.PHOTO_STABLE, progress = 0f, complete = false),
            ).map { it.id },
        )
        assertEquals(
            listOf("KF06"),
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(RecognitionSceneStage.PERIMETER_ARRIVAL, .1f, false, stageElapsedMs = 20L),
            ).map { it.id },
        )
        assertEquals(
            listOf("KF07"),
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(RecognitionSceneStage.PERIMETER_ARRIVAL, .5f, false, stageElapsedMs = 62L),
            ).map { it.id },
        )
        val blocked = frame(
            stage = RecognitionSceneStage.FISH_GOLD_PEAK,
            progress = 1f,
            complete = false,
        )
        assertTrue(RecognitionEvidenceCapturePolicy.checkpointsPresented(blocked).isEmpty())
        assertTrue(RecognitionEvidenceCapturePolicy.checkpointsPresented(blocked).isEmpty())

        val rendered = frame(
            stage = RecognitionSceneStage.FISH_GOLD_PEAK,
            progress = 1f,
            complete = true,
        )
        val first = RecognitionEvidenceCapturePolicy.checkpointsPresented(rendered)
        val repeated = RecognitionEvidenceCapturePolicy.checkpointsPresented(rendered)
        assertEquals(listOf("KF13"), first.map { it.id })
        assertEquals(first.map { it.id }, repeated.map { it.id })
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, first.single().stage)
        assertTrue(RecognitionEvidenceCapturePolicy.shouldQueueCheckpoint("NOT_REACHED"))
        assertFalse(RecognitionEvidenceCapturePolicy.shouldQueueCheckpoint("CAPTURE_REQUESTED"))
        assertEquals(1, RecognitionEvidenceCapturePolicy.MAX_UNFINISHED_PIXEL_WRITES)
        assertTrue(RecognitionEvidenceCapturePolicy.isFirstBlockedFrame(blocked, alreadyCaptured = false))
        assertFalse(RecognitionEvidenceCapturePolicy.isFirstBlockedFrame(blocked, alreadyCaptured = true))

        assertEquals(
            listOf("KF15"),
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(RecognitionSceneStage.ORB_TO_NAME, 0f, false, taskEnergyMerged = true),
            ).map { it.id },
        )
        assertTrue(
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(
                    RecognitionSceneStage.RESIDUE_SETTLE,
                    .8f,
                    false,
                    resultHandoffSubstage = ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
                    resultInfoArrivalProgress = .8f,
                ),
            ).isEmpty(),
        )
        assertEquals(
            listOf("KF23"),
            RecognitionEvidenceCapturePolicy.checkpointsPresented(
                frame(
                    RecognitionSceneStage.RESIDUE_SETTLE,
                    1f,
                    false,
                    resultHandoffSubstage = ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
                    resultInfoArrivalProgress = 1f,
                ),
            ).map { it.id },
        )
    }

    @Test fun reachedCheckpointWithoutPresentedEventIsMissingButUnreachedStageStaysUnreached() {
        val pending = RecognitionEvidenceCheckpointSnapshot("KF14", "ORB_RETURN", "NOT_REACHED")
        val blocked = RecognitionEvidenceCapturePolicy.checkpointStatusAfterExport(
            pending,
            listOf(RecognitionEvidenceStageSnapshot("ORB_RETURN", "BLOCKED")),
        )
        val notReached = RecognitionEvidenceCapturePolicy.checkpointStatusAfterExport(
            pending,
            listOf(RecognitionEvidenceStageSnapshot("ORB_RETURN", "NOT_REACHED")),
        )
        val optionalSaveNotTapped = RecognitionEvidenceCapturePolicy.checkpointStatusAfterExport(
            RecognitionEvidenceCheckpointSnapshot("KF25", "RESIDUE_SETTLE", "NOT_REACHED"),
            listOf(RecognitionEvidenceStageSnapshot("RESIDUE_SETTLE", "COMPLETED")),
        )
        assertEquals("MISSING", blocked.status)
        assertEquals("CHECKPOINT_EVENT_OR_PRESENTED_FRAME_NOT_OBSERVED", blocked.reason)
        assertEquals("NOT_REACHED", notReached.status)
        assertEquals("NOT_REACHED", optionalSaveNotTapped.status)
    }

    @Test fun standardCheckpointAndMotionEncodingPreserveFullResolutionPolicy() {
        assertEquals(
            RecognitionEvidenceScreenshotEncoding("WEBP", 92, "image/webp"),
            RecognitionEvidenceCapturePolicy.screenshotEncoding("VISUAL_CHECKPOINT", lossless = false),
        )
        assertEquals(
            RecognitionEvidenceScreenshotEncoding("WEBP", 90, "image/webp"),
            RecognitionEvidenceCapturePolicy.screenshotEncoding("MOTION_SAMPLE", lossless = false),
        )
        assertEquals(
            RecognitionEvidenceScreenshotEncoding("PNG", null, "image/png"),
            RecognitionEvidenceCapturePolicy.screenshotEncoding("GATE_BLOCKED", lossless = false),
        )
        assertTrue(
            RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
                paused = false,
                runState = SceneRunState.RUNNING,
                waitingReason = null,
                stage = RecognitionSceneStage.ORB_RETURN,
                stageProgress = .5f,
            ),
        )
        assertFalse(
            RecognitionEvidenceCapturePolicy.shouldCaptureMotion(
                paused = false,
                runState = SceneRunState.RUNNING,
                waitingReason = null,
                stage = RecognitionSceneStage.ORB_RETURN,
                stageProgress = 1f,
            ),
        )
    }

    private fun frame(
        stage: RecognitionSceneStage,
        progress: Float,
        complete: Boolean,
        resultHandoffSubstage: ResultHandoffSubstage? = null,
        resultInfoArrivalProgress: Float = 0f,
        taskEnergyMerged: Boolean = false,
        stageElapsedMs: Long = 1_000L,
    ) = com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator.frameForStage(
        identity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", "photo-sha", 1L),
        sceneElapsedMs = 4_000L,
        opticalReferenceElapsedMs = 4_000L,
        geometryVersion = 1L,
        stage = stage,
        stageElapsedMs = stageElapsedMs,
        stageProgress = progress,
        modelGates = com.yujian.ai.ui.waterlight.core.RecognitionSceneModelGates(
            runState = SceneRunState.RUNNING,
        ),
        eventSequence = 7L,
        monotonicTimestampMs = 4_000L,
        stageEventComplete = complete,
        resultHandoffSubstage = resultHandoffSubstage,
        resultInfoArrivalProgress = resultInfoArrivalProgress,
        taskEnergyMerged = taskEnergyMerged,
    )
}
