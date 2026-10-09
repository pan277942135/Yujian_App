package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.RemoteCatch
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.math.abs
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
            coverImage = "https://cdn.example/legacy-black-gold.webp",
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
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("草鱼").assertIsDisplayed()
        composeRule.onNodeWithText("草鱼 · 鱼鉴主视觉暂不可用").assertIsDisplayed()
        val carouselBounds = composeRule.onNodeWithTag("fish_guide_carousel").fetchSemanticsNode().boundsInRoot
        val speciesTitleBounds = composeRule.onNodeWithText("草鱼").fetchSemanticsNode().boundsInRoot
        assertTrue("Species title must anchor the upper card hierarchy", speciesTitleBounds.center.y < carouselBounds.center.y)
        saveScreenshot("fish_guide_lit.png", selectedSpeciesId = "grass_carp")

        swipeCarouselToSelectedSpecies(
            fromName = "草鱼",
            name = "鲫鱼",
            expectedPage = 1,
        )
        composeRule.onNodeWithText("尚未点亮").assertIsDisplayed()
        saveScreenshot("fish_guide_unlit.png", selectedSpeciesId = "crucian_carp")
        composeRule.onNodeWithText("鲫鱼").performClick()
        composeRule.runOnIdle { assertEquals("crucian_carp", opened) }
    }

    @Test
    fun fishGuide_reduceMotion_suppressesDiscoverHint_butSwipeWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("fish_guide_home", 0)
        prefs.edit().putBoolean("carousel_discover_hint_shown", false).commit()
        val animatorScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val transitionScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        try {
            shell("settings put global animator_duration_scale 0")
            shell("settings put global transition_animation_scale 0")
            assertEquals(
                0f,
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
                0f,
            )
            assertEquals(
                0f,
                Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
                0f,
            )
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
            waitForSelectedSpecies(name = "草鱼", expectedPage = 0)
            composeRule.mainClock.advanceTimeBy(1300)
            composeRule.runOnIdle {
                assertFalse(prefs.getBoolean("carousel_discover_hint_shown", false))
            }
            swipeCarouselToSelectedSpecies(
                fromName = "草鱼",
                name = "鲫鱼",
                expectedPage = 1,
            )
        } finally {
            shell("settings put global animator_duration_scale $animatorScale")
            shell("settings put global transition_animation_scale $transitionScale")
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
        composeRule.onNodeWithText("当前无法加载鱼种资料").assertIsDisplayed()
        saveScreenshot("fish_guide_error.png")
        composeRule.onNode(hasText("检查网络后重试") and hasClickAction()).performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun speciesDetail_usesNaturalKnowledgeSemanticsAndRealCatchCount() {
        val detail = detailFixture()
        val catches = listOf(
            catch("catch_1", "grass_carp", "2026-09-22T10:00:00Z"),
            catch("catch_2", "grass_carp", "2026-09-25T10:00:00Z"),
            catch("catch_3", "grass_carp", "2026-09-27T10:00:00Z"),
            catch("other", "crucian_carp", "2026-09-28T10:00:00Z"),
        )
        var openedCatchId: String? = null
        var openedSpeciesFilter: String? = null
        var backed = false
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = detail,
                fallback = null,
                savedCatches = catches,
                loading = false,
                offlinePreview = false,
                error = null,
                resolveAssetUrl = { null },
                resolveCatchImageUrl = { null },
                onRetry = {},
                onBack = { backed = true },
                onRecordCatch = {},
                onOpenCatch = { openedCatchId = it },
                onOpenSpeciesCatches = { openedSpeciesFilter = it },
            )
        }

        composeRule.onNodeWithText("Ctenopharyngodon idella").assertIsDisplayed()
        composeRule.onNodeWithText("鱼种名片").assertExists()
        assertTrue(composeRule.onAllNodesWithText("英雄卡").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("稀有 2  ·  力量 3  ·  挑战 1").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("01 / 05").assertIsDisplayed()
        val carouselBounds = composeRule.onNodeWithTag("fish_species_knowledge_carousel")
            .fetchSemanticsNode().boundsInRoot
        val activeCardBounds = composeRule.onNodeWithContentDescription("鱼种名片，草鱼")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Species Detail active card width must follow the frozen 82–86% range",
            activeCardBounds.width / carouselBounds.width in 0.82f..0.86f,
        )
        assertTrue(composeRule.onAllNodesWithText("排行榜").fetchSemanticsNodes().isEmpty())
        saveScreenshot("fish_species_detail.png")
        composeRule.onNodeWithText("我的草鱼").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3次记录").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("我的草鱼，3次记录，打开该鱼种鱼获").performClick()
        composeRule.runOnIdle { assertEquals("grass_carp", openedSpeciesFilter) }
        composeRule.onNodeWithContentDescription("打开草鱼鱼获记录，2026-09-27，照片暂不可用").performClick()
        composeRule.runOnIdle { assertEquals("catch_3", openedCatchId) }
        saveScreenshot("fish_species_detail_catch.png")

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle { assertTrue(backed) }
    }

    @Test
    fun speciesDetail_zeroCatch_usesQuietStateAndUnfilteredCaptureEntry() {
        var openedCapture = false
        var openedFilteredCatches = false
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = detailFixture(),
                fallback = null,
                savedCatches = emptyList(),
                loading = false,
                offlinePreview = false,
                error = null,
                resolveAssetUrl = { null },
                resolveCatchImageUrl = { null },
                onRetry = {},
                onBack = {},
                onRecordCatch = { openedCapture = true },
                onOpenCatch = {},
                onOpenSpeciesCatches = { openedFilteredCatches = true },
            )
        }

        composeRule.onNodeWithText("我的草鱼").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0次记录").assertIsDisplayed()
        composeRule.onNodeWithText("还没有记录").assertIsDisplayed()
        saveScreenshot("fish_species_detail_zero_catch.png")
        composeRule.onNodeWithText("去记录鱼获").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(openedCapture)
            assertFalse(openedFilteredCatches)
        }
    }

    @Test
    fun speciesDetail_knowledgeCarouselHasFiveFinitePositions() {
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = detailFixture(),
                fallback = null,
                savedCatches = emptyList(),
                loading = false,
                offlinePreview = false,
                error = null,
                resolveAssetUrl = { null },
                resolveCatchImageUrl = { null },
                onRetry = {},
                onBack = {},
                onRecordCatch = {},
                onOpenCatch = {},
                onOpenSpeciesCatches = {},
            )
        }

        composeRule.onNodeWithText("01 / 05").assertIsDisplayed()
        repeat(4) {
            composeRule.onNodeWithTag("fish_species_knowledge_carousel").performTouchInput { swipeLeft() }
        }
        composeRule.onNodeWithText("05 / 05").assertIsDisplayed()
        composeRule.onNodeWithTag("fish_species_knowledge_carousel").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("05 / 05").assertIsDisplayed()
    }

    @Test
    fun speciesDetail_empty_hasRetryPath() {
        var retried = false
        composeRule.setContent {
            FishSpeciesDetailScreen(
                detail = null,
                fallback = null,
                savedCatches = emptyList(),
                loading = false,
                offlinePreview = false,
                error = "服务暂不可用",
                resolveAssetUrl = { null },
                resolveCatchImageUrl = { null },
                onRetry = { retried = true },
                onBack = {},
                onRecordCatch = {},
                onOpenCatch = {},
                onOpenSpeciesCatches = {},
            )
        }
        composeRule.onNodeWithText("该鱼种资料暂不可用").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    private fun catch(id: String, speciesId: String, date: String) = RemoteCatch(
        id = id,
        imageUrl = "",
        speciesId = speciesId,
        speciesName = "草鱼",
        confidence = 0.9f,
        modelVersion = "test",
        capturedAt = date,
        createdAt = date,
    )

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

    private fun saveScreenshot(name: String, selectedSpeciesId: String? = null) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rawBitmap = instrumentation.uiAutomation.takeScreenshot()
        val appSurface = cropToComposeRoot(rawBitmap)
        try {
            val context = instrumentation.targetContext
            val root = File(context.getExternalFilesDir(null), "fish_guide_v1")
            check(root.exists() || root.mkdirs())
            val nativeScreenshot = File(root, name)
            FileOutputStream(nativeScreenshot).use { out ->
                check(rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            val appScreenshot = File(root, name.removeSuffix(".png") + "_app_surface.png")
            FileOutputStream(appScreenshot).use { out ->
                check(appSurface.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
            fun boundsForTag(tag: String): JSONObject? = runCatching {
                val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
                JSONObject()
                    .put("left", bounds.left)
                    .put("top", bounds.top)
                    .put("right", bounds.right)
                    .put("bottom", bounds.bottom)
            }.getOrNull()
            val geometry = JSONObject()
            boundsForTag("fish_guide_carousel")?.let { geometry.put("carousel_bounds_in_window", it) }
            boundsForTag("fish_guide_progress")?.let { geometry.put("progress_bounds_in_window", it) }
            selectedSpeciesId?.let { id ->
                boundsForTag("fish_guide_card_$id")?.let { geometry.put("selected_card_bounds_in_window", it) }
                geometry.put("selected_species_id", id)
            }
            val selectedItem = selectedSpeciesId?.let { id -> species.firstOrNull { it.id == id } }
            val windowOrigin = IntArray(2)
            var insetLeft = 0
            var insetTop = 0
            var insetRight = 0
            var insetBottom = 0
            var activityName = "unknown"
            composeRule.runOnUiThread {
                val decor = composeRule.activity.window.decorView
                decor.getLocationOnScreen(windowOrigin)
                val compatInsets = ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                if (compatInsets != null) {
                    insetLeft = compatInsets.left
                    insetTop = compatInsets.top
                    insetRight = compatInsets.right
                    insetBottom = compatInsets.bottom
                }
                activityName = composeRule.activity.javaClass.name
            }
            val apkSha256 = FileInputStream(context.packageCodePath).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
            val metadata = JSONObject().apply {
                put("package_name", context.packageName)
                put("activity", activityName)
                put("device_model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
                put("screenshot_width_px", rawBitmap.width)
                put("screenshot_height_px", rawBitmap.height)
                put("density", context.resources.displayMetrics.density)
                put("density_dpi", context.resources.displayMetrics.densityDpi)
                put("font_scale", context.resources.configuration.fontScale)
                put("insets_px", JSONObject().put("left", insetLeft).put("top", insetTop).put("right", insetRight).put("bottom", insetBottom))
                put("compose_root_bounds_in_window", JSONObject()
                    .put("left", rootBounds.left).put("top", rootBounds.top)
                    .put("right", rootBounds.right).put("bottom", rootBounds.bottom))
                put("window_origin_on_screen_px", JSONObject().put("x", windowOrigin[0]).put("y", windowOrigin[1]))
                put("safe_coordinate_map", JSONObject()
                    .put("origin_x_px", windowOrigin[0] + rootBounds.left)
                    .put("origin_y_px", windowOrigin[1] + rootBounds.top)
                    .put("width_px", rootBounds.width)
                    .put("height_px", rootBounds.height)
                    .put("source", "Compose semantics root bounds mapped to native screenshot pixels"))
                put("geometry_bounds", geometry)
                put("selected_cover_hero_status", selectedItem?.coverHeroStatus ?: JSONObject.NULL)
                put("selected_cover_hero_version_id", selectedItem?.coverHeroVersionId ?: JSONObject.NULL)
                put("selected_cover_hero_image_url", selectedItem?.coverHeroImage ?: JSONObject.NULL)
                put("native_screenshot", nativeScreenshot.name)
                put("native_screenshot_sha256", sha256(nativeScreenshot))
                put("app_surface_screenshot", appScreenshot.name)
                put("app_surface_screenshot_sha256", sha256(appScreenshot))
                put("apk_sha256", apkSha256)
            }
            FileOutputStream(File(root, name.removeSuffix(".png") + "_metadata.json")).use { out ->
                out.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
            }
        } finally {
            if (appSurface !== rawBitmap) appSurface.recycle()
            rawBitmap.recycle()
        }
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun cropToComposeRoot(source: Bitmap): Bitmap {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
        val windowOrigin = IntArray(2)
        composeRule.runOnUiThread {
            composeRule.activity.window.decorView.getLocationOnScreen(windowOrigin)
        }
        val bounds = Rect(
            kotlin.math.floor(rootBounds.left).toInt() + windowOrigin[0],
            kotlin.math.floor(rootBounds.top).toInt() + windowOrigin[1],
            kotlin.math.ceil(rootBounds.right).toInt() + windowOrigin[0],
            kotlin.math.ceil(rootBounds.bottom).toInt() + windowOrigin[1],
        )
        check(bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= source.width && bounds.bottom <= source.height
        ) {
            "Compose root bounds $bounds exceed screenshot ${source.width}x${source.height}"
        }
        check(bounds.width() > 0 && bounds.height() > 0) {
            "Compose root has empty screenshot bounds: $bounds"
        }
        return Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
    }

    private fun swipeCarouselToSelectedSpecies(
        fromName: String,
        name: String,
        expectedPage: Int,
    ) {
        waitForSelectedSpecies(name = fromName, expectedPage = expectedPage - 1)

        val carouselBounds = composeRule.onNodeWithTag("fish_guide_carousel")
            .fetchSemanticsNode()
            .boundsInRoot
        val start = Offset(carouselBounds.width * 0.84f, carouselBounds.height * 0.5f)
        val end = Offset(carouselBounds.width * 0.16f, carouselBounds.height * 0.5f)
        composeRule.onNodeWithTag("fish_guide_carousel").performTouchInput {
            swipe(start = start, end = end, durationMillis = 1_100L)
        }

        waitForSelectedSpecies(name = name, expectedPage = expectedPage)
        composeRule.onNodeWithText("${expectedPage + 1} / ${species.size}").assertIsDisplayed()
        composeRule.onNodeWithText(name).assertIsDisplayed()
    }

    private fun waitForSelectedSpecies(name: String, expectedPage: Int) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            selectedSpeciesNode(name = name, expectedPage = expectedPage) != null
        }

        val carouselBounds = composeRule.onNodeWithTag("fish_guide_carousel")
            .fetchSemanticsNode()
            .boundsInRoot
        val activeNode = selectedSpeciesNode(name = name, expectedPage = expectedPage)
            ?: throw AssertionError(
                "$name did not expose current-selected state for page $expectedPage",
            )
        val centerDelta = abs(activeNode.boundsInRoot.center.x - carouselBounds.center.x)
        assertTrue(
            "Selected card $name is not centered: page=$expectedPage, " +
                "carouselCenterX=${carouselBounds.center.x}, cardBounds=${activeNode.boundsInRoot}, " +
                "centerDeltaX=$centerDelta",
            activeNode.boundsInRoot.width > 0f &&
                centerDelta <= activeNode.boundsInRoot.width * 0.1f,
        )
    }

    private fun selectedSpeciesNode(name: String, expectedPage: Int) =
        composeRule.onAllNodesWithContentDescription(name)
            .fetchSemanticsNodes()
            .firstOrNull { node ->
                val stateDescription = runCatching {
                    node.config[SemanticsProperties.StateDescription]
                }.getOrNull()
                val description = stateDescription.orEmpty()
                description.contains("第 ${expectedPage + 1} 种，共 ${species.size} 种") &&
                    description.contains("当前选中")
            }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
