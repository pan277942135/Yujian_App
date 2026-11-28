package com.yujian.ai.ui.recognition.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecognitionSessionEvidenceRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun crashProcessDeathAndSameProcessRecreationPersistTheirClassificationBasis() {
        val root = File(context.cacheDir, "stage2-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val crashStore = store(root, "process-crash")
            val crashedAttempt = crashStore.begin(identity("attempt-crash"))
            File(crashedAttempt.directory, "logs/recognition_events.jsonl").writeText(
                "{\"evidenceSequence\":7,\"eventType\":\"MODEL_WAIT_HEARTBEAT\"}\n",
            )
            RecognitionEvidenceCrashHandler.writeCrashSidecarForDiagnostics(
                directory = crashedAttempt.directory,
                thread = Thread.currentThread(),
                failure = IllegalStateException("INJECTED_UNCAUGHT_EXCEPTION"),
                wallClockMs = { 10_000L },
                uptimeClockMs = { 500L },
                processId = 123,
            )
            val crashRecovery = store(root, "process-next").recoverUnfinishedAttempts()
                .single { it.attemptIdHash == sha256("attempt-crash") }
            assertEquals("CRASHED", crashRecovery.terminalState)
            val crashReport = JSONObject(File(crashedAttempt.directory, "recovery_report.json").readText())
            assertEquals("UNCAUGHT_EXCEPTION_SIDECAR_PRESENT", crashReport.getString("classificationBasis"))
            assertEquals(true, crashReport.getBoolean("crashSidecarPresent"))
            assertEquals(7L, crashReport.getLong("lastPersistedEvidenceSequence"))
            assertEquals("NOT_REACHED", crashReport.getString("resultEvidenceStatus"))

            val processStore = store(root, "process-before-kill")
            val processAttempt = processStore.begin(identity("attempt-process-died"))
            val processRecovery = store(root, "process-after-restart").recoverUnfinishedAttempts()
                .single { it.attemptIdHash == sha256("attempt-process-died") }
            assertEquals("PROCESS_DIED", processRecovery.terminalState)
            val processReport = JSONObject(File(processAttempt.directory, "recovery_report.json").readText())
            assertEquals("PERSISTED_PROCESS_INSTANCE_CHANGED", processReport.getString("classificationBasis"))
            assertFalse(processReport.getString("previousProcessInstanceId") == processReport.getString("currentProcessInstanceId"))
            assertEquals("NOT_REACHED", processReport.getString("resultEvidenceStatus"))

            val interruptedStore = store(root, "same-process-instance")
            val interruptedAttempt = interruptedStore.begin(identity("attempt-interrupted"))
            val interrupted = store(root, "same-process-instance").recoverUnfinishedAttempts()
                .single { it.attemptIdHash == sha256("attempt-interrupted") }
            assertEquals("INTERRUPTED", interrupted.terminalState)
            val interruptedReport = JSONObject(File(interruptedAttempt.directory, "recovery_report.json").readText())
            assertEquals("SAME_PROCESS_INSTANCE_WITHOUT_TERMINAL_MARKER", interruptedReport.getString("classificationBasis"))
            assertEquals(
                interruptedReport.getString("previousProcessInstanceId"),
                interruptedReport.getString("currentProcessInstanceId"),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun completionFailureAndCancellationPersistExplicitTerminalStatesWithoutInventedResultEvidence() {
        val root = File(context.cacheDir, "stage2-terminal-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store = store(root, "process-terminal")
            val cases = listOf(
                "normal" to RecognitionAttemptTerminalState.RESULT_STABLE,
                "failed" to RecognitionAttemptTerminalState.RECOGNITION_FAILED,
                "cancelled" to RecognitionAttemptTerminalState.CANCELLED,
            )
            cases.forEach { (name, state) ->
                val attempt = store.begin(identity("attempt-$name"))
                store.finish(attempt, state, reason = "FAULT_INJECTION_$name", resultEvidenceAvailable = false)
                val metadata = JSONObject(File(attempt.directory, "attempt.json").readText())
                assertEquals(state.name, metadata.getString("terminalState"))
                assertNotNull(metadata.get("resultEvidenceStatus"))
                if (state == RecognitionAttemptTerminalState.RESULT_STABLE) {
                    assertEquals("RESULT_EVIDENCE_NOT_AVAILABLE", metadata.getString("resultEvidenceStatus"))
                } else {
                    assertEquals("NOT_REACHED", metadata.getString("resultEvidenceStatus"))
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun persistedCrashTerminalMarkerRemainsClassifiableWhenSidecarWasNotRecoverable() {
        val root = File(context.cacheDir, "stage2-crash-marker-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val attempt = store(root, "process-crash-marker").begin(identity("attempt-crash-marker"))
            val attemptFile = File(attempt.directory, "attempt.json")
            val marker = JSONObject(attemptFile.readText())
                .put("terminalState", "CRASHED")
                .put("terminalReason", "PERSISTED_CRASH_TERMINAL_MARKER_INJECTION")
            attemptFile.writeText(marker.toString(2))

            val recovered = store(root, "process-after-crash-marker").recoverUnfinishedAttempts()
                .single { it.attemptIdHash == sha256("attempt-crash-marker") }
            val recoveryReport = JSONObject(File(attempt.directory, "recovery_report.json").readText())
            assertEquals("CRASHED", recovered.terminalState)
            assertEquals("PERSISTED_CRASH_TERMINAL_MARKER", recoveryReport.getString("classificationBasis"))
            assertEquals(false, recoveryReport.getBoolean("crashSidecarPresent"))
            assertEquals("NOT_REACHED", recoveryReport.getString("resultEvidenceStatus"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun store(root: File, processId: String) = RecognitionSessionEvidenceStore(root, processId) { 1_000L }

    private fun identity(attemptId: String) = RecognitionEvidenceIdentity(
        sessionId = "session-$attemptId",
        attemptId = attemptId,
        imageId = "photo-$attemptId",
        photoSha256 = sha256("photo-bytes-$attemptId"),
        photoWidthPx = 640,
        photoHeightPx = 480,
        orientationDegrees = 0,
    )
}
