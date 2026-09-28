package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeContractTest {
    @Test
    fun compatibilityTimelineMatchesFrozenV11Boundaries() {
        assertEquals("RECOGNITION_RUNTIME_v1_1", RecognitionRuntimeContract.CONTRACT_VERSION)
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
        assertEquals(listOf(0L, 350L, 950L, 1_550L, 2_800L), RecognitionRuntimeContract.timeline.map { it.startMs })
        assertTrue(RecognitionRuntimeContract.timeline.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    @Test
    fun phaseAtUsesFrozenV11TimelineAndClampsNegativeTime() {
        assertEquals(RecognitionPhase.CAPTURED, RecognitionRuntimeContract.phaseAt(-1L))
        assertEquals(RecognitionPhase.CAPTURED, RecognitionRuntimeContract.phaseAt(349L))
        assertEquals(RecognitionPhase.DETECTING, RecognitionRuntimeContract.phaseAt(350L))
        assertEquals(RecognitionPhase.OUTLINE, RecognitionRuntimeContract.phaseAt(950L))
        assertEquals(RecognitionPhase.CLASSIFYING, RecognitionRuntimeContract.phaseAt(1_550L))
        assertEquals(RecognitionPhase.RESULT, RecognitionRuntimeContract.phaseAt(2_800L))
        assertEquals(RecognitionPhase.RESULT, RecognitionRuntimeContract.phaseAt(Long.MAX_VALUE))
    }

    @Test
    fun labelsMirrorCurrentUserFacingProcessingCopy() {
        assertEquals("正在准备识别", RecognitionRuntimeContract.labelFor(RecognitionPhase.CAPTURED))
        assertEquals("正在理解这张照片", RecognitionRuntimeContract.labelFor(RecognitionPhase.DETECTING))
        assertEquals("已定位到鱼体", RecognitionRuntimeContract.labelFor(RecognitionPhase.OUTLINE))
        assertEquals("正在认识这条鱼", RecognitionRuntimeContract.labelFor(RecognitionPhase.CLASSIFYING))
        assertEquals("认识完成", RecognitionRuntimeContract.labelFor(RecognitionPhase.RESULT))
    }
}
