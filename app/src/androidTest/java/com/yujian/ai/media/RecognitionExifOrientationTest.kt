package com.yujian.ai.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.components.applyExifOrientation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionExifOrientationTest {
    @Test fun allEightOrientationsTransformActualPixels() {
        val cases = listOf(
            ExifInterface.ORIENTATION_NORMAL to listOf("abc", "def"),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf("cba", "fed"),
            ExifInterface.ORIENTATION_ROTATE_180 to listOf("fed", "cba"),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf("def", "abc"),
            ExifInterface.ORIENTATION_TRANSPOSE to listOf("ad", "be", "cf"),
            ExifInterface.ORIENTATION_ROTATE_90 to listOf("da", "eb", "fc"),
            ExifInterface.ORIENTATION_TRANSVERSE to listOf("fc", "eb", "da"),
            ExifInterface.ORIENTATION_ROTATE_270 to listOf("cf", "be", "ad"),
        )
        val colors = "abcdef".mapIndexed { i, c -> c to (0xff000000.toInt() or ((i + 1) shl 16)) }.toMap()
        for ((orientation, expected) in cases) {
            val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
            "abcdef".forEachIndexed { index, c -> source.setPixel(index % 3, index / 3, colors.getValue(c)) }
            val result = applyExifOrientation(source, orientation)
            assertEquals("orientation $orientation width", expected.first().length, result.width)
            assertEquals("orientation $orientation height", expected.size, result.height)
            expected.forEachIndexed { y, row -> row.forEachIndexed { x, c ->
                assertEquals("orientation $orientation [$x,$y]", colors.getValue(c), result.getPixel(x, y))
            } }
            result.recycle()
        }
    }
}
