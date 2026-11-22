package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOrbReturnPathDrawCoverageTest {
    @Test fun threeSegmentReturnReceiptNeedsCanvasCoverageAcrossTheWholeRoute() {
        val coverage = RecognitionOrbReturnPathDrawCoverage(listOf(100f, 200f, 100f))

        coverage.recordDrawnRange(0f, 90f)
        coverage.recordDrawnRange(90.5f, 250f)
        assertEquals(1, coverage.fullyCoveredSegmentCount())
        assertFalse(coverage.isComplete())

        coverage.recordDrawnRange(250f, 400f)
        assertEquals(3, coverage.fullyCoveredSegmentCount())
        assertTrue(coverage.isComplete())
    }

    @Test fun missingDrawnIntervalsCannotBeFilledByACompletionProgressValue() {
        val coverage = RecognitionOrbReturnPathDrawCoverage(listOf(100f, 100f, 100f))
        coverage.recordDrawnRange(0f, 40f)
        coverage.recordDrawnRange(60f, 300f)

        assertEquals(2, coverage.fullyCoveredSegmentCount())
        assertFalse(coverage.isComplete())
    }

    @Test fun oneSegmentOrbOnlyRouteCanBeProvenWithoutMaskIdentity() {
        val coverage = RecognitionOrbReturnPathDrawCoverage(listOf(240f))
        coverage.recordDrawnRange(0f, 240f)

        assertEquals(1, coverage.fullyCoveredSegmentCount())
        assertTrue(coverage.isComplete())
    }
}
