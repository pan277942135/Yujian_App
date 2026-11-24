package com.yujian.ai.recognition.domain

import java.security.MessageDigest

private const val PHOTO_CANONICAL_DIGEST_DOMAIN_V1 = "YUJIAN_CANONICAL_IMAGE_RGBA8_SRGB_V1\u0000"

/** Immutable identity for the source photo and the exact canonical pixels consumed by models. */
data class PhotoIdentity(
    val photoId: String,
    val sourceBytesSha256: String,
    val canonicalPixelSha256: String,
    val width: Int,
    val height: Int,
    /** Original EXIF orientation value (1..8), before normalization. */
    val sourceOrientation: Int,
    val normalizationVersion: String,
    val photoContentVersion: Long,
) {
    fun isValid(): Boolean = photoId.isNotBlank() &&
        sourceBytesSha256.isSha256() && canonicalPixelSha256.isSha256() &&
        width > 0 && height > 0 && sourceOrientation in 1..8 &&
        normalizationVersion.isNotBlank() && photoContentVersion > 0L

    fun matches(other: PhotoIdentity): Boolean = this == other

    companion object {
        const val NORMALIZATION_VERSION_V1 = "EXIF_1_TO_8_MAX_DIMENSION_2048_CAMERA_VIEWPORT_CROP_V1"
        const val PIXEL_SERIALIZATION_VERSION_V1 = "ARGB8888_TO_RGBA8_SRGB_TOP_DOWN_V1"
        const val PIXEL_FORMAT_V1 = "RGBA_8888"
        const val COLOR_SPACE_V1 = "sRGB"
        const val ROW_ORDER_V1 = "TOP_TO_BOTTOM"
        const val CHANNEL_ORDER_V1 = "RGBA"
        const val ORIENTATION_NORMALIZATION_V1 = "EXIF_ORIENTATION_1_TO_8_BEFORE_CROP_V1"
    }
}

/** Row reader keeps hashing bounded-memory and independent from Android Bitmap APIs. */
fun interface Argb8888PixelRows {
    fun copyRow(rowIndex: Int, destination: IntArray)
}

/** SHA-256 over canonical unpremultiplied ARGB_8888 pixels serialized as top-down RGBA bytes. */
object CanonicalPhotoPixelsV1 {
    fun sha256(width: Int, height: Int, rows: Argb8888PixelRows): String {
        require(width > 0 && height > 0)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(PHOTO_CANONICAL_DIGEST_DOMAIN_V1.toByteArray(Charsets.UTF_8))
        // Include dimensions so identical byte runs with different image geometry cannot alias.
        digest.update(byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
        ))
        val pixels = IntArray(width)
        val bytes = ByteArray(width * 4)
        for (y in 0 until height) {
            rows.copyRow(y, pixels)
            var out = 0
            for (argb in pixels) {
                bytes[out++] = ((argb ushr 16) and 0xff).toByte()
                bytes[out++] = ((argb ushr 8) and 0xff).toByte()
                bytes[out++] = (argb and 0xff).toByte()
                bytes[out++] = ((argb ushr 24) and 0xff).toByte()
            }
            digest.update(bytes)
        }
        return digest.digest().toLowerHex()
    }
}

fun String.isSha256(): Boolean = matches(Regex("(?i)^[a-f0-9]{64}$"))

private fun ByteArray.toLowerHex(): String = joinToString("") { "%02x".format(it) }
