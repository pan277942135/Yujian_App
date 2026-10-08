package com.yujian.ai.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.home.HomeMotionState
import com.yujian.ai.ui.home.NormalHomeRuntimeAssets
import com.yujian.ai.ui.home.NormalHomeContent
import com.yujian.ai.ui.recorddetail.FishRecordDetailScreen
import com.yujian.ai.ui.recorddetail.FishRecordDetailUiState
import com.yujian.ai.ui.theme.YujianTheme
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatchHeroAdaptiveRuntimeMatrixTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun eightRealPhotoCasesRenderOnHomeAndDetailAndExportSixteenDeviceScreenshots() {
        val target = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val display = displayMetrics(target)
        val detectedProfile = "${display.widthPixels}x${display.heightPixels}"
        val requestedProfile = InstrumentationRegistry.getArguments().getString("catch_hero_profile") ?: detectedProfile
        assertEquals("The requested runtime profile must match the actual display", detectedProfile, requestedProfile)
        val output = requireNotNull(target.getExternalFilesDir("catch_hero_adaptive_v1_1/matrix_$detectedProfile"))
        assertTrue(output.mkdirs() || output.isDirectory)
        val normalHomeAssets = readNormalHomeAssets(target.assets)
        val manifest = readFixtureManifest(assets)
        val fixtures = manifest.getJSONArray("fixtures")
        assertEquals("The matrix must contain eight real-photo test cases", 8, fixtures.length())

        val screenshotEntries = JSONArray()
        val homeModes = mutableSetOf<String>()
        val detailModes = mutableSetOf<String>()
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            val id = fixture.getString("id")
            val assetPath = fixture.getString("file").removePrefix("app/src/androidTest/assets/")
            val image = copyAsset(assets, target, assetPath, id)
            val inputShaBefore = sha256(image)
            val inputUri = Uri.fromFile(image).toString()
            val record = sampleRecord("adaptive-$id", inputUri)
            val modeTag = "catch-hero-mode-EVIDENCE_FIT"

            var openedRecordId: String? = null
            compose.setContent {
                YujianTheme {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = normalHomeAssets.sceneBase.asImageBitmap(),
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                        )
                        NormalHomeContent(
                            statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                            recentCatches = listOf(record),
                            resolveImageUrl = { it },
                            accessToken = "",
                            isLoggedIn = false,
                            avatarUrl = null,
                            onIdentify = {},
                            onSpeciesClick = {},
                            onCatchesClick = {},
                            onProfileClick = {},
                            onCatchClick = { openedRecordId = it },
                            motionState = HomeMotionState(),
                            runtimeAssets = normalHomeAssets,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            awaitMode(modeTag)
            val homeBounds = compose.onNodeWithTag("normal-home-catch-card-${record.id}").fetchSemanticsNode().boundsInRoot
            assertEquals("HOME Hero width stays at the frozen 740px reference geometry", 740f, homeBounds.width, 8f)
            assertEquals("HOME Hero height stays at the frozen 880px reference geometry", 880f, homeBounds.height, 8f)
            compose.onNodeWithTag("normal-home-catch-species-${record.id}", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("normal-home-catch-measurement-${record.id}", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("normal-home-catch-meta-${record.id}", useUnmergedTree = true).assertIsDisplayed()
            homeModes += "EVIDENCE_FIT"
            if (id == "portrait_black_bars_repo_fixture") {
                assertNoPureBlackBands(
                    compose.onNodeWithTag("normal-home-catch-media-${record.id}", useUnmergedTree = true)
                        .captureToImage().asAndroidBitmap(),
                )
            }
            saveRuntimeScreenshot(output, "HOME_$id.png")
            compose.onNodeWithTag("normal-home-catch-card-${record.id}").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(record.id, openedRecordId) }
            screenshotEntries.put(screenshotEntry("HOME", fixture, "EVIDENCE_FIT", "HOME_$id.png", homeBounds))

            var editClicks = 0
            compose.setContent {
                YujianTheme {
                    FishRecordDetailScreen(
                        uiState = FishRecordDetailUiState.Success(record.copy(bsideStatus = BsideStatus.NONE)),
                        imageUrlFor = { inputUri },
                        bsideUrlFor = { null },
                        accessToken = "",
                        onBack = {},
                        onRetry = {},
                        onOpenFishGuide = {},
                        onShare = {},
                        onEditRecord = { editClicks++ },
                        onAddMedia = {},
                        onContinuePhoto = {},
                        onRecordVideo = {},
                        onGenerateMemory = null,
                        onRefreshBsideStatus = { false },
                    )
                }
            }
            awaitMode(modeTag)
            compose.onNodeWithText("草鱼").assertIsDisplayed()
            compose.onNodeWithText("42.6 cm · 1.28 kg · 浙江 · 千岛湖").assertIsDisplayed()
            val detailBounds = compose.onNodeWithTag("fish-record-hero-card").fetchSemanticsNode().boundsInRoot
            assertEquals("DETAIL Hero preserves the frozen 841:540 ratio", 841f / 540f, detailBounds.width / detailBounds.height, 0.02f)
            val editEntry = compose.onNodeWithTag("fish-record-hero-edit-entry", useUnmergedTree = true)
            editEntry.assertIsDisplayed().assertHasClickAction()
            detailModes += "EVIDENCE_FIT"
            if (id == "portrait_black_bars_repo_fixture") {
                assertNoPureBlackBands(compose.onNodeWithTag("fish-record-hero-media", useUnmergedTree = true).captureToImage().asAndroidBitmap())
            }
            saveRuntimeScreenshot(output, "DETAIL_$id.png")
            editEntry.performClick()
            compose.runOnIdle { assertEquals(1, editClicks) }
            screenshotEntries.put(screenshotEntry("DETAIL", fixture, "EVIDENCE_FIT", "DETAIL_$id.png", detailBounds))
            assertEquals("Image loading must never rewrite the source fixture bytes", inputShaBefore, sha256(image))
        }

        assertEquals(setOf("EVIDENCE_FIT"), homeModes)
        assertEquals(setOf("EVIDENCE_FIT"), detailModes)
        assertEquals(16, screenshotEntries.length())
        writeRuntimeManifest(output, target, manifest, screenshotEntries, detectedProfile)
    }

    private fun awaitMode(tag: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        compose.waitForIdle()
    }

    private fun copyAsset(
        assets: android.content.res.AssetManager,
        target: Context,
        assetPath: String,
        id: String,
    ): File {
        val output = File(requireNotNull(target.getExternalFilesDir("catch_hero_adaptive_v1_1/input")), "$id.jpg")
        output.parentFile?.mkdirs()
        assets.open(assetPath).use { input -> output.outputStream().use { stream -> input.copyTo(stream) } }
        return output
    }

    private fun readNormalHomeAssets(assets: android.content.res.AssetManager) = NormalHomeRuntimeAssets(
        sceneBase = decodeHomeAsset(assets, "static/scene_base.png"),
        guestAvatar = decodeHomeAsset(assets, "avatar/guest_avatar.png"),
        fishCardGradient = decodeHomeAsset(assets, "fish_card/fish_card_gradient.png"),
        fishCardOutline = decodeHomeAsset(assets, "fish_card/fish_card_outline.png"),
        fishCardShadow = decodeHomeAsset(assets, "fish_card/fish_card_shadow.png"),
        cameraBase = decodeHomeAsset(assets, "camera/camera_button_base.png"),
        cameraGoldRim = decodeHomeAsset(assets, "camera/camera_gold_rim_mask.png"),
        cameraBreathGlow = decodeHomeAsset(assets, "camera/camera_breath_glow.png"),
    )

    private fun decodeHomeAsset(assets: android.content.res.AssetManager, path: String): Bitmap =
        assets.open("normal_home_runtime_v1/$path").use { stream ->
            BitmapFactory.decodeStream(stream) ?: error("Unable to decode Normal Home asset $path")
        }

    private fun saveRuntimeScreenshot(directory: File, filename: String) {
        compose.waitForIdle()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, filename).outputStream().use { stream ->
            assertTrue("Android screenshot should encode as PNG", screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        screenshot.recycle()
    }

    private fun assertNoPureBlackBands(bitmap: Bitmap) {
        try {
            val centerX = bitmap.width / 2
            val top = bitmap.getPixel(centerX, (bitmap.height * 0.07f).toInt().coerceIn(0, bitmap.height - 1))
            val bottom = bitmap.getPixel(centerX, (bitmap.height * 0.93f).toInt().coerceIn(0, bitmap.height - 1))
            fun isNearPureBlack(pixel: Int) =
                android.graphics.Color.red(pixel) <= 8 && android.graphics.Color.green(pixel) <= 8 &&
                    android.graphics.Color.blue(pixel) <= 8
            assertTrue("Verified bars must be trimmed and replaced by same-source ambient pixels", !isNearPureBlack(top))
            assertTrue("Verified bars must be trimmed and replaced by same-source ambient pixels", !isNearPureBlack(bottom))
        } finally {
            bitmap.recycle()
        }
    }

    private fun readFixtureManifest(assets: android.content.res.AssetManager): JSONObject =
        JSONObject(String(assets.open("catch_hero_adaptive_v1_1/fixture_manifest.json").use { it.readBytes() }, Charsets.UTF_8))

    private fun screenshotEntry(
        page: String,
        fixture: JSONObject,
        mode: String,
        filename: String,
        heroBounds: androidx.compose.ui.geometry.Rect,
    ) = JSONObject()
        .put("page", page)
        .put("fixture_id", fixture.getString("id"))
        .put("fixture_sha256", fixture.getString("sha256"))
        .put("mode", mode)
        .put("screenshot", filename)
        .put("page_state", if (page == "HOME") "NormalHomeContent; one catch; pager at first record" else "FishRecordDetailScreen Success; A-side; B-side NONE")
        .put("hero_bounds_root_px", JSONArray(listOf(heroBounds.left, heroBounds.top, heroBounds.right, heroBounds.bottom)))
        .put("source_key", fixture.getString("source_key"))
        .put("trusted_fish_box", false)

    private fun displayMetrics(target: Context): android.util.DisplayMetrics {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        target.getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun writeRuntimeManifest(
        output: File,
        target: Context,
        fixtureManifest: JSONObject,
        screenshots: JSONArray,
        profile: String,
    ) {
        val metrics = displayMetrics(target)
        val runtimeManifest = JSONObject()
            .put("device_class", if (Build.FINGERPRINT.contains("generic", ignoreCase = true)) "Emulator" else "Android device")
            .put("model", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("display_resolution", "${metrics.widthPixels}x${metrics.heightPixels}")
            .put("requested_profile", profile)
            .put("density_dpi", metrics.densityDpi)
            .put("screenshots", screenshots)
            .put("fixture_manifest", fixtureManifest)
        File(output, "android_runtime_manifest.json").writeText(runtimeManifest.toString(2))
    }

    private fun sampleRecord(id: String, imageUri: String) = RemoteCatch(
        id = id,
        imageUrl = imageUri,
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = .96f,
        modelVersion = "hero-adaptive-v1.1-test",
        capturedAt = "2026-10-06T08:30:00Z",
        createdAt = "2026-10-06T08:30:00Z",
        lengthCm = 42.6f,
        weightKg = 1.28f,
        location = "浙江·千岛湖",
    )

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
