package com.yujian.ai.ui.home

/** Normal Home only; shared/profile avatar contracts remain unchanged. */
internal enum class NormalHomeAvatarState {
    GUEST_DEFAULT,
    PROFILE_DEFAULT,
    PROFILE_LOADING,
    PROFILE_IMAGE,
}

/**
 * A remote avatar is rendered only for an authenticated profile with a URL.
 * While loading it uses the same local V2 image as its placeholder; a failed
 * request falls back to that image without a blank or intermediate icon.
 */
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
