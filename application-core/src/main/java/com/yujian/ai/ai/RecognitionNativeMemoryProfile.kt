package com.yujian.ai.ai

import android.os.Debug
import android.os.SystemClock
import org.json.JSONObject

data class RecognitionMemoryProfileSample(
    val schemaVersion: Int,
    val attemptId: String,
    val modelRunId: String,
    val modelId: String,
    val stageId: String,
    val sampledAtUptimeMs: Long,
    val javaHeapUsedBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val rssBytes: Long?,
    val pssBytes: Long?,
    val gpuMemoryBytes: Long?,
    val gpuMemoryStatus: String,
) {
    fun isValid(): Boolean = schemaVersion == 1 && attemptId.isNotBlank() && modelRunId.isNotBlank() &&
        modelId.isNotBlank() && sampledAtUptimeMs >= 0L && javaHeapUsedBytes >= 0L &&
        nativeHeapAllocatedBytes >= 0L && (rssBytes == null || rssBytes >= 0L) &&
        (pssBytes == null || pssBytes >= 0L) && (gpuMemoryBytes == null || gpuMemoryBytes >= 0L) &&
        gpuMemoryStatus.isNotBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("attemptId", attemptId)
        .put("modelRunId", modelRunId)
        .put("modelId", modelId)
        .put("stageId", stageId)
        .put("sampledAtUptimeMs", sampledAtUptimeMs)
        .put("javaHeapUsedBytes", javaHeapUsedBytes)
        .put("nativeHeapAllocatedBytes", nativeHeapAllocatedBytes)
        .put("rssBytes", rssBytes ?: JSONObject.NULL)
        .put("pssBytes", pssBytes ?: JSONObject.NULL)
        .put("gpuMemoryBytes", gpuMemoryBytes ?: JSONObject.NULL)
        .put("gpuMemoryStatus", gpuMemoryStatus)

    companion object {
        fun fromJson(json: JSONObject): RecognitionMemoryProfileSample = RecognitionMemoryProfileSample(
            schemaVersion = json.getInt("schemaVersion"),
            attemptId = json.getString("attemptId"),
            modelRunId = json.getString("modelRunId"),
            modelId = json.getString("modelId"),
            stageId = json.optString("stageId", "UNKNOWN"),
            sampledAtUptimeMs = json.getLong("sampledAtUptimeMs"),
            javaHeapUsedBytes = json.getLong("javaHeapUsedBytes"),
            nativeHeapAllocatedBytes = json.getLong("nativeHeapAllocatedBytes"),
            rssBytes = json.optNullableLong("rssBytes"),
            pssBytes = json.optNullableLong("pssBytes"),
            gpuMemoryBytes = json.optNullableLong("gpuMemoryBytes"),
            gpuMemoryStatus = json.getString("gpuMemoryStatus"),
        ).also { require(it.isValid()) { "MEMORY_PROFILE_SCHEMA_INVALID" } }

        private fun JSONObject.optNullableLong(key: String): Long? =
            if (!has(key) || isNull(key)) null else optLong(key, -1L)
    }
}

object RecognitionNativeMemoryProfiler {
    fun sample(
        attemptId: String,
        modelRunId: String,
        modelId: String,
        stageId: String = "UNKNOWN",
        nowUptimeMs: Long = SystemClock.uptimeMillis(),
        readProcStatus: () -> String? = { runCatching { java.io.File("/proc/self/status").readText() }.getOrNull() },
        readSmapsRollup: () -> String? = { runCatching { java.io.File("/proc/self/smaps_rollup").readText() }.getOrNull() },
    ): RecognitionMemoryProfileSample {
        val runtime = Runtime.getRuntime()
        val status = readProcStatus().orEmpty()
        val smaps = readSmapsRollup().orEmpty()
        fun kibibytes(source: String, key: String): Long? = Regex("(?m)^$key:\\s+(\\d+)\\s+kB\\s*$")
            .find(source)?.groupValues?.getOrNull(1)?.toLongOrNull()?.times(1024L)
        val sample = RecognitionMemoryProfileSample(
            schemaVersion = 1,
            attemptId = attemptId,
            modelRunId = modelRunId,
            modelId = modelId,
            stageId = stageId,
            sampledAtUptimeMs = nowUptimeMs,
            javaHeapUsedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            rssBytes = kibibytes(status, "VmRSS"),
            pssBytes = kibibytes(smaps, "Pss"),
            gpuMemoryBytes = null,
            gpuMemoryStatus = "UNAVAILABLE_PUBLIC_PROCESS_API",
        )
        require(sample.isValid()) { "MEMORY_PROFILE_SAMPLE_INVALID" }
        return sample
    }
}

data class RecognitionNativeAllocationTraceRecord(
    val modelRunId: String,
    val operatorName: String?,
    val timestampUptimeMs: Long,
    val allocationBytes: Long,
    val allocatorCallStack: String?,
)

data class RecognitionOperatorMemoryAttribution(
    val modelRunId: String,
    val operatorName: String,
    val allocationBytes: Long?,
    val status: String,
)

/** Attributes bytes only when a real allocator record identifies the same run, operator and time. */
object RecognitionOperatorMemoryAttributionPolicy {
    fun attribute(
        modelRunId: String,
        operatorName: String,
        operatorStartUptimeMs: Long,
        operatorEndUptimeMs: Long,
        allocatorRecords: List<RecognitionNativeAllocationTraceRecord>,
    ): RecognitionOperatorMemoryAttribution {
        require(modelRunId.isNotBlank() && operatorName.isNotBlank())
        val matching = allocatorRecords.filter {
            it.modelRunId == modelRunId && it.operatorName == operatorName &&
                it.timestampUptimeMs in operatorStartUptimeMs..operatorEndUptimeMs &&
                it.allocationBytes >= 0L && !it.allocatorCallStack.isNullOrBlank()
        }
        if (matching.isEmpty()) return RecognitionOperatorMemoryAttribution(
            modelRunId, operatorName, null, "OPERATOR_MEMORY_ATTRIBUTION_INCOMPLETE",
        )
        return RecognitionOperatorMemoryAttribution(
            modelRunId, operatorName, matching.sumOf { it.allocationBytes }, "ATTRIBUTED_FROM_NATIVE_ALLOCATOR_TRACE",
        )
    }
}
