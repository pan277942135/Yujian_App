package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeContractTest {
    @Test
    fun frozenTimelineHasOrderedStagesAndResultBoundary() {
        assertEquals("RECOGNITION_RUNTIME_v1", RecognitionRuntimeContract.CONTRACT_VERSION)
        assertEquals(
            listOf(
                RecognitionPhase.CAPTURED,
                RecognitionPhase.DETECTING,
                RecognitionPhase.OUTLINE,
                RecognitionPhase.CLASSIFYING,
                RecognitionPhase.RESULT,
            ),
            RecognitionRuntimeContract.timeline.map { it.phase },
        )
        assertTrue(
            RecognitionRuntimeContract.timeline.zipWithNext().all { (a, b) -> a.startMs < b.startMs },
        )
    }

    @Test
    fun phaseAtUsesTheFrozenTimelineAndClampsNegativeTime() {
        assertEquals(RecognitionPhase.CAPTURED, RecognitionRuntimeContract.phaseAt(-1L))
        assertEquals(RecognitionPhase.CAPTURED, RecognitionRuntimeContract.phaseAt(799L))
        assertEquals(RecognitionPhase.DETECTING, RecognitionRuntimeContract.phaseAt(800L))
        assertEquals(RecognitionPhase.OUTLINE, RecognitionRuntimeContract.phaseAt(1_500L))
        assertEquals(RecognitionPhase.CLASSIFYING, RecognitionRuntimeContract.phaseAt(2_300L))
        assertEquals(RecognitionPhase.RESULT, RecognitionRuntimeContract.phaseAt(3_000L))
        assertEquals(RecognitionPhase.RESULT, RecognitionRuntimeContract.phaseAt(Long.MAX_VALUE))
    }

    @Test
    fun labelsRemainUserFacingAndStable() {
        assertEquals("正在寻找鱼体", RecognitionRuntimeContract.labelFor(RecognitionPhase.DETECTING))
        assertEquals("鱼体轮廓出现", RecognitionRuntimeContract.labelFor(RecognitionPhase.OUTLINE))
        assertEquals("正在认识这条鱼", RecognitionRuntimeContract.labelFor(RecognitionPhase.CLASSIFYING))
        assertEquals("认识完成", RecognitionRuntimeContract.labelFor(RecognitionPhase.RESULT))
    }
}
