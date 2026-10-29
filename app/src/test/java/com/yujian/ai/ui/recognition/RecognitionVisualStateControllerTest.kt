package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionVisualStateControllerTest {
    @Test
    fun adaptiveBeatsStartAtStablePhotoAndWaitForTheRealClassifierResult() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 10)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(179))
        assertEquals(RecognitionPhase.DETECTING, controller.current(180))
        assertEquals(350L, controller.opticalReferenceElapsedMs(180))

        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 360, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.DETECTING, controller.current(879))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(880))
        assertEquals(950L, controller.opticalReferenceElapsedMs(880))

        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 900, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.OUTLINE, controller.current(1_629, trustedMask = true))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_630, trustedMask = true))
        assertEquals(1_550L, controller.opticalReferenceElapsedMs(1_630, trustedMask = true))

        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_429, trustedMask = true))
        controller.onPipelinePhase(RecognitionPhase.RESULT, 2_430, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.RESULT, controller.current(2_430, trustedMask = true))
        assertEquals(2_800L, controller.opticalReferenceElapsedMs(2_430, trustedMask = true))
    }

    @Test
    fun missingMaskDoesNotWaitForSlimSamAndResultStillWaitsForClassifier() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 20, hasRealFishFocus = true)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 30, hasRealFishFocus = true)

        assertEquals(RecognitionPhase.DETECTING, controller.current(879))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(880))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(1_629))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_630, trustedMask = false))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_979, trustedMask = false))

        controller.onPipelinePhase(RecognitionPhase.RESULT, 1_980, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.RESULT, controller.current(1_980, trustedMask = false))
    }

    @Test
    fun resultWithoutDetectorBoxUsesNoMaskRouteInsteadOfWaitingForever() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.RESULT, 10, hasRealFishFocus = false)

        assertEquals(RecognitionPhase.CAPTURED, controller.current(179))
        assertEquals(RecognitionPhase.DETECTING, controller.current(180))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(880))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_229))
        assertEquals(RecognitionPhase.RESULT, controller.current(1_230))
    }

    @Test
    fun latestModelPhaseBeforeStablePhotoFrameSeedsAllPriorStageEvents() {
        val controller = RecognitionVisualStateController().also { it.reset(5_000) }
        // The model may finish during the 420ms photo handoff. The scene only
        // has its latest real phase to replay at P0, so later phases imply the
        // earlier pipeline milestones without consuming visual time.
        controller.onPipelinePhase(
            RecognitionPhase.CLASSIFYING,
            nowMs = 5_200,
            hasRealFishFocus = true,
        )

        assertEquals(RecognitionPhase.CAPTURED, controller.current(5_179, trustedMask = false))
        assertEquals(RecognitionPhase.DETECTING, controller.current(5_180, trustedMask = false))
        assertEquals(RecognitionPhase.OUTLINE, controller.current(5_880, trustedMask = false))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(6_630, trustedMask = false))
    }

    @Test
    fun lateMaskInsideOutlineWindowFinishesContourAndKeepsGoldPeakForAtLeast450Ms() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 20, hasRealFishFocus = true)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 30, hasRealFishFocus = true)
        controller.onPipelinePhase(RecognitionPhase.RESULT, 1_630, hasRealFishFocus = true)
        controller.current(180)
        controller.current(880)
        controller.onTrustedMaskAvailable(1_250)

        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(1_630, trustedMask = true))
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(2_449, trustedMask = true))
        assertEquals(RecognitionPhase.RESULT, controller.current(2_450, trustedMask = true))
    }

    @Test
    fun realResultCanWaitInLowDisturbanceClassifyingWithoutReplayingEarlierStages() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.DETECTING, 1)
        controller.onPipelinePhase(RecognitionPhase.OUTLINE, 20, hasRealFishFocus = true)
        controller.onPipelinePhase(RecognitionPhase.CLASSIFYING, 30, hasRealFishFocus = true)
        controller.current(880)
        controller.current(1_630, trustedMask = true)
        assertEquals(RecognitionPhase.CLASSIFYING, controller.current(50_000, trustedMask = true))

        controller.onPipelinePhase(RecognitionPhase.RESULT, 50_001, hasRealFishFocus = true)
        assertEquals(RecognitionPhase.RESULT, controller.current(50_001, trustedMask = true))
    }

    @Test
    fun failureExitsProcessingImmediately() {
        val controller = RecognitionVisualStateController().also { it.reset(0) }
        controller.onPipelinePhase(RecognitionPhase.FAILURE, 25)

        assertEquals(RecognitionPhase.FAILURE, controller.current(25))
    }
}
