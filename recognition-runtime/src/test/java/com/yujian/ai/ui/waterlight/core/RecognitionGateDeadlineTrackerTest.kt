package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
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
        assertTrue(evidence.markFailed(
            RecognitionGateId.PIXELCOPY_CAPTURE,
            "PIXELCOPY_ERROR",
            event(2, "PIXELCOPY_ERROR"),
            1_001,
        ))
        assertNull(evidence.snapshot(1_001).firstFailure)
        assertTrue(evidence.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 1_002) != null)
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

    @Test fun missingParentFailsClosedWithExplicitNotArmedState() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        val child = requireNotNull(tracker.armOnce(RecognitionGateId.PROCESSING_GEOMETRY, 10))
        assertEquals(RecognitionGateRunStatus.BLOCKED, child.status)
        assertEquals(
            RecognitionGateParentState.PARENT_NOT_ARMED,
            child.parentStates[RecognitionGateId.PHOTO_FIRST_FRAME],
        )
        assertEquals("GATE_PARENT_NOT_SATISFIED", child.failureCode)
        assertEquals(RecognitionGateId.PROCESSING_GEOMETRY, tracker.snapshot(10).firstFailure?.gateId)
    }

    @Test fun waitingRetryingFailedExpiredAndCancelledParentsNeverAuthorizeChildren() {
        fun childWithParent(parentState: String): RecognitionGateRunSnapshot {
            val tracker = RecognitionGateDeadlineTracker("a1")
            tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 0)
            when (parentState) {
                "WAITING" -> Unit
                "RETRYING" -> assertTrue(tracker.markRetrying(
                    RecognitionGateId.PHOTO_FIRST_FRAME,
                    RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
                    event(2, "PHOTO_STABLE_FIRST_FRAME_REQUEST:RETRY"), 10,
                ))
                "FAILED" -> assertTrue(tracker.markFailed(
                    RecognitionGateId.PHOTO_FIRST_FRAME, "PHOTO_FAIL", event(2, "failure"), 10,
                ))
                "EXPIRED" -> tracker.evaluateTimeouts(3_000)
                "CANCELLED" -> assertTrue(tracker.markCancelled(RecognitionGateId.PHOTO_FIRST_FRAME, 10))
            }
            return requireNotNull(tracker.armOnce(
                RecognitionGateId.PROCESSING_GEOMETRY,
                3_001,
                startEvent = event(3, "NORMALIZED_PHOTO_GEOMETRY_REQUEST"),
            ))
        }
        assertEquals(RecognitionGateParentState.PARENT_WAITING, childWithParent("WAITING").parentStates[RecognitionGateId.PHOTO_FIRST_FRAME])
        assertEquals(RecognitionGateParentState.PARENT_RETRYING, childWithParent("RETRYING").parentStates[RecognitionGateId.PHOTO_FIRST_FRAME])
        assertEquals(RecognitionGateParentState.PARENT_FAILED, childWithParent("FAILED").parentStates[RecognitionGateId.PHOTO_FIRST_FRAME])
        assertEquals(RecognitionGateParentState.PARENT_EXPIRED, childWithParent("EXPIRED").parentStates[RecognitionGateId.PHOTO_FIRST_FRAME])
        assertEquals(RecognitionGateParentState.PARENT_CANCELLED, childWithParent("CANCELLED").parentStates[RecognitionGateId.PHOTO_FIRST_FRAME])
        assertTrue(childWithParent("WAITING").status != RecognitionGateRunStatus.WAITING)
    }

    @Test fun satisfiedParentAuthorizesChildAndOptionalBranchUsesOneExplicitAlternative() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.PHOTO_FIRST_FRAME, 0)
        assertTrue(tracker.markSatisfied(RecognitionGateId.PHOTO_FIRST_FRAME, event(2, "PHOTO_FRAME_COMMITTED"), 100))
        assertEquals(
            RecognitionGateRunStatus.WAITING,
            requireNotNull(tracker.armOnce(
                RecognitionGateId.PROCESSING_GEOMETRY, 101,
                startEvent = event(3, "NORMALIZED_PHOTO_GEOMETRY_REQUEST"),
            )).status,
        )

        val branch = RecognitionGateDeadlineTracker("a1")
        branch.armOnce(RecognitionGateId.REVEAL_DECISION, 0)
        assertTrue(branch.markSatisfied(
            RecognitionGateId.REVEAL_DECISION, event(2, "REVEAL_BRANCH_DECIDED"), 10,
        ))
        branch.armOnce(
            RecognitionGateId.ORB_ONLY_RETURN_PATH, 11,
            startEvent = event(3, "ORB_ONLY_BRANCH_CONFIRMED"),
        )
        assertTrue(branch.markSatisfied(
            RecognitionGateId.ORB_ONLY_RETURN_PATH, event(4, "ORB_ONLY_RETURN_PATH_READY"), 20,
        ))
        val returnRender = requireNotNull(branch.armOnce(
            RecognitionGateId.RETURN_RENDER, 21,
            startEvent = event(5, "KF14_ORB_RETURN_ENTERED"),
        ))
        assertEquals(RecognitionGateRunStatus.WAITING, returnRender.status)
        assertEquals(RecognitionGateParentState.PARENT_NOT_ARMED, returnRender.parentStates[RecognitionGateId.PATH_BOUND])
        assertEquals(RecognitionGateParentState.PARENT_SATISFIED, returnRender.parentStates[RecognitionGateId.ORB_ONLY_RETURN_PATH])
    }

    @Test fun failedOptionalRouteDoesNotBlockSatisfiedPathBindingAlternative() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        tracker.armOnce(RecognitionGateId.REVEAL_DECISION, 0)
        assertTrue(tracker.markSatisfied(
            RecognitionGateId.REVEAL_DECISION, event(2, "REVEAL_BRANCH_DECIDED"), 1,
        ))
        tracker.armOnce(RecognitionGateId.MASK_CONTOUR_PSTAR, 2)
        assertTrue(tracker.markSatisfied(
            RecognitionGateId.MASK_CONTOUR_PSTAR, event(4, "MASK_CONTOUR_PSTAR_READY"), 3,
        ))
        tracker.armOnce(RecognitionGateId.PATH_BOUND, 4)
        assertTrue(tracker.markSatisfied(
            RecognitionGateId.PATH_BOUND, event(6, "FISHPATH_BOUND_BY_CONTROLLER_AND_STORE"), 5,
        ))
        tracker.armOnce(RecognitionGateId.ORB_ONLY_RETURN_PATH, 6)
        assertTrue(tracker.markFailed(
            RecognitionGateId.ORB_ONLY_RETURN_PATH,
            "OPTIONAL_ORB_PATH_UNAVAILABLE",
            event(8, "OPTIONAL_ORB_PATH_FAILURE"),
            7,
        ))

        val returnRender = requireNotNull(tracker.armOnce(
            RecognitionGateId.RETURN_RENDER, 8,
            startEvent = event(9, "KF14_ORB_RETURN_ENTERED"),
        ))
        assertEquals(RecognitionGateRunStatus.WAITING, returnRender.status)
        assertFalse(tracker.snapshot(8).firstFailure?.blockedDownstream.orEmpty().contains(RecognitionGateId.RETURN_RENDER))
    }

    @Test fun dependencyCycleAndUnknownGateAreRejected() {
        val source = RecognitionGateDeadlinePolicyV1.specs
        val cycle = source.toMutableMap()
        cycle[RecognitionGateId.PHOTO_FIRST_FRAME] = source.getValue(RecognitionGateId.PHOTO_FIRST_FRAME).copy(
            dependsOn = setOf(RecognitionGateId.PROCESSING_GEOMETRY),
        )
        val error = runCatching { RecognitionGateDeadlinePolicyV1.validateDependencyGraph(cycle) }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(null, RecognitionGateDeadlinePolicyV1.specOrNull("UNKNOWN_GATE"))
    }

    @Test fun producerReceiptIsTypedValidatedAndDuplicateDeliveryIsIdempotent() {
        val tracker = RecognitionGateDeadlineTracker("a1")
        val identity = RecognitionGateIdentity(
            attemptId = "a1",
            photoIdentityHash = "photo-hash",
            recognitionSessionId = "session-1",
            photoId = "photo-1",
        )
        val spec = RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.RESULT_HOST_GEOMETRY)
        tracker.armOnce(
            RecognitionGateId.RESULT_HOST_GEOMETRY,
            nowUptimeMs = 0L,
            identity = identity,
            startEvent = RecognitionGateEvent(spec.startEvent, 1L, identity),
        )
        val receipt = RecognitionGateReceipt(
            receiptId = "result-host-1",
            receiptType = "PRODUCTION_SUCCESS",
            gateId = RecognitionGateId.RESULT_HOST_GEOMETRY,
            producerId = spec.producerId,
            producerVersion = spec.producerVersion,
            recognitionSessionId = "session-1",
            attemptId = "a1",
            photoId = "photo-1",
            photoIdentityHash = "photo-hash",
            sourceEventId = "layout-ready-1",
            eventSequence = 2L,
            stageId = "ORB_RETURN",
            geometryVersion = 3L,
            contentHash = "geometry-hash",
            monotonicTimestampMs = 1L,
            resultStatus = "ACCEPTED",
        )
        assertTrue(tracker.acceptReceipt(receipt, 1L))
        assertTrue(tracker.acceptReceipt(receipt, 2L))
        assertTrue(tracker.isSatisfied(RecognitionGateId.RESULT_HOST_GEOMETRY))
        assertFalse(tracker.acceptReceipt(receipt.copy(contentHash = "different"), 3L))
        assertEquals("GATE_RECEIPT_ID_REUSED", tracker.snapshot(3L).firstFailure?.failureCode)
    }
}

private fun event(sequence: Long, name: String) = RecognitionGateEvent(
    name,
    sequence,
    RecognitionGateIdentity("a1"),
)
