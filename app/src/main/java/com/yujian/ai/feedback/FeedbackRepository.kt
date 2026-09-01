package com.yujian.ai.feedback

import android.content.Context
import com.yujian.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL


data class FeedbackDraft(
    val sourceEventId: String,
    val feedbackType: String,
    val modelVersion: String,
    val predictedSpecies: String,
    val confidence: Float,
    val correctedSpecies: String?,
    val userNote: String? = null,
    val imageId: String? = null,
)

class FeedbackRepository(private val context: Context) {
    private val queueDir = File(context.filesDir, "feedback_queue").apply { mkdirs() }

    suspend fun submitOrQueue(imageFile: File, draft: FeedbackDraft): Boolean = withContext(Dispatchers.IO) {
        val entry = persistQueueEntry(imageFile, draft)
        if (!isConfigured()) return@withContext false
        if (upload(entry.first, JSONObject(entry.second.readText(Charsets.UTF_8)), smoke = false)) {
            entry.first.delete()
            entry.second.delete()
            true
        } else false
    }

    suspend fun flushQueued(): Int = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext 0
        var sent = 0
        queueDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.lastModified() }?.forEach { meta ->
            val image = File(queueDir, meta.nameWithoutExtension + ".jpg")
            if (image.exists() && upload(image, JSONObject(meta.readText(Charsets.UTF_8)), smoke = false)) {
                image.delete(); meta.delete(); sent++
            }
        }
        sent
    }

    /**
     * UAT-only transport smoke. It exercises the same authenticated multipart client
     * as production feedback, while the backend smoke mode writes + verifies + deletes
     * a temporary GCS object and does not create a FeedbackEvent.
     */
    suspend fun submitUatSmoke(imageFile: File, sourceEventId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        val json = JSONObject()
            .put("source_event_id", sourceEventId)
            .put("feedback_type", "confirmed")
            .put("source", "android_uat_smoke")
            .put("model_version", "android-uat-smoke")
            .put("predicted_species", "草鱼")
            .put("confidence", 0.99)
            .put("corrected_species", JSONObject.NULL)
            .put("user_note", JSONObject.NULL)
        upload(imageFile, json, smoke = true)
    }

    fun isNetworkConfigured(): Boolean = isConfigured()

    private fun isConfigured(): Boolean = BuildConfig.FEEDBACK_BASE_URL.isNotBlank() && BuildConfig.FEEDBACK_INGEST_KEY.isNotBlank()

    private fun persistQueueEntry(source: File, draft: FeedbackDraft): Pair<File, File> {
        val base = draft.sourceEventId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val image = File(queueDir, "$base.jpg")
        if (!image.exists()) source.copyTo(image, overwrite = true)
        val meta = File(queueDir, "$base.json")
        if (!meta.exists()) {
            val json = JSONObject()
                .put("source_event_id", draft.sourceEventId)
                .put("image_id", draft.imageId ?: JSONObject.NULL)
                .put("feedback_type", draft.feedbackType)
                .put("source", "android_app")
                .put("model_version", draft.modelVersion)
                .put("predicted_species", draft.predictedSpecies)
                .put("confidence", draft.confidence.toDouble())
                .put("corrected_species", draft.correctedSpecies ?: JSONObject.NULL)
                .put("ai_prediction", draft.predictedSpecies)
                .put("user_label", draft.correctedSpecies ?: JSONObject.NULL)
                .put("is_error", draft.correctedSpecies != null && draft.correctedSpecies != draft.predictedSpecies)
                .put("hard_case", draft.correctedSpecies != null && draft.correctedSpecies != draft.predictedSpecies)
                .put("user_note", draft.userNote ?: JSONObject.NULL)
            meta.writeText(json.toString(), Charsets.UTF_8)
        }
        return image to meta
    }

    private fun upload(image: File, json: JSONObject, smoke: Boolean): Boolean = runCatching {
        val boundary = "YuJianBoundary${System.currentTimeMillis()}"
        val baseUrl = BuildConfig.FEEDBACK_BASE_URL.trimEnd('/')
        val connection = (URL("$baseUrl/api/feedback/ingest").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("X-YuJian-Ingest-Key", BuildConfig.FEEDBACK_INGEST_KEY)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        DataOutputStream(connection.outputStream).use { out ->
            fun field(name: String, value: String?) {
                if (value == null) return
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("source_event_id", json.getString("source_event_id"))
            field("feedback_type", json.getString("feedback_type"))
            field("source", json.optString("source", "android_app"))
            field("model_version", json.optString("model_version").ifBlank { null })
            field("predicted_species", json.optString("predicted_species").ifBlank { null })
            field("confidence", json.optDouble("confidence").takeIf { !it.isNaN() }?.toString())
            field("corrected_species", if (json.isNull("corrected_species")) null else json.optString("corrected_species"))
            field("user_note", if (json.isNull("user_note")) null else json.optString("user_note"))
            if (smoke) field("smoke", "true")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"feedback.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            image.inputStream().use { it.copyTo(out) }
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val code = connection.responseCode
        if (code in 200..299) connection.inputStream.use { it.readBytes() } else connection.errorStream?.use { it.readBytes() }
        connection.disconnect()
        code in 200..299
    }.getOrDefault(false)
}
