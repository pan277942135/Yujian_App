package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import com.yujian.ai.R
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.designsystem.components.aiorb.LocalOrbShaderOverlayTestOutput
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.designsystem.components.aiorb.OrbShaderOverlayTestOutput
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathRenderer
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRendererMode
import com.yujian.ai.ui.waterlight.renderer.OrbR06RenderPass
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbStage02LayerIsolation
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
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
        val outputRoot = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06").apply {
            mkdirs()
        }
        val outputDir = File(outputRoot, "compose").apply {
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
                OrbForRender(
                    current.frame,
                    current.mode,
                    current.testOutput,
                    current.visualProfile,
                    current.layerIsolation,
                )
            }
        }

        val stageCases = listOf(
            StageCase(RecognitionSceneStage.PHOTO_STABLE, 0f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, 0f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .20f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .25f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .50f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, .75f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_WAKE, 1f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .20f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .50f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
            StageCase(RecognitionSceneStage.ORB_RELEASE, .80f,
                listOf(OrbRenderIsolationMode.RAW_CORE, OrbRenderIsolationMode.RAW_CORE_WITH_R06, OrbRenderIsolationMode.NORMAL)),
        )
        var capturedModeCount = 0
        val expectedCaptureCount = stageCases.sumOf { it.modes.size }
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
        assertEquals("all frozen comparison modes must be captured", expectedCaptureCount, capturedModeCount)
        val report = JSONObject()
            .put("status", "PASS")
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("renderer", "Android Compose captureToImage + production AIOrbCore")
            .put("setContentCalls", 1)
            .put("stageElapsedDerivedFromProgress", true)
            .put("sameFrameAcrossModeComparisons", true)
            .put("expectedCaptureCount", expectedCaptureCount)
            .put("actualCaptureCount", capturedModeCount)
            .put("drawSynchronization", "drawWithContent token; no sleeps")
            .put("captures", rows)
            .put("r06PixelComparisons", comparisons)
        File(outputRoot, "orb_pixel_report.json").writeText(report.toString(2) + "\n")
    }

    @Test
    fun rawCoreSurvivesBlackAndTransparentShaderOverlayOutputs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputRoot = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06").apply {
            mkdirs()
        }
        val outputDir = File(outputRoot, "shader-overlay-fault-injection").apply {
            deleteRecursively()
            mkdirs()
        }
        val identity = RecognitionSceneIdentity(
            "orb-overlay-session", "orb-overlay-attempt", "orb-overlay-image", "c".repeat(64), 9L,
        )
        val sharedFrame = frame(identity, RecognitionSceneStage.ORB_RELEASE, .50f)
        var input by mutableStateOf(RenderInput(sharedFrame, OrbRenderIsolationMode.RAW_CORE, 0))
        val completedDrawToken = AtomicInteger(0)

        composeRule.setContent {
            val current = input
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .drawWithContent {
                        drawContent()
                        completedDrawToken.set(current.drawToken)
                    },
                contentAlignment = Alignment.Center,
            ) {
                OrbForRender(current.frame, current.mode, current.testOutput)
            }
        }

        var nextToken = 0
        fun capture(mode: OrbRenderIsolationMode, output: OrbShaderOverlayTestOutput?): Bitmap {
            val token = ++nextToken
            composeRule.runOnIdle { input = RenderInput(sharedFrame, mode, token, output) }
            composeRule.waitUntil(10_000L) { completedDrawToken.get() >= token }
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            assertTrue("Android renderer produced an empty capture", bitmap.width > 0 && bitmap.height > 0)
            return bitmap
        }

        val raw = capture(OrbRenderIsolationMode.RAW_CORE, null)
        val blackOverlay = capture(
            OrbRenderIsolationMode.NORMAL,
            OrbShaderOverlayTestOutput(Color.Black),
        )
        val transparentOverlay = capture(
            OrbRenderIsolationMode.NORMAL,
            OrbShaderOverlayTestOutput(Color.Transparent),
        )
        assertEquals(raw.width, blackOverlay.width)
        assertEquals(raw.height, blackOverlay.height)
        assertEquals(raw.width, transparentOverlay.width)
        assertEquals(raw.height, transparentOverlay.height)

        val density = instrumentation.targetContext.resources.displayMetrics.density
        val diameterPx = (120f * density).roundToLong().toInt()
        val left = (raw.width / 2 - diameterPx / 2).coerceAtLeast(0)
        val top = (raw.height / 2 - diameterPx / 2).coerceAtLeast(0)
        val right = (raw.width / 2 + diameterPx / 2).coerceAtMost(raw.width)
        val bottom = (raw.height / 2 + diameterPx / 2).coerceAtMost(raw.height)
        val sourcePixels = mutableListOf<Pair<Int, Int>>()
        var rawCoverage = 0
        var blackCoverage = 0
        var rawLumaSum = 0.0
        var blackLumaSum = 0.0
        var rawMass = 0.0
        var rawMomentX = 0.0
        var rawMomentY = 0.0
        var blackMass = 0.0
        var blackMomentX = 0.0
        var blackMomentY = 0.0
        var keyX = left
        var keyY = top
        var keyLuma = -1.0
        for (y in top until bottom) for (x in left until right) {
            val rawColor = raw.getPixel(x, y)
            val rawBrightness = luminance(rawColor)
            if (rawBrightness >= 16.0) {
                sourcePixels += x to y
                rawLumaSum += rawBrightness
                val blackBrightness = luminance(blackOverlay.getPixel(x, y))
                blackLumaSum += blackBrightness
                if (rawBrightness > keyLuma) {
                    keyLuma = rawBrightness
                    keyX = x
                    keyY = y
                }
            }
            if (rawBrightness >= 8.0) rawCoverage++
            val blackBrightness = luminance(blackOverlay.getPixel(x, y))
            if (blackBrightness >= 8.0) blackCoverage++
            if (rawBrightness > 0.0) {
                rawMass += rawBrightness
                rawMomentX += x * rawBrightness
                rawMomentY += y * rawBrightness
            }
            if (blackBrightness > 0.0) {
                blackMass += blackBrightness
                blackMomentX += x * blackBrightness
                blackMomentY += y * blackBrightness
            }
        }
        assertTrue("RAW_CORE must retain a measurable source-water region", sourcePixels.size > MIN_CORE_PIXELS)
        val relativeLuma = blackLumaSum / rawLumaSum
        assertTrue("25% black overlay may not erase source-core brightness: ratio=$relativeLuma", relativeLuma >= .70)
        assertTrue("core pixel coverage remains visible beneath the black overlay",
            blackCoverage >= rawCoverage * .70)
        assertEquals("transparent shader output leaves the raw core pixels unchanged",
            bitmapDigest(raw), bitmapDigest(transparentOverlay))
        assertTrue("black shader output changes the overlay pixels", bitmapDigest(raw) != bitmapDigest(blackOverlay))
        assertEquals("same core layer bounds", raw.width, blackOverlay.width)
        assertEquals("same core layer bounds", raw.height, blackOverlay.height)

        val rawKeyAlpha = raw.getPixel(keyX, keyY) ushr 24 and 0xFF
        val blackKeyAlpha = blackOverlay.getPixel(keyX, keyY) ushr 24 and 0xFF
        assertTrue("key core pixel remains opaque over the same backdrop", rawKeyAlpha > 0)
        assertEquals("overlay preserves the key pixel alpha", rawKeyAlpha, blackKeyAlpha)
        assertTrue("black overlay keeps a visible key-pixel contribution",
            luminance(blackOverlay.getPixel(keyX, keyY)) >= keyLuma * .70)

        val rawCenterX = rawMomentX / rawMass
        val rawCenterY = rawMomentY / rawMass
        val blackCenterX = blackMomentX / blackMass
        val blackCenterY = blackMomentY / blackMass
        assertTrue("black overlay must not move the core layout", kotlin.math.abs(rawCenterX - blackCenterX) <= 1.0)
        assertTrue("black overlay must not move the core layout", kotlin.math.abs(rawCenterY - blackCenterY) <= 1.0)

        val rawName = "orb_stage03_raw_core.png"
        val blackName = "orb_stage03_black_shader_overlay.png"
        val transparentName = "orb_stage03_transparent_shader_overlay.png"
        savePng(raw, File(outputDir, rawName))
        savePng(blackOverlay, File(outputDir, blackName))
        savePng(transparentOverlay, File(outputDir, transparentName))
        File(outputRoot, "orb_shader_overlay_fault_report.json").writeText(
            JSONObject()
                .put("status", "PASS")
                .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                .put("renderer", "Android Compose captureToImage + production AIOrbCore core composite")
                .put("stage", sharedFrame.sceneFrame.stage.name)
                .put("stageProgress", sharedFrame.sceneFrame.stageProgress.toDouble())
                .put("sameSessionAttemptImageAndGeometry", true)
                .put("rawCoreValidPixelCount", sourcePixels.size)
                .put("blackOverlayMeanLuminanceRatio", relativeLuma)
                .put("rawCoreCoveragePixels", rawCoverage)
                .put("blackOverlayCoveragePixels", blackCoverage)
                .put("keyPixelAlphaRaw", rawKeyAlpha)
                .put("keyPixelAlphaBlackOverlay", blackKeyAlpha)
                .put("transparentOutputPixelDigestMatchesRaw", true)
                .put("blackOverlayCoreLayoutCentroidDeltaPx", maxOf(
                    kotlin.math.abs(rawCenterX - blackCenterX),
                    kotlin.math.abs(rawCenterY - blackCenterY),
                ))
                .put("blackOverlayAlphaCap", .25)
                .put("screenshots", JSONArray().put(rawName).put(blackName).put(transparentName))
                .toString(2) + "\n",
        )
        raw.recycle()
        blackOverlay.recycle()
        transparentOverlay.recycle()
    }

    @Test
    fun maxFluidChangesProductionNormalPixelsButRawCoreRemainsIdenticalAtSameFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputRoot = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06/max-fluid").apply {
            mkdirs()
        }
        val identity = RecognitionSceneIdentity(
            "orb-max-fluid-session", "orb-max-fluid-attempt", "orb-max-fluid-image", "d".repeat(64), 10L,
        )
        val sharedFrame = frame(identity, RecognitionSceneStage.ORB_WAKE, .75f)
        var input by mutableStateOf(
            RenderInput(
                sharedFrame,
                OrbRenderIsolationMode.NORMAL,
                0,
                visualProfile = OrbStage02VisualProfile.BASELINE,
                layerIsolation = OrbStage02LayerIsolation.R03_ONLY,
            ),
        )
        val completedDrawToken = AtomicInteger(-1)
        composeRule.setContent {
            val current = input
            Box(
                Modifier.fillMaxSize().background(Color.Black).drawWithContent {
                    drawContent()
                    completedDrawToken.set(current.drawToken)
                },
                contentAlignment = Alignment.Center,
            ) {
                OrbForRender(
                    current.frame,
                    current.mode,
                    current.testOutput,
                    current.visualProfile,
                    current.layerIsolation,
                )
            }
        }
        var token = 0
        fun capture(profile: OrbStage02VisualProfile, isolation: OrbRenderIsolationMode): Bitmap {
            val next = ++token
            composeRule.runOnIdle {
                input = RenderInput(
                    sharedFrame,
                    isolation,
                    next,
                    visualProfile = profile,
                    layerIsolation = OrbStage02LayerIsolation.R03_ONLY,
                )
            }
            composeRule.waitUntil(10_000L) { completedDrawToken.get() >= next }
            composeRule.waitForIdle()
            return composeRule.onRoot().captureToImage().asAndroidBitmap()
        }

        val baselineFluid = capture(OrbStage02VisualProfile.BASELINE, OrbRenderIsolationMode.NORMAL)
        val maxFluid = capture(OrbStage02VisualProfile.MAX, OrbRenderIsolationMode.NORMAL)
        val rawBaseline = capture(OrbStage02VisualProfile.BASELINE, OrbRenderIsolationMode.RAW_CORE)
        val rawMax = capture(OrbStage02VisualProfile.MAX, OrbRenderIsolationMode.RAW_CORE)
        val maxFluidDelta = bitmapDifferencePixels(baselineFluid, maxFluid, threshold = 2)
        assertTrue("same-frame production R03 MAX must change interior rendered pixels", maxFluidDelta > 0)
        assertEquals("profile switching must not alter Raw Core pixels", bitmapDigest(rawBaseline), bitmapDigest(rawMax))
        assertTrue("R03 MAX keeps the raw water core visible", nonBlackCorePixels(maxFluid) > MIN_CORE_PIXELS)
        savePng(baselineFluid, File(outputRoot, "r03_baseline_stage02_75.png"))
        savePng(maxFluid, File(outputRoot, "r03_max_stage02_75.png"))
        File(outputRoot, "r03_max_pixel_report.json").writeText(
            JSONObject()
                .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                .put("stage", sharedFrame.sceneFrame.stage.name)
                .put("stageProgress", sharedFrame.sceneFrame.stageProgress.toDouble())
                .put("visualComparison", "production AIOrbCore NORMAL R03_ONLY")
                .put("maxChangedPixels", maxFluidDelta)
                .put("rawCoreProfileDigestMatches", true)
                .put("sameSceneFrameAndGeometry", true)
                .toString(2) + "\n",
        )
        baselineFluid.recycle()
        maxFluid.recycle()
        rawBaseline.recycle()
        rawMax.recycle()
    }

    @Test
    fun everyFrozenR06PathHasCurrentFrameAndroidPaintAndPixelEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputRoot = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06").apply {
            mkdirs()
        }
        val outputDir = File(outputRoot, "paths").apply {
            mkdirs()
        }
        val expectedIds = listOf("G01", "G02", "A01", "A02", "C01")
        assertEquals(expectedIds, OrbRuntimeProfileV1.paths.map { it.id })
        val identity = RecognitionSceneIdentity("r06-session", "r06-attempt", "r06-image", "b".repeat(64), 8L)
        val reportRows = JSONArray()

        OrbRuntimeProfileV1.paths.forEach { spec ->
            val renderer = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
            val pathCases = listOf(
                RecognitionSceneStage.ORB_WAKE to .25f,
                RecognitionSceneStage.ORB_WAKE to .50f,
                RecognitionSceneStage.ORB_WAKE to .75f,
                RecognitionSceneStage.ORB_WAKE to 1f,
                RecognitionSceneStage.ORB_RELEASE to .20f,
                RecognitionSceneStage.ORB_RELEASE to .50f,
                RecognitionSceneStage.ORB_RELEASE to .80f,
            )
            pathCases.forEach { (stage, progress) ->
                val stageFrame = frame(identity, stage, progress)
                val (reusedBitmap, actual) = drawPath(renderer, stageFrame)
                val freshRenderer = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
                val (freshBitmap, fresh) = drawPath(freshRenderer, stageFrame)
                val pathMetrics = requireNotNull(actual.paths[spec.id])
                val pixels = nonBlackPixels(reusedBitmap)
                val expectedBodyLayerId = requireNotNull(OrbRuntimeProfileV1.materials[spec.material])
                    .layers.firstOrNull { it.id == "translucent_body" || it.id == "fine_filament" }?.id
                val progressTolerance = if (stage.minimumVisibleMs == 0L) 0f else
                    .5f / stage.minimumVisibleMs + .00001f
                assertEquals("renderer cache must not retain another stage's samples", fresh, actual)
                assertEquals("reused renderer pixels differ from a clean renderer", bitmapDigest(freshBitmap), bitmapDigest(reusedBitmap))
                assertTrue("stage elapsed and progress disagree", kotlin.math.abs(
                    stageFrame.sceneFrame.stageProgress - progress,
                ) <= progressTolerance)
                assertTrue("${spec.id} has no valid current-frame samples at $stage/$progress", pathMetrics.validSampleCount > 1)
                assertTrue("${spec.id} did not draw a valid body segment at $stage/$progress", pathMetrics.validBodySegmentCount > 0)
                assertTrue("${spec.id} has no independently drawn current run at $progress", pathMetrics.drawnRunCount > 0)
                assertTrue("${spec.id} actual Paint alpha is zero at $stage/$progress", pathMetrics.maximumPaintAlpha > 0)
                assertTrue("${spec.id} actual Paint stroke width is zero at $stage/$progress", pathMetrics.maximumStrokeWidthPx > 0f)
                assertEquals("${spec.id} must start a new subpath across invalid sample gaps", 0,
                    pathMetrics.bridgedInvalidGapCount)
                assertTrue("${spec.id} has no final Android bitmap pixel contribution at $stage/$progress", pixels > 0)
                assertEquals(setOf(spec.id), actual.drawnPathIds)
                assertTrue("${spec.id} path is rendered on the frozen expected plane", pathMetrics.front == spec.front)
                assertEquals("$stage body layer for ${spec.id}", expectedBodyLayerId, pathMetrics.bodyLayerId)
                if (stage == RecognitionSceneStage.ORB_WAKE && progress >= .50f) {
                    assertTrue("${spec.id} has no body segment at $stage/$progress",
                        pathMetrics.bodyValidSegmentCount > 0)
                    assertTrue("${spec.id} has no drawn body run at $stage/$progress",
                        pathMetrics.bodyDrawnRunCount > 0)
                    assertTrue("${spec.id} Body Paint alpha is zero at $stage/$progress",
                        pathMetrics.bodyPaintAlpha > 0)
                    assertTrue("${spec.id} Body Stroke width is zero at $stage/$progress",
                        pathMetrics.bodyStrokeWidthPx > 0f)
                }
                if (stage == RecognitionSceneStage.ORB_WAKE && progress == .75f &&
                    spec.id in setOf("G02", "A02", "C01")
                ) {
                    val baseline = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
                    val max = OrbFilamentPathRenderer(spec.front, density = 3f, pathIdFilter = spec.id)
                    val (baselineBitmap, baselineMetrics) = drawPath(
                        baseline, stageFrame, OrbStage02VisualProfile.BASELINE,
                    )
                    val (maxBitmap, maxMetrics) = drawPath(max, stageFrame, OrbStage02VisualProfile.MAX)
                    val delta = bitmapDifferencePixels(baselineBitmap, maxBitmap, threshold = 1)
                    val maxBody = requireNotNull(maxMetrics.paths[spec.id])
                    assertTrue("${spec.id} MAX body layer must have a real Canvas run", maxBody.bodyDrawnRunCount > 0)
                    assertTrue("${spec.id} MAX body has no effective Paint alpha", maxBody.bodyPaintAlpha > 0)
                    assertTrue("${spec.id} MAX body has no effective Stroke width", maxBody.bodyStrokeWidthPx > 0f)
                    assertTrue("${spec.id} MAX changes the selected frozen path pixels", delta > 0)
                    reportRows.put(JSONObject()
                        .put("pathId", spec.id)
                        .put("stage", stage.name)
                        .put("progress", progress.toDouble())
                        .put("comparison", "MAX minus BASELINE same path/frame/geometry")
                        .put("changedPixels", delta)
                        .put("baselineBodyRuns", baselineMetrics.paths[spec.id]?.bodyDrawnRunCount ?: 0)
                        .put("maxBodyRuns", maxBody.bodyDrawnRunCount)
                        .put("maxBodyPaintAlpha", maxBody.bodyPaintAlpha)
                        .put("maxBodyStrokeWidthPx", maxBody.bodyStrokeWidthPx.toDouble()))
                    baselineBitmap.recycle()
                    maxBitmap.recycle()
                }
                if (pathMetrics.bodyMaximumAddedAlpha > .002f) {
                    assertTrue("${spec.id} had readable enhancement but no body draw at $stage/$progress",
                        pathMetrics.bodyDrawnRunCount > 0 && pathMetrics.bodyPaintAlpha > 0 &&
                            pathMetrics.bodyStrokeWidthPx > 0f)
                }
                val name = "r06_${spec.id.lowercase()}_${stage.name.lowercase()}_${(progress * 100).roundToLong()}.png"
                savePng(reusedBitmap, File(outputDir, name))
                reportRows.put(JSONObject()
                    .put("pathId", spec.id)
                    .put("plane", if (spec.front) "foreground" else "background")
                    .put("materialLayerId", pathMetrics.bodyLayerId)
                    .put("stage", stage.name)
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
                    .put("bodyValidSegments", pathMetrics.bodyValidSegmentCount)
                    .put("bodyDrawnRuns", pathMetrics.bodyDrawnRunCount)
                    .put("bodyPaintAlpha", pathMetrics.bodyPaintAlpha)
                    .put("bodyStrokeWidthPx", pathMetrics.bodyStrokeWidthPx.toDouble())
                    .put("bodyOriginalAlpha", pathMetrics.bodyMaximumOriginalAlpha.toDouble())
                    .put("bodyFinalAlpha", pathMetrics.bodyMaximumFinalAlpha.toDouble())
                    .put("bodyAddedAlpha", pathMetrics.bodyMaximumAddedAlpha.toDouble())
                    .put("bodyEndFade", pathMetrics.bodyMaximumEndFade.toDouble())
                    .put("bodyDrawStatus", when {
                        pathMetrics.bodyDrawnRunCount > 0 -> "DRAWN"
                        pathMetrics.validSampleCount <= 1 -> "NO_VALID_SAMPLE"
                        pathMetrics.bodyMaximumAddedAlpha <= .002f -> "BODY_ALPHA_BELOW_DRAW_THRESHOLD"
                        else -> "NO_BODY_RUN_AFTER_ALPHA"
                    })
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
        File(outputRoot, "r06_path_pixel_report.json").writeText(report.toString(2) + "\n")
    }

    @Test
    fun maxBackGlassTransmissionUsesOnlyG01AndA01InsideTheSourceWaterCore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputRoot = File(instrumentation.context.cacheDir, "recognition-result-handoff/orb-r06/glass").apply {
            mkdirs()
        }
        val identity = RecognitionSceneIdentity("glass-session", "glass-attempt", "glass-image", "e".repeat(64), 11L)
        val stageFrame = frame(identity, RecognitionSceneStage.ORB_WAKE, .75f)
        val coreMask = requireNotNull(BitmapFactory.decodeResource(
            instrumentation.targetContext.resources,
            R.drawable.processing_water_core_v1,
        ))
        try {
            listOf("G01", "A01").forEach { pathId ->
                val renderer = OrbFilamentPathRenderer(
                    front = false,
                    density = 3f,
                    renderPass = OrbR06RenderPass.GLASS_TRANSMISSION,
                    pathIds = setOf(pathId),
                )
                val (bitmap, metrics) = drawPath(renderer, stageFrame, OrbStage02VisualProfile.MAX, coreMask)
                val pathMetrics = metrics.paths[pathId]
                assertTrue("$pathId glass pass must use frozen back geometry", pathMetrics?.front == false)
                assertTrue("$pathId glass pass must have valid source-path samples", (pathMetrics?.validSampleCount ?: 0) > 1)
                assertTrue("$pathId glass pass must draw an actual body run", (pathMetrics?.bodyDrawnRunCount ?: 0) > 0)
                val pixelsInside = nonBlackPixelsWithinCircle(bitmap, diameter = 240f, radiusFraction = .47f)
                val pixelsOutside = nonBlackPixelsOutsideCircle(bitmap, diameter = 240f, radiusFraction = .50f)
                assertTrue("$pathId glass pass has no interior pixel contribution", pixelsInside > 0)
                assertEquals("$pathId glass pass leaked pixels outside core/transmission edge", 0, pixelsOutside)
                savePng(bitmap, File(outputRoot, "${pathId.lowercase()}_glass_stage02_75.png"))
                bitmap.recycle()
            }
        } finally {
            coreMask.recycle()
        }
    }

    private fun drawPath(
        renderer: OrbFilamentPathRenderer,
        frame: WaterLightFrame,
        visualProfile: OrbStage02VisualProfile = OrbStage02VisualProfile.BASELINE,
        coreMaskBitmap: Bitmap? = null,
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
            visualProfile = visualProfile,
            coreMaskBitmap = coreMaskBitmap,
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
    private fun OrbForRender(
        frame: WaterLightFrame,
        isolation: OrbRenderIsolationMode,
        testOutput: OrbShaderOverlayTestOutput? = null,
        visualProfile: OrbStage02VisualProfile = OrbStage02VisualProfile.BASELINE,
        layerIsolation: OrbStage02LayerIsolation = OrbStage02LayerIsolation.R03_R06,
    ) {
        CompositionLocalProvider(LocalOrbShaderOverlayTestOutput provides testOutput) {
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
                visualProfile = visualProfile,
                stage02LayerIsolation = layerIsolation,
            )
        }
    }

    private data class RenderInput(
        val frame: WaterLightFrame,
        val mode: OrbRenderIsolationMode,
        val drawToken: Int,
        val testOutput: OrbShaderOverlayTestOutput? = null,
        val visualProfile: OrbStage02VisualProfile = OrbStage02VisualProfile.BASELINE,
        val layerIsolation: OrbStage02LayerIsolation = OrbStage02LayerIsolation.R03_R06,
    )
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

    private fun luminance(argb: Int): Double {
        val red = argb ushr 16 and 0xFF
        val green = argb ushr 8 and 0xFF
        val blue = argb and 0xFF
        return .2126 * red + .7152 * green + .0722 * blue
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

    private fun bitmapDifferencePixels(left: Bitmap, right: Bitmap, threshold: Int): Int {
        assertEquals(left.width, right.width)
        assertEquals(left.height, right.height)
        var count = 0
        for (y in 0 until left.height) for (x in 0 until left.width) {
            val a = left.getPixel(x, y)
            val b = right.getPixel(x, y)
            val delta = maxOf(
                kotlin.math.abs((a ushr 24 and 0xFF) - (b ushr 24 and 0xFF)),
                kotlin.math.abs((a ushr 16 and 0xFF) - (b ushr 16 and 0xFF)),
                kotlin.math.abs((a ushr 8 and 0xFF) - (b ushr 8 and 0xFF)),
                kotlin.math.abs((a and 0xFF) - (b and 0xFF)),
            )
            if (delta > threshold) count++
        }
        return count
    }

    private fun nonBlackPixelsWithinCircle(bitmap: Bitmap, diameter: Float, radiusFraction: Float): Int {
        val cx = bitmap.width * .5f
        val cy = bitmap.height * .5f
        val radius = diameter * radiusFraction
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val dx = x + .5f - cx
            val dy = y + .5f - cy
            if (dx * dx + dy * dy <= radius * radius && (bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
        }
        return count
    }

    private fun nonBlackPixelsOutsideCircle(bitmap: Bitmap, diameter: Float, radiusFraction: Float): Int {
        val cx = bitmap.width * .5f
        val cy = bitmap.height * .5f
        val radius = diameter * radiusFraction
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val dx = x + .5f - cx
            val dy = y + .5f - cy
            if (dx * dx + dy * dy > radius * radius && (bitmap.getPixel(x, y) and 0x00FFFFFF) != 0) count++
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
