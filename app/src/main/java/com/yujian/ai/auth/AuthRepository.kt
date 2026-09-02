package com.yujian.ai.auth

import com.yujian.ai.BuildConfig
import com.yujian.ai.session.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val statusCode: Int, message: String) : IOException(message)

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
        )
    }

    private fun request(path: String, body: JSONObject): JSONObject {
        val root = baseUrl.trimEnd('/')
        if (root.isBlank()) throw IOException("用户服务未配置")
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw ApiException(code, readableError(text, "请求失败 ($code)"))
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun readableError(raw: String, fallback: String): String = runCatching {
        JSONObject(raw).optString("detail").ifBlank { fallback }
    }.getOrDefault(fallback)
}
