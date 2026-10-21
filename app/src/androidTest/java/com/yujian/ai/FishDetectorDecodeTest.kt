package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.DetectorOrientationAttempt
import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishDetectorDecodeTest {
    @Test
    fun decodeUsesObjectnessTimesFishProbability() {
        val rows = arrayOf(floatArrayOf(208f, 208f, 100f, 100f, .9f, .9f))
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        val detection = detections.single()
        assertEquals(.81f, detection.confidence, 1e-6f)
        assertEquals(158f / 416f, detection.box.x1, 1e-6f)
        assertEquals(258f / 416f, detection.box.x2, 1e-6f)
    }

    @Test
    fun weakThresholdFiltersRowsBeforeNms() {
        val rows = arrayOf(
            floatArrayOf(208f, 208f, 100f, 100f, .9f, .9f),
            floatArrayOf(120f, 120f, 50f, 50f, .2f, .5f),
        )
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        assertTrue(detections.single().confidence >= .2f)
    }

    @Test
    fun nmsKeepsHigherConfidenceOverlappingFish() {
        val rows = arrayOf(
            floatArrayOf(208f, 208f, 120f, 120f, .95f, .95f),
            floatArrayOf(211f, 211f, 120f, 120f, .80f, .90f),
        )
        val detections = FishDetectorEngine.decodeAndNms(
            rows = rows,
            scale = 1f,
            sourceWidth = 416,
            sourceHeight = 416,
        )
        assertEquals(1, detections.size)
        assertEquals(.9025f, detections.single().confidence, 1e-6f)
    }

    @Test
    fun nonSquareTopLeftLetterboxDecodeMapsBackToSourceCoordinates() {
        // 300x500 -> 249x416 at scale 0.832, with content anchored at (0, 0).
        // The 167px horizontal padding is on the right, so x needs scale removal
        // but no left-padding subtraction.
        val detections = FishDetectorEngine.decodeAndNms(
            rows = arrayOf(floatArrayOf(124.8f, 208f, 83.2f, 166.4f, .9f, .8f)),
            scale = .832f,
            sourceWidth = 300,
            sourceHeight = 500,
        )
        val box = detections.single().box
        assertEquals(1f / 3f, box.x1, 1e-6f)
        assertEquals(.3f, box.y1, 1e-6f)
        assertEquals(2f / 3f, box.x2, 1e-6f)
        assertEquals(.7f, box.y2, 1e-6f)
    }

    @Test
    fun clockwiseAndCounterClockwiseBoxesMapBackAtAllFourCorners() {
        val sourceCorners = listOf(
            NormalizedFishBox(0f, 0f, .1f, .1f),
            NormalizedFishBox(.9f, 0f, 1f, .1f),
            NormalizedFishBox(0f, .9f, .1f, 1f),
            NormalizedFishBox(.9f, .9f, 1f, 1f),
        )
        val clockwiseCorners = listOf(
            NormalizedFishBox(.9f, 0f, 1f, .1f),
            NormalizedFishBox(.9f, .9f, 1f, 1f),
            NormalizedFishBox(0f, 0f, .1f, .1f),
            NormalizedFishBox(0f, .9f, .1f, 1f),
        )
        val counterClockwiseCorners = listOf(
            NormalizedFishBox(0f, .9f, .1f, 1f),
            NormalizedFishBox(0f, 0f, .1f, .1f),
            NormalizedFishBox(.9f, .9f, 1f, 1f),
            NormalizedFishBox(.9f, 0f, 1f, .1f),
        )

        sourceCorners.indices.forEach { index ->
            assertBoxEquals(
                sourceCorners[index],
                FishDetectorEngine.mapBoxToOriginal(clockwiseCorners[index], DetectorOrientationAttempt.CW90),
            )
            assertBoxEquals(
                sourceCorners[index],
                FishDetectorEngine.mapBoxToOriginal(counterClockwiseCorners[index], DetectorOrientationAttempt.CCW90),
            )
        }
    }

    @Test
    fun rotatedNonSquareDetectionBoxRoundTripsWithoutWidthHeightSwap() {
        val expected = NormalizedFishBox(.13f, .21f, .72f, .89f)
        val clockwiseCandidate = NormalizedFishBox(.11f, .13f, .79f, .72f)
        val counterClockwiseCandidate = NormalizedFishBox(.21f, .28f, .89f, .87f)

        assertBoxEquals(
            expected,
            FishDetectorEngine.mapBoxToOriginal(clockwiseCandidate, DetectorOrientationAttempt.CW90),
        )
        assertBoxEquals(
            expected,
            FishDetectorEngine.mapBoxToOriginal(counterClockwiseCandidate, DetectorOrientationAttempt.CCW90),
        )
    }

    private fun assertBoxEquals(expected: NormalizedFishBox, actual: NormalizedFishBox) {
        assertEquals(expected.x1, actual.x1, 1e-6f)
        assertEquals(expected.y1, actual.y1, 1e-6f)
        assertEquals(expected.x2, actual.x2, 1e-6f)
        assertEquals(expected.y2, actual.y2, 1e-6f)
    }
}
