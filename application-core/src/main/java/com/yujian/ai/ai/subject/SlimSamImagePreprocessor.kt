package com.yujian.ai.ai.subject

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

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
