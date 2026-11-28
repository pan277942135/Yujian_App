package com.yujian.ai.ai

/** States reflect the synchronous Interpreter API. A deadline quarantines; it cannot stop native work. */
enum class RecognitionTfliteNativeState {
    CREATED,
    RUNNING,
    NATIVE_CALL_STUCK,
    SESSION_QUARANTINED,
    RESOURCE_QUARANTINED,
    NATIVE_CALL_RETURNED,
    SESSION_CLOSED,
    RESOURCE_RELEASED,
}

data class RecognitionTfliteNativeEvent(
    val inferenceId: String,
    val attemptId: String,
    val modelSessionId: String,
    val state: RecognitionTfliteNativeState,
    val monotonicTimestampMs: Long,
    val inputBufferBytes: Long = 0L,
    val outputBufferBytes: Long = 0L,
    val detail: String? = null,
)

/**
 * Serializes ownership of one TFLite Interpreter. TFLite 2.16.1 exposes no verified hard-stop
 * for a synchronous run, so deadline expiry quarantines the session and its buffers until run()
 * really returns. Session close is deferred while a native call is active.
 */
internal class RecognitionTfliteNativeRunLifecycle(
    private val modelSessionId: String,
    private val nowUptimeMs: () -> Long,
    private val onEvent: (RecognitionTfliteNativeEvent) -> Unit = {},
) {
    internal data class Token(
        val inferenceId: String,
        val attemptId: String,
        val startedAtUptimeMs: Long,
        val deadlineUptimeMs: Long?,
        var inputBufferBytes: Long = 0L,
        var outputBufferBytes: Long = 0L,
        var nativeStarted: Boolean = false,
        var nativeReturned: Boolean = false,
        var timeoutObserved: Boolean = false,
    )

    private var active: Token? = null
    private var quarantined = false
    private var closeRequested = false
    private var sessionClosed = false
    private var sessionClosedEventRecorded = false
    private var lastToken: Token? = null

    @Synchronized
    fun begin(inferenceId: String, attemptId: String, deadline: RecognitionAttemptDeadline?): Token {
        require(inferenceId.isNotBlank() && attemptId.isNotBlank())
        check(!sessionClosed) { "TFLITE_SESSION_CLOSED" }
        check(!quarantined) { "TFLITE_SESSION_QUARANTINED" }
        check(active == null) { "TFLITE_NATIVE_RUN_ALREADY_ACTIVE" }
        val now = nowUptimeMs().coerceAtLeast(0L)
        val token = Token(
            inferenceId = inferenceId,
            attemptId = attemptId,
            startedAtUptimeMs = now,
            deadlineUptimeMs = deadline?.deadlineUptimeMs,
            timeoutObserved = deadline?.isExpired(now) == true,
        )
        active = token
        lastToken = token
        emit(token, RecognitionTfliteNativeState.CREATED)
        return token
    }

    @Synchronized
    fun startNative(token: Token, inputBufferBytes: Long, outputBufferBytes: Long): Boolean {
        if (active !== token || sessionClosed || quarantined || closeRequested) return false
        require(inputBufferBytes >= 0L && outputBufferBytes >= 0L)
        token.inputBufferBytes = inputBufferBytes
        token.outputBufferBytes = outputBufferBytes
        if (token.deadlineUptimeMs?.let { nowUptimeMs() >= it } == true) token.timeoutObserved = true
        if (token.timeoutObserved) return false
        token.nativeStarted = true
        emit(token, RecognitionTfliteNativeState.RUNNING)
        return true
    }

    /** Called by the deadline scheduler. It records quarantine but never claims Native termination. */
    @Synchronized
    fun onDeadline(token: Token): Boolean {
        if (active !== token || token.nativeReturned) return false
        token.timeoutObserved = true
        if (!token.nativeStarted || quarantined) return true
        quarantined = true
        closeRequested = true
        emit(token, RecognitionTfliteNativeState.NATIVE_CALL_STUCK, "hard termination unsupported by verified API")
        emit(token, RecognitionTfliteNativeState.SESSION_QUARANTINED)
        emit(token, RecognitionTfliteNativeState.RESOURCE_QUARANTINED)
        return true
    }

    /** Returns false when late output must be discarded. */
    @Synchronized
    fun nativeReturned(token: Token): Boolean {
        if (active !== token || !token.nativeStarted || token.nativeReturned) return false
        if (token.deadlineUptimeMs?.let { nowUptimeMs() >= it } == true) onDeadline(token)
        token.nativeReturned = true
        emit(token, RecognitionTfliteNativeState.NATIVE_CALL_RETURNED)
        return !token.timeoutObserved && !quarantined
    }

    /** Ends run-local buffer ownership. Returns true when the caller must close the Interpreter now. */
    @Synchronized
    fun finish(token: Token): Boolean {
        if (active !== token) return false
        if (token.nativeStarted && !token.nativeReturned) {
            // The caller must invoke this only after the synchronous native API has returned.
            return false
        }
        active = null
        emit(token, RecognitionTfliteNativeState.RESOURCE_RELEASED)
        if (closeRequested || quarantined) {
            sessionClosed = true
            return true
        }
        return false
    }

    /** Returns true when no call is using the Interpreter and the caller may close it immediately. */
    @Synchronized
    fun requestClose(): Boolean {
        closeRequested = true
        if (active != null || sessionClosed) return false
        sessionClosed = true
        return true
    }

    @Synchronized
    fun confirmSessionClosed() {
        val token = lastToken
        if (sessionClosed && !sessionClosedEventRecorded && token != null) {
            sessionClosedEventRecorded = true
            emit(token, RecognitionTfliteNativeState.SESSION_CLOSED)
        }
    }

    @Synchronized
    fun isQuarantined(): Boolean = quarantined

    @Synchronized
    fun hasActiveRun(): Boolean = active != null

    private fun emit(token: Token, state: RecognitionTfliteNativeState, detail: String? = null) {
        onEvent(
            RecognitionTfliteNativeEvent(
                inferenceId = token.inferenceId,
                attemptId = token.attemptId,
                modelSessionId = modelSessionId,
                state = state,
                monotonicTimestampMs = nowUptimeMs().coerceAtLeast(0L),
                inputBufferBytes = token.inputBufferBytes,
                outputBufferBytes = token.outputBufferBytes,
                detail = detail,
            ),
        )
    }
}
