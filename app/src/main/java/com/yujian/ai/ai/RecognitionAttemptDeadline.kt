package com.yujian.ai.ai

/**
 * One monotonic deadline shared by Detector, Classifier and SlimSAM for a recognition attempt.
 * Coroutine cancellation is cooperative: it cannot forcibly stop a synchronous native inference
 * already running. Callers must reject any late result after this deadline expires.
 */
data class RecognitionAttemptDeadline(
    val startedAtUptimeMs: Long,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    init {
        require(startedAtUptimeMs >= 0L)
        require(timeoutMs > 0L)
    }

    val deadlineUptimeMs: Long get() = startedAtUptimeMs + timeoutMs

    fun remainingMs(nowUptimeMs: Long): Long =
        (deadlineUptimeMs - nowUptimeMs.coerceAtLeast(0L)).coerceAtLeast(0L)

    fun isExpired(nowUptimeMs: Long): Boolean = remainingMs(nowUptimeMs) == 0L

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}

/** Stable failure families; the stage identifies the model active when the shared deadline expired. */
object RecognitionAttemptFailureCode {
    const val DETECTOR_TIMEOUT = "DETECTOR_TIMEOUT"
    const val CLASSIFIER_TIMEOUT = "CLASSIFIER_TIMEOUT"
    const val SLIMSAM_TIMEOUT = "SLIMSAM_TIMEOUT"
    const val MODEL_LOAD_FAILED = "MODEL_LOAD_FAILED"
    const val MODEL_INFERENCE_FAILED = "MODEL_INFERENCE_FAILED"
    const val NO_FISH_DETECTED = "NO_FISH_DETECTED"
    const val MASK_UNTRUSTED = "MASK_UNTRUSTED"
    const val INVALID_PHOTO_GEOMETRY = "INVALID_PHOTO_GEOMETRY"
    const val CANCELLED = "CANCELLED"
    const val STALE_ATTEMPT = "STALE_ATTEMPT"
}
