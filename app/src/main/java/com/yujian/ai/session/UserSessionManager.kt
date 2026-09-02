package com.yujian.ai.session

import android.content.Context

data class UserSession(
    val accessToken: String,
    val userId: String,
    val username: String,
    val nickname: String,
)

/** Persistent, app-private storage for the consumer App Bearer session. */
class UserSessionManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun current(): UserSession? {
        val token = preferences.getString(KEY_TOKEN, null)?.takeIf(String::isNotBlank) ?: return null
        val userId = preferences.getString(KEY_USER_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val username = preferences.getString(KEY_USERNAME, "") ?: ""
        val nickname = preferences.getString(KEY_NICKNAME, "") ?: ""
        return UserSession(token, userId, username, nickname)
    }

    fun save(session: UserSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_NICKNAME, session.nickname)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES = "yujian_user_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_NICKNAME = "nickname"
    }
}
