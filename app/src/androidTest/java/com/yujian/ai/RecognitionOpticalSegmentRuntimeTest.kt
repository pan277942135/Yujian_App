package com.yujian.ai

import android.graphics.PathMeasure
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.recognition.extractVisiblePathSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionOpticalSegmentRuntimeTest {
    @Test
    fun luminousSegmentUsesMeasuredBezierLengthAndWrapsAtTheRealPathEnd() {
        val curve = Path().apply {
            moveTo(0f, 0f)
            cubicTo(0f, 140f, 180f, 140f, 180f, 0f)
        }
        val curveLength = PathMeasure(curve.asAndroidPath(), false).length

        val interior = extractVisiblePathSegments(curve, offset = .20f, visibleRatio = .30f)
        val wrapped = extractVisiblePathSegments(curve, offset = .95f, visibleRatio = .30f)

        assertEquals(1, interior.size)
        assertEquals(2, wrapped.size)
        assertEquals(curveLength * .30f, interior.measuredLength(), 1f)
        assertEquals(curveLength * .30f, wrapped.measuredLength(), 1f)
        assertTrue(interior.measuredLength() < curveLength)
    }

    private fun List<com.yujian.ai.ui.recognition.RecognitionVisiblePathSegment>.measuredLength(): Float =
        sumOf { PathMeasure(it.path.asAndroidPath(), false).length.toDouble() }.toFloat()
}
