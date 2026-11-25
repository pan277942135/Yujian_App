package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionGateDeadlineTrackerTest {
    @Test fun armIsOnceAndRetryKeepsOriginalDeadlineAndRetryCount() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        val original = requireNotNull(tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 1_000))
        assertEquals(4_000L, original.deadlineUptimeMs)
        tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 2_000)
        assertEquals(4_000L, tracker.nextDeadlineUptimeMs())
        assertTrue(tracker.markRetrying(
            RecognitionGateId.PHOTO_FIRST_FRAME,
            RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
            event(2, "PHOTO_STABLE_FIRST_FRAME_REQUEST:RETRY"), 2_500,
        ))
        assertEquals(4_000L, tracker.nextDeadlineUptimeMs())
        assertEquals(1, tracker.snapshot(2_500).gates.getValue(RecognitionGateId.PHOTO_FIRST_FRAME).retriesUsed)
        assertTrue(tracker.markRetrying(
            RecognitionGateId.PHOTO_FIRST_FRAME,
            RecognitionGateRetryReason.TEMPORARY_GEOMETRY_UNAVAILABLE,
            event(3, "PHOTO_STABLE_FIRST_FRAME_REQUEST:RETRY"), 3_000,
        ))
        assertFalse(tracker.markRetrying(
            RecognitionGateId.PHOTO_FIRST_FRAME,
            RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
            event(4, "PHOTO_STABLE_FIRST_FRAME_REQUEST:RETRY"), 3_500,
        ))
        assertEquals(4_000L, tracker.snapshot(3_500).gates.getValue(RecognitionGateId.PHOTO_FIRST_FRAME).deadlineUptimeMs)
        assertEquals("GATE_RETRY_EXHAUSTED", tracker.snapshot(3_500).firstFailure?.failureCategory)
    }

    @Test fun parallelGatesAccumulateTheUnionOfBlockedIntervalsOnlyOnce() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 0)
        tracker.snapshot(1_000)
        tracker.armOnce(RecognitionGateId.RESULT_HOST_GEOMETRY, 1_000)
        assertEquals(2_000L, tracker.snapshot(2_000).blockedWaitTotalMs)
        assertTrue(tracker.markSatisfied(RecognitionGateId.RESULT_HOST_GEOMETRY, event(3, "RESULT_HOST_GEOMETRY_READY"), 2_000))
        assertEquals(2_500L, tracker.snapshot(2_500).blockedWaitTotalMs)
    }

    @Test fun modelWaitAndPixelCopyAreExcludedFromBusinessWaitBudget() {
        val modelWait = RecognitionGateDeadlineTracker("a1")
        modelWait.setRuntimeContext(0, modelRunning = true)
        modelWait.armOnce(RecognitionGateId.RESULT_HOST_GEOMETRY, 0)
        assertEquals(0L, modelWait.snapshot(2_000).blockedWaitTotalMs)
        modelWait.setRuntimeContext(2_000, modelRunning = false)
        assertEquals(500L, modelWait.snapshot(2_500).blockedWaitTotalMs)

        val evidence = RecognitionGateDeadlineTracker("a1")
        evidence.armOnce(RecognitionGateId.PIXELCOPY_CAPTURE, 0)
        assertEquals(0L, evidence.snapshot(1_000).blockedWaitTotalMs)
    }

    @Test fun retryMustUseSameGateInstanceAndExactRetryEvent() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 0)
        assertFalse(tracker.markRetrying(
            RecognitionGateId.PHOTO_FIRST_FRAME,
            RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
            event(2, "UNRELATED_RETRY"),
            100,
        ))
        val failed = tracker.snapshot(100)
        assertEquals("GATE_EVENT_ORDER_INVALID", failed.firstFailure?.failureCategory)
        assertEquals(0, failed.gates.getValue(RecognitionGateId.PHOTO_FIRST_FRAME).retriesUsed)
    }

    @Test fun onlyNewAttemptResetsBudgetAndTimeoutCreatesFirstFailureAndBlocksChildren() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 0)
        assertEquals(listOf(RecognitionGateId.PHOTO_FIRST_FRAME), tracker.evaluateTimeouts(3_000))
        assertEquals("NON_MODEL_GATE_TIMEOUT", tracker.snapshot(3_000).firstFailure?.failureCategory)
        val child = requireNotNull(tracker.armOnce(RecognitionGateId.PROCESSING_GEOMETRY, 3_001))
        assertEquals(RecognitionGateRunStatus.BLOCKED, child.status)
        assertEquals("BLOCKED_BY_UPSTREAM_FAILURE", child.failureCode)
        assertFalse(tracker.resetForNewAttempt("a1", 3_100))
        assertTrue(tracker.resetForNewAttempt("a2", 3_100))
        assertNull(tracker.snapshot(3_100).firstFailure)
        assertEquals(0L, tracker.snapshot(3_100).blockedWaitTotalMs)
    }

    @Test fun staleAttemptAndOutOfOrderReceiptsFailClosedAndPreserveFirstFailure() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.RESULT_HOST_GEOMETRY, 0, RecognitionGateIdentity("a1", "photo-hash"))
        assertFalse(tracker.markSatisfied(
            RecognitionGateId.RESULT_HOST_GEOMETRY,
            RecognitionGateEvent("RESULT_HOST_GEOMETRY_READY", 2, RecognitionGateIdentity("old-attempt", "photo-hash")),
            100,
        ))
        val first = tracker.snapshot(100).firstFailure
        assertEquals("GATE_IDENTITY_MISMATCH", first?.failureCategory)
        tracker.markFailed(RecognitionGateId.RESULT_HOST_GEOMETRY, "later-error", event(3, "failure"), 101)
        assertEquals(first, tracker.snapshot(101).firstFailure)
    }
}

private fun event(sequence: Long, name: String) = RecognitionGateEvent(
    name,
    sequence,
    RecognitionGateIdentity("a1"),
)
