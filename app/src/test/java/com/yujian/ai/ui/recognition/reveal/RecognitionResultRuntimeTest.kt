package com.yujian.ai.ui.recognition.reveal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionResultRuntimeTest {
    @Test
    fun fiveResultMilestonesHaveStableContractIdsAndStrictOrder() {
        val sequence = ResultHandoffSubstage.entries
        assertEquals(listOf("13.1", "13.2", "13.3", "13.4", "13.5"), sequence.map { it.contractId })

        var completed = emptySet<ResultHandoffSubstage>()
        sequence.forEach { stage ->
            assertTrue("${stage.contractId} must be the only next event", RecognitionResultHandoffContract.isNext(stage, completed))
            sequence.getOrNull(stage.ordinal + 1)?.let { skipped ->
                assertFalse("a skipped milestone must be rejected", RecognitionResultHandoffContract.isNext(skipped, completed))
            }
            completed = completed + stage
            assertFalse("a completed milestone cannot replay", RecognitionResultHandoffContract.isNext(stage, completed))
        }
        assertTrue(RecognitionResultHandoffContract.isComplete(completed))
        assertFalse(RecognitionResultHandoffContract.isNext(sequence.last(), completed))
    }

    @Test
    fun malformedCompletedSetCannotSkipAnEarlierSubstage() {
        val completed = setOf(ResultHandoffSubstage.NAME_STABLE, ResultHandoffSubstage.RESULT_INFO_ARRIVAL)
        assertFalse(RecognitionResultHandoffContract.isNext(ResultHandoffSubstage.RESULT_SCENE_STABLE, completed))
        assertFalse(RecognitionResultHandoffContract.isComplete(completed))
    }

    @Test
    fun handoffSequenceAndMonotonicClockRejectDuplicateSkippedOrNonIncreasingEvents() {
        assertTrue(RecognitionResultHandoffContract.isNextEventSequence(requested = 1L, lastAccepted = 0L))
        assertFalse(RecognitionResultHandoffContract.isNextEventSequence(requested = 2L, lastAccepted = 0L))
        assertFalse(RecognitionResultHandoffContract.isNextEventSequence(requested = 1L, lastAccepted = 1L))
        assertTrue(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 101L, lastAccepted = 100L))
        assertFalse(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 100L, lastAccepted = 100L))
        assertFalse(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 99L, lastAccepted = 100L))
    }
}
