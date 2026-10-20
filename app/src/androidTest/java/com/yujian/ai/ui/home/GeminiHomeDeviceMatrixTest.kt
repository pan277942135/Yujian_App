package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.DisplayMetrics
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.screens.HomeScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Actual API 28 window capture, using the installed app's production HomeScreen and real system density. */
@RunWith(AndroidJUnit4::class)
class GeminiHomeDeviceMatrixTest {
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var activeProfileId = "UNKNOWN"
    private var activeVariantName = "UNKNOWN"
    private var activeCaseOutput: File? = null
    private var sourceSha = "UNKNOWN_SOURCE_SHA"
    private var expectedFontScale = "UNKNOWN_FONT_SCALE"
    private var currentStep = "TEST_RULE_START"
    private var lastSuccessfulStep = "TEST_NOT_STARTED"
    private var startedAtEpochMs = 0L

    private val evidenceWatcher = object : TestWatcher() {
        override fun starting(description: Description) {
            val args = InstrumentationRegistry.getArguments()
            activeProfileId = args.getString("profile_id") ?: "UNKNOWN"
            activeVariantName = args.getString("layout_variant") ?: "UNKNOWN"
            sourceSha = args.getString("source_sha") ?: "UNKNOWN_SOURCE_SHA"
            expectedFontScale = args.getString("font_scale") ?: "UNKNOWN_FONT_SCALE"
            startedAtEpochMs = System.currentTimeMillis()
            currentStep = "TEST_BODY_START"
            lastSuccessfulStep = "TEST_RULE_STARTED"
            activeCaseOutput = runCatching {
                File(
                    requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
                    "nh01-adaptive-visual-balance-v1",
                ).apply { mkdirs() }
            }.getOrNull()
            caseReportFile()?.delete()
            writeCaseStatus("RUNNING", currentStep)
            Log.i("NH01_MATRIX_CASE", "START profile=$activeProfileId variant=$activeVariantName sha=$sourceSha")
        }

        override fun succeeded(description: Description) {
            val report = caseReportFile()?.let { file ->
                runCatching { JSONObject(file.readText()) }.getOrNull()
            }
            if (report?.optString("status") != "PASS") {
                writeCaseStatus(
                    status = "FAIL",
                    stage = "TEST_RULE_SUCCESS_WITHOUT_PASS_REPORT",
                    failure = AssertionError("Instrumentation completed without a PASS case report"),
                )
            }
        }

        override fun failed(e: Throwable, description: Description) {
            writeCaseStatus("FAIL", currentStep, e)
            captureFailureScreenshot()
            Log.e(
                "NH01_MATRIX_CASE",
                "FAIL profile=$activeProfileId variant=$activeVariantName stage=$currentStep " +
                    "last_successful_step=$lastSuccessfulStep",
                e,
            )
        }
    }

    @get:Rule
    val rules = RuleChain.outerRule(evidenceWatcher).around(compose)

    @Test
    fun capturesActualWindowWithTwoUniqueCatches() {
        val args = InstrumentationRegistry.getArguments()
        val profileId = requireNotNull(args.getString("profile_id")) { "profile_id is required" }
        val variant = GeminiHomeLayoutVariant.valueOf(
            args.getString("layout_variant") ?: GeminiHomeLayoutVariant.ADAPTIVE_V1.name,
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(targetContext.getExternalFilesDir(null)), "nh01-adaptive-visual-balance-v1")
            .apply { mkdirs() }
        val photo = File(targetContext.cacheDir, "nh01-adaptive-two-catch-fixture.jpg").apply {
            targetContext.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
                outputStream().use { sink -> input.copyTo(sink) }
            }
        }
        val catches = fixtureCatches(photo.absolutePath)
        activeProfileId = profileId
        activeVariantName = variant.name
        sourceSha = args.getString("source_sha") ?: "UNKNOWN_SOURCE_SHA"
        expectedFontScale = args.getString("font_scale") ?: "UNKNOWN_FONT_SCALE"
        activeCaseOutput = output
        completeStep("ARGUMENTS_AND_TWO_CATCH_FIXTURE_READY")
        val densityProbe = AtomicReference<RuntimeDensityProbe?>()
        val identifyClicks = AtomicInteger()
        val albumClicks = AtomicInteger()
        val catchClicks = mutableListOf<String>()

