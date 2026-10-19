package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOpticalConfigV2Test {
    @Test
    fun usesNormalizedReferenceWidthAndPreservesCornerStrengthRoles() {
        assertEquals("2.0.0", RecognitionOpticalConfigV2.VERSION)
        assertEquals(.85f, RecognitionOpticalConfigV2.islandStrength("G1"), .0001f)
        assertEquals(1.00f, RecognitionOpticalConfigV2.islandStrength("B1"), .0001f)
        assertEquals(.75f, RecognitionOpticalConfigV2.islandStrength("G3"), .0001f)
        assertEquals(.70f, RecognitionOpticalConfigV2.islandStrength("B3"), .0001f)
        assertEquals(0f, RecognitionOpticalConfigV2.islandStrength("unknown"), .0001f)

        val reference = RecognitionOpticalConfigV2.layerDimensions(
            displayedPhotoWidthPx = RecognitionOpticalConfigV2.REFERENCE_WIDTH_PX,
            quality = RecognitionQualityLevel.FULL,
        )
        val device = RecognitionOpticalConfigV2.layerDimensions(
            displayedPhotoWidthPx = 1080f,
            quality = RecognitionQualityLevel.FULL,
        )
        assertEquals(2.6f, reference.coreWidthPx, .001f)
        assertEquals(18f, reference.innerRadiusPx, .001f)
        assertEquals(.78f, RecognitionOpticalConfigV2.INNER_BLOOM_ALPHA_MODIFIER, .001f)
        assertEquals(54f, reference.outerRadiusPx, .001f)
        assertEquals(.74f, RecognitionOpticalConfigV2.OUTER_BLOOM_ALPHA_MODIFIER, .001f)
        assertEquals(98f, reference.spillRadiusPx, .001f)
        assertEquals(reference.coreWidthPx * 1.8f, device.coreWidthPx, .001f)
        assertEquals(reference.spillRadiusPx * 1.8f, device.spillRadiusPx, .001f)
    }

    @Test
    fun brightBackgroundGainIsLocalBoundedAndNeverReducesPhotoExposure() {
        assertEquals(1f, RecognitionOpticalConfigV2.localBackgroundGain(.20f), .0001f)
        assertEquals(1f, RecognitionOpticalConfigV2.localBackgroundGain(.46f), .0001f)
        assertTrue(RecognitionOpticalConfigV2.localBackgroundGain(.72f) in 1f..1.28f)
        assertEquals(1.28f, RecognitionOpticalConfigV2.localBackgroundGain(1f), .0001f)
    }

    @Test
    fun lowQualityKeepsTheFourColorIslandsAndReducesOnlyBroadLayers() {
        val high = RecognitionOpticalConfigV2.layerDimensions(600f, RecognitionQualityLevel.FULL)
        val medium = RecognitionOpticalConfigV2.layerDimensions(600f, RecognitionQualityLevel.BALANCED)
        val low = RecognitionOpticalConfigV2.layerDimensions(600f, RecognitionQualityLevel.LITE)
        assertEquals(high.coreWidthPx, medium.coreWidthPx, .0001f)
        assertEquals(high.coreWidthPx, low.coreWidthPx, .0001f)
        assertEquals(high.innerRadiusPx, low.innerRadiusPx, .0001f)
        assertEquals(high.outerRadiusPx * .75f, medium.outerRadiusPx, .0001f)
        assertEquals(high.outerRadiusPx * .50f, low.outerRadiusPx, .0001f)
        assertEquals(high.spillRadiusPx * .75f, medium.spillRadiusPx, .0001f)
        assertEquals(high.spillRadiusPx * .50f, low.spillRadiusPx, .0001f)
        assertTrue(high.outerRadiusPx > medium.outerRadiusPx)
        assertTrue(medium.outerRadiusPx > low.outerRadiusPx)
        assertTrue(high.spillRadiusPx > low.spillRadiusPx)
        assertEquals(.85f, RecognitionOpticalConfigV2.islandStrength("G1"), .0001f)
        assertEquals(1f, RecognitionOpticalConfigV2.islandStrength("B1"), .0001f)
        assertEquals(.75f, RecognitionOpticalConfigV2.islandStrength("G3"), .0001f)
        assertEquals(.70f, RecognitionOpticalConfigV2.islandStrength("B3"), .0001f)
    }
}
