package com.yujian.ai.ui.identify

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecognitionFirstCaptureGuideRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private lateinit var store: RecognitionFirstCaptureGuideStore

    @Before
    fun resetGuidePreference() {
        store = RecognitionFirstCaptureGuideStore(context)
        context.getSharedPreferences(
            RecognitionFirstCaptureGuideStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun triggerPolicyCoversReadyEntryGalleryBypassPermissionAndDataReset() {
        fun eligible(
            permission: Boolean = true,
            preview: Boolean = true,
            pickerPending: Boolean = false,
            completed: Boolean = false,
            galleryVisited: Boolean = false,
        ) = shouldShowRecognitionFirstCaptureGuide(
            cameraPermissionGranted = permission,
            previewReady = preview,
            galleryPickerPending = pickerPending,
            guideCompleted = completed,
            galleryVisitedThisSession = galleryVisited,
        )

        assertTrue(eligible())
        assertFalse(eligible(permission = false))
        assertFalse(eligible(preview = false))
        assertFalse(eligible(pickerPending = true))
        assertFalse(eligible(galleryVisited = true))
        assertTrue(store.markCompleted())
        assertFalse(eligible(completed = store.isCompleted()))

        context.getSharedPreferences(RecognitionFirstCaptureGuideStore.PREFERENCES_NAME, 0)
            .edit().clear().commit()
        store = RecognitionFirstCaptureGuideStore(context)
        assertFalse(store.isCompleted())
        assertTrue(eligible(completed = store.isCompleted()))
    }

    @Test
    fun pagerActionsSwipeAndTouchShieldNeverInvokeTheShutter() {
        var visible by mutableStateOf(true)
        var shutterRequests = 0
        var galleryRequests = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF5F7881))) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(76.dp)
                        .clickable { shutterRequests++ }
                        .background(Color.White)
                        .testTag("test-underlay-shutter"),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(54.dp)
                        .clickable { galleryRequests++ }
                        .testTag("test-underlay-gallery"),
                )
                if (visible) {
                    RecognitionFirstCaptureGuideOverlay(
                        onComplete = {
                            store.markCompleted()
                            visible = false
                        },
                        onDismiss = { visible = false },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("recognition-guide-page-title-1", useUnmergedTree = true).assertIsDisplayed()
        assertButtonSemantics("recognition-guide-back", "返回拍摄页面")
        assertButtonSemantics("recognition-guide-skip", "跳过首次拍照教学")
        assertButtonSemantics("recognition-guide-primary-action", "下一张教学示例")
        assertEquals(
            "第1页，共2页",
            composeRule.onNodeWithTag("recognition-guide-page-indicator")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )

        composeRule.onNodeWithTag("recognition-capture-first-guide").performTouchInput {
            // The shutter occupies the bottom-center band; the test taps above
            // the guide action bar but inside the underlying shutter target.
            click(Offset(width / 2f, height - 100f))
        }
        composeRule.runOnIdle {
            assertEquals(0, shutterRequests)
            assertTrue(visible)
        }

        composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
        composeRule.onNodeWithText("横屏拍摄示意").assertIsDisplayed()
        assertButtonSemantics("recognition-guide-previous", "上一张教学示例")
        composeRule.onNodeWithTag("recognition-guide-previous").assertHasClickAction().performClick()
        composeRule.onNodeWithTag("recognition-guide-page-title-1", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("recognition-guide-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("recognition-guide-page-title-2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-guide-pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("recognition-guide-page-title-3", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-guide-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("recognition-guide-page-title-2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-guide-pager").performTouchInput { swipeRight() }
        composeRule.onNodeWithTag("recognition-guide-page-title-1", useUnmergedTree = true).assertIsDisplayed()

        // The bottom-right underlying Gallery target overlaps the guide CTA.
        // Only the topmost guide action can receive the tap.
        composeRule.onNodeWithTag("recognition-capture-first-guide").performTouchInput {
            click(Offset(width - 26f, height - 55f))
        }
        composeRule.runOnIdle {
            assertEquals(0, galleryRequests)
            assertEquals(0, shutterRequests)
        }
    }

    @Test
    fun skipAndFinalStartPersistCompletionWithoutCapturing() {
        var visible by mutableStateOf(true)
        var shutterRequests = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).size(76.dp).clickable { shutterRequests++ })
                if (visible) {
                    RecognitionFirstCaptureGuideOverlay(
                        onComplete = {
                            assertTrue(store.markCompleted())
                            visible = false
                        },
                        onDismiss = { visible = false },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("recognition-guide-skip").performClick()
        composeRule.onNodeWithTag("recognition-capture-first-guide").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(store.isCompleted())
            assertEquals(0, shutterRequests)
        }

        context.getSharedPreferences(RecognitionFirstCaptureGuideStore.PREFERENCES_NAME, 0)
            .edit().clear().commit()
        store = RecognitionFirstCaptureGuideStore(context)
        composeRule.runOnIdle {
            visible = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
        composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
        composeRule.onNodeWithText("横屏拍摄示意").assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
        composeRule.onNodeWithTag("recognition-capture-first-guide").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(store.isCompleted())
            assertEquals(0, shutterRequests)
        }
    }

    @Test
    fun topBackSystemBackAndOutsideMaskDoNotPersistCompletion() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            if (visible) {
                RecognitionFirstCaptureGuideOverlay(
                    onComplete = { store.markCompleted(); visible = false },
                    onDismiss = { visible = false },
                )
            }
        }

        composeRule.onNodeWithTag("recognition-capture-first-guide").performTouchInput {
            click(Offset(2f, height / 2f))
        }
        composeRule.runOnIdle {
            assertTrue(visible)
            assertFalse(store.isCompleted())
        }

        composeRule.onNodeWithTag("recognition-guide-back").performClick()
        composeRule.onNodeWithTag("recognition-capture-first-guide").assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(store.isCompleted())
        }

        composeRule.runOnIdle {
            visible = true
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("recognition-capture-first-guide").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(store.isCompleted()) }

    }

    @Test
    fun guidePageAndVisibilitySurviveSaveableStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var page by rememberSaveable { mutableStateOf(RecognitionFirstCaptureGuidePage.P01) }
            var visible by rememberSaveable { mutableStateOf(true) }
            if (visible) {
                RecognitionFirstCaptureGuideOverlay(
                    initialPage = page,
                    onComplete = {
                        store.markCompleted()
                        visible = false
                    },
                    onDismiss = { visible = false },
                    onPageChanged = { page = it },
                )
            }
        }

        composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
        composeRule.onNodeWithText("横屏拍摄示意").assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("横屏拍摄示意").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(store.isCompleted())
        }
    }

    @Test
    fun requiredWidthsAndFontScalesKeepGuideActionsInsideTheViewport() {
        val profiles = listOf(320 to 640, 360 to 640, 411 to 891)
        val fontScales = listOf(1.0f, 1.3f, 2.0f)
        var viewportWidth by mutableStateOf(profiles.first().first)
        var viewportHeight by mutableStateOf(profiles.first().second)
        var guideFontScale by mutableStateOf(fontScales.first())

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = guideFontScale)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(viewportWidth.dp, viewportHeight.dp).testTag("responsive-guide-viewport")) {
                        RecognitionFirstCaptureGuideOverlay(
                            onComplete = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        profiles.forEach { (width, height) ->
            fontScales.forEach { fontScale ->
                composeRule.runOnIdle {
                    viewportWidth = width
                    viewportHeight = height
                    guideFontScale = fontScale
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("recognition-guide-skip").assertIsDisplayed()
                composeRule.onNodeWithTag("recognition-guide-primary-action").assertIsDisplayed()
                composeRule.onNodeWithTag("recognition-guide-back").assertIsDisplayed()
                assertInsideViewport("recognition-guide-skip", width, height)
                assertInsideViewport("recognition-guide-primary-action", width, height)
                assertInsideViewport("recognition-guide-back", width, height)
                composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
                composeRule.onNodeWithTag("recognition-guide-previous").assertIsDisplayed()
                assertInsideViewport("recognition-guide-previous", width, height)
                composeRule.onNodeWithTag("recognition-guide-previous").performClick()
                composeRule.waitForIdle()
            }
        }
    }

    @Test
    fun capturesComponentEvidenceAndReportsTheIndependentPhotoSourceBlocker() {
        assertEquals("runtime device must be API 28", 28, Build.VERSION.SDK_INT)
        var page by mutableStateOf(RecognitionFirstCaptureGuidePage.P01)
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF5F7881))) {
                RecognitionFirstCaptureGuideOverlay(
                    initialPage = page,
                    onComplete = {},
                    onDismiss = {},
                    onPageChanged = { page = it },
                )
            }
        }

        val evidenceDir = File(context.cacheDir, "recognition-first-use-camera-guide").apply { mkdirs() }
        val pages = mutableListOf<JSONObject>()
        listOf(
            Triple(RecognitionFirstCaptureGuidePage.P01, "P01_compose_component.png", "recognition-guide-page-title-1"),
            Triple(RecognitionFirstCaptureGuidePage.P02, "P02_compose_component.png", "recognition-guide-page-title-2"),
        ).forEachIndexed { index, (guidePage, fileName, titleTag) ->
            if (index > 0) {
                composeRule.onNodeWithTag("recognition-guide-primary-action").performClick()
            }
            composeRule.onNodeWithTag(titleTag, useUnmergedTree = true).assertIsDisplayed()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            writePng(bitmap, File(evidenceDir, fileName))
            pages += buildPageEvidence(guidePage, bitmap.width, bitmap.height, fileName)
            bitmap.recycle()
        }

        val report = JSONObject()
            .put("schema_version", "1.0")
            .put("task_id", "RECOGNITION-FIRST-USE-GUIDE-V1")
            .put("status", "ASSET_SOURCE_BLOCKED")
            .put("runtime_evidence_status", "COMPONENT_INSTRUMENTATION_ONLY")
            .put("runtime_route_evidence", "NOT_RUN")
            .put("apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
            .put("source_sha256", JSONObject.NULL)
            .put(
                "frozen_source_sha256",
                JSONObject()
                    .put("P01", "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50")
                    .put("P02", "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3"),
            )
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api_level", Build.VERSION.SDK_INT)
            .put("density", context.resources.displayMetrics.density)
            .put("font_scale", context.resources.configuration.fontScale)
            .put("viewport_px", JSONObject().put("width", composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width.toInt())
                .put("height", composeRule.onRoot().fetchSemanticsNode().boundsInRoot.height.toInt()))
            .put("safe_insets_px", readSafeInsets())
            .put("pages", JSONArray(pages))
            .put(
                "visual_observations",
                JSONArray().put("Rendered screenshots are from the Android Compose instrumentation component harness.")
                    .put("They are not IdentifyScreen/CameraX runtime screenshots and are not visual parity evidence.")
                    .put("Independent page-specific hero and error photographs are unavailable; frozen page PNGs were not used at runtime."),
            )
            .put("pass", false)
            .put("failure_classification", "ASSET_SOURCE_BLOCKED")
        File(evidenceDir, "first_use_camera_guide_runtime_report.json")
            .writeText(report.toString(2), Charsets.UTF_8)

        assertTrue(File(evidenceDir, "P01_compose_component.png").length() > 0L)
        assertTrue(File(evidenceDir, "P02_compose_component.png").length() > 0L)
        assertEquals("ASSET_SOURCE_BLOCKED", report.getString("status"))
    }

    private fun assertInsideViewport(tag: String, widthDp: Int, heightDp: Int) {
        val viewport = composeRule.onNodeWithTag("responsive-guide-viewport")
            .fetchSemanticsNode().boundsInRoot
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag left=${bounds.left} viewport=${viewport.left}", bounds.left >= viewport.left)
        assertTrue("$tag top=${bounds.top} viewport=${viewport.top}", bounds.top >= viewport.top)
        assertTrue("$tag right=${bounds.right} viewportRight=${viewport.right} width=$widthDp", bounds.right <= viewport.right + 0.5f)
        assertTrue("$tag bottom=${bounds.bottom} viewportBottom=${viewport.bottom} height=$heightDp", bounds.bottom <= viewport.bottom + 0.5f)
    }

    private fun assertButtonSemantics(tag: String, expectedContentDescription: String) {
        val config = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        assertEquals(listOf(expectedContentDescription), config[SemanticsProperties.ContentDescription])
    }

    private fun readSafeInsets(): JSONObject {
        val root: View = composeRule.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        return JSONObject()
            .put("left", insets?.left ?: 0)
            .put("top", insets?.top ?: 0)
            .put("right", insets?.right ?: 0)
            .put("bottom", insets?.bottom ?: 0)
    }

    private fun buildPageEvidence(
        page: RecognitionFirstCaptureGuidePage,
        screenshotWidthPx: Int,
        screenshotHeightPx: Int,
        screenshot: String,
    ): JSONObject {
        val mapping = mapRecognitionGuideReferenceSpace(page, screenshotWidthPx, screenshotHeightPx)
        val titleTag = "recognition-guide-page-title-${page.index + 1}"
        val actionBounds = composeRule.onNodeWithTag("recognition-guide-primary-action")
            .fetchSemanticsNode().boundsInWindow
        val titleBounds = composeRule.onNodeWithTag(titleTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        return JSONObject()
            .put("page", page.name)
            .put("screenshot", screenshot)
            .put("screenshot_px", JSONObject().put("width", screenshotWidthPx).put("height", screenshotHeightPx))
            .put("boundsInWindow", JSONObject()
                .put("page_title", rectJson(titleBounds.left, titleBounds.top, titleBounds.right, titleBounds.bottom))
                .put("primary_action", rectJson(actionBounds.left, actionBounds.top, actionBounds.right, actionBounds.bottom)))
            .put("frozen_reference_space", JSONObject()
                .put("width_px", mapping.referenceWidthPx)
                .put("height_px", mapping.referenceHeightPx)
                .put("bounds_px", rectJson(0f, 0f, mapping.referenceWidthPx.toFloat(), mapping.referenceHeightPx.toFloat())))
            .put("reference_to_runtime", JSONObject()
                .put("uniform_scale", mapping.uniformScale)
                .put("offset_x_px", mapping.offsetXPx)
                .put("offset_y_px", mapping.offsetYPx)
                .put("mapped_viewport_px", rectJson(
                    mapping.offsetXPx,
                    mapping.offsetYPx,
                    mapping.offsetXPx + mapping.referenceWidthPx * mapping.uniformScale,
                    mapping.offsetYPx + mapping.referenceHeightPx * mapping.uniformScale,
                )))
            .put("runtime_photo_assets", JSONObject()
                .put("status", "ASSET_SOURCE_BLOCKED")
                .put("hero", JSONObject.NULL)
                .put("errors", JSONArray().put(JSONObject.NULL).put(JSONObject.NULL).put(JSONObject.NULL)))
            .put("visual_status", "BLOCKED_NOT_PARITY_EVIDENCE")
            .put("pass", false)
    }

    private fun rectJson(left: Float, top: Float, right: Float, bottom: Float) =
        JSONObject()
            .put("left", left)
            .put("top", top)
            .put("right", right)
            .put("bottom", bottom)

    private fun writePng(bitmap: Bitmap, output: File) {
        output.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
