package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.recognition.RecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionVisualStateController
import com.yujian.ai.ui.screens.RecognitionIssueScreen
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.extractContour
import com.yujian.ai.ui.theme.YujianTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic emulator coverage for the production Recognition composables.
 * The harness injects pipeline state, but never substitutes a test-only screen.
 */
class RecognitionFrozenFlowEmulatorTest {
    private companion object {
        const val FROZEN_GATE_LOG_TAG = "RecognitionFrozenGate"

        // Manual ground-truth annotation in source-photo pixel coordinates.
        // Clockwise from the mouth around the actual carp silhouette.
        val REAL_FISH_SUBJECT_POLYGON = listOf(
            184f to 273f, 210f to 276f, 233f to 285f, 248f to 300f, 257f to 320f,
            273f to 340f, 286f to 366f, 293f to 397f, 304f to 430f, 313f to 466f,
            320f to 510f, 328f to 560f, 335f to 612f, 343f to 670f, 348f to 708f,
            361f to 720f, 374f to 742f, 383f to 775f, 391f to 806f, 388f to 827f,
            377f to 838f, 356f to 835f, 336f to 824f, 329f to 860f, 320f to 900f,
            310f to 936f, 302f to 962f, 308f to 974f, 329f to 991f, 346f to 1014f,
            351f to 1037f, 345f to 1057f, 329f to 1067f, 307f to 1064f, 286f to 1051f,
            278f to 1090f, 270f to 1130f, 263f to 1165f, 277f to 1181f, 293f to 1200f,
            309f to 1225f, 321f to 1250f, 325f to 1276f, 322f to 1295f, 310f to 1309f,
            291f to 1311f, 270f to 1303f, 251f to 1292f, 232f to 1284f, 213f to 1290f,
            192f to 1307f, 171f to 1318f, 153f to 1317f, 140f to 1306f, 134f to 1290f,
            135f to 1265f, 141f to 1238f, 144f to 1210f, 142f to 1180f, 134f to 1150f,
            130f to 1110f, 127f to 1070f, 124f to 1025f, 121f to 975f, 117f to 930f,
            113f to 885f, 101f to 875f, 80f to 874f, 62f to 866f, 53f to 851f,
            51f to 832f, 56f to 808f, 67f to 780f, 80f to 752f, 96f to 727f,
            113f to 710f, 116f to 670f, 118f to 625f, 119f to 580f, 120f to 535f,
            121f to 495f, 122f to 460f, 127f to 430f, 133f to 398f, 137f to 367f,
            142f to 344f, 151f to 326f, 161f to 314f, 170f to 307f, 174f to 293f,
        )

        @JvmStatic
        @BeforeClass
        fun clearFrozenEvidenceOnce() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            File(instrumentation.targetContext.cacheDir, "recognition-evidence").deleteRecursively()
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var device: UiDevice
    private lateinit var evidenceDir: File
    private lateinit var photo: SelectedImage
    private lateinit var high: ProductionRecognitionResult
    private lateinit var medium: ProductionRecognitionResult
    private lateinit var low: ProductionRecognitionResult
    private lateinit var noFish: ProductionRecognitionResult
    private lateinit var imageQuality: ProductionRecognitionResult
    private var currentFrozenState: FrozenState = FrozenState.CAPTURE_TRANSITION

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        device = UiDevice.getInstance(instrumentation)
        evidenceDir = File(targetContext.cacheDir, "recognition-evidence").apply { mkdirs() }
        device.executeShellCommand("rm -rf /data/local/tmp/recognition-evidence && mkdir -p /data/local/tmp/recognition-evidence")
        val bitmap = testContext.assets.open("recognition_real_catch_fixture.jpg").use(BitmapFactory::decodeStream)
            ?: error("real catch photo fixture is unavailable")
        photo = SelectedImage("recognition-real-catch-fixture", bitmap, "instrumentation")

        val detectorRun = FishDetectorEngine.DetectorRun("DET_FISH_v0.1", "fixture", 416, 1f, 224, 224, 1L, emptyList())
        val readyAssessment = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.92f, NormalizedFishBox(0.16f, 0.24f, 0.84f, 0.82f))),
        )
        high = result(detectorRun, readyAssessment, prediction(0.86f, 0.42f))
        medium = result(detectorRun, readyAssessment, prediction(0.58f, 0.52f))
        low = result(detectorRun, readyAssessment, prediction(0.31f, 0.21f))
        val noFishAssessment = FishDetectionQualityGate.assess(emptyList())
        noFish = result(detectorRun, noFishAssessment, null)
        val qualityAssessment = FishDetectionQualityGate.assess(
            listOf(
                FishDetection(0.92f, NormalizedFishBox(0.05f, 0.12f, 0.82f, 0.82f)),
                FishDetection(0.80f, NormalizedFishBox(0.18f, 0.18f, 0.76f, 0.76f)),
            ),
        )
        imageQuality = result(detectorRun, qualityAssessment, null)
    }

    @Test
    fun generatesNineFrozenStateScreenshotsAndExercisesProductionNavigation() {
        val state = mutableStateOf(FrozenState.CAPTURE_TRANSITION)
        composeRule.setContent { YujianTheme { FrozenRecognitionHarness(state, photo, high, medium, low, noFish, imageQuality) } }

        render(state, FrozenState.CAPTURE_TRANSITION, "正在准备识别", "01_capture_transition.png")
        assertVisible("AI 已获取这张照片")

        render(state, FrozenState.AI_UNDERSTANDING, "正在理解这张照片", "02_ai_understanding.png")
        assertVisible("寻找这次鱼获的线索")
        assertNoDirtyTechnicalUi()

        render(state, FrozenState.FISH_HIGHLIGHT, "已定位到鱼体", "03_fish_highlight.png")
        assertVisible("正在分析这次鱼获")

        render(state, FrozenState.FISH_IDENTIFYING, "正在认识这条鱼", "04_fish_identifying.png")
        assertVisible("分析鱼体特征")
        assertFalse(composeRule.onAllNodesWithText("草鱼").fetchSemanticsNodes().isNotEmpty())

        render(state, FrozenState.RESULT_HIGH, "修改鱼种 ›", "05_result_high.png")
        assertVisible("草鱼")
        composeRule.onNodeWithText("保存本次鱼获").assertIsEnabled()

        render(state, FrozenState.RESULT_MEDIUM, "帮我确认一下，这条鱼更像哪一种？", "06_result_medium.png")
        assertVisible("鲫鱼")
        composeRule.onNode(hasText("鲫鱼") and hasClickAction()).performClick()

        render(state, FrozenState.RESULT_LOW, "无法确认是什么鱼", "07_result_low.png")
        assertVisible("手动选择鱼种")
        assertVisible("重新拍摄")
        listOf("长度", "重量", "地点", "编辑鱼获记录").forEach {
            assertFalse(composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty())
        }
        listOf("还不能确定这是什么鱼", "打开鱼种选择", "收起 16 类鱼种", "其他鱼种（可选）", "补充鱼获信息", "认识完成")
            .forEach { assertFalse(composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()) }
        composeRule.onNodeWithText("手动选择鱼种").performClick()
        assertVisible("选择鱼种")

        render(state, FrozenState.ERROR_NO_FISH, "没有找到可识别的鱼", "08_error_no_fish.png")
        assertVisible("从相册选择")
        assertVisible("重新拍摄")

        render(state, FrozenState.ERROR_IMAGE_QUALITY, "照片不够清晰，无法识别", "09_error_image_quality.png")
        assertVisible("请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。")
        assertFalse(composeRule.onAllNodesWithText("没有找到可识别的鱼").fetchSemanticsNodes().isNotEmpty())

        render(state, FrozenState.TECHNICAL_FAILURE, "识别没有完成", null)
        assertVisible("请重新拍摄或选择照片。")
        assertNoDirtyTechnicalUi()
    }


    @Test
    fun recordsMeasuredNormalSpeedProcessingFlow() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = FishRecognitionPipeline(targetContext)
        val completed = mutableStateOf<ProductionRecognitionResult?>(null)
        val completedResult = AtomicReference<ProductionRecognitionResult?>(null)
        val pipelineTrace = Collections.synchronizedList(mutableListOf<String>())
        val visualTimes = Collections.synchronizedMap(LinkedHashMap<RecognitionPhase, Long>())
        val subjectEvidence = mutableStateOf<FishSubjectResult?>(null)
        var finishedAtMs = 0L

        composeRule.setContent {
            YujianTheme {
                val result = completed.value
                if (result?.ready == true) {
                    RecognitionResultScreen(
                        image = photo,
                        prediction = requireNotNull(result.prediction),
                        productionResult = result,
                        onBack = {}, onRetry = {}, onSave = { _, _, _ -> },
                    )
                } else {
                    RecognitionProcessingScene(
                        image = photo,
                        onBack = {},
                        recognize = { onProgress ->
                            pipeline.recognize(photo.bitmap) { progress ->
                                val entry = "PIPELINE phase=${progress.phase} at=${SystemClock.elapsedRealtime()}"
                                pipelineTrace += entry
                                trace(entry)
                                onProgress(progress)
                            }
                        },
                        generateSubject = { selected, box ->
                            withContext(Dispatchers.Default) {
                                createAnnotatedSubjectAlphaFixture(selected.bitmap, box).also { subjectEvidence.value = it }
                            }
                        },
                        onFinished = { resultValue ->
                            finishedAtMs = SystemClock.elapsedRealtime()
                            completedResult.set(resultValue)
                            completed.value = resultValue
                            trace("PRODUCTION_FLOW_FINISHED ready=${resultValue.ready} status=${resultValue.status}")
                        },
                        onFailure = { error ->
                            pipelineTrace += "FAILURE ${error::class.java.simpleName}: ${error.message}"
                        },
                        onVisualPhasePresented = { phase, atMs ->
                            visualTimes.putIfAbsent(phase, atMs)
                            trace("VISUAL_PRESENTED phase=$phase at=$atMs")
                        },
                    )
                }
            }
        }

        // Production timing must be measured without screenshot I/O. Host ADB owns
        // continuous video; a separate deterministic test owns the Level A proof frame.
        trace("PRODUCTION_FLOW_WAIT_COMPLETION")
        try {
            composeRule.waitUntil(timeoutMillis = 7_000L) {
                completedResult.get() != null
            }
            trace("PRODUCTION_FLOW_COMPLETED")
        } finally {
            pipeline.close()
        }

        val result = requireNotNull(completedResult.get()) { "production Recognition flow did not complete" }
        assertTrue(
            "real production fixture did not reach classifier-ready result: status=${result.status} " +
                "detections=${result.detectorRun.detections.size} failure=${result.failureCode}",
            result.ready,
        )
        val expectedPipeline = listOf(
            RecognitionPhase.CAPTURED,
            RecognitionPhase.DETECTING,
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING,
            RecognitionPhase.RESULT,
        )
        val actualPipeline = pipelineTrace.mapNotNull { line ->
            expectedPipeline.firstOrNull { line.contains("phase=$it") }
        }
        assertEquals(expectedPipeline, actualPipeline.distinct())

        val capturedAt = requireNotNull(visualTimes[RecognitionPhase.CAPTURED])
        val detectingAt = requireNotNull(visualTimes[RecognitionPhase.DETECTING])
        val outlineAt = requireNotNull(visualTimes[RecognitionPhase.OUTLINE])
        val classifyingAt = requireNotNull(visualTimes[RecognitionPhase.CLASSIFYING])
        assertTrue("RESULT callback did not complete", finishedAtMs > classifyingAt)

        val capturedMs = detectingAt - capturedAt
        val detectingMs = outlineAt - detectingAt
        val outlineMs = classifyingAt - outlineAt
        val classifyingMs = finishedAtMs - classifyingAt
        val totalMs = finishedAtMs - capturedAt
        val fishFocusStableMs = classifyingMs - RecognitionVisualStateController.RESOLVE_FADE_MS

        assertTrue("processing visual flow was outside runtime bound: ${totalMs}ms", totalMs in 2_500L..3_500L)
        assertTrue("final fish focus was too short: ${fishFocusStableMs}ms", fishFocusStableMs >= 1_000L)

        val primary = requireNotNull(result.assessment.primary)
        val box = primary.box.normalized()
        val subject = requireNotNull(subjectEvidence.value) { "subject alpha evidence was not generated" }
        val contourSegments = subject.bitmapPath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bitmap ->
                try {
                    extractContour(bitmap).size
                } finally {
                    bitmap.recycle()
                }
            }
        } ?: 0
        assertTrue("real-catch Level A fixture produced an empty contour", contourSegments > 0)
        File(evidenceDir, "recognition_processing_timing.txt").writeText(
            "Contract: CAPTURED=350ms DETECTING=600ms OUTLINE=600ms CLASSIFYING=1250ms TOTAL=2800ms\n" +
                "Runtime CAPTURED duration: ${capturedMs}ms\n" +
                "Runtime DETECTING duration: ${detectingMs}ms\n" +
                "Runtime OUTLINE duration: ${outlineMs}ms\n" +
                "Runtime CLASSIFYING duration: ${classifyingMs}ms\n" +
                "Runtime TOTAL duration: ${totalMs}ms\n" +
                "Runtime FINAL FISH FOCUS STABLE duration: ${fishFocusStableMs}ms\n",
        )
        File(evidenceDir, "recognition_production_flow_trace.txt").writeText(
            pipelineTrace.joinToString("\n") + "\n" +
                "REAL_BBOX=${box.x1},${box.y1},${box.x2},${box.y2} confidence=${primary.confidence}\n" +
                "SUBJECT_STATUS=${subject.status}\n" +
                "SUBJECT_QUALITY=${subject.quality}\n" +
                "SUBJECT_MASK_AREA=${subject.maskAreaRatio}\n" +
                "SUBJECT_WIDTH=${subject.width}\n" +
                "SUBJECT_HEIGHT=${subject.height}\n" +
                "CONTOUR_SEGMENTS=$contourSegments\n" +
                "FOCUS_RENDER_MODE=LEVEL_A\n" +
                "LOW_PERFORMANCE=false\n" +
                "REDUCE_MOTION=false\n" +
                "RESULT species=${result.prediction?.top1?.speciesKey} confidence=${result.prediction?.top1?.confidence}\n" +
                visualTimes.entries.joinToString("\n") { (phase, at) -> "VISUAL phase=$phase at=$at" } + "\n",
        )
        File(evidenceDir, "recognition_focus_diagnostic.txt").writeText(
            "SUBJECT_STATUS=${subject.status}\n" +
                "SUBJECT_QUALITY=${subject.quality}\n" +
                "SUBJECT_MASK_AREA=${subject.maskAreaRatio}\n" +
                "SUBJECT_WIDTH=${subject.width}\n" +
                "SUBJECT_HEIGHT=${subject.height}\n" +
                "CONTOUR_SEGMENTS=$contourSegments\n" +
                "FOCUS_RENDER_MODE=LEVEL_A\n" +
                "REAL_BBOX=${box.x1},${box.y1},${box.x2},${box.y2}\n" +
                "LOW_PERFORMANCE=false\n" +
                "REDUCE_MOTION=false\n",
        )
        trace(
            "TIMING_CAPTURED_MS=$capturedMs TIMING_DETECTING_MS=$detectingMs " +
                "TIMING_OUTLINE_MS=$outlineMs TIMING_CLASSIFYING_MS=$classifyingMs " +
                "TIMING_TOTAL_MS=$totalMs TIMING_FISH_FOCUS_STABLE_MS=$fishFocusStableMs",
        )
    }

    @Test
    fun realDetectorBboxAndAnnotatedSubjectRenderLevelAContourEvidence() = kotlinx.coroutines.runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = FishRecognitionPipeline(targetContext)
        val result = try {
            pipeline.recognize(photo.bitmap)
        } finally {
            pipeline.close()
        }
        assertTrue(
            "real catch fixture did not produce classifier-ready detector bbox for Level A proof",
            result.ready,
        )
        val realBox = requireNotNull(result.assessment.primary).box
        val subject = createAnnotatedSubjectAlphaFixture(photo.bitmap, realBox)
        val contourSegments = subject.bitmapPath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bitmap ->
                try {
                    extractContour(bitmap).size
                } finally {
                    bitmap.recycle()
                }
            }
        } ?: 0
        assertTrue("Level A contour fixture must contain real silhouette segments", contourSegments > 0)

        composeRule.setContent {
            YujianTheme {
                RecognitionProcessingScene(
                    image = photo,
                    onBack = {},
                    recognize = { onProgress ->
                        onProgress(RecognitionProgress(RecognitionPhase.CLASSIFYING, result.assessment))
                        result
                    },
                    generateSubject = { _, _ -> subject },
                    onFinished = {},
                    phaseOverride = RecognitionPhase.CLASSIFYING,
                    visualClockOverrideMs = 3_200L,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag("recognition-fish-focus-level-a").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("recognition-fish-focus-level-a").assertIsDisplayed()
        capture("10_level_a_contour.png")
        val box = realBox.normalized()
        trace(
            "LEVEL_A_REAL_BBOX confidence=${result.assessment.primary?.confidence} " +
            "x1=${box.x1} y1=${box.y1} x2=${box.x2} y2=${box.y2} " +
            "subject_area=${subject.maskAreaRatio} CONTOUR_SEGMENTS=$contourSegments " +
            "FOCUS_RENDER_MODE=LEVEL_A",
        )
    }

    @Test
    fun reduceMotionAndLowPerformanceKeepRecognitionSemanticStateStable() {
        val subject = createAnnotatedSubjectAlphaFixture(photo.bitmap, requireNotNull(high.assessment.primary).box)
        composeRule.setContent {
            YujianTheme {
                RecognitionProcessingScene(
                    image = photo,
                    onBack = {},
                    recognize = { onProgress ->
                        onProgress(RecognitionProgress(RecognitionPhase.CLASSIFYING, high.assessment))
                        high
                    },
                    generateSubject = { _, _ -> subject },
                    onFinished = {},
                    phaseOverride = RecognitionPhase.CLASSIFYING,
                    motionPolicyOverride = RecognitionMotionPolicy(
                        reduceMotion = true,
                        lowPerformance = true,
                    ),
                )
            }
        }

        assertVisible("正在认识这条鱼")
        assertVisible("分析鱼体特征")
        composeRule.onNodeWithTag("recognition-ambient-reduced-motion-low-performance").assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-fish-focus-level-b-low-performance").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("草鱼").fetchSemanticsNodes().isNotEmpty())

        composeRule.waitForIdle()
        val first = File(evidenceDir, "_reduce_motion_a.png")
        val second = File(evidenceDir, "_reduce_motion_b.png")
        assertTrue(device.takeScreenshot(first))
        Thread.sleep(350L)
        assertTrue(device.takeScreenshot(second))
        val firstBitmap = requireNotNull(BitmapFactory.decodeFile(first.absolutePath))
        val secondBitmap = requireNotNull(BitmapFactory.decodeFile(second.absolutePath))
        val diffRatio = bitmapDifferenceRatio(firstBitmap, secondBitmap, topSkipPx = 80)
        firstBitmap.recycle()
        secondBitmap.recycle()
        first.delete()
        second.delete()

        assertTrue("Reduce Motion still produced continuous visual travel: diffRatio=$diffRatio", diffRatio <= 0.01f)
        capture("11_reduce_motion_low_performance.png")
    }

    /**
     * Ground-truth alpha fixture manually annotated against recognition_real_catch_fixture.jpg.
     *
     * The polygon follows the actual fish silhouette in the 1152x1536 source photo,
     * including the pectoral/pelvic fins and tail. The real detector bbox still decides
     * the crop; this annotation supplies only subject alpha so Level A exercises the
     * same production contour extraction path without fabricating detector/classifier state.
     */
    private fun createAnnotatedSubjectAlphaFixture(
        source: Bitmap,
        detectorBox: NormalizedFishBox,
    ): FishSubjectResult {
        require(source.width == 1152 && source.height == 1536) {
            "annotated subject mask authority requires 1152x1536 fixture, got ${source.width}x${source.height}"
        }
        val expanded = detectorBox.expand(.12f)
        val pixels = FishDetectionQualityGate.cropBoxPixels(expanded, source.width, source.height)
        val roi = Bitmap.createBitmap(
            source,
            pixels[0],
            pixels[1],
            pixels[2] - pixels[0],
            pixels[3] - pixels[1],
        )
        val width = roi.width
        val height = roi.height

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val path = Path()
        REAL_FISH_SUBJECT_POLYGON.forEachIndexed { index, point ->
            val x = point.first - pixels[0]
            val y = point.second - pixels[1]
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            },
        )

        val roiPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        roi.getPixels(roiPixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        var foreground = 0
        val subjectPixels = IntArray(width * height) { index ->
            val alpha = Color.alpha(maskPixels[index])
            if (alpha >= 36) foreground += 1
            val pixel = roiPixels[index]
            Color.argb(alpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(subjectPixels, 0, width, 0, 0, width, height)

        val file = File(evidenceDir, "real_subject_alpha_fixture.png")
        file.outputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        output.recycle()
        mask.recycle()
        roi.recycle()

        val total = width * height
        val area = foreground.toFloat() / total.coerceAtLeast(1)
        assertTrue("annotated subject alpha is unexpectedly small: area=$area", area in .12f..0.65f)
        return FishSubjectResult(
            status = SubjectStatus.READY,
            bitmapPath = file.absolutePath,
            width = width,
            height = height,
            roiWidth = width,
            roiHeight = height,
            maskSize = total,
            expectedMaskSize = total,
            maskAreaRatio = area,
            quality = FishSubjectQuality.GOOD,
        )
    }

    private fun bitmapDifferenceRatio(left: Bitmap, right: Bitmap, topSkipPx: Int): Float {
        val width = minOf(left.width, right.width)
        val height = minOf(left.height, right.height)
        var changed = 0
        var sampled = 0
        var y = topSkipPx.coerceAtLeast(0)
        while (y < height) {
            var x = 0
            while (x < width) {
                val a = left.getPixel(x, y)
                val b = right.getPixel(x, y)
                val delta =
                    kotlin.math.abs(Color.red(a) - Color.red(b)) +
                    kotlin.math.abs(Color.green(a) - Color.green(b)) +
                    kotlin.math.abs(Color.blue(a) - Color.blue(b))
                if (delta > 18) changed += 1
                sampled += 1
                x += 4
            }
            y += 4
        }
        return changed.toFloat() / sampled.coerceAtLeast(1)
    }

    private fun waitForFlowCopy(text: String): Long {
        trace("TIMING_WAIT=$text")
        try {
            composeRule.waitUntil(timeoutMillis = 4_000L) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(text).assertIsDisplayed()
            return SystemClock.elapsedRealtime()
        } catch (error: Throwable) {
            val tree = runCatching { composeRule.onRoot(useUnmergedTree = true).printToString() }
                .getOrElse { "<semantics tree unavailable: ${it::class.java.simpleName}: ${it.message}>" }
            val message = "Timing visual state assertion failed\nexpected=$text\nsemantics=\n$tree"
            Log.e(FROZEN_GATE_LOG_TAG, message, error)
            throw AssertionError(message, error)
        }
    }

    private fun render(state: MutableState<FrozenState>, next: FrozenState, expected: String, screenshotName: String?) {
        trace("RENDER_STATE=$next EXPECTED=$expected")
        currentFrozenState = next
        composeRule.runOnUiThread { state.value = next }
        composeRule.waitForIdle()
        assertVisible(expected)
        if (screenshotName != null) capture(screenshotName)
        trace("PASS_STATE=$next")
    }

    private fun capture(name: String) {
        val output = File(evidenceDir, name)
        val raw = File(evidenceDir, ".$name.raw.png")
        assertTrue("screenshot capture failed: $name", device.takeScreenshot(raw))
        assertTrue("empty screenshot: $name", raw.isFile && raw.length() > 0L)

        val bitmap = BitmapFactory.decodeFile(raw.absolutePath)
        assertTrue("unreadable screenshot: $name", bitmap != null && bitmap.width > 0 && bitmap.height > 0)
        val decoded = requireNotNull(bitmap)
        val canonical = canonicalizeApi28Screenshot(decoded)
        output.outputStream().use { stream ->
            check(canonical.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        trace(
            "SCREENSHOT_CANONICALIZED name=$name raw=${decoded.width}x${decoded.height} " +
                "output=${canonical.width}x${canonical.height}",
        )
        if (canonical !== decoded) canonical.recycle()
        decoded.recycle()
        raw.delete()

        assertTrue("empty canonical screenshot: $name", output.isFile && output.length() > 0L)
        val verified = BitmapFactory.decodeFile(output.absolutePath)
        assertTrue(
            "unreadable canonical screenshot: $name",
            verified != null && verified.width > 0 && verified.height > 0,
        )
        verified?.recycle()

        // Evidence remains in the target app cache during instrumentation.
        // CI exports it after the test through adb run-as; do not make the
        // instrumentation process depend on writing /data/local/tmp.
    }

    /**
     * API28 UiDevice can return a 2x backing bitmap whose real display occupies
     * only the upper-left quadrant; the unused right/bottom halves are pure
     * black. Screenrecord and the actual user-visible surface use the real
     * viewport. Remove only that exact padding signature, never arbitrary dark
     * product pixels.
     */
    private fun canonicalizeApi28Screenshot(source: Bitmap): Bitmap {
        if (source.width < 2 || source.height < 2 ||
            source.width % 2 != 0 || source.height % 2 != 0
        ) return source
        val halfWidth = source.width / 2
        val halfHeight = source.height / 2
        // On the first API28 frame the unused upper-right quadrant can still
        // contain stale launcher pixels while the entire lower half is black.
        // A black lower half is the stable doubled-backing-buffer signature;
        // the Recognition surface itself always fills the real viewport.
        if (!isBlackPadding(source, 0, halfHeight, source.width, source.height)) {
            return source
        }
        return Bitmap.createBitmap(source, 0, 0, halfWidth, halfHeight)
    }

    private fun isBlackPadding(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        val stepX = ((right - left) / 24).coerceAtLeast(1)
        val stepY = ((bottom - top) / 24).coerceAtLeast(1)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 3 || Color.green(pixel) > 3 || Color.blue(pixel) > 3) {
                    return false
                }
                x += stepX
            }
            y += stepY
        }
        return true
    }

    private fun assertVisible(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(text).assertIsDisplayed()
        } catch (error: Throwable) {
            val tree = runCatching { composeRule.onRoot(useUnmergedTree = true).printToString() }
                .getOrElse { "<semantics tree unavailable: ${it::class.java.simpleName}: ${it.message}>" }
            val message = "Frozen state assertion failed\nexpected=$text\nstate=$currentFrozenState\nsemantics=\n$tree"
            Log.e(FROZEN_GATE_LOG_TAG, message, error)
            throw AssertionError(message, error)
        }
    }

    private fun trace(message: String) {
        println(message)
        Log.i(FROZEN_GATE_LOG_TAG, message)
    }

    private fun assertNoDirtyTechnicalUi() {
        val tree = composeRule.onRoot(useUnmergedTree = true).printToString()
        listOf(".onnx", ".tflite", "Exception", "IllegalStateException", "C:\\", "/data/", "stack trace", "null", "undefined", "java.")
            .forEach { signature -> assertFalse("dirty UI signature: $signature", tree.contains(signature)) }
    }

    private fun result(
        detectorRun: FishDetectorEngine.DetectorRun,
        assessment: com.yujian.ai.ai.FishInputAssessment,
        prediction: RecognitionPrediction?,
    ) = ProductionRecognitionResult(
        status = assessment.status,
        detectorRun = detectorRun,
        assessment = assessment,
        prediction = prediction,
        cropPixels = if (prediction == null) null else intArrayOf(20, 20, 180, 180),
    )

    private fun prediction(topConfidence: Float, secondConfidence: Float): RecognitionPrediction {
        val top = RecognitionCandidate(0, "grass_carp", "草鱼", topConfidence)
        val second = RecognitionCandidate(1, "crucian_carp", "鲫鱼", secondConfidence)
        return RecognitionPrediction("MODEL_M1_v0.6", "fixture", top, listOf(top, second), 1L)
    }
}

private enum class FrozenState {
    CAPTURE_TRANSITION,
    AI_UNDERSTANDING,
    FISH_HIGHLIGHT,
    FISH_IDENTIFYING,
    RESULT_HIGH,
    RESULT_MEDIUM,
    RESULT_LOW,
    ERROR_NO_FISH,
    ERROR_IMAGE_QUALITY,
    TECHNICAL_FAILURE,
}

@Composable
private fun FrozenRecognitionHarness(
    state: MutableState<FrozenState>,
    photo: SelectedImage,
    high: ProductionRecognitionResult,
    medium: ProductionRecognitionResult,
    low: ProductionRecognitionResult,
    noFish: ProductionRecognitionResult,
    imageQuality: ProductionRecognitionResult,
) {
    val stateValue = state.value
    key(stateValue) {
    when (stateValue) {
        FrozenState.CAPTURE_TRANSITION,
        FrozenState.AI_UNDERSTANDING,
        FrozenState.FISH_HIGHLIGHT,
        FrozenState.FISH_IDENTIFYING -> {
            RecognitionProcessingScene(
                image = photo,
                onBack = {},
                recognize = { onProgress ->
                    val target = when (stateValue) {
                        FrozenState.CAPTURE_TRANSITION -> RecognitionPhase.CAPTURED
                        FrozenState.AI_UNDERSTANDING -> RecognitionPhase.DETECTING
                        FrozenState.FISH_HIGHLIGHT -> RecognitionPhase.OUTLINE
                        else -> RecognitionPhase.CLASSIFYING
                    }
                    onProgress(RecognitionProgress(target))
                    high
                },
                onFinished = {},
                phaseOverride = when (stateValue) {
                    FrozenState.CAPTURE_TRANSITION -> RecognitionPhase.CAPTURED
                    FrozenState.AI_UNDERSTANDING -> RecognitionPhase.DETECTING
                    FrozenState.FISH_HIGHLIGHT -> RecognitionPhase.OUTLINE
                    FrozenState.FISH_IDENTIFYING -> RecognitionPhase.CLASSIFYING
                    else -> RecognitionPhase.CAPTURED
                },
                visualClockOverrideMs = 3_200L,
            )
        }
        FrozenState.RESULT_HIGH,
        FrozenState.RESULT_MEDIUM,
        FrozenState.RESULT_LOW -> {
            val result = when (stateValue) {
                FrozenState.RESULT_HIGH -> high
                FrozenState.RESULT_MEDIUM -> medium
                else -> low
            }
            RecognitionResultScreen(
                image = photo,
                prediction = requireNotNull(result.prediction),
                productionResult = result,
                onBack = {},
                onRetry = {},
                onSave = { _, _, _ -> },
            )
        }
        FrozenState.ERROR_NO_FISH,
        FrozenState.ERROR_IMAGE_QUALITY,
        FrozenState.TECHNICAL_FAILURE -> {
            val result = when (stateValue) {
                FrozenState.ERROR_NO_FISH -> noFish
                FrozenState.ERROR_IMAGE_QUALITY -> imageQuality
                else -> null
            }
            RecognitionIssueScreen(
                image = photo,
                result = result,
                technicalFailure = stateValue == FrozenState.TECHNICAL_FAILURE,
                onBack = {},
                onChooseAnother = {},
                onChooseGallery = {},
            )
        }
    }
    }
}
