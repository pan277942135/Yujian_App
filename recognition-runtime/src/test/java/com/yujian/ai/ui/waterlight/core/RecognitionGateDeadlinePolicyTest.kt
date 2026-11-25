package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionGateDeadlinePolicyTest {
    @Test fun allTwentyThreeFrozenGateTimeoutsRetriesAndAttemptBudgetsAreExact() {
        val expected = listOf(
            Triple(3_000L, 2, true), Triple(2_000L, 1, true), Triple(1_500L, 1, true),
            Triple(1_500L, 2, true), Triple(1_500L, 2, true), Triple(1_000L, 0, true),
            Triple(2_000L, 0, true), Triple(2_000L, 1, true), Triple(1_500L, 1, true),
            Triple(1_500L, 2, true), Triple(1_500L, 2, true), Triple(2_000L, 2, true),
            Triple(2_500L, 1, true), Triple(2_000L, 2, true), Triple(2_000L, 2, true),
            Triple(3_000L, 2, true), Triple(2_000L, 1, true), Triple(1_000L, 0, true),
            Triple(1_000L, 0, true), Triple(1_500L, 1, true), Triple(2_000L, 1, true),
            Triple(1_500L, 2, true), Triple(1_500L, 0, false),
        )
        assertEquals(23, RecognitionGateDeadlinePolicyV1.specs.size)
        assertEquals(expected, RecognitionGateId.entries.map { id ->
            val spec = RecognitionGateDeadlinePolicyV1.spec(id)
            Triple(spec.timeoutMs, spec.maxRetries, spec.countsTowardAttemptBudget)
        })
        assertEquals(6_000L, RecognitionGateDeadlinePolicyV1.ACTIVE_PRESENTATION_BUDGET_MS)
        assertEquals(60_000L, RecognitionGateDeadlinePolicyV1.MODEL_ATTEMPT_DEADLINE_MS)
        assertEquals(3_000L, RecognitionGateDeadlinePolicyV1.MAX_SINGLE_NON_MODEL_GATE_MS)
        assertEquals(10_000L, RecognitionGateDeadlinePolicyV1.MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS)
        assertEquals(1_500L, RecognitionGateDeadlinePolicyV1.PIXELCOPY_CAPTURE_TIMEOUT_MS)
        assertTrue(RecognitionGateDeadlinePolicyV1.specs.values.all { it.maxCumulativeBlockedMs == if (it.countsTowardAttemptBudget) it.timeoutMs else 0L })
        assertTrue(RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.GLYPH_PATH).dependsOn.isEmpty())
    }

    @Test fun gateStartsAndSuccessesAreExplicitAndRevealStartsAfterModelTerminal() {
        assertEquals("ALL_REQUIRED_MODELS_TERMINAL", RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.REVEAL_DECISION).startEvent)
        assertEquals("MASK_CALLBACK_TERMINAL", RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.MASK_CONTOUR_PSTAR).startEvent)
        assertEquals("PIXELCOPY_REQUEST_SUBMITTED", RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.PIXELCOPY_CAPTURE).startEvent)
        assertEquals(RecognitionGateCategory.OBSERVATIONAL, RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.PIXELCOPY_CAPTURE).category)
        assertEquals(RecognitionGateFailureAction.EVIDENCE_ONLY, RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.PIXELCOPY_CAPTURE).action)
        assertEquals(
            listOf(setOf(RecognitionGateId.PATH_BOUND, RecognitionGateId.ORB_ONLY_RETURN_PATH)),
            RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.RETURN_RENDER).anyOfDependencies,
        )
        assertTrue(RecognitionGateDeadlinePolicyV1.spec(RecognitionGateId.RETURN_RENDER).dependsOn.none {
            it == RecognitionGateId.RESULT_HOST_GEOMETRY || it == RecognitionGateId.GLYPH_PATH
        })
    }

    @Test fun onlyTransientPresentationAndGeometryFailuresCanRetry() {
        assertTrue(RecognitionGateDeadlinePolicyV1.mayRetry(RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED))
        assertTrue(RecognitionGateDeadlinePolicyV1.mayRetry(RecognitionGateRetryReason.TEMPORARY_GEOMETRY_UNAVAILABLE))
        assertFalse(RecognitionGateDeadlinePolicyV1.mayRetry(RecognitionGateRetryReason.INVALID_IDENTITY))
        assertFalse(RecognitionGateDeadlinePolicyV1.mayRetry(RecognitionGateRetryReason.INVALID_FISH_PATH))
        assertFalse(RecognitionGateDeadlinePolicyV1.mayRetry(RecognitionGateRetryReason.UNTRUSTED_MASK))
    }
}
