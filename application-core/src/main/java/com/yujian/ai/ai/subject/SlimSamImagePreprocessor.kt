package com.yujian.ai.ai.subject

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.floor

/** Backing bytes for the encoder's FLOAT32 NCHW input and its canonical receipt hash. */
internal fun allocateSlimSamPixelBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(3 * 1024 * 1024 * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)

private val NORMALIZED_RED = FloatArray(256) { ((it / 255f) - 0.485f) / 0.229f }
private val NORMALIZED_GREEN = FloatArray(256) { ((it / 255f) - 0.456f) / 0.224f }
private val NORMALIZED_BLUE = FloatArray(256) { ((it / 255f) - 0.406f) / 0.225f }

/** Writes one ARGB row into the existing normalized FLOAT32 NCHW input contract. */
internal fun writeNormalizedRgbRow(
    destination: FloatBuffer,
    pixels: IntArray,
    validWidth: Int,
    y: Int,
    inputSize: Int,
) {
    require(inputSize > 0)
    require(y in 0 until inputSize)
    require(validWidth in 0..inputSize)
    require(pixels.size >= validWidth)
    val planeSize = inputSize * inputSize
    val rowOffset = y * inputSize
    val greenRowOffset = planeSize + rowOffset
    val blueRowOffset = planeSize * 2 + rowOffset
    for (x in 0 until validWidth) {
        val color = pixels[x]
        destination.put(rowOffset + x, NORMALIZED_RED[(color ushr 16) and 0xff])
        destination.put(greenRowOffset + x, NORMALIZED_GREEN[(color ushr 8) and 0xff])
        destination.put(blueRowOffset + x, NORMALIZED_BLUE[color and 0xff])
    }
    for (x in validWidth until inputSize) {
        destination.put(rowOffset + x, 0f)
        destination.put(greenRowOffset + x, 0f)
        destination.put(blueRowOffset + x, 0f)
    }
}

/**
 * Writes one HuggingFace SamImageProcessor pixel-center bilinear row to YuJian's
 * existing normalized NCHW tensor. [sourceRow0] and [sourceRow1] are the two
 * source rows selected by the caller; they may refer to the same array at an edge.
 * Padding is written as exact zero after normalization.
 */
internal fun writePixelCenterBilinearNormalizedRow(
    destination: FloatBuffer,
    sourceRow0: IntArray,
    sourceRow1: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    resizedWidth: Int,
    resizedHeight: Int,
    outputY: Int,
    inputSize: Int,
) {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(sourceRow0.size >= sourceWidth && sourceRow1.size >= sourceWidth)
    require(resizedWidth in 1..inputSize && resizedHeight in 1..inputSize)
    require(outputY in 0 until inputSize)
    val planeSize = inputSize * inputSize
    val rowOffset = outputY * inputSize
    val greenOffset = planeSize + rowOffset
    val blueOffset = 2 * planeSize + rowOffset
    if (outputY >= resizedHeight) {
        for (x in 0 until inputSize) {
            destination.put(rowOffset + x, 0f)
            destination.put(greenOffset + x, 0f)
            destination.put(blueOffset + x, 0f)
        }
        return
    }

    val sourceY = ((outputY + 0.5f) * (sourceHeight.toFloat() / resizedHeight) - 0.5f)
        .coerceIn(0f, (sourceHeight - 1).toFloat())
    val y0 = floor(sourceY).toInt()
    val y1 = minOf(sourceHeight - 1, y0 + 1)
    val wy = sourceY - y0
    val inverseWy = 1f - wy
    val xScale = sourceWidth.toFloat() / resizedWidth
    for (x in 0 until resizedWidth) {
        val sourceX = ((x + 0.5f) * xScale - 0.5f).coerceIn(0f, (sourceWidth - 1).toFloat())
        val x0 = floor(sourceX).toInt()
        val x1 = minOf(sourceWidth - 1, x0 + 1)
        val wx = sourceX - x0
        val inverseWx = 1f - wx
        val w00 = inverseWx * inverseWy
        val w10 = wx * inverseWy
        val w01 = inverseWx * wy
        val w11 = wx * wy
        val c00 = sourceRow0[x0]
        val c10 = sourceRow0[x1]
        val c01 = sourceRow1[x0]
        val c11 = sourceRow1[x1]

        // Reuse the branch's frozen 8-bit normalization lookup tables. Normalization is
        // affine, so interpolating the normalized neighbors preserves the same transform.
        val red = NORMALIZED_RED[(c00 ushr 16) and 0xff] * w00 +
            NORMALIZED_RED[(c10 ushr 16) and 0xff] * w10 +
            NORMALIZED_RED[(c01 ushr 16) and 0xff] * w01 +
            NORMALIZED_RED[(c11 ushr 16) and 0xff] * w11
        val green = NORMALIZED_GREEN[(c00 ushr 8) and 0xff] * w00 +
            NORMALIZED_GREEN[(c10 ushr 8) and 0xff] * w10 +
            NORMALIZED_GREEN[(c01 ushr 8) and 0xff] * w01 +
            NORMALIZED_GREEN[(c11 ushr 8) and 0xff] * w11
        val blue = NORMALIZED_BLUE[c00 and 0xff] * w00 +
            NORMALIZED_BLUE[c10 and 0xff] * w10 +
            NORMALIZED_BLUE[c01 and 0xff] * w01 +
            NORMALIZED_BLUE[c11 and 0xff] * w11
        destination.put(rowOffset + x, red)
        destination.put(greenOffset + x, green)
        destination.put(blueOffset + x, blue)
    }
    for (x in resizedWidth until inputSize) {
        destination.put(rowOffset + x, 0f)
        destination.put(greenOffset + x, 0f)
        destination.put(blueOffset + x, 0f)
    }
}

/** HuggingFace longest-edge dimensions, using its frozen round-to-nearest rule. */
internal fun slimSamResizedDimensions(width: Int, height: Int, longestEdge: Int = 1024): Pair<Int, Int> {
    require(width > 0 && height > 0 && longestEdge > 0)
    val scale = minOf(longestEdge.toDouble() / width, longestEdge.toDouble() / height)
    fun referenceDimension(value: Int): Int =
        floor(kotlin.math.round(value * scale * 100.0) / 100.0).toInt().coerceIn(1, longestEdge)
    return referenceDimension(width) to referenceDimension(height)
}
