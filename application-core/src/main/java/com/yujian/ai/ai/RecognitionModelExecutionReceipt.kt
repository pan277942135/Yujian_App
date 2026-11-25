package com.yujian.ai.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/** Per-artifact identity; expected, packaged asset, and runtime-verified hashes remain distinct. */
data class RecognitionRuntimeModelIdentity(
    val modelId: String,
    val modelVersion: String,
    val expectedModelSha256: String,
    val actualAssetSha256: String,
    val runtimeVerifiedModelSha256: String,
    val modelFormat: String,
    val inputSchemaHash: String,
    val outputSchemaHash: String,
    val preprocessVersion: String,
    val postprocessVersion: String,
    val runtimeName: String,
    val runtimeVersion: String,
    val executionProvider: String,
    val modelSessionId: String,
) {
    fun isValid(): Boolean = listOf(
        modelId, modelVersion, modelFormat, preprocessVersion, postprocessVersion,
        runtimeName, runtimeVersion, executionProvider, modelSessionId,
    ).all(String::isNotBlank) && listOf(
        expectedModelSha256, actualAssetSha256, runtimeVerifiedModelSha256,
        inputSchemaHash, outputSchemaHash,
    ).all { it.matches(SHA256_PATTERN) } &&
        expectedModelSha256 == actualAssetSha256 && actualAssetSha256 == runtimeVerifiedModelSha256
}

/** Hashes and metadata are recorded after the producer owns a stable tensor buffer. */
data class RecognitionModelTensorReceipt(
    val inferenceId: String,
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val canonicalPixelSha256: String,
    val modelIdentity: RecognitionRuntimeModelIdentity,
    val tensorName: String,
    val dataType: String,
    val shape: List<Long>,
    val byteSize: Long,
    val contentHash: String,
    val startUptimeMs: Long,
    val endUptimeMs: Long,
    val validationStatus: String,
    val errorCode: String? = null,
) {
    fun isValid(): Boolean = inferenceId.isNotBlank() && recognitionSessionId.isNotBlank() &&
        attemptId.isNotBlank() && photoId.isNotBlank() && canonicalPixelSha256.matches(SHA256_PATTERN) &&
        modelIdentity.isValid() && tensorName.isNotBlank() && dataType.isNotBlank() && shape.isNotEmpty() &&
        shape.all { it > 0L } && byteSize == expectedByteSize() && contentHash.matches(SHA256_PATTERN) &&
        startUptimeMs >= 0L && endUptimeMs >= startUptimeMs && validationStatus == "VALID" && errorCode == null

    private fun expectedByteSize(): Long? {
        val bytesPerElement = when (dataType.uppercase()) {
            "FLOAT", "FLOAT32", "INT32", "UINT32" -> 4L
            "DOUBLE", "FLOAT64", "INT64", "UINT64" -> 8L
            "INT16", "UINT16", "FLOAT16", "BFLOAT16" -> 2L
            "INT8", "UINT8", "BOOL" -> 1L
            else -> return null
        }
        return runCatching {
            Math.multiplyExact(
                shape.fold(1L) { product, dimension -> Math.multiplyExact(product, dimension) },
                bytesPerElement,
            )
        }.getOrNull()
    }
}

internal object RecognitionTensorContentHash {
    fun sha256(buffer: ByteBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val copy = buffer.asReadOnlyBuffer()
        val chunk = ByteArray(minOf(64 * 1024, copy.remaining().coerceAtLeast(1)))
        while (copy.hasRemaining()) {
            val count = minOf(copy.remaining(), chunk.size)
            copy.get(chunk, 0, count)
            digest.update(chunk, 0, count)
        }
        return digest.digest().toHex()
    }

    /** Canonical little-endian IEEE754 float bytes, hashed in bounded chunks without a full copy. */
    fun sha256(buffer: FloatBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val copy = buffer.asReadOnlyBuffer()
        val bytes = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        while (copy.hasRemaining()) {
            bytes.clear()
            while (copy.hasRemaining() && bytes.remaining() >= Float.SIZE_BYTES) bytes.putFloat(copy.get())
            digest.update(bytes.array(), 0, bytes.position())
        }
        return digest.digest().toHex()
    }

    /** Canonical little-endian signed 64-bit bytes, hashed without copying the whole tensor. */
    fun sha256(buffer: LongBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val copy = buffer.asReadOnlyBuffer()
        val bytes = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        while (copy.hasRemaining()) {
            bytes.clear()
            while (copy.hasRemaining() && bytes.remaining() >= Long.SIZE_BYTES) bytes.putLong(copy.get())
            digest.update(bytes.array(), 0, bytes.position())
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private val SHA256_PATTERN = Regex("(?i)^[a-f0-9]{64}$")
