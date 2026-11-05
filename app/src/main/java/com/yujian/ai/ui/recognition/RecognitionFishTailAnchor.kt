package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.RecognitionImageTransform
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class RecognitionNormalizedPoint(val x: Float, val y: Float)

/**
 * Resolves the caudal-peduncle contact point from the current SlimSAM silhouette.
 * Contour input is already in canonical, EXIF-normalized whole-photo coordinates.
 */
internal object RecognitionFishTailAnchor {
    private data class Point(val x: Float, val y: Float)
    private data class Projected(val along: Float, val across: Float)
    private data class Candidate(val point: Point, val score: Float)

    fun resolve(contour: List<RecognitionContourSegment>): RecognitionNormalizedPoint? {
        val outline = largestClosedOutline(contour) ?: return null
        if (outline.size < 12) return null

        val meanX = outline.map(Point::x).average().toFloat()
        val meanY = outline.map(Point::y).average().toFloat()
        var xx = 0f
        var yy = 0f
        var xy = 0f
        outline.forEach { point ->
            val dx = point.x - meanX
            val dy = point.y - meanY
            xx += dx * dx
            yy += dy * dy
            xy += dx * dy
        }
        xx /= outline.size
        yy /= outline.size
        xy /= outline.size
        val discriminant = sqrt(((xx - yy) * (xx - yy) + 4f * xy * xy).coerceAtLeast(0f))
        val majorVariance = (xx + yy + discriminant) * .5f
        val minorVariance = (xx + yy - discriminant) * .5f
        if (majorVariance <= 1e-7f || minorVariance <= 1e-8f || majorVariance / minorVariance < 1.7f) return null

        val angle = .5f * atan2(2f * xy, xx - yy)
        val axisX = cos(angle)
        val axisY = sin(angle)
        val projected = outline.map { point ->
            val dx = point.x - meanX
            val dy = point.y - meanY
            Projected(dx * axisX + dy * axisY, -dx * axisY + dy * axisX)
        }
        val low = projected.minOf(Projected::along)
        val high = projected.maxOf(Projected::along)
        val length = high - low
        if (!length.isFinite() || length < .025f) return null

        fun widthAt(along: Float): Pair<Float, Pair<Float, Float>>? {
            val intersections = ArrayList<Float>(4)
            projected.indices.forEach { index ->
                val first = projected[index]
                val second = projected[(index + 1) % projected.size]
                val delta = second.along - first.along
                when {
                    abs(delta) < 1e-6f -> if (abs(along - first.along) < length * .002f) {
                        intersections += first.across
                        intersections += second.across
                    }
                    along >= minOf(first.along, second.along) && along < maxOf(first.along, second.along) -> {
                        val ratio = (along - first.along) / delta
                        intersections += first.across + (second.across - first.across) * ratio
                    }
                }
            }
            if (intersections.size < 2) return null
            val minimum = intersections.minOrNull() ?: return null
            val maximum = intersections.maxOrNull() ?: return null
            val centerAlong = (along - low) / length
            val centerAcross = (minimum + maximum) * .5f
            val centerX = meanX + axisX * (low + centerAlong * length) - axisY * centerAcross
            val centerY = meanY + axisY * (low + centerAlong * length) + axisX * centerAcross
            return (maximum - minimum) to (centerX to centerY)
        }

        fun medianWidth(fromEndStart: Float, fromEndEnd: Float, lowEnd: Boolean): Float? {
            val widths = (0..12).mapNotNull { sample ->
                val distanceFromEnd = fromEndStart + (fromEndEnd - fromEndStart) * sample / 12f
                val along = if (lowEnd) low + distanceFromEnd * length else high - distanceFromEnd * length
                widthAt(along)?.first
            }.filter { it.isFinite() && it > 1e-5f }.sorted()
            if (widths.size < 5) return null
            return widths[widths.size / 2]
        }

        val candidates = buildList {
            listOf(true, false).forEach { lowEnd ->
                val endWidth = medianWidth(.025f, .11f, lowEnd) ?: return@forEach
                val bodyWidth = medianWidth(.43f, .68f, lowEnd) ?: return@forEach
                for (step in 7..38) {
                    val distanceFromEnd = step / 100f
                    val along = if (lowEnd) low + distanceFromEnd * length else high - distanceFromEnd * length
                    val center = widthAt(along) ?: continue
                    val width = center.first
                    if (width <= 1e-5f || endWidth / width < 1.28f || bodyWidth / width < 1.28f) continue
                    val towardEnd = medianWidth((distanceFromEnd - .07f).coerceAtLeast(.015f),
                        (distanceFromEnd - .035f).coerceAtLeast(.02f), lowEnd) ?: continue
                    val towardBody = medianWidth(distanceFromEnd + .035f, distanceFromEnd + .07f, lowEnd) ?: continue
                    if (towardEnd / width < 1.12f || towardBody / width < 1.12f) continue
                    val (x, y) = center.second
                    val score = minOf(endWidth / width, bodyWidth / width) *
                        minOf(towardEnd / width, towardBody / width)
                    add(Candidate(Point(x, y), score))
                }
            }
        }
        return candidates.maxByOrNull(Candidate::score)?.takeIf { it.score >= 1.55f }?.point
            ?.let { RecognitionNormalizedPoint(it.x, it.y) }
    }

    fun resolveInViewport(
        contour: List<RecognitionContourSegment>,
        transform: RecognitionImageTransform,
    ): DisplayPoint? = resolve(contour)?.let { point ->
        val mapped = transform.mapNormalized(point.x, point.y)
        val photo = transform.photoContentRect
        mapped.takeIf {
            it.x.isFinite() && it.y.isFinite() && it.x in photo.left..photo.right && it.y in photo.top..photo.bottom
        }
    }

    private fun largestClosedOutline(contour: List<RecognitionContourSegment>): List<Point>? {
        if (contour.isEmpty()) return null
        var current: MutableList<Point>? = null
        var closed = false
        val closedLoops = ArrayList<List<Point>>()
        contour.forEach { segment ->
            if (segment.beginsContour || current == null) {
                if (closed && current != null) closedLoops += current!!.toList()
                current = mutableListOf()
                closed = false
            }
            if (listOf(segment.startX, segment.startY).all { it.isFinite() && it in 0f..1f }) {
                current?.add(Point(segment.startX, segment.startY))
            }
            if (segment.closesContour) closed = true
        }
        if (closed && current != null) closedLoops += current!!.toList()
        return closedLoops.asSequence()
            .filter { it.size >= 12 }
            .maxByOrNull { loop -> abs(signedArea(loop)) }
            ?.takeIf { abs(signedArea(it)) > 1e-5f }
    }

    private fun signedArea(points: List<Point>): Float = points.indices.sumOf { index ->
        val first = points[index]
        val second = points[(index + 1) % points.size]
        (first.x * second.y - second.x * first.y).toDouble()
    }.toFloat() * .5f
}
