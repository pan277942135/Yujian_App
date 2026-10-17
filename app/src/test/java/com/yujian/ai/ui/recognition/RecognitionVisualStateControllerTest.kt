package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test
    fun v31SequenceWaitsForEachRealEventAndMinimumBeat() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 10)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(219))
        assertEquals(RecognitionPhase.DETECTING, controller.current(220))

        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, controller.current(539))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(540))

        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 541, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(889))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(890))

        controller.onPipelinePhase(RecognitionPhase.RESULT, 900, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_139))
        assertEquals(RecognitionPhase.RESULT, controller.current(1_140))
    }

    @Test
    fun lateRealResultHandsOffImmediatelyAfterMinimumBeat() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.current(220)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221, hasRealFishFocus = true)
        controller.current(540)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 541, hasRealFishFocus = true)
        controller.current(890)

        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(10_000))
        controller.onPipelinePhase(RecognitionPhase.RESULT, 10_001, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.RESULT, controller.current(10_001))
    }

    @Test
    fun noTimerOnlyProgressWithoutDetectionFocusClassificationOrResult() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }

        assertEquals(RecognitionPhase.CAPTURED, controller.current(10_000))
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 10_001)
        assertEquals(RecognitionPhase.DETECTING, controller.current(10_001))

        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 10_002, hasRealFishFocus = false)
        assertEquals(RecognitionPhase.DETECTING, controller.current(20_000))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 20_001, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(20_001))

        assertEquals(RecognitionPhase.OUTLINE, controller.current(30_000))
    }

    @Test
    fun failureExitsProcessingImmediately() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.FAILURE, 25)

        assertEquals(RecognitionPhase.FAILURE, controller.current(25))
    }
}
