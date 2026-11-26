package com.yujian.ai.ai.subject

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Backing bytes for the encoder's FLOAT32 NCHW input and its canonical receipt hash. */
internal fun allocateSlimSamPixelBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(3 * 1024 * 1024 * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)

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
    for (x in 0 until inputSize) {
        val color = if (x < validWidth) pixels[x] else 0
        val red = if (x < validWidth) ((color shr 16) and 0xff) / 255f else 0f
        val green = if (x < validWidth) ((color shr 8) and 0xff) / 255f else 0f
        val blue = if (x < validWidth) (color and 0xff) / 255f else 0f
        destination.put(rowOffset + x, if (x < validWidth) (red - 0.485f) / 0.229f else 0f)
        destination.put(planeSize + rowOffset + x, if (x < validWidth) (green - 0.456f) / 0.224f else 0f)
        destination.put(planeSize * 2 + rowOffset + x, if (x < validWidth) (blue - 0.406f) / 0.225f else 0f)
    }
}
