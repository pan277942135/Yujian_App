package com.yujian.ai.auth

import android.content.Context
import android.net.Uri
import com.yujian.ai.BuildConfig
import com.yujian.ai.session.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ApiException(val statusCode: Int, message: String) : IOException(message)

data class AccountProfile(
    val id: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
)

data class AiModelImprovementSettings(
    val enabled: Boolean,
    val updatedAt: String?,
    val consentVersion: String?,
)

class AuthRepository(
    private val baseUrl: String = BuildConfig.USER_API_BASE_URL,
) {
    suspend fun register(username: String, password: String, nickname: String) = withContext(Dispatchers.IO) {
        request(
            path = "/api/v1/auth/register",
            body = JSONObject().put("username", username).put("password", password).put("nickname", nickname),
        )
    }

    suspend fun login(username: String, password: String): UserSession = withContext(Dispatchers.IO) {
        val response = request(
            path = "/api/v1/auth/login",
            body = JSONObject().put("username", username).put("password", password),
        )
        val user = response.optJSONObject("user") ?: throw IOException("登录响应缺少用户信息")
        UserSession(
            accessToken = response.optString("access_token").takeIf(String::isNotBlank)
                ?: throw IOException("登录响应缺少 access token"),
            userId = user.optString("id").takeIf(String::isNotBlank)
                ?: throw IOException("登录响应缺少用户 ID"),
            username = user.optString("username"),
            nickname = user.optString("nickname"),
            avatarUrl = user.optString("avatar_url").takeIf(String::isNotBlank)
                ?: user.optString("avatarUrl").takeIf(String::isNotBlank),
        )
    }

    suspend fun getProfile(accessToken: String): AccountProfile = withContext(Dispatchers.IO) {
        profileFrom(request(path = "/api/v1/me", method = "GET", accessToken = accessToken))
    }

    suspend fun updateProfile(accessToken: String, nickname: String): AccountProfile = withContext(Dispatchers.IO) {
        profileFrom(request(
            path = "/api/v1/me/profile",
            method = "PATCH",
            accessToken = accessToken,
            body = JSONObject().put("nickname", nickname),
        ))
    }

    suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        request(
            path = "/api/v1/auth/change-password",
            method = "POST",
            accessToken = accessToken,
            body = JSONObject().put("current_password", currentPassword).put("new_password", newPassword),
        )
    }

    suspend fun getPrivacySettings(accessToken: String): AiModelImprovementSettings = withContext(Dispatchers.IO) {
        privacyFrom(request(path = "/api/v1/me/privacy", method = "GET", accessToken = accessToken))
    }

    suspend fun setAiModelImprovementConsent(
        accessToken: String,
        enabled: Boolean,
        source: String,
    ): AiModelImprovementSettings = withContext(Dispatchers.IO) {
        privacyFrom(request(
            path = "/api/v1/me/privacy/ai-model-improvement",
            method = "PUT",
            accessToken = accessToken,
            body = JSONObject()
                .put("enabled", enabled)
                .put("consent_version", "AI_MODEL_IMPROVEMENT_V1")
                .put("source", source),
        ))
    }

    suspend fun updateAvatar(context: Context, accessToken: String, uri: Uri): AccountProfile = withContext(Dispatchers.IO) {
        val root = requireBaseUrl()
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取选择的头像")
        val mime = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val filename = "avatar-${UUID.randomUUID()}.${if (mime == "image/png") "png" else if (mime == "image/webp") "webp" else "jpg"}"
        val boundary = "YuJian-${UUID.randomUUID()}"
        val connection = (URL(root + "/api/v1/me/avatar").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n".toByteArray())
                output.write("Content-Type: $mime\r\n\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw ApiException(code, readableError(text, "头像上传失败 ($code)"))
            profileFrom(JSONObject(text))
        } finally {
            connection.disconnect()
        }
    }

    private fun request(
        path: String,
        method: String = "POST",
        accessToken: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val root = requireBaseUrl()
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = body != null
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw ApiException(code, readableError(text, "请求失败 ($code)"))
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireBaseUrl(): String = baseUrl.trimEnd('/').takeIf(String::isNotBlank)
        ?: throw IOException("用户服务未配置")

    private fun profileFrom(response: JSONObject): AccountProfile = AccountProfile(
        id = response.optString("id").takeIf(String::isNotBlank) ?: throw IOException("用户响应缺少 ID"),
        username = response.optString("username"),
        nickname = response.optString("nickname"),
        avatarUrl = response.optString("avatar_url").takeIf(String::isNotBlank),
    )

    private fun privacyFrom(response: JSONObject): AiModelImprovementSettings {
        val value = response.optJSONObject("ai_model_improvement") ?: throw IOException("隐私响应缺少授权状态")
        return AiModelImprovementSettings(
            enabled = value.optBoolean("enabled", false),
            updatedAt = value.optString("updated_at").takeIf(String::isNotBlank),
            consentVersion = value.optString("consent_version").takeIf(String::isNotBlank),
        )
    }

    private fun readableError(raw: String, fallback: String): String = runCatching {
        JSONObject(raw).optString("detail").ifBlank { fallback }
    }.getOrDefault(fallback)
}
