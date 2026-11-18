package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionSessionEvidenceRecorderTest {
    @Test fun recoveryClassifiesCrashProcessDeathAndSameProcessInterruptionWithoutSuccessFallback() {
        assertEquals(
            RecognitionAttemptTerminalState.CRASHED,
            RecognitionEvidenceRecoveryPolicy.classify("RUNNING", true, "old", "new"),
        )
        assertEquals(
            RecognitionAttemptTerminalState.PROCESS_DIED,
            RecognitionEvidenceRecoveryPolicy.classify("RUNNING", false, "pid-1", "pid-2"),
        )
        assertEquals(
            RecognitionAttemptTerminalState.INTERRUPTED,
            RecognitionEvidenceRecoveryPolicy.classify("RUNNING", false, "same-process", "same-process"),
        )
        assertEquals(
            RecognitionAttemptTerminalState.UNKNOWN_TERMINATION,
            RecognitionEvidenceRecoveryPolicy.classify("", false, null, "current"),
        )
        assertEquals(
            null,
            RecognitionEvidenceRecoveryPolicy.classify("RESULT_STABLE", false, "old", "new"),
        )
        assertEquals(
            "NOT_REACHED",
            RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(RecognitionAttemptTerminalState.PROCESS_DIED),
        )
        assertEquals(
            "RESULT_EVIDENCE_NOT_AVAILABLE",
            RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(RecognitionAttemptTerminalState.RESULT_STABLE),
        )
        assertEquals(
            "AVAILABLE",
            RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(
                RecognitionAttemptTerminalState.RESULT_STABLE,
                resultEvidenceAvailable = true,
            ),
        )
    }

    @Test fun normalFailureAndCancellationHaveDistinctTerminalOutcomes() {
        assertTrue(RecognitionAttemptTerminalState.RESULT_STABLE.isTerminal)
        assertTrue(RecognitionAttemptTerminalState.RECOGNITION_FAILED.isTerminal)
        assertTrue(RecognitionAttemptTerminalState.CANCELLED.isTerminal)
        assertEquals("RESULT_EVIDENCE_NOT_AVAILABLE", RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(
            RecognitionAttemptTerminalState.RESULT_STABLE,
        ))
        assertEquals("NOT_REACHED", RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(
            RecognitionAttemptTerminalState.RECOGNITION_FAILED,
        ))
        assertEquals("NOT_REACHED", RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(
            RecognitionAttemptTerminalState.CANCELLED,
        ))
    }

    @Test fun sceneKeyIsStableAcrossEvidenceConsumersAndExcludesRawIdentity() {
        val identity = RecognitionEvidenceIdentity("session-1", "attempt-1", "image-1", "photo-sha")
        val sceneKey = RecognitionSceneKey.from(identity)
        assertEquals("${sha256("session-1")}:${sha256("attempt-1")}:${sha256("image-1")}", sceneKey.value)
        assertFalse(sceneKey.value.contains("session-1"))
        assertFalse(sceneKey.value.contains("attempt-1"))
    }

    @Test fun evidenceZipSchemaRequiresTraceAndIdentityFilesEvenWhenExplicitlyNotRecorded() {
        assertTrue(RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION >= 2)
        assertTrue(RecognitionEvidenceZipContract.requiredEntries.contains("manifest.json"))
        assertTrue(RecognitionEvidenceZipContract.requiredEntries.contains("logs/gpu_trace.jsonl"))
        assertTrue(RecognitionEvidenceZipContract.requiredEntries.contains("logs/gl_trace.jsonl"))
        assertTrue(RecognitionEvidenceZipContract.requiredEntries.contains("frames/pixelcopy_manifest.jsonl"))
        assertTrue(RecognitionEvidenceZipContract.requiredEntries.contains("attempt.json"))
        assertTrue(RecognitionEvidenceZipContract.missingEntries(RecognitionEvidenceZipContract.requiredEntries).isEmpty())
        assertTrue(RecognitionEvidenceSchema.VISUAL_SPEC_NOT_FROZEN == "NOT_FROZEN")
    }

    @Test fun resultEvidenceIsAvailableOnlyAfterResultStablePixelCopySucceeds() {
        assertEquals(
            "RESULT_EVIDENCE_NOT_AVAILABLE",
            RecognitionEvidenceResultStatusPolicy.fromCaptureSnapshots(
                listOf(RecognitionEvidenceCaptureSnapshot("RESIDUE_SETTLE", "STAGE_ENTRY", "SUCCESS")),
            ).status,
        )
        assertEquals(
            "RESULT_EVIDENCE_NOT_AVAILABLE",
            RecognitionEvidenceResultStatusPolicy.fromCaptureSnapshots(
                listOf(RecognitionEvidenceCaptureSnapshot("RESIDUE_SETTLE", "RESULT_SCENE_STABLE", "PIXELCOPY_ERROR")),
            ).status,
        )
        assertEquals(
            "AVAILABLE",
            RecognitionEvidenceResultStatusPolicy.fromCaptureSnapshots(
                listOf(RecognitionEvidenceCaptureSnapshot("RESIDUE_SETTLE", "RESULT_SCENE_STABLE", "SUCCESS")),
            ).status,
        )
    }

    @Test fun oneEvidenceSequenceStaysMonotonicAcrossCriticalAndSampleEvents() {
        val root = java.nio.file.Files.createTempDirectory("yujian-evidence-sequence").toFile()
        val sink = RecognitionEvidenceEventSink(root, clockMs = { 100L })
        try {
            assertTrue(sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "SAMPLE_1", emptyMap(), RecognitionEvidencePriority.SAMPLE)))
            assertTrue(sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "CRITICAL_2", emptyMap())))
            assertTrue(sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "SAMPLE_3", emptyMap(), RecognitionEvidencePriority.SAMPLE)))
            assertTrue(sink.flush(1_000L))
            val sequences = java.io.File(root, "logs/recognition_events.jsonl").readLines()
                .map { line -> "\\\"evidenceSequence\\\":(\\d+)".toRegex().find(line)!!.groupValues[1].toLong() }
            assertEquals(listOf(1L, 2L, 3L), sequences)
        } finally {
            sink.dispose()
            root.deleteRecursively()
        }
    }

    @Test fun rotatingEvidenceLogsPreservesPriorSegmentsInsteadOfDeletingOlderFailureHistory() {
        val root = java.nio.file.Files.createTempDirectory("yujian-evidence-rotation").toFile()
        val sink = RecognitionEvidenceEventSink(root, clockMs = { 100L })
        try {
            val payload = mapOf("diagnosticPayload" to "x".repeat(110_000))
            repeat(42) { index ->
                assertTrue(sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "EVENT_$index", payload)))
            }
            assertTrue(sink.flush(5_000L))
            val logFiles = java.io.File(root, "logs").listFiles()
                .orEmpty()
                .filter { it.name.startsWith("recognition_events.jsonl") }
            assertTrue(logFiles.size >= 2)
            assertTrue(logFiles.any { it.name.contains(".part_") })
            assertEquals(42, logFiles.sumOf { it.readLines().size })
        } finally {
            sink.dispose()
            root.deleteRecursively()
        }
    }

    @Test fun eventStoreWriteFailureMakesFlushFailAndPreservesFailureReason() {
        val root = java.nio.file.Files.createTempFile("yujian-evidence-write-failure", ".file").toFile()
        val sink = RecognitionEvidenceEventSink(root, clockMs = { 100L })
        try {
            assertTrue(sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "WRITE_FAILURE", emptyMap())))
            assertFalse(sink.flush(1_000L))
            assertEquals(1L, sink.diagnostics().writeFailures)
            assertTrue(sink.diagnostics().incompleteReasons.any { it.startsWith("EVENT_WRITE_FAILED:") })
        } finally {
            sink.dispose()
            root.delete()
        }
    }

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
