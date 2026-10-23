package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.DisplayMetrics
import android.os.Build
import android.provider.Settings
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
    // Keep the activity alive while TestWatcher.failed captures the screenshot
    // and semantics snapshot. If the watcher is outside Compose, Compose tears
    // down its activity first and the failure screenshot can show the launcher.
    val rules = RuleChain.outerRule(compose).around(evidenceWatcher)

    @Test
    fun capturesActualWindowWithTwoUniqueCatches() {
        val args = InstrumentationRegistry.getArguments()
        val profileId = requireNotNull(args.getString("profile_id")) { "profile_id is required" }
        val variant = GeminiHomeLayoutVariant.valueOf(
            args.getString("layout_variant") ?: GeminiHomeLayoutVariant.ADAPTIVE_V2.name,
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(targetContext.getExternalFilesDir(null)), "nh01-adaptive-visual-balance-v2")
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
        advanceFramesUntil(20_000) {
            compose.onAllNodesWithTag("home-evidence-backdrop-ready", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        completeStep("FISH_PHOTO_RENDERED")

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
        val initialMetadataText = compose.onNodeWithTag(
            "gemini-home-catch-metadata-nh01-catch-001", useUnmergedTree = true,
        ).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text
        ].joinToString(separator = "\n") { it.text }
        assertTrue(
            "metadata must preserve the capture date in accessible text: $initialMetadataText",
            initialMetadataText.contains("05月29日 04:26"),
        )
        // Location is optional on the card and may be shortened at compact
        // widths; its full value remains in the source record and detail view.
        // At regular widths this short fixture must still be visible when it fits.
        if (profileId in setOf("M07", "M10") && expectedFontScaleValue == 1.0f) {
            assertTrue(
                "metadata location must remain visible when this regular-width fixture fits: $initialMetadataText",
                initialMetadataText.contains("浙江 · 千岛湖"),
            )
        }
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
            "gemini-home-section-title", "gemini-home-section-all-label", "gemini-home-middle-section", "gemini-home-carousel-track",
            "gemini-home-base-gap-a-to-b", "gemini-home-surplus-a-to-b", "gemini-home-section-header-row",
            "gemini-home-title-to-hero-gap", "gemini-home-base-gap-b-to-c", "gemini-home-surplus-b-to-c",
            "gemini-home-stat-species-value", "gemini-home-stat-species-label",
            "gemini-home-stat-catches-value", "gemini-home-stat-catches-label",
            "gemini-home-stat-record-days-value", "gemini-home-stat-record-days-label",
            "gemini-home-catch-card-nh01-catch-001", "gemini-home-catch-neighbor-nh01-catch-002",
            "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera", "gemini-home-camera-art",
            "gemini-home-catch-species-nh01-catch-001", "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
        val boundsPx = JSONObject().apply { tags.forEach { put(it, rect(it).toJson()) } }
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
        val middle = rect("gemini-home-middle-section").toDp(profile.density)
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
        val cameraArt = rect("gemini-home-camera-art").toDp(profile.density)
        val expectedCameraArtSize = geminiHomeCameraVisualSize(safeWidthDp, safeHeightDp)
        val brand = rect("gemini-home-brand-title").toDp(profile.density)
        val stats = rect("gemini-home-stats-bar").toDp(profile.density)
        val baseGapAToB = rect("gemini-home-base-gap-a-to-b").toDp(profile.density)
        val extraGapAToB = rect("gemini-home-surplus-a-to-b").toDp(profile.density)
        val sectionHeaderRow = rect("gemini-home-section-header-row").toDp(profile.density)
        val titleToHeroSpacer = rect("gemini-home-title-to-hero-gap").toDp(profile.density)
        val baseGapBToC = rect("gemini-home-base-gap-b-to-c").toDp(profile.density)
        val extraGapBToC = rect("gemini-home-surplus-b-to-c").toDp(profile.density)
        val sectionTitleCardGap = activeCard.top - title.bottom
        val statsToTitleGap = title.top - stats.bottom
        val statsToTitleBaseGap = statsToTitleGap - extraGapAToB.height()
        val cardToFooterGap = footer.top - activeCard.bottom
        val cardToFooterBaseGap = cardToFooterGap - extraGapBToC.height()
        val headerToStatsGap = stats.top - header.bottom
        val hasMiddleScroll = compose.onAllNodesWithTag("gemini-home-short-screen-scroll", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        val requiredMiddleContentHeight = baseGapAToB.height() + sectionHeaderRow.height() +
            titleToHeroSpacer.height() + track.height() + baseGapBToC.height()
        val measuredAvailableSurplus = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && !hasMiddleScroll) {
            (middle.height() - requiredMiddleContentHeight).coerceAtLeast(0f)
        } else {
            0f
        }
        val expectedSurplus = geminiHomeAllocateInterBandSurplus(measuredAvailableSurplus)
        Log.i(
            "NH01_NATIVE_GAP_METRICS",
            JSONObject().apply {
                put("profile_id", profileId)
                put("variant", variant.name)
                put("safe_width_dp", safeWidthDp)
                put("safe_height_dp", safeHeightDp)
                put("middle_height_dp", middle.height())
                put("required_middle_content_height_dp", requiredMiddleContentHeight)
                put("measured_available_surplus_dp", measuredAvailableSurplus)
                put("has_middle_scroll", hasMiddleScroll)
                put("base_a_to_b_tag_dp", baseGapAToB.height())
                put("extra_a_to_b_tag_dp", extraGapAToB.height())
                put("visible_a_to_b_gap_dp", statsToTitleGap)
                put("base_b_to_c_tag_dp", baseGapBToC.height())
                put("extra_b_to_c_tag_dp", extraGapBToC.height())
                put("visible_b_to_c_gap_dp", cardToFooterGap)
                put("policy_a_to_b_dp", layoutSpec.statsToTitleGap?.value)
                put("policy_b_to_c_dp", layoutSpec.cardToFooterGap?.value)
            }.toString(),
        )
        val centerOffset = abs(activeCard.centerX() - track.centerX())
        val ratio = activeCardLayoutWidthDp / activeCardLayoutHeightDp
        val neighborWidthFraction = neighborCard.width() / activeCardLayoutWidthDp
        val neighborOverlapDp = maxOf(0f, activeCard.right - neighborCard.left)
        val targetPeekWidthDp = (track.width() * 0.05f).coerceIn(12f, 28f)
        val pageSpacingDp = (track.width() * 0.032f).coerceIn(10f, 16f)
        val widthForTargetEdgePeek = 2f * targetPeekWidthDp + 2f * pageSpacingDp +
            2f * activeCardLayoutWidthDp - track.width()
        val minimumWidthToTouchEdge = (track.width() - 2f * activeCardLayoutWidthDp - 2f * pageSpacingDp).coerceAtLeast(0f)
        val requiredNeighborWidthDp = maxOf(targetPeekWidthDp, widthForTargetEdgePeek, minimumWidthToTouchEdge)
        val edgePeekFeasible = requiredNeighborWidthDp <= activeCardLayoutWidthDp * 0.82f + 1f
        val rightPeekVisibleWidthDp = (
            minOf(neighborCard.right, track.right) - maxOf(neighborCard.left, track.left)
        ).coerceAtLeast(0f)
        val rightEdgePeekVisibleWidthDp = if (neighborCard.right >= track.right - 1f && neighborCard.left < track.right) {
            (track.right - maxOf(neighborCard.left, track.left)).coerceAtLeast(0f)
        } else 0f
        val cardWidthFraction = when (variant) {
            GeminiHomeLayoutVariant.ADAPTIVE_V1 -> 0.76f
            GeminiHomeLayoutVariant.ADAPTIVE_V2 -> 0.70f
            GeminiHomeLayoutVariant.V3_BASELINE -> 0.686f
        }
        val cardWidthCap = if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V1) 360f else 337.2f
        val targetCardWidthDp = minOf(minOf(track.width(), 560f) * cardWidthFraction, cardWidthCap)

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
            assertTrue("A→B base gap follows the active policy after removing the measured surplus: $statsToTitleBaseGap dp",
                abs(statsToTitleBaseGap - requireNotNull(layoutSpec.statsToTitleGap).value) <= 2f)
            if (!hasMiddleScroll) {
                assertTrue("B→C base gap follows the active policy after removing the measured surplus: $cardToFooterBaseGap dp",
                    abs(cardToFooterBaseGap - requireNotNull(layoutSpec.cardToFooterGap).value) <= 2f)
            }
            val expectedBrandToStatsGap = if (safeHeightDp < 460f) {
                4f
            } else {
                requireNotNull(layoutSpec.brandToStatsGap).value
            }
            assertTrue(
                "brand-to-stats gap follows the active layout policy: expected=$expectedBrandToStatsGap actual=$headerToStatsGap dp",
                abs(headerToStatsGap - expectedBrandToStatsGap) <= 2f,
            )
        }
        assertTrue("measured A→B surplus matches the production allocator: expected=${expectedSurplus.aToB} actual=${extraGapAToB.height()} dp",
            abs(extraGapAToB.height() - expectedSurplus.aToB) <= 1f)
        assertTrue("measured B→C surplus matches the production allocator: expected=${expectedSurplus.bToC} actual=${extraGapBToC.height()} dp",
            abs(extraGapBToC.height() - expectedSurplus.bToC) <= 1f)
        assertTrue("native surplus is split 1:1: A→B=${extraGapAToB.height()} B→C=${extraGapBToC.height()} dp",
            abs(extraGapAToB.height() - extraGapBToC.height()) <= 1f)
        if (hasMiddleScroll || variant != GeminiHomeLayoutVariant.ADAPTIVE_V2 || measuredAvailableSurplus <= 0f) {
            assertTrue("no surplus is injected for a scrolling, non-V2, or zero-surplus layout",
                extraGapAToB.height() <= 1f && extraGapBToC.height() <= 1f)
        }
        assertTrue("camera touch target remains at least 72dp", camera.width() >= 72f && camera.height() >= 72f)
        assertTrue(
            "visible camera artwork follows width/height-aware policy: expected=$expectedCameraArtSize dp actual=$cameraArt",
            abs(cameraArt.width() - expectedCameraArtSize) <= 2f &&
                abs(cameraArt.height() - expectedCameraArtSize) <= 2f,
        )
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
        if (variant == GeminiHomeLayoutVariant.ADAPTIVE_V2 && profileId == "M01" && expectedFontScale == "1.0") {
            assertTrue("320×480 at default font scale must fit without middle scrolling", !hasMiddleScroll)
            assertTrue("320×480 main card must remain readable, width=$activeCardLayoutWidthDp dp",
                activeCardLayoutWidthDp >= 145f)
            val bottomClearance = root.bottom - profile.insets.bottom.value - camera.bottom
            assertTrue("320×480 footer must not add excessive blank space: $bottomClearance dp", bottomClearance <= 4f)
        }
        val cardTextTags = listOf(
            "gemini-home-catch-species-nh01-catch-001",
            "gemini-home-catch-measurement-nh01-catch-001",
            "gemini-home-catch-metadata-nh01-catch-001",
        )
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
        advanceFramesUntil(8_000) {
            val pageIsSecondCatch = runCatching {
                compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                    .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
                    .contains("nh01-catch-002")
            }.getOrDefault(false)
            if (!pageIsSecondCatch) {
                false
            } else {
                // currentPage changes near the midpoint of the pager animation.
                // Wait for the second card to settle at the track center before asserting visibility.
                runCatching {
                    val active = rect("gemini-home-catch-card-nh01-catch-002").toDp(profile.density)
                    val track = rect("gemini-home-carousel-track").toDp(profile.density)
                    abs(active.centerX() - track.centerX()) <= 2f
                }.getOrDefault(false)
            }
        }
        assertSingleNode("gemini-home-catch-card-nh01-catch-002")
        assertSingleNode("gemini-home-catch-neighbor-nh01-catch-001")
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
        val leftPeekVisibleWidthAfterSwipeDp = (
            minOf(leftPeekAfterSwipe.right, trackAfterSwipe.right) - maxOf(leftPeekAfterSwipe.left, trackAfterSwipe.left)
        ).coerceAtLeast(0f)
        val leftPeekEdgeVisibleWidthAfterSwipeDp = if (
            leftPeekAfterSwipe.left <= trackAfterSwipe.left + 1f && leftPeekAfterSwipe.right > trackAfterSwipe.left
        ) {
            (minOf(leftPeekAfterSwipe.right, trackAfterSwipe.right) - trackAfterSwipe.left).coerceAtLeast(0f)
        } else 0f
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
                put("pager_page_count", 2)
                put("initial_visible_card_ids", JSONArray(listOf("nh01-catch-001", "nh01-catch-002")))
                put("same_time_duplicate_ids", false)
                put("swipe_reached_second_id", true)
                put("middle_scroll_enabled", hasMiddleScroll)
                put("required_card_text_accessible", requiredCardTextAccessible)
                put("required_card_text_no_visual_overflow", requiredCardTextNoVisualOverflow)
                put("metadata_line_count_initial", firstMetadataLineCount)
                put("metadata_last_line_character_count_initial", firstMetadataLastLineLength)
                put("metadata_line_count_after_swipe", secondMetadataLineCount)
                put("metadata_last_line_character_count_after_swipe", secondMetadataLastLineLength)
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
                put("card_shrunk_for_height", activeCardLayoutWidthDp < targetCardWidthDp - 1f)
                put("section_title_to_card_gap_dp", sectionTitleCardGap)
                put("section_title_to_card_frozen_target_dp", frozenTitleCardGapDp ?: JSONObject.NULL)
                put("section_spacing_contract", "NH01_GEMINI_ADAPTIVE_V2_UNIFIED_V1_1")
                put("available_interband_surplus_dp", measuredAvailableSurplus)
                put("a_to_b_base_gap_dp", statsToTitleBaseGap)
                put("a_to_b_extra_surplus_dp", extraGapAToB.height())
                put("a_to_b_expected_extra_surplus_dp", expectedSurplus.aToB)
                put("b_to_c_base_gap_dp", cardToFooterBaseGap)
                put("b_to_c_extra_surplus_dp", extraGapBToC.height())
                put("b_to_c_expected_extra_surplus_dp", expectedSurplus.bToC)
                put("interband_surplus_equal_within_1dp", abs(extraGapAToB.height() - extraGapBToC.height()) <= 1f)
                put("card_to_footer_gap_dp", cardToFooterGap)
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
            put("bounds_after_swipe_in_window_px", afterSwipeBoundsPx)
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

    private fun advanceFramesUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val frames = (timeoutMillis / 16L).coerceAtLeast(1L)
        repeat(frames.toInt()) {
            if (condition()) return
            compose.mainClock.advanceTimeByFrame()
            // The production HomeMotionState intentionally ticks every frame.
            // Do not wait for idle while the frame clock is running.
            Thread.sleep(16L)
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
