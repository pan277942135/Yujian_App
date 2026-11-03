package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.MainActivity
import com.yujian.ai.catches.GuestCatchRepository
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImageRuntimeProbe
import kotlinx.coroutines.runBlocking
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
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Exercises the installed app through MainActivity and its guest repository-backed Home route. */
@RunWith(AndroidJUnit4::class)
class GeminiHomeDeviceMatrixTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    private var profileId = "UNKNOWN"
    private var sourceSha = "UNKNOWN_SOURCE_SHA"
    private var expectedFontScale = "UNKNOWN"
    private var expectedDisplay = "UNKNOWN"
    private var currentStep = "TEST_RULE_START"
    private var lastSuccessfulStep = "TEST_NOT_STARTED"
    private var activeOutput: File? = null
    private var caseStartedAt = 0L

    private val evidenceWatcher = object : TestWatcher() {
        override fun starting(description: Description) {
            val args = InstrumentationRegistry.getArguments()
            profileId = args.getString("profile_id") ?: "UNKNOWN"
            sourceSha = args.getString("source_sha") ?: "UNKNOWN_SOURCE_SHA"
            expectedFontScale = args.getString("font_scale") ?: "UNKNOWN"
            expectedDisplay = args.getString("expected_display_px") ?: "UNKNOWN"
            currentStep = "PRODUCTION_ACTIVITY_CREATED"
            lastSuccessfulStep = "ANDROID_COMPOSE_RULE_STARTED_MAIN_ACTIVITY"
            caseStartedAt = System.currentTimeMillis()
            activeOutput = runCatching {
                File(
                    requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
                    "nh01-adaptive-visual-balance-v2",
                ).apply { mkdirs() }
            }.getOrNull()
            caseReportFile()?.delete()
            captureStartupScreenshot()
            writeCaseStatus("RUNNING", currentStep)
            Log.i("NH01_MATRIX_CASE", "START profile=$profileId variant=ADAPTIVE_V2 sha=$sourceSha activity=MainActivity")
        }

        override fun succeeded(description: Description) {
            val report = caseReportFile()?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            if (report?.optString("status") != "PASS") {
                writeCaseStatus(
                    "FAIL",
                    "TEST_RULE_SUCCESS_WITHOUT_PASS_REPORT",
                    AssertionError("Instrumentation completed without a PASS device report"),
                )
            }
            GeminiHomeGeometryProbe.endCapture()
            RemoteImageRuntimeProbe.endCapture()
        }

        override fun failed(e: Throwable, description: Description) {
            captureFailureScreenshot()
            writeCaseStatus("FAIL", currentStep, e)
            GeminiHomeGeometryProbe.endCapture()
            RemoteImageRuntimeProbe.endCapture()
            Log.e(
                "NH01_MATRIX_CASE",
                "FAIL profile=$profileId variant=ADAPTIVE_V2 stage=$currentStep last_successful_step=$lastSuccessfulStep",
                e,
            )
        }
    }

    @get:Rule
    // Compose remains alive until the watcher has captured any failure screenshot and diagnostics.
    val rules = RuleChain.outerRule(compose).around(evidenceWatcher)

    @Test
    fun capturesProductionHomeForRepresentativeDevice() {
        val args = InstrumentationRegistry.getArguments()
        assertTrue("only M04/M07/M10 are in this run: $profileId", profileId in setOf("M04", "M07", "M10"))
        assertEquals("the runtime gate exercises ADAPTIVE_V2 only", "ADAPTIVE_V2", args.getString("layout_variant"))
        assertEquals("only the three-catch production fixture is supported", "three_unique_catches", args.getString("scenario"))
        expectedFontScale = requireNotNull(args.getString("font_scale"))
        expectedDisplay = requireNotNull(args.getString("expected_display_px"))
        val expectedSize = expectedDisplay.split("x").map(String::toInt)
        assertEquals("expected native viewport is width x height", 2, expectedSize.size)

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = GuestCatchRepository(targetContext)
        val catches = orderedHomeRecords(runBlocking { repository.listCatches() })
        val statistics = repository.statistics(catches)
        assertEquals("fixture is injected before MainActivity is measured", 3, catches.size)
        assertEquals("fixture records use unique persisted IDs", 3, catches.map(RemoteCatch::id).toSet().size)
        assertEquals(
            "the production archive contains exactly the deterministic NH01 IDs",
            setOf("nh01-catch-001", "nh01-catch-002", "nh01-catch-003"),
            catches.map { it.clientRecordId.orEmpty() }.toSet(),
        )
        assertTrue("all three real repository photo files exist", catches.all { File(it.imageUrl).isFile })
        val initialCatch = catches[0]
        val nextCatch = catches[1]
        val finalCatch = catches[2]
        assertEquals("latest seeded record is the frozen first card", "nh01-catch-001", initialCatch.clientRecordId)
        assertEquals("草鱼", initialCatch.speciesName)
        assertEquals(42.6f, requireNotNull(initialCatch.lengthCm), 0.01f)
        assertEquals(1.28f, requireNotNull(initialCatch.weightKg), 0.01f)
        assertEquals("浙江 · 千岛湖", initialCatch.location)
        assertEquals("fixture statistics are derived from repository data", 3, statistics.totalCatches)
        assertEquals("fixture contains three species", 3, statistics.speciesCount)
        assertEquals("fixture contains one capture day", 1, statistics.recordDays)
        completeStep("DETERMINISTIC_REPOSITORY_FIXTURE_VERIFIED")

        assertEquals("production route is MainActivity", MainActivity::class.java, compose.activity::class.java)
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
        animationScales.forEach { (key, value) -> assertEquals("$key was disabled once by the runner", 0f, value, 0f) }

        // Start the read-only probes before recreating the same production Activity so the
        // probes observe the real first composition and image decode, not a synthetic screen.
        compose.mainClock.autoAdvance = false
        GeminiHomeGeometryProbe.beginCapture()
        RemoteImageRuntimeProbe.beginCapture()
        beginStep("RECREATE_PRODUCTION_MAIN_ACTIVITY_WITH_SEEDED_REPOSITORY")
        compose.activityRule.scenario.recreate()
        awaitUi(30_000, "production Home route and seeded card") {
            compose.onAllNodesWithTag("gemini-home-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("gemini-home-catch-card-${initialCatch.id}", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        awaitUi(30_000, "seeded fish photo rendered") {
            val state = RemoteImageRuntimeProbe.snapshot(initialCatch.id)
            state?.loadStatus == "READY" && state.rendered == true
        }
        awaitPager(0, initialCatch.id, null, nextCatch.id)
        completeStep("PRODUCTION_HOME_FIXTURE_AND_PHOTO_RENDERED")

        val initialScreenshotName = "${profileId}_${expectedDisplay}_initial.png"
        val afterSwipeScreenshotName = "${profileId}_${expectedDisplay}_after_swipe.png"
        val initialCapture = captureScreenshotSet(initialScreenshotName)
        val initialScreenshot = initialCapture.canonical
        val initialHierarchy = writeHierarchy("${profileId}_initial_hierarchy.txt")
        val initialBounds = componentBounds(initialCatch.id, nextCatch.id, null)
        val initialVisibleBounds = componentVisibleBounds(initialCatch.id, null, nextCatch.id)
        updateReport("screenshots", JSONObject().apply {
            put("startup", "${profileId}_${expectedDisplay}_startup.png")
            put("initial", initialScreenshot.name)
        })
        updateReport("pre_assertion_evidence", JSONObject().apply {
            put("phase", "production_home_and_fixture_visible_before_layout_assertions")
            put("route", "home")
            put("activity", compose.activity.javaClass.name)
            put("screenshot", initialScreenshot.name)
            put("ui_hierarchy", initialHierarchy.name)
            put("component_bounds", initialBounds)
            put("window_geometry", viewWindowSnapshot())
            put("display_metrics", runCatching { runtimeMetricsJson(runtimeMetrics()) }
                .getOrElse { JSONObject().put("error", it.toString()) })
            put("screenshot_sources", initialCapture.sources)
        })
        completeStep("INITIAL_PRODUCTION_HOME_SCREENSHOT_AND_BOUNDS_CAPTURED")

        beginStep("VERIFY_INITIAL_SCREENSHOT_VIEWPORT_PIPELINE")
        assertScreenshotCapture(initialCapture, expectedSize)
        completeStep("INITIAL_SCREENSHOT_VIEWPORT_PIPELINE_PASSED")

        beginStep("ASSERT_PRODUCTION_ROUTE_FIXTURE_AND_CONTENT")
        assertEquals("API 28 runtime", 28, Build.VERSION.SDK_INT)
        assertTrue("MainActivity resolved to the populated Home route", nodeCount("gemini-home-screen") == 1)
        compose.onNodeWithTag("gemini-home-stat-catches-value", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("gemini-home-stat-species-value", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag("gemini-home-stat-record-days-value", useUnmergedTree = true).assertTextEquals("1")
        compose.onNodeWithTag("gemini-home-catch-species-${initialCatch.id}", useUnmergedTree = true)
            .assertTextEquals("草鱼")
        compose.onNodeWithTag("gemini-home-catch-measurement-${initialCatch.id}", useUnmergedTree = true)
            .assertTextEquals("42.6 cm · 1.28 kg")
        compose.onNodeWithTag("gemini-home-catch-metadata-${initialCatch.id}", useUnmergedTree = true)
            .assertTextContains("浙江 · 千岛湖", substring = true)
        compose.onNodeWithText("记录下一条鱼", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gemini-home-camera", useUnmergedTree = true).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("gemini-home-section-all", useUnmergedTree = true).assertHasClickAction()
        compose.onNodeWithTag("gemini-home-catch-card-${initialCatch.id}", useUnmergedTree = true)
            .assertIsDisplayed().assertHasClickAction()
        assertEquals("photo decode and actual Image composition completed", "READY",
            RemoteImageRuntimeProbe.snapshot(initialCatch.id)?.loadStatus)
        assertEquals(true, RemoteImageRuntimeProbe.snapshot(initialCatch.id)?.rendered)
        assertEquals("the real first page identity comes from Repository data", initialCatch.id,
            GeminiHomeGeometryProbe.pagerState()?.currentId)
        assertEquals("all three repository records are represented by the pager", 3,
            compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].columnCount)
        assertNoTextOverflow(initialCatch.id)
        completeStep("PRODUCTION_ROUTE_AND_INITIAL_BUSINESS_STATE_PASSED")

        beginStep("ASSERT_INITIAL_LAYOUT_AND_TOUCH_BOUNDS")
        val metrics = runtimeMetrics()
        assertEquals("actual emulator width matches the requested viewport", expectedSize[0], metrics.widthPx)
        assertEquals("actual emulator height matches the requested viewport", expectedSize[1], metrics.heightPx)
        assertEquals("actual density is 160 dpi", 160, metrics.densityDpi)
        assertEquals("actual font scale matches the requested value", expectedFontScale.toFloat(), metrics.fontScale, 0.02f)
        val initialLayout = layoutMeasurements(initialCatch.id, nextCatch.id, null, metrics)
        assertLayoutContract(initialCatch.id, nextCatch.id, null, initialLayout, metrics)
        completeStep("INITIAL_LAYOUT_AND_TOUCH_BOUNDS_PASSED")

        beginStep("SWIPE_TO_NEXT_REAL_REPOSITORY_CATCH")
        val pagerBeforeGesture = requireNotNull(GeminiHomeGeometryProbe.pagerState())
        assertEquals("gesture starts on first fish", 0, pagerBeforeGesture.currentPage)
        assertEquals("first fish is the pager target before gesture", 0, pagerBeforeGesture.targetPage)
        assertEquals("first fish is fully settled before gesture", 0, pagerBeforeGesture.settledPage)
        assertFalse("pager is idle before gesture", pagerBeforeGesture.isScrollInProgress)
        assertTrue("pager has no residual offset before gesture", abs(pagerBeforeGesture.offsetFraction) <= 0.005f)
        val pagerHistoryStart = GeminiHomeGeometryProbe.pagerStateHistory().size
        compose.onNodeWithTag("gemini-home-catch-pager", useUnmergedTree = true)
            .performTouchInput {
                val start = Offset(size.width * 0.72f, size.height * 0.5f)
                val end = Offset(size.width * 0.28f, size.height * 0.5f)
                down(start)
                repeat(30) { step ->
                    val fraction = (step + 1) / 30f
                    moveTo(
                        Offset(
                            x = start.x + (end.x - start.x) * fraction,
                            y = start.y + (end.y - start.y) * fraction,
                        ),
                        delayMillis = 20,
                    )
                }
                up()
            }
        awaitUi(10_000, "pager becomes idle after physical gesture") {
            val state = GeminiHomeGeometryProbe.pagerState()
            state != null && !state.isScrollInProgress && state.targetPage == state.currentPage
        }
        val stateAfterGesture = requireNotNull(GeminiHomeGeometryProbe.pagerState())
        val pagerGestureTrace = GeminiHomeGeometryProbe.pagerStateHistory().drop(pagerHistoryStart)
        updateReport("pager_gesture", JSONObject().apply {
            put("gesture", JSONObject().put("start_fraction_x", 0.72).put("end_fraction_x", 0.28)
                .put("y_fraction", 0.5).put("duration_ms", 600).put("mechanism", "real_touch_events"))
            put("before", pagerStateJson(pagerBeforeGesture))
            put("after_physical_gesture", pagerStateJson(stateAfterGesture))
            put("observed_states", JSONArray().apply { pagerGestureTrace.forEach { put(pagerStateJson(it)) } })
        })
        val observedCatch = catches.getOrNull(stateAfterGesture.currentPage)
        val observedLeft = catches.getOrNull(stateAfterGesture.currentPage - 1)
        val observedRight = catches.getOrNull(stateAfterGesture.currentPage + 1)
        val afterSwipeCapture = captureScreenshotSet(afterSwipeScreenshotName)
        val afterSwipeScreenshot = afterSwipeCapture.canonical
        val afterSwipeHierarchy = writeHierarchy("${profileId}_after_swipe_hierarchy.txt")
        val afterSwipeBounds = componentBounds(
            observedCatch?.id ?: nextCatch.id,
            observedLeft?.id,
            observedRight?.id,
        )
        val afterSwipeVisibleBounds = componentVisibleBounds(
            observedCatch?.id ?: nextCatch.id,
            observedLeft?.id,
            observedRight?.id,
        )
        updateReport("screenshots", JSONObject().apply {
            put("startup", "${profileId}_${expectedDisplay}_startup.png")
            put("initial", initialScreenshot.name)
            put("after_swipe", afterSwipeScreenshot.name)
        })
        updateReport("after_swipe_evidence", JSONObject().apply {
            put("active_catch_id", stateAfterGesture.currentId ?: JSONObject.NULL)
            put("active_client_record_id", observedCatch?.clientRecordId ?: JSONObject.NULL)
            put("expected_active_catch_id", nextCatch.id)
            put("screenshot", afterSwipeScreenshot.name)
            put("ui_hierarchy", afterSwipeHierarchy.name)
            put("component_bounds", afterSwipeBounds)
            put("visible_component_bounds", afterSwipeVisibleBounds)
            put("window_geometry", viewWindowSnapshot())
            put("display_metrics", runCatching { runtimeMetricsJson(runtimeMetrics()) }
                .getOrElse { JSONObject().put("error", it.toString()) })
            put("screenshot_sources", afterSwipeCapture.sources)
        })
        assertScreenshotCapture(afterSwipeCapture, expectedSize)
        assertTrue("one physical swipe must not skip over the next page", pagerGestureTrace.all {
            it.currentPage <= 1 && it.targetPage <= 1 && it.settledPage <= 1
        })
        assertTrue("gesture trace observes transition to exactly the next page", pagerGestureTrace.any {
            it.currentPage == 1 || it.targetPage == 1 || it.settledPage == 1
        })
        awaitPager(1, nextCatch.id, initialCatch.id, finalCatch.id)
        awaitUi(30_000, "next fish photo rendered") {
            val state = RemoteImageRuntimeProbe.snapshot(nextCatch.id)
            state?.loadStatus == "READY" && state.rendered == true
        }

        compose.onNodeWithTag("gemini-home-catch-card-${nextCatch.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gemini-home-catch-species-${nextCatch.id}", useUnmergedTree = true)
            .assertTextEquals(nextCatch.speciesName)
        compose.onNodeWithTag("gemini-home-catch-measurement-${nextCatch.id}", useUnmergedTree = true)
            .assertTextContains("31 cm", substring = true)
        compose.onNodeWithTag("gemini-home-catch-metadata-${nextCatch.id}", useUnmergedTree = true)
            .assertTextContains("上海", substring = true)
        assertEquals("swipe changes active ID to the next actual repository record", nextCatch.id,
            GeminiHomeGeometryProbe.pagerState()?.currentId)
        assertEquals("the previous catch is the left neighbor after swipe", initialCatch.id,
            GeminiHomeGeometryProbe.pagerState()?.leftId)
        assertEquals("the third catch is the right neighbor after swipe", finalCatch.id,
            GeminiHomeGeometryProbe.pagerState()?.rightId)
        assertEquals("left side catch is present", 1, nodeCount("gemini-home-catch-neighbor-${initialCatch.id}"))
        assertEquals("right side catch is present", 1, nodeCount("gemini-home-catch-neighbor-${finalCatch.id}"))
        assertNoTextOverflow(nextCatch.id)
        val afterSwipeLayout = layoutMeasurements(nextCatch.id, initialCatch.id, finalCatch.id, metrics)
        assertLayoutContract(nextCatch.id, initialCatch.id, finalCatch.id, afterSwipeLayout, metrics)
        completeStep("SWIPE_DATA_SWITCH_AND_SECOND_SCREENSHOT_PASSED")

        val report = JSONObject().apply {
            put("schema", "NH01_THREE_DEVICE_PRODUCTION_ROUTE_CASE_V3")
            put("status", "PASS")
            put("profile_id", profileId)
            put("layout_variant", "ADAPTIVE_V2")
            put("scenario", "three_unique_catches")
            put("source_commit", sourceSha)
            put("activity", compose.activity.javaClass.name)
            put("route", "home")
            put("android_api", Build.VERSION.SDK_INT)
            put("device_model", Build.MODEL)
            put("expected_display_px", expectedDisplay)
            put("actual_display_px", JSONArray(listOf(metrics.widthPx, metrics.heightPx)))
            put("density", metrics.density)
            put("density_dpi", metrics.densityDpi)
            put("font_scale", metrics.fontScale)
            put("expected_font_scale", expectedFontScale.toFloat())
            put("safe_insets_dp", metrics.insets)
            put("animation_scales", JSONObject().apply { animationScales.forEach { (key, value) -> put(key, value) } })
            put("fixture", fixtureJson(catches, statistics))
            put("data", JSONObject().apply {
                put("catch_ids", JSONArray(catches.map(RemoteCatch::id)))
                put("client_record_ids", JSONArray(catches.map { it.clientRecordId ?: JSONObject.NULL }))
                put("unique_catch_count", catches.map(RemoteCatch::id).toSet().size)
                put("pager_page_count", catches.size)
                put("initial_active_record_id", initialCatch.id)
                put("initial_active_client_record_id", initialCatch.clientRecordId ?: JSONObject.NULL)
                put("initial_left_record_id", JSONObject.NULL)
                put("initial_right_record_id", nextCatch.id)
                put("after_swipe_active_record_id", nextCatch.id)
                put("after_swipe_left_record_id", initialCatch.id)
                put("after_swipe_right_record_id", finalCatch.id)
            })
            put("data_switch", JSONObject().apply {
                put("initial_catch_id", initialCatch.id)
                put("initial_client_record_id", initialCatch.clientRecordId)
                put("after_swipe_catch_id", nextCatch.id)
                put("after_swipe_client_record_id", nextCatch.clientRecordId)
                put("left_neighbor_after_swipe", initialCatch.id)
                put("right_neighbor_after_swipe", finalCatch.id)
                put("pager_count", catches.size)
            })
            put("initial_layout", initialLayout)
            put("after_swipe_layout", afterSwipeLayout)
            put("initial_bounds", initialBounds)
            put("after_swipe_bounds", afterSwipeBounds)
            put("layout_bounds_logical_window_px", initialBounds)
            put("visible_bounds_clipped_window_px", initialVisibleBounds)
            put("layout_bounds_after_swipe_logical_window_px", afterSwipeBounds)
            put("visible_bounds_after_swipe_clipped_window_px", afterSwipeVisibleBounds)
            put("comparison_tags_initial", comparisonTags(initialCatch.id, null, nextCatch.id))
            put("comparison_tags_after_swipe", comparisonTags(nextCatch.id, initialCatch.id, finalCatch.id))
            put("screenshots", JSONObject().apply {
                put("startup", "${profileId}_${expectedDisplay}_startup.png")
                put("initial", initialScreenshot.name)
                put("after_swipe", afterSwipeScreenshot.name)
                put("capture", "UiAutomation.takeScreenshot from Android Emulator window")
            })
            put("interactions", JSONObject().apply {
                put("camera_has_click_action", true)
                put("section_all_has_click_action", true)
                put("catch_card_has_click_action", true)
                put("horizontal_swipe_changed_real_record_id", true)
            })
            put("completed_step", "PASS")
        }
        writeReport(report)
        completeStep("PASS_REPORT_WRITTEN")
    }

    private fun runtimeMetrics(): RuntimeMetrics {
        val display = DisplayMetrics()
        @Suppress("DEPRECATION")
        compose.activity.windowManager.defaultDisplay.getRealMetrics(display)
        val density = compose.activity.resources.displayMetrics.density
        val fontScale = compose.activity.resources.configuration.fontScale
        val compatInsets = compose.activity.window.decorView.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it).getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
        }
        val insets = JSONObject().apply {
            put("top", (compatInsets?.top ?: 0) / density)
            put("bottom", (compatInsets?.bottom ?: 0) / density)
            put("start", (compatInsets?.left ?: 0) / density)
            put("end", (compatInsets?.right ?: 0) / density)
        }
        return RuntimeMetrics(display.widthPixels, display.heightPixels, density, display.densityDpi, fontScale, insets)
    }

    private fun runtimeMetricsJson(metrics: RuntimeMetrics) = JSONObject().apply {
        put("real_display_width_px", metrics.widthPx)
        put("real_display_height_px", metrics.heightPx)
        put("resources_density", metrics.density)
        put("resources_density_dpi", metrics.densityDpi)
        put("font_scale", metrics.fontScale)
        put("system_bar_insets_dp", metrics.insets)
    }

    private fun assertLayoutContract(
        activeId: String,
        leftId: String?,
        rightId: String?,
        measurement: JSONObject,
        metrics: RuntimeMetrics,
    ) {
        val active = rect("gemini-home-catch-card-$activeId")
        val track = rect("gemini-home-carousel-track")
        val camera = touchRect("gemini-home-camera")
        val sectionAction = touchRect("gemini-home-section-all")
        val cta = rect("gemini-home-cta-label")
        val footer = rect("gemini-home-footer-cta")
        val root = rect("gemini-home-screen")
        val density = metrics.density
        val cardWidthDp = active.width() / density
        val cardHeightDp = active.height() / density
        val ratio = cardWidthDp / cardHeightDp
        assertTrue("hero aspect ratio matches frozen 247:293 contract: $ratio", abs(ratio / (247f / 293f) - 1f) <= 0.01f)
        assertTrue("hero is centered in the carousel track", abs(active.centerX() - track.centerX()) <= 2f * density)
        assertTrue("hero remains inside the track", active.left >= track.left - density && active.right <= track.right + density)
        val cardWidthCap = minOf(track.width() / density * 0.70f, 337.2f)
        assertTrue("hero remains within 70% width and 337.2dp cap", cardWidthDp <= cardWidthCap + 1f)
        assertTrue("camera effective touch target remains 72–88dp: $camera", camera.width() / density in 72f..88f && camera.height() / density in 72f..88f)
        assertTrue("section action retains its 48dp touch target: $sectionAction",
            sectionAction.width() / density >= 48f && sectionAction.height() / density >= 48f)
        assertTrue("CTA label does not overlap the camera", cta.bottom <= camera.top + density)
        assertTrue("footer and camera remain above the actual safe bottom inset", footer.bottom <= root.bottom - metrics.insets.optDouble("bottom") * density + density)
        assertTrue("camera remains above the actual safe bottom inset", camera.bottom <= root.bottom - metrics.insets.optDouble("bottom") * density + density)
        listOfNotNull(leftId, rightId).forEach { sideId ->
            val side = rect("gemini-home-catch-neighbor-$sideId")
            assertTrue("side card width stays within 82%: $sideId", side.width() <= active.width() * 0.82f + density)
            assertEquals("side card height remains 66.7%: $sideId", 0.667f,
                side.height() / active.height(), 0.015f)
            assertEquals("side card remains 15dp lower: $sideId", 15f,
                (side.top - active.top) / density, 1f)
        }
        measurement.put("hero_ratio", ratio)
        measurement.put("hero_width_dp", cardWidthDp)
        measurement.put("hero_height_dp", cardHeightDp)
        measurement.put("hero_center_error_dp", abs(active.centerX() - track.centerX()) / density)
        measurement.put("hero_width_cap_dp", cardWidthCap)
        measurement.put("camera_touch_bounds_dp", touchRect("gemini-home-camera").toDpJson(density))
        measurement.put("section_action_touch_bounds_dp", sectionAction.toDpJson(density))
        measurement.put("cta_camera_vertical_gap_dp", (camera.top - cta.bottom) / density)
        measurement.put("footer_camera_safe_bottom_clearance_dp",
            (root.bottom - metrics.insets.optDouble("bottom") * density - camera.bottom) / density)
    }

    private fun layoutMeasurements(activeId: String, leftId: String?, rightId: String?, metrics: RuntimeMetrics): JSONObject {
        val tags = listOf(
            "gemini-home-screen", "gemini-home-background", "gemini-home-top-section",
            "gemini-home-header-bar", "gemini-home-brand-title", "gemini-home-avatar-button",
            "gemini-home-avatar-visual", "gemini-home-stats-bar", "gemini-home-stat-species-value",
            "gemini-home-stat-species-label", "gemini-home-stat-catches-value", "gemini-home-stat-catches-label",
            "gemini-home-stat-record-days-value", "gemini-home-stat-record-days-label",
            "gemini-home-middle-section", "gemini-home-section-header", "gemini-home-section-title",
            "gemini-home-section-all", "gemini-home-section-all-label", "gemini-home-carousel-track",
            "gemini-home-catch-carousel", "gemini-home-catch-pager", "gemini-home-catch-card-$activeId",
            "gemini-home-catch-media-$activeId", "gemini-home-catch-species-$activeId",
            "gemini-home-catch-measurement-$activeId", "gemini-home-catch-metadata-$activeId",
            "gemini-home-footer-cta", "gemini-home-cta-decorated-label", "gemini-home-cta-rule-leading",
            "gemini-home-cta-label", "gemini-home-cta-rule-trailing", "gemini-home-camera", "gemini-home-camera-art",
        ) + listOfNotNull(leftId, rightId).flatMap { id ->
            listOf("gemini-home-catch-neighbor-$id", "gemini-home-card-peek-left-$id", "gemini-home-card-peek-right-$id")
        }
        val bounds = JSONObject()
        tags.distinct().forEach { tag ->
            bounds.put(tag, runCatching { rect(tag).toDpJson(metrics.density) }.getOrNull() ?: JSONObject.NULL)
        }
        return JSONObject().apply {
            put("bounds_dp", bounds)
            put("font_scale", metrics.fontScale)
            put("density", metrics.density)
            put("density_dpi", metrics.densityDpi)
            put("window_px", JSONArray(listOf(metrics.widthPx, metrics.heightPx)))
            put("safe_insets_dp", metrics.insets)
        }
    }

    private fun componentBounds(activeId: String, leftId: String?, rightId: String?): JSONObject {
        val tags = listOf(
            "gemini-home-screen", "gemini-home-background", "gemini-home-top-section", "gemini-home-header-bar",
            "gemini-home-brand-title", "gemini-home-avatar-button", "gemini-home-stats-bar",
            "gemini-home-stat-species-value", "gemini-home-stat-catches-value", "gemini-home-stat-record-days-value",
            "gemini-home-middle-section", "gemini-home-section-title", "gemini-home-section-all",
            "gemini-home-carousel-track", "gemini-home-catch-pager", "gemini-home-catch-card-$activeId",
            "gemini-home-catch-media-$activeId", "gemini-home-catch-species-$activeId",
            "gemini-home-catch-measurement-$activeId", "gemini-home-catch-metadata-$activeId",
            "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera", "gemini-home-camera-art",
        ) + listOfNotNull(leftId, rightId).map { "gemini-home-catch-neighbor-$it" }
        return JSONObject().apply {
            tags.distinct().forEach { tag -> put(tag, runCatching { rect(tag).toJson() }.getOrNull() ?: JSONObject.NULL) }
        }
    }

    private fun componentVisibleBounds(activeId: String, leftId: String?, rightId: String?): JSONObject {
        val tags = listOf(
            "gemini-home-screen", "gemini-home-background", "gemini-home-top-section", "gemini-home-header-bar",
            "gemini-home-brand-title", "gemini-home-avatar-visual", "gemini-home-stats-bar",
            "gemini-home-middle-section", "gemini-home-section-header", "gemini-home-section-title",
            "gemini-home-carousel-track", "gemini-home-catch-pager", "gemini-home-catch-card-$activeId",
            "gemini-home-footer-cta", "gemini-home-cta-label", "gemini-home-camera-art",
        ) + listOfNotNull(leftId, rightId).map { "gemini-home-catch-neighbor-$it" }
        return JSONObject().apply {
            tags.distinct().forEach { tag ->
                put(tag, runCatching {
                    val bounds = GeminiHomeGeometryProbe.bounds(tag).visibleWindow
                    RectF(bounds.left, bounds.top, bounds.right, bounds.bottom).toJson()
                }.getOrNull() ?: JSONObject.NULL)
            }
        }
    }

    private fun comparisonTags(activeId: String, leftId: String?, rightId: String?): JSONObject = JSONObject().apply {
        put("hero", "gemini-home-catch-card-$activeId")
        put("left_peek", leftId?.let { "gemini-home-catch-neighbor-$it" } ?: JSONObject.NULL)
        put("right_peek", rightId?.let { "gemini-home-catch-neighbor-$it" } ?: JSONObject.NULL)
    }

    private fun assertNoTextOverflow(catchId: String) {
        listOf(
            "gemini-home-catch-species-$catchId",
            "gemini-home-catch-measurement-$catchId",
            "gemini-home-catch-metadata-$catchId",
            "gemini-home-cta-label",
        ).forEach { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertFalse("text must not overflow: $tag", node.config[GeminiHomeTextHasVisualOverflow])
        }
    }

    private fun awaitPager(index: Int, activeId: String, leftId: String?, rightId: String?) {
        awaitUi(10_000, "settled pager index=$index id=$activeId") {
            val state = GeminiHomeGeometryProbe.pagerState()
            state?.currentPage == index && state.targetPage == index && state.settledPage == index &&
                !state.isScrollInProgress && abs(state.offsetFraction) <= 0.005f &&
                state.currentId == activeId && state.leftId == leftId && state.rightId == rightId
        }
    }

    private fun awaitUi(timeoutMillis: Long, description: String, condition: () -> Boolean) {
        val frameCount = TimeUnit.MILLISECONDS.toNanos(timeoutMillis).div(TimeUnit.MILLISECONDS.toNanos(16)).toInt()
        repeat(frameCount.coerceAtLeast(1)) {
            if (runCatching(condition).getOrDefault(false)) return
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(16)
        }
        assertTrue("Timed out waiting for $description", runCatching(condition).getOrDefault(false))
    }

    private fun rect(tag: String): RectF {
        val captured = runCatching { GeminiHomeGeometryProbe.bounds(tag).unclippedWindow }.getOrNull()
        if (captured != null) return RectF(captured.left, captured.top, captured.right, captured.bottom)
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun touchRect(tag: String): RectF {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        return RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun RectF.toDpJson(density: Float): JSONObject = toDp(density).toJson()
    private fun RectF.toDp(density: Float) = RectF(left / density, top / density, right / density, bottom / density)
    private fun RectF.toJson() = JSONObject().apply {
        put("left", left); put("top", top); put("right", right); put("bottom", bottom)
        put("width", width()); put("height", height())
    }

    private fun nodeCount(tag: String): Int = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun captureStartupScreenshot() {
        val dimensions = expectedDisplay.takeIf { 'x' in it } ?: "unknown"
        runCatching { captureWindowScreenshot("${profileId}_${dimensions}_startup.png") }
            .onFailure { Log.e("NH01_MATRIX_CASE", "Startup window screenshot failed", it) }
    }

    private fun captureFailureScreenshot() {
        runCatching {
            val capture = captureScreenshotSet("${profileId}_failure.png")
            failureDiagnostics(capture.canonical)
        }.onFailure { Log.e("NH01_MATRIX_CASE", "Failure evidence capture failed", it) }
    }

    private fun captureWindowScreenshot(name: String): File {
        val output = requireNotNull(activeOutput).apply { mkdirs() }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(output, name)
        file.outputStream().use { sink -> check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, sink)) { "screenshot save failed: $name" } }
        screenshot.recycle()
        check(file.isFile && file.length() > 0L) { "screenshot file was not written: $file" }
        return file
    }

    private fun captureScreenshotSet(name: String): ScreenshotCapture {
        val output = requireNotNull(activeOutput).apply { mkdirs() }
        val stem = name.removeSuffix(".png")
        val sources = JSONObject()
        val canonical = File(output, name)
        runCatching {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            saveBitmap(bitmap, canonical)
            bitmap.recycle()
            sources.put("ui_automation", screenshotSource(canonical))
        }.onFailure { sources.put("ui_automation", JSONObject().put("error", it.toString())) }

        val adbFile = File(output, "${stem}_adb.png")
        runCatching {
            val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p")
            try {
                FileInputStream(pipe.fileDescriptor).use { input ->
                    adbFile.outputStream().use { sink -> input.copyTo(sink) }
                }
            } finally {
                pipe.close()
            }
            check(adbFile.isFile && adbFile.length() > 0L) { "adb screencap returned no image" }
            sources.put("adb_screencap", screenshotSource(adbFile))
        }.onFailure { sources.put("adb_screencap", JSONObject().put("error", it.toString())) }

        val pixelCopyFile = File(output, "${stem}_pixelcopy.png")
        runCatching {
            val geometry = viewWindowSnapshot()
            val windowWidth = geometry.optInt("decor_width_px", 0)
            val windowHeight = geometry.optInt("decor_height_px", 0)
            check(windowWidth > 0 && windowHeight > 0) { "Window has no drawable bounds: $geometry" }
            val bitmap = Bitmap.createBitmap(windowWidth, windowHeight, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            val status = AtomicInteger(-1)
            PixelCopy.request(
                compose.activity.window,
                bitmap,
                { result -> status.set(result); latch.countDown() },
                Handler(Looper.getMainLooper()),
            )
            check(latch.await(10, TimeUnit.SECONDS)) { "PixelCopy timed out" }
            check(status.get() == PixelCopy.SUCCESS) { "PixelCopy failed with status ${status.get()}" }
            saveBitmap(bitmap, pixelCopyFile)
            bitmap.recycle()
            sources.put("pixel_copy", screenshotSource(pixelCopyFile).put("pixel_copy_status", status.get()))
        }.onFailure { sources.put("pixel_copy", JSONObject().put("error", it.toString())) }

        listOf("adb_screencap", "pixel_copy").forEach { sourceName ->
            val source = sources.optJSONObject(sourceName)
            val sourceFile = source?.optString("path")?.takeIf { it.isNotBlank() }?.let { File(output, it) }
            if (sourceFile != null && sourceFile.isFile && canonical.isFile) {
                val sourceObject = requireNotNull(source)
                runCatching { sourceObject.put("comparison_vs_ui_automation", compareScreenshots(canonical, sourceFile)) }
                    .onFailure { sourceObject.put("comparison_error", it.toString()) }
            }
        }

        return ScreenshotCapture(canonical, sources)
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().use { sink ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink)) { "screenshot save failed: ${file.name}" }
        }
        check(file.isFile && file.length() > 0L) { "screenshot file was not written: $file" }
    }

    private fun screenshotSource(file: File): JSONObject {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return JSONObject().apply {
            put("path", file.name)
            put("width_px", options.outWidth)
            put("height_px", options.outHeight)
            put("bytes", file.length())
        }
    }

    private fun compareScreenshots(reference: File, candidate: File): JSONObject {
        val expected = BitmapFactory.decodeFile(reference.absolutePath)
            ?: return JSONObject().put("status", "REFERENCE_DECODE_FAILED")
        val actual = BitmapFactory.decodeFile(candidate.absolutePath)
            ?: return JSONObject().put("status", "CANDIDATE_DECODE_FAILED")
        try {
            if (expected.width != actual.width || expected.height != actual.height) {
                return JSONObject().put("status", "DIMENSIONS_DIFFER")
                    .put("reference_px", JSONArray(listOf(expected.width, expected.height)))
                    .put("candidate_px", JSONArray(listOf(actual.width, actual.height)))
            }
            var closePixels = 0L
            var channelDelta = 0L
            val pixelCount = expected.width.toLong() * expected.height
            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    val a = expected.getPixel(x, y)
                    val b = actual.getPixel(x, y)
                    val delta = maxOf(
                        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
                    )
                    channelDelta += delta
                    if (delta <= 8) closePixels++
                }
            }
            return JSONObject().put("status", "COMPARED")
                .put("dimensions_px", JSONArray(listOf(expected.width, expected.height)))
                .put("fraction_pixels_with_max_rgb_delta_le_8", closePixels.toDouble() / pixelCount)
                .put("mean_absolute_rgb_channel_delta", channelDelta.toDouble() / pixelCount)
        } finally {
            expected.recycle()
            actual.recycle()
        }
    }

    private fun assertScreenshotCapture(capture: ScreenshotCapture, expectedSize: List<Int>) {
        val expectedWidth = expectedSize[0]
        val expectedHeight = expectedSize[1]
        listOf("ui_automation", "adb_screencap", "pixel_copy").forEach { sourceName ->
            val source = requireNotNull(capture.sources.optJSONObject(sourceName)) { "$sourceName screenshot report is missing" }
            assertTrue("$sourceName screenshot was captured", !source.has("error"))
            assertEquals("$sourceName screenshot width matches physical viewport", expectedWidth, source.optInt("width_px"))
            assertEquals("$sourceName screenshot height matches physical viewport", expectedHeight, source.optInt("height_px"))
            val file = File(requireNotNull(activeOutput), source.optString("path"))
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            assertTrue("$sourceName screenshot is decodable", bitmap != null)
            bitmap?.let { image ->
                try {
                val edgeWidth = minOf(2, image.width / 2)
                val edgeColumns = (0 until edgeWidth) + (image.width - edgeWidth until image.width)
                val blackPixels = edgeColumns.sumOf { x ->
                    (0 until image.height).count { y ->
                        val pixel = image.getPixel(x, y)
                        android.graphics.Color.red(pixel) < 12 &&
                            android.graphics.Color.green(pixel) < 12 &&
                            android.graphics.Color.blue(pixel) < 12
                    }
                }
                val edgePixels = edgeColumns.size * image.height
                assertTrue("$sourceName has no unexpected full-height black side border", blackPixels.toFloat() / edgePixels < 0.98f)
                } finally {
                    image.recycle()
                }
            }
        }
    }

    private fun viewWindowSnapshot(): JSONObject {
        val result = AtomicReference<JSONObject>()
        val latch = CountDownLatch(1)
        compose.activity.runOnUiThread {
            runCatching {
                val decor = compose.activity.window.decorView
                val root = decor.rootView
                val decorLocation = IntArray(2).also(decor::getLocationOnScreen)
                val rootLocation = IntArray(2).also(root::getLocationOnScreen)
                val composeRoot = runCatching { GeminiHomeGeometryProbe.bounds("gemini-home-screen").unclippedWindow }
                    .getOrNull()
                result.set(JSONObject().apply {
                    put("decor_location_on_screen_px", JSONArray(decorLocation.toList()))
                    put("decor_width_px", decor.width); put("decor_height_px", decor.height)
                    put("root_location_on_screen_px", JSONArray(rootLocation.toList()))
                    put("root_width_px", root.width); put("root_height_px", root.height)
                    put("window_token_present", decor.windowToken != null)
                    put("compose_root_bounds_window_px", composeRoot?.let {
                        JSONObject().put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom)
                    } ?: JSONObject.NULL)
                })
            }.onFailure { result.set(JSONObject().put("error", it.toString())) }
            latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Timed out reading window and root view geometry" }
        return result.get() ?: JSONObject().put("error", "window geometry unavailable")
    }

    private fun pagerStateJson(state: GeminiHomeGeometryProbe.PagerState) = JSONObject().apply {
        put("current_page", state.currentPage); put("target_page", state.targetPage)
        put("settled_page", state.settledPage); put("is_scroll_in_progress", state.isScrollInProgress)
        put("offset_fraction", state.offsetFraction); put("current_id", state.currentId ?: JSONObject.NULL)
        put("left_id", state.leftId ?: JSONObject.NULL); put("right_id", state.rightId ?: JSONObject.NULL)
    }

    private fun writeHierarchy(name: String): File {
        val file = File(requireNotNull(activeOutput), name)
        val hierarchy = runCatching { compose.onRoot(useUnmergedTree = true).printToString() }
            .getOrElse { "Hierarchy capture failed: $it" }
        file.writeText(hierarchy)
        return file
    }

    private fun failureDiagnostics(screenshot: File) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = runCatching { runBlocking { GuestCatchRepository(targetContext).listCatches() } }
            .getOrNull().orEmpty()
        val metrics = runCatching { runtimeMetrics() }.getOrNull()
        val hierarchy = runCatching { writeHierarchy("${profileId}_failure_hierarchy.txt") }.getOrNull()
        val report = caseReportFile()?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: JSONObject()
        report.put("status", "FAIL")
        report.put("failure_stage", currentStep)
        report.put("last_successful_step", lastSuccessfulStep)
        report.put("failure_screenshot", screenshot.name)
        report.put("failure_activity", runCatching { compose.activity.javaClass.name }.getOrNull() ?: JSONObject.NULL)
        report.put(
            "failure_route",
            if (runCatching { nodeCount("gemini-home-screen") > 0 }.getOrDefault(false)) "home" else "NOT_OBSERVED",
        )
        report.put("failure_fixture", fixtureJson(fixture, GuestCatchRepository(targetContext).statistics(fixture)))
        report.put("failure_device", metrics?.let {
            JSONObject().put("api", Build.VERSION.SDK_INT)
                .put("display_px", JSONArray(listOf(it.widthPx, it.heightPx)))
                .put("density", it.density).put("density_dpi", it.densityDpi)
                .put("font_scale", it.fontScale).put("safe_insets_dp", it.insets)
        } ?: JSONObject.NULL)
        report.put("failure_ui_hierarchy", hierarchy?.name ?: JSONObject.NULL)
        report.put("failure_component_bounds", runCatching {
            componentBounds(fixture.firstOrNull()?.id ?: "missing", fixture.getOrNull(1)?.id, fixture.getOrNull(2)?.id)
        }.getOrNull() ?: JSONObject.NULL)
        saveReport(report)
    }

    private fun writeCaseStatus(status: String, stage: String, failure: Throwable? = null) {
        val file = caseReportFile() ?: return
        runCatching {
            val report = if (file.isFile) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
            report.put("schema", "NH01_THREE_DEVICE_PRODUCTION_ROUTE_CASE_V3")
            report.put("status", status)
            report.put("profile_id", profileId)
            report.put("layout_variant", "ADAPTIVE_V2")
            report.put("source_commit", sourceSha)
            report.put("expected_font_scale", expectedFontScale)
            report.put("expected_display_px", expectedDisplay)
            report.put("target_application_id", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
            report.put("current_step", stage)
            report.put("last_successful_step", lastSuccessfulStep)
            report.put("case_started_epoch_ms", caseStartedAt)
            report.put("case_updated_epoch_ms", System.currentTimeMillis())
            if (failure != null) {
                val stack = StringWriter().also { failure.printStackTrace(PrintWriter(it)) }.toString()
                report.put("failure_class", failure.javaClass.name)
                report.put("failure_message", failure.message ?: "")
                report.put("failure_stacktrace", stack)
            }
            saveReport(report)
        }.onFailure { Log.e("NH01_MATRIX_CASE", "Could not write case status", it) }
    }

    private fun completeStep(step: String) {
        currentStep = step
        lastSuccessfulStep = step
        Log.i("NH01_MATRIX_CASE", "STEP profile=$profileId variant=ADAPTIVE_V2 step=$step")
    }

    private fun beginStep(step: String) {
        currentStep = step
        Log.i("NH01_MATRIX_CASE", "STEP profile=$profileId variant=ADAPTIVE_V2 step=$step")
    }

    private fun updateReport(key: String, value: JSONObject) {
        val report = caseReportFile()?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: JSONObject()
        report.put(key, value)
        saveReport(report)
    }

    private fun writeReport(report: JSONObject) = saveReport(report)

    private fun saveReport(report: JSONObject) {
        val file = caseReportFile() ?: return
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(report.toString(2) + "\n")
        android.system.Os.rename(temporary.absolutePath, file.absolutePath)
    }

    private fun caseReportFile(): File? = activeOutput?.let { File(it, "$profileId-adaptive_v2.json") }

    private fun fixtureJson(catches: List<RemoteCatch>, statistics: com.yujian.ai.catches.CatchStatistics): JSONObject =
        JSONObject().apply {
            put("record_count", catches.size)
            put("unique_ids", catches.map(RemoteCatch::id).toSet().size)
            put("records", JSONArray().apply {
                catches.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id); put("client_record_id", record.clientRecordId ?: JSONObject.NULL)
                        put("species_id", record.speciesId); put("species_name", record.speciesName)
                        put("length_cm", record.lengthCm ?: JSONObject.NULL); put("weight_kg", record.weightKg ?: JSONObject.NULL)
                        put("location", record.location ?: JSONObject.NULL); put("captured_at", record.capturedAt)
                        put("image_file_exists", File(record.imageUrl).isFile)
                    })
                }
            })
            put("statistics", JSONObject().apply {
                put("total_catches", statistics.totalCatches)
                put("species_count", statistics.speciesCount)
                put("record_days", statistics.recordDays)
            })
        }

    private data class RuntimeMetrics(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val densityDpi: Int,
        val fontScale: Float,
        val insets: JSONObject,
    )

    private data class ScreenshotCapture(val canonical: File, val sources: JSONObject)
}
