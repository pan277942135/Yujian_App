package com.yujian.ai.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yujian.ai.ui.home.CaptureVisualTokens

class CaptureZoomMappingTest {
    @Test fun zoomDialRoundTripsDeviceRangesIncludingUltraWideAndNoZoom() {
        for ((min, max) in listOf(.6f to 10f, 1f to 4f, 1f to 1f)) {
            for (i in 0..100) {
                val ratio = min + (max - min) * i / 100f
                val fraction = captureZoomFraction(ratio, min, max)
                assertTrue(fraction in 0f..1f)
                assertEquals(ratio, captureZoomRatio(fraction, min, max), .0001f)
            }
            assertEquals(min, captureZoomRatio(-1f, min, max), .0001f)
            assertEquals(max, captureZoomRatio(2f, min, max), .0001f)
        }
    }
    @Test fun activationCompletesOnceWithoutWrappingWhenCameraIsSlow() {
        assertEquals(0f, CaptureVisualTokens.activationProgress(-1), 0f)
        assertEquals(.5f, CaptureVisualTokens.activationProgress(375), 0f)
        for (elapsed in listOf(750L, 1000L, 5000L)) assertEquals(1f, CaptureVisualTokens.activationProgress(elapsed), 0f)
    }
}
