package com.yujian.ai.catches

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
import kotlin.math.roundToInt

data class UploadedCatchImage(val uploadId: String, val imageUrl: String)

data class RemoteCatch(
    val id: String,
    val imageUrl: String,
    val speciesId: String,
    val speciesName: String,
    val confidence: Float,
    val modelVersion: String,
    val capturedAt: String,
    val createdAt: String,
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
)

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
        val body = JSONObject()
            .put("image_upload_id", upload.uploadId)
            .put("species_id", draft.speciesId)
            .put("species_name", draft.speciesName)
            .put("confidence", draft.confidence.toDouble())
            .put("model_version", draft.modelVersion)
            .put("detector_result", draft.detectorResult ?: JSONObject.NULL)
            .put("classifier_result", draft.classifierResult ?: JSONObject.NULL)
        val response = json("POST", "/api/v1/catches", token, body)
        return@withContext parseCatch(response.optJSONObject("catch") ?: throw IOException("保存响应缺少鱼获记录"))
    }

    suspend fun listCatches(token: String): List<RemoteCatch> = withContext(Dispatchers.IO) {
        val response = jsonArray("/api/v1/catches", token)
        (0 until response.length()).map { parseCatch(response.getJSONObject(it)) }
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
    )

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
