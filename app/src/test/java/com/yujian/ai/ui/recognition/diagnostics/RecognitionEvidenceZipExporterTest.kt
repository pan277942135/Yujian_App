package com.yujian.ai.ui.recognition.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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

    @Test fun blockedProcessingStagesExportVerifiedZipWithoutInventingResultEvidence() {
        val identity = RecognitionEvidenceIdentity("session-1", "attempt-1", "image-1", "photo-sha")
        val attemptBeforeExport = identity.copy()
        val blockedStages = listOf("ORB_RELEASE", "PERIMETER_ARRIVAL", "PATH_TO_FISH")
        val stageCaptures = blockedStages.map { stage ->
            RecognitionEvidenceCaptureSnapshot(stage, "STAGE_ENTRY", "SUCCESS")
        }
        val resultStatus = RecognitionEvidenceResultStatusPolicy.fromCaptureSnapshots(stageCaptures)
        assertEquals("RESULT_EVIDENCE_NOT_AVAILABLE", resultStatus.status)
        assertEquals("RESULT_EVIDENCE_NOT_AVAILABLE", resultStatus.missingReason)
        assertEquals(false, resultStatus.resultStablePixelCopySucceeded)

        val stageTimeline = """{"stages":[{"stage":"ORB_RELEASE","status":"BLOCKED","waitingReason":"WAITING_FOR_RENDERED_RELEASE_EVENT"},{"stage":"PERIMETER_ARRIVAL","status":"BLOCKED","waitingReason":"WAITING_FOR_PERIMETER_ARRIVAL_EVENT"},{"stage":"PATH_TO_FISH","status":"BLOCKED","waitingReason":"WAITING_FOR_PSTAR_PATH"}]}""".toByteArray()
        val pathLog = """{"stage":"PATH_TO_FISH","feedback":"ABSORB","rejectionReason":"EVENT_PATH_PENDING","attemptIdHash":"${sha256(identity.attemptId)}"}
""".toByteArray()
        val report = resultStatus.toJsonString().toByteArray()
        val files = mapOf(
            "session/session_summary.json" to """{"sessionIdHash":"${sha256(identity.sessionId)}","attemptIdHash":"${sha256(identity.attemptId)}"}""".toByteArray(),
            "logs/stage_timeline.json" to stageTimeline,
            "logs/waterlight_paths.jsonl" to pathLog,
            "reports/result_evidence_status.json" to report,
        )
        val manifest = """{"schemaVersion":1,"sessionIdHash":"${sha256(identity.sessionId)}","attemptIdHash":"${sha256(identity.attemptId)}"}""".toByteArray()
        val archive = createArchive(files, manifest)

        RecognitionEvidenceArchiveVerifier.verify(archive, files.mapValues { sha256(it.value) }, manifest)
        assertTrue(archive.isFile)
        assertEquals(attemptBeforeExport, identity)
        assertEquals(
            """{"status":"RESULT_EVIDENCE_NOT_AVAILABLE","missingReason":"RESULT_EVIDENCE_NOT_AVAILABLE","resultStablePixelCopySucceeded":false}""",
            report.decodeToString(),
        )
    }

    private fun createArchive(content: ByteArray, manifest: ByteArray): File {
        return createArchive(mapOf("logs/recognition_events.jsonl" to content), manifest)
    }

    private fun createArchive(files: Map<String, ByteArray>, manifest: ByteArray): File {
        val archive = File.createTempFile("recognition-evidence-test-", ".zip")
        archive.deleteOnExit()
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
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
