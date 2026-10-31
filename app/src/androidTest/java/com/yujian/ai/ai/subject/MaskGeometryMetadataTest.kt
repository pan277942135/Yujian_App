package com.yujian.ai.ai.subject

import android.graphics.Matrix
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    }
}
