package com.yujian.ai.account

import com.yujian.ai.BuildConfig
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

data class RegisteredUser(
    val userId: String,
    val username: String,
    val nickname: String,
)

data class CatchSubmission(
    val imageUrl: String,
    val speciesId: String,
    val speciesName: String,
    val confidence: Float,
    val modelVersion: String,
    val imageId: String,
    val capturedAt: String? = null,
)

data class RemoteCatch(
    val id: String,
    val imageUrl: String?,
    val speciesId: String,
    val speciesName: String,
    val confidence: Float,
    val modelVersion: String,
    val capturedAt: String?,
    val createdAt: String?,
)

data class CatchStatistics(
    val totalCatches: Int,
    val speciesCount: Int,
    val topSpecies: String?,
    val recentSpecies: String?,
)

class UserRepository(
    private val baseUrl: String = BuildConfig.FISH_KNOWLEDGE_BASE_URL,
) {
    suspend fun register(username: String, password: String, nickname: String): RegisteredUser =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("nickname", nickname.trim())
            val root = JSONObject(request("/api/v1/auth/register", "POST", body.toString(), null))
            RegisteredUser(
                userId = root.optString("user_id"),
                username = root.optString("username"),
                nickname = root.optString("nickname"),
            )
        }

    suspend fun login(username: String, password: String): UserSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
            val root = JSONObject(request("/api/v1/auth/login", "POST", body.toString(), null))
            val user = root.optJSONObject("user") ?: throw IOException("登录响应缺少用户信息")
            UserSession(
                token = root.optString("access_token").ifBlank { throw IOException("登录响应缺少令牌") },
                userId = user.optString("id").ifBlank { throw IOException("登录响应缺少用户ID") },
                username = user.optString("username"),
                nickname = user.optString("nickname"),
            )
        }

    suspend fun uploadCatchImage(file: File, session: UserSession): String =
        withContext(Dispatchers.IO) {
            if (!file.isFile) throw IOException("原始照片文件不存在")
            val extension = file.extension.lowercase()
            val contentType = when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "image/jpeg"
            }
            val boundary = "----YuJian-${UUID.randomUUID()}"
            val connection = open("/api/v1/catches/upload-image", "POST", session.token).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            try {
                DataOutputStream(connection.outputStream).use { output ->
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                    output.writeBytes("Content-Type: $contentType\r\n\r\n")
                    file.inputStream().use { it.copyTo(output) }
                    output.writeBytes("\r\n--$boundary--\r\n")
                }
                val root = JSONObject(readSuccessful(connection))
                root.optString("url").ifBlank {
                    root.optString("image_url").ifBlank { throw IOException("上传响应缺少图片地址") }
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun createCatch(session: UserSession, payload: CatchSubmission): String =
        withContext(Dispatchers.IO) {
            val classifier = JSONObject()
                .put("species_id", payload.speciesId)
                .put("species_name", payload.speciesName)
                .put("confidence", payload.confidence.toDouble())
                .put("model_version", payload.modelVersion)
            val detector = JSONObject()
                .put("source", "android")
                .put("image_id", payload.imageId)
            val body = JSONObject()
                .put("image_url", payload.imageUrl)
                .put("species_id", payload.speciesId)
                .put("species_name", payload.speciesName)
                .put("confidence", payload.confidence.toDouble())
                .put("model_version", payload.modelVersion)
                .put("detector_result", detector)
                .put("classifier_result", classifier)
            payload.capturedAt?.let { body.put("captured_at", it) }
            JSONObject(request("/api/v1/catches", "POST", body.toString(), session.token))
                .optString("catch_id")
                .ifBlank { throw IOException("保存鱼获响应缺少ID") }
        }

    suspend fun listCatches(session: UserSession, limit: Int = 50): List<RemoteCatch> =
        withContext(Dispatchers.IO) {
            val array = JSONArray(request("/api/v1/catches?limit=$limit", "GET", null, session.token))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                RemoteCatch(
                    id = item.optString("catch_id"),
                    imageUrl = item.optString("image_url").ifBlank { null },
                    speciesId = item.optString("species_id"),
                    speciesName = item.optString("species_name"),
                    confidence = item.optDouble("confidence", 0.0).toFloat(),
                    modelVersion = item.optString("model_version"),
                    capturedAt = item.optString("captured_at").ifBlank { null },
                    createdAt = item.optString("created_at").ifBlank { null },
                )
            }
        }

    suspend fun statistics(session: UserSession): CatchStatistics =
        withContext(Dispatchers.IO) {
            val root = JSONObject(request("/api/v1/catches/statistics", "GET", null, session.token))
            val top = root.optJSONArray("top_species")?.optJSONObject(0)
            CatchStatistics(
                totalCatches = root.optInt("total_catches", 0),
                speciesCount = root.optInt("species_count", 0),
                topSpecies = top?.optString("species")?.ifBlank { null },
                recentSpecies = root.optString("recent_species").ifBlank { null },
            )
        }

    fun resolveAssetUrl(asset: String?): String? {
        val value = asset?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        val root = baseUrl.trimEnd('/')
        if (root.isBlank()) return null
        return if (value.startsWith('/')) "$root$value" else "$root/$value"
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    private fun request(path: String, method: String, body: String?, token: String?): String {
        val connection = open(path, method, token)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            return readSuccessful(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String, token: String?): HttpURLConnection {
        val root = baseUrl.trimEnd('/')
        if (root.isBlank()) throw IOException("Fish Knowledge / 用户 API 未配置")
        return (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            token?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private fun readSuccessful(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            .orEmpty()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
            throw IOException(if (detail.isBlank()) "请求失败（HTTP $status）" else detail)
        }
        return body
    }
}
