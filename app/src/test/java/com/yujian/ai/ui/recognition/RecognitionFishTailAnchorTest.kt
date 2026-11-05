package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RecognitionFishTailAnchorTest {
    @Test
    fun resolvesCaudalPeduncleFromMaskShapeInsteadOfMaskCentroid() {
        val tail = RecognitionFishTailAnchor.resolve(fishContour())

        assertTrue(tail != null)
        assertEquals(.18f, requireNotNull(tail).x, .09f)
        assertEquals(.5f, tail.y, .09f)
        assertTrue(tail.x < .35f)
    }

    @Test
    fun resolvesTailAfterCanonicalQuarterTurnRotation() {
        val rotated = fishPoints().map { point -> RecognitionNormalizedPoint(.5f - point.y * .65f, .12f + point.x * .72f) }
        val tail = RecognitionFishTailAnchor.resolve(toContour(rotated))

        assertTrue(tail != null)
        assertEquals(.24f, requireNotNull(tail).y, .10f)
        assertEquals(.175f, tail.x, .10f)
    }

    @Test
    fun mapsCanonicalMaskPointThroughCropAndFitPhotoGeometry() {
        val contour = fishContour()
        val crop = calculateRecognitionImageTransform(
            containerWidth = 400f,
            containerHeight = 400f,
            imageWidth = 1_200,
            imageHeight = 800,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )
        val fit = calculateRecognitionImageTransform(
            containerWidth = 400f,
            containerHeight = 400f,
            imageWidth = 1_200,
            imageHeight = 800,
            contentScaleMode = RecognitionContentScaleMode.FIT,
        )

        val cropPoint = RecognitionFishTailAnchor.resolveInViewport(contour, crop)
        val fitPoint = RecognitionFishTailAnchor.resolveInViewport(contour, fit)
        assertTrue(cropPoint != null && fitPoint != null)
        assertEquals(requireNotNull(cropPoint).x + 60f, requireNotNull(fitPoint).x, 4f)
        assertEquals(requireNotNull(cropPoint).y, requireNotNull(fitPoint).y, 4f)
    }

    @Test
    fun refusesAnAmbiguousSilhouetteAndATailOutsideTheVisibleCrop() {
        val ellipse = (0 until 40).map { index ->
            val angle = 2.0 * PI * index / 40.0
            RecognitionNormalizedPoint(.5f + .35f * cos(angle).toFloat(), .5f + .12f * sin(angle).toFloat())
        }
        assertNull(RecognitionFishTailAnchor.resolve(toContour(ellipse)))

        val croppedTransform = calculateRecognitionImageTransform(
            containerWidth = 400f,
            containerHeight = 400f,
            imageWidth = 2_400,
            imageHeight = 800,
            contentScaleMode = RecognitionContentScaleMode.CROP,
        )
        assertNull(RecognitionFishTailAnchor.resolveInViewport(fishContour(), croppedTransform))
    }

    private fun fishPoints(): List<RecognitionNormalizedPoint> = listOf(
        RecognitionNormalizedPoint(.05f, .35f),
        RecognitionNormalizedPoint(.10f, .42f),
        RecognitionNormalizedPoint(.18f, .47f),
        RecognitionNormalizedPoint(.30f, .34f),
        RecognitionNormalizedPoint(.48f, .27f),
        RecognitionNormalizedPoint(.68f, .31f),
        RecognitionNormalizedPoint(.83f, .40f),
        RecognitionNormalizedPoint(.96f, .48f),
        RecognitionNormalizedPoint(.83f, .60f),
        RecognitionNormalizedPoint(.68f, .69f),
        RecognitionNormalizedPoint(.48f, .73f),
        RecognitionNormalizedPoint(.30f, .66f),
        RecognitionNormalizedPoint(.18f, .53f),
        RecognitionNormalizedPoint(.10f, .58f),
        RecognitionNormalizedPoint(.05f, .65f),
    )

    private fun fishContour() = toContour(fishPoints())

    private fun toContour(points: List<RecognitionNormalizedPoint>): List<RecognitionContourSegment> =
        points.indices.map { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            RecognitionContourSegment(
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
                control1X = start.x,
                control1Y = start.y,
                control2X = end.x,
                control2Y = end.y,
                beginsContour = index == 0,
                closesContour = index == points.lastIndex,
            )
        }
}
