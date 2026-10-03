package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFishFocusTest {
    @Test
    fun realOutlineBoxCreatesOneBoundedTransientHaloThatSettlesToTheFrozenFloor() {
        val start = recognitionOutlineHaloAlpha(0L, reduceMotion = false)
        val peak = recognitionOutlineHaloAlpha(120L, reduceMotion = false)
        val settling = recognitionOutlineHaloAlpha(300L, reduceMotion = false)
        val settled = recognitionOutlineHaloAlpha(420L, reduceMotion = false)

        assertTrue(start >= .10f)
        assertEquals(.18f, peak, .001f)
        assertTrue(settling < peak)
        assertEquals(.10f, settled, .001f)
        assertTrue((0L..1_000L step 20L).all { recognitionOutlineHaloAlpha(it, false) <= .18f })
    }

    @Test
    fun reduceMotionKeepsAStableSemanticFishFocusWithoutTheTransientPulse() {
        val first = recognitionOutlineHaloAlpha(0L, reduceMotion = true)
        val later = recognitionOutlineHaloAlpha(1_000L, reduceMotion = true)

        assertEquals(.15f, first, .001f)
        assertEquals(first, later, .001f)
    }

    @Test
    fun levelBIsPerceptibleOnTheFirstOutlineFrameBeforeContourPromotion() {
        val halo = recognitionLevelBHaloAlpha(0L, reduceMotion = false)
        val perimeter = recognitionLevelBPerimeterAlpha(0L, reduceMotion = false)

        assertTrue("Level B halo must be immediately visible", halo >= .24f)
        assertTrue("Level B perimeter must be immediately visible", perimeter >= .42f)
        assertTrue(recognitionLevelBHaloAlpha(120L, false) > halo)
        assertTrue(recognitionLevelBPerimeterAlpha(120L, false) > perimeter)
    }

    @Test
    fun levelAPromotionWaitsForTheLevelBBeatButKeepsFallbacksBounded() {
        assertEquals(
            RecognitionFishFocusLevel.B,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.A,
                levelAAvailable = false,
                phaseElapsedMs = 0L,
                phaseOverrideActive = false,
            ),
        )
        assertEquals(
            RecognitionFishFocusLevel.B,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.A,
                levelAAvailable = true,
                phaseElapsedMs = FISH_FOCUS_A_PROMOTION_DELAY_MS - 1L,
                phaseOverrideActive = false,
            ),
        )
        assertEquals(
            RecognitionFishFocusLevel.A,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.A,
                levelAAvailable = true,
                phaseElapsedMs = FISH_FOCUS_A_PROMOTION_DELAY_MS,
                phaseOverrideActive = false,
            ),
        )
        assertEquals(
            RecognitionFishFocusLevel.C,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.C,
                levelAAvailable = true,
                phaseElapsedMs = 0L,
                phaseOverrideActive = false,
            ),
        )
    }

    @Test
    fun levelARadiiUseFrozenFourteenDpOpticalPadding() {
        val transform = calculateRecognitionImageTransform(1080f, 2340f, 1152, 1536)
        val box = NormalizedFishBox(.20f, .25f, .80f, .75f)
        val mapped = transform.mapBoxRect(box)
        val radii = recognitionFishFocusRadii(
            transform,
            box,
            RecognitionFishFocusLevel.A,
            paddingPx = 14f,
        )

        assertEquals(mapped.width * .62f + 14f, radii.radiusX, .001f)
        assertEquals(mapped.height * .72f + 14f, radii.radiusY, .001f)
    }
}

