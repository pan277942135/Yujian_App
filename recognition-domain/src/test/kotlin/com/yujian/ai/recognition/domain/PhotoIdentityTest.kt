package com.yujian.ai.recognition.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoIdentityTest {
    @Test
    fun canonicalHashIsStableAcrossDifferentEncodedSourceIdentities() {
        val pixels = intArrayOf(0xff112233.toInt(), 0x80445566.toInt())
        val first = CanonicalPhotoPixelsV1.sha256(2, 1) { _, row -> pixels.copyInto(row) }
        val second = CanonicalPhotoPixelsV1.sha256(2, 1) { _, row -> pixels.copyInto(row) }
        assertEquals(first, second)

        val firstIdentity = identity(sourceHash = "a".repeat(64), canonicalHash = first)
        val secondIdentity = identity(sourceHash = "b".repeat(64), canonicalHash = second)
        assertTrue(firstIdentity.isValid())
        assertTrue(secondIdentity.isValid())
        assertNotEquals(firstIdentity.sourceBytesSha256, secondIdentity.sourceBytesSha256)
        assertEquals(firstIdentity.canonicalPixelSha256, secondIdentity.canonicalPixelSha256)
    }

    @Test
    fun differentPixelsProduceDifferentCanonicalHashes() {
        val first = CanonicalPhotoPixelsV1.sha256(1, 1) { _, row -> row[0] = 0xff000000.toInt() }
        val second = CanonicalPhotoPixelsV1.sha256(1, 1) { _, row -> row[0] = 0xffffffff.toInt() }
        assertNotEquals(first, second)
    }

    @Test
    fun serializationSpecificationIsFrozenAndIdentityFieldsAreValidated() {
        assertEquals("RGBA_8888", PhotoIdentity.PIXEL_FORMAT_V1)
        assertEquals("sRGB", PhotoIdentity.COLOR_SPACE_V1)
        assertEquals("TOP_TO_BOTTOM", PhotoIdentity.ROW_ORDER_V1)
        assertEquals("RGBA", PhotoIdentity.CHANNEL_ORDER_V1)
        assertEquals("ARGB8888_TO_RGBA8_SRGB_TOP_DOWN_V1", PhotoIdentity.PIXEL_SERIALIZATION_VERSION_V1)
        assertTrue(identity("a".repeat(64), "b".repeat(64)).isValid())
        assertTrue(!identity("bad", "b".repeat(64)).isValid())
    }

    private fun identity(sourceHash: String, canonicalHash: String) = PhotoIdentity(
        photoId = "photo-1",
        sourceBytesSha256 = sourceHash,
        canonicalPixelSha256 = canonicalHash,
        width = 2,
        height = 1,
        sourceOrientation = 1,
        normalizationVersion = PhotoIdentity.NORMALIZATION_VERSION_V1,
        photoContentVersion = 1L,
    )
}
