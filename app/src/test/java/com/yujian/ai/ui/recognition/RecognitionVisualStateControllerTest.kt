package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test fun fastResultCannotSkipPresentedStages() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 1, hasRealFishFocus = true)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionPhase.DETECTING, clock.current(10_000))
        assertEquals(0L, clock.phaseElapsedMs(10_000))
        assertEquals(RecognitionPhase.DETECTING, clock.current(10_699))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(10_700))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(11_450))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(11_799))
        assertEquals(RecognitionPhase.RESULT, clock.current(11_800))
        assertEquals(2_600L, clock.opticalReferenceElapsedMs(11_800))
        assertEquals(2_700L, clock.opticalReferenceElapsedMs(11_900))
        assertEquals(2_800L, clock.opticalReferenceElapsedMs(12_000))
        assertEquals(false, clock.resultVisualReady(11_999))
        assertEquals(true, clock.resultVisualReady(12_000))
    }

    @Test fun lateMaskReceivesFullRevealAndGoldHold() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 1, hasRealFishFocus = true)
        clock.current(180)
        clock.current(880)
        clock.current(1_630)
        clock.onTrustedMaskAvailable(2_000)
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(2_800, trustedMask = true))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(3_199, trustedMask = true))
        clock.onRevealDecisionReady()
        assertEquals(RecognitionPhase.RESULT, clock.current(3_200, trustedMask = true))
        assertEquals(2_600L, clock.opticalReferenceElapsedMs(3_200, trustedMask = true))
    }

    @Test fun slowClassifierRestsAtQuietP6WithoutEnteringReturnStage() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        clock.onPipelinePhase(RecognitionPhase.OUTLINE, 10, hasRealFishFocus = true)
        clock.onPipelinePhase(RecognitionPhase.CLASSIFYING, 20, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, clock.current(180))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(880))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(1_630))
        assertEquals(2_600L, clock.opticalReferenceElapsedMs(2_200))
        assertEquals(2_600L, clock.opticalReferenceElapsedMs(20_000))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(20_000))
        assertEquals(false, clock.resultVisualReady(20_000))
        clock.onRevealDecisionReady()
        assertEquals(RecognitionPhase.RESULT, clock.current(20_000))
        assertEquals(false, clock.resultVisualReady(20_199))
        assertEquals(true, clock.resultVisualReady(20_200))
    }

    @Test fun outlineClockHoldsAtPathToFishUntilSlimSamIsTerminal() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.OUTLINE, 10, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, clock.current(180, subjectTerminal = false))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(880, subjectTerminal = false))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(1_193, subjectTerminal = false))
        assertEquals(1_200L, clock.opticalReferenceElapsedMs(1_193, subjectTerminal = false))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(5_000, subjectTerminal = false))
        assertEquals(1_200L, clock.opticalReferenceElapsedMs(5_000, subjectTerminal = false))
        assertEquals(RecognitionPhase.OUTLINE, clock.current(5_000, subjectTerminal = true))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.current(5_437, subjectTerminal = true))
    }

    @Test fun failureExitsImmediately() {
        val clock = RecognitionVisualStateController().also { it.reset(0) }
        clock.onPipelinePhase(RecognitionPhase.FAILURE, 25)
        assertEquals(RecognitionPhase.FAILURE, clock.current(25))
    }
}
