package com.yujian.ai.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NormalHomeMotionEvidenceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun initialCatchRevealIs160msLinearOpacityAndKeepsLayoutFixed() {
        val page = mutableStateOf(false)
        val motion = HomeMotionState().apply {
            running = true
            reduceMotion = false
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = if (page.value) 1 else 0, speciesCount = if (page.value) 1 else 0),
                recentCatches = if (page.value) listOf(motionRecord("reveal-catch")) else emptyList(),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                isResolving = !page.value,
                motionState = motion,
                runtimeAssets = null,
                modifier = Modifier.fillMaxSize().background(Color.White).testTag("motion-evidence-window"),
            )
        }
        assertTrue(compose.onAllNodesWithTag("normal-home-catch-pager").fetchSemanticsNodes().isEmpty())
        compose.runOnIdle { page.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        val root = compose.onRoot()
        val card = compose.onNodeWithTag("normal-home-catch-card-reveal-catch")
        card.assertIsDisplayed()
        val startBounds = card.fetchSemanticsNode().boundsInRoot
        val startColor = sampleCardBackdrop(root, card)
        compose.mainClock.advanceTimeBy(80)
        val middleBounds = card.fetchSemanticsNode().boundsInRoot
        val middleColor = sampleCardBackdrop(root, card)
        compose.mainClock.advanceTimeBy(80)
        val at160Bounds = card.fetchSemanticsNode().boundsInRoot
        val at160Color = sampleCardBackdrop(root, card)
        compose.mainClock.advanceTimeBy(40)
        val settledBounds = card.fetchSemanticsNode().boundsInRoot
        val settledColor = sampleCardBackdrop(root, card)

        val alpha0 = estimateBlendAlpha(startColor, settledColor, Color.White)
        val alpha80 = estimateBlendAlpha(middleColor, settledColor, Color.White)
        val alpha160 = estimateBlendAlpha(at160Color, settledColor, Color.White)
        val settledPixelDelta = maxChannelDelta(at160Color, settledColor)
        val layoutDelta = maxEdgeDelta(startBounds, middleBounds, at160Bounds, settledBounds)
        assertTrue("The 80ms midpoint should show a partial opacity reveal: $alpha80", alpha80 in 0.25f..0.75f)
        assertTrue("The reveal should reach full opacity at 160ms: $alpha160", alpha160 >= 0.98f)
        assertTrue("The reveal must be settled by 200ms: pixel delta=$settledPixelDelta", settledPixelDelta <= 2)
        assertTrue("Reveal must not move or resize the card: max edge delta=$layoutDelta", layoutDelta <= 2f)
        Log.i(
            "NORMAL_HOME_MOTION_TRACE",
            "event=card_reveal duration_ms=160 easing=linear alpha_0=$alpha0 alpha_80=$alpha80 " +
                "alpha_160=$alpha160 settled_pixel_delta=$settledPixelDelta max_layout_edge_delta_px=$layoutDelta",
        )
    }

    @Test
    fun selectedHeroBreathUsesFrozenKeyframesAndNeighborOffsetIsDpBased() {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val motion = HomeMotionState().apply {
            running = true
            reduceMotion = false
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(motionRecord("breathing-catch")),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                motionState = motion,
                runtimeAssets = null,
                modifier = Modifier.fillMaxSize().background(Color.White),
            )
        }
        compose.mainClock.advanceTimeByFrame()
        val root = compose.onRoot()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val neutralCardWidthPx = 740f * root.width / 1080f
        val card = compose.onNodeWithTag("normal-home-catch-card-breathing-catch")
        card.assertIsDisplayed()
        val atStart = card.fetchSemanticsNode().boundsInRoot
        val renderedStartCenterY = renderedCardCenterY(root.captureToImage(), rootBounds, atStart)
        compose.mainClock.advanceTimeBy(3_000)
        val atPeak = card.fetchSemanticsNode().boundsInRoot
        val renderedPeakCenterY = renderedCardCenterY(root.captureToImage(), rootBounds, atPeak)
        compose.mainClock.advanceTimeBy(3_000)
        val atEnd = card.fetchSemanticsNode().boundsInRoot
        val renderedEndCenterY = renderedCardCenterY(root.captureToImage(), rootBounds, atEnd)

        val scaleAtStart = atStart.width / neutralCardWidthPx
        val scaleAtPeak = atPeak.width / neutralCardWidthPx
        val scaleAtEnd = atEnd.width / neutralCardWidthPx
        val yAtPeakDp = (renderedPeakCenterY - renderedStartCenterY) / density
        val yAtEndDp = (renderedEndCenterY - renderedStartCenterY) / density
        assertTrue("Hero begins at neutral scale: $scaleAtStart", abs(scaleAtStart - 1f) <= 0.002f)
        assertTrue("Selected Hero scale at 3000ms must be 1.008: $scaleAtPeak", abs(scaleAtPeak - 1.008f) <= 0.002f)
        assertTrue("Selected Hero Y at 3000ms must be -2dp: $yAtPeakDp", abs(yAtPeakDp + 2f) <= 0.5f)
        assertTrue("Hero returns to neutral scale at 6000ms: $scaleAtEnd", abs(scaleAtEnd - 1f) <= 0.002f)
        assertTrue("Hero returns to neutral Y at 6000ms: $yAtEndDp", abs(yAtEndDp) <= 0.5f)

        val q0 = normalHomePagerNeighborTranslationYDp(0f)
        val qHalf = normalHomePagerNeighborTranslationYDp(0.5f)
        val q1 = normalHomePagerNeighborTranslationYDp(1f)
        assertEquals(0f, q0, 0.001f)
        assertEquals(2f, qHalf, 0.001f)
        assertEquals(4f, q1, 0.001f)
        Log.i(
            "NORMAL_HOME_MOTION_TRACE",
            "event=hero_idle period_ms=6000 scale_0=$scaleAtStart " +
                "scale_3000=$scaleAtPeak scale_6000=$scaleAtEnd " +
                "y_0_dp=0 y_3000_dp=$yAtPeakDp y_6000_dp=$yAtEndDp " +
                "neighbor_q0_dp=$q0 neighbor_q05_dp=$qHalf neighbor_q1_dp=$q1",
        )
    }

    @Test
    fun guestToProfileSwitchUsesAvatarContractWithoutAnUnspecifiedTween() {
        val loggedIn = mutableStateOf(false)
        val motion = HomeMotionState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NormalHomeContent(
                statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                recentCatches = listOf(motionRecord("avatar-catch")),
                resolveImageUrl = { null },
                accessToken = "",
                isLoggedIn = loggedIn.value,
                avatarUrl = null,
                onIdentify = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
                motionState = motion,
                runtimeAssets = null,
                modifier = Modifier.fillMaxSize().background(Color.White),
            )
        }
        compose.onNodeWithTag("normal-home-guest-avatar", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { loggedIn.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("normal-home-guest-avatar", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("normal-home-default-profile-avatar", useUnmergedTree = true).assertIsDisplayed()

        val loading = normalHomeAvatarState(true, "file:///avatar.png", null)
        val ready = normalHomeAvatarState(true, "file:///avatar.png", true)
        val failed = normalHomeAvatarState(true, "file:///avatar.png", false)
        assertEquals(NormalHomeAvatarState.PROFILE_LOADING, loading)
        assertEquals(NormalHomeAvatarState.PROFILE_IMAGE, ready)
        assertEquals(NormalHomeAvatarState.PROFILE_DEFAULT, failed)
        Log.i(
            "NORMAL_HOME_AVATAR_STATE_TRACE",
            "guest=GUEST_DEFAULT profile_default=PROFILE_DEFAULT loading=$loading ready=$ready failed=$failed " +
                "transition=immediate_replacement animator=not_authorized_by_NH07",
        )
    }

    private fun sampleCardBackdrop(
        root: androidx.compose.ui.test.SemanticsNodeInteraction,
        card: androidx.compose.ui.test.SemanticsNodeInteraction,
    ): Color {
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        // Sample the flat media placeholder, away from its centered error glyph
        // and the footer. This makes the 0/80/160ms blend probe deterministic.
        val pixel = root.captureToImage().toPixelMap()[
            (cardBounds.center.x - rootBounds.left).toInt().coerceIn(0, root.fetchSemanticsNode().size.width - 1),
            (cardBounds.top + cardBounds.height * 0.22f - rootBounds.top).toInt()
                .coerceIn(0, root.fetchSemanticsNode().size.height - 1),
        ]
        return pixel
    }

    private fun renderedCardCenterY(
        image: ImageBitmap,
        rootBounds: androidx.compose.ui.geometry.Rect,
        cardBounds: androidx.compose.ui.geometry.Rect,
    ): Float {
        val pixels = image.toPixelMap()
        val x = (cardBounds.left + cardBounds.width * 0.12f - rootBounds.left)
            .toInt().coerceIn(0, pixels.width - 1)
        val top = (cardBounds.top - rootBounds.top - 40f).toInt().coerceAtLeast(0)
        val bottom = (cardBounds.bottom - rootBounds.top + 40f).toInt().coerceAtMost(pixels.height)
        val occupied = (top until bottom).filter { y ->
            val pixel = pixels[x, y]
            maxOf(1f - pixel.red, 1f - pixel.green, 1f - pixel.blue) > 0.035f
        }
        check(occupied.isNotEmpty()) { "Rendered Hero surface was not found at x=$x in the captured Compose root" }
        return (occupied.first() + occupied.last()) / 2f + rootBounds.top
    }

    private fun estimateBlendAlpha(sample: Color, end: Color, base: Color): Float {
        fun luma(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        val denominator = luma(end) - luma(base)
        if (abs(denominator) < 0.02f) return 0f
        return ((luma(sample) - luma(base)) / denominator).coerceIn(0f, 1f)
    }

    private fun maxChannelDelta(left: Color, right: Color): Int = maxOf(
        (kotlin.math.abs(left.red - right.red) * 255f).toInt(),
        (kotlin.math.abs(left.green - right.green) * 255f).toInt(),
        (kotlin.math.abs(left.blue - right.blue) * 255f).toInt(),
    )

    private fun maxEdgeDelta(vararg rects: androidx.compose.ui.geometry.Rect): Float {
        val baseline = rects.first()
        return rects.drop(1).flatMap { rect ->
            listOf(
                abs(rect.left - baseline.left), abs(rect.top - baseline.top),
                abs(rect.right - baseline.right), abs(rect.bottom - baseline.bottom),
            )
        }.maxOrNull() ?: 0f
    }

    private fun motionRecord(id: String) = RemoteCatch(
        id = id,
        imageUrl = "/$id.jpg",
        speciesId = "grass-carp",
        speciesName = "草鱼",
        confidence = 0.92f,
        modelVersion = "test",
        capturedAt = "2026-10-14T10:00:00+08:00",
        createdAt = "2026-10-14T10:00:00+08:00",
    )
}
