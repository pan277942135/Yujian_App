package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalHomeAvatarContractTest {
    @Test
    fun guestUsesTheDistinctGuestEntryState() {
        assertEquals(
            NormalHomeAvatarState.GUEST_DEFAULT,
            normalHomeAvatarState(isLoggedIn = false, avatarUrl = null, loadSucceeded = null),
        )
        assertEquals(
            NormalHomeAvatarState.GUEST_DEFAULT,
            normalHomeAvatarState(isLoggedIn = false, avatarUrl = "https://example.test/avatar.png", loadSucceeded = true),
        )
    }

    @Test
    fun loggedInProfileUsesDefaultWhileMissingLoadingOrFailed() {
        assertEquals(
            NormalHomeAvatarState.PROFILE_DEFAULT,
            normalHomeAvatarState(isLoggedIn = true, avatarUrl = null, loadSucceeded = null),
        )
        assertEquals(
            NormalHomeAvatarState.PROFILE_DEFAULT,
            normalHomeAvatarState(isLoggedIn = true, avatarUrl = " ", loadSucceeded = null),
        )
        assertEquals(
            NormalHomeAvatarState.PROFILE_LOADING,
            normalHomeAvatarState(isLoggedIn = true, avatarUrl = "https://example.test/avatar.png", loadSucceeded = null),
        )
        assertEquals(
            NormalHomeAvatarState.PROFILE_DEFAULT,
            normalHomeAvatarState(isLoggedIn = true, avatarUrl = "https://example.test/avatar.png", loadSucceeded = false),
        )
    }

    @Test
    fun loggedInProfileUsesRemoteImageOnlyAfterSuccessfulLoad() {
        assertEquals(
            NormalHomeAvatarState.PROFILE_IMAGE,
            normalHomeAvatarState(isLoggedIn = true, avatarUrl = "https://example.test/avatar.png", loadSucceeded = true),
        )
    }
}
