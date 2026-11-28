package com.yujian.ai.ai.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.floor

class SlimSamImagePreprocessorTest {
    @Test
    fun planarRgbRowMatchesTheExistingNchwNormalizationAndZeroPadding() {
        val size = 2
        val input = FloatBuffer.allocate(3 * size * size)
        val pixels = intArrayOf(0xff336699.toInt())

        writeNormalizedRgbRow(input, pixels, validWidth = 1, y = 0, inputSize = size)

        assertEquals((0x33 / 255f - 0.485f) / 0.229f, input.get(0), 1e-6f)
        assertEquals(0f, input.get(1), 0f)
        assertEquals((0x66 / 255f - 0.456f) / 0.224f, input.get(size * size), 1e-6f)
        assertEquals(0f, input.get(size * size + 1), 0f)
        assertEquals((0x99 / 255f - 0.406f) / 0.225f, input.get(size * size * 2), 1e-6f)
        assertEquals(0f, input.get(size * size * 2 + 1), 0f)
    }

    @Test
    fun repeatedRowsWriteToTheirNchwOffsetsWithoutChangingBufferPosition() {
        val size = 2
        val input = FloatBuffer.allocate(3 * size * size)
        val row0 = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt())
        val row1 = intArrayOf(0xff0000ff.toInt(), 0xffffffff.toInt())

        writeNormalizedRgbRow(input, row0, validWidth = size, y = 0, inputSize = size)
        writeNormalizedRgbRow(input, row1, validWidth = size, y = 1, inputSize = size)

        assertEquals((1f - 0.485f) / 0.229f, input.get(0), 1e-6f)
        assertEquals((1f - 0.456f) / 0.224f, input.get(size * size + 1), 1e-6f)
        assertEquals((1f - 0.406f) / 0.225f, input.get(size * size * 2 + 2), 1e-6f)
        assertEquals(0f, input.position().toFloat(), 0f)
    }

    @Test
    fun lookupNormalizationMatchesTheFrozenFloatFormulaForEveryChannelByte() {
        val size = 256
        val pixels = IntArray(size) { value ->
            (0xff shl 24) or (value shl 16) or ((255 - value) shl 8) or (value xor 0x5a)
        }
        val input = FloatBuffer.allocate(3 * size * size)

        writeNormalizedRgbRow(input, pixels, validWidth = size, y = 0, inputSize = size)

        for (x in 0 until size) {
            val pixel = pixels[x]
            val red = ((pixel ushr 16) and 0xff) / 255f
            val green = ((pixel ushr 8) and 0xff) / 255f
            val blue = (pixel and 0xff) / 255f
            assertEquals((red - 0.485f) / 0.229f, input.get(x), 0f)
            assertEquals((green - 0.456f) / 0.224f, input.get(size * size + x), 0f)
            assertEquals((blue - 0.406f) / 0.225f, input.get(size * size * 2 + x), 0f)
        }
    }

    @Test
    fun pixelCenterResizeMatchesReferenceForPortraitLandscapeAnd1279By1706() {
        assertPixelParity(width = 7, height = 11, longestEdge = 8)
        assertPixelParity(width = 11, height = 7, longestEdge = 8)
        assertPixelParity(width = 1279, height = 1706, longestEdge = 1024)
        assertEquals(767 to 1024, slimSamResizedDimensions(1279, 1706, 1024))
    }

    private fun assertPixelParity(width: Int, height: Int, longestEdge: Int) {
        val source = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (0xff shl 24) or (((x * 31 + y * 7) and 0xff) shl 16) or
                (((x * 13 + y * 29) and 0xff) shl 8) or ((x * 3 + y * 19) and 0xff)
        }
        val (resizedWidth, resizedHeight) = slimSamResizedDimensions(width, height, longestEdge)
        val pad = longestEdge
        val actual = FloatBuffer.allocate(3 * pad * pad)
        val row0 = IntArray(width)
        val row1 = IntArray(width)
        for (y in 0 until pad) {
            if (y < resizedHeight) {
                val sy = ((y + 0.5f) * (height.toFloat() / resizedHeight) - 0.5f)
                    .coerceIn(0f, (height - 1).toFloat())
                val y0 = floor(sy).toInt()
                val y1 = minOf(height - 1, y0 + 1)
                source.copyInto(row0, 0, y0 * width, (y0 + 1) * width)
                source.copyInto(row1, 0, y1 * width, (y1 + 1) * width)
            }
            writePixelCenterBilinearNormalizedRow(
                destination = actual,
                sourceRow0 = row0,
                sourceRow1 = row1,
                sourceWidth = width,
                sourceHeight = height,
                resizedWidth = resizedWidth,
                resizedHeight = resizedHeight,
                outputY = y,
                inputSize = pad,
            )
        }

        // Independently transcribed from the pinned reference's preprocessArgb loop.
        val expected = FloatArray(3 * pad * pad)
        val plane = pad * pad
        val xScale = width.toFloat() / resizedWidth
        val yScale = height.toFloat() / resizedHeight
        for (y in 0 until resizedHeight) {
            val sy = ((y + 0.5f) * yScale - 0.5f).coerceIn(0f, (height - 1).toFloat())
            val y0 = floor(sy).toInt()
            val y1 = minOf(height - 1, y0 + 1)
            val wy = sy - y0
            for (x in 0 until resizedWidth) {
                val sx = ((x + 0.5f) * xScale - 0.5f).coerceIn(0f, (width - 1).toFloat())
                val x0 = floor(sx).toInt()
                val x1 = minOf(width - 1, x0 + 1)
                val wx = sx - x0
                val w00 = (1f - wx) * (1f - wy)
                val w10 = wx * (1f - wy)
                val w01 = (1f - wx) * wy
                val w11 = wx * wy
                val c00 = source[y0 * width + x0]
                val c10 = source[y0 * width + x1]
                val c01 = source[y1 * width + x0]
                val c11 = source[y1 * width + x1]
                val out = y * pad + x
                val r = (((((c00 ushr 16) and 0xff) * w00 + ((c10 ushr 16) and 0xff) * w10 +
                    ((c01 ushr 16) and 0xff) * w01 + ((c11 ushr 16) and 0xff) * w11) * (1f / 255f)) - 0.485f) * (1f / 0.229f)
                val g = (((((c00 ushr 8) and 0xff) * w00 + ((c10 ushr 8) and 0xff) * w10 +
                    ((c01 ushr 8) and 0xff) * w01 + ((c11 ushr 8) and 0xff) * w11) * (1f / 255f)) - 0.456f) * (1f / 0.224f)
                val b = ((((c00 and 0xff) * w00 + (c10 and 0xff) * w10 +
                    (c01 and 0xff) * w01 + (c11 and 0xff) * w11) * (1f / 255f) - 0.406f) * (1f / 0.225f))
                expected[out] = r
                expected[plane + out] = g
                expected[2 * plane + out] = b
            }
        }
        for (index in expected.indices) {
            assertEquals("$width x $height tensor index $index", expected[index], actual.get(index), 2e-6f)
        }
        val paddingIndex = if (resizedWidth < pad) resizedWidth else resizedHeight * pad
        assertEquals("$width x $height red padding", 0f, actual.get(paddingIndex), 0f)
        assertEquals("$width x $height green padding", 0f, actual.get(plane + paddingIndex), 0f)
        assertEquals("$width x $height blue padding", 0f, actual.get(2 * plane + paddingIndex), 0f)
    }
}
