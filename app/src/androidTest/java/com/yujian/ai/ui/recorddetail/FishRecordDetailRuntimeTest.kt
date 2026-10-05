package com.yujian.ai.ui.recorddetail

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.theme.YujianTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FishRecordDetailRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noUploadedMemoryKeepsThreeActionsAndDoesNotExposeFlipBeforeReady() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            YujianTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    FishRecordDetailScreen(
                        uiState = FishRecordDetailUiState.Success(record("no-memory")),
                        initialSection = "memory",
                        imageUrlFor = { null },
                        bsideUrlFor = { null },
                        accessToken = "",
                        onBack = {},
                        onRetry = {},
                        onOpenFishGuide = {},
                        onShare = {},
                        onEditRecord = {},
                        onAddMedia = { calls += "add" },
                        onContinuePhoto = { calls += "photo" },
                        onRecordVideo = { calls += "video" },
                        onGenerateMemory = null,
                        onRefreshBsideStatus = { true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("鱼获记忆").assertIsDisplayed()
        composeRule.onNodeWithText("还没有留下影像").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithContentDescription("切换鱼获记忆").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("添加照片/视频").performClick()
        composeRule.onNodeWithText("继续拍照").performClick()
        composeRule.onNodeWithText("录制视频").performClick()
        composeRule.onNodeWithText("为这次相遇生成一份鱼获记忆").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("add", "photo", "video"), calls) }
    }

    @Test
    fun suppliedStoryIsRenderedInAboutCatchSection() {
        composeRule.setContent {
            YujianTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    FishRecordDetailScreen(
                        uiState = FishRecordDetailUiState.Success(record("with-story").copy(story = "第一条黑鱼。")),
                        imageUrlFor = { null },
                        bsideUrlFor = { null },
                        accessToken = "",
                        onBack = {},
                        onRetry = {},
                        onOpenFishGuide = {},
                        onShare = {},
                        onEditRecord = {},
                        onAddMedia = {},
                        onContinuePhoto = {},
                        onRecordVideo = {},
                        onGenerateMemory = null,
                        onRefreshBsideStatus = { true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("第一条黑鱼。").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun readyBsideAutoRevealsOnceAfterRealImageLoadsThenLaterEntryStartsOnA() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "first-reveal-${System.currentTimeMillis()}"
        val record = record(id).copy(bsideStatus = BsideStatus.READY)
        val original = imageFile(context.cacheDir, "catch-$id.png", 0xFF487A91.toInt())
        val memory = imageFile(context.cacheDir, "memory-$id.png", 0xFF497B83.toInt())
        val preferences = context.getSharedPreferences("fish_record_detail_v1", 0)
        preferences.edit().remove("first_b_reveal_done:$id").commit()
        val laterEntry = mutableStateOf(false)

        composeRule.setContent {
            YujianTheme {
                key(laterEntry.value) {
                    Box(Modifier.size(360.dp, 640.dp)) {
                        FishRecordDetailScreen(
                            uiState = FishRecordDetailUiState.Success(record),
                            imageUrlFor = { Uri.fromFile(original).toString() },
                            bsideUrlFor = { Uri.fromFile(memory).toString() },
                            accessToken = "",
                            onBack = {},
                            onRetry = {},
                            onOpenFishGuide = {},
                            onShare = {},
                            onEditRecord = {},
                            onAddMedia = {},
                            onContinuePhoto = {},
                            onRecordVideo = {},
                            onGenerateMemory = null,
                            onRefreshBsideStatus = { true },
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(5_000) {
            preferences.getBoolean("first_b_reveal_done:$id", false)
        }
        composeRule.onNodeWithContentDescription("草鱼 鱼获记忆").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切回鱼获照片").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("为这次相遇生成一份鱼获记忆").fetchSemanticsNodes().isEmpty())

        composeRule.runOnIdle { laterEntry.value = true }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("草鱼 鱼获照片")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("草鱼 鱼获照片").assertIsDisplayed()
    }

    private fun record(id: String) = RemoteCatch(
        id = id,
        imageUrl = "",
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = .96f,
        modelVersion = "test",
        capturedAt = "2026-09-25T08:30:00Z",
        createdAt = "2026-09-25T08:30:00Z",
        lengthCm = 42.6f,
        weightKg = 1.28f,
        location = "浙江·千岛湖",
    )

    private fun imageFile(directory: File, name: String, color: Int): File =
        File(directory, name).apply {
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
}
