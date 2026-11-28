package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.Color
import com.yujian.ai.ai.subject.BoundaryTerminationReason
import com.yujian.ai.ai.subject.SlimSamContourGeometry

/** Converts the emitted binary-alpha mask through the pinned Fish-Mask contour pipeline. */
internal object RecognitionMaskContourExtractor {
    data class Extraction(
        val segments: List<RecognitionContourSegment>,
        val rawPointCount: Int,
        val traceClosed: Boolean,
        val terminationReason: BoundaryTerminationReason,
    )

    fun extract(bitmap: Bitmap, threshold: Int = 128): Extraction {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return invalid()
        val alpha = ByteArray(Math.multiplyExact(width, height))
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            val offset = y * width
            for (x in 0 until width) alpha[offset + x] = Color.alpha(row[x]).toByte()
        }
        return extract(width, height, alpha, threshold)
    }

    fun extract(width: Int, height: Int, alpha: ByteArray, threshold: Int = 128): Extraction {
        if (width <= 1 || height <= 1 || alpha.size != width * height) return invalid()
        val cut = threshold.coerceIn(1, 254)
        val binaryMask = ByteArray(alpha.size) { index ->
            if ((alpha[index].toInt() and 0xff) >= cut) 0xff.toByte() else 0
        }
        val lcc = SlimSamContourGeometry.getLargestConnectedComponent(binaryMask, width, height)
        binaryMask.fill(0)
        val trace = if (lcc.pixelCount > 0) {
            SlimSamContourGeometry.extractBoundaryTrace(lcc.cleanMask, width, height, lcc.startX, lcc.startY)
        } else null
        val contour = trace?.let { SlimSamContourGeometry.contourFromBoundaryTrace(it, 2.2f, 3) }
        lcc.cleanMask.fill(0)
        if (trace == null || contour == null || !trace.traceClosed ||
            trace.terminationReason != BoundaryTerminationReason.RETURNED_TO_START) {
            return Extraction(
                segments = emptyList(),
                rawPointCount = trace?.points?.size ?: 0,
                traceClosed = trace?.traceClosed == true,
                terminationReason = trace?.terminationReason ?: BoundaryTerminationReason.INVALID_INPUT,
            )
        }
        val points = contour.simplifiedPoints
        val segments = contour.bezierSegments.mapIndexed { index, bezier ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            fun normalized(value: Float, dimension: Int) = (value / dimension).coerceIn(0f, 1f)
            RecognitionContourSegment(
                startX = normalized(start.x, width),
                startY = normalized(start.y, height),
                endX = normalized(end.x, width),
                endY = normalized(end.y, height),
                control1X = normalized(bezier.control1X, width),
                control1Y = normalized(bezier.control1Y, height),
                control2X = normalized(bezier.control2X, width),
                control2Y = normalized(bezier.control2Y, height),
                beginsContour = index == 0,
                closesContour = index == contour.bezierSegments.lastIndex,
            )
        }
        return Extraction(segments, contour.rawPointCount, trace.traceClosed, trace.terminationReason)
    }

    private fun invalid() = Extraction(
        segments = emptyList(),
        rawPointCount = 0,
        traceClosed = false,
        terminationReason = BoundaryTerminationReason.INVALID_INPUT,
    )
}
