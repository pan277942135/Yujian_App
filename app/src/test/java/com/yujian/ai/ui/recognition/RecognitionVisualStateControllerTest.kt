package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionVisualStateControllerTest {
    private fun readyForDetectorPath(clock: RecognitionVisualStateController) {
        clock.onPipelinePhase(RecognitionPhase.RESULT, 10L, hasRealFishFocus = true)
        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, false, false, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, false, false, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, false, false, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, false, false, true))
    }

    @Test fun classifierFirstWaitsForSlimSamTerminalAndThenUsesTrustedFishFocus() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        readyForDetectorPath(clock)

        // The Classifier result is saved, but it cannot reveal while SlimSAM is pending.
        clock.onDetectorPathPresented()
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(60_000L, false, false, true))
        assertFalse(clock.resultVisualReady(60_000L))

        clock.onTrustedMaskAvailable(60_001L)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(60_001L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_B, clock.currentSceneStage(60_251L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(60_501L, true, true, true))
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, clock.currentSceneStage(60_751L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(61_201L, true, true, true))
        assertTrue("Result T0 is the start of the shared Orb-return stage", clock.resultVisualReady(61_201L))
        assertEquals(0L, clock.revealElapsedMs(61_201L))
        assertEquals(RecognitionSceneStage.ORB_TO_NAME, clock.currentSceneStage(61_581L, true, true, true))
        assertEquals(RecognitionSceneStage.SPECIES_NAME_REVEAL, clock.currentSceneStage(61_751L, true, true, true))
        assertEquals(RecognitionSceneStage.RESIDUE_SETTLE, clock.currentSceneStage(61_881L, true, true, true))
        assertFalse(clock.revealSettled(62_050L))
        assertTrue(clock.revealSettled(62_051L))
    }

    @Test fun slowClassifierWaitsAtPathEvenWhenTrustedMaskArrivesFirst() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onTrustedMaskAvailable(1_000L)
        clock.onPipelinePhase(RecognitionPhase.OUTLINE, 1_000L, hasRealFishFocus = true)

        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, true, true, false))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, true, true, false))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, true, true, false))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, true, true, false))
        clock.onDetectorPathPresented()
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(30_000L, true, true, false))
        assertEquals("A slow Classifier cannot enter the contour branch", 1_200L,
            clock.opticalReferenceElapsedMs(30_000L, true, true))
        assertFalse(clock.resultVisualReady(30_000L))

        clock.onPipelinePhase(RecognitionPhase.RESULT, 30_001L, hasRealFishFocus = true)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(30_001L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_B, clock.currentSceneStage(30_251L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(30_501L, true, true, true))
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, clock.currentSceneStage(30_751L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(31_201L, true, true, true))
    }

    @Test fun untrustedTerminalMaskUsesOrbOnlyRouteWithoutFakeContour() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 0L, hasRealFishFocus = false)
        clock.onRevealDecisionReady()

        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, false, true, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, false, true, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, false, true, true))
        // Without a real Detector target, the scene skips PATH_TO_FISH and does not fabricate one.
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(1_850L, false, true, true))
        assertTrue(clock.resultVisualReady(1_850L))
        assertEquals(RecognitionPhase.CLASSIFYING, clock.presentedPhase())
    }

    @Test fun detectorPathPresentationMustMatchCurrentPhotoAndGeometry() {
        val identity = RecognitionSceneIdentity("s1", "a1", "i1", "sha1", 4L)
        val clock = RecognitionVisualStateController().also {
            it.reset(0L, identity = identity, geometryVersion = 7L)
        }
        readyForDetectorPath(clock)
        clock.onSceneGeometryChanged(identity, 8L)
        clock.onDetectorPathPresented(identity, 7L)
        clock.onPipelinePhase(RecognitionPhase.RESULT, 2_000L, hasRealFishFocus = true)
        clock.onTrustedMaskAvailable(2_000L)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(2_070L, true, true, true))
        clock.onDetectorPathPresented(identity, 8L)
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(2_071L, true, true, true))
    }

    @Test fun resultFirstPresentedFrameAnchorsRr09OnTheSharedController() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.RESULT, 0L, hasRealFishFocus = true)
        clock.onTrustedMaskAvailable(0L)
        clock.onRevealDecisionReady()
        assertEquals(RecognitionSceneStage.ORB_WAKE, clock.currentSceneStage(120L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, clock.currentSceneStage(470L, true, true, true))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, clock.currentSceneStage(1_670L, true, true, true))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, clock.currentSceneStage(1_850L, true, true, true))
        clock.onDetectorPathPresented()
        assertEquals(RecognitionSceneStage.CONTOUR_A, clock.currentSceneStage(2_070L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_B, clock.currentSceneStage(2_320L, true, true, true))
        assertEquals(RecognitionSceneStage.CONTOUR_C, clock.currentSceneStage(2_570L, true, true, true))
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, clock.currentSceneStage(2_820L, true, true, true))
        assertEquals(RecognitionSceneStage.ORB_RETURN, clock.currentSceneStage(3_270L, true, true, true))

        assertTrue(clock.anchorResultHandoffAt(3_310L))
        assertFalse(clock.anchorResultHandoffAt(3_330L))
        assertEquals(0L, clock.revealElapsedMs(3_310L))
        assertEquals(190L, clock.revealElapsedMs(3_500L))
        assertEquals(RecognitionSceneStage.ORB_TO_NAME, clock.currentSceneStage(3_690L, true, true, true))
        assertEquals(380L, clock.revealElapsedMs(3_690L))
    }

    @Test fun failureExitsImmediately() {
        val clock = RecognitionVisualStateController().also { it.reset(0L) }
        clock.onPipelinePhase(RecognitionPhase.FAILURE, 25L)
        assertEquals(RecognitionPhase.FAILURE, clock.current(25L))
    }
}
