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

    @Test fun concurrentWritesDoNotCorruptImmutableSnapshotPackagingOrHashes() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-p0-snapshot-test").toFile()
        val attemptDir = File(tempRoot, "attempt").apply { mkdirs() }
        val stagingDir = File(tempRoot, "staging").apply { mkdirs() }
        val outputDir = File(tempRoot, "diagnostics").apply { mkdirs() }
        var nowMs = 100L
        val sink = RecognitionEvidenceEventSink(attemptDir, clockMs = { ++nowMs })
        val keepWriting = java.util.concurrent.atomic.AtomicBoolean(true)
        val writerThread = Thread {
            var idx = 0
            while (keepWriting.get()) {
                sink.record(RecognitionEvidenceEvent("recognition_events.jsonl", "CONCURRENT_WRITE", mapOf("seq" to ++idx)))
                Thread.sleep(1L)
            }
        }.apply { start() }
        try {
            Thread.sleep(25L)
            assertTrue(sink.flush(2_000L))
            val diagnostics = sink.diagnostics()
            assertEquals("writeFailures=$diagnostics", 0L, diagnostics.writeFailures)
            assertTrue("incompleteReasons=$diagnostics", diagnostics.incompleteReasons.isEmpty())
            val liveLogFiles = File(attemptDir, "logs").listFiles().orEmpty().filter { it.isFile }
            assertTrue(liveLogFiles.map { it.name }.contains("recognition_events.jsonl"))
            assertTrue(liveLogFiles.sumOf { it.length() } > 0L)
            val snapshot = sink.snapshotLogsTo(File(stagingDir, "logs"), flushTimeoutMs = 500L)
            assertEquals(0L, snapshot.diagnostics.writeFailures)
            assertTrue(snapshot.diagnostics.incompleteReasons.none { it.contains("FLUSH_TIMEOUT") })
            assertTrue(snapshot.copiedFiles.contains("recognition_events.jsonl"))
            val frozenLog = File(stagingDir, "logs/recognition_events.jsonl")
            val frozenBytesBeforePack = frozenLog.readBytes()
            assertTrue(frozenBytesBeforePack.isNotEmpty())
            Thread.sleep(25L)
            val liveBytesAfter = File(attemptDir, "logs/recognition_events.jsonl").readBytes()
            assertTrue(liveBytesAfter.size >= frozenBytesBeforePack.size)
            assertTrue(frozenLog.readBytes().contentEquals(frozenBytesBeforePack))

            val outputZip = File(outputDir, "YuJian_Recognition_Evidence_test.zip")
            val packaged = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(stagingDir, outputZip) { hashes ->
                """{"schemaVersion":1,"fileCount":${hashes.size}}""".toByteArray(Charsets.UTF_8)
            }
            assertTrue(packaged.archive.isFile)
            assertEquals(packaged.sha256, sha256(packaged.archive.readBytes()))
            val sidecar = File(outputDir, "${outputZip.name}.sha256")
            assertTrue(sidecar.isFile)
            assertTrue(sidecar.readText().startsWith(packaged.sha256))
        } finally {
            keepWriting.set(false)
            writerThread.join(1_000L)
            sink.dispose()
            tempRoot.deleteRecursively()
        }
    }

    @Test fun interruptedZipExportKeepsPreviousArchiveAndRemovesPartialTemporaryArchive() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-zip-interrupt-test").toFile()
        val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
        val archive = File(tempRoot, "recognition-evidence.zip").apply { writeText("previous-valid-archive") }
        File(snapshot, "attempt.json").writeText("{\"terminalState\":\"PROCESS_DIED\"}")
        File(snapshot, "events.jsonl").writeText("{\"evidenceSequence\":1}\n")
        try {
            try {
                RecognitionEvidenceZipExporter.packageSnapshotAndVerify(
                    snapshotDir = snapshot,
                    outputArchive = archive,
                    afterEntryWritten = { throw java.io.IOException("INJECTED_ZIP_EXPORT_INTERRUPTION") },
                ) { hashes -> "{\"fileCount\":${hashes.size}}".toByteArray() }
                throw AssertionError("interrupted export should fail")
            } catch (expected: java.io.IOException) {
                assertEquals("INJECTED_ZIP_EXPORT_INTERRUPTION", expected.message)
            }
            assertEquals("previous-valid-archive", archive.readText())
            assertTrue(tempRoot.listFiles().orEmpty().none { it.name.contains(".tmp.") })
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test fun duplicateZipExportAtomicallyReplacesArchiveWithIdenticalVerifiedBytes() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-zip-repeat-test").toFile()
        val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
        val archive = File(tempRoot, "recognition-evidence.zip")
        File(snapshot, "attempt.json").writeText("{\"terminalState\":\"INTERRUPTED\"}")
        File(snapshot, "recovery_report.json").writeText("{\"recoveryStatus\":\"RECOVERED\"}")
        try {
            val first = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(snapshot, archive) { hashes ->
                "{\"fileCount\":${hashes.size}}".toByteArray()
            }
            val firstBytes = archive.readBytes()
            val second = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(snapshot, archive) { hashes ->
                "{\"fileCount\":${hashes.size}}".toByteArray()
            }
            assertEquals(first.sha256, second.sha256)
            assertTrue(firstBytes.contentEquals(archive.readBytes()))
            assertTrue(File(tempRoot, "${archive.name}.sha256").readText().startsWith(second.sha256))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test fun interruptedArchiveReplacementRestoresPreviousPublishedArchive() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-zip-replace-interrupt-test").toFile()
        val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
        val archive = File(tempRoot, "recognition-evidence.zip").apply { writeText("previous-valid-archive") }
        File(snapshot, "attempt.json").writeText("{\"terminalState\":\"PROCESS_DIED\"}")
        try {
            try {
                RecognitionEvidenceZipExporter.packageSnapshotAndVerify(
                    snapshotDir = snapshot,
                    outputArchive = archive,
                    beforePublish = { throw java.io.IOException("INJECTED_PUBLISH_INTERRUPTION") },
                ) { hashes -> "{\"fileCount\":${hashes.size}}".toByteArray(Charsets.UTF_8) }
                throw AssertionError("interrupted replacement should fail")
            } catch (expected: java.io.IOException) {
                assertEquals("INJECTED_PUBLISH_INTERRUPTION", expected.message)
            }
            assertEquals("previous-valid-archive", archive.readText())
            assertTrue(tempRoot.listFiles().orEmpty().none { it.name.endsWith(".backup") })
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test fun recoveryExportRepairsBackupLeftByProcessDeathThenPublishesVerifiedArchive() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-zip-recovery-test").toFile()
        val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
        val archive = File(tempRoot, "recognition-evidence.zip")
        File(tempRoot, "${archive.name}.backup").writeText("previous-valid-archive")
        File(snapshot, "attempt.json").writeText("{\"terminalState\":\"PROCESS_DIED\"}")
        try {
            val packaged = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(snapshot, archive) { hashes ->
                "{\"fileCount\":${hashes.size}}".toByteArray(Charsets.UTF_8)
            }
            RecognitionEvidenceArchiveVerifier.verify(
                packaged.archive,
                packaged.fileHashes,
                File(snapshot, "manifest.json").readBytes(),
            )
            assertEquals(packaged.sha256, sha256(archive.readBytes()))
            assertTrue(File(tempRoot, "${archive.name}.sha256").readText().startsWith(packaged.sha256))
            assertTrue(!File(tempRoot, "${archive.name}.backup").exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test fun logSnapshotEnforcesByteCapAndMarksIncompleteReason() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-p0-cap-test").toFile()
        val attemptDir = File(tempRoot, "attempt").apply { mkdirs() }
        val logsDir = File(attemptDir, "logs").apply { mkdirs() }
        val sink = RecognitionEvidenceEventSink(attemptDir, clockMs = { 1_000L })
        try {
            val payload = (1..200).joinToString("\n") { """{"line":$it,"msg":"abcdefghij"}""" } + "\n"
            File(logsDir, "waterlight_paths.jsonl").writeText(payload, Charsets.UTF_8)
            val stagingLogs = File(tempRoot, "staging_logs")
            val result = sink.snapshotLogsTo(stagingLogs, maxTotalBytes = 512L)
            assertTrue(result.sizeCapReached)
            assertTrue(result.truncatedFiles.contains("waterlight_paths.jsonl"))
            assertTrue(result.diagnostics.incompleteReasons.contains("LOG_SNAPSHOT_SIZE_CAP_REACHED"))
            val copied = File(stagingLogs, "waterlight_paths.jsonl")
            assertTrue(copied.length() <= 512L)
            assertTrue(copied.readText().contains("""{"line":200"""))
        } finally {
            sink.dispose()
            tempRoot.deleteRecursively()
        }
    }

    @Test fun boundedCommandCaptureDegradesGracefullyOnTimeoutTruncationAndMissingBinary() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-p0-logcat-test").toFile()
        try {
            val timeoutFile = File(tempRoot, "timeout.txt")
            val startedNs = System.nanoTime()
            val timeoutResult = RecognitionEvidenceZipExporter.captureBoundedCommandOutput(
                command = listOf("sh", "-c", "echo first_line; sleep 2"),
                target = timeoutFile,
                timeoutMs = 150L,
                maxBytes = 4_096,
            )
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            assertEquals("TIMEOUT", timeoutResult.status)
            assertEquals("LOGCAT_TIMEOUT", timeoutResult.degradedReason)
            assertTrue(elapsedMs < 1_500L)
            assertTrue(timeoutFile.readText().contains("LOGCAT_TIMEOUT"))

            val truncatedFile = File(tempRoot, "truncated.txt")
            val truncatedResult = RecognitionEvidenceZipExporter.captureBoundedCommandOutput(
                command = listOf("sh", "-c", "i=0; while [ \$i -lt 400 ]; do echo 'logline1234567890'; i=\$((i+1)); done"),
                target = truncatedFile,
                timeoutMs = 2_000L,
                maxBytes = 1_024,
            )
            assertEquals("TRUNCATED", truncatedResult.status)
            assertTrue(truncatedResult.truncated)
            assertEquals("LOGCAT_SIZE_CAP_REACHED", truncatedResult.degradedReason)
            assertTrue(truncatedFile.length() <= 1_100L)

            val missingFile = File(tempRoot, "missing.txt")
            val missingResult = RecognitionEvidenceZipExporter.captureBoundedCommandOutput(
                command = listOf("/no/such/logcat_binary_for_test"),
                target = missingFile,
                timeoutMs = 500L,
                maxBytes = 1_024,
            )
            assertEquals("FAILED", missingResult.status)
            assertTrue(missingFile.readText().startsWith("LOGCAT_NOT_RECORDED:"))
        } finally {
            tempRoot.deleteRecursively()
        }
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
