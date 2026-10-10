package com.yujian.ai.ui.home

internal const val NORMAL_HOME_GUEST_AVATAR_ASSET_PATH = "normal_home_runtime_v1/avatar/guest_avatar.png"

/** Normal Home only; shared/profile avatar contracts remain unchanged. */
internal enum class NormalHomeAvatarState {
    GUEST_DEFAULT,
    PROFILE_DEFAULT,
    PROFILE_LOADING,
    PROFILE_IMAGE,
}

/** V2 is the authenticated profile fallback; guests use their own Home asset. */
internal fun normalHomeAvatarState(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    loadSucceeded: Boolean?,
): NormalHomeAvatarState {
    if (!isLoggedIn) return NormalHomeAvatarState.GUEST_DEFAULT
    if (avatarUrl.isNullOrBlank()) return NormalHomeAvatarState.PROFILE_DEFAULT
    return when (loadSucceeded) {
        true -> NormalHomeAvatarState.PROFILE_IMAGE
        false -> NormalHomeAvatarState.PROFILE_DEFAULT
        null -> NormalHomeAvatarState.PROFILE_LOADING
    }
}
