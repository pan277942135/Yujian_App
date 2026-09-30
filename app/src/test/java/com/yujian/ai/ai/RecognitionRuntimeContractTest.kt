package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeContractTest {
    @Test
    fun presentationContractContainsExactlyThreeProcessingStatesAndResolve() {
        assertEquals("RECOGNITION_PRESENTATION_v1_3", RecognitionRuntimeContract.CONTRACT_VERSION)
        assertEquals(
            listOf(
                RecognitionPresentationState.IMAGE_RECOGNIZING,
                RecognitionPresentationState.FISH_LOCATED,
                RecognitionPresentationState.SPECIES_RECOGNIZING,
                RecognitionPresentationState.RESOLVE,
                RecognitionPresentationState.RESULT,
            ),
            RecognitionRuntimeContract.timeline.map { it.state },
        )
        assertEquals(
            listOf(0L, 900L, 1_500L, 2_750L, 2_950L),
            RecognitionRuntimeContract.timeline.map { it.startMs },
        )
        assertTrue(RecognitionRuntimeContract.timeline.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    @Test
    fun stateAtUsesFrozenPresentationBoundaries() {
        assertEquals(RecognitionPresentationState.IMAGE_RECOGNIZING, RecognitionRuntimeContract.stateAt(-1L))
        assertEquals(RecognitionPresentationState.IMAGE_RECOGNIZING, RecognitionRuntimeContract.stateAt(899L))
        assertEquals(RecognitionPresentationState.FISH_LOCATED, RecognitionRuntimeContract.stateAt(900L))
        assertEquals(RecognitionPresentationState.SPECIES_RECOGNIZING, RecognitionRuntimeContract.stateAt(1_500L))
        assertEquals(RecognitionPresentationState.RESOLVE, RecognitionRuntimeContract.stateAt(2_750L))
        assertEquals(RecognitionPresentationState.RESULT, RecognitionRuntimeContract.stateAt(2_950L))
        assertEquals(RecognitionPresentationState.RESULT, RecognitionRuntimeContract.stateAt(Long.MAX_VALUE))
    }

    @Test
    fun productCopyUsesOnlyTheThreeFrozenProcessingLabels() {
        assertEquals("图片识别中", RecognitionRuntimeContract.labelFor(RecognitionPresentationState.IMAGE_RECOGNIZING))
        assertEquals("已定位到鱼体", RecognitionRuntimeContract.labelFor(RecognitionPresentationState.FISH_LOCATED))
        assertEquals("鱼种识别中", RecognitionRuntimeContract.labelFor(RecognitionPresentationState.SPECIES_RECOGNIZING))
        assertEquals("", RecognitionRuntimeContract.labelFor(RecognitionPresentationState.RESOLVE))
    }
}
