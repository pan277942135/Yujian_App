package com.yujian.ai.ui.recognition.diagnostics

/** Version identifiers carried by every exported evidence package. */
object RecognitionEvidenceSchema {
    const val EVIDENCE_SCHEMA_VERSION = 2
    const val RUNTIME_CONTRACT_VERSION = "yujian-runtime-contract-v1"
    const val DIAGNOSTIC_BUILD_VERSION = "stage2-foundation-v1"
    const val VISUAL_SPEC_NOT_FROZEN = "NOT_FROZEN"
}

enum class RecognitionAttemptTerminalState(val isTerminal: Boolean) {
    RUNNING(false),
    RESULT_STABLE(true),
    RECOGNITION_FAILED(true),
    INTERRUPTED(true),
    CRASHED(true),
    PROCESS_DIED(true),
    SURFACE_LOST(true),
    UNKNOWN_TERMINATION(true),
}

internal object RecognitionEvidenceRecoveryPolicy {
    fun classify(
        previousTerminalState: String,
        crashSidecarPresent: Boolean,
        previousProcessInstanceId: String?,
        currentProcessInstanceId: String,
    ): RecognitionAttemptTerminalState? = when {
        previousTerminalState == RecognitionAttemptTerminalState.CRASHED.name -> RecognitionAttemptTerminalState.CRASHED
        previousTerminalState !in setOf("", RecognitionAttemptTerminalState.RUNNING.name) -> null
        crashSidecarPresent -> RecognitionAttemptTerminalState.CRASHED
        previousTerminalState.isBlank() -> RecognitionAttemptTerminalState.UNKNOWN_TERMINATION
        previousProcessInstanceId == currentProcessInstanceId -> RecognitionAttemptTerminalState.INTERRUPTED
        previousProcessInstanceId.isNullOrBlank() || previousProcessInstanceId == "NOT_RECORDED" ->
            RecognitionAttemptTerminalState.UNKNOWN_TERMINATION
        else -> RecognitionAttemptTerminalState.PROCESS_DIED
    }

    fun reasonFor(terminalState: RecognitionAttemptTerminalState): String = when (terminalState) {
        RecognitionAttemptTerminalState.CRASHED -> "UNCAUGHT_EXCEPTION_REPORT_PRESENT"
        RecognitionAttemptTerminalState.INTERRUPTED -> "SAME_PROCESS_RECREATION_WITHOUT_TERMINAL_MARKER"
        RecognitionAttemptTerminalState.UNKNOWN_TERMINATION -> "TERMINAL_OR_PROCESS_METADATA_UNAVAILABLE"
        RecognitionAttemptTerminalState.PROCESS_DIED -> "PROCESS_RESTART_WITHOUT_TERMINAL_MARKER"
        else -> "RECOVERY_CLASSIFIED:${terminalState.name}"
    }

    fun resultEvidenceStatus(
        terminalState: RecognitionAttemptTerminalState,
        resultEvidenceAvailable: Boolean = false,
    ): String = when {
        terminalState == RecognitionAttemptTerminalState.RESULT_STABLE && resultEvidenceAvailable -> "AVAILABLE"
        terminalState == RecognitionAttemptTerminalState.RESULT_STABLE -> "RESULT_EVIDENCE_NOT_AVAILABLE"
        else -> "NOT_REACHED"
    }
}
