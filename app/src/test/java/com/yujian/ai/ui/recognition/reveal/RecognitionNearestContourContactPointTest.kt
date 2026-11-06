package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionNearestContourContactPointTest {
    @Test
    fun choosesClosestMaskContourPoint() {
        val result = resolve(square(.1f, .1f, .9f, .9f), DisplayPoint(.49f, .22f))
        assertNotNull(result)
        assertEquals(.49f, result!!.x, .02f)
        assertEquals(.1f, result.y, .01f)
    }

    @Test
    fun excludesInternalHoleBoundariesFromContactCandidates() {
        val contour = square(.1f, .1f, .9f, .9f) + square(.45f, .45f, .55f, .55f)
        val result = resolve(contour, DisplayPoint(.5f, .5f))
        assertNotNull(result)
        val dx = (result!!.x - .5f) * 1000f
        val dy = (result.y - .5f) * 1000f
        assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) > 350f)
    }

    @Test
    fun rejectsSmallNoiseLoopsWhileRetainingTheMainMaskBoundary() {
        val contour = square(.1f, .1f, .9f, .9f) + square(.48f, .48f, .52f, .52f)
        val result = resolve(contour, DisplayPoint(.5f, .5f))
        assertNotNull(result)
        val dx = (result!!.x - .5f) * 1000f
        val dy = (result.y - .5f) * 1000f
        assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) > 350f)
    }

    @Test
    fun mapsRoiContourIntoCanonicalPhotoBeforeMeasuringDistance() {
        val result = RecognitionNearestContourContactPoint.resolve(
            contour = square(.1f, .1f, .9f, .9f),
            subjectRoiInSource = NormalizedSourceRect(.2f, .3f, .8f, .7f),
            contourIsCanonicalPhoto = false,
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            orbSourceNormalized = DisplayPoint(.5f, .2f),
        )
        assertNotNull(result)
        assertEquals(.5f, result!!.x, .02f)
        assertEquals(.34f, result.y, .02f)
    }

    private fun resolve(contour: List<RecognitionContourSegment>, orb: DisplayPoint) =
        RecognitionNearestContourContactPoint.resolve(
            contour = contour,
            subjectRoiInSource = NormalizedSourceRect(0f, 0f, 1f, 1f),
            contourIsCanonicalPhoto = true,
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
            orbSourceNormalized = orb,
        )

    private fun square(left: Float, top: Float, right: Float, bottom: Float): List<RecognitionContourSegment> {
        val points = listOf(
            DisplayPoint(left, top),
            DisplayPoint(right, top),
            DisplayPoint(right, bottom),
            DisplayPoint(left, bottom),
        )
        return points.mapIndexed { index, start ->
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
}
