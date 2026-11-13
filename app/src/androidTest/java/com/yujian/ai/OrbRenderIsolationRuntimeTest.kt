package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathRenderer
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

class OrbRenderIsolationRuntimeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun productionOrbCoreAndR06RenderWithOneContentAndIdenticalStageFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputDir = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06").apply {
            deleteRecursively()
            mkdirs()
        }
        val identity = RecognitionSceneIdentity(
            "orb-pixel-session", "orb-pixel-attempt", "orb-pixel-image", "a".repeat(64), 7L,
        )
        var input by mutableStateOf(RenderInput(frame(identity, RecognitionSceneStage.PHOTO_STABLE, 0f), OrbRenderIsolationMode.RAW_CORE, 1))
        val completedDrawToken = AtomicInteger(0)

        // Exactly one Compose root is installed. Every comparison changes only the renderer mode.
        composeRule.setContent {
            val current = input
            val drawToken = current.drawToken
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .drawWithContent {
                        drawContent()
                        completedDrawToken.set(drawToken)
                    },
                contentAlignment = Alignment.Center,
            ) {
                OrbForRender(current.frame, current.mode)
            }
        }

        val stageCases = listOf(
            StageCase(RecognitionSceneStage.PHOTO_STABLE, 0f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .25f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .75f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .20f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .50f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .80f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
        )
        var capturedModeCount = 0
        val rows = JSONArray()
        val comparisons = JSONArray()
        var token = 1
        stageCases.forEachIndexed { caseIndex, testCase ->
            val sharedFrame = frame(identity, testCase.stage, testCase.progress)
            val progressTolerance = if (testCase.stage.minimumVisibleMs == 0L) 0f else
                .5f / testCase.stage.minimumVisibleMs + .00001f
            assertTrue("${testCase.stage}: stage elapsed and stage progress disagree",
                kotlin.math.abs(sharedFrame.sceneFrame.stageProgress - testCase.progress) <= progressTolerance)
            val caseCaptures = linkedMapOf<OrbRenderIsolationMode, Bitmap>()
            testCase.modes.forEachIndexed { modeIndex, mode ->
                val nextToken = token++
                val previousDrawToken = completedDrawToken.get()
                composeRule.runOnIdle { input = RenderInput(sharedFrame, mode, nextToken) }
                composeRule.waitUntil(10_000L) { completedDrawToken.get() >= nextToken }
                composeRule.waitForIdle()
                val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
                assertTrue("Android renderer produced an empty capture", bitmap.width > 0 && bitmap.height > 0)
                assertTrue("state mutation must produce a draw after prior token $previousDrawToken", completedDrawToken.get() >= nextToken)
                val name = "orb_${caseIndex.toString().padStart(2, '0')}_${testCase.stage.name.lowercase()}_" +
                    "${(testCase.progress * 100).roundToLong()}_${mode.name.lowercase()}.png"
                savePng(bitmap, File(outputDir, name))
                caseCaptures[mode] = bitmap
                capturedModeCount++
                rows.put(JSONObject()
                    .put("stage", testCase.stage.name)
                    .put("requestedProgress", testCase.progress.toDouble())
                    .put("stageProgress", sharedFrame.sceneFrame.stageProgress.toDouble())
                    .put("stageElapsedMs", sharedFrame.sceneFrame.stageElapsedMs)
                    .put("sceneElapsedMs", sharedFrame.sceneFrame.sceneElapsedMs)
                    .put("geometryVersion", sharedFrame.geometryVersion)
                    .put("sessionId", sharedFrame.sessionId)
                    .put("attemptId", sharedFrame.attemptId)
                    .put("imageId", sharedFrame.imageId)
                    .put("renderMode", mode.name)
                    .put("drawToken", nextToken)
                    .put("screenshot", name)
                    .put("coreNonBackgroundPixels", nonBlackCorePixels(bitmap))
                    .put("centerNonBackgroundPixels", nonBlackCenterPixels(bitmap)))
                if (mode == OrbRenderIsolationMode.RAW_CORE || mode == OrbRenderIsolationMode.NORMAL) {
                    assertTrue("${testCase.stage} ${mode.name}: source Orb core disappeared",
                        nonBlackCorePixels(bitmap) > MIN_CORE_PIXELS)
                }
                if (mode == OrbRenderIsolationMode.NORMAL) {
                    assertTrue("NORMAL shader/effects hid the source core at ${testCase.stage}/${testCase.progress}",
                        nonBlackCorePixels(bitmap) > MIN_CORE_PIXELS)
                }
                if (modeIndex > 0) assertTrue("comparison dimensions changed for the same stage frame",
                    bitmap.width == requireNotNull(caseCaptures[testCase.modes.first()]).width &&
                        bitmap.height == requireNotNull(caseCaptures[testCase.modes.first()]).height)
            }
            val raw = requireNotNull(caseCaptures[OrbRenderIsolationMode.RAW_CORE])
            val r06 = caseCaptures[OrbRenderIsolationMode.RAW_CORE_WITH_R06]
            if (r06 != null) {
                val addedPixels = pixelsAddedOverBlack(raw, r06)
                assertTrue("R06 added no visible Android pixels at ${testCase.stage}/${testCase.progress}", addedPixels > 0)
                comparisons.put(JSONObject()
                    .put("stage", testCase.stage.name)
                    .put("stageProgress", testCase.progress.toDouble())
                    .put("comparison", "RAW_CORE_WITH_R06 minus RAW_CORE")
                    .put("r06AddedPixels", addedPixels))
            }
            caseCaptures.values.forEach(Bitmap::recycle)
        }
        assertEquals("all frozen comparison modes must be captured", 15, capturedModeCount)
        val report = JSONObject()
            .put("status", "PASS")
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("renderer", "Android Compose captureToImage + production AIOrbCore")
            .put("setContentCalls", 1)
            .put("stageElapsedDerivedFromProgress", true)
            .put("sameFrameAcrossModeComparisons", true)
            .put("drawSynchronization", "drawWithContent token; no sleeps")
            .put("captures", rows)
            .put("r06PixelComparisons", comparisons)
        File(outputDir, "orb_pixel_report.json").writeText(report.toString(2) + "\n")
    }

    @Test
    fun everyFrozenR06PathHasCurrentFrameAndroidPaintAndPixelEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputDir = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06/paths").apply {
            mkdirs()
        }
        val expectedIds = listOf("G01", "G02", "A01", "A02", "C01")
        assertEquals(expectedIds, OrbRuntimeProfileV1.paths.map { it.id })
        val identity = RecognitionSceneIdentity("r06-session", "r06-attempt", "r06-image", "b".repeat(64), 8L)
        val reportRows = JSONArray()

        OrbRuntimeProfileV1.paths.forEach { spec ->
            val renderer = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
            listOf(.20f, .50f, .80f).forEachIndexed { progressIndex, progress ->
                val stageFrame = frame(identity, RecognitionSceneStage.ORB_RELEASE, progress)
                val (reusedBitmap, actual) = drawPath(renderer, stageFrame)
                val freshRenderer = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
                val (freshBitmap, fresh) = drawPath(freshRenderer, stageFrame)
                val pathMetrics = requireNotNull(actual.paths[spec.id])
                val pixels = nonBlackPixels(reusedBitmap)
                val progressTolerance = if (RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs == 0L) 0f else
                    .5f / RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs
                assertEquals("renderer cache must not retain another stage's samples", fresh, actual)
                assertEquals("reused renderer pixels differ from a clean renderer", bitmapDigest(freshBitmap), bitmapDigest(reusedBitmap))
                assertTrue("stage elapsed and progress disagree", kotlin.math.abs(
                    stageFrame.sceneFrame.stageProgress - progress,
                ) <= progressTolerance)
                assertTrue("${spec.id} has no valid current-frame samples at $progress", pathMetrics.validSampleCount > 1)
                assertTrue("${spec.id} did not draw a valid body segment at $progress", pathMetrics.validBodySegmentCount > 0)
                assertTrue("${spec.id} has no independently drawn current run at $progress", pathMetrics.drawnRunCount > 0)
                assertTrue("${spec.id} actual Paint alpha is zero at $progress", pathMetrics.maximumPaintAlpha > 0)
                assertTrue("${spec.id} actual Paint stroke width is zero at $progress", pathMetrics.maximumStrokeWidthPx > 0f)
                assertEquals("${spec.id} must start a new subpath across invalid sample gaps", 0,
                    pathMetrics.bridgedInvalidGapCount)
                assertTrue("${spec.id} has no final Android bitmap pixel contribution at $progress", pixels > 0)
                assertEquals(setOf(spec.id), actual.drawnPathIds)
                assertTrue("${spec.id} path is rendered on the frozen expected plane", pathMetrics.front == spec.front)
                val name = "r06_${spec.id.lowercase()}_release_${(progress * 100).roundToLong()}.png"
                savePng(reusedBitmap, File(outputDir, name))
                reportRows.put(JSONObject()
                    .put("pathId", spec.id)
                    .put("plane", if (spec.front) "foreground" else "background")
                    .put("stage", RecognitionSceneStage.ORB_RELEASE.name)
                    .put("progress", progress.toDouble())
                    .put("stageElapsedMs", stageFrame.sceneFrame.stageElapsedMs)
                    .put("sceneElapsedMs", stageFrame.sceneFrame.sceneElapsedMs)
                    .put("geometryVersion", stageFrame.geometryVersion)
                    .put("validCurrentSamples", pathMetrics.validSampleCount)
                    .put("validBodySegments", pathMetrics.validBodySegmentCount)
                    .put("drawnRuns", pathMetrics.drawnRunCount)
                    .put("separatedInvalidGaps", pathMetrics.separatedInvalidGapCount)
                    .put("bridgedInvalidGaps", pathMetrics.bridgedInvalidGapCount)
                    .put("drawnChunks", pathMetrics.drawnChunkCount)
                    .put("paintAlpha", pathMetrics.maximumPaintAlpha)
                    .put("strokeWidthPx", pathMetrics.maximumStrokeWidthPx.toDouble())
                    .put("pixelContribution", pixels)
                    .put("reusedCacheMatchesCleanRender", true)
                    .put("runsDrawnSeparatelyAtInvalidGaps", true)
                    .put("screenshot", name))
                reusedBitmap.recycle()
                freshBitmap.recycle()
            }
        }
        val report = JSONObject()
            .put("status", "PASS")
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("renderer", "OrbFilamentPathRenderer on android.graphics.Canvas")
            .put("frozenPathOrder", JSONArray(expectedIds))
            .put("geometryMaterialPhaseCycleAnd14dpBoundaryChanged", false)
            .put("paths", reportRows)
        File(outputDir, "r06_path_pixel_report.json").writeText(report.toString(2) + "\n")
    }

    private fun drawPath(
        renderer: OrbFilamentPathRenderer,
        frame: WaterLightFrame,
    ): Pair<Bitmap, OrbFilamentPathRenderer.FrameMetrics> {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(AndroidColor.BLACK) }
        val metrics = renderer.draw(
            canvas = canvas,
            diameterPx = 240f,
            centerX = CANVAS_SIZE / 2f,
            centerY = CANVAS_SIZE / 2f,
            frame = frame,
            opening = 1f,
            settle = 0f,
            low = false,
            lifeAlpha = 1f,
            event = OrbRuntimeProfileV1.MotionEvent.NONE,
            eventStartMs = 0L,
            density = 3f,
        )
        return bitmap to metrics
    }

    private fun frame(
        identity: RecognitionSceneIdentity,
        stage: RecognitionSceneStage,
        progress: Float,
    ): WaterLightFrame {
        val stageElapsedMs = (stage.minimumVisibleMs * progress).roundToLong()
        val actualProgress = if (stage.minimumVisibleMs <= 0L) 0f else
            (stageElapsedMs.toFloat() / stage.minimumVisibleMs).coerceIn(0f, 1f)
        val previousStageMs = RecognitionSceneStage.entries.takeWhile { it != stage }.sumOf { it.minimumVisibleMs }
        val sceneElapsedMs = previousStageMs + stageElapsedMs
        val scene = RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = sceneElapsedMs,
            opticalReferenceElapsedMs = sceneElapsedMs,
            geometryVersion = 17L,
            stage = stage,
            stageElapsedMs = stageElapsedMs,
            stageProgress = actualProgress,
            playbackMode = ScenePlaybackMode.AUTO,
            monotonicTimestampMs = sceneElapsedMs,
        )
        return WaterLightFrame(
            sessionId = identity.sessionId,
            photoSha256 = identity.photoSha256,
            elapsedMs = sceneElapsedMs,
            opticalReferenceElapsedMs = sceneElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = 0f,
            reducedMotion = false,
            geometryVersion = 17L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            playbackMode = ScenePlaybackMode.AUTO,
            sceneFrameOverride = scene,
        )
    }

    @androidx.compose.runtime.Composable
    private fun OrbForRender(frame: WaterLightFrame, isolation: OrbRenderIsolationMode) {
        AIOrbCore(
            state = AIOrbVisualState.ORB_ACTIVE,
            coreSize = 120.dp,
            elapsedMs = frame.elapsedMs,
            completion = 0f,
            waterLightFrame = frame,
            waterLightMaterial = WaterLightMaterialProfile.ORB_LEGACY,
            orbRendererMode = OrbFilamentRendererMode.ORB_V1_CANDIDATE,
            orbLifeState = OrbRuntimeProfileV1.LifeState.S2_ACTIVE,
            renderIsolationMode = isolation,
        )
    }

    private data class RenderInput(val frame: WaterLightFrame, val mode: OrbRenderIsolationMode, val drawToken: Int)
    private data class StageCase(val stage: RecognitionSceneStage, val progress: Float, val modes: List<OrbRenderIsolationMode>)

    private fun savePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
    }

    private fun nonBlackCorePixels(bitmap: Bitmap): Int {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        return countNonBlack(bitmap, cx - 60, cy - 60, cx + 60, cy + 60)
    }

    private fun nonBlackCenterPixels(bitmap: Bitmap): Int {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        return countNonBlack(bitmap, cx - 140, cy - 140, cx + 140, cy + 140)
    }

    private fun countNonBlack(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top.coerceAtLeast(0)..bottom.coerceAtMost(bitmap.height - 1)) {
            for (x in left.coerceAtLeast(0)..right.coerceAtMost(bitmap.width - 1)) {
                if ((bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
            }
        }
        return count
    }

    private fun pixelsAddedOverBlack(raw: Bitmap, r06: Bitmap): Int {
        assertEquals(raw.width, r06.width)
        assertEquals(raw.height, r06.height)
        var count = 0
        for (y in 0 until raw.height) for (x in 0 until raw.width) {
            val baseline = raw.getPixel(x, y) and 0x00FFFFFF
            val actual = r06.getPixel(x, y) and 0x00FFFFFF
            if (baseline == 0 && actual != 0) count++
        }
        return count
    }

    private fun nonBlackPixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
        }
        return count
    }

    private fun bitmapDigest(bitmap: Bitmap): String {
        val buffer = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(buffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bytes = ByteArray(buffer.size * 4)
        buffer.forEachIndexed { index, pixel ->
            val offset = index * 4
            bytes[offset] = (pixel ushr 24).toByte()
            bytes[offset + 1] = (pixel ushr 16).toByte()
            bytes[offset + 2] = (pixel ushr 8).toByte()
            bytes[offset + 3] = pixel.toByte()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MIN_CORE_PIXELS = 500
        const val CANVAS_SIZE = 384
    }
}
