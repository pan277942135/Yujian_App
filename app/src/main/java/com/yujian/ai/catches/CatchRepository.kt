package com.yujian.ai.catches

import android.net.Uri
import com.yujian.ai.BuildConfig
import com.yujian.ai.auth.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

data class UploadedCatchImage(val uploadId: String, val imageUrl: String)

enum class BsideStatus {
    NONE,
    GENERATING,
    READY,
    FAILED;

    companion object {
        fun fromWire(value: String?): BsideStatus = entries.firstOrNull {
            it.name == value?.trim()?.uppercase()
        } ?: NONE
    }
}

data class BsideGeneration(
    val jobId: String?,
    val status: BsideStatus,
    val resultUri: String?,
)

/** App-private original media attached to a FishRecord on this device. */
data class CatchMemoryMedia(
    val id: String,
    val filePath: String,
    val mimeType: String,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class RemoteCatch(
    val id: String,
    val imageUrl: String,
    val speciesId: String,
    val speciesName: String,
    val confidence: Float,
    val modelVersion: String,
    val capturedAt: String,
    val createdAt: String,
    val lengthCm: Float? = null,
    val weightKg: Float? = null,
    val location: String? = null,
    val bsideStatus: BsideStatus = BsideStatus.NONE,
    val bsideUri: String? = null,
    val story: String? = null,
    val clientRecordId: String? = null,
    val memoryMedia: List<CatchMemoryMedia> = emptyList(),
) {
    val confidencePercent: Int get() = (confidence * 100).roundToInt().coerceIn(0, 100)
}

data class SpeciesCatchCount(val speciesId: String, val speciesName: String, val count: Int)

data class CatchStatistics(
    val totalCatches: Int = 0,
    val speciesCount: Int = 0,
    val topSpecies: List<SpeciesCatchCount> = emptyList(),
    val recentSpecies: String? = null,
)

data class CatchSaveDraft(
    val speciesId: String,
    val speciesName: String,
    val confidence: Float,
    val modelVersion: String,
    val detectorResult: JSONObject? = null,
    val classifierResult: JSONObject? = null,
    val metadata: CatchSaveMetadata? = null,
    val clientRecordId: String = UUID.randomUUID().toString(),
    val capturedAt: String? = null,
)

/** Reserved domain object for the future First Journey memory entry. */
data class MemoryEntry(
    val id: String,
    val catchId: String,
    val title: String,
    val assetUrl: String? = null,
    val createdAt: String,
)

/** Maps the authenticated catches API's top-level story field to user content. */
internal fun catchStoryFromWire(value: String?): String? = value
    ?.trim()
    ?.takeIf {
        it.isNotEmpty() &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("undefined", ignoreCase = true)
    }

class CatchRepository(
    private val baseUrl: String = BuildConfig.USER_API_BASE_URL,
) {
    suspend fun uploadImage(token: String, image: File): UploadedCatchImage = withContext(Dispatchers.IO) {
        require(image.exists() && image.length() > 0L) { "待保存的鱼获照片不存在" }
        val boundary = "YuJianCatchBoundary${System.currentTimeMillis()}"
        val response = multipart("/api/v1/catches/upload-image", token, boundary) { out ->
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"catch.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            image.inputStream().use { it.copyTo(out) }
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        UploadedCatchImage(
            uploadId = response.optString("image_upload_id").takeIf(String::isNotBlank)
                ?: throw IOException("图片上传响应缺少 image_upload_id"),
            imageUrl = response.optString("image_url"),
        )
    }

    suspend fun saveCatch(token: String, upload: UploadedCatchImage, draft: CatchSaveDraft): RemoteCatch = withContext(Dispatchers.IO) {
        val classifier = draft.classifierResult
        val metadata = draft.metadata ?: classifier?.toCatchSaveMetadata() ?: CatchSaveMetadata(null, null, null, null)
        require(draft.clientRecordId.isNotBlank() && draft.clientRecordId.length <= 128) { "鱼获记录标识无效" }
        val body = JSONObject()
            .put("image_upload_id", upload.uploadId)
            .put("species_id", draft.speciesId)
            .put("species_name", draft.speciesName)
            .put("confidence", draft.confidence.toDouble())
            .put("model_version", draft.modelVersion)
            .put("detector_result", draft.detectorResult ?: JSONObject.NULL)
            .put("classifier_result", draft.classifierResult ?: JSONObject.NULL)
            .put("length_cm", metadata.lengthCm ?: JSONObject.NULL)
            .put("weight_kg", metadata.weightKg ?: JSONObject.NULL)
            .put("location", metadata.location ?: JSONObject.NULL)
            .put("story", metadata.story ?: JSONObject.NULL)
            .put("captured_at", draft.capturedAt ?: JSONObject.NULL)
            .put("client_record_id", draft.clientRecordId)
        val response = json("POST", "/api/v1/catches", token, body)
        if (!response.optBoolean("saved", false)) throw IOException("服务端未确认鱼获保存成功")
        val catchId = response.optString("catch_id").takeIf(String::isNotBlank)
            ?: throw IOException("保存响应缺少鱼获标识")
        val returned = parseCatch(response.optJSONObject("catch") ?: throw IOException("保存响应缺少鱼获记录"))
        if (returned.id != catchId) throw IOException("保存响应中的鱼获标识不一致")
        validateSavedCatch(returned, draft, metadata)
        val reread = getCatch(token, catchId)
        validateSavedCatch(reread, draft, metadata)
        if (reread.imageUrl != returned.imageUrl) throw IOException("重新读取后鱼获照片关联发生变化")
        reread
    }

    suspend fun listCatches(token: String, limit: Int = 100): List<RemoteCatch> = withContext(Dispatchers.IO) {
        val response = jsonArray("/api/v1/catches?limit=${limit.coerceIn(1, 100)}", token)
        (0 until response.length()).map { parseCatch(response.getJSONObject(it)) }
    }

    suspend fun getCatch(token: String, catchId: String): RemoteCatch = withContext(Dispatchers.IO) {
        parseCatch(json("GET", "/api/v1/catches/${Uri.encode(catchId)}", token, null))
    }

    suspend fun requireMetadataMigrationSupport(token: String) = withContext(Dispatchers.IO) {
        val result = json("GET", "/api/v1/catches/capabilities", token, null)
        if (result.optInt("metadata_version") < 1 ||
            !result.optBoolean("idempotency_keys") ||
            !result.optBoolean("lookup_by_client_record_id")
        ) throw IOException("当前鱼获服务不支持安全迁移，请稍后重试")
    }

    suspend fun findByClientRecordId(token: String, clientRecordId: String): RemoteCatch? = withContext(Dispatchers.IO) {
        try {
            parseCatch(json("GET", "/api/v1/catches/by-client-record/${Uri.encode(clientRecordId)}", token, null))
        } catch (error: ApiException) {
            if (error.statusCode == 404) null else throw error
        }
    }

    suspend fun statistics(token: String): CatchStatistics = withContext(Dispatchers.IO) {
        val response = json("GET", "/api/v1/catches/statistics", token, null)
        val top = response.optJSONArray("top_species") ?: JSONArray()
        CatchStatistics(
            totalCatches = response.optInt("total_catches"),
            speciesCount = response.optInt("species_count"),
            topSpecies = (0 until top.length()).map { index ->
                val item = top.getJSONObject(index)
                SpeciesCatchCount(item.optString("species_id"), item.optString("species"), item.optInt("count"))
            },
            recentSpecies = response.optString("recent_species").takeIf(String::isNotBlank),
        )
    }

    suspend fun createBsideJob(token: String, catchId: String): BsideGeneration = withContext(Dispatchers.IO) {
        parseBsideGeneration(json("POST", "/api/v1/catches/$catchId/bside", token, null))
    }

    suspend fun bsideStatus(token: String, catchId: String): BsideGeneration = withContext(Dispatchers.IO) {
        parseBsideGeneration(json("GET", "/api/v1/catches/$catchId/bside-status", token, null))
    }

    fun resolveUrl(path: String?): String? {
        val value = path?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        val root = baseUrl.trimEnd('/')
        return if (root.isBlank()) null else if (value.startsWith('/')) "$root$value" else "$root/$value"
    }

    private fun parseCatch(item: JSONObject): RemoteCatch = RemoteCatch(
        id = item.optString("id"),
        imageUrl = item.optString("image_url"),
        speciesId = item.optString("species_id"),
        speciesName = item.optString("species_name"),
        confidence = item.optDouble("confidence").toFloat(),
        modelVersion = item.optString("model_version"),
        capturedAt = item.optString("captured_at"),
        createdAt = item.optString("created_at"),
        lengthCm = item.optionalFloat("length_cm", "length"),
        weightKg = item.optionalFloat("weight_kg", "weight"),
        location = catchStoryFromWire(item.optString("location"))
            ?: catchStoryFromWire(item.optString("location_name")),
        bsideStatus = BsideStatus.fromWire(item.optString("bside_status")),
        bsideUri = item.optString("bside_uri").takeIf(String::isNotBlank),
        story = catchStoryFromWire(item.optString("story")),
        clientRecordId = item.optString("client_record_id").takeIf(String::isNotBlank),
    )

    private fun parseBsideGeneration(item: JSONObject): BsideGeneration = BsideGeneration(
        jobId = item.optString("job_id").takeIf(String::isNotBlank),
        status = BsideStatus.fromWire(item.optString("status")),
        resultUri = item.optString("result_uri").takeIf(String::isNotBlank),
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

    private fun validateSavedCatch(record: RemoteCatch, draft: CatchSaveDraft, metadata: CatchSaveMetadata) {
        val imageBelongsToRecord = record.imageUrl.endsWith("/api/v1/catches/${record.id}/media")
        if (record.id.isBlank() || !imageBelongsToRecord || record.speciesId != draft.speciesId ||
            record.speciesName != draft.speciesName || record.clientRecordId != draft.clientRecordId
        ) throw IOException("保存响应中的鱼种、照片或迁移映射不完整")
        if (!sameMeasurement(record.lengthCm, metadata.lengthCm) ||
            !sameMeasurement(record.weightKg, metadata.weightKg) ||
            record.location != metadata.location || record.story != metadata.story
        ) throw IOException("服务端保存的鱼获元数据与输入不一致")
    }

    private fun sameMeasurement(actual: Float?, expected: Double?): Boolean = when {
        actual == null -> expected == null
        expected == null -> false
        else -> abs(actual.toDouble() - expected) <= 0.0001
    }

    private fun JSONObject.toCatchSaveMetadata(): CatchSaveMetadata {
        fun number(vararg keys: String): Double? {
            keys.forEach { key ->
                if (!has(key)) return@forEach
                if (isNull(key)) return null
                val value = when (val raw = opt(key)) {
                    is Number -> raw.toDouble()
                    is String -> raw.trim().replace(',', '.').toDoubleOrNull()
                    else -> null
                } ?: throw IllegalArgumentException("鱼获测量数据格式不正确")
                require(value.isFinite() && value > 0.0 && value <= 1000.0) { "鱼获测量数据超出有效范围" }
                return value
            }
            return null
        }
        return CatchSaveMetadata(
            lengthCm = number("length_cm", "length"),
            weightKg = number("weight_kg", "weight"),
            location = optString("location").takeIf(String::isNotBlank),
            story = optString("story").takeIf(String::isNotBlank),
        )
    }

    private fun json(method: String, path: String, token: String, body: JSONObject?): JSONObject {
        val connection = connection(method, path, token, "application/json; charset=utf-8")
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            JSONObject(readResponse(connection))
        } finally { connection.disconnect() }
    }

    private fun jsonArray(path: String, token: String): JSONArray {
        val connection = connection("GET", path, token, null)
        return try { JSONArray(readResponse(connection)) } finally { connection.disconnect() }
    }

    private fun multipart(path: String, token: String, boundary: String, write: (DataOutputStream) -> Unit): JSONObject {
        val connection = connection("POST", path, token, "multipart/form-data; boundary=$boundary")
        return try {
            DataOutputStream(connection.outputStream).use(write)
            JSONObject(readResponse(connection))
        } finally { connection.disconnect() }
    }

    private fun connection(method: String, path: String, token: String, contentType: String?): HttpURLConnection {
        val root = baseUrl.trimEnd('/')
        if (root.isBlank()) throw IOException("用户服务未配置")
        return (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 30_000
            doOutput = method != "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            contentType?.let { setRequestProperty("Content-Type", it) }
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        if (code !in 200..299) throw ApiException(code, readableError(text, "请求失败 ($code)"))
        return text
    }

    private fun readableError(raw: String, fallback: String): String = runCatching {
        JSONObject(raw).optString("detail").ifBlank { fallback }
    }.getOrDefault(fallback)
}
