package com.yujian.ai

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeCard
import com.yujian.ai.knowledge.FishKnowledgeCardContent
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.FishKnowledgeEcology
import com.yujian.ai.knowledge.FishKnowledgeFishing
import com.yujian.ai.knowledge.FishKnowledgeGear
import com.yujian.ai.knowledge.FishKnowledgeProfile
import com.yujian.ai.knowledge.FishKnowledgeSkill
import com.yujian.ai.knowledge.FishKnowledgeSpecies
import com.yujian.ai.knowledge.FishKnowledgeStructured
import com.yujian.ai.ui.screens.FishGuideHomeScreen
import com.yujian.ai.ui.screens.FishSpeciesDetailScreen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FishGuideRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val species = listOf(
        FishGuideItem(
            id = "grass_carp",
            nameCn = "草鱼",
            aliases = listOf("鲩鱼"),
            scientificName = "Ctenopharyngodon idella",
            category = "鲤科",
            summary = "常见大型淡水鱼。",
            discovered = true,
            catches = 2,
        ),
        FishGuideItem(
            id = "crucian_carp",
            nameCn = "鲫鱼",
            aliases = listOf("鲫"),
            scientificName = "Carassius auratus",
            category = "鲤科",
            summary = "广泛分布的淡水鱼。",
            discovered = false,
            catches = 0,
        ),
        FishGuideItem(
            id = "common_carp",
            nameCn = "鲤鱼",
            scientificName = "Cyprinus carpio",
            category = "鲤科",
            summary = "常见底层淡水鱼。",
            discovered = true,
            catches = 1,
        ),
    )

    @Test
    fun fishGuide_litAndUnlit_areBrowsableAndClickable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fish_guide_home", 0)
            .edit().putBoolean("carousel_discover_hint_shown", true).commit()
        var opened: String? = null
        composeRule.setContent {
            FishGuideHomeScreen(
                species = species,
                loading = false,
                offlinePreview = false,
                error = null,
                resolveAssetUrl = { null },
                onBack = {},
                onRetry = {},
                onSpeciesClick = { opened = it.id },
            )
        }

        composeRule.onNodeWithText("鱼鉴").assertIsDisplayed()
        composeRule.onNodeWithText("已点亮 2 / 3 种").assertIsDisplayed()
        composeRule.onNodeWithText("草鱼").assertIsDisplayed()
        saveScreenshot("fish_guide_lit.png")

        composeRule.onNodeWithTag("fish_guide_carousel").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("鲫鱼").assertIsDisplayed()
        composeRule.onNodeWithText("尚未点亮").assertIsDisplayed()
        saveScreenshot("fish_guide_unlit.png")
        composeRule.onNodeWithText("鲫鱼").performClick()
        composeRule.runOnIdle { assertEquals("crucian_carp", opened) }
    }

    @Test
    fun fishGuide_reduceMotion_suppressesDiscoverHint_butSwipeWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("fish_guide_home", 0)
        prefs.edit().putBoolean("carousel_discover_hint_shown", false).commit()
        shell("settings put global animator_duration_scale 0")
        shell("settings put global transition_animation_scale 0")
        try {
            composeRule.setContent {
                FishGuideHomeScreen(
                    species = species,
                    loading = false,
                    offlinePreview = false,
                    error = null,
                    resolveAssetUrl = { null },
                    onBack = {},
                    onRetry = {},
                    onSpeciesClick = {},
                )
            }
            composeRule.mainClock.advanceTimeBy(1300)
            composeRule.waitForIdle()
            assertFalse(prefs.getBoolean("carousel_discover_hint_shown", false))
            composeRule.onNodeWithTag("fish_guide_carousel").performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("鲫鱼").assertIsDisplayed()
        } finally {
            shell("settings put global animator_duration_scale 1")
            shell("settings put global transition_animation_scale 1")
        }
    }

    @Test
    fun fishGuide_error_hasRetryPath() {
        var retried = false
        composeRule.setContent {
            FishGuideHomeScreen(
                species = emptyList(),
                loading = false,
                offlinePreview = false,
                error = "网络暂不可用",
                resolveAssetUrl = { null },
                onBack = {},
                onRetry = { retried = true },
                onSpeciesClick = {},
            )
        }
        composeRule.onNodeWithText("鱼种档案暂时无法加载").assertIsDisplayed()
        saveScreenshot("fish_guide_error.png")
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun speciesDetail_usesNaturalKnowledgeSemanticsAndRealCatchCount() {
        val fallback = species.first().copy(catches = 3)
        val detail = detailFixture()
        var openedCatch = false
        var backed = false
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = detail,
                fallback = fallback,
                loading = false,
                offlinePreview = false,
                error = null,
                resolveAssetUrl = { null },
                onRetry = {},
                onBack = { backed = true },
                onOpenCatch = { openedCatch = true },
            )
        }

        composeRule.onNodeWithText("Ctenopharyngodon idella").assertIsDisplayed()
        composeRule.onNodeWithText("鱼种主卡").assertExists()
        composeRule.onNodeWithText("英雄卡").assertDoesNotExist()
        composeRule.onNodeWithText("稀有 2  ·  力量 3  ·  挑战 1").assertDoesNotExist()
        saveScreenshot("fish_species_detail.png")

        composeRule.onNodeWithText("我的鱼获").performClick()
        composeRule.onNodeWithText("已记录 3 条该鱼种鱼获").assertIsDisplayed()
        saveScreenshot("fish_species_detail_catch.png")
        composeRule.onNodeWithText("查看我的鱼获 · 3").performClick()
        composeRule.runOnIdle { assertTrue(openedCatch) }

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test
    fun speciesDetail_empty_hasRetryPath() {
        var retried = false
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = null,
                fallback = null,
                loading = false,
                offlinePreview = false,
                error = "服务暂不可用",
                resolveAssetUrl = { null },
                onRetry = { retried = true },
                onBack = {},
                onOpenCatch = {},
            )
        }
        composeRule.onNodeWithText("暂时无法读取鱼种详情").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    private fun detailFixture(): FishKnowledgeDetail = FishKnowledgeDetail(
        species = FishKnowledgeSpecies(
            id = "grass_carp",
            nameCn = "草鱼",
            aliases = listOf("鲩鱼"),
            scientificName = "Ctenopharyngodon idella",
            category = "鲤科",
            family = "Cyprinidae",
            genus = "Ctenopharyngodon",
            summary = "常见大型淡水鱼。",
            status = "ACTIVE",
            coverImage = null,
        ),
        cover = null,
        cards = listOf(
            FishKnowledgeCard(
                id = 1,
                speciesId = "grass_carp",
                cardType = "HERO",
                title = "草鱼主卡",
                imageUrl = "",
                description = "草食性大型淡水鱼",
                content = FishKnowledgeCardContent(
                    type = "HERO",
                    tag = "中下层",
                    rarity = 2,
                    power = 3,
                    challenge = 1,
                    description = "草食性大型淡水鱼",
                ),
                sortOrder = 0,
                status = "ACTIVE",
            ),
            FishKnowledgeCard(
                id = 2,
                speciesId = "grass_carp",
                cardType = "IDENTIFICATION",
                title = "旧草稿",
                imageUrl = "",
                description = "",
                content = FishKnowledgeCardContent(type = "IDENTIFICATION"),
                sortOrder = 1,
                status = "DRAFT",
            ),
        ),
        gallery = emptyList(),
        profile = FishKnowledgeProfile(
            bodyShape = "纺锤形",
            features = listOf("体色青灰", "鳞片较大"),
            habitat = listOf("江河", "湖库"),
            food = "草食",
            season = listOf("春", "夏", "秋"),
        ),
        fishing = FishKnowledgeFishing(
            waterLayer = "中下层",
            season = listOf("夏", "秋"),
            bait = listOf("玉米"),
            method = listOf("底钓"),
            summary = "寻找有水草的缓流区域。",
        ),
        videos = emptyList(),
        similarity = emptyList(),
        knowledge = FishKnowledgeStructured(
            ecology = FishKnowledgeEcology(
                habitat = listOf("江河", "湖库"),
                waterLayer = "中下层",
                season = "夏秋",
                behavior = "喜在水草丰富区域活动",
                diet = "草食",
            ),
            gear = FishKnowledgeGear(
                method = "底钓",
                rod = "中长竿",
                line = "中等线组",
                hook = "中号钩",
                bait = listOf("玉米"),
            ),
            skill = FishKnowledgeSkill(tip = "中鱼后保持稳定控鱼"),
        ),
        dynamicAvailable = true,
    )

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "fish_guide_v1")
        check(root.exists() || root.mkdirs())
        FileOutputStream(File(root, name)).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        bitmap.recycle()
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command).close()
    }
}
