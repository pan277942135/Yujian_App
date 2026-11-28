package com.yujian.ai.session

import android.content.Context

data class UserSession(
    val accessToken: String,
    val userId: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
)

/** The app can be used before authentication; this is the resolved state. */
data class UserSessionState(
    val isLogin: Boolean,
    val userId: String?,
    val guestId: String,
    val hasSeenIntroVideo: Boolean,
)

/** Persistent, app-private storage for the consumer App Bearer session. */
class UserSessionManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun current(): UserSession? {
        val token = preferences.getString(KEY_TOKEN, null)?.takeIf(String::isNotBlank) ?: return null
        val userId = preferences.getString(KEY_USER_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val username = preferences.getString(KEY_USERNAME, "") ?: ""
        val nickname = preferences.getString(KEY_NICKNAME, "") ?: ""
        val avatarUrl = preferences.getString(KEY_AVATAR_URL, null)
        return UserSession(token, userId, username, nickname, avatarUrl)
    }

    fun save(session: UserSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_NICKNAME, session.nickname)
            .putString(KEY_AVATAR_URL, session.avatarUrl)
            .apply()
    }

    fun guestId(): String {
        val existing = preferences.getString(KEY_GUEST_ID, null)?.takeIf(String::isNotBlank)
        if (existing != null) return existing
        val generated = "guest_${java.util.UUID.randomUUID()}"
        preferences.edit().putString(KEY_GUEST_ID, generated).apply()
        return generated
    }

    fun state(): UserSessionState = UserSessionState(
        isLogin = current() != null,
        userId = current()?.userId,
        guestId = guestId(),
        hasSeenIntroVideo = preferences.getBoolean(KEY_INTRO_VIDEO_SEEN, false),
    )

    fun markIntroVideoSeen() {
        preferences.edit().putBoolean(KEY_INTRO_VIDEO_SEEN, true).apply()
    }

    fun guestRegistrationPromptShown(): Boolean = preferences.getBoolean(KEY_GUEST_PROMPT_SHOWN, false)

    fun markGuestRegistrationPromptShown() {
        preferences.edit().putBoolean(KEY_GUEST_PROMPT_SHOWN, true).apply()
    }

    fun clear() {
        // Keep the guest identity and local guest archive across logout.
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_URL)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "yujian_user_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AVATAR_URL = "avatar_url"
        const val KEY_GUEST_ID = "guest_id"
        const val KEY_GUEST_PROMPT_SHOWN = "guest_registration_prompt_shown"
        const val KEY_INTRO_VIDEO_SEEN = "has_seen_intro_video"
    }
}
