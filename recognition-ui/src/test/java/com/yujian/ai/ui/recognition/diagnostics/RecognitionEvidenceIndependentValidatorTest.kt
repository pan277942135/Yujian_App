package com.yujian.ai.ui.recognition.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecognitionEvidenceIndependentValidatorTest {
    @Test
    fun rejectsEventSequenceRegressionAfterReadingThePackagedZip() {
        val events = """
            {"schemaVersion":1,"sequence":2,"timestampUptimeMs":10,"eventType":"FIRST"}
            {"schemaVersion":1,"sequence":2,"timestampUptimeMs":11,"eventType":"SECOND"}
        """.trimIndent().toByteArray()
        val manifest = "{}".toByteArray()
        val archive = archive(mapOf("logs/recognition_events.jsonl" to events), manifest)
        try {
            assertTrue(runCatching {
                RecognitionEvidenceArchiveVerifier.verify(archive, mapOf(
                    "logs/recognition_events.jsonl" to sha256(events),
                ), manifest)
            }.exceptionOrNull()?.message.orEmpty().contains("EVENT_SEQUENCE_NOT_INCREASING"))
        } finally {
            archive.delete()
        }
    }

    @Test
    fun rejectsAnAvailableFileDeclaredInTheArchiveButMissingFromTheZip() {
        val availability = """
            {"schemaVersion":1,"files":{"logs/missing.jsonl":{"status":"AVAILABLE","reason":null,"sizeBytes":0}}}
        """.trimIndent().toByteArray()
        val manifest = "{}".toByteArray()
        val archive = archive(mapOf("reports/evidence_availability.json" to availability), manifest)
        try {
            assertTrue(runCatching {
                RecognitionEvidenceArchiveVerifier.verify(archive, mapOf(
                    "reports/evidence_availability.json" to sha256(availability),
                ), manifest)
            }.exceptionOrNull()?.message.orEmpty().contains("AVAILABLE_EVIDENCE_MISSING_FROM_ZIP"))
        } finally {
            archive.delete()
        }
    }

    private fun archive(files: Map<String, ByteArray>, manifest: ByteArray): File {
        val target = File.createTempFile("yujian-independent-evidence", ".zip")
        ZipOutputStream(target.outputStream()).use { zip ->
            (files + ("manifest.json" to manifest)).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
