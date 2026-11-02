package com.yujian.ai.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/** Identity carried by model timing events; it never changes model inputs or inference policy. */
data class RecognitionModelIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
)

class RecognitionModelTimeoutException(
    val modelName: String,
    val timeoutMs: Long,
) : Exception("$modelName exceeded its $timeoutMs ms watchdog")

/** Adds bounded, identity-tagged lifecycle timing around each real model call. */
object RecognitionModelWatchdog {
    const val DEFAULT_TIMEOUT_MS = 60_000L

    suspend fun <T : Any> execute(
        modelName: String,
        identity: RecognitionModelIdentity?,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        nowMs: () -> Long = SystemClock::uptimeMillis,
        log: (String) -> Unit = { Log.i("RecognitionModel", it) },
        block: suspend () -> T,
    ): T {
        require(modelName.isNotBlank())
        require(timeoutMs > 0L)
        val startedAtMs = nowMs()
        val tag = identity?.let {
            "session=${it.sessionId} attempt=${it.attemptId} image=${it.imageId} sha256=${it.photoSha256}"
        } ?: "session=unknown attempt=unknown image=unknown sha256=unknown"
        log("MODEL_START model=$modelName $tag uptime_ms=$startedAtMs timeout_ms=$timeoutMs")

        // The wrapper distinguishes a real null model payload from a watchdog timeout.
        val completed = withTimeoutOrNull(timeoutMs) { Completed(block()) }
        val endedAtMs = nowMs()
        if (completed == null) {
            log("MODEL_END model=$modelName $tag uptime_ms=$endedAtMs duration_ms=${(endedAtMs - startedAtMs).coerceAtLeast(0L)} outcome=TIMEOUT")
            throw RecognitionModelTimeoutException(modelName, timeoutMs)
        }
        log("MODEL_END model=$modelName $tag uptime_ms=$endedAtMs duration_ms=${(endedAtMs - startedAtMs).coerceAtLeast(0L)} outcome=READY")
        return completed.value
    }

    private data class Completed<T>(val value: T)
}
