package com.yujian.ai.ui.recorddetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.CatchRecordEditDraft
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.theme.YujianTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishRecordEditSheetRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun timeIsHumanReadableAndSaveFailureKeepsStoryForRetry() {
        val saveResult = AtomicBoolean(false)
        val dismissed = AtomicBoolean(false)
        val savedDrafts = mutableListOf<CatchRecordEditDraft>()
        val record = RemoteCatch(
            id = "record-edit-runtime",
            imageUrl = "https://example.test/catch.jpg",
            speciesId = "grass_carp",
            speciesName = "草鱼",
            confidence = .96f,
            modelVersion = "test",
            capturedAt = "2026-10-07T12:34:00+08:00",
            createdAt = "2026-10-07T12:34:00+08:00",
            lengthCm = 40f,
            weightKg = 6f,
            location = "杭州·钱塘江",
            bsideStatus = BsideStatus.NONE,
        )

        composeRule.setContent {
            YujianTheme {
                FishRecordEditSheet(
                    record = record,
                    speciesCatalog = listOf(FishGuideItem(id = "grass_carp", nameCn = "草鱼")),
                    onDismiss = { dismissed.set(true) },
                    onSave = { draft ->
                        savedDrafts += draft
                        saveResult.get()
                    },
                )
            }
        }

        composeRule.onNodeWithText("编辑鱼获信息").assertIsDisplayed()
        composeRule.onNodeWithText("保存修改").assertIsDisplayed()
        composeRule.onNodeWithText("2026年10月7日 12:34").performScrollTo().assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("2026-10-07T12:34:00+08:00").fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithTag("fish_record_story_input").performScrollTo().performTextInput("现场水面很平静。")
        composeRule.onNodeWithTag("fish_record_save_button").performClick()
        composeRule.onNodeWithText("保存失败，修改仍保留在这里，请重试。").assertIsDisplayed()
        composeRule.onNodeWithTag("fish_record_story_input").assertTextContains("现场水面很平静。")

        composeRule.runOnIdle { saveResult.set(true) }
        composeRule.onNodeWithTag("fish_record_save_button").performClick()
        composeRule.runOnIdle {
            assertEquals(2, savedDrafts.size)
            assertEquals("现场水面很平静。", savedDrafts.last().story)
            assertEquals("2026-10-07T12:34:00+08:00", savedDrafts.last().capturedAt)
            assertEquals(true, dismissed.get())
        }
    }
}
