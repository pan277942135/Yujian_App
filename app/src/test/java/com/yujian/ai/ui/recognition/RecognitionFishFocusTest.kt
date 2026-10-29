package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFishFocusTest {
    @Test
    fun goldContourStrengthPeaksAt1550AndHoldsThroughP7WithoutBreathing() {
        assertEquals(0f, recognitionFishGoldStrength(949), 0f)
        assertEquals(.15f, recognitionFishGoldStrength(950), 0f)
        assertEquals(.45f, recognitionFishGoldStrength(1_200), 0f)
        assertEquals(1f, recognitionFishGoldStrength(1_550), 0f)
        assertEquals(1f, recognitionFishGoldStrength(2_050), 0f)
        assertEquals(1f, recognitionFishGoldStrength(2_600), 0f)
        assertEquals(1f, recognitionFishGoldStrength(2_800), 0f)
        assertEquals(.18f, recognitionFishGoldStrength(2_800, reduceMotion = true), 0f)
    }

    @Test
    fun untrustedMaskNeverEnablesAnyFishShapeAndTrustedMaskIsPhaseGated() {
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = false))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.CLASSIFYING, maskTrusted = false))
        assertTrue(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = true))
        assertTrue(recognitionMaskContourEnabled(RecognitionPhase.CLASSIFYING, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.CAPTURED, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.RESULT, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.FAILURE, maskTrusted = true))
    }

    @Test
    fun lateTrustedMaskAlwaysStartsAtZeroAndRevealsOnTheSharedProcessingClock() {
        assertEquals(0f, recognitionMaskContourRevealProgress(1_900L, null), 0f)
        assertEquals(0f, recognitionMaskContourRevealProgress(2_100L, 2_100L), 0f)
        assertEquals(.5f, recognitionMaskContourRevealProgress(2_600L, 2_100L), 0.0001f)
        assertEquals(1f, recognitionMaskContourRevealProgress(3_100L, 2_100L), 0f)
        assertEquals(1f, recognitionMaskContourRevealProgress(2_100L, null, reduceMotion = true), 0f)
    }

    @Test
    fun noMaskMayBeRecordedAsLowCapabilityButDoesNotPromoteToAContour() {
        assertEquals(
            RecognitionFishFocusLevel.B,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.A,
                levelAAvailable = false,
                phaseElapsedMs = 0L,
                phaseOverrideActive = false,
            ),
        )
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = false))
    }
}
