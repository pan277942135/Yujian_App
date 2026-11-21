package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAttemptDeadlineTest {
    @Test fun detectorClassifierAndSlimSamShareTheSameRemainingBudget() {
        val deadline = RecognitionAttemptDeadline(startedAtUptimeMs = 10_000L, timeoutMs = 60_000L)
        assertEquals(60_000L, deadline.remainingMs(10_000L))
        assertEquals(24_000L, deadline.remainingMs(46_000L))
        assertEquals(1L, deadline.remainingMs(69_999L))
        assertEquals(0L, deadline.remainingMs(70_000L))
        assertFalse(deadline.isExpired(69_999L))
        assertTrue(deadline.isExpired(70_000L))
    }

    @Test fun expiredBudgetCannotBeRestartedByALaterModelStage() {
        val deadline = RecognitionAttemptDeadline(startedAtUptimeMs = 5L, timeoutMs = 60_000L)
        assertEquals(0L, deadline.remainingMs(75_000L))
        assertEquals(0L, deadline.remainingMs(90_000L))
    }

    @Test fun failureFamiliesRemainStableAcrossModelMaskGeometryAndAttemptBoundaries() {
        assertEquals("DETECTOR_TIMEOUT", RecognitionAttemptFailureCode.DETECTOR_TIMEOUT)
        assertEquals("CLASSIFIER_TIMEOUT", RecognitionAttemptFailureCode.CLASSIFIER_TIMEOUT)
        assertEquals("SLIMSAM_TIMEOUT", RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT)
        assertEquals("MODEL_LOAD_FAILED", RecognitionAttemptFailureCode.MODEL_LOAD_FAILED)
        assertEquals("MODEL_INFERENCE_FAILED", RecognitionAttemptFailureCode.MODEL_INFERENCE_FAILED)
        assertEquals("NO_FISH_DETECTED", RecognitionAttemptFailureCode.NO_FISH_DETECTED)
        assertEquals("MASK_UNTRUSTED", RecognitionAttemptFailureCode.MASK_UNTRUSTED)
        assertEquals("INVALID_PHOTO_GEOMETRY", RecognitionAttemptFailureCode.INVALID_PHOTO_GEOMETRY)
        assertEquals("CANCELLED", RecognitionAttemptFailureCode.CANCELLED)
        assertEquals("STALE_ATTEMPT", RecognitionAttemptFailureCode.STALE_ATTEMPT)
    }
}
