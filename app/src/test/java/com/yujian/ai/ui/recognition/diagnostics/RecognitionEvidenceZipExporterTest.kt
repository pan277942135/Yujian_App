package com.yujian.ai.ui.recognition.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEvidenceZipExporterTest {
    @Test fun archiveContainsExactlyTheHashedFilesAndMatchingManifest() {
        val content = "events\n".toByteArray()
        val manifest = "{\"schemaVersion\":1}\n".toByteArray()
        val archive = createArchive(content, manifest)

        RecognitionEvidenceArchiveVerifier.verify(
            archive,
            mapOf("logs/recognition_events.jsonl" to sha256(content)),
            manifest,
        )
        assertTrue(archive.isFile)
    }

    @Test(expected = IllegalStateException::class)
    fun archiveRejectsAChangedManifest() {
        val content = "events\n".toByteArray()
        val manifest = "{\"schemaVersion\":1}\n".toByteArray()
        val archive = createArchive(content, manifest)
        RecognitionEvidenceArchiveVerifier.verify(
            archive,
            mapOf("logs/recognition_events.jsonl" to sha256(content)),
            "{}\n".toByteArray(),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun archiveRejectsAChangedFileHash() {
        val content = "events\n".toByteArray()
        val manifest = "{\"schemaVersion\":1}\n".toByteArray()
        val archive = createArchive(content, manifest)
        RecognitionEvidenceArchiveVerifier.verify(
            archive,
            mapOf("logs/recognition_events.jsonl" to "0".repeat(64)),
            manifest,
        )
    }

    private fun createArchive(content: ByteArray, manifest: ByteArray): File {
        val archive = File.createTempFile("recognition-evidence-test-", ".zip")
        archive.deleteOnExit()
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("logs/recognition_events.jsonl"))
            zip.write(content)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
        }
        return archive
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
