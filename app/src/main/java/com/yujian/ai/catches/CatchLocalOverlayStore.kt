package com.yujian.ai.catches

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists detail edits and original media references against an existing catch ID.
 * The current catches API has no update or supplemental-media endpoint, so these
 * additions are app-private and remain on this device.
 */
class CatchLocalOverlayStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mediaDirectory = File(appContext.filesDir, "fish_record_memory").apply { mkdirs() }

    fun apply(record: RemoteCatch): RemoteCatch {
        val edit = readEdits().optJSONObject(record.id)
        val media = readMedia(record.id)
        return record.copy(
            speciesId = edit?.optString("species_id")?.takeIf(String::isNotBlank) ?: record.speciesId,
            speciesName = edit?.optString("species_name")?.takeIf(String::isNotBlank) ?: record.speciesName,
            capturedAt = edit?.optString("captured_at")?.takeIf(String::isNotBlank) ?: record.capturedAt,
            lengthCm = if (edit != null) edit.optionalFloat("length_cm") else record.lengthCm,
            weightKg = if (edit != null) edit.optionalFloat("weight_kg") else record.weightKg,
            location = if (edit != null) edit.optString("location").takeIf(String::isNotBlank) else record.location,
            story = if (edit != null) edit.optString("story").takeIf(String::isNotBlank) else record.story,
            memoryMedia = media,
        )
    }

    fun apply(records: List<RemoteCatch>): List<RemoteCatch> = records.map { apply(it) }

    fun saveEdit(catchId: String, draft: CatchRecordEditDraft): Boolean {
        require(catchId.isNotBlank())
        val edits = readEdits()
        edits.put(
            catchId,
            JSONObject()
                .put("species_id", draft.speciesId)
                .put("species_name", draft.speciesName)
                .put("captured_at", draft.capturedAt)
                .put("length_cm", draft.lengthCm ?: JSONObject.NULL)
                .put("weight_kg", draft.weightKg ?: JSONObject.NULL)
                .put("location", draft.location)
                .put("story", draft.story),
        )
        return preferences.edit().putString(KEY_EDITS, edits.toString()).commit()
    }

    fun newCaptureFile(catchId: String, mimeType: String): File {
        val safeId = catchId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: if (mimeType.startsWith("video/")) "mp4" else "jpg"
        return File(mediaDirectory, "${safeId}_${UUID.randomUUID()}.$extension")
    }

    fun newImportedFile(catchId: String, mimeType: String?): File = newCaptureFile(
        catchId,
        mimeType?.takeIf(String::isNotBlank) ?: "image/jpeg",
    )

    fun importUri(catchId: String, uri: Uri): CatchMemoryMedia {
        val mimeType = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
        val destination = newImportedFile(catchId, mimeType)
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取所选影像")
        input.use { source -> destination.outputStream().use(source::copyTo) }
        require(destination.length() > 0L) { "所选影像为空" }
        return CatchMemoryMedia(UUID.randomUUID().toString(), destination.absolutePath, mimeType)
            .also { appendMedia(catchId, it) }
    }

    fun attachCapture(catchId: String, file: File, mimeType: String): CatchMemoryMedia {
        require(file.isFile && file.length() > 0L) { "拍摄的影像文件为空" }
        val ownedFile = if (file.canonicalFile.parentFile == mediaDirectory.canonicalFile) {
            file
        } else {
            val destination = newCaptureFile(catchId, mimeType)
            file.copyTo(destination, overwrite = true)
            destination
        }
        return CatchMemoryMedia(UUID.randomUUID().toString(), ownedFile.absolutePath, mimeType)
            .also { appendMedia(catchId, it) }
    }

    private fun appendMedia(catchId: String, media: CatchMemoryMedia) {
        val entries = readMediaJson(catchId)
        entries.put(
            JSONObject()
                .put("id", media.id)
                .put("file_path", media.filePath)
                .put("mime_type", media.mimeType),
        )
        check(preferences.edit().putString(mediaKey(catchId), entries.toString()).commit()) {
            "无法保存鱼获影像关联"
        }
    }

    private fun readEdits(): JSONObject = runCatching {
        JSONObject(preferences.getString(KEY_EDITS, "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    private fun readMedia(catchId: String): List<CatchMemoryMedia> {
        val items = readMediaJson(catchId)
        return (0 until items.length()).mapNotNull { index ->
            runCatching {
                val item = items.getJSONObject(index)
                CatchMemoryMedia(
                    id = item.getString("id"),
                    filePath = item.getString("file_path"),
                    mimeType = item.getString("mime_type"),
                ).takeIf { File(it.filePath).isFile }
            }.getOrNull()
        }
    }

    private fun readMediaJson(catchId: String): JSONArray = runCatching {
        JSONArray(preferences.getString(mediaKey(catchId), "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun JSONObject.optionalFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key, Double.NaN).takeUnless(Double::isNaN)?.toFloat()
    }

    private fun mediaKey(catchId: String) = "$KEY_MEDIA_PREFIX$catchId"

    private companion object {
        const val PREFERENCES = "fish_record_local_overlay_v1"
        const val KEY_EDITS = "record_edits"
        const val KEY_MEDIA_PREFIX = "record_media:"
    }
}

data class CatchRecordEditDraft(
    val speciesId: String,
    val speciesName: String,
    val capturedAt: String,
    val lengthCm: Float?,
    val weightKg: Float?,
    val location: String,
    val story: String,
)
