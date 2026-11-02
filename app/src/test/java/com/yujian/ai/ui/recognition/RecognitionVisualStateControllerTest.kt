package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test fun fastModelResultCannotSkipThirteenStagePresentationBeats() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 10L, hasRealFishFocus = true)
        clock.onRevealDecisionReady()

        assertEquals(RecognitionSceneStage.PHOTO_STABLE, clock.currentSceneStage(0L, false, false, true))
        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, false, false, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, false, false, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, false, false, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, false, false, true))

        // A fast Classifier cannot invent a Mask. The scene waits at PATH_TO_FISH.
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(20_000L, false, false, true))
        assertFalse(clock.resultVisualReady(20_000L))

        clock.onTrustedMaskAvailable(20_000L)
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(20_000L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_B, clock.currentSceneStage(20_250L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(20_500L, true, true, true))
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, clock.currentSceneStage(20_750L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(21_200L, true, true, true))
        assertFalse(clock.resultVisualReady(21_579L))
        assertTrue(clock.resultVisualReady(21_580L))
    }

    @Test fun slowClassifierHoldsAtContourCWithoutGlobal2800Or3650Cutoff() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onTrustedMaskAvailable(1_000L)
        clock.onPipelinePhase(RecognitionPhase.OUTLINE, 1_000L, hasRealFishFocus = true)

        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, true, true, false))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, true, true, false))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, true, true, false))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, true, true, false))
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(2_070L, true, true, false))
        assertEquals(RecognitionSceneStage.CONTOUR_B, clock.currentSceneStage(2_320L, true, true, false))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(2_570L, true, true, false))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(30_000L, true, true, false))
        assertEquals(30_000L, clock.opticalReferenceElapsedMs(30_000L, true, true))
        assertFalse(clock.resultVisualReady(30_000L))

        clock.onPipelinePhase(RecognitionPhase.RESULT, 30_000L, hasRealFishFocus = true)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, clock.currentSceneStage(30_000L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(30_450L, true, true, true))
        assertTrue(clock.resultVisualReady(30_830L))
    }

    @Test fun terminalUntrustedMaskUsesOrbOnlyReturnWithoutContourOrGold() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 0L, hasRealFishFocus = false)
        clock.onRevealDecisionReady()

        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, false, true, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, false, true, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, false, true, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, false, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(2_070L, false, true, true))
        assertTrue(clock.resultVisualReady(2_450L))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.presentedPhase())
    }

    @Test fun classifierReadyBeforeSlimSamWaitsForMaskTerminal() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 0L, hasRealFishFocus = true)
        clock.onRevealDecisionReady()

        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, false, false, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, false, false, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, false, false, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, false, false, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(60_000L, false, false, true))
        assertFalse(clock.resultVisualReady(60_000L))

        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(60_001L, false, true, true))
        assertTrue(clock.resultVisualReady(60_381L))
    }

    @Test fun failureExitsImmediately() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.FAILURE, 25L)
        assertEquals(RecognitionPhase.FAILURE, clock.current(25L))
    }
}
