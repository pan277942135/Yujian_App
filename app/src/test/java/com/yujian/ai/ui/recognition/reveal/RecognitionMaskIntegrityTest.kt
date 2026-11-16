package com.yujian.ai.ui.recognition.reveal

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionMaskIntegrityTest {
    @Test fun declaredGeometryAndActualPngBytesMustShareTheSameSha() {
        val maskFile = File.createTempFile("real-mask-identity-", ".png").apply {
            writeBytes(byteArrayOf(0x50, 0x4e, 0x47, 0x01, 0x02))
            deleteOnExit()
        }
        val actualSha = sha256(maskFile.readBytes())

        assertTrue(RecognitionMaskIntegrity.matches(actualSha, actualSha, actualSha))
        assertFalse(RecognitionMaskIntegrity.matches(actualSha, "0".repeat(64), actualSha))
        assertFalse(RecognitionMaskIntegrity.matches(actualSha, actualSha, "0".repeat(64)))
        assertFalse(RecognitionMaskIntegrity.matches("", actualSha, actualSha))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
