package com.yujian.ai.ai.subject

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.FloatBuffer

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
}
