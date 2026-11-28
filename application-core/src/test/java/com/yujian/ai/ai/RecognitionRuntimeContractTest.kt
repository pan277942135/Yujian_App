package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeContractTest {
    @Test
    fun runtimeProjectionMatchesV31StatesDurationsAndCopy() {
        assertEquals(
            "recognition_processing_state_machine_v3_1",
            RecognitionRuntimeContract.CONTRACT_VERSION,
        )
        assertEquals(
            listOf(
                RecognitionPresentationState.CAPTURED,
                RecognitionPresentationState.DETECTING,
                RecognitionPresentationState.OUTLINE,
                RecognitionPresentationState.CLASSIFYING,
                RecognitionPresentationState.RESULT_READY,
            ),
            RecognitionRuntimeContract.presentationSequence,
        )
        assertEquals(
            listOf(220L, 320L, 350L, 250L, 0L),
            RecognitionRuntimeContract.states.map { it.minimumDurationMs },
        )
        assertEquals(
            listOf(
                "正在准备识别" to "AI 已获取这张照片",
                "正在理解这张照片" to "寻找这次鱼获的线索",
                "已定位到鱼体" to "正在分析这次鱼获",
                "正在认识这条鱼" to "分析鱼体特征",
                "" to "",
            ),
            RecognitionRuntimeContract.states.map { it.title to it.subtitle },
        )
        assertTrue(RecognitionRuntimeContract.states.zipWithNext().all { (a, b) ->
            a.minimumDurationMs > 0L && b.minimumDurationMs >= 0L
        })
    }

    @Test
    fun resultReadyIsAnImmediateHandoffBoundary() {
        val resultReady = RecognitionRuntimeContract.state(RecognitionPresentationState.RESULT_READY)
        assertEquals(0L, resultReady.minimumDurationMs)
        assertEquals("", resultReady.title)
        assertEquals("", resultReady.subtitle)
    }
}
