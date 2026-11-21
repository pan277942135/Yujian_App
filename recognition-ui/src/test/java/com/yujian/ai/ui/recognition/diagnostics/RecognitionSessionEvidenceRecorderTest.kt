package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneRunState
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
}
