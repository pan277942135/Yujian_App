package com.yujian.ai.ai

import com.yujian.ai.ai.subject.allocateSlimSamPixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecognitionModelExecutionReceiptTest {
    @Test fun receiptRequiresByteSizeToMatchTensorShapeAndType() {
        val valid = receipt(dataType = "FLOAT", shape = listOf(1, 4), byteSize = 16)
        assertTrue(valid.isValid())
        assertFalse(valid.copy(byteSize = 15).isValid())
        assertFalse(valid.copy(dataType = "UNSUPPORTED_TYPE").isValid())
        assertFalse(valid.copy(shape = listOf(1, 5)).isValid())
    }

    @Test fun tensorHashUsesRemainingBytesWithoutChangingCallerPosition() {
        val buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(11)
            .putInt(22)
        buffer.flip()
        buffer.position(4)
        val hash = RecognitionTensorContentHash.sha256(buffer)
        assertEquals(4, buffer.position())
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun directByteBufferHashMatchesCanonicalFloatBufferHash() {
        val bytes = allocateSlimSamPixelBuffer()
        val floats = bytes.asFloatBuffer()
        val values = floatArrayOf(1.25f, -2.5f, 0f, -0f, Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE)
        val elementCount = 3 * 1024 * 1024
        for (index in 0 until elementCount) {
            floats.put(index, values[index % values.size])
        }

        assertTrue(bytes.isDirect)
        assertEquals(ByteOrder.LITTLE_ENDIAN, bytes.order())
        val directHash = RecognitionTensorContentHash.sha256(bytes)
        assertEquals(RecognitionTensorContentHash.sha256(floats), directHash)
        assertEquals(64, directHash.length)
        assertEquals(0, bytes.position())
        assertEquals(elementCount * Float.SIZE_BYTES, bytes.limit())
        assertEquals(0, floats.position())
        assertEquals(elementCount, floats.limit())

        // A change in the final float must affect the receipt, even across hash chunks.
        floats.put(elementCount - 1, 42f)
        val updatedHash = RecognitionTensorContentHash.sha256(bytes)
        assertNotEquals(directHash, updatedHash)
        assertEquals(RecognitionTensorContentHash.sha256(floats), updatedHash)
    }

    @Test fun finiteValidationAndHashMatchCanonicalHashWithoutChangingPosition() {
        val buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(1.25f)
            .putFloat(-2.5f)
            .putFloat(0f)
            .putFloat(Float.MAX_VALUE)
        buffer.flip()
        val floats = buffer.asFloatBuffer()
        floats.position(1)
        val originalPosition = floats.position()

        val combinedHash = RecognitionTensorContentHash.sha256AndRequireFinite(
            floats,
            "non-finite test tensor",
        )

        assertEquals(RecognitionTensorContentHash.sha256(floats), combinedHash)
        assertEquals(originalPosition, floats.position())
    }

    @Test fun finiteValidationRejectsNanAndInfinity() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            val buffer = ByteBuffer.allocateDirect(Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(invalid)
                .apply { flip() }
                .asFloatBuffer()

            val error = assertThrows(IllegalArgumentException::class.java) {
                RecognitionTensorContentHash.sha256AndRequireFinite(buffer, "tensor contains a non-finite value")
            }
            assertEquals("tensor contains a non-finite value", error.message)
            assertEquals(0, buffer.position())
        }
    }

    private fun receipt(
        dataType: String,
        shape: List<Long>,
        byteSize: Long,
    ) = RecognitionModelTensorReceipt(
        inferenceId = "inference-1",
        recognitionSessionId = "recognition-session-1",
        attemptId = "attempt-1",
        photoId = "photo-1",
        canonicalPixelSha256 = SHA,
        modelIdentity = RecognitionRuntimeModelIdentity(
            modelId = "detector",
            modelVersion = "v1",
            expectedModelSha256 = SHA,
            actualAssetSha256 = SHA,
            runtimeVerifiedModelSha256 = SHA,
            modelFormat = "ONNX",
            inputSchemaHash = SHA,
            outputSchemaHash = SHA,
            preprocessVersion = "preprocess-v1",
            postprocessVersion = "postprocess-v1",
            runtimeName = "ONNX Runtime Android",
            runtimeVersion = "1.29.0",
            executionProvider = "CPU",
            modelSessionId = "model-session-1",
        ),
        tensorName = "input",
        dataType = dataType,
        shape = shape,
        byteSize = byteSize,
        contentHash = SHA,
        startUptimeMs = 1,
        endUptimeMs = 2,
        validationStatus = "VALID",
    )

    private companion object {
        val SHA = "0123456789abcdef".repeat(4)
    }
}
