package com.yujian.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishQualityLevel
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishDetectionQualityGateTest {
    private fun det(conf: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
        FishDetection(conf, NormalizedFishBox(x1, y1, x2, y2))

    @Test
    fun noFish_whenNothingAboveWeakThreshold() {
        val result = FishDetectionQualityGate.assess(emptyList())
        assertEquals(FishInputStatus.NO_FISH, result.status)
        assertEquals(FishQualityLevel.INVALID, result.qualityLevel)
        assertEquals(false, result.isClassifierEligible)
        assertNull(result.primary)
    }

    @Test
    fun weakDetection_isUncertain() {
        val result = FishDetectionQualityGate.assess(listOf(det(.25f, .2f, .2f, .8f, .8f)))
        assertEquals(FishInputStatus.UNCERTAIN, result.status)
        assertEquals(FishQualityLevel.WARNING, result.qualityLevel)
        assertEquals(true, result.isClassifierEligible)
        assertNotNull(result.primary)
        assertNotNull(result.cropBox)
    }

    @Test
    fun singleCompleteFish_isReadyAndExpanded() {
        val result = FishDetectionQualityGate.assess(listOf(det(.92f, .2f, .25f, .8f, .75f)))
        assertEquals(FishInputStatus.READY, result.status)
        assertEquals(FishQualityLevel.GOOD, result.qualityLevel)
        assertEquals(true, result.isClassifierEligible)
        val crop = requireNotNull(result.cropBox)
        check(crop.x1 < .2f && crop.y1 < .25f && crop.x2 > .8f && crop.y2 > .75f)
    }

    @Test
    fun touchingEdge_isWarningAndStillClassifierEligible() {
        val result = FishDetectionQualityGate.assess(listOf(det(.93f, 0f, .2f, .75f, .8f)))
        assertEquals(FishInputStatus.INCOMPLETE_FISH, result.status)
        assertEquals(FishQualityLevel.WARNING, result.qualityLevel)
        assertEquals(true, result.isClassifierEligible)
        assertNotNull(result.cropBox)
        assertEquals("primary_fish_bbox_touches_image_edge", result.qualityReason)
    }

    @Test
    fun smallFish_isRejectedBeforeClassifier() {
        val result = FishDetectionQualityGate.assess(listOf(det(.91f, .40f, .40f, .58f, .58f)))
        assertEquals(FishInputStatus.FISH_TOO_SMALL, result.status)
        assertEquals(FishQualityLevel.INVALID, result.qualityLevel)
        assertEquals(false, result.isClassifierEligible)
    }

    @Test
    fun multipleStrongFish_isExplicitStatus() {
        val result = FishDetectionQualityGate.assess(
            listOf(
                det(.95f, .1f, .2f, .45f, .7f),
                det(.88f, .55f, .2f, .9f, .7f),
            ),
        )
        assertEquals(FishInputStatus.MULTIPLE_FISH, result.status)
        assertEquals(FishQualityLevel.INVALID, result.qualityLevel)
        assertEquals(false, result.isClassifierEligible)
        assertEquals(2, result.strongDetections.size)
    }

    @Test
    fun cropPixelRounding_matchesBackendContract() {
        assertArrayEquals(
            intArrayOf(10, 40, 80, 180),
            FishDetectionQualityGate.cropBoxPixels(
                NormalizedFishBox(.101f, .201f, .799f, .899f),
                100,
                200,
            ),
        )
    }
}
