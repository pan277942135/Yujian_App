package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/** Constraint-window evidence is Compose simulation and is reported separately from device captures. */
@RunWith(AndroidJUnit4::class)
class GeminiNormalHomeResponsiveTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun responsiveWindowAndFontScaleMatrixKeepsSectionsAndActionsUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "nh01-gemini-v3/compose-simulation")
            .apply { mkdirs() }
        val photo = File(context.cacheDir, "gemini-responsive-fish.jpg").apply {
            context.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
                outputStream().use { target -> input.copyTo(target) }
            }
        }
        val records = fixtureCatches(photo.absolutePath)
        val profile = mutableStateOf(Profile(320, 480, 1.0f, "320x480"))
        val avatarClicks = AtomicInteger()
        val cameraClicks = AtomicInteger()
        val albumClicks = AtomicInteger()
        val speciesClicks = AtomicInteger()
        val catchClicks = AtomicInteger()
        val recordClicks = AtomicInteger()

        compose.setContent {
            val window = profile.value
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = window.fontScale)) {
                Box(Modifier.requiredSize(window.widthDp.dp, window.heightDp.dp)) {
                    GeminiNormalHomeContent(
                        statistics = CatchStatistics(totalCatches = 3, speciesCount = 3),
                        recentCatches = records,
                        resolveImageUrl = { it },
                        accessToken = "",
                        isLoggedIn = false,
                        avatarUrl = null,
                        onIdentify = { cameraClicks.incrementAndGet() },
                        onLoginClick = { avatarClicks.incrementAndGet() },
                        onSpeciesClick = { speciesClicks.incrementAndGet() },
                        onCatchesClick = { albumClicks.incrementAndGet() },
                        onProfileClick = { error("Guest avatar must open login, not profile") },
                        onCatchClick = { if (it.isNotBlank()) recordClicks.incrementAndGet() },
                        motionState = rememberHomeMotionState(),
                        runtimeAssets = null,
                        safeInsets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.onNodeWithTag("gemini-home-stat-species-value", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("gemini-home-stat-catches-value", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("gemini-home-stat-record-days-value", useUnmergedTree = true).assertTextEquals("1")
        compose.onNodeWithTag("gemini-home-catch-species-gemini-fixture-01", useUnmergedTree = true)
            .assertTextEquals("草鱼")
        compose.onNodeWithTag("gemini-home-catch-measurement-gemini-fixture-01", useUnmergedTree = true)
            .assertTextEquals("42.6 cm · 1.28 kg")

        val profiles = buildList {
            listOf(320 to 480, 360 to 640, 390 to 844, 430 to 932, 480 to 840, 600 to 960).forEach { (width, height) ->
                listOf(1.0f, 1.3f, 1.5f).forEach { scale ->
                    add(Profile(width, height, scale, "${width}x${height}-font-${scaleToken(scale)}"))
                }
            }
        }
        val profileResults = JSONArray()

        profiles.forEachIndexed { index, window ->
            compose.runOnUiThread { profile.value = window }
            compose.waitForIdle()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("home-evidence-backdrop-ready", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("source-fit foreground must remain composed ${window.name}",
                compose.onAllNodesWithTag("home-evidence-fit-foreground", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())

            val root = bounds("gemini-home-screen")
            val top = bounds("gemini-home-top-section")
            val middle = bounds("gemini-home-middle-section")
            val footer = bounds("gemini-home-footer-cta")
            val camera = bounds("gemini-home-camera")
            val cameraArt = bounds("gemini-home-camera-art")
            val ctaLabel = bounds("gemini-home-cta-label")
            val sectionTitle = bounds("gemini-home-section-title")
            val activeCard = bounds("gemini-home-catch-card-gemini-fixture-01")
            val rightPeek = bounds("gemini-home-card-peek-right")
            val footerContent = bounds("gemini-home-catch-footer-gemini-fixture-01")
            val species = bounds("gemini-home-catch-species-gemini-fixture-01")
            val measurement = bounds("gemini-home-catch-measurement-gemini-fixture-01")
            val metadata = bounds("gemini-home-catch-metadata-gemini-fixture-01")
            val heroBounds = JSONObject().apply {
                put("profile", window.name)
                put("root", root.toJson())
                put("top", top.toJson())
                put("middle", middle.toJson())
                put("footer", footer.toJson())
                put("camera_target", camera.toJson())
                put("camera_art", cameraArt.toJson())
                put("cta_label", ctaLabel.toJson())
                put("section_title", sectionTitle.toJson())
                put("active_card", activeCard.toJson())
                put("right_peek", rightPeek.toJson())
                put("footer_container", footerContent.toJson())
                put("species", species.toJson())
                put("measurement", measurement.toJson())
                put("metadata", metadata.toJson())
            }
            Log.i("GEMINI_HOME_HERO_BOUNDS", heroBounds.toString())

            compose.onNodeWithTag("gemini-home-avatar-button", useUnmergedTree = true)
                .assertHasClickAction().performClick()
            compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true)
                .assertHasClickAction().performClick()
            compose.onNodeWithTag("gemini-home-stat-species", useUnmergedTree = true)
                .assertHasClickAction().performClick()
            compose.onNodeWithTag("gemini-home-section-all", useUnmergedTree = true)
                .assertHasClickAction().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, avatarClicks.get())
                assertEquals(index + 1, cameraClicks.get())
                assertEquals(index + 1, speciesClicks.get())
                assertEquals(index + 1, albumClicks.get())
            }

            // Persist screenshot and exact measured Bounds before any visual assertion so
            // a failed profile remains diagnosable in the runtime artifact.
            val image = compose.onNodeWithTag("gemini-home-screen", useUnmergedTree = true)
                .assertIsDisplayed().captureToImage().let { it.asAndroidBitmap() }
            val screenshot = File(output, "${window.name}.png")
            screenshot.outputStream().use { stream ->
                assertTrue("failed to save ${window.name}", image.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            File(output, "${window.name}.json").writeText(JSONObject().apply {
                put("profile", window.name)
                put("mode", "COMPOSE_CONSTRAINT_SIMULATION")
                put("status", "CHECKING")
                put("font_scale", window.fontScale)
                put("window_dp", JSONArray(listOf(window.widthDp, window.heightDp)))
                put("screenshot", screenshot.name)
                put("bounds_dp", heroBounds)
                put("interaction_state", "NOT_RUN_BEFORE_PROFILE_ASSERTIONS")
            }.toString(2))

            assertEquals("root width ${window.name}", window.widthDp.toFloat(), root.width(), 1f)
            assertEquals("root height ${window.name}", window.heightDp.toFloat(), root.height(), 1f)
            assertTrue("top must precede the flexible middle ${window.name}", top.bottom <= middle.top + 1f)
            assertTrue("middle must precede the footer ${window.name}", middle.bottom <= footer.top + 1f)
            val initiallyVisible = metadata.isInside(middle)
            if (initiallyVisible) {
                // boundsInRoot is clipped by the Middle scroll viewport. Check the
                // full-card ratio only when the card/footer are not viewport-clipped.
                assertEquals(
                    "active Hero card keeps the Gemini 247:293 ratio ${window.name}",
                    247f / 293f,
                    activeCard.width() / activeCard.height(),
                    0.015f,
                )
            } else {
                assertTrue(
                    "clipped Hero footer must have a Middle scroll path ${window.name}",
                    compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty(),
                )
            }
            assertEquals(
                "visible section title follows its bounded adaptive gap ${window.name}",
                resolveGeminiHomeLayoutSpec(
                    safeWidthDp = window.widthDp.toFloat(),
                    safeHeightDp = (window.heightDp - 48f).coerceAtLeast(1f),
                    fontScale = window.fontScale,
                ).titleToCardGap.value,
                activeCard.top - sectionTitle.bottom,
                1.1f,
            )
            assertEquals(
                "neighbor card top is offset 15dp ${window.name}",
                15f,
                rightPeek.top - activeCard.top,
                1.5f,
            )
            if (initiallyVisible) {
                assertEquals(
                    "neighbor card height remains 66.7% ${window.name}",
                    0.667f,
                    rightPeek.height() / activeCard.height(),
                    0.015f,
                )
            }
            val statDividers = compose.onAllNodesWithTag("gemini-home-stats-divider", useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertEquals("stats bar keeps two separators ${window.name}", 2, statDividers.size)
            statDividers.forEach { divider ->
                assertEquals("stats separator is 26dp ${window.name}", 26f, divider.boundsInRoot.height, 0.5f)
            }
            assertTrue("camera hit target must be at least 48dp ${window.name}", camera.width() >= 48f && camera.height() >= 48f)
            val cameraCenterDistance = kotlin.math.hypot(
                cameraArt.centerX() - camera.centerX(),
                cameraArt.centerY() - camera.centerY(),
            )
            assertTrue("camera art must be centered in its target ${window.name}", cameraCenterDistance <= 2f)
            assertTrue("CTA label and camera may not overlap ${window.name}", ctaLabel.bottom <= camera.top + 1f)
            assertTrue("camera must remain above the bottom safe inset ${window.name}", camera.bottom <= root.bottom - 24f + 1f)
            assertEquals(window.widthDp, image.width)
            assertEquals(window.heightDp, image.height)

            var afterScrollVisible = initiallyVisible
            var postScrollScreenshot: String? = null
            var finalActiveCard = activeCard
            var finalFooterContent = footerContent
            var finalSpecies = species
            var finalMeasurement = measurement
            var finalMetadata = metadata
            if (!initiallyVisible) {
                assertTrue(
                    "${window.name} clips Hero footer without a scroll path",
                    compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty(),
                )
                compose.onNodeWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                    .performTouchInput { repeat(3) { swipeUp() } }
                compose.waitForIdle()
                val scrolledMiddle = bounds("gemini-home-middle-section")
                finalActiveCard = bounds("gemini-home-catch-card-gemini-fixture-01")
                finalFooterContent = bounds("gemini-home-catch-footer-gemini-fixture-01")
                finalSpecies = bounds("gemini-home-catch-species-gemini-fixture-01")
                finalMeasurement = bounds("gemini-home-catch-measurement-gemini-fixture-01")
                finalMetadata = bounds("gemini-home-catch-metadata-gemini-fixture-01")
                afterScrollVisible = finalMetadata.isInside(scrolledMiddle)
                val scrolledHeroBounds = JSONObject().apply {
                    put("profile", window.name)
                    put("middle", scrolledMiddle.toJson())
                    put("active_card", finalActiveCard.toJson())
                    put("footer_container", finalFooterContent.toJson())
                    put("species", finalSpecies.toJson())
                    put("measurement", finalMeasurement.toJson())
                    put("metadata", finalMetadata.toJson())
                }
                Log.i("GEMINI_HOME_HERO_BOUNDS", "AFTER_SCROLL $scrolledHeroBounds")
                val scrolledScreenshot = File(output, "${window.name}-hero-footer-visible.png")
                writeScreenshot("gemini-home-screen", scrolledScreenshot)
                postScrollScreenshot = scrolledScreenshot.name
                File(output, "${window.name}-hero-footer-visible.json").writeText(JSONObject().apply {
                    put("profile", window.name)
                    put("mode", "COMPOSE_CONSTRAINT_SIMULATION_AFTER_SCROLL")
                    put("status", "CHECKING")
                    put("screenshot", scrolledScreenshot.name)
                    put("bounds_dp", scrolledHeroBounds)
                }.toString(2))
                assertTrue("${window.name} must scroll the whole Hero footer into view", afterScrollVisible)
            }

            val heroOffenders = listOf("species" to finalSpecies, "measurement" to finalMeasurement, "metadata" to finalMetadata)
                .filterNot { (_, bounds) -> bounds.isInside(finalActiveCard) }
                .joinToString { (name, bounds) -> "$name=$bounds" }
            val heroContentInside = heroOffenders.isEmpty()
            if (!heroContentInside) {
                Log.e("GEMINI_HOME_HERO_BOUNDS", "OUT_OF_CARD offenders=$heroOffenders card=$finalActiveCard footer=$finalFooterContent")
            }
            assertTrue("Hero footer content remains inside the card ${window.name}; offenders=$heroOffenders; card=$finalActiveCard; footer=$finalFooterContent",
                heroContentInside)

            compose.onNodeWithTag("gemini-home-catch-card-gemini-fixture-01", useUnmergedTree = true)
                .assertHasClickAction().performClick()
            compose.runOnIdle {
                assertEquals(index + 1, avatarClicks.get())
                assertEquals(index + 1, cameraClicks.get())
                assertEquals(index + 1, speciesClicks.get())
                assertEquals(index + 1, albumClicks.get())
                assertEquals(index + 1, recordClicks.get())
            }

            val result = JSONObject().apply {
                put("profile", window.name)
                put("mode", "COMPOSE_CONSTRAINT_SIMULATION")
                put("status", "PASS")
                put("font_scale", window.fontScale)
                put("density", 1.0)
                put("window_dp", JSONArray(listOf(window.widthDp, window.heightDp)))
                put("safe_insets_dp", JSONObject().put("top", 24).put("bottom", 24).put("start", 0).put("end", 0))
                put("screenshot", screenshot.name)
                put("post_scroll_screenshot", postScrollScreenshot)
                put("screenshot_dimensions_px", JSONArray(listOf(image.width, image.height)))
                put("hero_metadata_initially_visible", initiallyVisible)
                put("hero_metadata_visible_after_scroll", afterScrollVisible)
                put("scrollability", if (initiallyVisible) "NOT_REQUIRED" else "PASS")
                put("camera_touch_target_min_dp", 48)
                put("cta_camera_overlap", false)
                put("interactions", JSONObject().apply {
                    put("guest_avatar_login", "PASS")
                    put("camera_identify", "PASS")
                    put("species_stats", "PASS")
                    put("all_catches", "PASS")
                    put("catch_record", "PASS")
                })
                put("bounds_dp", JSONObject().apply {
                    put("root", root.toJson())
                    put("top", top.toJson())
                    put("middle", middle.toJson())
                    put("footer", footer.toJson())
                    put("camera_target", camera.toJson())
                    put("camera_art", cameraArt.toJson())
                    put("cta_label", ctaLabel.toJson())
                    put("initial_hero_bounds", heroBounds)
                    put("active_card", finalActiveCard.toJson())
                    put("footer_content", finalFooterContent.toJson())
                    put("species", finalSpecies.toJson())
                    put("measurement", finalMeasurement.toJson())
                    put("metadata", finalMetadata.toJson())
                })
            }
            profileResults.put(result)
            File(output, "${window.name}.json").writeText(result.toString(2))
            if (postScrollScreenshot != null) {
                File(output, "${window.name}-hero-footer-visible.json").writeText(JSONObject().apply {
                    put("profile", window.name)
                    put("mode", "COMPOSE_CONSTRAINT_SIMULATION_AFTER_SCROLL")
                    put("status", "PASS")
                    put("screenshot", postScrollScreenshot)
                    put("bounds_dp", JSONObject().apply {
                        put("active_card", finalActiveCard.toJson())
                        put("footer_container", finalFooterContent.toJson())
                        put("species", finalSpecies.toJson())
                        put("measurement", finalMeasurement.toJson())
                        put("metadata", finalMetadata.toJson())
                    })
                }.toString(2))
            }
            Log.i("GEMINI_HOME_RESPONSIVE_PROFILE", result.toString())

            if (compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                compose.onNodeWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                    .performTouchInput { repeat(3) { swipeDown() } }
            }
            image.recycle()
        }

        val pagerBeforeFinalSwipe = currentCarouselId()
        swipeOnePagerPage(forward = true)
        runCatching { compose.waitUntil(5_000) { currentCarouselId().contains("gemini-fixture-02") } }
        val pagerAfterFinalSwipe = currentCarouselId()
        if (!pagerAfterFinalSwipe.contains("gemini-fixture-02")) {
            val diagnostic = JSONObject().apply {
                put("schema", "NH01_GEMINI_FINAL_SWIPE_FAILURE_V1")
                put("status", "FAIL")
                put("pager_before", pagerBeforeFinalSwipe)
                put("pager_after", pagerAfterFinalSwipe)
                put("pager_bounds_dp", bounds("gemini-home-catch-pager").toJson())
                put("profile", profiles.last().name)
                put("note", "Swipe is issued through the active hero card interior to avoid clipped viewport edge coordinates.")
            }
            File(output, "final-pager-swipe-failure.json").writeText(diagnostic.toString(2))
            writeScreenshot("gemini-home-screen", File(output, "final-pager-swipe-failure.png"))
        }
        assertTrue(
            "horizontal carousel must expose the next card after a horizontal swipe; before=$pagerBeforeFinalSwipe after=$pagerAfterFinalSwipe",
            pagerAfterFinalSwipe.contains("gemini-fixture-02"),
        )
        assertEquals(profiles.size, profileResults.length())

        val report = JSONObject().apply {
            put("schema", "NH01_GEMINI_RESPONSIVE_COMPOSE_REPORT_V3")
            put("test", "GeminiNormalHomeResponsiveTest.responsiveWindowAndFontScaleMatrixKeepsSectionsAndActionsUsable")
            put("mode", "COMPOSE_CONSTRAINT_SIMULATION")
            put("device_api_level", Build.VERSION.SDK_INT)
            put("device_capture", "NOT_RUN_BY_THIS_TEST")
            put("business_fixture", JSONObject().put("species_count", 3).put("catch_count", 3).put("record_days", 1)
                .put("first_catch", "草鱼").put("length_cm", 42.6).put("weight_kg", 1.28).put("location", "浙江 · 千岛湖"))
            put("fish_photo_source", "home_normal/fish_record/sample_recent_catch.jpg")
            put("fish_photo_render_mode", "FIT_WITH_BLURRED_BACKGROUND")
            put("profiles", profileResults)
            put("horizontal_pager_swipe", "PASS")
            put("profile_count", profiles.size)
        }
        File(output, "gemini_responsive_compose_report.json").writeText(report.toString(2))
        photo.delete()
    }

    @Test
    fun finiteCarouselPageCountsIdentityAndDataChangesStayBoundToRealCatches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "nh01-gemini-v3/compose-simulation/carousel")
            .apply { mkdirs() }
        val records = mutableStateOf(fixtureCatches(null, count = 3))
        val clickedIds = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(390.dp, 844.dp)) {
                    GeminiNormalHomeContent(
                        statistics = CatchStatistics(),
                        recentCatches = records.value,
                        resolveImageUrl = { null },
                        accessToken = "",
                        isLoggedIn = false,
                        avatarUrl = null,
                        onIdentify = {},
                        onLoginClick = {},
                        onSpeciesClick = {},
                        onCatchesClick = {},
                        onProfileClick = {},
                        onCatchClick = { clickedIds += it },
                        motionState = rememberHomeMotionState(),
                        runtimeAssets = null,
                        safeInsets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val countCases = JSONArray()
        listOf(0, 1, 2, 3, 10).forEach { count ->
            val source = fixtureCatches(null, count = count)
            compose.runOnUiThread { records.value = source }
            compose.waitForIdle()
            val orderedIds = orderedHomeRecords(source).map(RemoteCatch::id)
            val carouselTag = if (count == 0) "gemini-home-empty-carousel" else "gemini-home-catch-pager"
            val carouselNode = compose.onNodeWithTag(carouselTag, useUnmergedTree = true).fetchSemanticsNode()
            val columns = carouselNode.config[SemanticsProperties.CollectionInfo].columnCount
            assertEquals("carousel must expose one finite page per valid unique catch", count, columns)

            if (count == 0) {
                val emptyShot = File(output, "carousel_0.png")
                writeScreenshot("gemini-home-screen", emptyShot)
                countCases.put(JSONObject().apply {
                    put("catch_count", count)
                    put("page_count", columns)
                    put("current_catch_id", JSONObject.NULL)
                    put("screenshot", emptyShot.name)
                    put("bounds_dp", bounds("gemini-home-empty-carousel").toJson())
                    put("interaction", "EMPTY_STATE_PASS")
                })
                return@forEach
            }

            val pageTrace = JSONArray()
            val pageScreenshotNames = JSONArray()
            orderedIds.forEachIndexed { index, expectedId ->
                val state = compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                    .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
                assertTrue("pager must select the real ordered id $expectedId; state=$state", state.contains(expectedId))
                compose.onNodeWithTag("gemini-home-catch-card-$expectedId", useUnmergedTree = true)
                    .assertHasClickAction().performClick()
                compose.runOnIdle { assertEquals(expectedId, clickedIds.last()) }
                val pageScreenshot = File(output, "carousel_${count}_page_${index + 1}.png")
                writeScreenshot("gemini-home-screen", pageScreenshot)
                pageScreenshotNames.put(pageScreenshot.name)
                pageTrace.put(JSONObject().put("page", index).put("catch_id", expectedId)
                    .put("click_id", clickedIds.last()).put("state_before_swipe", state)
                    .put("screenshot", pageScreenshot.name))
                File(output, "carousel_${count}_progress.json").writeText(JSONObject().apply {
                    put("catch_count", count)
                    put("page_count", columns)
                    put("page_trace", pageTrace)
                    put("page_screenshots", pageScreenshotNames)
                    put("status", "IN_PROGRESS")
                }.toString(2))
                if (index < orderedIds.lastIndex) {
                    val expectedNextId = orderedIds[index + 1]
                    Log.i("GEMINI_HOME_CAROUSEL_PAGE", "SWIPE_START count=$count from=$expectedId to=$expectedNextId state=$state")
                    swipeOnePagerPage(forward = true)
                    compose.waitForIdle()
                    try {
                        compose.waitUntil(5_000) {
                            currentCarouselId().contains(expectedNextId)
                        }
                    } catch (failure: Throwable) {
                        val stateAfterSwipe = currentCarouselId()
                        val failedShot = File(output, "carousel_${count}_swipe_${index + 1}_failed.png")
                        writeScreenshot("gemini-home-screen", failedShot)
                        val diagnostic = JSONObject().apply {
                            put("catch_count", count)
                            put("page_count", columns)
                            put("from_catch_id", expectedId)
                            put("expected_catch_id", expectedNextId)
                            put("state_before_swipe", state)
                            put("state_after_swipe", stateAfterSwipe)
                            put("screenshot", failedShot.name)
                            put("page_trace", pageTrace)
                        }
                        File(output, "carousel_${count}_swipe_${index + 1}_failed.json").writeText(diagnostic.toString(2))
                        Log.e("GEMINI_HOME_CAROUSEL_PAGE", "SWIPE_FAILED $diagnostic", failure)
                        throw AssertionError(
                            "finite carousel swipe count=$count from=$expectedId to=$expectedNextId; state=$stateAfterSwipe",
                            failure,
                        )
                    }
                    compose.waitForIdle()
                    val stateAfterSwipe = currentCarouselId()
                    Log.i("GEMINI_HOME_CAROUSEL_PAGE", "SWIPE_PASS count=$count from=$expectedId to=$expectedNextId state=$stateAfterSwipe")
                    assertTrue("pager state must settle on the real next catch $expectedNextId; state=$stateAfterSwipe",
                        stateAfterSwipe.contains(expectedNextId))
                }
            }
            val lastId = orderedIds.last()
            swipeOnePagerPage(forward = true)
            compose.waitForIdle()
            assertTrue("finite pager end may not clone-wrap to the first item", currentCarouselId().contains(lastId))

            if (count > 1) {
                repeat(count - 1) {
                    swipeOnePagerPage(forward = false)
                    compose.waitForIdle()
                }
                val firstId = orderedIds.first()
                assertTrue("previous-page mapping must return the immediately preceding real catch", currentCarouselId().contains(firstId))
                swipeOnePagerPage(forward = false)
                compose.waitForIdle()
                assertTrue("finite pager start may not clone-wrap to the last item", currentCarouselId().contains(firstId))
            }

            val screenshot = File(output, "carousel_${count}.png")
            writeScreenshot("gemini-home-screen", screenshot)
            countCases.put(JSONObject().apply {
                put("catch_count", count)
                put("page_count", columns)
                put("first_catch_id", orderedIds.first())
                put("last_catch_id", orderedIds.last())
                put("page_trace", pageTrace)
                put("screenshot", screenshot.name)
                put("page_screenshots", pageScreenshotNames)
                put("bounds_dp", JSONObject().apply {
                    put("pager", bounds("gemini-home-catch-pager").toJson())
                    put("selected_card", bounds("gemini-home-catch-card-${orderedIds.first()}").toJson())
                })
                put("click_mapping", "PASS")
                put("finite_previous_next_mapping", "PASS")
            })
            File(output, "carousel_${count}_progress.json").writeText(JSONObject().apply {
                put("catch_count", count)
                put("page_count", columns)
                put("page_trace", pageTrace)
                put("page_screenshots", pageScreenshotNames)
                put("status", "PASS")
            }.toString(2))
        }

        // Adding a newer catch preserves the visible catch by stable id. Deleting it
        // updates count and selected id to an item that still exists in the collection.
        compose.runOnUiThread { records.value = fixtureCatches(null, count = 3) }
        compose.waitForIdle()
        swipeOnePagerPage(forward = true)
        compose.waitForIdle()
        val selectedBeforeInsert = currentCarouselId().substringAfter("当前鱼获 ").substringBefore('，')
        val insertedRecords = fixtureCatches(null, count = 4).map { item ->
            if (item.id.endsWith("04")) item.copy(
                capturedAt = "2027-01-01T00:00:00Z",
                createdAt = "2027-01-01T00:00:00Z",
            ) else item
        }
        compose.runOnUiThread { records.value = insertedRecords }
        compose.waitForIdle()
        assertTrue("insert/reorder must retain selection by stable catch_id", currentCarouselId().contains(selectedBeforeInsert))
        val selectedForDelete = selectedBeforeInsert
        compose.runOnUiThread { records.value = insertedRecords.filterNot { it.id == selectedForDelete } }
        compose.waitForIdle()
        val remainingIds = orderedHomeRecords(records.value).map(RemoteCatch::id)
        val selectedAfterDelete = currentCarouselId().substringAfter("当前鱼获 ").substringBefore('，')
        assertTrue("after deleting the selected record the pager must resolve to a live catch", selectedAfterDelete in remainingIds)
        assertEquals(3, compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].columnCount)
        val duplicateSource = fixtureCatches(null, count = 3) + fixtureCatches(null, count = 1).first().copy(
            capturedAt = "2027-02-01T00:00:00Z",
            createdAt = "2027-02-01T00:00:00Z",
            imageUrl = "duplicate-latest.jpg",
        )
        compose.runOnUiThread { records.value = duplicateSource }
        compose.waitForIdle()
        assertEquals("same stable catch_id must occupy one page", 3, compose.onNodeWithTag(
            "gemini-home-catch-pager", useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].columnCount)

        val report = JSONObject().apply {
            put("schema", "NH01_GEMINI_CAROUSEL_COUNT_REPORT_V3")
            put("test", "GeminiNormalHomeResponsiveTest.finiteCarouselPageCountsIdentityAndDataChangesStayBoundToRealCatches")
            put("mode", "COMPOSE_CONSTRAINT_SIMULATION")
            put("window_dp", JSONArray(listOf(390, 844)))
            put("font_scale", 1.0)
            put("count_cases", countCases)
            put("insert_keeps_selected_catch_id", "PASS")
            put("delete_keeps_selection_on_live_id", "PASS")
            put("duplicate_ids_deduplicated_by_ordered_home_records", "PASS_COMPOSE_AND_UNIT_TEST")
        }
        File(output, "gemini_catch_carousel_count_report.json").writeText(report.toString(2))
        Log.i("GEMINI_CAROUSEL_COUNT_REPORT", report.toString())
    }

    @Test
    fun approvedAvatarFallbackAndAccountTransitionsNeverRetainOldAvatar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "nh01-gemini-v3/avatar")
            .apply { mkdirs() }
        val userAvatar = avatarFixture(context.cacheDir, "gemini-user-a.png", android.graphics.Color.MAGENTA)
        val failedAvatar = File(context.cacheDir, "gemini-user-b-missing.png")
        val auth = mutableStateOf(AvatarProfile(isLoggedIn = true, avatarUrl = userAvatar.absolutePath))
        val profileClicks = AtomicInteger()
        val loginClicks = AtomicInteger()

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.0f)) {
                Box(Modifier.requiredSize(390.dp, 844.dp)) {
                    GeminiNormalHomeContent(
                        statistics = CatchStatistics(totalCatches = 38, speciesCount = 12),
                        recentCatches = fixtureCatches(null),
                        resolveImageUrl = { it },
                        accessToken = "token-a",
                        isLoggedIn = auth.value.isLoggedIn,
                        avatarUrl = auth.value.avatarUrl,
                        onIdentify = {},
                        onLoginClick = { loginClicks.incrementAndGet() },
                        onSpeciesClick = {},
                        onCatchesClick = {},
                        onProfileClick = { profileClicks.incrementAndGet() },
                        onCatchClick = {},
                        motionState = rememberHomeMotionState(),
                        runtimeAssets = null,
                        safeInsets = SafeDrawingInsetsDp(top = 24.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.waitUntil(10_000) {
            val state = compose.onNodeWithTag("gemini-home-avatar-button", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            state == "用户头像"
        }
        compose.onNodeWithTag("gemini-home-avatar-button", useUnmergedTree = true)
            .assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, profileClicks.get()) }
        val loadedImage = compose.onNodeWithTag("gemini-home-avatar-visual", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        val center = loadedImage[loadedImage.width / 2, loadedImage.height / 2]
        assertTrue("real user avatar must replace the V2 placeholder after successful load", center.red > 0.8f && center.blue > 0.8f)

        auth.value = AvatarProfile(isLoggedIn = true, avatarUrl = failedAvatar.absolutePath)
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("gemini-home-avatar-button", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "默认头像"
        }
        assertEquals(0, compose.onAllNodesWithTag("gemini-home-remote-avatar", useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        compose.onNodeWithTag("gemini-home-default-avatar-v2", useUnmergedTree = true).assertIsDisplayed()
        val fallback = compose.onNodeWithTag("gemini-home-avatar-visual", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        val buttonBounds = bounds("gemini-home-avatar-button")
        val visualBounds = bounds("gemini-home-avatar-visual")
        assertTrue("avatar action target must be at least 48dp", buttonBounds.width() >= 48f && buttonBounds.height() >= 48f)
        assertTrue("avatar visual must stay within its independent action target", visualBounds.isInside(buttonBounds))
        val fallbackPixels = fallback.asImageBitmap().toPixelMap()
        val centerPixel = fallbackPixels[fallbackPixels.width / 2, fallbackPixels.height / 2]
        val cornerPixel = fallbackPixels[0, 0]
        val edgeDistance = kotlin.math.abs(centerPixel.red - cornerPixel.red) +
            kotlin.math.abs(centerPixel.green - cornerPixel.green) + kotlin.math.abs(centerPixel.blue - cornerPixel.blue)
        assertTrue("avatar visual must retain its circular edge boundary", edgeDistance > 0.08f)
        val fallbackFile = File(output, "gemini_signed_in_failed_avatar_fallback.png")
        fallbackFile.outputStream().use { assertTrue(fallback.compress(Bitmap.CompressFormat.PNG, 100, it)) }

        auth.value = AvatarProfile(isLoggedIn = false, avatarUrl = null)
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("gemini-home-remote-avatar", useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        compose.onNodeWithTag("gemini-home-default-avatar-v2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gemini-home-avatar-button", useUnmergedTree = true)
            .assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals("logout/user-switch must drop the previous remote avatar", 1, loginClicks.get())
            assertEquals("Guest tap must not be treated as profile", 1, profileClicks.get())
        }

        val report = JSONObject().apply {
            put("schema", "NH01_GEMINI_AVATAR_TRANSITION_REPORT_V3")
            put("mode", "COMPOSE_CONSTRAINT_SIMULATION")
            put("avatar_asset", "drawable/normal_home_default_avatar_v2.png")
            put("avatar_asset_sha256", "fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528")
            put("avatar_visual_crop", "CIRCLE_CLIP_CONTENT_SCALE_FIT")
            put("avatar_visual_bounds_dp", visualBounds.toJson())
            put("avatar_button_bounds_dp", buttonBounds.toJson())
            put("user_avatar_load", "PASS")
            put("user_avatar_failure_fallback", "PASS")
            put("account_switch_clears_previous_avatar", "PASS")
            put("logout_clears_previous_avatar", "PASS")
            put("guest_click", "LOGIN_PASS")
            put("signed_in_click", "PROFILE_PASS")
            put("avatar_screenshot", fallbackFile.name)
        }
        File(output, "gemini_avatar_transition_report.json").writeText(report.toString(2))
        Log.i("GEMINI_HOME_AVATAR_TRANSITIONS", report.toString())
        fallback.recycle()
        userAvatar.delete()
    }

    private fun fixtureCatches(imagePath: String?, count: Int = 3): List<RemoteCatch> {
        val names = listOf("草鱼", "鲤鱼", "鲫鱼", "黑鱼", "鳜鱼", "鲢鱼", "鳙鱼", "鲈鱼", "鲶鱼", "黄颡鱼", "青鱼", "鲑鱼")
        val firstDay = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (0 until count).map { index ->
            val dayOffset = 1
            val time = (firstDay.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -(dayOffset - 1))
                if (index == 1) add(Calendar.MINUTE, -60)
                else if (index > 1) {
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 55 - (index % 50))
                }
            }.time
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.format(time)
            RemoteCatch(
                id = "gemini-fixture-${(index + 1).toString().padStart(2, '0')}",
                imageUrl = imagePath.orEmpty(),
                speciesId = "species-${index % 12}",
                speciesName = names[index % names.size],
                confidence = 0.92f,
                modelVersion = "nh01-gemini-responsive-fixture",
                capturedAt = timestamp,
                createdAt = timestamp,
                lengthCm = if (index == 0) 42.6f else 28f + index,
                weightKg = if (index == 0) 1.28f else 0.75f + index * 0.05f,
                location = if (index == 0) "浙江 · 千岛湖" else "清晨湖畔",
            )
        }
    }

    private fun avatarFixture(directory: File, name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val target = File(directory, name)
        target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return target
    }

    private fun bounds(tag: String): RectF {
        val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected a semantics node for $tag", nodes.isNotEmpty())
        val node = if (tag == "gemini-home-card-peek-right" && nodes.size > 1) {
            // HorizontalPager may keep more than one off-screen forward page composed.
            // The responsive geometry contract is for the immediate neighbor beside the active card.
            val active = compose.onNodeWithTag(
                "gemini-home-catch-card-gemini-fixture-01", useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val activeCenterX = (active.left + active.right) / 2f
            nodes.minBy { candidate ->
                kotlin.math.abs((candidate.boundsInRoot.left + candidate.boundsInRoot.right) / 2f - activeCenterX)
            }
        } else {
            assertEquals("expected exactly one semantics node for $tag", 1, nodes.size)
            nodes.single()
        }
        val rect = node.boundsInRoot
        return RectF(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun currentCarouselId(): String = compose.onNodeWithTag(
        "gemini-home-catch-pager", useUnmergedTree = true,
    ).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun swipeOnePagerPage(forward: Boolean) {
        val tag = "gemini-home-catch-pager"
        val carouselBounds = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // Move far enough to cross the pager threshold: the hero page is about
        // 76% of the track, so 66% -> 34% only travels ~42% of a page. At 75%
        // -> 25% the gesture exceeds half a page while staying in the active
        // hero and inside the 480dp API 28 window for the 600dp fixture.
        val startX = if (forward) carouselBounds.width * 0.75f else carouselBounds.width * 0.25f
        val endX = if (forward) carouselBounds.width * 0.25f else carouselBounds.width * 0.75f
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(startX, carouselBounds.height * 0.5f),
                end = Offset(endX, carouselBounds.height * 0.5f),
                durationMillis = 1_100L,
            )
        }
    }

    private fun writeScreenshot(tag: String, output: File) {
        val image = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assertIsDisplayed().captureToImage().asAndroidBitmap()
        output.outputStream().use { stream ->
            assertTrue("failed to save ${output.name}", image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        image.recycle()
    }

    private fun RectF.isInside(parent: RectF): Boolean =
        left >= parent.left - 1f && top >= parent.top - 1f && right <= parent.right + 1f && bottom <= parent.bottom + 1f

    private fun RectF.toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
        put("width", width())
        put("height", height())
    }

    private fun scaleToken(scale: Float): String = String.format(Locale.US, "%.1f", scale).replace('.', '_')

    private data class Profile(val widthDp: Int, val heightDp: Int, val fontScale: Float, val name: String)

    private data class AvatarProfile(val isLoggedIn: Boolean, val avatarUrl: String?)
}
