package com.yujian.ai.ai

import android.os.SystemClock
import android.util.Log
import com.yujian.ai.recognition.domain.PhotoIdentity
import com.yujian.ai.core.BuildConfig
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

/** Identity carried by model timing events; it never changes model inputs or inference policy. */
data class RecognitionModelIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoIdentity: PhotoIdentity? = null,
    val attemptDeadline: RecognitionAttemptDeadline? = null,
) {
    init {
        require(photoIdentity == null || photoIdentity.isValid())
        require(photoIdentity == null || photoIdentity.photoId == imageId)
        require(photoIdentity == null || photoIdentity.canonicalPixelSha256 == photoSha256)
    }
}

/** Adds identity-tagged lifecycle timing around each real model call without imposing a deadline. */
object RecognitionModelWatchdog {
    private val inferenceSequence = AtomicLong(0L)

    suspend fun <T : Any> execute(
        modelName: String,
        identity: RecognitionModelIdentity?,
        nowMs: () -> Long = SystemClock::uptimeMillis,
        log: (String) -> Unit = { Log.i("RecognitionModel", it) },
        block: suspend () -> T,
    ): T {
        require(modelName.isNotBlank())
        val startedAtMs = nowMs()
        val modelRunId = "${identity?.attemptId ?: "unknown"}:$modelName:$startedAtMs:${inferenceSequence.incrementAndGet()}"
        val tag = identity?.let {
            "session=${it.sessionId} attempt=${it.attemptId} image=${it.imageId} " +
                "canonical_sha256=${it.photoIdentity?.canonicalPixelSha256 ?: it.photoSha256} " +
                "source_sha256=${it.photoIdentity?.sourceBytesSha256 ?: "unknown"}"
        } ?: "session=unknown attempt=unknown image=unknown sha256=unknown"
        log("MODEL_START model=$modelName inference_id=$modelRunId $tag uptime_ms=$startedAtMs")
        fun memoryProfile(phase: String, timestampMs: Long): String {
            if (!BuildConfig.DEBUG) return ""
            val sample = runCatching {
                RecognitionNativeMemoryProfiler.sample(
                    attemptId = identity?.attemptId ?: "unknown",
                    modelRunId = modelRunId,
                    modelId = modelName,
                    stageId = "MODEL_INFERENCE",
                    nowUptimeMs = timestampMs,
                )
            }.getOrNull() ?: return " MEMORY_PROFILE_UNAVAILABLE phase=$phase"
            return " MEMORY_PROFILE phase=$phase sample=${sample.toJson()}"
        }
        log("MODEL_MEMORY_PROFILE model=$modelName inference_id=$modelRunId $tag${memoryProfile("START", startedAtMs)}")

        fun endRecord(outcome: String, detail: String = ""): String {
            val endedAtMs = nowMs()
            val suffix = if (detail.isBlank()) "" else " $detail"
            return "MODEL_END model=$modelName inference_id=$modelRunId $tag uptime_ms=$endedAtMs " +
                "duration_ms=${(endedAtMs - startedAtMs).coerceAtLeast(0L)} outcome=$outcome$suffix" +
                memoryProfile("END", endedAtMs)
        }

        return try {
            val result = block()
            if (identity?.attemptDeadline?.isExpired(nowMs()) == true) {
                throw RecognitionAttemptNativeTimeoutException("$modelName completed after the shared Attempt deadline")
            }
            log(endRecord("READY"))
            result
        } catch (cancelled: CancellationException) {
            log(endRecord("CANCELLED", "failure_code=${RecognitionFailureCode.CANCELLED}"))
            throw cancelled
        } catch (error: Throwable) {
            val failureCode = (error as? RecognitionStageFailureException)?.failureCode?.name
                ?: if (error is RecognitionAttemptNativeTimeoutException) {
                    when (modelName) {
                        "DETECTOR" -> RecognitionAttemptFailureCode.DETECTOR_TIMEOUT
                        "CLASSIFIER" -> RecognitionAttemptFailureCode.CLASSIFIER_TIMEOUT
                        "SLIMSAM" -> RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT
                        else -> RecognitionAttemptFailureCode.MODEL_INFERENCE_FAILED
                    }
                } else RecognitionFailureCode.MODEL_INFERENCE_FAILED.name
            log(endRecord("FAILED", "failure_code=$failureCode error=${error::class.java.simpleName}"))
            throw error
        }
    }
}
