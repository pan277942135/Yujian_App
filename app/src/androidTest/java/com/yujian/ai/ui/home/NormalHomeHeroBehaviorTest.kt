package com.yujian.ai.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NormalHomeHeroBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSingleCatchIsCenteredAndOpensItsRecordId() {
        val record = record("record-42")
        var openedId: String? = null
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record),
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = { openedId = it },
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        val pager = compose.onNodeWithTag("normal-home-catch-pager")
        val card = compose.onNodeWithTag("normal-home-catch-card-record-42")
        card.assertIsDisplayed()
        assertEquals(
            pager.fetchSemanticsNode().boundsInRoot.center.x,
            card.fetchSemanticsNode().boundsInRoot.center.x,
            1.5f,
        )
        pager.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithTag("normal-home-catch-card-record-42").fetchSemanticsNodes().size)
        assertEquals(
            pager.fetchSemanticsNode().boundsInRoot.center.x,
            card.fetchSemanticsNode().boundsInRoot.center.x,
            1.5f,
        )
        card.performClick()
        compose.runOnIdle { assertEquals("record-42", openedId) }
    }

    @Test
    fun unresolvedArchiveKeepsNormalEnvironmentStructureWithoutInventingCatchContent() {
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(),
                recentCatches = emptyList(),
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                isResolving = true,
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        compose.onNodeWithTag("normal-home-resolving-hero").assertIsDisplayed()
        compose.onNodeWithContentDescription("开始识鱼").assertHasClickAction()
        assertTrue(compose.onAllNodesWithText("最近鱼获").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("记录下一条鱼").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("normal-home-catch-pager").fetchSemanticsNodes().isEmpty())
        assertEquals(3, compose.onAllNodesWithText("—").fetchSemanticsNodes().size)
    }

    @Test
    fun unavailableCatchMediaKeepsItsRecordAndUsesNeutralFallback() {
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record("offline-image")),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        compose.onNodeWithTag("normal-home-catch-card-offline-image").assertIsDisplayed()
        compose.onNodeWithTag(
            "normal-home-catch-media-mode-evidence-fit-bbox-absent-offline-image",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithTag("normal-home-media-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("草鱼").assertIsDisplayed()
    }

    @Test
    fun loggedInDefaultAvatarKeepsProfileButtonSemanticsAndAction() {
        var profileClicks = 0
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record("profile-fallback")),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = true,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = { profileClicks++ },
                onCatchClick = {},
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        val profileButton = compose.onNodeWithContentDescription("个人中心")
        val profileBounds = profileButton.fetchSemanticsNode().boundsInRoot
        assertTrue("Profile hitbox width must remain at least 48dp", profileBounds.width / densityScale >= 48f)
        assertTrue("Profile hitbox height must remain at least 48dp", profileBounds.height / densityScale >= 48f)
        profileButton.assertHasClickAction().performClick()
        compose.onNodeWithTag("normal-home-default-profile-avatar", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, profileClicks) }
    }

    @Test
    fun guestAvatarUsesSeparateArtworkAndOpensLoginRegister() {
        var loginClicks = 0
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record("guest-entry")),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = { loginClicks++ },
                onCatchClick = {},
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        val guestButton = compose.onNodeWithContentDescription("登录或注册")
        val bounds = guestButton.fetchSemanticsNode().boundsInRoot
        assertTrue("Guest entry width must remain at least 48dp", bounds.width / densityScale >= 48f)
        assertTrue("Guest entry height must remain at least 48dp", bounds.height / densityScale >= 48f)
        guestButton.assertHasClickAction().performClick()
        compose.onNodeWithTag("normal-home-guest-avatar", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("normal-home-default-profile-avatar", useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty())
        compose.runOnIdle { assertEquals(1, loginClicks) }
    }

    @Test
    fun multipleCatchesStayManualAndCanBeSwiped() {
        val records = listOf(record("record-1"), record("record-2"), record("record-3"))
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 3, speciesCount = 1),
                recentCatches = records,
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                motionState = HomeMotionState(),
                runtimeAssets = null,
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        compose.mainClock.advanceTimeBy(20_000)
        val pager = compose.onNodeWithTag("normal-home-catch-pager")
        val firstCard = compose.onNodeWithTag("normal-home-catch-card-record-1")
        val pagerCenter = pager.fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(pagerCenter, firstCard.fetchSemanticsNode().boundsInRoot.center.x, 1.5f)

        pager.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val laterCardCenters = listOf("record-2", "record-3").flatMap { recordId ->
            compose.onAllNodesWithTag("normal-home-catch-card-$recordId")
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.center.x }
        }
        assertTrue(
            "A manual swipe should move a later catch into the pager center",
            laterCardCenters.any { kotlin.math.abs(it - pagerCenter) < 4f },
        )
    }

    private fun record(id: String) = RemoteCatch(
        id = id,
        imageUrl = "/$id.jpg",
        speciesId = "grass-carp",
        speciesName = "草鱼",
        confidence = 0.92f,
        modelVersion = "test",
        capturedAt = "2026-09-25T18:20:00+08:00",
        createdAt = "2026-09-25T18:20:00+08:00",
    )
}
