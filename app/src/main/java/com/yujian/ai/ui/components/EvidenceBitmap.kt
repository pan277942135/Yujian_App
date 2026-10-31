package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Display-only decode. Never rewrites the archived source or its EXIF metadata. */
internal fun decodeOrientedEvidence(bytes: ByteArray): Bitmap? {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return applyExifOrientation(decoded, readExifOrientation(bytes))
}

/** Decode no larger than the measured image slot needs, then apply EXIF before display/crop geometry. */
internal fun decodeOrientedEvidenceSampled(bytes: ByteArray, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val orientation = readExifOrientation(bytes)
    val swapsAxes = orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270
    val requiredRawWidth = (if (swapsAxes) targetHeightPx else targetWidthPx).coerceAtLeast(1)
    val requiredRawHeight = (if (swapsAxes) targetWidthPx else targetHeightPx).coerceAtLeast(1)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= requiredRawWidth &&
        bounds.outHeight / (sampleSize * 2) >= requiredRawHeight
    ) sampleSize *= 2
    val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: return null
    return applyExifOrientation(sampled, orientation)
}

private fun readExifOrientation(bytes: ByteArray): Int = runCatching {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
    )
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

internal fun applyExifOrientation(decoded: Bitmap, orientation: Int): Bitmap {
    val matrix = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> return decoded
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it !== decoded) decoded.recycle() }
}

/** Bounded CPU blur for API24+, used only behind the unchanged fitted evidence. */
internal fun blurEvidenceBackdrop(source: Bitmap, radiusInSourcePixels: Float): Bitmap {
    val scale = minOf(1f, 192f / max(source.width, source.height))
    val width = max(1, (source.width * scale).roundToInt())
    val height = max(1, (source.height * scale).roundToInt())
    val sampled = Bitmap.createScaledBitmap(source, width, height, true)
    // Composite transparent sources onto the existing opaque Hero base before filtering.
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(output).apply { drawColor(0xFF16242C.toInt()); drawBitmap(sampled, 0f, 0f, null) }
    if (sampled !== source) sampled.recycle()
    var pixels = IntArray(width * height)
    output.getPixels(pixels, 0, width, 0, 0, width, height)
    val radius = ceil(radiusInSourcePixels * scale).toInt().coerceIn(1, 32)
    // Three separable box passes approximate a soft Gaussian; work is bounded by 192².
    repeat(3) {
        pixels = blurEvidenceAxis(pixels, width, height, radius, horizontal = true)
        pixels = blurEvidenceAxis(pixels, width, height, radius, horizontal = false)
    }
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    return output
}

private fun blurEvidenceAxis(input: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val output = IntArray(input.size)
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val count = radius * 2 + 1
    for (line in 0 until lines) {
        fun index(position: Int): Int = if (horizontal) line * width + position else position * width + line
        var red = 0; var green = 0; var blue = 0
        fun accumulate(position: Int, sign: Int) {
            val color = input[index(position.coerceIn(0, length - 1))]
            red += ((color ushr 16) and 255) * sign
            green += ((color ushr 8) and 255) * sign
            blue += (color and 255) * sign
        }
        for (position in -radius..radius) accumulate(position, 1)
        for (position in 0 until length) {
            output[index(position)] = (255 shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            accumulate(position - radius, -1)
            accumulate(position + radius + 1, 1)
        }
    }
    return output
}
