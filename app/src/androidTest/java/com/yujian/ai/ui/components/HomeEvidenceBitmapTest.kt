package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeEvidenceBitmapTest {
    @Test
    fun allExifOrientationsKeepEvidenceCornersAndOriginalBytes() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val expected = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 3, 2),
            intArrayOf(3, 2, 1, 0), intArrayOf(2, 3, 0, 1),
            intArrayOf(0, 2, 1, 3), intArrayOf(2, 0, 3, 1),
            intArrayOf(3, 1, 2, 0), intArrayOf(1, 3, 0, 2),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "home-evidence-exif-test.jpg")
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) for (x in 0 until 80) {
            source.setPixel(x, y, colors[(if (y >= 20) 2 else 0) + (if (x >= 40) 1 else 0)])
        }
        try {
            for (orientation in 1..8) {
                photo.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
                ExifInterface(photo).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val original = photo.readBytes()
                val decoded = requireNotNull(decodeOrientedEvidence(original))
                val swapped = orientation >= 5
                assertEquals(if (swapped) 40 else 80, decoded.width)
                assertEquals(if (swapped) 80 else 40, decoded.height)
                val corners = listOf(
                    decoded.getPixel(decoded.width / 4, decoded.height / 4),
                    decoded.getPixel(decoded.width * 3 / 4, decoded.height / 4),
                    decoded.getPixel(decoded.width / 4, decoded.height * 3 / 4),
                    decoded.getPixel(decoded.width * 3 / 4, decoded.height * 3 / 4),
                )
                corners.forEachIndexed { index, actual ->
                    val target = colors[expected[orientation - 1][index]]
                    assertTrue("orientation=$orientation corner=$index", colorDistance(actual, target) < 24)
                }
                assertArrayEquals("Display decode must not rewrite archived JPEG", original, photo.readBytes())
                decoded.recycle()
            }
        } finally {
            photo.delete()
            source.recycle()
        }
    }

    @Test
    fun sampledExifDecodeUsesMeasuredTargetAxesForRotatedAndMirroredPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "fish-photo-sampled-exif-test.jpg")
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        try {
            for (orientation in 1..8) {
                photo.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
                ExifInterface(photo).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val decoded = requireNotNull(decodeOrientedEvidenceSampled(photo.readBytes(), 200, 300))
                val swapsAxes = orientation >= 5
                assertEquals(if (swapsAxes) 300 else 400, decoded.width)
                assertEquals(if (swapsAxes) 400 else 300, decoded.height)
                assertTrue(colorDistance(decoded.getPixel(decoded.width / 2, decoded.height / 2), Color.CYAN) < 20)
                decoded.recycle()
            }
        } finally {
            photo.delete()
            source.recycle()
        }
    }

    @Test
    fun softwareBackdropBlursOnApi28WithoutMutatingSource() {
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val original = IntArray(96 * 96) { index ->
            if (((index % 96) / 4 + (index / 96) / 4) % 2 == 0) Color.WHITE else Color.BLACK
        }
        source.setPixels(original, 0, 96, 0, 0, 96, 96)
        val backdrop = blurEvidenceBackdrop(source, 8f)
        val after = IntArray(original.size)
        source.getPixels(after, 0, 96, 0, 0, 96, 96)
        assertArrayEquals("Foreground evidence must stay byte-for-byte unchanged", original, after)
        var contrast = 0L
        for (y in 16 until 80) for (x in 16 until 80) {
            contrast += kotlin.math.abs(Color.red(backdrop.getPixel(x, y)) - 127)
        }
        assertTrue("API28 backdrop must actually defocus high-frequency detail", contrast / (64f * 64f) < 12f)
        assertTrue(backdrop !== source)
        backdrop.recycle()
        source.recycle()
    }

    @Test
    fun backdropIsBoundedAndSinglePixelSourceIsSafe() {
        val large = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blurred = blurEvidenceBackdrop(large, 22f)
        assertEquals(192, blurred.width)
        assertEquals(144, blurred.height)
        assertEquals(Color.RED, blurred.getPixel(96, 72))
        val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val tinyBlurred = blurEvidenceBackdrop(tiny, 22f)
        assertEquals(Color.BLUE, tinyBlurred.getPixel(0, 0))
        blurred.recycle(); large.recycle(); tinyBlurred.recycle(); tiny.recycle()
    }

    private fun colorDistance(left: Int, right: Int): Int = maxOf(
        kotlin.math.abs(Color.red(left) - Color.red(right)),
        kotlin.math.abs(Color.green(left) - Color.green(right)),
        kotlin.math.abs(Color.blue(left) - Color.blue(right)),
    )
}
