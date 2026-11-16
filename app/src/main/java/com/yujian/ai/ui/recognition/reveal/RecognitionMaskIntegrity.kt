package com.yujian.ai.ui.recognition.reveal

import java.io.File
import java.security.MessageDigest

/** Verifies that the decoded Mask bytes, model result and Mask geometry share one SHA identity. */
object RecognitionMaskIntegrity {
    fun sha256File(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun matches(declaredSha256: String, geometrySha256: String?, fileSha256: String?): Boolean =
        declaredSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
            geometrySha256 == declaredSha256 && fileSha256 == declaredSha256
}
