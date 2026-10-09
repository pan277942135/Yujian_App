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

internal fun guestMigrationDraft(sourceGuestId: String, finalView: RemoteCatch): CatchSaveDraft {
    require(sourceGuestId.isNotBlank()) { "游客鱼获标识缺失" }
    val metadata = CatchSaveMetadata(
        lengthCm = finalView.lengthCm?.toDouble(),
        weightKg = finalView.weightKg?.toDouble(),
        location = finalView.location,
        story = finalView.story,
    )
    return CatchSaveDraft(
        speciesId = finalView.speciesId,
        speciesName = finalView.speciesName,
        confidence = finalView.confidence,
        modelVersion = finalView.modelVersion,
        metadata = metadata,
        clientRecordId = sourceGuestId,
        capturedAt = finalView.capturedAt,
    )
}

internal fun guestMigrationMatches(source: RemoteCatch, remote: RemoteCatch): Boolean =
    remote.id.isNotBlank() &&
        remote.imageUrl.endsWith("/api/v1/catches/${remote.id}/media") &&
        remote.clientRecordId == source.id &&
        remote.speciesId == source.speciesId &&
        remote.speciesName == source.speciesName &&
        sameMeasurement(remote.lengthCm, source.lengthCm) &&
        sameMeasurement(remote.weightKg, source.weightKg) &&
        remote.location == source.location &&
        remote.story == source.story &&
        remote.modelVersion == source.modelVersion &&
        remote.confidence == source.confidence &&
        sameTimestamp(remote.capturedAt, source.capturedAt)

private fun sameMeasurement(remote: Float?, local: Float?): Boolean = when {
    remote == null -> local == null
    local == null -> false
    else -> kotlin.math.abs(remote - local) <= 0.0001f
}