        // The image decoder and derivative renderer complete asynchronously and
        // their Compose state needs frames to publish the rendered semantics.
        // Keep the test clock advancing until the image is ready; freeze it only
        // for the deterministic pager gesture section.
        compose.mainClock.autoAdvance = true
        beginStep("COMPOSE_PRODUCTION_HOME_SCREEN")
        compose.setContent {
            val density = LocalDensity.current
            val insets = rememberSafeDrawingInsets()
            SideEffect {
                densityProbe.set(
                    RuntimeDensityProbe(
                        density = density.density,
                        fontScale = density.fontScale,
                        insets = insets,
                    ),
                )
            }
            HomeScreen(
                nickname = "",
                statistics = CatchStatistics(totalCatches = 2, speciesCount = 2),
                recentCatches = catches,
                resolveImageUrl = { it },
                accessToken = "",
                isLoggedIn = false,
                avatarUrl = null,
                showEmptyState = false,
                isResolving = false,
                onIdentify = { identifyClicks.incrementAndGet() },
                onAlbumClick = { albumClicks.incrementAndGet() },
                onLoginClick = {},
                onSpeciesClick = {},
                onCatchesClick = { albumClicks.incrementAndGet() },
                onProfileClick = {},
                onCatchClick = { catchClicks += it },
                layoutVariant = variant,
            )
        }
        compose.waitForIdle()
        completeStep("PRODUCTION_HOME_SCREEN_COMPOSED")

        beginStep("WAIT_FOR_MAIN_CATCH_CARD")
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        completeStep("MAIN_CATCH_CARD_COMPOSED")

