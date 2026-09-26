package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test fun fastResultRetainsAllFrozenStagesUntil2800ms() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.RESULT, 10)
        assertEquals(RecognitionPhase.CAPTURED, controller.current(349))
        assertEquals(RecognitionPhase.DETECTING, controller.current(350))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(950))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_550))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_799))
        assertEquals(RecognitionPhase.RESULT, controller.current(2_800))
    }

    @Test fun holdsEachStageForV11FrozenMinimum() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        assertEquals(RecognitionPhase.CAPTURED, controller.current(349))
        assertEquals(RecognitionPhase.DETECTING, controller.current(350))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 221)
        assertEquals(RecognitionPhase.DETECTING, controller.current(949))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(950))
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 951)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(1_549))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_550))
    }

    @Test fun finalFishFocusIsStableForAtLeastOneSecondBeforeResolve() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.RESULT, 1)
        controller.current(350); controller.current(950); controller.current(1_550)
        assertEquals(0f, controller.resolveProgress(2_600))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_600))
        assertEquals(1f, controller.resolveProgress(2_750))
    }

    @Test fun slowDetectorCannotInventOutline() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        assertEquals(RecognitionPhase.DETECTING, controller.current(350))
        assertEquals(RecognitionPhase.DETECTING, controller.current(5_000))
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 5_001)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(5_001))
    }

    @Test fun slowClassifierStaysClassifyingUntilRealResult() {
        val controller = RecognitionVisualStateController(); controller.reset(0)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 1)
        controller.current(350); controller.current(950); controller.current(1_550)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(10_000))
    }
}
