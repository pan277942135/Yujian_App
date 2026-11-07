package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import kotlin.math.abs

/**
 * Resolves the point on the current trusted Mask's exterior contour that is
 * closest to the Orb anchor. Distances are measured in canonical source photo
 * pixels; the caller maps the Orb and the selected point through the same photo
 * transform.
 */
internal object RecognitionNearestContourContactPoint {
    private const val CURVE_STEPS = 24
    private const val MIN_EXTERIOR_AREA_RATIO = 1f / 200f

    private data class Ring(
        val segments: List<RecognitionContourSegment>,
        val points: List<DisplayPoint>,
        val signedArea: Float,
    )

    fun resolve(
        contour: List<RecognitionContourSegment>,
        subjectRoiInSource: NormalizedSourceRect,
        contourIsCanonicalPhoto: Boolean,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        orbSourceNormalized: DisplayPoint,
    ): DisplayPoint? {
        if (contour.isEmpty() || sourceWidthPx <= 0 || sourceHeightPx <= 0) return null
        if (!isValidPoint(orbSourceNormalized) || !isValidRect(subjectRoiInSource)) return null
        if (contour.any { !isValidSegment(it) }) return null

        val rings = splitRings(contour).mapNotNull { segments ->
            val points = segments.flatMap { segment ->
                (0..CURVE_STEPS).map { step ->
                    val local = cubicPoint(segment, step / CURVE_STEPS.toFloat())
                    toSourceNormalized(local, subjectRoiInSource, contourIsCanonicalPhoto)
                }
            }
            if (points.size < 4) null else Ring(segments, points, signedArea(points))
        }.filter { abs(it.signedArea) > 0.0001f }
        if (rings.isEmpty()) return null

        // Even nesting depth denotes an exterior Mask boundary; odd depth denotes
        // an internal hole and cannot be used as an Orb contact target.
        val exterior = rings.filter { ring ->
            val nestingDepth = rings.count { other ->
                other !== ring && contains(other.points, ring.points.first())
            }
            nestingDepth % 2 == 0
        }
        val largestExteriorArea = exterior.maxOfOrNull { abs(it.signedArea) } ?: return null
        val minimumArea = largestExteriorArea * MIN_EXTERIOR_AREA_RATIO
        val eligible = exterior.filter { abs(it.signedArea) >= minimumArea }
        if (eligible.isEmpty()) return null

        var nearest: DisplayPoint? = null
        var nearestDistanceSquared = Float.POSITIVE_INFINITY
        eligible.forEach { ring ->
            ring.segments.forEach { segment ->
                for (step in 0..CURVE_STEPS) {
                    val local = cubicPoint(segment, step / CURVE_STEPS.toFloat())
                    val source = toSourceNormalized(local, subjectRoiInSource, contourIsCanonicalPhoto)
                    val dx = (source.x - orbSourceNormalized.x) * sourceWidthPx
                    val dy = (source.y - orbSourceNormalized.y) * sourceHeightPx
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared
                        nearest = source
                    }
                }
            }
        }
        return nearest
    }

    private fun splitRings(contour: List<RecognitionContourSegment>): List<List<RecognitionContourSegment>> {
        if (contour.none { it.beginsContour || it.closesContour }) return listOf(contour)
        val rings = ArrayList<List<RecognitionContourSegment>>()
        var current = ArrayList<RecognitionContourSegment>()
        contour.forEach { segment ->
            if (segment.beginsContour && current.isNotEmpty()) {
                rings += current
                current = ArrayList()
            }
            current += segment
            if (segment.closesContour) {
                rings += current
                current = ArrayList()
            }
        }
        if (current.isNotEmpty()) rings += current
        return rings
    }

    private fun cubicPoint(segment: RecognitionContourSegment, t: Float): DisplayPoint {
        val inverse = 1f - t
        val a = inverse * inverse * inverse
        val b = 3f * inverse * inverse * t
        val c = 3f * inverse * t * t
        val d = t * t * t
        return DisplayPoint(
            x = a * segment.startX + b * segment.control1X + c * segment.control2X + d * segment.endX,
            y = a * segment.startY + b * segment.control1Y + c * segment.control2Y + d * segment.endY,
        )
    }

    private fun toSourceNormalized(
        point: DisplayPoint,
        roi: NormalizedSourceRect,
        canonicalPhoto: Boolean,
    ) = if (canonicalPhoto) {
        point
    } else {
        DisplayPoint(roi.left + point.x * roi.width, roi.top + point.y * roi.height)
    }

    private fun signedArea(points: List<DisplayPoint>): Float {
        var twiceArea = 0f
        points.indices.forEach { index ->
            val point = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += point.x * next.y - next.x * point.y
        }
        return twiceArea * .5f
    }

    private fun contains(polygon: List<DisplayPoint>, point: DisplayPoint): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            if ((current.y > point.y) != (previous.y > point.y)) {
                val intersectionX = (previous.x - current.x) *
                    (point.y - current.y) / (previous.y - current.y) + current.x
                if (point.x < intersectionX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun isValidPoint(point: DisplayPoint) =
        point.x.isFinite() && point.y.isFinite() && point.x in 0f..1f && point.y in 0f..1f

    private fun isValidRect(rect: NormalizedSourceRect) =
        listOf(rect.left, rect.top, rect.right, rect.bottom).all(Float::isFinite) &&
            rect.left >= 0f && rect.top >= 0f && rect.right <= 1f && rect.bottom <= 1f &&
            rect.left < rect.right && rect.top < rect.bottom

    private fun isValidSegment(segment: RecognitionContourSegment) =
        listOf(
            segment.startX, segment.startY, segment.endX, segment.endY,
            segment.control1X, segment.control1Y, segment.control2X, segment.control2Y,
        ).all { it.isFinite() && it in 0f..1f }
}
