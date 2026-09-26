package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test fun preservesVisualOrderForFastPipelineCompletion() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.RESULT, 10)
        assertEquals(RecognitionPhase.CAPTURED, controller.current(100))
        assertEquals(RecognitionPhase.DETECTING, controller.current(180))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(440))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(730))
        assertEquals(RecognitionPhase.RESULT, controller.current(1_000))
    }

    @Test fun holdsEachRealPhaseForItsFrozenMinimum() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        assertEquals(RecognitionPhase.CAPTURED, controller.current(219))
        assertEquals(RecognitionPhase.DETECTING, controller.current(220))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221)
        assertEquals(RecognitionPhase.DETECTING, controller.current(539))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(540))
    }

    @Test fun neverHoldsResultMoreThanFrozenPostResultBound() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.RESULT, 1)
        assertEquals(RecognitionPhase.RESULT, controller.current(902))
    }
}
