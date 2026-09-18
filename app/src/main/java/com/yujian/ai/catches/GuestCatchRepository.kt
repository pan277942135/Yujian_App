package com.yujian.ai.catches

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * App-private archive used while the user is a guest.
 *
 * The server catch API intentionally remains account-scoped.  A guest still
 * needs to be able to complete the core loop, so we persist the photo and the
 * recognition result locally until the user decides to sign in.
 */
class GuestCatchRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val imageDirectory = File(context.filesDir, "guest_catches").apply { mkdirs() }

    suspend fun listCatches(): List<RemoteCatch> = withContext(Dispatchers.IO) {
        readCatches()
    }

    suspend fun saveCatch(source: File, draft: CatchSaveDraft): RemoteCatch = withContext(Dispatchers.IO) {
        require(source.exists() && source.length() > 0L) { "待保存的鱼获照片不存在" }
        val id = "guest_${UUID.randomUUID()}"
        val extension = source.extension.lowercase(Locale.US).takeIf { it.isNotBlank() } ?: "jpg"
        val destination = File(imageDirectory, "$id.$extension")
        source.copyTo(destination, overwrite = true)

        val timestamp = nowIso()
        val record = RemoteCatch(
            id = id,
            imageUrl = destination.absolutePath,
            speciesId = draft.speciesId,
            speciesName = draft.speciesName,
            confidence = draft.confidence,
            modelVersion = draft.modelVersion,
            capturedAt = timestamp,
            createdAt = timestamp,
        )
        val records = JSONArray(preferences.getString(KEY_RECORDS, "[]") ?: "[]")
        records.put(record.toJson())
        preferences.edit().putString(KEY_RECORDS, records.toString()).apply()
        record
    }

    suspend fun migrateToRemote(token: String, remote: CatchRepository) = withContext(Dispatchers.IO) {
        val records = readCatches()
        records.forEach { record ->
            val image = File(record.imageUrl)
            require(image.exists() && image.length() > 0L) { "游客鱼获照片不存在：${record.id}" }
            val upload = remote.uploadImage(token, image)
            remote.saveCatch(
                token,
                upload,
                CatchSaveDraft(
                    speciesId = record.speciesId,
                    speciesName = record.speciesName,
                    confidence = record.confidence,
                    modelVersion = record.modelVersion,
                ),
            )
        }
        clear()
    }

    fun hasRecords(): Boolean = readCatches().isNotEmpty()

    private fun clear() {
        readCatches().forEach { File(it.imageUrl).delete() }
        preferences.edit().remove(KEY_RECORDS).apply()
    }

    fun statistics(catches: List<RemoteCatch>): CatchStatistics {
        val species = catches.groupingBy { it.speciesId.ifBlank { it.speciesName } }.eachCount()
        val names = catches.associateBy { it.speciesId.ifBlank { it.speciesName } }
        return CatchStatistics(
            totalCatches = catches.size,
            speciesCount = species.size,
            topSpecies = species.entries
                .sortedByDescending { it.value }
                .map { (id, count) -> SpeciesCatchCount(id, names[id]?.speciesName.orEmpty(), count) },
            recentSpecies = catches.lastOrNull()?.speciesName?.takeIf(String::isNotBlank),
        )
    }

    private fun readCatches(): List<RemoteCatch> {
        val records = runCatching { JSONArray(preferences.getString(KEY_RECORDS, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        return (0 until records.length()).mapNotNull { index ->
            runCatching { records.getJSONObject(index).toCatch() }.getOrNull()
        }
    }

    private fun JSONObject.toCatch(): RemoteCatch = RemoteCatch(
        id = optString("id"),
        imageUrl = optString("image_url"),
        speciesId = optString("species_id"),
        speciesName = optString("species_name"),
        confidence = optDouble("confidence").toFloat(),
        modelVersion = optString("model_version"),
        capturedAt = optString("captured_at"),
        createdAt = optString("created_at"),
    )

    private fun RemoteCatch.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("image_url", imageUrl)
        .put("species_id", speciesId)
        .put("species_name", speciesName)
        .put("confidence", confidence.toDouble())
        .put("model_version", modelVersion)
        .put("captured_at", capturedAt)
        .put("created_at", createdAt)

    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private companion object {
        const val PREFERENCES = "yujian_guest_archive"
        const val KEY_RECORDS = "records"
    }
}
