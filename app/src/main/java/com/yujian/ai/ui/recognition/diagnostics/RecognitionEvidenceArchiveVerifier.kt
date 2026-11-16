package com.yujian.ai.ui.recognition.diagnostics

import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object RecognitionEvidenceArchiveVerifier {
    fun verify(archive: File, expectedFileHashes: Map<String, String>, expectedManifest: ByteArray) {
        ZipFile(archive).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toList()
            val expectedNames = expectedFileHashes.keys + "manifest.json"
            check(entryNames.size == expectedNames.size && entryNames.toSet() == expectedNames.toSet()) {
                "ZIP_ENTRY_SET_MISMATCH"
            }
            expectedFileHashes.forEach { (path, expectedHash) ->
                val entry = requireNotNull(zip.getEntry(path)) { "ZIP_MISSING_ENTRY:$path" }
                val actualHash = zip.getInputStream(entry).use(::sha256)
                check(actualHash == expectedHash) { "ZIP_HASH_MISMATCH:$path" }
            }
            val archivedManifest = zip.getInputStream(requireNotNull(zip.getEntry("manifest.json"))).use { it.readBytes() }
            check(archivedManifest.contentEquals(expectedManifest)) { "ZIP_MANIFEST_MISMATCH" }
        }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(input).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
