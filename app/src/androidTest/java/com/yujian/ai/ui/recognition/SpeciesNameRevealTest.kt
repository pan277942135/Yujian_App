package com.yujian.ai.ui.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.recognition.reveal.SpeciesNameReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeciesNameRevealTest {
    @Test fun realChineseGlyphIsCapturedProgressively() {
        val text = "黑鱼"
        val path = Path()
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 46f }
            .getTextPath(text, 0, text.length, 2f, 50f, path)
        val renderer = SpeciesNameReveal()
        fun pixels(progress: Float): IntArray {
            val bitmap = Bitmap.createBitmap(120, 64, Bitmap.Config.ARGB_8888)
            renderer.draw(Canvas(bitmap), path, 120f, 64f, progress, 550L)
            return IntArray(120 * 64).also { bitmap.getPixels(it, 0, 120, 0, 0, 120, 64); bitmap.recycle() }
        }
        val partial = pixels(.30f)
        val full = pixels(1f)
        assertTrue(partial.any { it ushr 24 != 0 })
        assertEquals(0, partial.filterIndexed { index, _ -> index % 120 > 55 }.count { it ushr 24 != 0 })
        assertTrue(full.filterIndexed { index, _ -> index % 120 > 55 }.any { it ushr 24 != 0 })
    }
}
