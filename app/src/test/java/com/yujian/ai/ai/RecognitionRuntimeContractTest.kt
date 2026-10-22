package com.yujian.ai.ai

import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeContractTest {
    @Test
    fun runtimeProjectionMatchesFrozenV31StateMachineWithoutAddingTotalTime() {
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
        assertEquals(200L, RecognitionRuntimeContract.RESOLVE_FADE_MS)
        assertEquals(1_900L, RecognitionRuntimeContract.CLASSIFYING_BREATH_PERIOD_MS)
        assertEquals(
            listOf(
                RecognitionRuntimeContract.CAPTURED_MIN_MS,
                RecognitionRuntimeContract.DETECTING_MIN_MS,
                RecognitionRuntimeContract.OUTLINE_MIN_MS,
                RecognitionRuntimeContract.CLASSIFYING_MIN_MS,
            ),
            listOf(
                RecognitionVisualStateController.CAPTURED_MIN_MS,
                RecognitionVisualStateController.DETECTING_MIN_MS,
                RecognitionVisualStateController.OUTLINE_MIN_MS,
                RecognitionVisualStateController.CLASSIFYING_MIN_MS,
            ),
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
    fun resultReadyIsATerminalHandoffBoundaryAfterControllerTiming() {
        val resultReady = RecognitionRuntimeContract.state(RecognitionPresentationState.RESULT_READY)
        assertEquals(0L, resultReady.minimumDurationMs)
        assertEquals("", resultReady.title)
        assertEquals("", resultReady.subtitle)
    }
}