private fun sameTimestamp(remote: String, local: String): Boolean {
    fun millis(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)?.time
    }.getOrNull()
    val remoteMillis = millis(remote)
    val localMillis = millis(local)
    return remoteMillis != null && remoteMillis == localMillis
}

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

        val timestamp = draft.capturedAt ?: nowIso()
        val classifier = draft.classifierResult
        val metadata = draft.metadata
        val record = RemoteCatch(
            id = id,
            imageUrl = destination.absolutePath,
            speciesId = draft.speciesId,
            speciesName = draft.speciesName,
            confidence = draft.confidence,
            modelVersion = draft.modelVersion,
            capturedAt = timestamp,
            createdAt = timestamp,
            lengthCm = metadata?.lengthCm?.toFloat()
                ?: if (metadata == null) classifier?.takeIf { it.has("length_cm") && !it.isNull("length_cm") }?.optDouble("length_cm")?.toFloat() else null,
            weightKg = metadata?.weightKg?.toFloat()
                ?: if (metadata == null) classifier?.takeIf { it.has("weight_kg") && !it.isNull("weight_kg") }?.optDouble("weight_kg")?.toFloat() else null,
            location = if (metadata != null) metadata.location else classifier?.takeIf { it.has("location") && !it.isNull("location") }
                ?.optString("location")?.takeIf(String::isNotBlank),
            story = if (metadata != null) metadata.story else classifier?.takeIf { it.has("story") && !it.isNull("story") }
                ?.optString("story")?.takeIf(String::isNotBlank),
        )
        val records = JSONArray(preferences.getString(KEY_RECORDS, "[]") ?: "[]")
        records.put(record.toJson())
        preferences.edit().putString(KEY_RECORDS, records.toString()).apply()
        record
    }

    suspend fun migrateToRemote(
        token: String,
        remote: CatchRepository,
        overlays: CatchLocalOverlayStore,
    ) = withContext(Dispatchers.IO) {
        remote.requireMetadataMigrationSupport(token)
        val records = readCatches()
        records.forEach { record ->
            val effective = overlays.apply(record)
            val previousCatchId = readMigrationMappings().optString(record.id).takeIf(String::isNotBlank)
            var saved = previousCatchId?.let { id ->
                try {
                    remote.getCatch(token, id)
                } catch (error: com.yujian.ai.auth.ApiException) {
                    if (error.statusCode == 404) null else throw error
                }
            } ?: remote.findByClientRecordId(token, record.id)
            if (saved == null) {
                val image = File(record.imageUrl)
                require(image.exists() && image.length() > 0L) { "游客鱼获照片不存在：${record.id}" }
                val upload = remote.uploadImage(token, image)
                saved = remote.saveCatch(
                    token,
                    upload,
                    guestMigrationDraft(record.id, effective),
                )
            }
            require(guestMigrationMatches(effective, saved)) { "迁移后的鱼获数据、照片或映射不一致" }
            val reread = remote.getCatch(token, saved.id)
            require(guestMigrationMatches(effective, reread)) { "重新读取后的鱼获数据、照片或映射不一致" }
            require(reread.id == saved.id) { "迁移后的鱼获标识不一致" }

            // Store the source-to-server mapping before dropping this local row.
            val mappings = readMigrationMappings().put(record.id, reread.id)
            check(preferences.edit().putString(KEY_MIGRATIONS, mappings.toString()).commit()) {
                "游客鱼获迁移映射保存失败"
            }
            removeMigratedRecord(record)
        }
    }

    fun hasRecords(): Boolean = readCatches().isNotEmpty()

    private fun removeMigratedRecord(record: RemoteCatch) {
        val current = runCatching { JSONArray(preferences.getString(KEY_RECORDS, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        val retained = JSONArray()
        for (index in 0 until current.length()) {
            val item = runCatching { current.getJSONObject(index) }.getOrNull()
            if (item == null || item.optString("id") != record.id) {
                retained.put(item ?: current.opt(index))
            }
        }
        check(preferences.edit().putString(KEY_RECORDS, retained.toString()).commit()) {
            "游客鱼获源记录没有安全清理，请保留并重试"
        }
        File(record.imageUrl).delete()
    }

    private fun readMigrationMappings(): JSONObject = runCatching {
        JSONObject(preferences.getString(KEY_MIGRATIONS, "{}") ?: "{}")
    }.getOrDefault(JSONObject())

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
        lengthCm = optionalFloat("length_cm", "length"),
        weightKg = optionalFloat("weight_kg", "weight"),
        location = catchStoryFromWire(optString("location"))
            ?: catchStoryFromWire(optString("location_name")),
        bsideStatus = BsideStatus.fromWire(optString("bside_status")),
        bsideUri = optString("bside_uri").takeIf(String::isNotBlank),
        story = catchStoryFromWire(optString("story")),
        clientRecordId = optString("client_record_id").takeIf(String::isNotBlank),
    )

    private fun JSONObject.optionalFloat(vararg keys: String): Float? {
        keys.forEach { key ->
            if (has(key) && !isNull(key)) {
                val value = optDouble(key, Double.NaN)
                if (value.isFinite()) return value.toFloat()
            }
        }
        return null
    }

    private fun RemoteCatch.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("image_url", imageUrl)
        .put("species_id", speciesId)
        .put("species_name", speciesName)
        .put("confidence", confidence.toDouble())
        .put("model_version", modelVersion)
        .put("captured_at", capturedAt)
        .put("created_at", createdAt)
        .put("length_cm", lengthCm ?: JSONObject.NULL)
        .put("weight_kg", weightKg ?: JSONObject.NULL)
        .put("location", location ?: JSONObject.NULL)
        .put("story", story ?: JSONObject.NULL)
        .put("client_record_id", clientRecordId ?: JSONObject.NULL)
        .put("bside_status", bsideStatus.name)
        .put("bside_uri", bsideUri ?: JSONObject.NULL)

    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private companion object {
        const val PREFERENCES = "yujian_guest_archive"
        const val KEY_RECORDS = "records"
        const val KEY_MIGRATIONS = "record_migrations"
    }
}
