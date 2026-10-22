package com.yujian.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exercises the production file normalizer against all eight EXIF transforms. */
@RunWith(AndroidJUnit4::class)
class RecognitionImageStoreExifOrientationRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cameraNormalizerAppliesAllExifOrientationsBeforeReturningDetectorBitmap() = runBlocking {
        (ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270).forEach { orientation ->
            val sourceFile = File(context.cacheDir, "exif_orientation_${orientation}_source.jpg")
            val sourceBitmap = cornerColorFixture()
            sourceFile.outputStream().use { output ->
                assertTrue(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
            sourceBitmap.recycle()
            ExifInterface(sourceFile).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }

            val normalized = RecognitionImageStore.normalizeCameraFileWithDetails(context, sourceFile)
            val bitmap = normalized.selectedImage.bitmap
            val savedFile = File(normalized.selectedImage.filePath)
            try {
                assertEquals(orientation, normalized.exifOrientation)
                val swapsDimensions = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
                assertEquals(if (swapsDimensions) 32 else 48, bitmap.width)
                assertEquals(if (swapsDimensions) 48 else 32, bitmap.height)
                assertCornerColors(expectedCorners(orientation), sampledCornerColors(bitmap))

                val persisted = requireNotNull(BitmapFactory.decodeFile(savedFile.absolutePath))
                try {
                    assertEquals(bitmap.width, persisted.width)
                    assertEquals(bitmap.height, persisted.height)
                    val savedOrientation = ExifInterface(savedFile).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                    assertTrue(
                        "normalized JPEG must store upright pixels without a remaining EXIF transform",
                        savedOrientation == ExifInterface.ORIENTATION_NORMAL ||
                            savedOrientation == ExifInterface.ORIENTATION_UNDEFINED,
                    )
                } finally {
                    persisted.recycle()
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                sourceFile.delete()
                savedFile.delete()
            }
        }
    }

    private fun cornerColorFixture(): Bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    y < height / 2 && x < width / 2 -> Color.RED
                    y < height / 2 -> Color.GREEN
                    x < width / 2 -> Color.BLUE
                    else -> Color.YELLOW
                }
                setPixel(x, y, color)
            }
        }
    }

    private fun expectedCorners(orientation: Int): List<Int> = when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE)
        ExifInterface.ORIENTATION_ROTATE_180 -> listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN)
        ExifInterface.ORIENTATION_TRANSPOSE -> listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW)
        ExifInterface.ORIENTATION_ROTATE_90 -> listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN)
        ExifInterface.ORIENTATION_TRANSVERSE -> listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED)
        ExifInterface.ORIENTATION_ROTATE_270 -> listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE)
        else -> error("Unsupported test orientation $orientation")
    }

    private fun sampledCornerColors(bitmap: Bitmap): List<Int> {
        val left = 8
        val right = bitmap.width - 9
        val top = 8
        val bottom = bitmap.height - 9
        return listOf(
            bitmap.getPixel(left, top),
            bitmap.getPixel(right, top),
            bitmap.getPixel(left, bottom),
            bitmap.getPixel(right, bottom),
        )
    }

    private fun assertCornerColors(expected: List<Int>, actual: List<Int>) {
        assertEquals("expected four corner samples", 4, actual.size)
        expected.zip(actual).forEachIndexed { index, (expectedColor, actualColor) ->
            assertColorNear("corner $index", expectedColor, actualColor)
        }
    }

    private fun assertColorNear(label: String, expected: Int, actual: Int) {
        val tolerance = 36
        assertTrue("$label red expected=${Color.red(expected)} actual=${Color.red(actual)}",
            abs(Color.red(expected) - Color.red(actual)) <= tolerance)
        assertTrue("$label green expected=${Color.green(expected)} actual=${Color.green(actual)}",
            abs(Color.green(expected) - Color.green(actual)) <= tolerance)
        assertTrue("$label blue expected=${Color.blue(expected)} actual=${Color.blue(actual)}",
            abs(Color.blue(expected) - Color.blue(actual)) <= tolerance)
    }
}
