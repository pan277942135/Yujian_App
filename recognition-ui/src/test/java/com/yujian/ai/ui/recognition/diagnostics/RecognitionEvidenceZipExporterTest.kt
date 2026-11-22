package com.yujian.ai.ui.recognition.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEvidenceZipExporterTest {
    @Test fun archiveCopyPreservesBytesForDocumentProvider() {
        val bytes = ByteArray(140_000) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), RecognitionEvidenceZipExporter.copyArchive(bytes.inputStream(), output))
        assertTrue(bytes.contentEquals(output.toByteArray()))
    }

    @Test(expected = java.io.IOException::class)
    fun documentProviderWriteFailurePropagates() {
        val output = object : OutputStream() {
            override fun write(value: Int) { throw java.io.IOException("provider rejected write") }
            override fun write(data: ByteArray, offset: Int, length: Int) { throw java.io.IOException("provider rejected write") }
        }
        RecognitionEvidenceZipExporter.copyArchive(byteArrayOf(1, 2).inputStream(), output)
    }
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

    @Test fun archiveIndexesResolveToFullResolutionWebpCheckpointsWithinStandardBudget() {
        val fixture = requireNotNull(
            javaClass.getResourceAsStream("/evidence/representative_checkpoint_1080x2400_q92.webp"),
        ).use { it.readBytes() }
        assertTrue(fixture.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
        assertTrue(fixture.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()))
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-26kf-webp-budget-test").toFile()
        val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
        try {
            val captureIndex = JSONArray()
            val checkpointIndex = JSONArray()
            repeat(26) { index ->
                val checkpointId = "KF${index.toString().padStart(2, '0')}"
                val captureId = "capture-$checkpointId"
                val path = "frames/checkpoints/${checkpointId}_ORB_RETURN.webp"
                File(snapshot, path).apply { parentFile?.mkdirs(); writeBytes(fixture) }
                val hash = sha256(fixture)
                captureIndex.put(
                    JSONObject()
                        .put("captureId", captureId).put("checkpointId", checkpointId)
                        .put("stage", "ORB_RETURN").put("pixelCopyStatus", "SUCCESS")
                        .put("priority", "KEY").put("encoding", "WEBP").put("mimeType", "image/webp")
                        .put("quality", 92)
                        .put("width", 1080).put("height", 2400).put("relativePath", path).put("sha256", hash),
                )
                checkpointIndex.put(
                    JSONObject()
                        .put("checkpointId", checkpointId).put("stage", "ORB_RETURN")
                        .put("status", "CAPTURED").put("captureId", captureId)
                        .put("relativePath", path).put("sha256", hash),
                )
            }
            File(snapshot, "frames/frame_index.json").writeText(captureIndex.toString(), Charsets.UTF_8)
            File(snapshot, "frames/26kf_checkpoint_index.json").writeText(checkpointIndex.toString(), Charsets.UTF_8)
            File(snapshot, "logs/recognition_events.jsonl").apply {
                parentFile?.mkdirs()
                writeText("{\"event\":\"checkpoint-index-retained\"}\n", Charsets.UTF_8)
            }
            val budgetDegradedReasons = mutableListOf<String>()
            val budgetOutcome = RecognitionEvidenceZipExporter.applyStandardFrameBudget(
                snapshot,
                captureIndex,
                budgetDegradedReasons,
                lossless = false,
            )
            assertEquals(0, budgetOutcome.droppedSampleCount)
            assertTrue(budgetDegradedReasons.isEmpty())
            assertTrue(budgetOutcome.retainedFrameBytes <= RecognitionEvidenceZipExporter.STANDARD_FRAME_BUDGET_BYTES)
            val archive = File(tempRoot, "diagnostics/26kf.zip")
            val packaged = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(snapshot, archive) { hashes ->
                JSONObject().put("schemaVersion", 2).put("fileSha256", JSONObject(hashes))
                    .toString().toByteArray(Charsets.UTF_8)
            }
            assertTrue("unexpected ZIP size ${packaged.sizeBytes}", packaged.sizeBytes in (10L * 1024 * 1024)..(20L * 1024 * 1024))
            ZipFile(archive).use { zip ->
                val archivedCheckpoints = JSONArray(
                    zip.getInputStream(zip.getEntry("frames/26kf_checkpoint_index.json"))
                        .use { it.readBytes().toString(Charsets.UTF_8) },
                )
                val archivedFrames = JSONArray(
                    zip.getInputStream(zip.getEntry("frames/frame_index.json"))
                        .use { it.readBytes().toString(Charsets.UTF_8) },
                )
                assertEquals(26, archivedCheckpoints.length())
                assertEquals(26, archivedFrames.length())
                assertEquals(1080, archivedFrames.getJSONObject(0).getInt("width"))
                assertEquals(2400, archivedFrames.getJSONObject(0).getInt("height"))
                assertEquals(26, zip.entries().asSequence().count {
                    it.name.startsWith("frames/checkpoints/") && it.name.endsWith(".webp")
                })
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test fun frameBudgetDropsMotionSamplesBeforeKeyCheckpoints() {
        val root = java.nio.file.Files.createTempDirectory("yujian-frame-budget-test").toFile()
        try {
            val key = File(root, "frames/checkpoints/KF14.webp").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(128) { 7 })
            }
            val sample = File(root, "frames/motion_samples/sample.webp").apply {
                parentFile?.mkdirs()
                java.io.RandomAccessFile(this, "rw").use {
                    it.setLength(RecognitionEvidenceZipExporter.STANDARD_FRAME_BUDGET_BYTES + 1L)
                }
            }
            val index = JSONArray()
                .put(JSONObject().put("captureId", "key").put("priority", "KEY")
                    .put("pixelCopyStatus", "SUCCESS").put("relativePath", key.relativeTo(root).invariantSeparatorsPath))
                .put(JSONObject().put("captureId", "sample").put("priority", "SAMPLE")
                    .put("pixelCopyStatus", "SUCCESS").put("relativePath", sample.relativeTo(root).invariantSeparatorsPath))
            val degraded = mutableListOf<String>()
            val outcome = RecognitionEvidenceZipExporter.applyStandardFrameBudget(root, index, degraded, lossless = false)
            assertEquals(1, outcome.droppedSampleCount)
            assertTrue(key.isFile)
            assertTrue(!sample.exists())
            assertEquals("SUCCESS", index.getJSONObject(0).optString("pixelCopyStatus"))
            assertEquals("DROPPED_SAMPLE_BUDGET", index.getJSONObject(1).optString("pixelCopyStatus"))
            assertTrue(degraded.single().startsWith("MOTION_SAMPLES_DROPPED_TO_MEET_FRAME_BUDGET"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun originalMaskPngBytesAndShaSurviveArchivePackagingExactly() {
        val sourceBytes = requireNotNull(javaClass.getResourceAsStream("/evidence/original_mask_fixture.png"))
            .use { it.readBytes() }
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-mask-archive-test").toFile()
        try {
            val snapshot = File(tempRoot, "snapshot").apply { mkdirs() }
            File(snapshot, "mask/original_mask.png").apply { parentFile?.mkdirs(); writeBytes(sourceBytes) }
            val maskHash = sha256(sourceBytes)
            val archive = File(tempRoot, "mask.zip")
            val packaged = RecognitionEvidenceZipExporter.packageSnapshotAndVerify(snapshot, archive) { hashes ->
                JSONObject().put("originalMaskSha256", maskHash)
                    .put("originalMaskSizeBytes", sourceBytes.size).put("fileSha256", JSONObject(hashes))
                    .toString().toByteArray(Charsets.UTF_8)
            }
            ZipFile(packaged.archive).use { zip ->
                val archived = zip.getInputStream(zip.getEntry("mask/original_mask.png")).use { it.readBytes() }
                assertTrue(sourceBytes.contentEquals(archived))
                assertEquals(maskHash, sha256(archived))
                assertEquals(maskHash, packaged.fileHashes["mask/original_mask.png"])
            }
        } finally {
            tempRoot.deleteRecursively()
        }
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
        val sink = RecognitionEvidenceEventSink(attemptDir) { ++nowMs }
        val keepWriting = java.util.concurrent.atomic.AtomicBoolean(true)
        val writerThread = Thread {
            var idx = 0
            while (keepWriting.get()) {
                sink.recordRawLine("recognition_events.jsonl", """{"seq":${++idx}}""")
                Thread.sleep(1L)
            }
        }.apply { start() }
        try {
            Thread.sleep(25L)
            val snapshot = sink.snapshotLogsTo(File(stagingDir, "logs"), flushTimeoutMs = 500L)
            assertTrue(snapshot.copiedFiles.contains("recognition_events.jsonl"))
            val frozenLog = File(stagingDir, "logs/recognition_events.jsonl")
            val frozenBytesBeforePack = frozenLog.readBytes()
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

    @Test fun logSnapshotEnforcesByteCapAndMarksIncompleteReason() {
        val tempRoot = java.nio.file.Files.createTempDirectory("yujian-p0-cap-test").toFile()
        val attemptDir = File(tempRoot, "attempt").apply { mkdirs() }
        val logsDir = File(attemptDir, "logs").apply { mkdirs() }
        val sink = RecognitionEvidenceEventSink(attemptDir) { 1_000L }
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
