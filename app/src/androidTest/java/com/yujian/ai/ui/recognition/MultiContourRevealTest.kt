package com.yujian.ai.ui.recognition

import android.graphics.Path
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiContourRevealTest {
    @Test fun revealTraversesBothClosedLoops() {
        val source = Path().apply {
            addRect(0f, 0f, 10f, 10f, Path.Direction.CW)
            addRect(40f, 0f, 50f, 10f, Path.Direction.CW)
        }
        val revealed = revealedRecognitionContour(source, .75f)
        val bounds = RectF()
        revealed.computeBounds(bounds, true)
        assertTrue("the second loop must participate", bounds.right >= 49f)
        assertTrue("first loop remains present", bounds.left <= 1f)
    }
}
