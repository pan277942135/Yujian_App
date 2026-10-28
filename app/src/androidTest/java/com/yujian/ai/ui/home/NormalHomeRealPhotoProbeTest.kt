package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real repository candidates, explicitly NOT approved F01-F08 closure sources. */
class NormalHomeRealPhotoProbeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realCarpCandidateRendersInHomeWithoutChangingArchivedPhoto() = probe("carp", night = false)
    @Test fun realNightCandidateRendersInHomeWithoutChangingArchivedPhoto() = probe("night", night = true)

    private fun probe(id: String, night: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val relativeSource = if (night) "detector_case_b_night_flash.jpg" else "home_normal/fish_record/sample_recent_catch.jpg"
        val assets = if (night) instrumentation.context.assets else context.assets
        val bytes = assets.open(relativeSource).use { it.readBytes() }
        val photo = File(context.cacheDir, "normal-home-real-$id.jpg").apply { writeBytes(bytes) }
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        val record = RemoteCatch(
            id = "real-photo-probe-$id", imageUrl = photo.toURI().toString(),
            speciesId = "fixture-$id", speciesName = if (night) "黄颡鱼" else "鲤鱼",
            confidence = 0.9f, modelVersion = "real-photo-layout-probe-only",
            capturedAt = "2026-10-15T08:00:00+08:00", createdAt = "2026-10-15T08:00:00+08:00",
        )
        var openedRecord: String? = null
        try {
            compose.setContent {
                NormalHomeContent(
                    statistics = CatchStatistics(totalCatches = 1, speciesCount = 1),
                    recentCatches = listOf(record), resolveImageUrl = { photo.absolutePath },
                    accessToken = "", isLoggedIn = false, avatarUrl = null,
                    onIdentify = {}, onSpeciesClick = {}, onCatchesClick = {}, onProfileClick = {},
                    onCatchClick = { openedRecord = it }, motionState = HomeMotionState(),
                    runtimeAssets = null, modifier = Modifier.fillMaxSize(),
                )
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("${record.speciesName} 鱼获照片").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("home-evidence-backdrop-ready", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            val card = compose.onNodeWithTag("normal-home-catch-card-${record.id}")
                .assertIsDisplayed().assertHasClickAction()
            // Semantic readiness can precede the next Surface draw. Synchronize a real
            // image readback before collecting the native device screenshot.
            compose.waitForIdle()
            val rendered = compose.onNodeWithTag("home-evidence-backdrop-ready", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            if (!night) {
                val edge = rendered[(rendered.width * 0.02f).toInt(), (rendered.height * 0.35f).toInt()]
                val distanceFromPlaceholder = kotlin.math.abs(edge.red - 22f / 255f) +
                    kotlin.math.abs(edge.green - 36f / 255f) + kotlin.math.abs(edge.blue - 44f / 255f)
                assertTrue("Real-photo backdrop pixels must replace the temporary opaque base", distanceFromPlaceholder > 0.2f)
            }
            instrumentation.waitForIdleSync()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = File(context.getExternalFilesDir(null), "normal-home-real-photo-probes").apply { mkdirs() }
            val screenshotFile = File(directory, "$id-home.png")
            screenshotFile.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertArrayEquals(bytes, photo.readBytes())
            card.performClick()
            compose.runOnIdle { assertEquals(record.id, openedRecord) }
            val report = JSONObject().apply {
                put("record_id", record.id)
                put("source_path", relativeSource)
                put("source_sha256", sha(bytes))
                put("source_dimensions_px", org.json.JSONArray(listOf(dimensions.outWidth, dimensions.outHeight)))
                put("screenshot", screenshotFile.name)
                put("screenshot_sha256", sha(screenshotFile.readBytes()))
                put("screenshot_dimensions_px", org.json.JSONArray(listOf(screenshot.width, screenshot.height)))
                put("api", android.os.Build.VERSION.SDK_INT)
                put("density_dpi", context.resources.displayMetrics.densityDpi)
                put("font_scale", context.resources.configuration.fontScale)
                put("render_mode", "FULL_FRAME_WITH_SOURCE_BACKDROP")
                put("backdrop", "SAME_SOURCE_CPU_BLUR_PLUS_FULL_FIT")
                put("source_unchanged", true)
                put("record_navigation", "PASS")
                put("fish_bbox", "UNVERIFIED")
                put("acceptance_status", "FIXTURE_PROVENANCE_PENDING")
                put("scope", "HOME rendering and record callback only; not HOME+DETAIL matrix closure")
            }
            File(directory, "$id-home.json").writeText(report.toString(2))
            Log.i("NORMAL_HOME_REAL_PHOTO_PROBE", report.toString())
            screenshot.recycle()
        } finally { photo.delete() }
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
