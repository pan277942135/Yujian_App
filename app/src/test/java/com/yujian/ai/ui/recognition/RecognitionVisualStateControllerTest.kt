package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test
    fun v31SequenceWaitsForEachRealEventAndMinimumBeatWithoutFixedTotal() {
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
        assertEquals(940L, controller.resolveFadeStartAtMs())
        assertEquals(1f, controller.resolveStrength(939), 0f)
        assertEquals(1f, controller.resolveStrength(940), 0f)
        assertEquals(.25f, controller.resolveStrength(1_040), .0001f)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_139))
        assertEquals(0f, controller.resolveStrength(1_140), 0f)
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
        assertEquals(null, controller.resolveFadeStartAtMs())
        assertEquals(1f, controller.resolveStrength(10_001), 0f)
        assertEquals(RecognitionPhase.RESULT, controller.current(10_001))
    }

    @Test
    fun lateResultInsideMinimumBeatDoesNotStartPartialResolveOrExtendHandoff() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.current(220)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221, hasRealFishFocus = true)
        controller.current(540)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 541, hasRealFishFocus = true)
        controller.current(890)

        controller.onPipelinePhase(RecognitionPhase.RESULT, 1_000, hasRealFishFocus = true)
        assertEquals(null, controller.resolveFadeStartAtMs())
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_139))
        assertEquals(RecognitionPhase.RESULT, controller.current(1_140))
    }

    @Test
    fun classifyingDoesNotResolveUntilTheRealResultArrives() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.current(220)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221, hasRealFishFocus = true)
        controller.current(540)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 541, hasRealFishFocus = true)

        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(10_000))
        assertEquals(1f, controller.resolveStrength(10_000), 0f)
        assertEquals(null, controller.resolveFadeStartAtMs())
    }

    @Test
    fun noTimerOnlyProgressWithoutDetectionFocusClassificationOrResult() {
        val controller = RecognitionVisualStateController().also { it.reset(10_000) }

        assertEquals(RecognitionPhase.CAPTURED, controller.current(10_219))
        assertEquals(1f, controller.resolveStrength(10_000), 0f)
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 10_219)
        assertEquals(RecognitionPhase.CAPTURED, controller.current(10_219))
        assertEquals(RecognitionPhase.DETECTING, controller.current(10_220))

        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 10_351, hasRealFishFocus = false)
        assertEquals(RecognitionPhase.DETECTING, controller.current(20_000))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 20_001, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(20_001))

        assertEquals(RecognitionPhase.OUTLINE, controller.current(30_000))
        assertEquals(1f, controller.resolveStrength(30_000), 0f)
    }

    @Test
    fun failureExitsProcessingImmediately() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.FAILURE, 25)

        assertEquals(RecognitionPhase.FAILURE, controller.current(25))
    }
}
