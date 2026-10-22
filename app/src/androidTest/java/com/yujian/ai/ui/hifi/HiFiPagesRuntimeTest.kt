package com.yujian.ai.ui.hifi

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.recorddetail.FishRecordDetailScreen
import com.yujian.ai.ui.recorddetail.FishRecordDetailUiState
import com.yujian.ai.ui.screens.MyScreen
import com.yujian.ai.ui.theme.YujianTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Controlled states render real production composables, not a raster copy of Frozen.
 * Dynamic fish photos come from an existing application test image asset.
 */
@RunWith(AndroidJUnit4::class)
class HiFiPagesRuntimeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun photoFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(context.cacheDir, "hifi-real-fish.jpg")
        context.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { source ->
            target.outputStream().use { dest -> source.copyTo(dest) }
        }
        return target
    }

    private fun record(id: String, url: String) = RemoteCatch(
        id = id,
        imageUrl = url,
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = 0.96f,
        modelVersion = "hifi-evidence",
        capturedAt = "2026-10-08T08:30:00+08:00",
        createdAt = "2026-10-08T08:30:00+08:00",
        lengthCm = 42.6f,
        weightKg = 1.28f,
        location = "浙江·千岛湖",
    )

    @Test
    fun fishRecordDetailFullScreenAProducesAuthenticPhotoEvidence() {
        val photo = Uri.fromFile(photoFile()).toString()
        val item = record("hifi-record-detail", photo)
        rule.setContent {
            YujianTheme {
                FishRecordDetailScreen(
                    uiState = FishRecordDetailUiState.Success(item),
                    imageUrlFor = { photo },
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
        rule.onNodeWithText("鱼获详情").assertIsDisplayed()
        rule.onNodeWithContentDescription("草鱼 鱼获照片").assertIsDisplayed()
        rule.waitForIdle()
        HiFiRuntimeCapture.save(rule.activity, "login_v2/hifi-pages", "record_detail_a.png")
    }

    @Test
    fun myCatchesTimelineProducesAuthenticDataStateEvidence() {
        val photo = Uri.fromFile(photoFile()).toString()
        val records = listOf(
            record("hifi-catch-1", photo),
            record("hifi-catch-2", photo).copy(
                speciesId = "mandarin_fish", speciesName = "鳜鱼",
                capturedAt = "2026-10-07T09:12:00+08:00",
                createdAt = "2026-10-07T09:12:00+08:00",
            ),
        )
        rule.setContent {
            YujianTheme {
                MyScreen(
                    catches = records,
                    loading = false,
                    error = null,
                    resolveImageUrl = { it },
                    accessToken = "",
                    onBack = {},
                    onCatch = {},
                    onRetry = {},
                    onCapture = {},
                    onDayDetail = {},
                )
            }
        }
        rule.onNodeWithText("我的鱼获").assertIsDisplayed()
        rule.onNodeWithContentDescription("搜索鱼获").assertIsDisplayed()
        rule.onNodeWithContentDescription("筛选").assertIsDisplayed()
        rule.waitForIdle()
        HiFiRuntimeCapture.save(rule.activity, "login_v2/hifi-pages", "my_catches_timeline.png")
    }
}
