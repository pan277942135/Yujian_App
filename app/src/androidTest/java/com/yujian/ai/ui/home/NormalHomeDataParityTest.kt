package com.yujian.ai.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
                location = "江苏省苏州市吴中区太湖国家湿地公园东岸",
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
                    modifier = Modifier.size(360.dp, 760.dp),
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
            assertTrue(firstMediaBounds.width < firstCardBounds.width)
            assertTrue(firstMediaBounds.height < firstCardBounds.height)

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
            assertTrue(metaText.contains("江苏省苏州市吴中区太湖国家湿地公园东岸"))
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

            assertCoveredCard(firstCard)
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
            assertCoveredCard(secondCard)

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

    private fun assertCoveredCard(card: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val pixels = card.captureToImage().toPixelMap()
        val y = (pixels.height * 0.28f).toInt().coerceIn(0, pixels.height - 1)
        val edge = pixels[(pixels.width * 0.02f).toInt().coerceIn(0, pixels.width - 1), y]
        assertTrue(
            "The viewport edge should show the source image's side marker, not a fitted-image pillar",
            edge.red > 0.78f && edge.green > 0.68f && edge.blue < 0.42f,
        )
        val quietTop = pixels[(pixels.width * 0.97f).toInt(), (pixels.height * 0.30f).toInt()]
        val readableBottom = pixels[(pixels.width * 0.97f).toInt(), (pixels.height * 0.88f).toInt()]
        val topBrightness = quietTop.red + quietTop.green + quietTop.blue
        val bottomBrightness = readableBottom.red + readableBottom.green + readableBottom.blue
        assertTrue("A subtle local bottom treatment keeps card text readable", topBrightness - bottomBrightness > 0.12f)
        val roundedCorner = pixels[(pixels.width * 0.02f).toInt(), (pixels.height * 0.02f).toInt()]
        assertTrue(
            "The source image must not show through the rounded card corner",
            !(roundedCorner.red > 0.78f && roundedCorner.green > 0.68f && roundedCorner.blue < 0.42f),
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
