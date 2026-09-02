package com.yujian.ai.account

import android.content.Context

data class UserSession(
    val token: String,
    val userId: String,
    val username: String,
    val nickname: String,
)

class UserSessionManager(context: Context) {
    private val preferences = context.getSharedPreferences("yujian_user_session", Context.MODE_PRIVATE)

    fun current(): UserSession? {
        val token = preferences.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val userId = preferences.getString(KEY_USER_ID, null)?.trim().orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val nickname = preferences.getString(KEY_NICKNAME, null)?.trim().orEmpty()
        return if (token.isBlank() || userId.isBlank()) {
            null
        } else {
            UserSession(token, userId, username, nickname)
        }
    }

    fun save(session: UserSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_NICKNAME, session.nickname)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_NICKNAME = "nickname"
    }
}
