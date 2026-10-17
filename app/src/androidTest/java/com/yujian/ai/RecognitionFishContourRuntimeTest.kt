package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.recognition.buildRecognitionContourPath
import com.yujian.ai.ui.screens.extractContour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RecognitionFishContourRuntimeTest {
    @Test
    fun contourFollowsConnectedMaskAndBuildsOneClosedSmoothPath() {
        val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawOval(
            RectF(24f, 34f, 136f, 86f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() },
        )

        val contour = extractContour(bitmap)
        val begins = contour.count { it.beginsContour }
        val closes = contour.count { it.closesContour }
        val path = buildRecognitionContourPath(
            transform = calculateRecognitionImageTransform(160f, 120f, 160, 120),
            subjectBox = NormalizedFishBox(0f, 0f, 1f, 1f),
            contour = contour,
        )
        val pathLength = PathMeasure(path.asAndroidPath(), false).length

        assertTrue("mask contour must contain a real connected boundary", contour.size >= 8)
        assertEquals(1, begins)
        assertEquals(1, closes)
        assertTrue("top of the silhouette remains centered", contour.any {
            abs(it.startX - .5f) < .12f && it.startY < .35f
        })
        assertFalse("a bbox corner is not synthesized as contour", contour.any {
            it.startX < .25f && it.startY < .35f
        })
        assertTrue("quadratic corner smoothing must create curved controls", contour.any {
            abs(it.control1X - it.startX) > .0001f || abs(it.control1Y - it.startY) > .0001f
        })
        assertTrue("rendered path must retain the real mask perimeter", pathLength > 250f)
    }
}
