package com.yujian.ai.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class RecognitionMaskContourExtractorTest {
    @Test fun marchingSquaresEmitsClosedContourWithSubpixelThresholdCrossings() {
        val width = 6
        val height = 5
        val alpha = ByteArray(width * height)
        val columns = intArrayOf(0, 40, 82, 174, 230, 255)
        for (y in 1..3) for (x in 0 until width) alpha[y * width + x] = columns[x].toByte()

        val contour = RecognitionMaskContourExtractor.extract(width, height, alpha, threshold = 100)
        assertTrue(contour.isNotEmpty())
        assertEquals(1, contour.count { it.beginsContour })
        assertEquals(1, contour.count { it.closesContour })
        val hasSubpixelCrossing = contour.any { segment ->
            val sourceX = segment.startX * width
            kotlin.math.abs(sourceX - sourceX.roundToInt()) > .05f
        }
        assertTrue("Mask alpha interpolation should retain subpixel edge locations", hasSubpixelCrossing)
    }

    @Test fun keepsHoleContoursAndRejectsIsolatedTransparentMaskNoise() {
        val width = 12
        val height = 12
        val alpha = ByteArray(width * height)
        for (y in 2..9) for (x in 2..9) {
            if (x !in 5..6 || y !in 5..6) alpha[y * width + x] = 255.toByte()
        }
        alpha[0] = 255.toByte() // a detached single pixel is below the component floor

        val contour = RecognitionMaskContourExtractor.extract(width, height, alpha)
        assertEquals(2, contour.count { it.beginsContour })
        assertEquals(2, contour.count { it.closesContour })
        assertTrue(contour.all { segment ->
            listOf(segment.startX, segment.startY, segment.endX, segment.endY,
                segment.control1X, segment.control1Y, segment.control2X, segment.control2Y)
                .all { it.isFinite() && it in 0f..1f }
        })
    }

    @Test fun narrowTailAndFinConnectionsStayInTheLargestRealMaskContour() {
        val width = 16
        val height = 10
        val alpha = ByteArray(width * height)
        for (y in 3..6) for (x in 4..11) alpha[y * width + x] = 255.toByte()
        for (x in 1..3) alpha[4 * width + x] = 255.toByte() // tail
        for (x in 7..9) alpha[2 * width + x] = 255.toByte() // fin

        val contour = RecognitionMaskContourExtractor.extract(width, height, alpha)
        assertEquals(1, contour.count { it.beginsContour })
        val allX = contour.flatMap { listOf(it.startX, it.endX) }
        val allY = contour.flatMap { listOf(it.startY, it.endY) }
        assertTrue(allX.minOrNull()!! < .2f)
        assertTrue(allY.minOrNull()!! < .3f)
    }

    @Test fun closedSmoothingKeepsARealMaskFinTipTailAndFiltersDetachedNoise() {
        val width = 64
        val height = 48
        val alpha = ByteArray(width * height)
        fun fill(x: Int, y: Int) { alpha[y * width + x] = 255.toByte() }

        for (y in 18..35) for (x in 10..53) fill(x, y)
        for (y in 23..27) {
            val halfWidth = 9 - kotlin.math.abs(25 - y) * 2
            for (x in 10 - halfWidth..9) fill(x, y)
        }
        for (y in 7..17) {
            val halfWidth = 1 + (y - 7) * 2
            for (x in 30 - halfWidth..30 + halfWidth) fill(x, y)
        }
        fill(61, 2) // Detached one-pixel segmentation noise.

        val contour = RecognitionMaskContourExtractor.extract(width, height, alpha)
        assertEquals(1, contour.count { it.beginsContour })
        assertEquals(1, contour.count { it.closesContour })
        assertTrue("Closed-loop smoothing must retain the pointed dorsal fin", contour.minOf { it.startY } < .18f)
        assertTrue("The actual tail pixels must remain part of the contour", contour.minOf { it.startX } < .08f)
        assertTrue(contour.all { segment ->
            listOf(segment.startX, segment.startY, segment.endX, segment.endY,
                segment.control1X, segment.control1Y, segment.control2X, segment.control2Y)
                .all { it.isFinite() && it in 0f..1f }
        })
    }
}
