package com.yujian.ai.ui.recognition.diagnostics

import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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
                if (path.endsWith(".json")) {
                    zip.getInputStream(entry).use {
                        runCatching { JSONTokener(it.readBytes().toString(Charsets.UTF_8)).nextValue() }
                            .getOrElse { failure -> throw IllegalStateException("ZIP_JSON_INVALID:$path", failure) }
                    }
                }
            }
            val archivedManifest = zip.getInputStream(requireNotNull(zip.getEntry("manifest.json"))).use { it.readBytes() }
            check(archivedManifest.contentEquals(expectedManifest)) { "ZIP_MANIFEST_MISMATCH" }
            verifyScreenshotIndexes(zip, expectedFileHashes)
            verifyCheckpointStatusMatrix(zip)
            verifyEvidenceAvailability(zip)
            verifyRecognitionEventSequence(zip)
            verifyOriginalMaskHash(zip, expectedFileHashes, archivedManifest)
        }
    }

    private fun verifyCheckpointStatusMatrix(zip: ZipFile) {
        val entry = zip.getEntry("frames/26kf_checkpoint_index.json") ?: return
        val rows = zip.getInputStream(entry).use { JSONArray(it.readBytes().toString(Charsets.UTF_8)) }
        check(rows.length() == 26) { "CHECKPOINT_MATRIX_COUNT_MISMATCH" }
        val expectedIds = (0..25).map { "KF${it.toString().padStart(2, '0')}" }
        val allowedStatuses = setOf("CAPTURED", "CAPTURE_FAILED", "MISSING", "NOT_REACHED", "NOT_APPLICABLE")
        val actualIds = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: error("CHECKPOINT_ROW_INVALID:$index")
                val id = row.optString("checkpointId")
                check(row.optString("status") in allowedStatuses) { "CHECKPOINT_STATUS_INVALID:$id" }
                add(id)
            }
        }
        check(actualIds == expectedIds) { "CHECKPOINT_MATRIX_ID_ORDER_MISMATCH" }
    }

    private fun verifyEvidenceAvailability(zip: ZipFile) {
        val entry = zip.getEntry("reports/evidence_availability.json") ?: return
        val root = zip.getInputStream(entry).use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        check(root.optInt("schemaVersion") == 1) { "EVIDENCE_AVAILABILITY_SCHEMA_UNSUPPORTED" }
        val files = root.optJSONObject("files") ?: error("EVIDENCE_AVAILABILITY_FILES_MISSING")
        val allowed = setOf("AVAILABLE", "NOT_RUN", "UNAVAILABLE", "FAILED", "NOT_REACHED", "NOT_APPLICABLE")
        files.keys().forEach { path ->
            val row = files.optJSONObject(path) ?: error("EVIDENCE_AVAILABILITY_ROW_INVALID:$path")
            val status = row.optString("status")
            check(status in allowed) { "EVIDENCE_AVAILABILITY_STATUS_INVALID:$path:$status" }
            if (status == "AVAILABLE") {
                val archived = zip.getEntry(path) ?: error("AVAILABLE_EVIDENCE_MISSING_FROM_ZIP:$path")
                check(row.optLong("sizeBytes", -1L) == archived.size) { "EVIDENCE_AVAILABILITY_SIZE_MISMATCH:$path" }
            }
        }
    }

    private fun verifyRecognitionEventSequence(zip: ZipFile) {
        val entry = zip.getEntry("logs/recognition_events.jsonl") ?: return
        var previous = 0L
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { index, line ->
                val event = runCatching { JSONObject(line) }.getOrElse { error("EVENT_JSON_INVALID:${index + 1}") }
                check(event.optInt("schemaVersion") == 1) { "EVENT_SCHEMA_INVALID:${index + 1}" }
                val sequence = event.optLong("sequence", -1L)
                check(sequence > previous) { "EVENT_SEQUENCE_NOT_INCREASING:${index + 1}" }
                check(event.optString("eventType").isNotBlank()) { "EVENT_TYPE_MISSING:${index + 1}" }
                previous = sequence
            }
        }
    }

    private fun verifyOriginalMaskHash(zip: ZipFile, expectedFileHashes: Map<String, String>, manifestBytes: ByteArray) {
        val expected = JSONObject(manifestBytes.toString(Charsets.UTF_8)).optString("originalMaskSha256")
        if (expected.isBlank() || expected == "null") return
        val entry = zip.getEntry("mask/original_mask.png") ?: error("MASK_ORIGINAL_PNG_MISSING")
        val actual = zip.getInputStream(entry).use(::sha256)
        check(actual == expected) { "MASK_ORIGINAL_SHA256_MISMATCH" }
        check(expectedFileHashes["mask/original_mask.png"] == actual) { "MASK_MANIFEST_SHA256_MISMATCH" }
        val declaredSize = JSONObject(manifestBytes.toString(Charsets.UTF_8)).optLong("originalMaskSizeBytes", -1L)
        check(declaredSize == entry.size) { "MASK_ORIGINAL_SIZE_MISMATCH" }
    }

    private fun verifyScreenshotIndexes(zip: ZipFile, expectedFileHashes: Map<String, String>) {
        val frameIndexBytes = zip.getEntry("frames/frame_index.json")?.let { entry ->
            zip.getInputStream(entry).use { it.readBytes() }
        } ?: return
        val captureIndex = JSONArray(frameIndexBytes.toString(Charsets.UTF_8))
        val captureById = mutableMapOf<String, JSONObject>()
        for (index in 0 until captureIndex.length()) {
            val row = captureIndex.optJSONObject(index) ?: continue
            val captureId = row.optString("captureId")
            if (captureId.isNotBlank()) captureById[captureId] = row
            if (row.optString("pixelCopyStatus") != "SUCCESS") continue
            val path = row.optString("relativePath")
            check(path.isNotBlank()) { "CAPTURE_INDEX_PATH_MISSING:${row.optString("checkpointId")}" }
            when (row.optString("encoding")) {
                "WEBP" -> {
                    check(path.endsWith(".webp", ignoreCase = true)) { "CAPTURE_ENCODING_PATH_MISMATCH:$path" }
                    check(row.optString("mimeType") == "image/webp") { "CAPTURE_ENCODING_MIME_MISMATCH:$path" }
                    check(row.optInt("quality", -1) in 90..95) { "CAPTURE_WEBP_QUALITY_INVALID:$path" }
                }
                "PNG" -> {
                    check(path.endsWith(".png", ignoreCase = true)) { "CAPTURE_ENCODING_PATH_MISMATCH:$path" }
                    check(row.optString("mimeType") == "image/png") { "CAPTURE_ENCODING_MIME_MISMATCH:$path" }
                    val header = zip.getInputStream(requireNotNull(zip.getEntry(path))).use { input -> ByteArray(8).also { input.read(it) } }
                    check(header.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) {
                        "CAPTURE_PNG_SIGNATURE_INVALID:$path"
                    }
                }
                else -> error("CAPTURE_ENCODING_UNSUPPORTED:$path")
            }
            check(row.optInt("width") > 0 && row.optInt("height") > 0) { "CAPTURE_DIMENSIONS_INVALID:$path" }
            val entry = zip.getEntry(path) ?: error("CAPTURE_INDEX_FILE_MISSING:$path")
            val actualHash = zip.getInputStream(entry).use(::sha256)
            check(actualHash == row.optString("sha256")) { "CAPTURE_INDEX_HASH_MISMATCH:$path" }
            check(expectedFileHashes[path] == actualHash) { "CAPTURE_INDEX_MANIFEST_HASH_MISMATCH:$path" }
        }
        val checkpointEntry = zip.getEntry("frames/26kf_checkpoint_index.json") ?: return
        val checkpoints = zip.getInputStream(checkpointEntry).use { JSONArray(it.readBytes().toString(Charsets.UTF_8)) }
        for (index in 0 until checkpoints.length()) {
            val row = checkpoints.optJSONObject(index) ?: continue
            if (row.optString("status") != "CAPTURED") continue
            val captureId = row.optString("captureId")
            val capture = captureById[captureId] ?: error("CHECKPOINT_CAPTURE_ROW_MISSING:${row.optString("checkpointId")}")
            val path = row.optString("relativePath")
            check(path == capture.optString("relativePath")) { "CHECKPOINT_INDEX_PATH_MISMATCH:${row.optString("checkpointId")}" }
            check(row.optString("sha256") == capture.optString("sha256")) { "CHECKPOINT_INDEX_HASH_MISMATCH:${row.optString("checkpointId")}" }
            check(zip.getEntry(path) != null) { "CHECKPOINT_FILE_MISSING:${row.optString("checkpointId")}" }
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
