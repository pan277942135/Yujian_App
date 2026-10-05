package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test
    fun fastResultKeepsThreeProductBeatsAndResolveUntil2950ms() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.RESULT, 10)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(899))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(900))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(1_499))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_500))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_949))
        assertEquals(RecognitionPhase.RESULT, controller.current(2_950))
    }

    @Test
    fun resolveIsTheFinal200msOfSpeciesRecognition() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.RESULT, 1)
        controller.current(900)
        controller.current(1_500)

        assertEquals(false, controller.isResolveActive(2_749))
        assertEquals(true, controller.isResolveActive(2_750))
        assertEquals(0f, controller.resolveProgress(2_749))
        assertEquals(0f, controller.resolveProgress(2_750))
        assertEquals(.25f, controller.resolveProgress(2_800))
        assertEquals(1f, controller.resolveProgress(2_950))
    }

    @Test
    fun capturedAndDetectingShareOneVisibleStateUntilRealFishLocation() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(900))
        assertEquals(RecognitionPhase.CAPTURED, controller.current(5_000))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 5_001)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(5_001))
    }

    @Test
    fun slowClassifierRemainsInCurrentTruthfulProductState() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 1)

        controller.current(900)
        controller.current(1_500)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(10_000))
    }

    @Test
    fun failureExitsProcessingImmediately() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.FAILURE, 25)

        assertEquals(RecognitionPhase.FAILURE, controller.current(25))
    }
}
