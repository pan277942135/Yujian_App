package com.yujian.ai.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishDetectorOrientationTest {
    @Test
    fun cw90DetectionMapsBackToOriginalNormalizedCoordinates() {
        val original = FishDetectorEngine.mapBoxToOriginal(
            NormalizedFishBox(0.1f, 0.2f, 0.5f, 0.8f),
            DetectorOrientationAttempt.CW90,
        )

        assertEquals(0.2f, original.x1, 0.00001f)
        assertEquals(0.5f, original.y1, 0.00001f)
        assertEquals(0.8f, original.x2, 0.00001f)
        assertEquals(0.9f, original.y2, 0.00001f)
    }

    @Test
    fun ccw90DetectionMapsBackToOriginalBeforeQualityGateAndCrop() {
        val rotated = NormalizedFishBox(
            x1 = 0.363650f,
            y1 = 0.394564f,
            x2 = 0.924975f,
            y2 = 0.657070f,
        )

        val original = FishDetectorEngine.mapBoxToOriginal(
            rotated,
            DetectorOrientationAttempt.CCW90,
        )
        val assessment = FishDetectionQualityGate.assess(
            listOf(FishDetection(confidence = 0.635500f, box = original)),
        )

        assertEquals(0.342930f, original.x1, 0.00001f)
        assertEquals(0.363650f, original.y1, 0.00001f)
        assertEquals(0.605436f, original.x2, 0.00001f)
        assertEquals(0.924975f, original.y2, 0.00001f)
        assertEquals(FishInputStatus.READY, assessment.status)
        assertTrue(assessment.isClassifierEligible)
        assertEquals(0.147351f, requireNotNull(assessment.bboxAreaRatio), 0.00001f)

        val crop = FishDetectionQualityGate.cropBoxPixels(
            requireNotNull(assessment.cropBox),
            width = 1152,
            height = 1536,
        )
        assertArrayEquals(intArrayOf(349, 429, 743, 1536), crop)
    }

    @Test
    fun retryIsAllowedOnlyForNoFishAssessment() {
        val noFish = FishDetectionQualityGate.assess(emptyList())
        val weak = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.25f, NormalizedFishBox(0.1f, 0.1f, 0.8f, 0.8f))),
        )
        val strong = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.9f, NormalizedFishBox(0.1f, 0.1f, 0.8f, 0.8f))),
        )

        assertTrue(FishDetectorEngine.shouldRetryAfter(noFish))
        assertFalse(FishDetectorEngine.shouldRetryAfter(weak))
        assertFalse(FishDetectorEngine.shouldRetryAfter(strong))
    }

    @Test
    fun orientationWireNamesAndOrderMatchTheBoundedRuntimeContract() {
        assertEquals(
            listOf("ORIGINAL", "CW90", "CCW90"),
            FishDetectorEngine.ORIENTATION_ATTEMPTS.map { it.wireName },
        )
        assertEquals(
            "DETECTOR_ORIENTATION_RETRY_v2",
            FishDetectorEngine.ORIENTATION_RETRY_POLICY_VERSION,
        )
    }

    @Test
    fun originalNoFishRunsBothRotationsAndSelectsGoodOverEarlierWarning() {
        val original = FishDetectionQualityGate.assess(emptyList())
        val cwWarning = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.23f, NormalizedFishBox(0.2f, 0.2f, 0.8f, 0.8f))),
        )
        val ccwGood = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.71f, NormalizedFishBox(0.2f, 0.2f, 0.8f, 0.8f))),
        )

        assertEquals(
            listOf(DetectorOrientationAttempt.ORIGINAL, DetectorOrientationAttempt.CW90, DetectorOrientationAttempt.CCW90),
            FishDetectorEngine.attemptsForOriginalAssessment(original),
        )
        assertEquals(FishQualityLevel.WARNING, cwWarning.qualityLevel)
        assertTrue(cwWarning.isClassifierEligible)
        assertEquals(FishQualityLevel.GOOD, ccwGood.qualityLevel)
        val selected = FishDetectorEngine.selectRecoveredAssessment(
            listOf(
                DetectorAssessmentCandidate(DetectorOrientationAttempt.CW90, cwWarning),
                DetectorAssessmentCandidate(DetectorOrientationAttempt.CCW90, ccwGood),
            ),
        )

        assertEquals(DetectorOrientationAttempt.CCW90, selected?.orientation)
        assertEquals("GOOD_HIGHEST_RANK_SCORE", FishDetectorEngine.selectionReasonFor(requireNotNull(selected).assessment))
    }

    @Test
    fun sameQualityClassSelectsHigherExistingRankScoreDeterministically() {
        val cwGood = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.55f, NormalizedFishBox(0.2f, 0.2f, 0.8f, 0.8f))),
        )
        val ccwGood = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.70f, NormalizedFishBox(0.2f, 0.2f, 0.8f, 0.8f))),
        )

        val selected = FishDetectorEngine.selectRecoveredAssessment(
            listOf(
                DetectorAssessmentCandidate(DetectorOrientationAttempt.CW90, cwGood),
                DetectorAssessmentCandidate(DetectorOrientationAttempt.CCW90, ccwGood),
            ),
        )

        assertEquals(DetectorOrientationAttempt.CCW90, selected?.orientation)
    }

    @Test
    fun originalReadyKeepsOnlyOriginalAttempt() {
        val ready = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.9f, NormalizedFishBox(0.2f, 0.2f, 0.8f, 0.8f))),
        )

        assertEquals(
            listOf(DetectorOrientationAttempt.ORIGINAL),
            FishDetectorEngine.attemptsForOriginalAssessment(ready),
        )
    }

    @Test
    fun allAttemptsNoFishSelectNoneAndPreserveNoFishAssessment() {
        val noFish = FishDetectionQualityGate.assess(emptyList())

        assertEquals(
            listOf(DetectorOrientationAttempt.ORIGINAL, DetectorOrientationAttempt.CW90, DetectorOrientationAttempt.CCW90),
            FishDetectorEngine.attemptsForOriginalAssessment(noFish),
        )
        assertEquals(
            null,
            FishDetectorEngine.selectRecoveredAssessment(
                listOf(
                    DetectorAssessmentCandidate(DetectorOrientationAttempt.CW90, noFish),
                    DetectorAssessmentCandidate(DetectorOrientationAttempt.CCW90, noFish),
                ),
            ),
        )
        assertEquals(FishInputStatus.NO_FISH, noFish.status)
    }
}
