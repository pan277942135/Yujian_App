package com.yujian.ai.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.screens.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class NormalHomeDataParityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun differentPortraitRatiosShareCoverViewportAndPreserveHomeDataAndRoutes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val narrowPortrait = createPortraitFixture(context, "normal-home-9x16.png", 360, 640, AndroidColor.rgb(214, 53, 81))
        val broadPortrait = createPortraitFixture(context, "normal-home-4x5.png", 640, 800, AndroidColor.rgb(46, 126, 184))
        val originalHashes = mapOf(narrowPortrait to sha256(narrowPortrait), broadPortrait to sha256(broadPortrait))
        val catches = listOf(
            record(
                id = "catch-bass",
                image = broadPortrait,
                speciesId = "mandarin-fish",
                speciesName = "鳜鱼",
                capturedAt = "2026-10-04T21:48:00+08:00",
                lengthCm = 32f,
                weightKg = 3f,
                location = "浙江省杭州市临安区青山湖国家森林公园东侧码头",
            ),
            record(
                id = "catch-snakehead",
                image = narrowPortrait,
                speciesId = "snakehead",
                speciesName = "黑鱼",
                capturedAt = "2026-10-04T21:50:00+08:00",
                lengthCm = 28f,
                weightKg = 2.6f,
                location = "江苏省苏州市吴中区太湖国家湿地公园东岸东岸东岸国家级风景区游客中心北侧临水平台",
            ),
        )
        var allClicks = 0
        var speciesClicks = 0
        var catchStatClicks = 0
        var openedCatch: String? = null
        var cameraClicks = 0

        try {
            compose.setContent {
                NormalHomeContent(
                    statistics = CatchStatistics(totalCatches = 2, speciesCount = 2),
                    recentCatches = catches,
                    resolveImageUrl = { it },
                    accessToken = "",
                    isLoggedIn = false,
                    avatarUrl = null,
                    onIdentify = { cameraClicks++ },
                    onSpeciesClick = { speciesClicks++ },
                    onCatchesClick = { allClicks++; catchStatClicks++ },
                    onProfileClick = {},
                    onCatchClick = { openedCatch = it },
                    motionState = HomeMotionState(),
                    runtimeAssets = null,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                )
            }

            // Wait for the active card's image only. The adjacent pager page is
            // composed for layout, but its image semantics may not be exposed until
            // that page becomes active on this API 28 profile.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("黑鱼 鱼获照片").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("normal-home-stat-species-value", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("2")
            compose.onNodeWithTag("normal-home-stat-catches-value", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("2")
            compose.onNodeWithTag("normal-home-stat-record-days-value", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("1")
            compose.onNodeWithTag("normal-home-stat-species-label", useUnmergedTree = true).assertTextEquals("鱼种")
            compose.onNodeWithTag("normal-home-stat-catches-label", useUnmergedTree = true).assertTextEquals("鱼获")
            compose.onNodeWithTag("normal-home-stat-record-days-label", useUnmergedTree = true).assertTextEquals("记录天数")

            val labels = listOf("species", "catches", "record-days").map { tag ->
                compose.onNodeWithTag("normal-home-stat-$tag-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            val values = listOf("species", "catches", "record-days").map { tag ->
                compose.onNodeWithTag("normal-home-stat-$tag-value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            assertEquals(labels[0].bottom, labels[1].bottom, 1.5f)
            assertEquals(labels[1].bottom, labels[2].bottom, 1.5f)
            assertEquals(values[0].top, values[1].top, 1.5f)
            assertEquals(values[1].top, values[2].top, 1.5f)
            assertEquals(
                labels[1].center.x - labels[0].center.x,
                labels[2].center.x - labels[1].center.x,
                2f,
            )
            val pagerBounds = compose.onNodeWithTag("normal-home-catch-pager").fetchSemanticsNode().boundsInRoot
            assertEquals(280f / 1080f, labels[0].center.x / pagerBounds.width, 0.01f)
            assertEquals(0.5f, labels[1].center.x / pagerBounds.width, 0.01f)
            assertEquals(800f / 1080f, labels[2].center.x / pagerBounds.width, 0.01f)
            val dividerBounds = listOf(1, 2).map { index ->
                compose.onNodeWithTag("normal-home-stat-divider-$index").fetchSemanticsNode().boundsInRoot
            }
            dividerBounds.forEach { bounds ->
                val referenceHeight = bounds.height / pagerBounds.width * 1080f
                assertTrue("Stats separator height must follow the width-scaled authority", referenceHeight in 52f..60f)
            }

            compose.onNodeWithText("最近鱼获").assertIsDisplayed()
            compose.onNodeWithText("全部", useUnmergedTree = true).assertIsDisplayed()
            val allAction = compose.onNodeWithContentDescription("全部鱼获")
            allAction.assertHasClickAction().assertIsDisplayed()
            val header = compose.onNodeWithTag("normal-home-recent-header").fetchSemanticsNode().boundsInRoot
            val allBounds = allAction.fetchSemanticsNode().boundsInRoot
            assertTrue("全部 must remain inside the header safe area", allBounds.right <= header.right + 1f)
            val density = context.resources.displayMetrics.density
            assertTrue("全部 must retain a 48dp tap target", allBounds.height >= 48f * density - 1f)
            assertEquals(
                compose.onNodeWithTag("normal-home-recent-title").fetchSemanticsNode().boundsInRoot.center.y,
                allBounds.center.y,
                2f,
            )

            compose.onNodeWithTag("normal-home-brand-title", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("渔见", useUnmergedTree = true).assertIsDisplayed()
            assertBrandArtworkAnchor()
            listOf("species", "catches", "record-days").forEach { stat ->
                logTextLayoutMetric("normal-home-stat-$stat-value", "normal-home-stat-$stat", referenceFontPx = 36f)
                logTextLayoutMetric("normal-home-stat-$stat-label", "normal-home-stat-$stat", referenceFontPx = 28f)
            }
            logTextLayoutMetric("normal-home-recent-title", "normal-home-recent-header", referenceFontPx = 48f)
            logTextLayoutMetric("normal-home-recent-all-label", "normal-home-recent-header", referenceFontPx = 40f)
            logTextLayoutMetric("normal-home-capture-cta-text", "normal-home-capture-cta-text-region", referenceFontPx = 40f)
            logHierarchyEvidence()

            val firstCard = compose.onNodeWithTag("normal-home-catch-card-catch-snakehead")
            val secondCard = compose.onNodeWithTag("normal-home-catch-card-catch-bass")
            val pager = compose.onNodeWithTag("normal-home-catch-pager")
            val shortScreenScroll = compose.onAllNodesWithTag("normal-home-short-screen-scroll")
            val isShortScreen = shortScreenScroll.fetchSemanticsNodes().isNotEmpty()
            if (isShortScreen) {
                compose.onNodeWithTag("normal-home-short-screen-scroll").performTouchInput {
                    val gutterX = width * 0.04f
                    swipe(
                        start = Offset(gutterX, height * 0.92f),
                        end = Offset(gutterX, height * 0.08f),
                        durationMillis = 500,
                    )
                }
                compose.waitForIdle()
            }
            firstCard.assertIsDisplayed()
            secondCard.assertIsDisplayed()
            val firstCardBounds = firstCard.fetchSemanticsNode().boundsInRoot
            val firstCardLayout = firstCard.fetchSemanticsNode().layoutInfo
            val firstCardLayoutHeight = firstCardLayout.height.toFloat()
            val firstMediaNode = compose.onNodeWithTag("normal-home-catch-media-catch-snakehead", useUnmergedTree = true)
                .fetchSemanticsNode()
            val firstMediaBounds = firstMediaNode.boundsInRoot
            assertEquals(pagerBounds.center.x, firstCardBounds.center.x, 1.5f)
            assertEquals(740f / 1080f, firstCardBounds.width / pagerBounds.width, 0.015f)
            assertEquals(880f / 1080f, firstCardLayoutHeight / pagerBounds.width, 0.02f)
            assertEquals("The Hero is fully visible after scrolling the constrained page", firstCardLayoutHeight, firstCardBounds.height, 1.5f)
            assertTrue("The next card remains visible as an adjacent-page peek", secondCard.fetchSemanticsNode().boundsInRoot.left < pagerBounds.right)
            assertEquals("HOME media and outer card share one horizontal boundary", firstCardBounds.width, firstMediaBounds.width, 1.5f)
            assertEquals("HOME media and outer card share one vertical boundary", firstCardBounds.height, firstMediaBounds.height, 1.5f)
            val footerBounds = compose.onNodeWithTag("normal-home-catch-footer-catch-snakehead", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(56f / 740f, (footerBounds.left - firstCardBounds.left) / firstCardBounds.width, 0.025f)
            assertEquals(32f / 740f, (firstCardBounds.bottom - footerBounds.bottom) / firstCardBounds.width, 0.025f)

            compose.onNodeWithTag("normal-home-catch-species-catch-snakehead", useUnmergedTree = true).assertTextEquals("黑鱼")
            compose.onNodeWithTag("normal-home-catch-measurement-catch-snakehead", useUnmergedTree = true).assertTextEquals("28 cm · 2.6 kg")
            assertTrue(
                compose.onNodeWithTag("normal-home-catch-species-catch-snakehead", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right <= firstCardBounds.right + 1f,
            )
            assertTrue(
                compose.onNodeWithTag("normal-home-catch-measurement-catch-snakehead", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right <= firstCardBounds.right + 1f,
            )
            compose.onNodeWithTag("normal-home-catch-meta-catch-snakehead", useUnmergedTree = true).assertIsDisplayed()
            val longMeta = compose.onNodeWithTag("normal-home-catch-meta-catch-snakehead", useUnmergedTree = true).fetchSemanticsNode()
            val metaText = longMeta.config[SemanticsProperties.Text].joinToString("") { it.text }
            assertTrue("The timestamp and separator stay before the long location", metaText.contains("21:50 · "))
            assertTrue(metaText.contains("江苏省苏州市吴中区太湖国家湿地公园东岸东岸东岸国家级风景区游客中心北侧临水平台"))
            assertTrue("Long location stays on one constrained line", longMeta.boundsInRoot.height < firstCardBounds.height / 8f)
            assertTrue("Metadata stays inside the card", longMeta.boundsInRoot.right <= firstCardBounds.right + 1f)
            logTextLayoutMetric("normal-home-catch-species-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 56f)
            logTextLayoutMetric("normal-home-catch-measurement-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 46f)
            logTextLayoutMetric("normal-home-catch-meta-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 36f)
            val layoutResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val layoutAction = longMeta.config[SemanticsActions.GetTextLayoutResult].action
            assertTrue("Metadata exposes its rendered text layout", layoutAction != null)
            assertTrue(layoutAction!!.invoke(layoutResults))
            assertEquals(1, layoutResults.single().lineCount)
            assertTrue("The location line is visually ellipsized to its remaining width", layoutResults.single().hasVisualOverflow)
            assertTrue(
                "The time and separator remain before the visible ellipsis",
                layoutResults.single().getLineEnd(0, visibleEnd = true) > metaText.indexOf("21:50 · ") + 7,
            )

            assertEvidenceFit(firstCard, expectedMarkerCenter = 0.20f, cardName = "catch-snakehead")
            firstCard.performClick()
            compose.runOnIdle { assertEquals("catch-snakehead", openedCatch) }

            pager.performTouchInput { swipeLeft() }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("鳜鱼 鱼获照片").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            val secondCardBounds = secondCard.fetchSemanticsNode().boundsInRoot
            val secondMediaBounds = compose.onNodeWithTag("normal-home-catch-media-catch-bass", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(pagerBounds.center.x, secondCardBounds.center.x, 1.5f)
            assertEquals(firstCardBounds.width, secondCardBounds.width, 1.5f)
            assertEquals(firstCardBounds.height, secondCardBounds.height, 1.5f)
            assertEquals(firstMediaBounds.width, secondMediaBounds.width, 1.5f)
            assertEquals(firstMediaBounds.height, secondMediaBounds.height, 1.5f)
            compose.onNodeWithTag("normal-home-catch-species-catch-bass", useUnmergedTree = true).assertTextEquals("鳜鱼")
            compose.onNodeWithTag("normal-home-catch-measurement-catch-bass", useUnmergedTree = true).assertTextEquals("32 cm · 3 kg")
            val secondMeta = compose.onNodeWithTag("normal-home-catch-meta-catch-bass", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            val secondMetaText = secondMeta.config[SemanticsProperties.Text].joinToString("") { it.text }
            assertTrue(secondMetaText.contains("21:48 · "))
            assertTrue(secondMetaText.contains("浙江省杭州市临安区青山湖国家森林公园东侧码头"))
            assertEvidenceFit(secondCard, expectedMarkerCenter = 0.10f, cardName = "catch-bass")

            if (isShortScreen) {
                compose.onNodeWithTag("normal-home-short-screen-scroll").performTouchInput {
                    val gutterX = width * 0.04f
                    swipe(
                        start = Offset(gutterX, height * 0.08f),
                        end = Offset(gutterX, height * 0.92f),
                        durationMillis = 500,
                    )
                }
                compose.waitForIdle()
            }
            allAction.performClick()
            compose.runOnIdle { assertEquals(1, allClicks) }
            compose.onNodeWithTag("normal-home-stat-species").performClick()
            compose.onNodeWithTag("normal-home-stat-catches").performClick()
            compose.runOnIdle {
                assertEquals(1, speciesClicks)
                assertEquals(2, catchStatClicks)
            }
            compose.onNodeWithContentDescription("开始识鱼").assertHasClickAction().performClick()
            compose.runOnIdle { assertEquals(1, cameraClicks) }

            assertEquals(originalHashes[narrowPortrait], sha256(narrowPortrait))
            assertEquals(originalHashes[broadPortrait], sha256(broadPortrait))
        } finally {
            narrowPortrait.delete()
            broadPortrait.delete()
        }
    }

    @Test
    fun registeredCameraArtworkResourceMatchesApprovedSourceIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.resources.openRawResource(com.yujian.ai.R.drawable.normal_home_camera_primary_v1).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2", digest)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(208, bitmap.width)
        assertEquals(208, bitmap.height)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(104, 104)))
        bitmap.recycle()
    }

    @Test
    fun registeredBrandArtworkResourceMatchesFrozenRasterIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.resources.openRawResource(com.yujian.ai.R.drawable.brand_title_primary).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc", digest)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(155, bitmap.width)
        assertEquals(80, bitmap.height)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
        assertTrue((0 until bitmap.height).any { y ->
            (0 until bitmap.width).any { x -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 }
        })
    }

    private fun assertBrandArtworkAnchor() {
        val root = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("normal-home-header", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val pixels = root.captureToImage().toPixelMap()
        val scale = header.width / 904f
        val left = (header.left - rootBounds.left + 5f * scale).toInt().coerceAtLeast(0)
        val top = (header.top - rootBounds.top + 5f * scale).toInt().coerceAtLeast(0)
        val right = (header.left - rootBounds.left + 170f * scale).toInt().coerceAtMost(pixels.width)
        val bottom = (header.top - rootBounds.top + 95f * scale).toInt().coerceAtMost(pixels.height)
        var minX = pixels.width
        var minY = pixels.height
        var maxX = -1
        var maxY = -1
        for (y in top until bottom) for (x in left until right) {
            val pixel = pixels[x, y]
            if (pixel.red < 0.38f && pixel.green < 0.48f && pixel.blue < 0.58f) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        assertTrue("Frozen brand artwork produced no measurable ink", maxX >= minX && maxY >= minY)
        val tolerance = maxOf(3f, 0.003f * (header.width * 1080f / 904f))
        val expected = listOf(15f, 21f, 154f, 84f).map { it * scale }
        val actual = listOf(
            minX + rootBounds.left - header.left,
            minY + rootBounds.top - header.top,
            maxX + 1f + rootBounds.left - header.left,
            maxY + 1f + rootBounds.top - header.top,
        )
        actual.zip(expected).forEachIndexed { index, (measured, target) ->
            assertTrue("Brand artwork anchor[$index] measured=$measured target=$target tolerance=$tolerance", kotlin.math.abs(measured - target) <= tolerance)
        }
    }

    @Test
    fun realSamplePhotoSharesFullHeroBoundsAndClipsAtAllFourRoundedCorners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "normal-home-real-photo.jpg")
        val backdrop = AndroidColor.rgb(11, 23, 31)
        context.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
            photo.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color(backdrop))) {
                    NormalHomeContent(
                        statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                        recentCatches = listOf(
                            record(
                                id = "real-photo-catch",
                                image = photo,
                                speciesId = "grass-carp",
                                speciesName = "草鱼",
                                capturedAt = "2026-10-04T18:20:00+08:00",
                                lengthCm = 42.6f,
                                weightKg = 1.28f,
                                location = "浙江省杭州市淳安县千岛湖",
                            ),
                        ),
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("草鱼 鱼获照片").fetchSemanticsNodes().isNotEmpty()
            }
            val shortScreenScroll = compose.onAllNodesWithTag("normal-home-short-screen-scroll")
            if (shortScreenScroll.fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("normal-home-short-screen-scroll").performTouchInput {
                    val gutterX = width * 0.04f
                    swipe(
                        start = Offset(gutterX, height * 0.92f),
                        end = Offset(gutterX, height * 0.08f),
                        durationMillis = 500,
                    )
                }
                compose.waitForIdle()
            }
            val card = compose.onNodeWithTag("normal-home-catch-card-real-photo-catch")
            val media = compose.onNodeWithTag("normal-home-catch-media-real-photo-catch", useUnmergedTree = true)
            val cardNode = card.fetchSemanticsNode()
            val cardBounds = cardNode.boundsInRoot
            val mediaBounds = media.fetchSemanticsNode().boundsInRoot
            assertEquals("The Hero is fully visible before sampling all four photo corners", cardNode.layoutInfo.height.toFloat(), cardBounds.height, 1.5f)
            assertEquals(cardBounds.left, mediaBounds.left, 1f)
            assertEquals(cardBounds.top, mediaBounds.top, 1f)
            assertEquals(cardBounds.width, mediaBounds.width, 1f)
            assertEquals(cardBounds.height, mediaBounds.height, 1f)
            assertEquals(1, compose.onAllNodesWithTag("normal-home-catch-card-real-photo-catch").fetchSemanticsNodes().size)
            println(
                "NORMAL_HOME_HERO_MEDIA_BOUNDS " +
                    "card=${cardBounds.left},${cardBounds.top},${cardBounds.right},${cardBounds.bottom} " +
                    "media=${mediaBounds.left},${mediaBounds.top},${mediaBounds.right},${mediaBounds.bottom} " +
                    "expectedFrozenCornerRadiusPx=32"
            )

            val root = compose.onNodeWithTag("normal-home-content-root").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onNodeWithTag("normal-home-content-root").captureToImage().toPixelMap()
            val cardLeft = cardBounds.left - root.left
            val cardTop = cardBounds.top - root.top
            val photoSamples = listOf(
                pixels[(cardLeft + cardBounds.width / 2).toInt(), (cardTop + cardBounds.height / 5).toInt()],
                pixels[(cardLeft + cardBounds.width / 3).toInt(), (cardTop + cardBounds.height / 3).toInt()],
                pixels[(cardLeft + cardBounds.width * 2 / 3).toInt(), (cardTop + cardBounds.height / 2).toInt()],
                pixels[(cardLeft + cardBounds.width / 4).toInt(), (cardTop + cardBounds.height * 3 / 5).toInt()],
            )
            val redSpread = photoSamples.maxOf { it.red } - photoSamples.minOf { it.red }
            val greenSpread = photoSamples.maxOf { it.green } - photoSamples.minOf { it.green }
            assertTrue("A decoded photographic image, not a flat fallback, fills the Hero", redSpread > 0.12f || greenSpread > 0.12f)

            val cornerSamples = listOf(
                pixels[(cardLeft + cardBounds.width * 0.005f).toInt(), (cardTop + cardBounds.height * 0.005f).toInt()],
                pixels[(cardLeft + cardBounds.width * 0.995f).toInt(), (cardTop + cardBounds.height * 0.005f).toInt()],
                pixels[(cardLeft + cardBounds.width * 0.005f).toInt(), (cardTop + cardBounds.height * 0.995f).toInt()],
                pixels[(cardLeft + cardBounds.width * 0.995f).toInt(), (cardTop + cardBounds.height * 0.995f).toInt()],
            )
            cornerSamples.forEachIndexed { index, color ->
                assertTrue(
                    "The real photo and card share the rounded clip at corner $index (root=${root.width}x${root.height})",
                    kotlin.math.abs(color.red * 255f - AndroidColor.red(backdrop)) < 4f &&
                        kotlin.math.abs(color.green * 255f - AndroidColor.green(backdrop)) < 4f &&
                        kotlin.math.abs(color.blue * 255f - AndroidColor.blue(backdrop)) < 4f,
                )
            }
        } finally {
            photo.delete()
        }
    }

    private fun assertEvidenceFit(
        card: androidx.compose.ui.test.SemanticsNodeInteraction,
        expectedMarkerCenter: Float,
        cardName: String,
    ) {
        val pixels = card.captureToImage().toPixelMap()
        val y = (pixels.height * 0.28f).toInt().coerceIn(0, pixels.height - 1)
        val markerSamples = listOf(
            pixels[(pixels.width * expectedMarkerCenter).toInt().coerceIn(0, pixels.width - 1), y],
            pixels[(pixels.width * (1f - expectedMarkerCenter)).toInt().coerceIn(0, pixels.width - 1), y],
        )
        markerSamples.forEachIndexed { index, marker ->
            assertTrue(
                "EvidenceFit keeps source edge marker $index inside the fitted image bounds",
                marker.red > 0.78f && marker.green > 0.68f && marker.blue < 0.42f,
            )
        }
        // Sample inside the fitted source photo; x=0.97 is the dark side backdrop for portrait media.
        val quietTop = pixels[(pixels.width * 0.50f).toInt(), (pixels.height * 0.30f).toInt()]
        val readableBottom = pixels[(pixels.width * 0.50f).toInt(), (pixels.height * 0.88f).toInt()]
        val topBrightness = quietTop.red + quietTop.green + quietTop.blue
        val bottomBrightness = readableBottom.red + readableBottom.green + readableBottom.blue
        val bottomTreatmentDelta = topBrightness - bottomBrightness
        assertTrue(
            "A subtle local bottom treatment keeps card text readable: card=$cardName " +
                "quietTop=$quietTop readableBottom=$readableBottom " +
                "topBrightness=$topBrightness bottomBrightness=$bottomBrightness " +
                "delta=$bottomTreatmentDelta threshold=0.12",
            bottomTreatmentDelta > 0.12f,
        )
        val corners = listOf(
            pixels[(pixels.width * 0.01f).toInt(), (pixels.height * 0.01f).toInt()],
            pixels[(pixels.width * 0.99f).toInt(), (pixels.height * 0.01f).toInt()],
            pixels[(pixels.width * 0.01f).toInt(), (pixels.height * 0.99f).toInt()],
            pixels[(pixels.width * 0.99f).toInt(), (pixels.height * 0.99f).toInt()],
        )
        corners.forEachIndexed { index, color ->
            assertTrue(
                "The synthetic source marker stays clipped outside shared rounded corner $index",
                !(color.red > 0.78f && color.green > 0.68f && color.blue < 0.42f),
            )
        }
    }

    private fun logHierarchyEvidence() {
        fun rect(tag: String, unmerged: Boolean = true): androidx.compose.ui.geometry.Rect =
            compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInWindow

        val bounds = linkedMapOf(
            "root" to rect("normal-home-content-root"),
            "header" to rect("normal-home-header"),
            "brand_title" to rect("normal-home-brand-title"),
            "guest_avatar" to rect("normal-home-guest-avatar"),
            "stats" to rect("normal-home-stats"),
            "recent_header" to rect("normal-home-recent-header"),
            "hero" to rect("normal-home-catch-card-catch-snakehead"),
            "hero_media" to rect("normal-home-catch-media-catch-snakehead"),
            "hero_footer" to rect("normal-home-catch-footer-catch-snakehead"),
            "cta_layout" to rect("normal-home-capture-cta"),
            "cta_text_region" to rect("normal-home-capture-cta-text-region"),
            "cta_text" to rect("normal-home-capture-cta-text"),
            "camera_touch" to rect("normal-home-camera", unmerged = false),
        )
        val header = bounds.getValue("header")
        val title = bounds.getValue("brand_title")
        val avatar = bounds.getValue("guest_avatar")
        val recentHeader = bounds.getValue("recent_header")
        val hero = bounds.getValue("hero")
        val media = bounds.getValue("hero_media")
        val footer = bounds.getValue("hero_footer")
        val cta = bounds.getValue("cta_layout")
        val ctaTextRegion = bounds.getValue("cta_text_region")
        val ctaText = bounds.getValue("cta_text")
        val camera = bounds.getValue("camera_touch")
        val root = bounds.getValue("root")
        val referenceScale = root.width / 1080f
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

        val payload = bounds.entries.joinToString(" ") { (name, rect) ->
            "$name=${rect.left},${rect.top},${rect.right},${rect.bottom}"
        }
        android.util.Log.i("NORMAL_HOME_HIERARCHY_EVIDENCE", payload)

        assertTrue("Brand title stays inside the header", title.left >= header.left && title.right <= header.right)
        assertTrue("Avatar stays inside the header", avatar.left >= header.left && avatar.right <= header.right)
        assertTrue("Brand title and avatar do not overlap", title.right <= avatar.left || avatar.right <= title.left)
        assertTrue("Recent header remains separated from Hero", recentHeader.bottom <= hero.top)
        assertTrue(
            "Hero media shares the rounded card bounds",
            kotlin.math.abs(hero.left - media.left) <= 1f &&
                kotlin.math.abs(hero.top - media.top) <= 1f &&
                kotlin.math.abs(hero.right - media.right) <= 1f &&
                kotlin.math.abs(hero.bottom - media.bottom) <= 1f,
        )
        assertTrue("Hero footer remains over media and inside the Hero", footer.left >= hero.left && footer.top >= hero.top && footer.right <= hero.right && footer.bottom <= hero.bottom)
        assertTrue(
            "CTA layout remains outside the camera semantic target: cta=$cta camera=$camera",
            cta.bottom <= camera.top || camera.bottom <= cta.top,
        )
        assertEquals("CTA keeps its frozen 420 px reference width", 420f * referenceScale, cta.width, 1.5f)
        assertEquals("CTA container keeps its frozen 58 px reference height", 58f * referenceScale, cta.height, 1.5f)
        assertEquals("CTA text region follows the CTA width", cta.width, ctaTextRegion.width, 1f)
        assertTrue("CTA text fits its measured text region", ctaText.top >= ctaTextRegion.top - 1f && ctaText.bottom <= ctaTextRegion.bottom + 1f)
        assertTrue("CTA text and camera touch target do not overlap", ctaText.bottom <= camera.top || camera.bottom <= ctaText.top)
        assertEquals("Camera touch target remains a full square in the window", 216f * referenceScale, camera.width, 1.5f)
        assertEquals("Camera touch target remains a full square in the window", 216f * referenceScale, camera.height, 1.5f)
        assertTrue("Camera touch target meets 48dp in both dimensions", camera.width / density >= 48f && camera.height / density >= 48f)
        assertTrue(
            "Camera touch target remains inside the measured window",
            camera.left >= root.left && camera.top >= root.top && camera.right <= root.right && camera.bottom <= root.bottom,
        )

    }

    private fun logTextLayoutMetric(tag: String, parentTag: String, referenceFontPx: Float) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val parent = compose.onNodeWithTag(parentTag, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("$tag exposes TextLayoutResult", action != null)
        assertTrue(action!!.invoke(layouts))
        val layout = layouts.single()
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        val nodeBounds = node.boundsInRoot
        val parentBounds = parent.boundsInRoot
        val density = layout.layoutInput.density.density
        val fontScale = layout.layoutInput.density.fontScale
        val visibleEnd = if (layout.lineCount > 0) {
            layout.getLineEnd(0, visibleEnd = true).coerceIn(0, text.length)
        } else {
            0
        }
        val characterBoxBounds = if (visibleEnd > 0) {
            val boxes = (0 until visibleEnd).map(layout::getBoundingBox)
            androidx.compose.ui.geometry.Rect(
                left = boxes.minOf { it.left },
                top = boxes.minOf { it.top },
                right = boxes.maxOf { it.right },
                bottom = boxes.maxOf { it.bottom },
            )
        } else {
            androidx.compose.ui.geometry.Rect.Zero
        }
        // getPathForRange returns selection geometry, not the rasterized glyph outline.
        val selectionPathBounds = if (visibleEnd > 0) {
            layout.getPathForRange(0, visibleEnd).getBounds()
        } else {
            androidx.compose.ui.geometry.Rect.Zero
        }
        val layoutBounds = androidx.compose.ui.geometry.Rect(
            0f,
            0f,
            layout.size.width.toFloat(),
            layout.size.height.toFloat(),
        )
        val selectionPathInsideLayout = selectionPathBounds.left >= layoutBounds.left - 0.5f &&
            selectionPathBounds.top >= layoutBounds.top - 0.5f &&
            selectionPathBounds.right <= layoutBounds.right + 0.5f &&
            selectionPathBounds.bottom <= layoutBounds.bottom + 0.5f
        val selectionPathRootBounds = androidx.compose.ui.geometry.Rect(
            nodeBounds.left + selectionPathBounds.left,
            nodeBounds.top + selectionPathBounds.top,
            nodeBounds.left + selectionPathBounds.right,
            nodeBounds.top + selectionPathBounds.bottom,
        )
        val selectionPathInsideParent = selectionPathRootBounds.left >= parentBounds.left - 0.5f &&
            selectionPathRootBounds.top >= parentBounds.top - 0.5f &&
            selectionPathRootBounds.right <= parentBounds.right + 0.5f &&
            selectionPathRootBounds.bottom <= parentBounds.bottom + 0.5f
        val rasterInkBounds = if (tag == "normal-home-brand-title") {
            measureBrandRasterInk(nodeBounds, parentBounds)
        } else {
            null
        }
        val lineBounds = if (layout.lineCount > 0) {
            "${layout.getLineLeft(0)},${layout.getLineTop(0)}..${layout.getLineRight(0)},${layout.getLineBottom(0)}"
        } else {
            "unavailable"
        }
        android.util.Log.i(
            "NORMAL_HOME_TEXT_LAYOUT",
            "tag=$tag parent=$parentTag referenceFontPx=$referenceFontPx " +
                "actualFontSize=${layout.layoutInput.style.fontSize} " +
                "lineHeight=${layout.layoutInput.style.lineHeight} " +
                "density=$density fontScale=$fontScale " +
                "effectiveFontPx=${layout.layoutInput.style.fontSize.value * density * fontScale} " +
                "nodeBoundsPx=${nodeBounds.left},${nodeBounds.top}..${nodeBounds.right},${nodeBounds.bottom} " +
                "nodeSizePx=${nodeBounds.width}x${nodeBounds.height} " +
                "characterBoxBoundsPx=${characterBoxBounds.left},${characterBoxBounds.top}.." +
                "${characterBoxBounds.right},${characterBoxBounds.bottom} " +
                "selectionPathBoundsPx=${selectionPathBounds.left},${selectionPathBounds.top}.." +
                "${selectionPathBounds.right},${selectionPathBounds.bottom} " +
                "selectionPathRootBoundsPx=${selectionPathRootBounds.left},${selectionPathRootBounds.top}.." +
                "${selectionPathRootBounds.right},${selectionPathRootBounds.bottom} " +
                "selectionPathInsideLayout=$selectionPathInsideLayout selectionPathInsideParent=$selectionPathInsideParent " +
                "rasterInkBoundsPx=$rasterInkBounds rasterInkMethod=white-probe-rgb-core-less-than-0.38-0.48-0.58 " +
                "lineBoundsPx=$lineBounds visibleTextEnd=$visibleEnd/${text.length} " +
                "parentBoundsPx=${parentBounds.width}x${parentBounds.height} " +
                "parentRectPx=${parentBounds.left},${parentBounds.top}..${parentBounds.right},${parentBounds.bottom} " +
                "textParentWidthRatio=${nodeBounds.width / parentBounds.width} " +
                "layoutSizePx=${layout.size.width}x${layout.size.height} " +
                "lineCount=${layout.lineCount} didOverflowWidth=${layout.didOverflowWidth} " +
                "didOverflowHeight=${layout.didOverflowHeight} visualOverflow=${layout.hasVisualOverflow} " +
                "textLength=${text.length}",
        )
        assertEquals("$tag follows the Normal Home single-line contract", 1, layout.lineCount)
        if (!tag.contains("-meta-")) {
            assertEquals("$tag keeps every character visible", text.length, visibleEnd)
            // The brand's selection path includes line selection geometry below
            // the Text node. Validate actual rendered ink against the measured boxes.
            val brandRasterFitsContainers = tag == "normal-home-brand-title" &&
                rasterInkBounds != null &&
                rasterInkBounds.left >= nodeBounds.left &&
                rasterInkBounds.top >= nodeBounds.top &&
                rasterInkBounds.right <= nodeBounds.right &&
                rasterInkBounds.bottom <= nodeBounds.bottom &&
                rasterInkBounds.left >= parentBounds.left &&
                rasterInkBounds.top >= parentBounds.top &&
                rasterInkBounds.right <= parentBounds.right &&
                rasterInkBounds.bottom <= parentBounds.bottom
            // Compose can flag a tight intrinsic Text width as overflow after
            // fractional glyph advance rounding even with all characters present.
            // Test the concrete character rectangles, not the boolean alone.
            // The brand has a stronger actual-pixel ink containment check above.
            // getBoundingBox is a character/selection box, not painted ink:
            // measured 48px Recent title has a 71px character box in its
            // frozen 70px Text node while the rendered glyph is wholly visible.
            // Allow <= 1.5 physical pixels of fractional typography rounding,
            // but reject omitted text, wrapping, or material clipping.
            val characterGeometryRoundingPx = 1.5f
            val characterBoxesFitHorizontallyWithinNode = visibleEnd == text.length &&
                characterBoxBounds.left >= -characterGeometryRoundingPx &&
                characterBoxBounds.right <= nodeBounds.width + characterGeometryRoundingPx
            val characterBoxesFitHorizontallyWithinParent =
                nodeBounds.left + characterBoxBounds.left >= parentBounds.left - characterGeometryRoundingPx &&
                nodeBounds.left + characterBoxBounds.right <= parentBounds.right + characterGeometryRoundingPx
            val characterBoxesFitVerticallyWithinNode = visibleEnd == text.length &&
                characterBoxBounds.top >= -characterGeometryRoundingPx &&
                characterBoxBounds.bottom <= nodeBounds.height + characterGeometryRoundingPx
            val characterBoxesFitVerticallyWithinParent =
                nodeBounds.top + characterBoxBounds.top >= parentBounds.top - characterGeometryRoundingPx &&
                    nodeBounds.top + characterBoxBounds.bottom <= parentBounds.bottom + characterGeometryRoundingPx
            val measuredGlyphsFitHorizontally =
                characterBoxesFitHorizontallyWithinNode && characterBoxesFitHorizontallyWithinParent
            val measuredGlyphsFitVertically =
                characterBoxesFitVerticallyWithinNode && characterBoxesFitVerticallyWithinParent
            assertTrue(
                "$tag has clipped horizontal text: didOverflowWidth=${layout.didOverflowWidth} " +
                    "characterBoxes=$characterBoxBounds node=$nodeBounds",
                !layout.didOverflowWidth || brandRasterFitsContainers || measuredGlyphsFitHorizontally,
            )
            assertTrue(
                "$tag has vertical overflow outside its raster container: didOverflowHeight=${layout.didOverflowHeight} " +
                    "characterBoxes=$characterBoxBounds node=$nodeBounds",
                !layout.didOverflowHeight || brandRasterFitsContainers || measuredGlyphsFitVertically,
            )
        }
        assertTrue("$tag remains inside its measured parent", nodeBounds.left >= parentBounds.left - 1f)
        assertTrue("$tag remains inside its measured parent", nodeBounds.top >= parentBounds.top - 1f)
        assertTrue("$tag remains inside its measured parent", nodeBounds.right <= parentBounds.right + 1f)
        assertTrue("$tag remains inside its measured parent", nodeBounds.bottom <= parentBounds.bottom + 1f)
    }

    private fun measureBrandRasterInk(
        nodeBounds: androidx.compose.ui.geometry.Rect,
        parentBounds: androidx.compose.ui.geometry.Rect,
    ): androidx.compose.ui.geometry.Rect {
        val root = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val pixels = root.captureToImage().toPixelMap()
        val scanLeft = ((nodeBounds.left - rootBounds.left).toInt() - 8).coerceAtLeast(0)
        val scanTop = ((nodeBounds.top - rootBounds.top).toInt() - 8).coerceAtLeast(0)
        val scanRight = ((nodeBounds.right - rootBounds.left).toInt() + 8).coerceAtMost(pixels.width)
        val scanBottom = ((nodeBounds.bottom - rootBounds.top).toInt() + 8).coerceAtMost(pixels.height)
        var minX = pixels.width
        var minY = pixels.height
        var maxX = -1
        var maxY = -1
        var corePixelCount = 0
        for (y in scanTop until scanBottom) {
            for (x in scanLeft until scanRight) {
                val color = pixels[x, y]
                // The probe canvas is white; this selects dark ink cores, not
                // antialiased selection/layout bounds.
                if (color.red < 0.38f && color.green < 0.48f && color.blue < 0.58f) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    corePixelCount += 1
                }
            }
        }
        assertTrue("The brand Text node rendered no measurable ink-core pixels", corePixelCount > 0)
        val rasterBounds = androidx.compose.ui.geometry.Rect(
            rootBounds.left + minX,
            rootBounds.top + minY,
            rootBounds.left + maxX + 1f,
            rootBounds.top + maxY + 1f,
        )
        assertTrue(
            "Brand raster ink escapes its measured Text node: raster=$rasterBounds node=$nodeBounds",
            rasterBounds.left >= nodeBounds.left &&
                rasterBounds.top >= nodeBounds.top &&
                rasterBounds.right <= nodeBounds.right &&
                rasterBounds.bottom <= nodeBounds.bottom,
        )
        assertTrue(
            "Brand raster ink escapes the measured header: raster=$rasterBounds header=$parentBounds",
            rasterBounds.left >= parentBounds.left &&
                rasterBounds.top >= parentBounds.top &&
                rasterBounds.right <= parentBounds.right &&
                rasterBounds.bottom <= parentBounds.bottom,
        )
        return rasterBounds
    }

    @Test
    fun edgeToEdgeNormalHomeUsesOneWindowOriginAndReportsInsets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var observedInsets: SafeDrawingInsetsDp? = null

        // The Home scene has a perpetual frame clock for decorative motion.
        // Freeze test-frame advancement while asserting static window geometry;
        // otherwise Espresso waits for an animation that intentionally never ends.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val safeInsets = rememberSafeDrawingInsets()
            SideEffect { observedInsets = safeInsets }
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
                onLoginClick = {},
                onSpeciesClick = {},
                onCatchesClick = {},
                onProfileClick = {},
                onCatchClick = {},
            )
        }
        // With autoAdvance disabled, drive the initial composition once before
        // waiting for semantics and the inset SideEffect; keep later frames frozen.
        compose.mainClock.advanceTimeByFrame()

        compose.waitUntil(timeoutMillis = 10_000) {
            observedInsets != null &&
                compose.onAllNodesWithTag("normal-home-content-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()

        val insets = checkNotNull(observedInsets)
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.configuration.fontScale
        val composeWindow = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val contentWindow = compose.onNodeWithTag("normal-home-content-root", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val headerWindow = compose.onNodeWithTag("normal-home-header", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val ctaContainerWindow = compose.onNodeWithTag("normal-home-capture-cta", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val ctaTextWindow = compose.onNodeWithTag("normal-home-capture-cta-text", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val cameraTouchWindow = compose.onNodeWithTag("normal-home-camera", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow

        val scaleDp = contentWindow.width / density / 1080f
        val usableHeightDp = composeWindow.height / density - insets.top.value - insets.bottom.value
        val offsetYpx = normalHomeVerticalOffset(scaleDp, usableHeightDp) * density
        val expectedHeaderWindowY = composeWindow.top + (104f * scaleDp * density) + offsetYpx

        android.util.Log.i(
            "NORMAL_HOME_WINDOW_METRICS",
            
                "density=$density fontScale=$fontScale safeInsetsPx=" +
                "${insets.start.value * density},${insets.top.value * density}," +
                "${insets.end.value * density},${insets.bottom.value} " +
                "composeWindowBounds=$composeWindow contentBoundsInWindow=$contentWindow " +
                "headerBoundsInWindow=$headerWindow ctaContainerBoundsInWindow=$ctaContainerWindow " +
                "ctaTextBoundsInWindow=$ctaTextWindow cameraTouchBoundsInWindow=$cameraTouchWindow " +
                "scalePhysical=${scaleDp * density} offsetYPhysical=$offsetYpx",
        )
        assertEquals("The composition root must begin at the edge-to-edge window origin", 0f, composeWindow.top, 1f)
        assertEquals("Normal Home must retain the same window Y origin", composeWindow.top, contentWindow.top, 1f)
        assertEquals(
            "Normal Home header uses one safe-height adjustment and no extra top-inset translation",
            expectedHeaderWindowY,
            headerWindow.top,
            1f,
        )
    }

    @Test
    fun shortScreenKeepsHeroAndCameraActionReachableAtLargeFontScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = createPortraitFixture(
            context,
            "normal-home-short-screen-evidence.png",
            360,
            640,
            AndroidColor.rgb(52, 119, 146),
        )
        try {
            compose.setContent {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f),
                ) {
                    Box(
                        Modifier
                            .requiredSize(320.dp, 480.dp)
                            .background(Color.Black),
                    ) {
                        NormalHomeContent(
                            statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                            recentCatches = listOf(
                                record(
                                    id = "short-screen-catch",
                                    image = photo,
                                    speciesId = "grass-carp",
                                    speciesName = "草鱼",
                                    capturedAt = "2026-10-04T18:20:00+08:00",
                                    lengthCm = 42.6f,
                                    weightKg = 1.28f,
                                    location = "千岛湖",
                                ),
                            ),
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
                            safeInsets = SafeDrawingInsetsDp(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            compose.onNodeWithTag("normal-home-short-screen-scroll").fetchSemanticsNode()
            compose.onNodeWithTag("normal-home-header").assertIsDisplayed()
            compose.onNodeWithTag("normal-home-catch-pager").assertIsDisplayed()
            compose.onNodeWithTag("normal-home-capture-cta-text", useUnmergedTree = true).fetchSemanticsNode()

            val rootBounds = compose.onNodeWithTag("normal-home-content-root").fetchSemanticsNode().boundsInRoot
            val camera = compose.onNodeWithTag("normal-home-camera")
            val cameraBeforeScroll = camera.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val ctaBeforeScroll = compose.onNodeWithTag("normal-home-capture-cta").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val ctaTextBeforeScroll = compose.onNodeWithTag("normal-home-capture-cta-text", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val scroll = compose.onNodeWithTag("normal-home-short-screen-scroll")
            val hero = compose.onNodeWithTag("normal-home-catch-pager")
            val maximumScrollPx = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            assertTrue("Large-font short screen exposes a real vertical scroll range: max=$maximumScrollPx", maximumScrollPx > 0f)
            scroll.performTouchInput {
                val gutterX = width * 0.04f
                swipe(
                    start = Offset(gutterX, height * 0.08f),
                    end = Offset(gutterX, height * 0.92f),
                    durationMillis = 500,
                )
            }
            compose.waitForIdle()
            val scrollOffsetBefore = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            scroll.performTouchInput {
                val gutterX = width * 0.04f
                swipe(
                    start = Offset(gutterX, height * 0.92f),
                    end = Offset(gutterX, height * 0.08f),
                    durationMillis = 500,
                )
            }
            compose.waitForIdle()
            val scrollOffsetAfter = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

            val cameraAfterScroll = camera.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val ctaBounds = compose.onNodeWithTag("normal-home-capture-cta").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val ctaTextBounds = compose.onNodeWithTag("normal-home-capture-cta-text", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val density = context.resources.displayMetrics.density
            val expectedHeroHeightPx = 880f * rootBounds.width / 1080f
            val scrollViewportHeightDp = scroll.fetchSemanticsNode().boundsInRoot.height / density
            val heroAfterScrollNode = hero.assertIsDisplayed().fetchSemanticsNode()
            val heroAfterScroll = heroAfterScrollNode.boundsInRoot
            assertTrue(
                "Safe overflow keeps the certified 160dp minimum scroll viewport: ${scrollViewportHeightDp}dp",
                scrollViewportHeightDp >= 160f,
            )
            assertTrue(
                "Pinned camera target remains within the viewport before scrolling",
                cameraBeforeScroll.bottom <= rootBounds.bottom - 16f * density + 1f,
            )
            assertTrue("Short screen mode keeps the camera target inside the safe viewport", cameraAfterScroll.bottom <= rootBounds.bottom - 16f * density + 1f)
            assertEquals("Scrolling the Hero does not move the pinned camera", cameraBeforeScroll.top, cameraAfterScroll.top, 1f)
            assertEquals(
                "Short screen preserves the full-size measured Hero layout even when the viewport clips it",
                expectedHeroHeightPx,
                heroAfterScrollNode.layoutInfo.height.toFloat(),
                2f,
            )
            assertTrue(
                "An upward touch gesture advances the real scroll state: before=$scrollOffsetBefore after=$scrollOffsetAfter max=$maximumScrollPx",
                scrollOffsetAfter > scrollOffsetBefore,
            )
            assertTrue("The Hero keeps a nonzero visible window bound after scrolling", heroAfterScroll.height > 0f)
            assertTrue("The registered camera target remains at least 48dp", cameraAfterScroll.height / density >= 48f)
            assertTrue("CTA remains present and reachable after overflow", ctaBounds.bottom <= cameraAfterScroll.top + 1f)
            assertEquals("Scrolling the Hero does not move the pinned CTA", ctaBeforeScroll.top, ctaBounds.top, 1f)
            assertEquals("Scrolling the Hero does not move the scaled CTA label", ctaTextBeforeScroll.top, ctaTextBounds.top, 1f)
            assertTrue("Scaled CTA label stays above the camera target", ctaTextBounds.bottom <= cameraAfterScroll.top + 1f)
            assertTrue(
                "The scroll reveals an offscreen camera target with a nonzero runtime hit area",
                cameraAfterScroll.width > 0f && cameraAfterScroll.height > 0f,
            )
            assertTrue(
                "Normal Home Hero remains registered during safe overflow",
                compose.onAllNodesWithTag("normal-home-catch-pager").fetchSemanticsNodes().isNotEmpty(),
            )
        } finally {
            photo.delete()
        }
    }

    private fun createPortraitFixture(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            val marker = AndroidColor.rgb(255, 230, 66)
            val markerWidth = (width * 0.1f).toInt()
            for (y in 0 until height) {
                for (x in 0 until markerWidth) {
                    setPixel(x, y, marker)
                    setPixel(width - x - 1, y, marker)
                }
            }
        }
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private fun record(
        id: String,
        image: File,
        speciesId: String,
        speciesName: String,
        capturedAt: String,
        lengthCm: Float,
        weightKg: Float,
        location: String,
    ) = RemoteCatch(
        id = id,
        imageUrl = image.absolutePath,
        speciesId = speciesId,
        speciesName = speciesName,
        confidence = 0.92f,
        modelVersion = "data-parity-fixture",
        capturedAt = capturedAt,
        createdAt = capturedAt,
        lengthCm = lengthCm,
        weightKg = weightKg,
        location = location,
    )
}
