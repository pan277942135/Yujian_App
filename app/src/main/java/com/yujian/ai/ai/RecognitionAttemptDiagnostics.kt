package com.yujian.ai.ai

import android.content.Context
import android.os.SystemClock
import com.yujian.ai.model.SelectedImage
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Durable, bounded, per-attempt technical evidence; failures never block inference. */
class RecognitionAttemptDiagnostics(context: Context) {
    private val root = File(context.filesDir, "recognition_attempt_diagnostics").apply { mkdirs() }
    private val lock = Any()
    private var sequence = 0L
    private var attempt: String? = null
    private var session: String? = null
    private var image: SelectedImage? = null
    private var startedMs = 0L

    fun begin(sessionId: String, attemptId: String, selected: SelectedImage) = synchronized(lock) {
        session = sessionId; attempt = attemptId; image = selected
        sequence = 0; startedMs = SystemClock.uptimeMillis()
        runCatching {
            file(attemptId).writeText("")
            root.listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
        }
        event("PIPELINE_START", "PHOTO", "STARTED", mapOf(
            "width" to selected.bitmap.width, "height" to selected.bitmap.height,
            "bitmap_config" to selected.bitmap.config.toString(), "exif_orientation" to selected.sourceExifOrientation,
            "source_orientation_degrees" to selected.sourceOrientationDegrees,
        ))
    }

    fun event(name: String, stage: String, status: String, details: Map<String, Any?> = emptyMap(), error: Throwable? = null) = synchronized(lock) {
        val id = attempt ?: return@synchronized
        runCatching {
            val now = SystemClock.uptimeMillis()
            val row = JSONObject().put("schema_version", 1).put("event_sequence", ++sequence)
                .put("event_name", name).put("stage", stage).put("status", status)
                .put("wall_time", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())).put("uptime_ms", now)
                .put("duration_ms", now - startedMs)
                .put("recognition_session_id", session).put("recognition_attempt_id", id)
                .put("image_id", image?.imageId).put("photo_sha256", image?.photoSha256)
                .put("detector_model_id", "DET_FISH_v0.3")
                .put("detector_sha256", "98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1")
                .put("classifier_model_id", "MODEL_CROP_M1_v0.2")
                .put("classifier_sha256", "29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18")
                .put("details", JSONObject(details))
            if (error != null) {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                row.put("exception_type", error.javaClass.name).put("exception_message", error.message)
                    .put("stack_trace", trace.take(32_768))
                row.put("root_cause", generateSequence(error) { it.cause }.last().toString())
            }
            file(id).appendText(row.toString() + "\n")
        }
    }

    fun read(attemptId: String?): String = synchronized(lock) {
        if (attemptId == null) return@synchronized "NOT_AVAILABLE: attempt identity"
        runCatching { file(attemptId).takeIf { it.isFile }?.readText() ?: "NOT_AVAILABLE: diagnostic file" }
            .getOrElse { "INCOMPLETE: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun export(attemptId: String?, cacheDir: File): File {
        require(attemptId != null)
        val output = File(cacheDir, "diagnostics/recognition_${attemptId}.zip")
        output.parentFile?.mkdirs()
        val lines = read(attemptId)
        ZipOutputStream(output.outputStream()).use { zip ->
            val contents = mapOf(
                "manifest.json" to JSONObject().put("schema_version", 1).put("attempt_id", attemptId).toString(),
                "pipeline_events.jsonl" to lines,
                "attempt_summary.json" to (lines.lineSequence().lastOrNull { it.isNotBlank() } ?: "NOT_AVAILABLE"),
                "model_identity.json" to JSONObject().put("detector", "DET_FISH_v0.3").put("classifier", "MODEL_CROP_M1_v0.2").toString(),
                "detector_trace.json" to "NOT_AVAILABLE: see pipeline_events.jsonl",
                "crop_trace.json" to "NOT_AVAILABLE: see pipeline_events.jsonl",
                "classifier_trace.json" to "NOT_AVAILABLE: see pipeline_events.jsonl",
                "failure.json" to (lines.lineSequence().lastOrNull { it.contains("\"status\":\"FAIL\"") } ?: "NOT_AVAILABLE"),
                "environment.json" to JSONObject().put("android_api", android.os.Build.VERSION.SDK_INT).toString(),
            )
            contents.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry("recognition_diagnostics/$name"))
                zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return output
    }

    private fun file(id: String) = File(root, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.jsonl")
}
