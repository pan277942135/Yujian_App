package com.yujian.ai.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.screens.HomeScreen
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

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
    fun loadedAvatarImageUsesProfileDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "normal-home-avatar-load-fixture.png")
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        source.outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        val avatarUrl = "file://${source.absolutePath}"
        var profileClicks = 0
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record("remote-avatar-success")),
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = true,
                avatarUrl = avatarUrl,
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

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("normal-home-profile-avatar-loaded", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("个人中心").assertHasClickAction().performClick()
        compose.onNodeWithTag("normal-home-default-profile-avatar", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, profileClicks) }
    }

    @Test
    fun failedAvatarLoadFallsBackToV2AndKeepsProfileDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = File(context.cacheDir, "normal-home-avatar-missing.png")
        var profileClicks = 0
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(record("remote-avatar-failure")),
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = true,
                avatarUrl = "file://${missing.absolutePath}",
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

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("normal-home-profile-avatar-fallback", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("normal-home-default-profile-avatar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("个人中心").assertHasClickAction().performClick()
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
    fun guestAvatarOnHomeScreenRoutesToLoginInsteadOfProfile() {
        var loginClicks = 0
        var profileClicks = 0
        // Freeze the Compose test clock so HomeScreen's live camera motion does
        // not keep Espresso waiting for a continuously animated frame.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HomeScreen(
                nickname = "访客",
                statistics = CatchStatistics(totalCatches = 0, speciesCount = 0),
                recentCatches = emptyList(),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                showEmptyState = false,
                onIdentify = {},
                onAlbumClick = {},
                onLoginClick = { loginClicks++ },
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = { profileClicks++ },
                onCatchClick = {},
            )
        }

        val overflowRoot = compose.onAllNodesWithTag(
            "normal-home-safe-overflow-root",
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(
            "The 1080x1920 HomeScreen has no measured overlap or visible truncation and stays NORMAL_FIXED",
            overflowRoot.isEmpty(),
        )
        val normalRoot = compose.onNodeWithTag(
            "normal-home-content-root",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val referenceScale = normalRoot.width / 1080f
        val geometryTolerancePx = maxOf(2f, 0.002f * normalRoot.width)
        val ctaBounds = compose.onNodeWithTag(
            "normal-home-capture-cta",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val cameraBounds = compose.onNodeWithContentDescription("开始识鱼")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(normalRoot.top + 1512f * referenceScale, ctaBounds.top, geometryTolerancePx)
        assertEquals(normalRoot.top + 1588f * referenceScale, cameraBounds.top, geometryTolerancePx)

        compose.onNodeWithContentDescription("登录或注册").assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals(1, loginClicks)
            assertEquals(0, profileClicks)
        }
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

    @Test
    fun short320x480ViewportAtFontScale13ScrollsContentAndPins48dpActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var runtimeDensity = 1f
        compose.setContent {
            val baseDensity = LocalDensity.current
            runtimeDensity = baseDensity.density
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
                NormalHomeContent(
                    statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                    recentCatches = listOf(record("short-screen")),
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
                    safeInsets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp),
                    modifier = Modifier.size(320.dp, 480.dp),
                )
            }
        }
        compose.waitForIdle()

        val root = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        assertEquals(320f, rootBounds.width / runtimeDensity, 1f)
        assertEquals(480f, rootBounds.height / runtimeDensity, 1f)
        compose.onNodeWithTag("normal-home-safe-overflow-root", useUnmergedTree = true).assertIsDisplayed()

        val scroll = compose.onNodeWithTag("normal-home-safe-scroll", useUnmergedTree = true)
        val scrollBounds = scroll.fetchSemanticsNode().boundsInRoot
        assertTrue("The short-screen content viewport must retain at least 160dp", scrollBounds.height / runtimeDensity >= 160f)
        val guestButton = compose.onNodeWithContentDescription("登录或注册")
        val allButton = compose.onNodeWithContentDescription("全部鱼获")
        val cameraButton = compose.onNodeWithContentDescription("开始识鱼")
        val cta = compose.onNodeWithTag("normal-home-capture-cta", useUnmergedTree = true)
        assertHitTargetDp(guestButton.fetchSemanticsNode().boundsInRoot, runtimeDensity, "guest avatar")
        assertHitTargetDp(allButton.fetchSemanticsNode().boundsInRoot, runtimeDensity, "all catches")
        assertHitTargetDp(cameraButton.fetchSemanticsNode().boundsInRoot, runtimeDensity, "camera")
        assertHitTargetDp(cta.fetchSemanticsNode().boundsInRoot, runtimeDensity, "CTA container")
        guestButton.assertHasClickAction()
        allButton.assertHasClickAction()
        cameraButton.assertHasClickAction()

        val safeBottomPx = rootBounds.bottom - 24f * runtimeDensity
        val initialCameraBounds = cameraButton.fetchSemanticsNode().boundsInRoot
        assertTrue("Pinned camera touch target must remain inside bottom safe inset", initialCameraBounds.bottom <= safeBottomPx - 15f * runtimeDensity + 2f)
        assertTrue("Pinned CTA and camera hit areas must not overlap", cta.fetchSemanticsNode().boundsInRoot.bottom <= initialCameraBounds.top)

        val startCapture = root.captureToImage().asAndroidBitmap()
        saveShortViewportCapture(context, "normal_home_safe_overflow_320x480_start.png", startCapture)
        scroll.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val heroBounds = compose.onNodeWithTag("normal-home-catch-card-short-screen", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The full Home Hero must be reachable inside the scrolling viewport", heroBounds.top >= scrollBounds.top - 1f)
        assertTrue("The full Home Hero must be reachable inside the scrolling viewport", heroBounds.bottom <= scrollBounds.bottom + 1f)
        val heroCapture = root.captureToImage().asAndroidBitmap()
        saveShortViewportCapture(context, "normal_home_safe_overflow_320x480_hero_reached.png", heroCapture)
        val cameraAfterScroll = cameraButton.fetchSemanticsNode().boundsInRoot
        assertEquals(initialCameraBounds.top, cameraAfterScroll.top, 1f)
        assertEquals(initialCameraBounds.bottom, cameraAfterScroll.bottom, 1f)
        val manifest = File(context.getExternalFilesDir(null), "normal_home_safe_overflow_320x480.json")
        manifest.parentFile?.mkdirs()
        manifest.writeText(
            "{\"viewport_dp\":[320,480],\"safe_insets_dp\":{\"top\":24,\"right\":0,\"bottom\":24,\"left\":0},\"font_scale\":1.3,\"density\":$runtimeDensity,\"mode\":\"SAFE_OVERFLOW\",\"start_capture_px\":[${startCapture.width},${startCapture.height}],\"hero_capture_px\":[${heroCapture.width},${heroCapture.height}],\"source\":\"API28 Compose viewport fixture\"}\n",
            Charsets.UTF_8,
        )
    }

    @Test
    fun shortViewportRetainsScrollableSafeActionsAtDensity1And3() {
        val densityProfile = mutableStateOf(Density(1f, 1f))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides densityProfile.value) {
                NormalHomeContent(
                    statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                    recentCatches = listOf(record("density-profile")),
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
                    safeInsets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp),
                    modifier = Modifier.size(320.dp, 480.dp),
                )
            }
        }

        for ((profileDensity, profileFontScale) in listOf(1f to 1f, 1f to 1.3f, 3f to 1f, 3f to 1.3f)) {
            compose.runOnIdle { densityProfile.value = Density(profileDensity, profileFontScale) }
            compose.waitForIdle()
            val rootBounds = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val scrollBounds = compose.onNodeWithTag("normal-home-safe-scroll", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val guestBounds = compose.onNodeWithContentDescription("登录或注册").fetchSemanticsNode().boundsInRoot
            val allBounds = compose.onNodeWithContentDescription("全部鱼获").fetchSemanticsNode().boundsInRoot
            val ctaBounds = compose.onNodeWithTag("normal-home-capture-cta", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val cameraBounds = compose.onNodeWithContentDescription("开始识鱼").fetchSemanticsNode().boundsInRoot
            assertTrue("SAFE_OVERFLOW stays enabled at density=$profileDensity fontScale=$profileFontScale",
                compose.onAllNodesWithTag("normal-home-safe-overflow-root", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())
            assertTrue("Scroll viewport retains >=160dp at density=$profileDensity fontScale=$profileFontScale",
                scrollBounds.height / profileDensity >= 160f)
            assertHitTargetDp(guestBounds, profileDensity, "guest avatar")
            assertHitTargetDp(allBounds, profileDensity, "all catches")
            assertHitTargetDp(ctaBounds, profileDensity, "CTA")
            assertHitTargetDp(cameraBounds, profileDensity, "camera")
            val safeBottom = rootBounds.bottom - 24f * profileDensity
            assertTrue("Pinned camera stays in the safe region", cameraBounds.bottom <= safeBottom - 15f * profileDensity + 2f)
            assertTrue("CTA and camera hit areas remain separate", ctaBounds.bottom <= cameraBounds.top)
        }
    }

    @Test
    fun asymmetricInsetsInRtlKeepGuestTouchTargetInsideSafeWidth() {
        var densityScale = 1f
        compose.setContent {
            densityScale = LocalDensity.current.density
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NormalHomeContent(
                    statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                    recentCatches = listOf(record("rtl-safe")),
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
                    safeInsets = SafeDrawingInsetsDp(start = 34.dp, end = 12.dp),
                    modifier = Modifier.size(360.dp, 640.dp),
                )
            }
        }
        compose.waitForIdle()

        val rootBounds = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("normal-home-safe-overflow-root", useUnmergedTree = true).assertIsDisplayed()
        val guestBounds = compose.onNodeWithContentDescription("登录或注册").fetchSemanticsNode().boundsInRoot
        val safeLeft = rootBounds.left + 12f * densityScale
        val safeRight = rootBounds.right - 34f * densityScale
        assertTrue(guestBounds.left >= safeLeft - 1f)
        assertTrue(guestBounds.right <= safeRight + 1f)
        assertHitTargetDp(guestBounds, densityScale, "RTL guest avatar")
        val cameraBounds = compose.onNodeWithContentDescription("开始识鱼").fetchSemanticsNode().boundsInRoot
        val ctaBounds = compose.onNodeWithTag("normal-home-capture-cta", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(cameraBounds.left >= safeLeft - 1f && cameraBounds.right <= safeRight + 1f)
        assertTrue(ctaBounds.left >= safeLeft - 1f && ctaBounds.right <= safeRight + 1f)
    }

    private fun assertHitTargetDp(bounds: androidx.compose.ui.geometry.Rect, density: Float, label: String) {
        assertTrue("$label target width must remain at least 48dp", bounds.width / density >= 48f)
        assertTrue("$label target height must remain at least 48dp", bounds.height / density >= 48f)
    }

    private fun saveShortViewportCapture(context: android.content.Context, name: String, bitmap: android.graphics.Bitmap) {
        val output = File(context.getExternalFilesDir(null), name)
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            assertTrue("Could not write $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
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
