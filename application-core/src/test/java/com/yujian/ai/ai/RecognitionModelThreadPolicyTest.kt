package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionModelThreadPolicyTest {
    @Test
    fun fourCoreRunnerUsesTwoOneOneAsUnverifiedMeasurementCandidate() {
        val allocation = RecognitionModelThreadPolicy.allocate(4)

        assertEquals(2, allocation.visionEncoderThreads)
        assertEquals(1, allocation.detectorThreads)
        assertEquals(1, allocation.classifierThreads)
        assertEquals(4, allocatedWorkers(allocation))
    }

    @Test
    fun largerDevicesScaleWithinPerModelCapsAndAvailableWorkers() {
        val allocation = RecognitionModelThreadPolicy.allocate(8)

        assertEquals(4, allocation.visionEncoderThreads)
        assertEquals(2, allocation.detectorThreads)
        assertEquals(2, allocation.classifierThreads)
        assertEquals(8, allocatedWorkers(allocation))
    }

    @Test
    fun allocationsFitAvailableProcessorsWhenEachModelCanReceiveOneWorker() {
        for (processors in 3..64) {
            val allocation = RecognitionModelThreadPolicy.allocate(processors)
            assertTrue(
                "$processors processors received ${allocatedWorkers(allocation)} model workers",
                allocatedWorkers(allocation) <= processors,
            )
        }
    }

    @Test
    fun lowAndInvalidProcessorCountsKeepEachNativeRuntimeAtItsMinimum() {
        for (processors in 0..2) {
            val allocation = RecognitionModelThreadPolicy.allocate(processors)
            assertEquals(processors.coerceAtLeast(1), allocation.availableProcessors)
            assertEquals(1, allocation.visionEncoderThreads)
            assertEquals(1, allocation.detectorThreads)
            assertEquals(1, allocation.classifierThreads)
        }
    }

    private fun allocatedWorkers(allocation: RecognitionModelThreadAllocation) =
        allocation.visionEncoderThreads + allocation.detectorThreads + allocation.classifierThreads
}
