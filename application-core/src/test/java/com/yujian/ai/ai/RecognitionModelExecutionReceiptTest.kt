package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
