package com.yujian.ai.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionNativeMemoryProfileTest {
    @Test
    fun schemaParserKeepsUnavailableGpuAsUnavailableInsteadOfZero() {
        val sample = RecognitionMemoryProfileSample.fromJson(JSONObject()
            .put("schemaVersion", 1)
            .put("attemptId", "attempt")
            .put("modelRunId", "run-1")
            .put("modelId", "SLIMSAM_ENCODER")
            .put("stageId", "MASK")
            .put("sampledAtUptimeMs", 100L)
            .put("javaHeapUsedBytes", 123L)
            .put("nativeHeapAllocatedBytes", 456L)
            .put("rssBytes", 789L)
            .put("pssBytes", 700L)
            .put("gpuMemoryBytes", JSONObject.NULL)
            .put("gpuMemoryStatus", "UNAVAILABLE_PUBLIC_PROCESS_API"))
        assertEquals(123L, sample.javaHeapUsedBytes)
        assertNull(sample.gpuMemoryBytes)
        assertEquals("UNAVAILABLE_PUBLIC_PROCESS_API", sample.gpuMemoryStatus)
    }

    @Test
    fun operatorBytesRequireSameRunOperatorTimeAndAllocatorStack() {
        val matching = RecognitionNativeAllocationTraceRecord("run-1", "Conv", 110L, 64L, "OrtAllocator.allocate")
        val wrongRun = matching.copy(modelRunId = "run-old", allocationBytes = 512L)
        val missingStack = matching.copy(timestampUptimeMs = 115L, allocationBytes = 256L, allocatorCallStack = null)
        val result = RecognitionOperatorMemoryAttributionPolicy.attribute(
            modelRunId = "run-1",
            operatorName = "Conv",
            operatorStartUptimeMs = 100L,
            operatorEndUptimeMs = 120L,
            allocatorRecords = listOf(matching, wrongRun, missingStack),
        )
        assertEquals(64L, result.allocationBytes)
        assertEquals("ATTRIBUTED_FROM_NATIVE_ALLOCATOR_TRACE", result.status)
    }

    @Test
    fun missingOperatorAllocationCorrelationIsExplicitlyIncomplete() {
        val result = RecognitionOperatorMemoryAttributionPolicy.attribute(
            modelRunId = "run-1", operatorName = "Conv",
            operatorStartUptimeMs = 100L, operatorEndUptimeMs = 120L,
            allocatorRecords = listOf(RecognitionNativeAllocationTraceRecord("run-1", "Conv", 90L, 1_024L, "stack")),
        )
        assertNull(result.allocationBytes)
        assertEquals("OPERATOR_MEMORY_ATTRIBUTION_INCOMPLETE", result.status)
    }
}
