package com.yujian.ai.ui.recognition.result

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Produces a small, source-derived blur for Result Hero side fill.
 * The original bitmap remains untouched and is always drawn in front at full evidence scale.
 */
internal fun createRecognitionHeroAmbientBackdrop(
    source: Bitmap,
    maxDimension: Int = 256,
    blurRadius: Int = 12,
    passes: Int = 2,
): Bitmap {
    require(source.width > 0 && source.height > 0)
    require(maxDimension > 0)
    require(blurRadius > 0)
    require(passes > 0)

    val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height))
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val sampled = Bitmap.createScaledBitmap(source, width, height, true)
    val working = sampled.copy(Bitmap.Config.ARGB_8888, true)
        ?: error("Unable to create Recognition Hero ambient bitmap")
    if (sampled !== source) sampled.recycle()

    var pixels = IntArray(width * height)
    try {
        working.getPixels(pixels, 0, width, 0, 0, width, height)
    } finally {
        working.recycle()
    }
    repeat(passes) {
        pixels = boxBlur(pixels, width, height, blurRadius, horizontal = true)
        pixels = boxBlur(pixels, width, height, blurRadius, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlur(
    input: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
): IntArray {
    val output = IntArray(input.size)
    val diameter = radius * 2 + 1

    if (horizontal) {
        for (y in 0 until height) {
            val row = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[row + offset.coerceIn(0, width - 1)]
                alpha += color ushr 24 and 0xff
                red += color ushr 16 and 0xff
                green += color ushr 8 and 0xff
                blue += color and 0xff
            }
            for (x in 0 until width) {
                output[row + x] = packColor(alpha, red, green, blue, diameter)
                val remove = input[row + (x - radius).coerceIn(0, width - 1)]
                val add = input[row + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += (add ushr 24 and 0xff) - (remove ushr 24 and 0xff)
                red += (add ushr 16 and 0xff) - (remove ushr 16 and 0xff)
                green += (add ushr 8 and 0xff) - (remove ushr 8 and 0xff)
                blue += (add and 0xff) - (remove and 0xff)
            }
        }
    } else {
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[offset.coerceIn(0, height - 1) * width + x]
                alpha += color ushr 24 and 0xff
                red += color ushr 16 and 0xff
                green += color ushr 8 and 0xff
                blue += color and 0xff
            }
            for (y in 0 until height) {
                output[y * width + x] = packColor(alpha, red, green, blue, diameter)
                val remove = input[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += (add ushr 24 and 0xff) - (remove ushr 24 and 0xff)
                red += (add ushr 16 and 0xff) - (remove ushr 16 and 0xff)
                green += (add ushr 8 and 0xff) - (remove ushr 8 and 0xff)
                blue += (add and 0xff) - (remove and 0xff)
            }
        }
    }
    return output
}

private fun packColor(alpha: Int, red: Int, green: Int, blue: Int, divisor: Int): Int =
    ((alpha / divisor).coerceIn(0, 255) shl 24) or
        ((red / divisor).coerceIn(0, 255) shl 16) or
        ((green / divisor).coerceIn(0, 255) shl 8) or
        (blue / divisor).coerceIn(0, 255)
