package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test
    fun v13SequenceWaitsForEachRealEventAndMinimumBeat() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 10)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(349))
        assertEquals(RecognitionPhase.DETECTING, controller.current(350))

        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 360, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, controller.current(949))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(950))

        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 960, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(1_549))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_550))

        controller.onPipelinePhase(RecognitionPhase.RESULT, 1_600, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_799))
        assertEquals(RecognitionPhase.RESULT, controller.current(2_800))
    }

    @Test
    fun lateRealResultHandsOffImmediatelyAfterMinimumBeat() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.current(350)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 360, hasRealFishFocus = true)
        controller.current(950)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 960, hasRealFishFocus = true)
        controller.current(1_550)

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
