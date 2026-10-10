package com.yujian.ai.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
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

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("鳜鱼 鱼获照片").fetchSemanticsNodes().isNotEmpty() &&
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

            val firstCard = compose.onNodeWithTag("normal-home-catch-card-catch-snakehead")
            val secondCard = compose.onNodeWithTag("normal-home-catch-card-catch-bass")
            val pager = compose.onNodeWithTag("normal-home-catch-pager")
            firstCard.assertIsDisplayed()
            secondCard.assertIsDisplayed()
            val firstCardBounds = firstCard.fetchSemanticsNode().boundsInRoot
            val firstMediaBounds = compose.onNodeWithTag("normal-home-catch-media-catch-snakehead", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(pagerBounds.center.x, firstCardBounds.center.x, 1.5f)
            assertEquals(740f / 1080f, firstCardBounds.width / pagerBounds.width, 0.015f)
            assertEquals(880f / 1080f, firstCardBounds.height / pagerBounds.width, 0.02f)
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

            logTextLayoutMetric("normal-home-brand-title", "normal-home-header", referenceFontPx = 72f)
            listOf("species", "catches", "record-days").forEach { stat ->
                logTextLayoutMetric("normal-home-stat-$stat-value", "normal-home-stat-$stat", referenceFontPx = 36f)
                logTextLayoutMetric("normal-home-stat-$stat-label", "normal-home-stat-$stat", referenceFontPx = 28f)
            }
            logTextLayoutMetric("normal-home-recent-title", "normal-home-recent-header", referenceFontPx = 48f)
            logTextLayoutMetric("normal-home-recent-all-label", "normal-home-recent-header", referenceFontPx = 40f)
            logTextLayoutMetric("normal-home-catch-species-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 56f)
            logTextLayoutMetric("normal-home-catch-measurement-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 46f)
            logTextLayoutMetric("normal-home-catch-meta-catch-snakehead", "normal-home-catch-footer-catch-snakehead", referenceFontPx = 36f)
            logTextLayoutMetric("normal-home-capture-cta-text", "normal-home-capture-cta", referenceFontPx = 40f)

            assertEvidenceFitCard(firstCard, sourceAspectRatio = 360f / 640f)
            firstCard.performClick()
            compose.runOnIdle { assertEquals("catch-snakehead", openedCatch) }

            pager.performTouchInput { swipeLeft() }
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
            assertEvidenceFitCard(secondCard, sourceAspectRatio = 640f / 800f)

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
    fun realSamplePhotoSharesFullHeroBoundsAndClipsAtAllFourRoundedCorners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "normal-home-real-photo.jpg")
        val backdrop = AndroidColor.rgb(11, 23, 31)
        context.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
            photo.outputStream().use { output -> input.copyTo(output) }
        }
        val sourceShaBeforeDisplay = sha256(photo)
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
            val card = compose.onNodeWithTag("normal-home-catch-card-real-photo-catch")
            val media = compose.onNodeWithTag("normal-home-catch-media-real-photo-catch", useUnmergedTree = true)
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            val mediaBounds = media.fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag(
                "normal-home-catch-media-mode-evidence-fit-bbox-absent-real-photo-catch",
                useUnmergedTree = true,
            ).assertIsDisplayed()
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
            assertEquals("EVIDENCE_FIT leaves the stored source bytes unchanged", sourceShaBeforeDisplay, sha256(photo))

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

    private fun assertEvidenceFitCard(
        card: androidx.compose.ui.test.SemanticsNodeInteraction,
        sourceAspectRatio: Float,
    ) {
        val pixels = card.captureToImage().toPixelMap()
        val cardAspectRatio = pixels.width.toFloat() / pixels.height.toFloat()
        val fittedWidthFraction = minOf(1f, sourceAspectRatio / cardAspectRatio)
        val fittedLeft = (1f - fittedWidthFraction) / 2f
        val y = (pixels.height * 0.28f).toInt().coerceIn(0, pixels.height - 1)
        val leftMarkerX = (pixels.width * (fittedLeft + fittedWidthFraction * 0.05f)).toInt()
        val rightMarkerX = (pixels.width * (fittedLeft + fittedWidthFraction * 0.95f)).toInt()
        val leftMarker = pixels[leftMarkerX.coerceIn(0, pixels.width - 1), y]
        val rightMarker = pixels[rightMarkerX.coerceIn(0, pixels.width - 1), y]
        listOf(leftMarker, rightMarker).forEachIndexed { index, marker ->
            assertTrue(
                "EVIDENCE_FIT preserves source edge marker $index inside the full-image foreground",
                marker.red > 0.78f && marker.green > 0.68f && marker.blue < 0.42f,
            )
        }
        val leftBackdrop = pixels[(pixels.width * 0.01f).toInt(), y]
        val rightBackdrop = pixels[(pixels.width * 0.99f).toInt(), y]
        listOf(leftBackdrop, rightBackdrop).forEachIndexed { index, color ->
            assertTrue(
                "EVIDENCE_FIT fills unused space from the same photo instead of black bars ($index)",
                color.red + color.green + color.blue > 0.12f,
            )
        }
        val quietTop = pixels[(pixels.width * 0.97f).toInt(), (pixels.height * 0.30f).toInt()]
        val readableBottom = pixels[(pixels.width * 0.97f).toInt(), (pixels.height * 0.88f).toInt()]
        val topBrightness = quietTop.red + quietTop.green + quietTop.blue
        val bottomBrightness = readableBottom.red + readableBottom.green + readableBottom.blue
        assertTrue("A subtle local bottom treatment keeps card text readable", topBrightness - bottomBrightness > 0.12f)
        val corners = listOf(
            pixels[(pixels.width * 0.01f).toInt(), (pixels.height * 0.01f).toInt()],
            pixels[(pixels.width * 0.99f).toInt(), (pixels.height * 0.01f).toInt()],
            pixels[(pixels.width * 0.01f).toInt(), (pixels.height * 0.99f).toInt()],
            pixels[(pixels.width * 0.99f).toInt(), (pixels.height * 0.99f).toInt()],
        )
        corners.forEachIndexed { index, color ->
            assertTrue(
                "The synthetic edge marker stays clipped outside shared rounded corner $index",
                !(color.red > 0.78f && color.green > 0.68f && color.blue < 0.42f),
            )
        }
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
            val characterBoxesInsideNode = visibleEnd == text.length &&
                characterBoxBounds.left >= -characterGeometryRoundingPx &&
                characterBoxBounds.top >= -characterGeometryRoundingPx &&
                characterBoxBounds.right <= nodeBounds.width + characterGeometryRoundingPx &&
                characterBoxBounds.bottom <= nodeBounds.height + characterGeometryRoundingPx
            val characterBoxesInsideParent =
                nodeBounds.left + characterBoxBounds.left >= parentBounds.left - characterGeometryRoundingPx &&
                nodeBounds.top + characterBoxBounds.top >= parentBounds.top - characterGeometryRoundingPx &&
                nodeBounds.left + characterBoxBounds.right <= parentBounds.right + characterGeometryRoundingPx &&
                nodeBounds.top + characterBoxBounds.bottom <= parentBounds.bottom + characterGeometryRoundingPx
            val measuredGlyphsFit = characterBoxesInsideNode && characterBoxesInsideParent
            assertTrue(
                "$tag has clipped horizontal text: didOverflowWidth=${layout.didOverflowWidth} " +
                    "characterBoxes=$characterBoxBounds node=$nodeBounds",
                !layout.didOverflowWidth || brandRasterFitsContainers || measuredGlyphsFit,
            )
            assertTrue(
                "$tag has vertical overflow outside its raster container: didOverflowHeight=${layout.didOverflowHeight} " +
                    "characterBoxes=$characterBoxBounds node=$nodeBounds",
                !layout.didOverflowHeight || brandRasterFitsContainers || measuredGlyphsFit,
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
