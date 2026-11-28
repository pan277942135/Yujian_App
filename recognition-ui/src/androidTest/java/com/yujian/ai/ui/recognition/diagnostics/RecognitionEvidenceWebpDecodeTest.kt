package com.yujian.ai.ui.recognition.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RecognitionEvidenceWebpDecodeTest {
    @Test fun q92WebpRoundTripsAtOriginalPixelDimensions() {
        val source = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(source.width * source.height) { index ->
                val x = index % source.width
                val y = index / source.width
                val red = (x * 255 / source.width).coerceIn(0, 255)
                val green = (y * 255 / source.height).coerceIn(0, 255)
                val blue = ((x xor y) and 0xff)
                (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
            source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val encoded = ByteArrayOutputStream().use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else Bitmap.CompressFormat.WEBP
                assertEquals(true, source.compress(format, 92, output))
                output.toByteArray()
            }
            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            assertNotNull(decoded)
            assertEquals(source.width, decoded!!.width)
            assertEquals(source.height, decoded.height)
            decoded.recycle()
        } finally {
            source.recycle()
        }
    }
}