        beginStep("WAIT_FOR_FISH_PHOTO_RENDER")
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("home-evidence-backdrop-ready", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        completeStep("FISH_PHOTO_RENDERED")
        compose.mainClock.autoAdvance = false

        val profile = requireNotNull(densityProbe.get()) { "Compose density/insets were not observed" }
        val expectedFontScaleValue = args.getString("font_scale")?.toFloatOrNull()
            ?: throw AssertionError("font_scale is required")
        beginStep("ASSERT_FONT_SCALE_AND_REQUIRED_CONTENT")
        assertEquals("system font_scale must reach Compose unchanged", expectedFontScaleValue, profile.fontScale, 0.02f)
        compose.onNodeWithTag("gemini-home-stat-catches-value", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithTag("gemini-home-catch-species-nh01-catch-001", useUnmergedTree = true)
            .assertTextEquals("草鱼")
        compose.onNodeWithTag("gemini-home-catch-measurement-nh01-catch-001", useUnmergedTree = true)
            .assertTextEquals("42.6 cm · 1.28 kg")
        compose.onNodeWithTag("gemini-home-catch-metadata-nh01-catch-001", useUnmergedTree = true)
            .assertTextContains("05月29日 04:26 · 浙江 · 千岛湖")
        compose.onNodeWithText("记录下一条鱼", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true).assertIsDisplayed().assertHasClickAction()
        completeStep("REQUIRED_CONTENT_AND_CAMERA_ACTION_VISIBLE")

        beginStep("ASSERT_TWO_CATCH_IDENTITY_AND_PAGER_COUNT")
        val pager = compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("two real catches must be exactly two finite pager pages", 2,
            pager.config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].columnCount)
        val pageState = pager.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue("initial page is the first real stable id: $pageState", pageState.contains("nh01-catch-001"))
        assertSingleNode("gemini-home-catch-card-nh01-catch-001")
        assertSingleNode("gemini-home-catch-neighbor-nh01-catch-002")
        assertEquals("the second catch may not be duplicated as a main card", 0,
            compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-002", useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        assertEquals("the first catch may not be duplicated as a neighbor", 0,
            compose.onAllNodesWithTag("gemini-home-catch-neighbor-nh01-catch-001", useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        completeStep("TWO_CATCH_UNIQUENESS_ASSERTIONS_PASSED")

        beginStep("CAPTURE_COMPONENT_BOUNDS_AND_LAYOUT_METRICS")
        val tags = listOf(
            "gemini-home-screen", "gemini-home-brand-title", "gemini-home-header-bar", "gemini-home-stats-bar",
            "gemini-home-section-title", "gemini-home-middle-section", "gemini-home-carousel-track",
            "gemini-home-catch-card-nh01-catch-001", "gemini-home-catch-neighbor-nh01-catch-002",
            "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera",
            "gemini-home-catch-species-nh01-catch-001", "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        val boundsPx = JSONObject().apply { tags.forEach { put(it, rect(it).toJson()) } }
        val root = rect("gemini-home-screen").toDp(profile.density)
        val track = rect("gemini-home-carousel-track").toDp(profile.density)
        val safeWidthDp = (root.width() - profile.insets.start.value - profile.insets.end.value).coerceAtLeast(1f)
        val safeHeightDp = (root.height() - profile.insets.top.value - profile.insets.bottom.value).coerceAtLeast(1f)
        val layoutSpec = resolveGeminiHomeLayoutSpec(safeWidthDp, safeHeightDp, profile.fontScale, variant)
        val header = rect("gemini-home-header-bar").toDp(profile.density)
        val activeNodeSize = compose.onNodeWithTag(
            "gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true,
        ).fetchSemanticsNode().size
        val activeCardLayoutWidthDp = activeNodeSize.width / profile.density
        val activeCardLayoutHeightDp = activeNodeSize.height / profile.density
        val activeCard = rect("gemini-home-catch-card-nh01-catch-001").toDp(profile.density)
        val neighborCard = rect("gemini-home-catch-neighbor-nh01-catch-002").toDp(profile.density)
        val title = rect("gemini-home-section-title").toDp(profile.density)
        val footer = rect("gemini-home-footer-cta").toDp(profile.density)
        val cta = rect("gemini-home-cta-label").toDp(profile.density)
        val camera = rect("gemini-home-camera").toDp(profile.density)
        val brand = rect("gemini-home-brand-title").toDp(profile.density)
        val stats = rect("gemini-home-stats-bar").toDp(profile.density)
        val sectionTitleCardGap = activeCard.top - title.bottom
        val statsToTitleGap = title.top - stats.bottom
        val headerToStatsGap = stats.top - header.bottom
        val centerOffset = abs(activeCard.centerX() - track.centerX())
        val ratio = activeCardLayoutWidthDp / activeCardLayoutHeightDp
        val cardWidthFraction = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) 0.76f else 0.686f
        val cardWidthCap = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) 360f else 337.2f
        val targetCardWidthDp = minOf(minOf(track.width(), 560f) * cardWidthFraction, cardWidthCap)

        assertTrue("card aspect ratio stays within 1% of the frozen 247:293 contract: $ratio",
            abs(ratio / (247f / 293f) - 1f) <= 0.01f)
        assertTrue("main card stays horizontally centered, offset=$centerOffset dp", centerOffset <= 2f)
        assertTrue("measured card may shrink under height pressure but may not exceed its policy target",
            activeCardLayoutWidthDp <= targetCardWidthDp + 1f)
        assertTrue("card bounds remain inside the pager track", activeCard.left >= track.left - 1f && activeCard.right <= track.right + 1f)
        assertTrue("title-to-card gap follows the active layout policy: $sectionTitleCardGap dp",
            abs(sectionTitleCardGap - layoutSpec.titleToCardGap.value) <= 2f)
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) {
            assertTrue("stats-to-title visual gap follows the active layout policy: $statsToTitleGap dp",
                abs(statsToTitleGap - requireNotNull(layoutSpec.statsToTitleGap).value) <= 2f)
            assertTrue("brand-to-stats gap follows the active layout policy: $headerToStatsGap dp",
                abs(headerToStatsGap - requireNotNull(layoutSpec.brandToStatsGap).value) <= 2f)
        }
        assertTrue("camera hit target is at least 48dp", camera.width() >= 48f && camera.height() >= 48f)
        assertTrue("CTA and camera must not overlap", cta.bottom <= camera.top + 1f)
        assertTrue("camera stays above the actual bottom safe inset",
            camera.bottom <= root.bottom - profile.insets.bottom.value + 1f)
        assertTrue("footer remains on screen", footer.bottom <= root.bottom - profile.insets.bottom.value + 1f)
        completeStep("BOUNDS_AND_LAYOUT_ASSERTIONS_PASSED")

        beginStep("CAPTURE_INITIAL_ANDROID_SCREENSHOT")
        val windowManager = compose.activity.windowManager
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        assertEquals("the runtime matrix is pinned to API 28", 28, Build.VERSION.SDK_INT)
        assertEquals("logical matrix density is 160 dpi", 160, displayMetrics.densityDpi)
        val expectedDisplay = requireNotNull(args.getString("expected_display_px"))
            .split("x").map { it.toInt() }
        assertEquals("Android must apply the requested physical pixel width", expectedDisplay[0], displayMetrics.widthPixels)
        assertEquals("Android must apply the requested physical pixel height", expectedDisplay[1], displayMetrics.heightPixels)
        val imageCapture = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val screenshotName = "$profileId-${variant.name.lowercase(Locale.US)}.png"
        val screenshot = File(output, screenshotName)
        screenshot.outputStream().use { sink -> assertTrue("screenshot must save", imageCapture.compress(Bitmap.CompressFormat.PNG, 100, sink)) }
        imageCapture.recycle()
        completeStep("INITIAL_SCREENSHOT_CAPTURED")

        beginStep("VERIFY_CTA_AND_CATCH_INTERACTIONS")
        compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true).performClick()
        assertEquals("camera action remains available", 1, identifyClicks.get())
        compose.onNodeWithTag("gemini-home-section-all", useUnmergedTree = true).performClick()
        assertEquals("all catches action remains available", 1, albumClicks.get())
        compose.onNodeWithTag("gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true).performClick()
        assertEquals("main card click maps to the real catch id", listOf("nh01-catch-001"), catchClicks)
        completeStep("CTA_AND_CATCH_INTERACTIONS_PASSED")

        beginStep("VERIFY_CARD_TEXT_ACCESSIBILITY_AND_SCROLL")
        val hasMiddleScroll = compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        val cardTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        cardTextTags.forEach { tag ->
            val interaction = compose.onNodeWithTag(tag, useUnmergedTree = true)
            if (hasMiddleScroll) interaction.performScrollTo().assertIsDisplayed() else interaction.assertIsDisplayed()
        }
        val contentScreenshotName = if (hasMiddleScroll) "$profileId-${variant.name.lowercase(Locale.US)}-content-visible.png" else null
        if (contentScreenshotName != null) {
            val contentBitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(output, contentScreenshotName).outputStream().use { sink ->
                assertTrue(contentBitmap.compress(Bitmap.CompressFormat.PNG, 100, sink))
            }
            contentBitmap.recycle()
        }
        completeStep("CARD_TEXT_ACCESSIBILITY_PASSED")

        beginStep("VERIFY_HORIZONTAL_SWIPE_TO_SECOND_REAL_CATCH")
        compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true).performTouchInput { swipeLeft() }
        advanceFramesUntil(8_000) {
            compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
                .contains("nh01-catch-002")
        }
        assertSingleNode("gemini-home-catch-card-nh01-catch-002")
        assertSingleNode("gemini-home-catch-neighbor-nh01-catch-001")
        val afterSwipe = File(output, "$profileId-${variant.name.lowercase(Locale.US)}-after-swipe.png")
        val afterSwipeBitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        afterSwipe.outputStream().use { sink -> assertTrue(afterSwipeBitmap.compress(Bitmap.CompressFormat.PNG, 100, sink)) }
        afterSwipeBitmap.recycle()
        completeStep("HORIZONTAL_SWIPE_AND_SECOND_SCREENSHOT_PASSED")

