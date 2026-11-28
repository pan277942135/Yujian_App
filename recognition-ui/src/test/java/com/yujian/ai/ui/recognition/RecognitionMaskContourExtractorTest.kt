package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.subject.BoundaryTerminationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionMaskContourExtractorTest {
    @Test fun binaryAlphaProducesOneClosedReferenceContour() {
        val width = 24
        val height = 24
        val alpha = ByteArray(width * height)
        for (y in 4..19) for (x in 5..18) alpha[y * width + x] = 0xff.toByte()

        val result = RecognitionMaskContourExtractor.extract(width, height, alpha)

        assertTrue(result.segments.isNotEmpty())
        assertEquals(1, result.segments.count { it.beginsContour })
        assertEquals(1, result.segments.count { it.closesContour })
        assertTrue(result.traceClosed)
        assertEquals(BoundaryTerminationReason.RETURNED_TO_START, result.terminationReason)
        assertTrue(result.rawPointCount >= 20)
        assertTrue(result.segments.all(::normalizedFinite))
    }

    @Test fun configuredThresholdIsAppliedBeforeFourConnectedLcc() {
        val width = 18
        val height = 18
        val alpha = ByteArray(width * height) { 90.toByte() }
        for (y in 4..13) for (x in 4..13) alpha[y * width + x] = 100.toByte()
        alpha[0] = 255.toByte() // detached noise must not become the returned component

        val result = RecognitionMaskContourExtractor.extract(width, height, alpha, threshold = 100)

        assertTrue(result.traceClosed)
        assertEquals(BoundaryTerminationReason.RETURNED_TO_START, result.terminationReason)
        assertTrue(result.segments.all(::normalizedFinite))
    }

    @Test fun exteriorContourDoesNotCreateASecondLoopForAHole() {
        val width = 24
        val height = 24
        val alpha = ByteArray(width * height)
        for (y in 3..20) for (x in 3..20) {
            if (x !in 10..13 || y !in 10..13) alpha[y * width + x] = 0xff.toByte()
        }

        val result = RecognitionMaskContourExtractor.extract(width, height, alpha)

        assertTrue(result.traceClosed)
        assertEquals(1, result.segments.count { it.beginsContour })
        assertEquals(1, result.segments.count { it.closesContour })
    }

    @Test fun isolatedPixelReturnsAnExplicitUnclosedFailure() {
        val alpha = ByteArray(12 * 12)
        alpha[6 * 12 + 6] = 0xff.toByte()

        val result = RecognitionMaskContourExtractor.extract(12, 12, alpha)

        assertTrue(result.segments.isEmpty())
        assertFalse(result.traceClosed)
        assertEquals(BoundaryTerminationReason.ISOLATED_PIXEL, result.terminationReason)
    }

    @Test fun connectedFinAndTailRemainInThePrimaryClosedContour() {
        val width = 64
        val height = 48
        val alpha = ByteArray(width * height)
        fun fill(x: Int, y: Int) { alpha[y * width + x] = 0xff.toByte() }

        for (y in 18..35) for (x in 10..53) fill(x, y)
        for (y in 23..27) {
            val halfWidth = 9 - kotlin.math.abs(25 - y) * 2
            for (x in 10 - halfWidth..9) fill(x, y)
        }
        for (y in 7..17) {
            val halfWidth = 1 + (y - 7) * 2
            for (x in 30 - halfWidth..30 + halfWidth) fill(x, y)
        }
        fill(61, 2)

        val result = RecognitionMaskContourExtractor.extract(width, height, alpha)

        assertTrue(result.traceClosed)
        assertEquals(1, result.segments.count { it.beginsContour })
        // The reference applies three closed-loop smoothing passes; retain the peak within five
        // pixels of the unsmoothed seven-pixel tip rather than requiring the raw boundary point.
        assertTrue("smoothed fin tip minY=${result.segments.minOf { it.startY }}", result.segments.minOf { it.startY } < .25f)
        assertTrue("smoothed left-fin minX=${result.segments.minOf { it.startX }}", result.segments.minOf { it.startX } < .15f)
        assertTrue(result.segments.all(::normalizedFinite))
    }

    private fun normalizedFinite(segment: RecognitionContourSegment): Boolean = listOf(
        segment.startX, segment.startY, segment.endX, segment.endY,
        segment.control1X, segment.control1Y, segment.control2X, segment.control2Y,
    ).all { it.isFinite() && it in 0f..1f }
}
