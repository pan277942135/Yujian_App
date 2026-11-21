package com.yujian.ai.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException

/** Identity carried by model timing events; it never changes model inputs or inference policy. */
data class RecognitionModelIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
)

/** Adds identity-tagged lifecycle timing around each real model call without imposing a deadline. */
object RecognitionModelWatchdog {
    suspend fun <T : Any> execute(
        modelName: String,
        identity: RecognitionModelIdentity?,
        nowMs: () -> Long = SystemClock::uptimeMillis,
        log: (String) -> Unit = { Log.i("RecognitionModel", it) },
        block: suspend () -> T,
    ): T {
        require(modelName.isNotBlank())
        val startedAtMs = nowMs()
        val tag = identity?.let {
            "session=${it.sessionId} attempt=${it.attemptId} image=${it.imageId} sha256=${it.photoSha256}"
        } ?: "session=unknown attempt=unknown image=unknown sha256=unknown"
        log("MODEL_START model=$modelName $tag uptime_ms=$startedAtMs")

        fun endRecord(outcome: String, detail: String = ""): String {
            val endedAtMs = nowMs()
            val suffix = if (detail.isBlank()) "" else " $detail"
            return "MODEL_END model=$modelName $tag uptime_ms=$endedAtMs " +
                "duration_ms=${(endedAtMs - startedAtMs).coerceAtLeast(0L)} outcome=$outcome$suffix"
        }

        return try {
            block().also { log(endRecord("READY")) }
        } catch (cancelled: CancellationException) {
            log(endRecord("CANCELLED", "failure_code=${RecognitionFailureCode.CANCELLED}"))
            throw cancelled
        } catch (error: Throwable) {
            val failureCode = (error as? RecognitionStageFailureException)?.failureCode?.name
                ?: RecognitionFailureCode.MODEL_INFERENCE_FAILED.name
            log(endRecord("FAILED", "failure_code=$failureCode error=${error::class.java.simpleName}"))
            throw error
        }
    }
}
