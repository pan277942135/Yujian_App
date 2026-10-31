package com.yujian.ai.ai.subject

import android.graphics.Matrix
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.waterlight.mask.WaterLightMaskAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaskGeometryMetadataTest {
    @Test fun actualRoiAndModelGridRoundTrip() {
        val modelToPhoto = Matrix().apply { setScale(4f / 0.8f, 4f / 0.8f) }
        val geometry = MaskGeometryMetadata(
            sessionId = "session", attemptId = "attempt", imageId = "image", photoSha256 = "sha",
            canonicalWidth = 1280, canonicalHeight = 720, roiPx = Rect(240, 100, 900, 600),
            modelInputSize = 1024, resizedWidth = 1024, resizedHeight = 576,
            padLeft = 0, padTop = 0, padRight = 0, padBottom = 448,
            maskGridWidth = 256, maskGridHeight = 256, modelToPhotoTransform = modelToPhoto,
            pixelCenterConvention = "TOP_LEFT_ZERO_BASED", threshold = 0.5f,
            contourVersion = "GRID_CONTOUR_V1", modelSha256 = "model",
        )
        assertTrue(geometry.matches("session", "attempt", "image", "sha", 1280, 720))
        assertFalse(geometry.matches("old-session", "attempt", "image", "sha", 1280, 720))
        assertFalse(geometry.matches("session", "attempt", "image", "other-sha", 1280, 720))
        val roi = geometry.normalizedRoi()
        assertEquals(240f / 1280, roi.x1, 0.00001f)
        assertEquals(600f / 720, roi.y2, 0.00001f)
        val photoPoint = floatArrayOf(48f, 20f)
        modelToPhoto.mapPoints(photoPoint)
        assertEquals(240f, photoPoint[0], 0.0001f)
        assertEquals(100f, photoPoint[1], 0.0001f)
        val inverse = Matrix()
        assertTrue(modelToPhoto.invert(inverse))
        inverse.mapPoints(photoPoint)
        assertEquals(48f, photoPoint[0], 0.0001f)
        assertEquals(20f, photoPoint[1], 0.0001f)
        val loops = listOf(
            RecognitionContourSegment(0f, 0f, 1f, 1f, .5f, .25f, .5f, .75f,
                beginsContour = true, closesContour = true),
            RecognitionContourSegment(.25f, .4f, .75f, .8f,
                beginsContour = true, closesContour = true),
        )
        val mapped = WaterLightMaskAdapter.toPhotoSpace(geometry, loops)
        assertEquals(240f / 1280, mapped[0].startX, .00001f)
        assertEquals(100f / 720, mapped[0].startY, .00001f)
        assertEquals(900f / 1280, mapped[0].endX, .00001f)
        assertEquals(600f / 720, mapped[0].endY, .00001f)
        assertEquals(570f / 1280, mapped[0].control1X, .00001f)
        assertEquals(225f / 720, mapped[0].control1Y, .00001f)
        assertTrue(mapped[0].beginsContour && mapped[0].closesContour)
        assertTrue(mapped[1].beginsContour && mapped[1].closesContour)
    }
}
