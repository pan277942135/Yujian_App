package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.screens.HomeScreen
import com.yujian.ai.ui.theme.YujianTheme
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EmptyHomeResponsiveRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class RuntimeProfile(
        val density: Float,
        val fontScale: Float,
        val safeInsets: SafeDrawingInsetsDp,
    )

    @Test
    fun requiredControlsRemainVisibleInsideSafeDrawingViewport() {
        val arguments = InstrumentationRegistry.getArguments()
        val canonicalCapture = arguments.getString("canonicalCapture")?.toBoolean() ?: false
        val expectedWidthDp = if (canonicalCapture) null else requireNotNull(arguments.getString("expectedWidthDp")?.toFloat()) {
            "expectedWidthDp instrumentation argument is required for responsive validation"
        }
        val expectedHeightDp = if (canonicalCapture) null else requireNotNull(arguments.getString("expectedHeightDp")?.toFloat()) {
            "expectedHeightDp instrumentation argument is required for responsive validation"
        }
        val expectedWidthPx = if (canonicalCapture) requireNotNull(arguments.getString("expectedWidthPx")?.toInt()) {
            "expectedWidthPx instrumentation argument is required for canonical capture"
        } else null
        val expectedHeightPx = if (canonicalCapture) requireNotNull(arguments.getString("expectedHeightPx")?.toInt()) {
            "expectedHeightPx instrumentation argument is required for canonical capture"
        } else null
        val expectedFontScale = requireNotNull(arguments.getString("expectedFontScale")?.toFloat()) {
            "expectedFontScale instrumentation argument is required"
        }
        val evidenceName = requireNotNull(arguments.getString("evidenceName")) {
            "evidenceName instrumentation argument is required"
        }

        val observedProfile = AtomicReference<RuntimeProfile?>()
        if (canonicalCapture) {
            // Freeze the Compose frame clock before composition so continuous
            // Empty Home animations cannot keep the global idling resource busy.
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setContent {
            val density = LocalDensity.current
            val safeInsets = rememberSafeDrawingInsets()
            SideEffect {
                observedProfile.set(
                    RuntimeProfile(
                        density = density.density,
                        fontScale = density.fontScale,
                        safeInsets = safeInsets,
                    ),
                )
            }
            YujianTheme {
                HomeScreen(
                    nickname = "渔友",
                    statistics = CatchStatistics(),
                    recentCatches = emptyList(),
                    resolveImageUrl = { it },
                    accessToken = "",
                    isLoggedIn = false,
                    avatarUrl = null,
                    showEmptyState = true,
                    onIdentify = {},
                    onAlbumClick = {},
                    onLoginClick = {},
                    onSpeciesClick = {},
                    onCatchesClick = {},
                    onProfileClick = {},
                    onCatchClick = {},
                )
            }
        }

        if (canonicalCapture) {
            // Advance only a couple of deterministic frames for first composition.
            // Do not wait for global idle on a page with continuous animation.
            repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
            composeRule.waitUntil(timeoutMillis = 5_000L) { observedProfile.get() != null }
        } else {
            composeRule.waitUntil(timeoutMillis = 5_000L) { observedProfile.get() != null }
            composeRule.waitForIdle()
        }
        val profile = requireNotNull(observedProfile.get())
        assertEquals(expectedFontScale, profile.fontScale, 0.06f)

        if (canonicalCapture) {
            val appRoot = composeRule.activity.window.decorView
            assertEquals("CANONICAL_CAPTURE_INVALID width", expectedWidthPx, appRoot.width)
            assertEquals("CANONICAL_CAPTURE_INVALID height", expectedHeightPx, appRoot.height)
        } else {
            val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val density = profile.density
            assertEquals(requireNotNull(expectedWidthDp), root.width / density, 4f)
            assertEquals(requireNotNull(expectedHeightDp), root.height / density, 4f)

            val header = composeRule.onNodeWithText("渔见").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val subtitle = composeRule.onNodeWithText("拍照收藏每次渔获").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val login = composeRule.onNodeWithContentDescription("登录").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val hero = composeRule.onNodeWithContentDescription("现在，轮到你记录第一条鱼").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val prompt = composeRule.onNodeWithText("对准鱼获，拍一张").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val camera = composeRule.onNodeWithContentDescription("开始识鱼").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val album = composeRule.onNodeWithContentDescription("从相册选择照片").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

            val safeLeft = root.left + profile.safeInsets.start.value * density
            val safeTop = root.top + profile.safeInsets.top.value * density
            val safeRight = root.right - profile.safeInsets.end.value * density
            val safeBottom = root.bottom - profile.safeInsets.bottom.value * density
            listOf(header, subtitle, login, hero, prompt, camera, album).forEach { bounds ->
                assertBoundsInside(bounds, safeLeft, safeTop, safeRight, safeBottom)
            }
            assertTrue("header and subtitle must stay above the Hero", maxOf(header.bottom, subtitle.bottom) <= hero.top)
            assertTrue("Hero must preserve its frozen 2:1 box", kotlin.math.abs(hero.width / hero.height - 2f) <= 0.02f)
            assertTrue("prompt must remain separated from Camera", prompt.bottom < camera.top)
            assertTrue("Camera must remain a reachable touch target", camera.width / density >= 48f && camera.height / density >= 48f)
            assertTrue("Album must remain above the safe bottom inset", album.bottom <= safeBottom)
            assertTrue("Album action must remain below Camera", album.top > camera.bottom)
        }
        saveAppSurfaceScreenshot(evidenceName, expectedWidthPx, expectedHeightPx)
    }

    private fun saveAppSurfaceScreenshot(name: String, expectedWidthPx: Int?, expectedHeightPx: Int?) {
        val requireCanonical = expectedWidthPx != null && expectedHeightPx != null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val captured = AtomicReference<Bitmap?>()
        composeRule.runOnUiThread {
            val appRoot = composeRule.activity.window.decorView
            val width = appRoot.width
            val height = appRoot.height
            assertTrue("app surface is not laid out: ${width}x${height}", appRoot.isLaidOut && width > 0 && height > 0)
            if (requireCanonical) {
                assertEquals("CANONICAL_CAPTURE_INVALID width", expectedWidthPx, width)
                assertEquals("CANONICAL_CAPTURE_INVALID height", expectedHeightPx, height)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            appRoot.draw(Canvas(bitmap))
            captured.set(bitmap)
        }

        val appSurface = requireNotNull(captured.get()) { "app surface capture was not produced" }
        try {
            if (requireCanonical) {
                assertEquals("CANONICAL_CAPTURE_INVALID width", expectedWidthPx, appSurface.width)
                assertEquals("CANONICAL_CAPTURE_INVALID height", expectedHeightPx, appSurface.height)
            }
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "empty_home_responsive")
            assertTrue("responsive evidence directory cannot be created", directory.exists() || directory.mkdirs())
            FileOutputStream(File(directory, name)).use { output ->
                assertTrue("app surface PNG encoding failed", appSurface.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            appSurface.recycle()
        }
    }

    private fun assertBoundsInside(bounds: Rect, left: Float, top: Float, right: Float, bottom: Float) {
        assertTrue("required control has empty bounds: $bounds", bounds.width > 0f && bounds.height > 0f)
        assertTrue("required control crosses safe left inset: $bounds", bounds.left >= left - 1f)
        assertTrue("required control crosses safe top inset: $bounds", bounds.top >= top - 1f)
        assertTrue("required control crosses safe right inset: $bounds", bounds.right <= right + 1f)
        assertTrue("required control crosses safe bottom inset: $bounds", bounds.bottom <= bottom + 1f)
    }
}
