package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.DisplayMetrics
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
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
import org.junit.Assert.assertFalse
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
            GeminiHomeGeometryProbe.endCapture()
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
                    "nh01-adaptive-visual-balance-v2",
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
            GeminiHomeGeometryProbe.endCapture()
        }

        override fun failed(e: Throwable, description: Description) {
            writeCaseStatus("FAIL", currentStep, e)
            captureFailureScreenshot()
            GeminiHomeGeometryProbe.endCapture()
            Log.e(
                "NH01_MATRIX_CASE",
                "FAIL profile=$activeProfileId variant=$activeVariantName stage=$currentStep " +
                    "last_successful_step=$lastSuccessfulStep",
                e,
            )
        }
    }

    @get:Rule
    // Keep the activity alive while TestWatcher.failed captures the screenshot
    // and semantics snapshot. If the watcher is outside Compose, Compose tears
    // down its activity first and the failure screenshot can show the launcher.
    val rules = RuleChain.outerRule(compose).around(evidenceWatcher)

    @Test
    fun capturesActualWindowForScenario() {
        val args = InstrumentationRegistry.getArguments()
        val profileId = requireNotNull(args.getString("profile_id")) { "profile_id is required" }
        val scenario = args.getString("scenario") ?: "two_unique_catches"
        require(scenario in setOf("two_unique_catches", "single_record", "image_failure", "three_unique_catches")) {
            "unsupported NH01 runtime scenario: $scenario"
        }
        val variant = GeminiHomeLayoutVariant.valueOf(
            args.getString("layout_variant") ?: GeminiHomeLayoutVariant.ADAPTIVE_V2.name,
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(targetContext.getExternalFilesDir(null)), "nh01-adaptive-visual-balance-v2")
            .apply { mkdirs() }
        val photo = File(targetContext.cacheDir, "nh01-adaptive-catch-fixture.jpg").apply {
            targetContext.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
                outputStream().use { sink -> input.copyTo(sink) }
            }
        }
        val fixture = fixtureCatches(photo.absolutePath)
        val catches = when (scenario) {
            "single_record" -> fixture.take(1)
            "three_unique_catches" -> fixture
            else -> fixture.take(2)
        }
        val catchesState = mutableStateOf(catches)
        activeProfileId = profileId
        activeVariantName = variant.name
        sourceSha = args.getString("source_sha") ?: "UNKNOWN_SOURCE_SHA"
        expectedFontScale = args.getString("font_scale") ?: "UNKNOWN_FONT_SCALE"
        activeCaseOutput = output
        completeStep("ARGUMENTS_AND_CATCH_FIXTURE_READY")
        val animationScales = mapOf(
            "animator_duration_scale" to Settings.Global.getFloat(
                targetContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
            "transition_animation_scale" to Settings.Global.getFloat(
                targetContext.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            ),
            "window_animation_scale" to Settings.Global.getFloat(
                targetContext.contentResolver,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                1f,
            ),
        )
        beginStep("VERIFY_DETERMINISTIC_SYSTEM_ANIMATION_SETTINGS")
        animationScales.forEach { (setting, value) ->
            assertEquals("$setting must be disabled for deterministic Compose runtime capture", 0f, value, 0f)
        }
        completeStep("DETERMINISTIC_SYSTEM_ANIMATION_SETTINGS_CONFIRMED")
        val densityProbe = AtomicReference<RuntimeDensityProbe?>()
        val identifyClicks = AtomicInteger()
        val albumClicks = AtomicInteger()
        val catchClicks = mutableListOf<String>()

        // The production HomeMotionState intentionally emits a frame continuously.
        // Freeze implicit clock advancement from the start and advance frames only
        // while waiting for concrete UI/image conditions; waitForIdle cannot succeed
        // while this frame clock is running.
        compose.mainClock.autoAdvance = false
        GeminiHomeGeometryProbe.beginCapture()
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
                statistics = CatchStatistics(
                    totalCatches = catchesState.value.size,
                    speciesCount = catchesState.value.map(RemoteCatch::speciesId).distinct().size,
                ),
                recentCatches = catchesState.value,
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
        advanceFramesUntil(20_000) {
            compose.onAllNodesWithTag("gemini-home-screen", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        completeStep("PRODUCTION_HOME_SCREEN_COMPOSED")

        beginStep("WAIT_FOR_MAIN_CATCH_CARD")
        advanceFramesUntil(20_000) {
            compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        completeStep("MAIN_CATCH_CARD_COMPOSED")

        beginStep("WAIT_FOR_FISH_PHOTO_RENDER")
        val initialImageStatusTag = "gemini-home-catch-media-nh01-catch-001"
        advanceFramesUntil(20_000, timeoutDiagnostic = {
            "image_status=${imageLoadStatus(initialImageStatusTag)} " +
                "placeholder_nodes=${nodeCount("gemini-home-catch-image-placeholder-nh01-catch-001")}, " +
                "rendered_nodes=${nodeCount("home-evidence-backdrop-ready")}, " +
                "fixture=${photo.absolutePath} exists=${photo.isFile} bytes=${photo.length()}"
        }) {
            imageLoadStatus(initialImageStatusTag) in setOf("READY", "FALLBACK")
        }
        val initialImageStatus = imageLoadStatus(initialImageStatusTag)
        assertEquals(
            "the bundled real fish photo must decode and render; fallback is a recorded failure",
            "READY",
            initialImageStatus,
        )
        assertTrue(
            "READY image status must correspond to an actual rendered fish-photo node",
            nodeCount("home-evidence-backdrop-ready") > 0,
        )
        completeStep("FISH_PHOTO_RENDERED")

        val profile = requireNotNull(densityProbe.get()) { "Compose density/insets were not observed" }
        val expectedFontScaleValue = args.getString("font_scale")?.toFloatOrNull()
            ?: throw AssertionError("font_scale is required")
        beginStep("ASSERT_FONT_SCALE_AND_REQUIRED_CONTENT")
        assertEquals("system font_scale must reach Compose unchanged", expectedFontScaleValue, profile.fontScale, 0.02f)
        compose.onNodeWithTag("gemini-home-stat-catches-value", useUnmergedTree = true)
            .assertTextEquals(catchesState.value.size.toString())
        compose.onNodeWithTag("gemini-home-catch-species-nh01-catch-001", useUnmergedTree = true)
            .assertTextEquals("草鱼")
        compose.onNodeWithTag("gemini-home-catch-measurement-nh01-catch-001", useUnmergedTree = true)
            .assertTextEquals("42.6 cm · 1.28 kg")
        compose.onNodeWithTag("gemini-home-catch-metadata-nh01-catch-001", useUnmergedTree = true)
            .assertTextContains("05月29日 04:26", substring = true)
        val requiredCardTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        var requiredCardTextNoVisualOverflow = requiredCardTextTags.none(::hasVisualOverflow)
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
            assertTrue("Adaptive V2 must not visually truncate required card text: $requiredCardTextTags", requiredCardTextNoVisualOverflow)
        }
        compose.onNodeWithText("记录下一条鱼", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true).assertIsDisplayed().assertHasClickAction()
        completeStep("REQUIRED_CONTENT_AND_CAMERA_ACTION_VISIBLE")

        beginStep("ASSERT_CATCH_IDENTITY_AND_PAGER_COUNT")
        val pager = compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true).fetchSemanticsNode()
        val expectedPageCount = catchesState.value.size
        assertEquals("pager contains only the real finite record set", expectedPageCount,
            pager.config[androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo].columnCount)
        val pageState = pager.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue("initial page is the first real stable id: $pageState", pageState.contains("nh01-catch-001"))
        waitForPagerSettled(
            targetIndex = 0,
            targetId = "nh01-catch-001",
            density = profile.density,
            expectedRightId = catchesState.value.getOrNull(1)?.id,
        )
        assertSingleNode("gemini-home-catch-card-nh01-catch-001")
        if (scenario == "single_record") {
            assertEquals("single-record state must not create a fake adjacent catch", 0,
                compose.onAllNodesWithTag("gemini-home-catch-neighbor-nh01-catch-002", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
            assertEquals("single-record state has no second main card", 0,
                compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-002", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
            assertTrue("single-record state remains centered", abs(rect("gemini-home-catch-card-nh01-catch-001").centerX() -
                rect("gemini-home-carousel-track").centerX()) <= 2f * (densityProbe.get()?.density ?: 1f))
            val root = rect("gemini-home-screen").toDp(densityProbe.get()!!.density)
            val card = rect("gemini-home-catch-card-nh01-catch-001").toDp(densityProbe.get()!!.density)
            val middleScroll = compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
            val exactM01Window = root.width() == 320f && root.height() == 480f && profile.fontScale == 1f
            val singleSafeWidthDp = root.width() - profile.insets.start.value - profile.insets.end.value
            val singleSafeHeightDp = root.height() - profile.insets.top.value - profile.insets.bottom.value
            val singleLayoutSpec = resolveGeminiHomeLayoutSpec(
                singleSafeWidthDp, singleSafeHeightDp, profile.fontScale, variant, exactM01Exception = exactM01Window,
            )
            val singleBoundsTags = listOf(
                "gemini-home-screen", "gemini-home-background", "gemini-home-top-section",
                "gemini-home-header-bar", "gemini-home-brand-title", "gemini-home-avatar-button",
                "gemini-home-avatar-visual", "gemini-home-stats-bar", "gemini-home-stat-species-value",
                "gemini-home-stat-species-label", "gemini-home-stats-divider",
                "gemini-home-stat-catches-value", "gemini-home-stat-catches-label",
                "gemini-home-stat-record-days-value", "gemini-home-stat-record-days-label",
                "gemini-home-middle-section", "gemini-home-section-title", "gemini-home-section-all",
                "gemini-home-section-all-label", "gemini-home-carousel-track",
                "gemini-home-catch-card-nh01-catch-001", "gemini-home-catch-media-nh01-catch-001",
                "gemini-home-catch-species-nh01-catch-001",
                "gemini-home-catch-measurement-nh01-catch-001",
                "gemini-home-catch-metadata-nh01-catch-001", "gemini-home-footer-cta",
                "gemini-home-cta-label", "gemini-home-camera", "gemini-home-camera-art",
            ).distinct()
            val singleBoundsPx = JSONObject().apply { singleBoundsTags.filter { it != "gemini-home-stats-divider" }.forEach { put(it, rect(it).toJson()) } }
            val singleVisibleBoundsPx = JSONObject().apply { singleBoundsTags.filter { it != "gemini-home-stats-divider" }.forEach { put(it, visibleRect(it).toJson()) } }
            val singleBoundsDp = JSONObject().apply {
                singleBoundsTags.filter { it != "gemini-home-stats-divider" }.forEach {
                    put(it, rect(it).toDp(profile.density).toJson())
                }
            }
            val singleStats = rect("gemini-home-stats-bar").toDp(profile.density)
            val singleTitle = rect("gemini-home-section-title").toDp(profile.density)
            val singleTop = rect("gemini-home-top-section").toDp(profile.density)
            val singleMiddle = rect("gemini-home-middle-section").toDp(profile.density)
            val singleFooter = rect("gemini-home-footer-cta").toDp(profile.density)
            val singleCamera = rect("gemini-home-camera").toDp(profile.density)
            val singleCameraArt = rect("gemini-home-camera-art").toDp(profile.density)
            val singleCta = rect("gemini-home-cta-label").toDp(profile.density)
            val singleAvatarVisual = rect("gemini-home-avatar-visual").toDp(profile.density)
            val singleBrand = rect("gemini-home-brand-title").toDp(profile.density)
            val singleCard = card
            val singleHADp = singleTop.height()
            val singleHBDp = singleCard.bottom - singleTitle.top
            val singleHCDp = singleFooter.height()
            val singleGABBaseDp = singleLayoutSpec.statsToTitleGap?.value ?: (singleTitle.top - singleStats.bottom)
            val singleGBCBaseDp = singleLayoutSpec.cardToFooterGap?.value ?: (singleFooter.top - singleCard.bottom)
            val singleGABActualDp = singleTitle.top - singleStats.bottom
            val singleGBCActualDp = singleFooter.top - singleCard.bottom
            val singleSurplusDp = (singleSafeHeightDp -
                (singleHADp + singleHBDp + singleHCDp + singleGABBaseDp + singleGBCBaseDp)).coerceAtLeast(0f)
            val singleExpectedHalfSurplusDp = singleSurplusDp / 2f
            val m01RequiredHeroHeightDp = 145f * 293f / 247f
            val singleSectionAction = touchRect("gemini-home-section-all").toDp(profile.density)
            val singleTrack = rect("gemini-home-carousel-track").toDp(profile.density)
            val singleMinimumHeroLeft = singleTrack.centerX() - 145f / 2f
            val singleMinimumHeroRight = singleTrack.centerX() + 145f / 2f
            val m01HeaderActionOverlapsMinimumHeroHorizontally =
                singleSectionAction.left < singleMinimumHeroRight && singleSectionAction.right > singleMinimumHeroLeft
            val singleTitleToCardBaseGapDp = singleLayoutSpec.titleToCardGap.value
            val singleMinimumHeroTopOffsetFromTitleDp = maxOf(
                singleTitle.height() + singleTitleToCardBaseGapDp,
                if (m01HeaderActionOverlapsMinimumHeroHorizontally) {
                    singleSectionAction.bottom - singleTitle.top
                } else 0f,
            )
            val m01MinimumRegionBDp = singleMinimumHeroTopOffsetFromTitleDp + m01RequiredHeroHeightDp
            val m01MinimumRequiredSafeHeightDp = singleHADp + singleHCDp +
                singleGABBaseDp + singleGBCBaseDp + m01MinimumRegionBDp
            val m01FeasibilityDeficitDp = (m01MinimumRequiredSafeHeightDp - singleSafeHeightDp).coerceAtLeast(0f)
            val singleTextTags = listOf(
                "gemini-home-catch-species-nh01-catch-001",
                "gemini-home-catch-measurement-nh01-catch-001",
                "gemini-home-catch-metadata-nh01-catch-001",
                "gemini-home-cta-label",
            )
            val singleTextLayouts = JSONObject().apply {
                singleTextTags.forEach { tag ->
                    put(tag, JSONObject().apply {
                        put("line_count", runCatching { textLineCount(tag) }.getOrNull() ?: JSONObject.NULL)
                        put("line_ranges", runCatching { textLineRanges(tag) }.getOrNull() ?: JSONObject.NULL)
                        put("visual_overflow", runCatching { hasVisualOverflow(tag) }.getOrNull() ?: JSONObject.NULL)
                        put("font_size_sp", runCatching { fontSizeSp(tag) }.getOrNull() ?: JSONObject.NULL)
                    })
                }
            }
            val repeatedBoundsTags = listOf("gemini-home-stats-divider", "home-evidence-fit-foreground",
                "home-evidence-backdrop-ready", "home-evidence-backdrop-crop-fallback", "fish-photo-subject-crop")
            val repeatedBounds = JSONObject().apply {
                repeatedBoundsTags.forEach { tag ->
                    val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                    put(tag, JSONArray().apply {
                        nodes.forEach { node ->
                            val bounds = node.boundsInWindow
                            put(JSONObject().apply {
                                put("left", bounds.left); put("top", bounds.top)
                                put("right", bounds.right); put("bottom", bounds.bottom)
                                put("width", bounds.width); put("height", bounds.height)
                            })
                        }
                    })
                }
            }
            val singleDiagnostic = JSONObject().apply {
                put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_GEOMETRY_DIAGNOSTIC_V1")
                put("status", "MEASURED_BEFORE_ASSERTIONS")
                put("profile_id", profileId)
                put("layout_variant", variant.name)
                put("source_commit", sourceSha)
                put("scenario", scenario)
                put("system_font_scale", profile.fontScale)
                put("density", profile.density)
                put("density_dpi", compose.activity.resources.displayMetrics.densityDpi)
                put("android_api", Build.VERSION.SDK_INT)
                put("device_model", Build.MODEL)
                put("app_package", targetContext.packageName)
                put("display_resolution_px", JSONArray(listOf(root.width() * profile.density, root.height() * profile.density)))
                put("window_bounds_dp", root.toJson())
                put("safe_insets_dp", JSONObject().apply {
                    put("top", profile.insets.top.value); put("bottom", profile.insets.bottom.value)
                    put("start", profile.insets.start.value); put("end", profile.insets.end.value)
                })
                put("data", JSONObject().apply {
                    put("catch_ids", JSONArray(listOf("nh01-catch-001")))
                    put("unique_catch_count", 1); put("pager_page_count", 1)
                    put("middle_scroll_enabled", middleScroll)
                    put("single_record_state", "ONE_CENTERED_HERO_NO_NEIGHBOR")
                })
                put("metrics", JSONObject().apply {
                    put("safe_width_dp", singleSafeWidthDp); put("safe_height_dp", singleSafeHeightDp)
                    put("hA_top_dp", singleHADp); put("hB_middle_dp", singleHBDp); put("hC_footer_dp", singleHCDp)
                    put("gAB_base_dp", singleGABBaseDp); put("gBC_base_dp", singleGBCBaseDp)
                    put("gAB_actual_dp", singleGABActualDp); put("gBC_actual_dp", singleGBCActualDp)
                    put("surplus_S_dp", singleSurplusDp); put("expected_S_over_2_dp", singleExpectedHalfSurplusDp)
                    put("actual_gAB_increment_dp", singleGABActualDp - singleGABBaseDp)
                    put("actual_gBC_increment_dp", singleGBCActualDp - singleGBCBaseDp)
                    put("gAB_error_dp", (singleGABActualDp - singleGABBaseDp) - singleExpectedHalfSurplusDp)
                    put("gBC_error_dp", (singleGBCActualDp - singleGBCBaseDp) - singleExpectedHalfSurplusDp)
                    put("hero_bounds_dp", singleCard.toJson())
                    put("hero_width_dp", singleCard.width()); put("hero_height_dp", singleCard.height())
                    put("hero_aspect_ratio", singleCard.width() / singleCard.height())
                    put("hero_minimum_readable_height_dp", singleLayoutSpec.minReadableCardHeight.value)
                    put("m01_required_hero_width_dp", 145f)
                    put("m01_required_hero_height_dp", m01RequiredHeroHeightDp)
                    put("m01_minimum_hero_top_offset_from_title_dp", singleMinimumHeroTopOffsetFromTitleDp)
                    put("m01_minimum_region_b_height_dp", m01MinimumRegionBDp)
                    put("m01_header_action_overlaps_minimum_hero_horizontally", m01HeaderActionOverlapsMinimumHeroHorizontally)
                    put("m01_minimum_required_safe_height_dp", m01MinimumRequiredSafeHeightDp)
                    put("m01_feasibility_deficit_dp", m01FeasibilityDeficitDp)
                    put("m01_actual_hero_height_deficit_dp", (m01RequiredHeroHeightDp - singleCard.height()).coerceAtLeast(0f))
                    put("stats_bounds_dp", singleStats.toJson()); put("title_bounds_dp", singleTitle.toJson())
                    put("top_bounds_dp", singleTop.toJson()); put("middle_bounds_dp", singleMiddle.toJson())
                    put("footer_bounds_dp", singleFooter.toJson())
                    put("brand_bounds_dp", singleBrand.toJson()); put("avatar_visual_bounds_dp", singleAvatarVisual.toJson())
                    put("avatar_touch_bounds_dp", touchRect("gemini-home-avatar-button").toDp(profile.density).toJson())
                    put("camera_art_bounds_dp", singleCameraArt.toJson()); put("camera_touch_bounds_dp", touchRect("gemini-home-camera").toDp(profile.density).toJson())
                    put("cta_bounds_dp", singleCta.toJson())
                    put("text_layouts", singleTextLayouts)
                    put("initial_photo_load_status", initialImageStatus)
                    put("pager_state", pagerSnapshot())
                    put("insets_consumption", "safe dimensions subtract WindowInsets once; content Column applies padding once")
                    put("repeated_bounds_window_px", repeatedBounds)
                    put("layout_bounds_unclipped_window_px", singleBoundsPx)
                    put("visible_bounds_clipped_window_px", singleVisibleBoundsPx)
                    put("bounds_dp", singleBoundsDp)
                })
                put("spacing_s2_status", if (variant != GeminiHomeLayoutVariant.V3_BASELINE &&
                    abs((singleGABActualDp - singleGABBaseDp) - singleExpectedHalfSurplusDp) <= 2f &&
                    abs((singleGBCActualDp - singleGBCBaseDp) - singleExpectedHalfSurplusDp) <= 2f)
                    "PASS" else if (variant == GeminiHomeLayoutVariant.V3_BASELINE) "NOT_APPLICABLE" else "FAIL")
                put("contract_evaluation", when {
                    exactM01Window && middleScroll -> "FAIL_CONSTRAINT/SPEC_CONFLICT"
                    exactM01Window && card.width() < 145f && m01FeasibilityDeficitDp > 0.5f -> "FAIL_CONSTRAINT/SPEC_CONFLICT"
                    exactM01Window && card.width() < 145f -> "FAIL_IMPLEMENTATION/FEASIBLE_MINIMUM_NOT_HONORED"
                    variant != GeminiHomeLayoutVariant.V3_BASELINE &&
                        (abs((singleGABActualDp - singleGABBaseDp) - singleExpectedHalfSurplusDp) > 2f ||
                            abs((singleGBCActualDp - singleGBCBaseDp) - singleExpectedHalfSurplusDp) > 2f) -> "FAIL_S2_ALLOCATION"
                    else -> "MEASURED"
                })
                put("screenshot", "$profileId-${variant.name.lowercase(Locale.US)}.png")
            }
            File(output, "$profileId-${variant.name.lowercase(Locale.US)}-geometry-diagnostic.json")
                .writeText(singleDiagnostic.toString(2) + "\n")
            val screenshotName = "$profileId-${variant.name.lowercase(Locale.US)}.png"
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(output, screenshotName).outputStream().use { sink ->
                assertTrue("single-record screenshot must save", bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink))
            }
            bitmap.recycle()
            if (variant != GeminiHomeLayoutVariant.V3_BASELINE) {
                assertTrue("single-record inter-band additions must be equal S/2: before=${singleGABActualDp - singleGABBaseDp} after=${singleGBCActualDp - singleGBCBaseDp}",
                    abs((singleGABActualDp - singleGABBaseDp) - (singleGBCActualDp - singleGBCBaseDp)) <= 2f)
                assertTrue("single-record A-to-B gap must equal base plus S/2",
                    abs((singleGABActualDp - singleGABBaseDp) - singleExpectedHalfSurplusDp) <= 2f)
                assertTrue("single-record B-to-C gap must equal base plus S/2",
                    abs((singleGBCActualDp - singleGBCBaseDp) - singleExpectedHalfSurplusDp) <= 2f)
            }
            if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && exactM01Window) {
                assertFalse("FAIL_CONSTRAINT/SPEC_CONFLICT: exact 320×480dp M01 must not scroll", middleScroll)
                assertTrue(
                    "${if (m01FeasibilityDeficitDp > 0.5f) "FAIL_CONSTRAINT/SPEC_CONFLICT" else "FAIL_IMPLEMENTATION/FEASIBLE_MINIMUM_NOT_HONORED"}: " +
                        "exact M01 contract requires 145dp × $m01RequiredHeroHeightDp dp Hero; " +
                        "actual=${card.width()}×${card.height()}dp, H_safe=$singleSafeHeightDp, " +
                        "A=$singleHADp, B=$singleHBDp, C=$singleHCDp, baseGaps=$singleGABBaseDp/$singleGBCBaseDp, " +
                        "minimum_safe_height=$m01MinimumRequiredSafeHeightDp deficit=$m01FeasibilityDeficitDp",
                    card.width() >= 145f,
                )
            }
            assertTrue("single-record Hero keeps the frozen ratio", abs(card.width() / card.height() - (247f / 293f)) <= 0.01f)
            val avatar = touchRect("gemini-home-avatar-button").toDp(densityProbe.get()!!.density)
            val camera = touchRect("gemini-home-camera").toDp(densityProbe.get()!!.density)
            val cta = rect("gemini-home-cta-label").toDp(densityProbe.get()!!.density)
            assertTrue("single-record avatar target remains at least 48dp", avatar.width() >= 48f && avatar.height() >= 48f)
            assertTrue("single-record camera target remains at least 72dp", camera.width() >= 72f && camera.height() >= 72f)
            assertTrue("single-record CTA does not overlap the camera", cta.bottom <= camera.top + 1f)
            val singleBounds = singleBoundsPx
            val density = compose.activity.resources.displayMetrics.density
            val windowManager = compose.activity.windowManager
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            val singleReport = JSONObject().apply {
                put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_DEVICE_CASE_V1")
                put("execution_mode", "ANDROID_API28_RUNTIME")
                put("status", "PASS")
                put("profile_id", profileId)
                put("layout_variant", variant.name)
                put("source_commit", sourceSha)
                put("target_application_id", targetContext.packageName)
                put("system_font_scale", densityProbe.get()!!.fontScale)
                put("density", density)
                put("density_dpi", displayMetrics.densityDpi)
                put("display_resolution_px", JSONArray(listOf(displayMetrics.widthPixels, displayMetrics.heightPixels)))
                put("window_bounds_dp", root.toJson())
                put("safe_insets_dp", JSONObject().apply {
                    put("top", densityProbe.get()!!.insets.top.value); put("bottom", densityProbe.get()!!.insets.bottom.value)
                    put("start", densityProbe.get()!!.insets.start.value); put("end", densityProbe.get()!!.insets.end.value)
                })
                put("scenario", scenario)
                put("data", JSONObject().apply {
                    put("catch_ids", JSONArray(listOf("nh01-catch-001")))
                    put("unique_catch_count", 1)
                    put("pager_page_count", 1)
                    put("single_record_state", "PASS_ONE_CENTERED_HERO_NO_NEIGHBOR")
                    put("middle_scroll_enabled", middleScroll)
                })
                put("metrics", JSONObject().apply {
                    put("card_bounds_dp", card.toJson())
                    put("card_aspect_ratio", card.width() / card.height())
                    put("card_actual_width_dp", card.width())
                    put("card_actual_height_dp", card.height())
                    put("avatar_touch_bounds_dp", avatar.toJson())
                    put("camera_touch_bounds_dp", camera.toJson())
                    put("cta_bounds_dp", cta.toJson())
                    put("safe_width_dp", singleSafeWidthDp)
                    put("safe_height_dp", singleSafeHeightDp)
                    put("hA_top_dp", singleHADp); put("hB_middle_dp", singleHBDp); put("hC_footer_dp", singleHCDp)
                    put("gAB_base_dp", singleGABBaseDp); put("gBC_base_dp", singleGBCBaseDp)
                    put("gAB_actual_dp", singleGABActualDp); put("gBC_actual_dp", singleGBCActualDp)
                    put("surplus_S_dp", singleSurplusDp); put("expected_S_over_2_dp", singleExpectedHalfSurplusDp)
                    put("actual_gAB_increment_dp", singleGABActualDp - singleGABBaseDp)
                    put("actual_gBC_increment_dp", singleGBCActualDp - singleGBCBaseDp)
                    put("gAB_error_dp", (singleGABActualDp - singleGABBaseDp) - singleExpectedHalfSurplusDp)
                    put("gBC_error_dp", (singleGBCActualDp - singleGBCBaseDp) - singleExpectedHalfSurplusDp)
                    put("hero_minimum_readable_height_dp", singleLayoutSpec.minReadableCardHeight.value)
                    put("text_layouts", singleTextLayouts)
                    put("photo_renderer_bounds_window_px", repeatedBounds)
                })
                put("layout_bounds_unclipped_window_px", singleBounds)
                put("visible_bounds_clipped_window_px", singleVisibleBoundsPx)
                put("initial_photo_load_status", initialImageStatus)
                put("screenshots", JSONObject().put("initial", screenshotName)
                    .put("capture", "UiAutomation.takeScreenshot full Android runtime window"))
            }
            File(output, "$profileId-${variant.name.lowercase(Locale.US)}.json").writeText(singleReport.toString(2) + "\n")
            writeCaseStatus("PASS", "SINGLE_RECORD_CENTERED_HERO_AND_NO_FAKE_PEEK")
            return
        } else {
            assertSingleNode("gemini-home-catch-neighbor-nh01-catch-002")
            assertEquals("the second catch may not be duplicated as a main card", 0,
                compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-002", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
            assertEquals("the first catch may not be duplicated as a neighbor", 0,
                compose.onAllNodesWithTag("gemini-home-catch-neighbor-nh01-catch-001", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
        }
        completeStep("CATCH_IDENTITY_ASSERTIONS_PASSED")

        beginStep("CAPTURE_COMPONENT_BOUNDS_AND_LAYOUT_METRICS")
        val tags = listOf(
            "gemini-home-screen", "gemini-home-background", "gemini-home-top-section", "gemini-home-avatar-button",
            "gemini-home-avatar-visual", "gemini-home-brand-title", "gemini-home-header-bar", "gemini-home-stats-bar",
            "gemini-home-section-title", "gemini-home-section-all", "gemini-home-section-all-label", "gemini-home-middle-section", "gemini-home-carousel-track",
            "gemini-home-stat-species-value", "gemini-home-stat-species-label",
            "gemini-home-stat-catches-value", "gemini-home-stat-catches-label",
            "gemini-home-stat-record-days-value", "gemini-home-stat-record-days-label",
            "gemini-home-catch-card-nh01-catch-001", "gemini-home-catch-media-nh01-catch-001",
            "gemini-home-catch-neighbor-nh01-catch-002",
            "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera", "gemini-home-camera-art",
            "gemini-home-catch-species-nh01-catch-001", "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        val boundsPx = JSONObject().apply { tags.forEach { put(it, rect(it).toJson()) } }
        val visibleBoundsPx = JSONObject().apply { tags.forEach { put(it, visibleRect(it).toJson()) } }
        val fontSizeTags = listOf(
            "gemini-home-stat-species-value", "gemini-home-stat-species-label",
            "gemini-home-stat-catches-value", "gemini-home-stat-catches-label",
            "gemini-home-stat-record-days-value", "gemini-home-stat-record-days-label",
            "gemini-home-section-title", "gemini-home-section-all-label",
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
            "gemini-home-cta-label",
        )
        val renderedFontSizesSp = JSONObject().apply {
            fontSizeTags.forEach { put(it, fontSizeSp(it)) }
        }
        val root = rect("gemini-home-screen").toDp(profile.density)
        val track = rect("gemini-home-carousel-track").toDp(profile.density)
        val safeWidthDp = (root.width() - profile.insets.start.value - profile.insets.end.value).coerceAtLeast(1f)
        val safeHeightDp = (root.height() - profile.insets.top.value - profile.insets.bottom.value).coerceAtLeast(1f)
        args.getString("expected_safe_height_dp")?.toFloatOrNull()?.let { expectedSafeHeight ->
            assertEquals("boundary case must land on the requested measured safe height", expectedSafeHeight, safeHeightDp, 0.25f)
        }
        val exactM01Exception = safeWidthDp == 320f && root.height() == 480f && expectedFontScaleValue == 1f
        val layoutSpec = resolveGeminiHomeLayoutSpec(
            safeWidthDp, safeHeightDp, profile.fontScale, variant, exactM01Exception = exactM01Exception,
        )
        val header = rect("gemini-home-header-bar").toDp(profile.density)
        val activeCard = rect("gemini-home-catch-card-nh01-catch-001").toDp(profile.density)
        val activeCardLayoutWidthDp = activeCard.width()
        val activeCardLayoutHeightDp = activeCard.height()
        val neighborCard = rect("gemini-home-catch-neighbor-nh01-catch-002").toDp(profile.density)
        val title = rect("gemini-home-section-title").toDp(profile.density)
        val footer = rect("gemini-home-footer-cta").toDp(profile.density)
        val topSection = rect("gemini-home-top-section").toDp(profile.density)
        val middleSection = rect("gemini-home-middle-section").toDp(profile.density)
        val avatar = touchRect("gemini-home-avatar-button").toDp(profile.density)
        val cta = rect("gemini-home-cta-label").toDp(profile.density)
        val sectionAction = touchRect("gemini-home-section-all").toDp(profile.density)
        val camera = touchRect("gemini-home-camera").toDp(profile.density)
        val cameraArt = rect("gemini-home-camera-art").toDp(profile.density)
        val expectedCameraArtSize = geminiHomeCameraVisualSize(safeWidthDp, safeHeightDp)
        val brand = rect("gemini-home-brand-title").toDp(profile.density)
        val stats = rect("gemini-home-stats-bar").toDp(profile.density)
        val sectionTitleCardGap = activeCard.top - title.bottom
        val statsToTitleGap = title.top - stats.bottom
        val headerToStatsGap = stats.top - header.bottom
        val cardToFooterGap = footer.top - activeCard.bottom
        val hA = topSection.height()
        val hB = activeCard.bottom - title.top
        val hC = footer.height()
        val baseGapBefore = layoutSpec.statsToTitleGap?.value ?: statsToTitleGap
        val baseGapAfter = layoutSpec.cardToFooterGap?.value ?: cardToFooterGap
        val contractSurplus = (safeHeightDp - (hA + hB + hC + baseGapBefore + baseGapAfter)).coerceAtLeast(0f)
        val m01RequiredHeroHeightDp = 145f * 293f / 247f
        val m01MinimumHeroLeft = track.centerX() - 145f / 2f
        val m01MinimumHeroRight = track.centerX() + 145f / 2f
        val m01HeaderActionOverlapsMinimumHeroHorizontally =
            sectionAction.left < m01MinimumHeroRight && sectionAction.right > m01MinimumHeroLeft
        val m01MinimumHeroTopOffsetFromTitleDp = maxOf(
            title.height() + layoutSpec.titleToCardGap.value,
            if (m01HeaderActionOverlapsMinimumHeroHorizontally) sectionAction.bottom - title.top else 0f,
        )
        val m01MinimumRegionBDp = m01MinimumHeroTopOffsetFromTitleDp + m01RequiredHeroHeightDp
        val m01MinimumRequiredSafeHeightDp = hA + hC + baseGapBefore + baseGapAfter + m01MinimumRegionBDp
        val m01FeasibilityDeficitDp = (m01MinimumRequiredSafeHeightDp - safeHeightDp).coerceAtLeast(0f)
        val expectedEqualGapAddition = contractSurplus / 2f
        val actualGapBeforeAddition = statsToTitleGap - baseGapBefore
        val actualGapAfterAddition = cardToFooterGap - baseGapAfter
        val centerOffset = abs(activeCard.centerX() - track.centerX())
        val ratio = activeCardLayoutWidthDp / activeCardLayoutHeightDp
        val neighborWidthFraction = neighborCard.width() / activeCardLayoutWidthDp
        val neighborOverlapDp = maxOf(0f, activeCard.right - neighborCard.left)
        val targetPeekWidthDp = (track.width() * 0.05f).coerceIn(12f, 28f)
        val pageSpacingDp = (track.width() * 0.025f).coerceIn(8f, 12f)
        val widthForTargetEdgePeek = 2f * targetPeekWidthDp + 2f * pageSpacingDp +
            2f * activeCardLayoutWidthDp - track.width()
        val minimumWidthToTouchEdge = (track.width() - 2f * activeCardLayoutWidthDp - 2f * pageSpacingDp).coerceAtLeast(0f)
        val requiredNeighborWidthDp = maxOf(targetPeekWidthDp, widthForTargetEdgePeek, minimumWidthToTouchEdge)
        val edgePeekFeasible = requiredNeighborWidthDp <= activeCardLayoutWidthDp * 0.82f + 1f
        val visibleNeighborCard = visibleRect("gemini-home-catch-neighbor-nh01-catch-002").toDp(profile.density)
        val rightPeekVisibleWidthDp = (
            minOf(visibleNeighborCard.right, track.right) - maxOf(visibleNeighborCard.left, track.left)
        ).coerceAtLeast(0f)
        val rightEdgePeekVisibleWidthDp = if (visibleNeighborCard.right >= track.right - 1f && visibleNeighborCard.left < track.right) {
            (track.right - maxOf(visibleNeighborCard.left, track.left)).coerceAtLeast(0f)
        } else 0f
        val cardWidthFraction = when (variant) {
            GeminiHomeLayoutVariant.ADAPTIVE_V1 -> 0.76f
            GeminiHomeLayoutVariant.ADAPTIVE_V2 -> 0.70f
            GeminiHomeLayoutVariant.V3_BASELINE -> 0.686f
        }
        val cardWidthCap = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) 360f else 337.2f
        val targetCardWidthDp = minOf(minOf(track.width(), 560f) * cardWidthFraction, cardWidthCap)
        val middleScrollEnabled = compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        val diagnosticRepeatedTags = listOf(
            "gemini-home-stats-divider", "home-evidence-fit-foreground", "home-evidence-backdrop-ready",
            "home-evidence-backdrop-crop-fallback", "fish-photo-subject-crop",
        )
        val diagnosticRepeatedBounds = JSONObject().apply {
            diagnosticRepeatedTags.forEach { tag ->
                val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                put(tag, JSONArray().apply {
                    nodes.forEach { node ->
                        val bounds = node.boundsInWindow
                        put(JSONObject().apply {
                            put("left", bounds.left); put("top", bounds.top)
                            put("right", bounds.right); put("bottom", bounds.bottom)
                            put("width", bounds.width); put("height", bounds.height)
                        })
                    }
                })
            }
        }
        val diagnosticTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        val diagnosticTextLayouts = JSONObject().apply {
            diagnosticTextTags.forEach { tag ->
                put(tag, JSONObject().apply {
                    put("line_count", runCatching { textLineCount(tag) }.getOrNull() ?: JSONObject.NULL)
                    put("line_ranges", runCatching { textLineRanges(tag) }.getOrNull() ?: JSONObject.NULL)
                    put("visual_overflow", runCatching { hasVisualOverflow(tag) }.getOrNull() ?: JSONObject.NULL)
                })
            }
        }
        val geometryDiagnostic = JSONObject().apply {
            put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_GEOMETRY_DIAGNOSTIC_V1")
            put("status", "MEASURED_BEFORE_ASSERTIONS")
            put("profile_id", profileId)
            put("layout_variant", variant.name)
            put("source_commit", args.getString("source_sha") ?: "MISSING_SOURCE_SHA")
            put("scenario", scenario)
            put("system_font_scale", profile.fontScale)
            put("density", profile.density)
            put("window_bounds_dp", root.toJson())
            put("safe_insets_dp", JSONObject().apply {
                put("top", profile.insets.top.value); put("bottom", profile.insets.bottom.value)
                put("start", profile.insets.start.value); put("end", profile.insets.end.value)
            })
            put("data", JSONObject().apply {
                put("catch_ids", JSONArray(catches.map(RemoteCatch::id)))
                put("unique_catch_count", catches.map(RemoteCatch::id).toSet().size)
                put("pager_page_count", catchesState.value.size)
                put("middle_scroll_enabled", middleScrollEnabled)
            })
            put("metrics", JSONObject().apply {
                put("safe_width_dp", safeWidthDp); put("safe_height_dp", safeHeightDp)
                put("hA_top_dp", hA); put("hB_middle_dp", hB); put("hC_footer_dp", hC)
                put("gAB_base_dp", baseGapBefore); put("gBC_base_dp", baseGapAfter)
                put("gAB_actual_dp", statsToTitleGap); put("gBC_actual_dp", cardToFooterGap)
                put("surplus_S_dp", contractSurplus); put("expected_S_over_2_dp", expectedEqualGapAddition)
                put("actual_gAB_increment_dp", actualGapBeforeAddition)
                put("actual_gBC_increment_dp", actualGapAfterAddition)
                put("gAB_error_dp", actualGapBeforeAddition - expectedEqualGapAddition)
                put("gBC_error_dp", actualGapAfterAddition - expectedEqualGapAddition)
                put("card_bounds_dp", activeCard.toJson())
                put("card_target_width_dp", targetCardWidthDp)
                put("card_actual_width_dp", activeCardLayoutWidthDp)
                put("card_actual_height_dp", activeCardLayoutHeightDp)
                put("card_aspect_ratio", ratio)
                put("minimum_readable_card_height_dp", layoutSpec.minReadableCardHeight.value)
                put("m01_required_hero_width_dp", 145f)
                put("m01_required_hero_height_dp", m01RequiredHeroHeightDp)
                put("m01_minimum_hero_top_offset_from_title_dp", m01MinimumHeroTopOffsetFromTitleDp)
                put("m01_minimum_region_b_height_dp", m01MinimumRegionBDp)
                put("m01_header_action_overlaps_minimum_hero_horizontally", m01HeaderActionOverlapsMinimumHeroHorizontally)
                put("m01_minimum_required_safe_height_dp", m01MinimumRequiredSafeHeightDp)
                put("m01_feasibility_deficit_dp", m01FeasibilityDeficitDp)
                put("m01_actual_hero_height_deficit_dp", (m01RequiredHeroHeightDp - activeCardLayoutHeightDp).coerceAtLeast(0f))
                put("rendered_font_sizes_sp", renderedFontSizesSp)
                put("photo_renderer_bounds_window_px", diagnosticRepeatedBounds)
                put("title_bounds_dp", title.toJson())
                put("stats_bounds_dp", stats.toJson())
                put("top_bounds_dp", topSection.toJson())
                put("middle_bounds_dp", middleSection.toJson())
                put("footer_bounds_dp", footer.toJson())
                put("hero_neighbor_bounds_dp", neighborCard.toJson())
                put("text_layouts", diagnosticTextLayouts)
                put("pager_state", pagerSnapshot())
                put("photo_load_status", imageLoadStatus("gemini-home-catch-media-nh01-catch-001") ?: "NOT_OBSERVED")
                put("insets_consumption", "safe dimensions subtract WindowInsets once; content Column applies padding once")
                put("layout_bounds_unclipped_window_px", boundsPx)
                put("visible_bounds_clipped_window_px", visibleBoundsPx)
            })
            put("contract_evaluation", when {
                exactM01Exception && middleScrollEnabled -> "FAIL_CONSTRAINT/SPEC_CONFLICT"
                exactM01Exception && activeCardLayoutWidthDp < 145f && m01FeasibilityDeficitDp > 0.5f ->
                    "FAIL_CONSTRAINT/SPEC_CONFLICT"
                exactM01Exception && activeCardLayoutWidthDp < 145f -> "FAIL_IMPLEMENTATION/FEASIBLE_MINIMUM_NOT_HONORED"
                else -> "MEASURED"
            })
        }
        File(output, "$profileId-${variant.name.lowercase(Locale.US)}-geometry-diagnostic.json")
            .writeText(geometryDiagnostic.toString(2) + "\n")

        assertTrue("card aspect ratio stays within 1% of the frozen 247:293 contract: $ratio",
            abs(ratio / (247f / 293f) - 1f) <= 0.01f)
        assertTrue("main card stays horizontally centered, offset=$centerOffset dp", centerOffset <= 2f)
        assertTrue("measured card may shrink under height pressure but may not exceed its policy target",
            activeCardLayoutWidthDp <= targetCardWidthDp + 1f)
        assertTrue("card bounds remain inside the pager track", activeCard.left >= track.left - 1f && activeCard.right <= track.right + 1f)
        assertTrue("right peek must not overlap the active card: active=$activeCard neighbor=$neighborCard",
            neighborCard.left >= activeCard.right - 1f)
        assertTrue("right peek must remain at most 82% of active card width: active=$activeCardLayoutWidthDp neighbor=$neighborCard",
            neighborCard.width() <= activeCardLayoutWidthDp * 0.82f + 1f)
        assertTrue("right peek must remain visibly present inside the track",
            rightPeekVisibleWidthDp >= minOf(12f, neighborCard.width()) - 1f)
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && edgePeekFeasible) {
            assertTrue("Adaptive V2 edge peek must expose the target width at the track edge: target=$targetPeekWidthDp actual=$rightEdgePeekVisibleWidthDp",
                rightEdgePeekVisibleWidthDp >= targetPeekWidthDp - 1f)
        }
        // V2.2 approval: independent visible title TEXT to selected CARD gap,
        // not just self-consistency against the old 7-12dp layout policy.
        val frozenTitleCardGapDp = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 &&
            profile.fontScale <= 1.01f
        ) {
            when (profileId) {
                "M01" -> 8f
                "M04" -> 12f
                "M07" -> 18f
                "M10" -> 20f
                else -> null
            }
        } else null
        if (frozenTitleCardGapDp != null) {
            assertTrue(
                "V2.2 independent title-card gap target=$frozenTitleCardGapDp dp actual=$sectionTitleCardGap dp profile=$profileId",
                abs(sectionTitleCardGap - frozenTitleCardGapDp) <= 2f,
            )
        } else {
            assertTrue("V3 comparison or font-scaled gap tracks its own policy: $sectionTitleCardGap dp",
                abs(sectionTitleCardGap - layoutSpec.titleToCardGap.value) <= 2f)
        }
        if (variant != GeminiHomeLayoutVariant.V3_BASELINE) {
            assertTrue("stats-to-title visual gap equals its base plus the contract S/2 addition: actual=$statsToTitleGap dp expected=${baseGapBefore + expectedEqualGapAddition} dp",
                abs(statsToTitleGap - (baseGapBefore + expectedEqualGapAddition)) <= 2f)
            assertTrue("brand-to-stats gap follows the active layout policy: $headerToStatsGap dp",
                abs(headerToStatsGap - requireNotNull(layoutSpec.brandToStatsGap).value) <= 2f)
            assertTrue("both inter-band gaps receive equal S/2 additions: before=$actualGapBeforeAddition after=$actualGapAfterAddition",
                abs(actualGapBeforeAddition - actualGapAfterAddition) <= 2f)
            assertTrue("Stats-to-title gap reflects measured safe-area surplus: expected=$expectedEqualGapAddition actual=$actualGapBeforeAddition",
                abs(actualGapBeforeAddition - expectedEqualGapAddition) <= 2f)
            assertTrue("Hero-to-Footer gap reflects measured safe-area surplus: expected=$expectedEqualGapAddition actual=$actualGapAfterAddition",
                abs(actualGapAfterAddition - expectedEqualGapAddition) <= 2f)
        }
        assertTrue("Footer stays outside the Middle region: middle=$middleSection footer=$footer",
            middleSection.bottom <= footer.top + 1f)
        assertTrue("section action retains its 48dp touch target",
            sectionAction.width() >= 48f && sectionAction.height() >= 48f)
        assertTrue("avatar action retains its 48dp touch target", avatar.width() >= 48f && avatar.height() >= 48f)
        assertTrue("camera touch target remains at least 72dp", camera.width() >= 72f && camera.height() >= 72f)
        assertTrue(
            "visible camera artwork follows width/height-aware policy: expected=$expectedCameraArtSize dp actual=$cameraArt",
            abs(cameraArt.width() - expectedCameraArtSize) <= 2f &&
                abs(cameraArt.height() - expectedCameraArtSize) <= 2f,
        )
        assertTrue("CTA and camera must not overlap", cta.bottom <= camera.top + 1f)
        assertFalse("CTA text must not truncate", hasVisualOverflow("gemini-home-cta-label"))
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

        if (scenario == "image_failure") {
            val geometryTags = listOf(
                "gemini-home-top-section", "gemini-home-middle-section", "gemini-home-section-title",
                "gemini-home-carousel-track", "gemini-home-catch-card-nh01-catch-001",
                "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera",
            )
            val beforeFailure = geometryTags.associateWith { rect(it).toDp(profile.density) }
            beginStep("INJECT_IMAGE_DECODE_FAILURE_AFTER_VALID_RENDER")
            val missingPhotoPath = "/data/local/tmp/nh01-intentionally-missing-fish-photo.jpg"
            compose.runOnIdle {
                catchesState.value = catchesState.value.map { it.copy(imageUrl = missingPhotoPath) }
            }
            val failureMediaTag = "gemini-home-catch-media-nh01-catch-001"
            advanceFramesUntil(20_000, timeoutDiagnostic = {
                "image_status=${imageLoadStatus(failureMediaTag)} " +
                    "placeholder_nodes=${nodeCount("gemini-home-catch-image-placeholder-nh01-catch-001")}, " +
                    "rendered_nodes=${nodeCount("home-evidence-backdrop-ready")}, source=$missingPhotoPath"
            }) {
                imageLoadStatus(failureMediaTag) == "FALLBACK"
            }
            val fallbackNode = compose.onNodeWithTag(
                "gemini-home-catch-image-placeholder-nh01-catch-001", useUnmergedTree = true,
            ).fetchSemanticsNode()
            assertEquals("failed fish-photo loading must render the safe placeholder", "FALLBACK",
                fallbackNode.config[GeminiHomeImageLoadStatus])
            assertEquals("terminal media state is observable on the tagged media container", "FALLBACK",
                imageLoadStatus(failureMediaTag))
            assertEquals("failed media may not retain the rendered-image state", 0,
                compose.onAllNodesWithTag("home-evidence-backdrop-ready", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
            val afterFailure = geometryTags.associateWith { rect(it).toDp(profile.density) }
            geometryTags.forEach { tag ->
                val before = requireNotNull(beforeFailure[tag])
                val after = requireNotNull(afterFailure[tag])
                assertTrue("image failure may not change $tag geometry: before=$before after=$after",
                    abs(before.left - after.left) <= 1f && abs(before.top - after.top) <= 1f &&
                        abs(before.right - after.right) <= 1f && abs(before.bottom - after.bottom) <= 1f)
            }
            val fallbackName = "$profileId-${variant.name.lowercase(Locale.US)}-fallback.png"
            val fallbackBitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(output, fallbackName).outputStream().use { sink ->
                assertTrue("image fallback screenshot must save", fallbackBitmap.compress(Bitmap.CompressFormat.PNG, 100, sink))
            }
            fallbackBitmap.recycle()
            val imageFailureReport = JSONObject().apply {
                put("schema", "NH01_ADAPTIVE_VISUAL_BALANCE_DEVICE_CASE_V1")
                put("execution_mode", "ANDROID_API28_RUNTIME")
                put("status", "PASS")
                put("profile_id", profileId)
                put("layout_variant", variant.name)
                put("source_commit", sourceSha)
                put("target_application_id", targetContext.packageName)
                put("system_font_scale", profile.fontScale)
                put("density", profile.density)
                put("density_dpi", compose.activity.resources.displayMetrics.densityDpi)
                put("scenario", scenario)
                put("initial_photo_load_status", initialImageStatus)
                put("data", JSONObject().apply {
                    put("catch_ids", JSONArray(catches.map(RemoteCatch::id)))
                    put("unique_catch_count", catches.map(RemoteCatch::id).toSet().size)
                    put("pager_page_count", 2)
                    put("image_failure_fallback", "PASS_FALLBACK_RENDERED")
                    put("fallback_tag_status", fallbackNode.config[GeminiHomeImageLoadStatus])
                    put("rendered_image_nodes_after_failure", 0)
                })
                put("metrics", JSONObject().apply {
                    put("geometry_unchanged_after_image_failure", true)
                    put("max_component_edge_delta_dp", 1f)
                    put("geometry_tags", JSONArray(geometryTags))
                    put("before_failure_bounds_dp", JSONObject().apply {
                        beforeFailure.forEach { (tag, bounds) -> put(tag, bounds.toJson()) }
                    })
                    put("after_failure_bounds_dp", JSONObject().apply {
                        afterFailure.forEach { (tag, bounds) -> put(tag, bounds.toJson()) }
                    })
                })
                put("screenshots", JSONObject().apply {
                    put("initial", screenshotName)
                    put("image_failure_fallback", fallbackName)
                    put("failure_source", missingPhotoPath)
                })
            }
            File(output, "$profileId-${variant.name.lowercase(Locale.US)}.json")
                .writeText(imageFailureReport.toString(2) + "\n")
            writeCaseStatus("PASS", "IMAGE_FAILURE_FALLBACK_PRESERVED_LAYOUT_GEOMETRY")
            return
        }

        beginStep("VERIFY_CTA_AND_CATCH_INTERACTIONS")
        compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true).performClick()
        assertEquals("camera action remains available", 1, identifyClicks.get())
        compose.onNodeWithTag("gemini-home-section-all", useUnmergedTree = true).performClick()
        assertEquals("all catches action remains available", 1, albumClicks.get())
        compose.onNodeWithTag("gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true).performClick()
        assertEquals("main card click maps to the real catch id", listOf("nh01-catch-001"), catchClicks)
        completeStep("CTA_AND_CATCH_INTERACTIONS_PASSED")

        beginStep("VERIFY_CARD_TEXT_ACCESSIBILITY_AND_SCROLL")
        val hasMiddleScroll = middleScrollEnabled
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && exactM01Exception) {
            assertTrue("320×480 at default font scale must fit without middle scrolling", !hasMiddleScroll)
            assertTrue(
                "${if (m01FeasibilityDeficitDp > 0.5f) "FAIL_CONSTRAINT/SPEC_CONFLICT" else "FAIL_IMPLEMENTATION/FEASIBLE_MINIMUM_NOT_HONORED"}: " +
                    "exact M01 contract requires 145dp × $m01RequiredHeroHeightDp dp Hero; " +
                    "actual=${activeCardLayoutWidthDp}×${activeCardLayoutHeightDp}dp, H_safe=$safeHeightDp, " +
                    "A=$hA, B=$hB, C=$hC, baseGaps=$baseGapBefore/$baseGapAfter, S=$contractSurplus, " +
                    "minimum_safe_height=$m01MinimumRequiredSafeHeightDp deficit=$m01FeasibilityDeficitDp",
                activeCardLayoutWidthDp >= 145f,
            )
            assertTrue("320×480 Hero keeps the 247:293 ratio", abs(ratio / (247f / 293f) - 1f) <= 0.01f)
            val bottomClearance = root.bottom - profile.insets.bottom.value - camera.bottom
            assertTrue("320×480 footer must not add excessive blank space: $bottomClearance dp", bottomClearance <= 4f)
        }
        val cardTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        val scrollStartOffsetPx = scrollOffsetPx()
        val cardLogicalTopBeforeScrollPx = rect("gemini-home-catch-card-nh01-catch-001").top + scrollStartOffsetPx
        cardTextTags.forEach { tag ->
            val interaction = compose.onNodeWithTag(tag, useUnmergedTree = true)
            if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
                if (hasMiddleScroll) interaction.performScrollTo().assertIsDisplayed() else interaction.assertIsDisplayed()
            } else {
                // V3 is a visual comparison control. Preserve and record its text semantics
                // even when its historical short-screen layout requires scrolling.
                assertTrue("V3 baseline retains semantic text node $tag",
                    compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            }
        }
        val initialCardTextSemanticsPresent = cardTextTags.all { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("required first-catch text semantics must exist", initialCardTextSemanticsPresent)
        val firstMetadataLineCount = textLineCount("gemini-home-catch-metadata-nh01-catch-001")
        val firstMetadataLastLineLength = textLastLineCharacterCount("gemini-home-catch-metadata-nh01-catch-001")
        listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        ).forEach { tag ->
            assertTrue("required text must expose measured line ranges: $tag", textLineRanges(tag).isNotBlank())
            assertTrue("required text may occupy only the contract two-line budget: $tag", textLineCount(tag) in 1..2)
        }
        assertEquals("static CTA label remains a single readable line", 1, textLineCount("gemini-home-cta-label"))
        assertTrue("CTA line range must be recorded", textLineRanges("gemini-home-cta-label").isNotBlank())
        val initialTextLayouts = JSONObject().apply {
            listOf(
                "gemini-home-catch-species-nh01-catch-001",
                "gemini-home-catch-measurement-nh01-catch-001",
                "gemini-home-catch-metadata-nh01-catch-001",
                "gemini-home-cta-label",
            ).forEach { tag ->
                put(tag, JSONObject().apply {
                    put("line_count", textLineCount(tag))
                    put("line_ranges", textLineRanges(tag))
                    put("visual_overflow", hasVisualOverflow(tag))
                })
            }
        }
        val scrollMaximumPx = scrollMaximumPx()
        val scrollEndOffsetPx = scrollOffsetPx()
        if (middleScrollEnabled) {
            assertTrue("short-screen scroll range must be observable", scrollMaximumPx > 0)
            assertTrue("performScrollTo must move the actual Middle scroll state before geometry assertions",
                scrollEndOffsetPx > scrollStartOffsetPx)
            val cardLogicalTopAfterScrollPx = rect("gemini-home-catch-card-nh01-catch-001").top + scrollEndOffsetPx
            assertTrue(
                "unclipped LayoutCoordinates plus actual ScrollState offset must recover the same logical card position: " +
                    "before=$cardLogicalTopBeforeScrollPx after=$cardLogicalTopAfterScrollPx range=$scrollMaximumPx",
                abs(cardLogicalTopAfterScrollPx - cardLogicalTopBeforeScrollPx) <= 1.5f,
            )
            assertTrue(
                "visible card bounds must reflect viewport clipping while full layout bounds remain available",
                visibleRect("gemini-home-catch-card-nh01-catch-001").height() <
                    rect("gemini-home-catch-card-nh01-catch-001").height(),
            )
        }
        val scrollMeasurement = JSONObject().apply {
            put("middle_scroll_enabled", middleScrollEnabled)
            put("scroll_offset_before_px", scrollStartOffsetPx)
            put("scroll_offset_after_text_reveal_px", scrollEndOffsetPx)
            put("scroll_maximum_px", scrollMaximumPx)
            put("card_unclipped_bounds_before_px", RectF(
                rect("gemini-home-catch-card-nh01-catch-001").left,
                cardLogicalTopBeforeScrollPx,
                rect("gemini-home-catch-card-nh01-catch-001").right,
                rect("gemini-home-catch-card-nh01-catch-001").bottom + scrollStartOffsetPx,
            ).toJson())
            put("card_unclipped_bounds_after_px", rect("gemini-home-catch-card-nh01-catch-001").toJson())
            put("card_visible_bounds_after_px", visibleRect("gemini-home-catch-card-nh01-catch-001").toJson())
            put("coordinate_reconstruction", "unclipped_window_bounds + observed ScrollState.value")
        }
        if (middleScrollEnabled) {
            compose.onNodeWithTag("gemini-home-section-title", useUnmergedTree = true).performScrollTo()
            advanceFramesUntil(2_000) { scrollOffsetPx() == 0 }
        }
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
            assertTrue("metadata must occupy one or two lines", firstMetadataLineCount in 1..2)
            assertTrue("metadata last line must not be an orphan glyph", firstMetadataLastLineLength >= 2)
        }
        val contentScreenshotName = if (hasMiddleScroll && variant == GeminiHomeLayoutVariant.ADAPTIVE_V2)
            "$profileId-${variant.name.lowercase(Locale.US)}-content-visible.png" else null
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
        waitForPagerSettled(
            targetIndex = 1,
            targetId = "nh01-catch-002",
            density = profile.density,
            expectedLeftId = catchesState.value.getOrNull(0)?.id,
            expectedRightId = catchesState.value.getOrNull(2)?.id,
        )
        assertSingleNode("gemini-home-catch-card-nh01-catch-002")
        assertSingleNode("gemini-home-catch-neighbor-nh01-catch-001")
        assertEquals("the first catch is not duplicated as an active main card after swipe", 0,
            compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-001", useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        if (scenario == "three_unique_catches") {
            assertSingleNode("gemini-home-catch-neighbor-nh01-catch-003")
            assertEquals("the third catch may not be duplicated as a main card", 0,
                compose.onAllNodesWithTag("gemini-home-catch-card-nh01-catch-003", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
            assertEquals("the selected catch may not also be duplicated as a neighbor", 0,
                compose.onAllNodesWithTag("gemini-home-catch-neighbor-nh01-catch-002", useUnmergedTree = true)
                    .fetchSemanticsNodes().size)
        }
        val secondCardTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-002",
            "gemini-home-catch-measurement-nh01-catch-002",
            "gemini-home-catch-metadata-nh01-catch-002",
        )
        secondCardTextTags.forEach { tag ->
            if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
                compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            } else {
                assertTrue("V3 baseline retains semantic text node after swipe: $tag",
                    compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            }
        }
        val secondCardTextSemanticsPresent = secondCardTextTags.all { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("required second-catch text semantics must exist", secondCardTextSemanticsPresent)
        val secondMetadataTag = "gemini-home-catch-metadata-nh01-catch-002"
        advanceFramesUntil(3_000) {
            runCatching { textLineCount(secondMetadataTag) in 1..2 }.getOrDefault(false)
        }
        val secondMetadataLineCount = textLineCount(secondMetadataTag)
        val secondMetadataLastLineLength = textLastLineCharacterCount(secondMetadataTag)
        advanceFramesUntil(3_000) {
            secondCardTextTags.all { tag -> runCatching { textLineRanges(tag).isNotBlank() }.getOrDefault(false) }
        }
        val secondTextLayouts = JSONObject().apply {
            secondCardTextTags.forEach { tag ->
                put(tag, JSONObject().apply {
                    put("line_count", textLineCount(tag))
                    put("line_ranges", textLineRanges(tag))
                    put("visual_overflow", hasVisualOverflow(tag))
                })
            }
        }
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
            assertTrue("long metadata must occupy at most two lines", secondMetadataLineCount in 1..2)
            assertTrue("long metadata cannot end in one isolated CJK glyph or punctuation",
                secondMetadataLastLineLength >= 2)
        }
        val secondCardTextNoVisualOverflow = secondCardTextTags.none(::hasVisualOverflow)
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2) {
            assertTrue("Adaptive V2 must not visually truncate required second-catch text: $secondCardTextTags", secondCardTextNoVisualOverflow)
        }
        requiredCardTextNoVisualOverflow = requiredCardTextNoVisualOverflow && secondCardTextNoVisualOverflow
        val requiredCardTextAccessible = initialCardTextSemanticsPresent && secondCardTextSemanticsPresent
        val activeAfterSwipe = rect("gemini-home-catch-card-nh01-catch-002").toDp(profile.density)
        val leftPeekAfterSwipe = rect("gemini-home-catch-neighbor-nh01-catch-001").toDp(profile.density)
        val trackAfterSwipe = rect("gemini-home-carousel-track").toDp(profile.density)
        val leftPeekOverlapAfterSwipeDp = maxOf(0f, leftPeekAfterSwipe.right - activeAfterSwipe.left)
        val visibleLeftPeekAfterSwipe = visibleRect("gemini-home-catch-neighbor-nh01-catch-001").toDp(profile.density)
        val leftPeekVisibleWidthAfterSwipeDp = (
            minOf(visibleLeftPeekAfterSwipe.right, trackAfterSwipe.right) - maxOf(visibleLeftPeekAfterSwipe.left, trackAfterSwipe.left)
        ).coerceAtLeast(0f)
        val leftPeekEdgeVisibleWidthAfterSwipeDp = if (
            visibleLeftPeekAfterSwipe.left <= trackAfterSwipe.left + 1f && visibleLeftPeekAfterSwipe.right > trackAfterSwipe.left
        ) {
            (minOf(visibleLeftPeekAfterSwipe.right, trackAfterSwipe.right) - trackAfterSwipe.left).coerceAtLeast(0f)
        } else 0f
        val rightPeekAfterSwipe = if (scenario == "three_unique_catches") {
            rect("gemini-home-catch-neighbor-nh01-catch-003").toDp(profile.density)
        } else null
        val visibleRightPeekAfterSwipe = if (scenario == "three_unique_catches") {
            visibleRect("gemini-home-catch-neighbor-nh01-catch-003").toDp(profile.density)
        } else null
        val rightPeekOverlapAfterSwipeDp = rightPeekAfterSwipe?.let {
            maxOf(0f, activeAfterSwipe.right - it.left)
        } ?: 0f
        val rightPeekVisibleWidthAfterSwipeDp = visibleRightPeekAfterSwipe?.let {
            (minOf(it.right, trackAfterSwipe.right) - maxOf(it.left, trackAfterSwipe.left)).coerceAtLeast(0f)
        } ?: 0f
        val rightPeekEdgeVisibleWidthAfterSwipeDp = visibleRightPeekAfterSwipe?.takeIf {
            it.right >= trackAfterSwipe.right - 1f && it.left < trackAfterSwipe.right
        }?.let { (trackAfterSwipe.right - maxOf(it.left, trackAfterSwipe.left)).coerceAtLeast(0f) } ?: 0f
        val widthForRightTargetEdgePeek = 2f * targetPeekWidthDp + 2f * pageSpacingDp +
            2f * activeAfterSwipe.width() - trackAfterSwipe.width()
        val minimumRightWidthToTouchEdge = (
            trackAfterSwipe.width() - 2f * activeAfterSwipe.width() - 2f * pageSpacingDp
        ).coerceAtLeast(0f)
        val requiredRightPeekWidthDp = maxOf(targetPeekWidthDp, widthForRightTargetEdgePeek, minimumRightWidthToTouchEdge)
        val rightEdgePeekFeasibleAfterSwipe = rightPeekAfterSwipe != null &&
            requiredRightPeekWidthDp <= activeAfterSwipe.width() * 0.82f + 1f
        val heroGeometryUnchangedAfterSwipe =
            abs(activeAfterSwipe.width() - activeCard.width()) <= 1f &&
                abs(activeAfterSwipe.height() - activeCard.height()) <= 1f &&
                abs(activeAfterSwipe.centerX() - activeCard.centerX()) <= 1f &&
                abs(activeAfterSwipe.centerY() - activeCard.centerY()) <= 1f
        if (scenario == "three_unique_catches") {
            assertTrue("three-record Hero geometry must remain stable when selecting the center record", heroGeometryUnchangedAfterSwipe)
            assertTrue("right neighbor must not overlap the selected Hero", rightPeekOverlapAfterSwipeDp <= 1f)
            assertTrue("right neighbor remains within the 82% cap", requireNotNull(rightPeekAfterSwipe).width() <= activeAfterSwipe.width() * 0.82f + 1f)
            assertTrue("right neighbor remains visible inside the track", rightPeekVisibleWidthAfterSwipeDp >= minOf(12f, requireNotNull(rightPeekAfterSwipe).width()) - 1f)
            if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && rightEdgePeekFeasibleAfterSwipe) {
                assertTrue("Adaptive V2 center-page right peek exposes the frozen target width",
                    rightPeekEdgeVisibleWidthAfterSwipeDp >= targetPeekWidthDp - 1f)
            }
        }
        val widthForLeftTargetEdgePeek = 2f * targetPeekWidthDp + 2f * pageSpacingDp +
            2f * activeAfterSwipe.width() - trackAfterSwipe.width()
        val minimumLeftWidthToTouchEdge = (
            trackAfterSwipe.width() - 2f * activeAfterSwipe.width() - 2f * pageSpacingDp
        ).coerceAtLeast(0f)
        val requiredLeftPeekWidthDp = maxOf(targetPeekWidthDp, widthForLeftTargetEdgePeek, minimumLeftWidthToTouchEdge)
        val leftEdgePeekFeasibleAfterSwipe = requiredLeftPeekWidthDp <= activeAfterSwipe.width() * 0.82f + 1f
        assertTrue("left peek must not overlap the active card after swipe: active=$activeAfterSwipe left=$leftPeekAfterSwipe",
            leftPeekOverlapAfterSwipeDp <= 1f)
        assertTrue("left peek must remain subordinate after swipe",
            leftPeekAfterSwipe.width() <= activeAfterSwipe.width() * 0.82f + 1f)
        assertTrue("left peek must remain visible inside the track after swipe",
            leftPeekVisibleWidthAfterSwipeDp >= minOf(12f, leftPeekAfterSwipe.width()) - 1f)
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && leftEdgePeekFeasibleAfterSwipe) {
            assertTrue("Adaptive V2 left edge peek must expose the target width after swipe",
                leftPeekEdgeVisibleWidthAfterSwipeDp >= targetPeekWidthDp - 1f)
        }
        val afterSwipeBoundsPx = JSONObject().apply {
            put("gemini-home-carousel-track", rect("gemini-home-carousel-track").toJson())
            put("gemini-home-catch-card-nh01-catch-002", rect("gemini-home-catch-card-nh01-catch-002").toJson())
            put("gemini-home-catch-neighbor-nh01-catch-001", rect("gemini-home-catch-neighbor-nh01-catch-001").toJson())
            if (scenario == "three_unique_catches") {
                put("gemini-home-catch-neighbor-nh01-catch-003", rect("gemini-home-catch-neighbor-nh01-catch-003").toJson())
            }
        }
        val visibleAfterSwipeBoundsPx = JSONObject().apply {
            put("gemini-home-carousel-track", visibleRect("gemini-home-carousel-track").toJson())
            put("gemini-home-catch-card-nh01-catch-002", visibleRect("gemini-home-catch-card-nh01-catch-002").toJson())
            put("gemini-home-catch-neighbor-nh01-catch-001", visibleRect("gemini-home-catch-neighbor-nh01-catch-001").toJson())
            if (scenario == "three_unique_catches") {
                put("gemini-home-catch-neighbor-nh01-catch-003", visibleRect("gemini-home-catch-neighbor-nh01-catch-003").toJson())
            }
        }
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
            put("scenario", scenario)
            put("execution_mode", "ANDROID_API28_RUNTIME")
            put("status", "PASS")
            put("profile_id", profileId)
            put("layout_variant", variant.name)
            put("source_commit", args.getString("source_sha") ?: "MISSING_SOURCE_SHA")
            put("target_application_id", targetContext.packageName)
            put("external_files_directory", output.absolutePath)
            put("system_font_scale", profile.fontScale)
            put("android_animation_scales", JSONObject().apply {
                animationScales.forEach { (setting, value) -> put(setting, value) }
            })
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
                put("pager_page_count", catchesState.value.size)
                put("initial_visible_card_ids", JSONArray(listOf("nh01-catch-001", "nh01-catch-002")))
                put("selected_record_after_swipe", "nh01-catch-002")
                put("left_neighbor_after_swipe", "nh01-catch-001")
                put("right_neighbor_after_swipe", if (scenario == "three_unique_catches") "nh01-catch-003" else JSONObject.NULL)
                put("both_real_neighbors_after_swipe", scenario == "three_unique_catches")
                put("same_time_duplicate_ids", false)
                put("swipe_reached_second_id", true)
                put("middle_scroll_enabled", hasMiddleScroll)
                put("middle_scroll_offset_after_restore_px", scrollOffsetPx())
                put("middle_scroll_maximum_px", scrollMaximumPx())
                put("required_card_text_accessible", requiredCardTextAccessible)
                put("required_card_text_no_visual_overflow", requiredCardTextNoVisualOverflow)
                put("metadata_line_count_initial", firstMetadataLineCount)
                put("metadata_last_line_character_count_initial", firstMetadataLastLineLength)
                put("metadata_line_count_after_swipe", secondMetadataLineCount)
                put("metadata_last_line_character_count_after_swipe", secondMetadataLastLineLength)
                put("scenario", scenario)
                put("safe_width_dp", safeWidthDp)
                put("safe_height_dp", safeHeightDp)
                put("footer_outside_middle", middleSection.bottom <= footer.top + 1f)
            })
            put("metrics", JSONObject().apply {
                put("card_bounds_dp", activeCard.toJson())
                put("card_unclipped_layout_size_dp", JSONArray(listOf(activeCardLayoutWidthDp, activeCardLayoutHeightDp)))
                put("neighbor_bounds_dp", neighborCard.toJson())
                put("card_aspect_ratio", ratio)
                put("card_aspect_error_percent", ratioErrorPercent)
                put("card_target_width_dp", targetCardWidthDp)
                put("card_actual_width_dp", activeCardLayoutWidthDp)
                put("neighbor_width_dp", neighborCard.width())
                put("neighbor_width_fraction", neighborWidthFraction)
                put("rendered_font_sizes_sp", renderedFontSizesSp)
                put("peek_card_overlap_dp", neighborOverlapDp)
                put("peek_visible_width_dp", rightPeekVisibleWidthDp)
                put("peek_edge_visible_width_dp", rightEdgePeekVisibleWidthDp)
                put("peek_edge_target_dp", targetPeekWidthDp)
                put("peek_edge_feasible", edgePeekFeasible)
                put("left_peek_after_swipe_width_dp", leftPeekAfterSwipe.width())
                put("left_peek_after_swipe_overlap_dp", leftPeekOverlapAfterSwipeDp)
                put("left_peek_after_swipe_visible_width_dp", leftPeekVisibleWidthAfterSwipeDp)
                put("left_peek_after_swipe_edge_visible_width_dp", leftPeekEdgeVisibleWidthAfterSwipeDp)
                put("left_peek_after_swipe_edge_feasible", leftEdgePeekFeasibleAfterSwipe)
                put("right_peek_after_swipe_bounds_dp", rightPeekAfterSwipe?.toJson() ?: JSONObject.NULL)
                put("right_peek_after_swipe_width_dp", rightPeekAfterSwipe?.width() ?: 0f)
                put("right_peek_after_swipe_overlap_dp", rightPeekOverlapAfterSwipeDp)
                put("right_peek_after_swipe_visible_width_dp", rightPeekVisibleWidthAfterSwipeDp)
                put("right_peek_after_swipe_edge_visible_width_dp", rightPeekEdgeVisibleWidthAfterSwipeDp)
                put("right_peek_after_swipe_edge_feasible", rightEdgePeekFeasibleAfterSwipe)
                put("hero_geometry_unchanged_after_swipe", heroGeometryUnchangedAfterSwipe)
                put("pager_state_after_swipe", pagerSnapshot())
                put("middle_scroll_measurement", scrollMeasurement)
                put("card_shrunk_for_height", activeCardLayoutWidthDp < targetCardWidthDp - 1f)
                put("section_title_to_card_gap_dp", sectionTitleCardGap)
                put("section_title_to_card_frozen_target_dp", frozenTitleCardGapDp ?: JSONObject.NULL)
                put("section_spacing_contract", "NH01_GEMINI_ADAPTIVE_V2_UNIFIED_V1")
                put("card_to_footer_gap_dp", cardToFooterGap)
                put("header_to_stats_gap_dp", headerToStatsGap)
                put("stats_to_title_gap_dp", statsToTitleGap)
                put("surplus_contract_dp", contractSurplus)
                put("expected_equal_gap_addition_dp", expectedEqualGapAddition)
                put("actual_stats_to_title_addition_dp", actualGapBeforeAddition)
                put("actual_card_to_footer_addition_dp", actualGapAfterAddition)
                put("hA_top_dp", hA)
                put("hB_middle_dp", hB)
                put("hC_footer_dp", hC)
                put("base_gAB_dp", baseGapBefore)
                put("base_gBC_dp", baseGapAfter)
                put("card_track_horizontal_center_error_dp", centerOffset)
                put("cta_camera_overlap", cta.bottom > camera.top)
                put("camera_safe_bottom_clearance_dp", root.bottom - profile.insets.bottom.value - camera.bottom)
                put("brand_bounds_dp", brand.toJson())
                put("stats_bounds_dp", stats.toJson())
                put("footer_bounds_dp", footer.toJson())
                put("camera_bounds_dp", camera.toJson())
                put("cta_bounds_dp", cta.toJson())
                put("section_action_bounds_dp", sectionAction.toJson())
                put("avatar_touch_bounds_dp", avatar.toJson())
                put("middle_bounds_dp", middleSection.toJson())
                put("top_bounds_dp", topSection.toJson())
                put("actual_insets_consumption", "safe dimensions subtract insets once; parent content Column applies the same Insets once")
                put("text_layouts", initialTextLayouts)
                put("text_layouts_after_swipe", secondTextLayouts)
            })
            put("layout_bounds_unclipped_window_px", boundsPx)
            put("visible_bounds_clipped_window_px", visibleBoundsPx)
            put("layout_bounds_after_swipe_unclipped_window_px", afterSwipeBoundsPx)
            put("visible_bounds_after_swipe_clipped_window_px", visibleAfterSwipeBoundsPx)
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
                report.put("failure_semantics_snapshot", failureSemanticsSnapshot())
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

    private fun failureSemanticsSnapshot(): JSONObject = JSONObject().apply {
        listOf(
            "gemini-home-screen",
            "gemini-home-catch-card-nh01-catch-001",
            "home-evidence-backdrop-ready",
            "home-evidence-backdrop-crop-fallback",
            "home-evidence-fit-foreground",
            "fish-photo-subject-crop",
        ).forEach { tag ->
            val count = runCatching {
                compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
            }.getOrDefault(-1)
            put(tag, count)
        }
        put("activity_is_finishing", runCatching { compose.activity.isFinishing }.getOrNull())
        put("activity_is_destroyed", runCatching { compose.activity.isDestroyed }.getOrNull())
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
                speciesName = "长吻鮠（黄颡鱼）", confidence = 0.88f, modelVersion = "nh01-layout-fixture",
                capturedAt = timestamp, createdAt = timestamp, lengthCm = 31f, weightKg = 0.84f,
                location = "上海市青浦区岑卜村东岸临时钓位定位点地址编号321号",
            ),
            RemoteCatch(
                id = "nh01-catch-003", imageUrl = imagePath, speciesId = "species-koi-carp",
                speciesName = "鲤鱼", confidence = 0.91f, modelVersion = "nh01-layout-fixture",
                capturedAt = timestamp, createdAt = timestamp, lengthCm = 35f, weightKg = 1.12f,
                location = "江苏 · 太湖",
            ),
        )
    }

    private fun assertSingleNode(tag: String) {
        assertEquals("$tag must appear once", 1,
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun hasVisualOverflow(tag: String): Boolean {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.config[GeminiHomeTextHasVisualOverflow]
    }

    private fun fontSizeSp(tag: String): Float {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.config[GeminiHomeRenderedFontSizeSp]
    }

    private fun textLineCount(tag: String): Int {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.config[GeminiHomeTextLineCount]
    }

    private fun textLastLineCharacterCount(tag: String): Int {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.config[GeminiHomeTextLastLineCharacterCount]
    }

    private fun textLineRanges(tag: String): String {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.config[GeminiHomeTextLineRanges]
    }

    private fun advanceFramesUntil(timeoutMillis: Long, condition: () -> Boolean) {
        advanceFramesUntil(timeoutMillis, timeoutDiagnostic = { "No additional state was provided" }, condition = condition)
    }

    private fun advanceFramesUntil(
        timeoutMillis: Long,
        timeoutDiagnostic: () -> String,
        condition: () -> Boolean,
    ) {
        val frames = (timeoutMillis / 16L).coerceAtLeast(1L)
        repeat(frames.toInt()) {
            if (condition()) return
            compose.mainClock.advanceTimeByFrame()
            // The production HomeMotionState intentionally ticks every frame.
            // Do not wait for idle while the frame clock is running.
            Thread.sleep(16L)
        }
        assertTrue("Observed UI condition did not become true before timeout: ${timeoutDiagnostic()}", condition())
    }

    private fun rect(tag: String): RectF {
        val bounds = GeminiHomeGeometryProbe.bounds(tag).unclippedWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun visibleRect(tag: String): RectF {
        val bounds = GeminiHomeGeometryProbe.bounds(tag).visibleWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun touchRect(tag: String): RectF {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun imageLoadStatus(tag: String): String? = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().config[GeminiHomeImageLoadStatus]
    }.getOrNull()

    private fun nodeCount(tag: String): Int = runCatching {
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    }.getOrDefault(-1)

    private fun scrollOffsetPx(): Int = runCatching {
        compose.onNodeWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[GeminiHomeScrollOffsetPx]
    }.getOrDefault(0)

    private fun scrollMaximumPx(): Int = runCatching {
        compose.onNodeWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
            .fetchSemanticsNode().config[GeminiHomeScrollMaximumPx]
    }.getOrDefault(0)

    private fun pagerSnapshot(): JSONObject = runCatching {
        val state = compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
            .fetchSemanticsNode().config
        val currentId = runCatching { state[GeminiHomePagerCurrentRecordId] }.getOrNull()
        val activeBounds = currentId?.let { id ->
            runCatching { rect("gemini-home-catch-card-$id").toJson() }.getOrNull()
        }
        JSONObject().apply {
            put("current_page", runCatching { state[GeminiHomePagerCurrentPage] }.getOrNull() ?: JSONObject.NULL)
            put("target_page", runCatching { state[GeminiHomePagerTargetPage] }.getOrNull() ?: JSONObject.NULL)
            put("settled_page", runCatching { state[GeminiHomePagerSettledPage] }.getOrNull() ?: JSONObject.NULL)
            put("is_scroll_in_progress", runCatching { state[GeminiHomePagerScrollInProgress] }.getOrNull() ?: JSONObject.NULL)
            put("current_page_offset_fraction", runCatching { state[GeminiHomePagerOffsetFraction] }.getOrNull() ?: JSONObject.NULL)
            put("current_record_id", currentId ?: JSONObject.NULL)
            put("left_record_id", runCatching { state[GeminiHomePagerLeftRecordId] }.getOrNull() ?: JSONObject.NULL)
            put("right_record_id", runCatching { state[GeminiHomePagerRightRecordId] }.getOrNull() ?: JSONObject.NULL)
            put("state_description", state[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
            put("scroll_offset_px", scrollOffsetPx())
            put("active_card_bounds_unclipped_window_px", activeBounds ?: JSONObject.NULL)
            put("active_card_node_count", currentId?.let { nodeCount("gemini-home-catch-card-$it") } ?: 0)
        }
    }.getOrElse { error ->
        JSONObject().put("snapshot_error", error.toString())
    }

    private fun waitForPagerSettled(
        targetIndex: Int,
        targetId: String,
        density: Float,
        expectedLeftId: String? = null,
        expectedRightId: String? = null,
        timeoutMillis: Long = 8_000,
    ) {
        advanceFramesUntil(timeoutMillis / 2, timeoutDiagnostic = {
            "Pager did not acknowledge target index $targetIndex/id=$targetId; observed=${pagerSnapshot()}"
        }) {
            runCatching {
                compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                    .fetchSemanticsNode().config[GeminiHomePagerTargetPage] == targetIndex
            }.getOrDefault(false)
        }
        var consecutiveStableFrames = 0
        advanceFramesUntil(timeoutMillis, timeoutDiagnostic = { pagerSnapshot().toString() }) {
            val state = runCatching {
                compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                    .fetchSemanticsNode().config
            }.getOrNull()
            if (state == null) {
                consecutiveStableFrames = 0
                false
            } else {
                val current = runCatching { state[GeminiHomePagerCurrentPage] }.getOrNull()
                val target = runCatching { state[GeminiHomePagerTargetPage] }.getOrNull()
                val settled = runCatching { state[GeminiHomePagerSettledPage] }.getOrNull()
                val inProgress = runCatching { state[GeminiHomePagerScrollInProgress] }.getOrNull()
                val offset = runCatching { state[GeminiHomePagerOffsetFraction] }.getOrNull()
                val currentId = runCatching { state[GeminiHomePagerCurrentRecordId] }.getOrNull()
                val leftId = runCatching { state[GeminiHomePagerLeftRecordId] }.getOrNull()
                val rightId = runCatching { state[GeminiHomePagerRightRecordId] }.getOrNull()
                val activeTag = "gemini-home-catch-card-$targetId"
                val geometryStable = runCatching {
                    nodeCount(activeTag) == 1 &&
                        abs(rect(activeTag).centerX() - rect("gemini-home-carousel-track").centerX()) <= 2f * density
                }.getOrDefault(false)
                val stateStable = current == targetIndex && target == targetIndex && settled == targetIndex &&
                    inProgress == false && (offset?.let { abs(it) <= 0.001f } == true) && currentId == targetId &&
                    leftId == expectedLeftId && rightId == expectedRightId
                if (stateStable && geometryStable) consecutiveStableFrames++ else consecutiveStableFrames = 0
                consecutiveStableFrames >= 3
            }
        }
    }

    private fun RectF.toDp(density: Float) = RectF(left / density, top / density, right / density, bottom / density)

    private fun RectF.toJson() = JSONObject().apply {
        put("left", left); put("top", top); put("right", right); put("bottom", bottom)
        put("width", width()); put("height", height())
    }

    private data class RuntimeDensityProbe(val density: Float, val fontScale: Float, val insets: SafeDrawingInsetsDp)
}
