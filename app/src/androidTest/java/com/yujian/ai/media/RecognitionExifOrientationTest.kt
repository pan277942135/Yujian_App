package com.yujian.ai.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.yujian.ai.ui.components.applyExifOrientation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionExifOrientationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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

    @Test
    fun normalizationRecordsOriginalExifAndUsesTransformedCanonicalDimensions() = kotlinx.coroutines.runBlocking {
        val input = File(context.cacheDir, "camera/exif-transpose-identity.jpg")
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixels(intArrayOf(
                0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(),
                0xffffff00.toInt(), 0xffff00ff.toInt(), 0xff00ffff.toInt(),
            ), 0, 3, 0, 0, 3, 2)
        }
        var normalized: File? = null
        try {
            input.parentFile?.mkdirs()
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            ExifInterface(input).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_TRANSPOSE.toString())
                saveAttributes()
            }

            val selected = RecognitionImageStore.normalizeCameraFile(context, input)
            normalized = File(selected.filePath)
            val identity = requireNotNull(selected.photoIdentity)

            assertEquals(ExifInterface.ORIENTATION_TRANSPOSE, identity.sourceOrientation)
            assertEquals(2, identity.width)
            assertEquals(3, identity.height)
            assertEquals(identity.width, selected.bitmap.width)
            assertEquals(identity.height, selected.bitmap.height)
        } finally {
            bitmap.recycle()
            input.delete()
            normalized?.delete()
        }
    }

}
