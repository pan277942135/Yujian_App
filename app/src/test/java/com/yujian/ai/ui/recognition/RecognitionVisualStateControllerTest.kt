package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test fun fastResultCannotSkipPresentedStages() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 1, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, clock.current(10_000))
        assertEquals(0L, clock.phaseElapsedMs(10_000))
        assertEquals(RecognitionPhase.DETECTING, clock.current(10_699))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(10_700))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(11_450))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(12_249))
        assertEquals(RecognitionPhase.RESULT, clock.current(12_250))
    }

    @Test fun lateMaskReceivesFullRevealAndGoldHold() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 1, hasRealFishFocus = true)
        clock.current(180)
        clock.current(880)
        clock.current(1_630)
        clock.onTrustedMaskAvailable(2_000)
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(2_800, trustedMask = true))
        assertEquals(RecognitionPhase.RESULT, clock.current(3_200, trustedMask = true))
    }

    @Test fun failureExitsImmediately() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.FAILURE, 25)
        assertEquals(RecognitionPhase.FAILURE, clock.current(25))
    }
}
