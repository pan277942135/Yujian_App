package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.DisplayRect
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint

/** Geometry builders shared by the Stage-04/05 producer and its decision gate. */
object RecognitionRevealPathGeometry {
    fun perimeterArrival(
        transform: RecognitionImageTransform,
        detectorBox: NormalizedFishBox,
        releaseEndpoint: WaterLightPathPoint,
        seed: Int,
    ): WaterLightPathGeometry? {
        if (!releaseEndpoint.isValid() || !detectorBox.isValidNormalized()) return null
        val rect = transform.photoContentRect
        if (!rect.isValid()) return null
        val detectorCenter = transform.mapBox(detectorBox)
        val entry = nearestPerimeterAnchor(rect, detectorCenter, releaseEndpoint) ?: return null
        val start = DisplayPoint(releaseEndpoint.x, releaseEndpoint.y)
        if (kotlin.math.hypot(entry.x - start.x, entry.y - start.y) < 1f) {
            // The release already touched the perimeter: advance along that edge.
            val step = minOf(24f, maxOf(rect.width, rect.height) * .08f)
            val target = when {
                kotlin.math.abs(entry.y - rect.top) < .01f || kotlin.math.abs(entry.y - rect.bottom) < .01f ->
                    DisplayPoint((entry.x + (if (entry.x + step <= rect.right) step else -step)).coerceIn(rect.left, rect.right), entry.y)
                else -> DisplayPoint(entry.x, (entry.y + (if (entry.y + step <= rect.bottom) step else -step)).coerceIn(rect.top, rect.bottom))
            }
            return WaterLightPathGeometry(
                start = releaseEndpoint,
                control1 = WaterLightPathPoint(start.x + (target.x - start.x) / 3f, start.y + (target.y - start.y) / 3f),
                control2 = WaterLightPathPoint(start.x + (target.x - start.x) * 2f / 3f, start.y + (target.y - start.y) * 2f / 3f),
                end = WaterLightPathPoint(target.x, target.y),
            ).takeIf(WaterLightPathGeometry::isValid)
        }
        return WaterLightPathGeometryFactory.between(
            start = releaseEndpoint,
            end = WaterLightPathPoint(entry.x, entry.y),
            seed = seed,
        )?.takeIf(WaterLightPathGeometry::isValid)
    }

    fun fishContact(
        transform: RecognitionImageTransform,
        contactPointNormalizedSource: WaterLightPathPoint,
        seed: Int,
        taskSplitPoint: WaterLightPathPoint? = null,
    ): WaterLightPathGeometry? {
        if (!contactPointNormalizedSource.isValid() ||
            contactPointNormalizedSource.x !in 0f..1f || contactPointNormalizedSource.y !in 0f..1f
        ) return null
        val rect = transform.photoContentRect
        if (!rect.isValid()) return null
        val contact = transform.mapNormalized(contactPointNormalizedSource.x, contactPointNormalizedSource.y)
        if (!contact.isInside(rect)) return null
        val perimeter = taskSplitPoint?.let { DisplayPoint(it.x, it.y) }
            ?: nearestPerimeterAnchor(rect, contact)
            ?: return null
        val onPerimeter =
            (kotlin.math.abs(perimeter.x - rect.left) <= .5f || kotlin.math.abs(perimeter.x - rect.right) <= .5f) &&
                perimeter.y in rect.top - .5f..rect.bottom + .5f ||
                (kotlin.math.abs(perimeter.y - rect.top) <= .5f || kotlin.math.abs(perimeter.y - rect.bottom) <= .5f) &&
                perimeter.x in rect.left - .5f..rect.right + .5f
        if (!onPerimeter) return null
        return WaterLightPathGeometryFactory.between(
            start = WaterLightPathPoint(perimeter.x, perimeter.y),
            end = WaterLightPathPoint(contact.x, contact.y),
            seed = seed,
        )?.takeIf(WaterLightPathGeometry::isValid)
    }

    private fun nearestPerimeterAnchor(rect: DisplayRect, point: DisplayPoint, preferNear: WaterLightPathPoint? = null): DisplayPoint? {
        if (!rect.isValid() || !point.x.isFinite() || !point.y.isFinite()) return null
        return listOf(
            DisplayPoint(point.x.coerceIn(rect.left, rect.right), rect.top),
            DisplayPoint(rect.right, point.y.coerceIn(rect.top, rect.bottom)),
            DisplayPoint(point.x.coerceIn(rect.left, rect.right), rect.bottom),
            DisplayPoint(rect.left, point.y.coerceIn(rect.top, rect.bottom)),
        ).minWithOrNull(compareBy<DisplayPoint> { candidate ->
            val dx = candidate.x - point.x
            val dy = candidate.y - point.y
            dx * dx + dy * dy
        }.thenBy { candidate ->
            val dx = candidate.x - (preferNear?.x ?: candidate.x)
            val dy = candidate.y - (preferNear?.y ?: candidate.y)
            dx * dx + dy * dy
        })
    }

    private fun DisplayRect.isValid(): Boolean =
        listOf(left, top, right, bottom).all(Float::isFinite) && width > 1f && height > 1f

    private fun DisplayPoint.isInside(rect: DisplayRect): Boolean =
        x.isFinite() && y.isFinite() && x > rect.left && x < rect.right && y > rect.top && y < rect.bottom
}
