package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionMotionPolicyTest {
    @Test
    fun frozenD0ThroughD4LadderDegradesAmbientBeforeFishFocus() {
        val expected = listOf(
            RecognitionQualityLevel.FULL to RecognitionFishFocusLevel.A,
            RecognitionQualityLevel.BALANCED to RecognitionFishFocusLevel.A,
            RecognitionQualityLevel.LITE to RecognitionFishFocusLevel.A,
            RecognitionQualityLevel.LITE to RecognitionFishFocusLevel.B,
            RecognitionQualityLevel.LITE to RecognitionFishFocusLevel.C,
        )

        expected.forEachIndexed { index, pair ->
            val policy = RecognitionMotionPolicy(
                degradationLevel = RecognitionDegradationLevel.values()[index],
            )
            assertEquals(pair.first, policy.qualityLevel)
            assertEquals(pair.second, policy.fishFocusLevel)
        }
    }

    @Test
    fun lowPerformanceCompatibilityInputMapsToD2AndPreservesFocusA() {
        val policy = RecognitionMotionPolicy(lowPerformance = true)

        assertEquals(RecognitionDegradationLevel.D2, policy.degradationLevel)
        assertEquals(RecognitionQualityLevel.LITE, policy.qualityLevel)
        assertEquals(RecognitionFishFocusLevel.A, policy.fishFocusLevel)
    }

    @Test
    fun reduceMotionIsIndependentOfDegradationLevel() {
        val policy = RecognitionMotionPolicy(
            reduceMotion = true,
            degradationLevel = RecognitionDegradationLevel.D0,
        )

        assertEquals(RecognitionQualityLevel.FULL, policy.qualityLevel)
        assertEquals(RecognitionFishFocusLevel.A, policy.fishFocusLevel)
    }
}
