package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
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
        val expectedWidthDp = requireNotNull(arguments.getString("expectedWidthDp")?.toFloat()) {
            "expectedWidthDp instrumentation argument is required"
        }
        val expectedHeightDp = requireNotNull(arguments.getString("expectedHeightDp")?.toFloat()) {
            "expectedHeightDp instrumentation argument is required"
        }
        val expectedFontScale = requireNotNull(arguments.getString("expectedFontScale")?.toFloat()) {
            "expectedFontScale instrumentation argument is required"
        }
        val evidenceName = requireNotNull(arguments.getString("evidenceName")) {
            "evidenceName instrumentation argument is required"
        }

        val observedProfile = AtomicReference<RuntimeProfile?>()
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

        composeRule.waitUntil(timeoutMillis = 5_000L) { observedProfile.get() != null }
        composeRule.waitForIdle()
        val profile = requireNotNull(observedProfile.get())
        assertEquals(expectedFontScale, profile.fontScale, 0.06f)

        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val density = profile.density
        assertEquals(expectedWidthDp, root.width / density, 4f)
        assertEquals(expectedHeightDp, root.height / density, 4f)

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
        saveAppSurfaceScreenshot(evidenceName)
    }

    private fun saveAppSurfaceScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val raw = instrumentation.uiAutomation.takeScreenshot()
        try {
            val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
            val windowOrigin = IntArray(2)
            composeRule.runOnUiThread {
                composeRule.activity.window.decorView.getLocationOnScreen(windowOrigin)
            }
            val crop = AndroidRect(
                kotlin.math.floor(rootBounds.left).toInt() + windowOrigin[0],
                kotlin.math.floor(rootBounds.top).toInt() + windowOrigin[1],
                kotlin.math.ceil(rootBounds.right).toInt() + windowOrigin[0],
                kotlin.math.ceil(rootBounds.bottom).toInt() + windowOrigin[1],
            ).apply { intersect(0, 0, raw.width, raw.height) }
            assertTrue("Compose app surface crop is empty: $crop", !crop.isEmpty)
            val appSurface = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height())
            try {
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "empty_home_responsive")
                assertTrue("responsive evidence directory cannot be created", directory.exists() || directory.mkdirs())
                FileOutputStream(File(directory, name)).use { output ->
                    assertTrue("responsive screenshot encoding failed", appSurface.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                appSurface.recycle()
            }
        } finally {
            raw.recycle()
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
