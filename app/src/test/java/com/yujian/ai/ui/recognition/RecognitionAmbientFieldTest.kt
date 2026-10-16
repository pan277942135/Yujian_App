package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAmbientFieldTest {
    @Test
    fun locatedAndClassifyingFieldStrengthsRecedeBelowOpeningField() {
        val opening = recognitionAmbientStateCalibration(RecognitionPhase.CAPTURED)
        val located = recognitionAmbientStateCalibration(RecognitionPhase.OUTLINE)
        val classifying = recognitionAmbientStateCalibration(RecognitionPhase.CLASSIFYING)

        assertTrue(located.strength < opening.strength)
        assertTrue(classifying.strength < located.strength)
        assertEquals(.58f, located.strength, .001f)
        assertEquals(.36f, classifying.strength, .001f)
        assertEquals(.62f, located.speed, .001f)
        assertEquals(.42f, classifying.speed, .001f)
    }

    @Test
    fun primaryFieldKeepsFourIslandsAndSecondaryPathsRemainFragments() {
        assertEquals(setOf("B1", "G1", "B3", "G3"), recognitionAmbientPrimaryIslandIds)
        assertEquals(setOf("B2", "G2", "B4"), recognitionAmbientSecondaryFragmentIds)
        assertTrue(recognitionAmbientPrimaryIslandIds.intersect(recognitionAmbientSecondaryFragmentIds).isEmpty())
    }

    @Test
    fun liveFieldClockKeepsSegmentPhaseAcrossPipelineStateChanges() {
        val clock = EdgeFieldMotionAccumulator()
        val beforeOutline = clock.sample(
            nowMs = 1_000L,
            phase = RecognitionPhase.DETECTING,
            reduceMotionNow = false,
        )
        val firstOutlineFrame = clock.sample(
            nowMs = 1_016L,
            phase = RecognitionPhase.OUTLINE,
            reduceMotionNow = false,
        )
        val settledOutlineClock = clock.sample(
            nowMs = 1_032L,
            phase = RecognitionPhase.OUTLINE,
            reduceMotionNow = false,
        )

        assertTrue(firstOutlineFrame.cycleOffset >= beforeOutline.cycleOffset)
        assertTrue(
            "phase transition reset or jumped the segment",
            settledOutlineClock.cycleOffset - beforeOutline.cycleOffset < .01f,
        )
        assertTrue(settledOutlineClock.segmentSpeed > .62f)
        assertTrue(settledOutlineClock.segmentSpeed < 1f)
    }
}