        val density = compose.activity.resources.displayMetrics.density
        val config = compose.activity.resources.configuration
        val ratioErrorPercent = abs(ratio / (247f / 293f) - 1f) * 100f
        val report = JSONObject().apply {
            put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_DEVICE_CASE_V1")
            put("execution_mode", "ANDROID_API28_RUNTIME")
            put("status", "PASS")
            put("profile_id", profileId)
            put("layout_variant", variant.name)
            put("source_commit", args.getString("source_sha") ?: "MISSING_SOURCE_SHA")
            put("target_application_id", targetContext.packageName)
            put("external_files_directory", output.absolutePath)
            put("system_font_scale", profile.fontScale)
            put("density", density)
            put("density_dpi", displayMetrics.densityDpi)
            put("display_resolution_px", JSONArray(listOf(displayMetrics.widthPixels, displayMetrics.heightPixels)))
            put("configuration_screen_dp", JSONArray(listOf(config.screenWidthDp, config.screenHeightDp)))
            put("window_bounds_dp", root.toJson())
            put("safe_insets_dp", JSONObject().apply {
                put("top", profile.insets.top.value); put("bottom", profile.insets.bottom.value)
                put("start", profile.insets.start.value); put("end", profile.insets.end.value)
            })
            put("data", JSONObject().apply {
                put("catch_ids", JSONArray(catches.map(RemoteCatch::id)))
                put("unique_catch_count", catches.map(RemoteCatch::id).toSet().size)
                put("pager_page_count", 2)
                put("initial_visible_card_ids", JSONArray(listOf("nh01-catch-001", "nh01-catch-002")))
                put("same_time_duplicate_ids", false)
                put("swipe_reached_second_id", true)
                put("middle_scroll_enabled", hasMiddleScroll)
                put("required_card_text_accessible", true)
            })
            put("metrics", JSONObject().apply {
                put("card_bounds_dp", activeCard.toJson())
                put("card_unclipped_layout_size_dp", JSONArray(listOf(activeCardLayoutWidthDp, activeCardLayoutHeightDp)))
                put("neighbor_bounds_dp", neighborCard.toJson())
                put("card_aspect_ratio", ratio)
                put("card_aspect_error_percent", ratioErrorPercent)
                put("card_target_width_dp", targetCardWidthDp)
                put("card_actual_width_dp", activeCardLayoutWidthDp)
                put("card_shrunk_for_height", activeCardLayoutWidthDp < targetCardWidthDp - 1f)
                put("section_title_to_card_gap_dp", sectionTitleCardGap)
                put("card_to_footer_gap_dp", footer.top - activeCard.bottom)
                put("header_to_stats_gap_dp", headerToStatsGap)
                put("stats_to_title_gap_dp", statsToTitleGap)
                put("card_track_horizontal_center_error_dp", centerOffset)
                put("cta_camera_overlap", cta.bottom > camera.top)
                put("camera_safe_bottom_clearance_dp", root.bottom - profile.insets.bottom.value - camera.bottom)
                put("brand_bounds_dp", brand.toJson())
                put("stats_bounds_dp", stats.toJson())
                put("footer_bounds_dp", footer.toJson())
                put("camera_bounds_dp", camera.toJson())
            })
            put("bounds_in_window_px", boundsPx)
            put("screenshots", JSONObject().apply {
                put("initial", screenshotName); put("after_swipe", afterSwipe.name)
                put("content_visible", contentScreenshotName ?: JSONObject.NULL)
                put("capture", "UiAutomation.takeScreenshot full Android runtime window")
            })
            put("interactions", JSONObject().apply {
                put("camera", "PASS"); put("all_catches", "PASS")
                put("catch_id_mapping", "PASS"); put("horizontal_swipe", "PASS")
            })
        }
        beginStep("WRITE_FINAL_PASS_REPORT")
        File(output, "$profileId-${variant.name.lowercase(Locale.US)}.json").writeText(report.toString(2) + "\n")
        completeStep("PASS_REPORT_WRITTEN")
        writeCaseStatus("PASS", "PASS_REPORT_WRITTEN")
        photo.delete()
    }

    private fun beginStep(step: String) {
        currentStep = step
        writeCaseStatus("RUNNING", step)
        Log.i("NH01_MATRIX_CASE", "STEP profile=$activeProfileId variant=$activeVariantName step=$step")
    }

    private fun completeStep(step: String) {
        lastSuccessfulStep = step
        currentStep = "AFTER_$step"
        writeCaseStatus("RUNNING", currentStep)
    }

    private fun caseReportFile(): File? {
        val output = activeCaseOutput ?: return null
        val variant = activeVariantName.lowercase(Locale.US)
        return File(output, "$activeProfileId-$variant.json")
    }

    private fun writeCaseStatus(status: String, stage: String, failure: Throwable? = null) {
        val reportFile = caseReportFile() ?: return
        runCatching {
            reportFile.parentFile?.mkdirs()
            val report = if (reportFile.isFile) {
                runCatching { JSONObject(reportFile.readText()) }.getOrDefault(JSONObject())
            } else JSONObject()
            report.put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_DEVICE_CASE_V1")
            report.put("execution_mode", "ANDROID_API28_RUNTIME")
            report.put("status", status)
            report.put("profile_id", activeProfileId)
            report.put("layout_variant", activeVariantName)
            report.put("source_commit", sourceSha)
            report.put("expected_system_font_scale", expectedFontScale)
            report.put("target_application_id", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
            report.put("external_files_directory", activeCaseOutput?.absolutePath ?: "MISSING")
            report.put("current_step", stage)
            report.put("last_successful_step", lastSuccessfulStep)
            report.put("started_at_epoch_ms", startedAtEpochMs)
            report.put("updated_at_epoch_ms", System.currentTimeMillis())
            if (failure != null) {
                val stack = StringWriter().also { writer -> failure.printStackTrace(PrintWriter(writer)) }.toString()
                report.put("failure_stage", stage)
                report.put("failure_class", failure.javaClass.name)
                report.put("failure_message", failure.message ?: "")
                report.put("failure_stacktrace", stack)
                report.put("failure_screenshot", "$activeProfileId-${activeVariantName.lowercase(Locale.US)}-failure.png")
            }
            val text = report.toString(2) + "\n"
            val temp = File(reportFile.parentFile, reportFile.name + ".tmp")
            temp.writeText(text)
            try {
                android.system.Os.rename(temp.absolutePath, reportFile.absolutePath)
            } catch (_: Throwable) {
                reportFile.writeText(text)
                temp.delete()
            }
        }.onFailure { error ->
            Log.e("NH01_MATRIX_CASE", "Could not persist case report $reportFile", error)
        }
    }

    private fun captureFailureScreenshot() {
        val output = activeCaseOutput ?: return
        runCatching {
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val file = File(output, "$activeProfileId-${activeVariantName.lowercase(Locale.US)}-failure.png")
            file.outputStream().use { sink ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, sink)) { "failure screenshot compression failed" }
            }
            screenshot.recycle()
        }.onFailure { error ->
            Log.e("NH01_MATRIX_CASE", "Could not capture failure screenshot", error)
        }
    }

    private fun fixtureCatches(imagePath: String): List<RemoteCatch> {
        val timestamp = "2024-05-29T04:26:00+08:00"
        return listOf(
            RemoteCatch(
                id = "nh01-catch-001", imageUrl = imagePath, speciesId = "species-grass-carp",
                speciesName = "草鱼", confidence = 0.92f, modelVersion = "nh01-layout-fixture",
                capturedAt = timestamp, createdAt = timestamp, lengthCm = 42.6f, weightKg = 1.28f,
                location = "浙江 · 千岛湖",
            ),
            RemoteCatch(
                id = "nh01-catch-002", imageUrl = imagePath, speciesId = "species-common-carp",
                speciesName = "鲤鱼", confidence = 0.88f, modelVersion = "nh01-layout-fixture",
                capturedAt = timestamp, createdAt = timestamp, lengthCm = 31f, weightKg = 0.84f,
                location = "浙江 · 千岛湖",
            ),
        )
    }

    private fun assertSingleNode(tag: String) {
        assertEquals("$tag must appear once", 1,
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun advanceFramesUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val frames = (timeoutMillis / 16L).coerceAtLeast(1L)
        repeat(frames.toInt()) {
            if (condition()) return
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue("Compose animation did not settle before timeout", condition())
    }

    private fun rect(tag: String): RectF {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val bounds = node.boundsInWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun RectF.toDp(density: Float) = RectF(left / density, top / density, right / density, bottom / density)

    private fun RectF.toJson() = JSONObject().apply {
        put("left", left); put("top", top); put("right", right); put("bottom", bottom)
        put("width", width()); put("height", height())
    }

    private data class RuntimeDensityProbe(val density: Float, val fontScale: Float, val insets: SafeDrawingInsetsDp)
}
