package com.yujian.ai.ui.recognition.diagnostics

import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject

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
            verifyScreenshotIndexes(zip, expectedFileHashes)
            verifyOriginalMaskHash(zip, expectedFileHashes, archivedManifest)
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
