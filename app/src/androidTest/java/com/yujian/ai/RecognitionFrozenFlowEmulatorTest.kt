package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic emulator coverage for the production Recognition composables.
 * The harness injects pipeline state, but never substitutes a test-only screen.
 */
class RecognitionFrozenFlowEmulatorTest {
    private companion object {
        const val FROZEN_GATE_LOG_TAG = "RecognitionFrozenGate"

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
                        onBack = {}, onRetry = {}, onSave = { _, _ -> }, onViewGuide = {},
                    )
                } else {
                    RecognitionProcessingScene(
                        image = photo,
                        onBack = {},
                        recognize = { onProgress ->
                            pipeline.recognize(photo.bitmap) { progress ->
                                pipelineTrace += "PIPELINE phase=\${progress.phase} at=\${SystemClock.elapsedRealtime()}"
                                onProgress(progress)
                            }
                        },
                        generateSubject = { selected, box ->
                            withContext(Dispatchers.Default) {
                                createSubjectAlphaFixture(selected.bitmap, box).also { subjectEvidence.value = it }
                            }
                        },
                        onFinished = { resultValue ->
                            finishedAtMs = SystemClock.elapsedRealtime()
                            completed.value = resultValue
                        },
                        onFailure = { error ->
                            pipelineTrace += "FAILURE \${error::class.java.simpleName}: \${error.message}"
                        },
                        onVisualPhasePresented = { phase, atMs ->
                            visualTimes.putIfAbsent(phase, atMs)
                        },
                    )
                }
            }
        }

        val runtimeVideoFrameDir = File(evidenceDir, "runtime-video-frames").apply {
            deleteRecursively()
            mkdirs()
        }
        var runtimeFrameIndex = 0
        var resultSeenAtMs = Long.MIN_VALUE
        var levelACaptured = false

        fun captureRuntimeFrame() {
            if (runtimeFrameIndex >= 300) return
            val frame = File(
                runtimeVideoFrameDir,
                "runtime_frame_\${runtimeFrameIndex.toString().padStart(5, '0')}.png",
            )
            val captured = runCatching { device.takeScreenshot(frame) }.getOrDefault(false)
            if (captured && frame.isFile && frame.length() > 0L) runtimeFrameIndex += 1 else frame.delete()
        }

        trace("RUNTIME_VIDEO_CAPTURE_START")
        try {
            val hardDeadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < hardDeadline && runtimeFrameIndex < 300) {
                captureRuntimeFrame()

                if (!levelACaptured) {
                    val levelAVisible = runCatching {
                        composeRule.onNodeWithTag("recognition-fish-focus-level-a").fetchSemanticsNode()
                        true
                    }.getOrDefault(false)
                    if (levelAVisible) {
                        capture("10_level_a_contour.png")
                        levelACaptured = true
                    }
                }

                if (completed.value != null && resultSeenAtMs == Long.MIN_VALUE) {
                    resultSeenAtMs = SystemClock.elapsedRealtime()
                }
                if (resultSeenAtMs != Long.MIN_VALUE &&
                    SystemClock.elapsedRealtime() - resultSeenAtMs >= 3_300L
                ) {
                    break
                }
                Thread.sleep(16L)
            }
        } finally {
            trace("RUNTIME_VIDEO_CAPTURE_STOP")
            pipeline.close()
        }

        val result = requireNotNull(completed.value) { "production Recognition flow did not complete" }
        assertTrue(
            "real production fixture did not reach classifier-ready result: status=\${result.status} " +
                "detections=\${result.detectorRun.detections.size} failure=\${result.failureCode}",
            result.ready,
        )
        assertTrue("Level A contour was never presented from real bbox + alpha subject fixture", levelACaptured)

        val expectedPipeline = listOf(
            RecognitionPhase.CAPTURED,
            RecognitionPhase.DETECTING,
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING,
            RecognitionPhase.RESULT,
        )
        val actualPipeline = pipelineTrace.mapNotNull { line ->
            expectedPipeline.firstOrNull { line.contains("phase=\$it") }
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

        assertTrue("processing visual flow was outside runtime bound: \${totalMs}ms", totalMs in 2_500L..3_500L)
        assertTrue("final fish focus was too short: \${fishFocusStableMs}ms", fishFocusStableMs >= 1_000L)

        val primary = requireNotNull(result.assessment.primary)
        val box = primary.box.normalized()
        val subject = requireNotNull(subjectEvidence.value) { "subject alpha evidence was not generated" }
        File(evidenceDir, "recognition_processing_timing.txt").writeText(
            "Contract: CAPTURED=350ms DETECTING=600ms OUTLINE=600ms CLASSIFYING=1250ms TOTAL=2800ms\n" +
                "Runtime CAPTURED duration: \${capturedMs}ms\n" +
                "Runtime DETECTING duration: \${detectingMs}ms\n" +
                "Runtime OUTLINE duration: \${outlineMs}ms\n" +
                "Runtime CLASSIFYING duration: \${classifyingMs}ms\n" +
                "Runtime TOTAL duration: \${totalMs}ms\n" +
                "Runtime FINAL FISH FOCUS STABLE duration: \${fishFocusStableMs}ms\n",
        )
        File(evidenceDir, "recognition_production_flow_trace.txt").writeText(
            pipelineTrace.joinToString("\n") + "\n" +
                "REAL_BBOX confidence=\${primary.confidence} x1=\${box.x1} y1=\${box.y1} x2=\${box.x2} y2=\${box.y2}\n" +
                "SUBJECT_ALPHA status=\${subject.status} size=\${subject.width}x\${subject.height} " +
                "mask_area=\${subject.maskAreaRatio}\n" +
                "RESULT species=\${result.prediction?.top1?.speciesKey} confidence=\${result.prediction?.top1?.confidence}\n" +
                visualTimes.entries.joinToString("\n") { (phase, at) -> "VISUAL phase=\$phase at=\$at" } + "\n",
        )
        trace(
            "TIMING_CAPTURED_MS=\$capturedMs TIMING_DETECTING_MS=\$detectingMs " +
                "TIMING_OUTLINE_MS=\$outlineMs TIMING_CLASSIFYING_MS=\$classifyingMs " +
                "TIMING_TOTAL_MS=\$totalMs TIMING_FISH_FOCUS_STABLE_MS=\$fishFocusStableMs",
        )
    }

    @Test
    fun reduceMotionAndLowPerformanceKeepRecognitionSemanticStateStable() {
        val subject = createSubjectAlphaFixture(photo.bitmap, requireNotNull(high.assessment.primary).box)
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

        assertTrue("Reduce Motion still produced continuous visual travel: diffRatio=\$diffRatio", diffRatio <= 0.01f)
        capture("11_reduce_motion_low_performance.png")
    }

    private fun createSubjectAlphaFixture(source: Bitmap, detectorBox: NormalizedFishBox): FishSubjectResult {
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
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colors = IntArray(width * height)
        var foreground = 0
        for (y in 0 until height) {
            val ny = (y / (height - 1f).coerceAtLeast(1f) - .5f) * 2f
            for (x in 0 until width) {
                val nx = (x / (width - 1f).coerceAtLeast(1f) - .5f) * 2f
                val taperedBody = (nx * nx) / (.92f * .92f) + (ny * ny) / (.52f * .52f) <= 1f
                val tail = nx < -.58f && nx > -.98f &&
                    kotlin.math.abs(ny) <= (.42f * (nx + .98f) / .40f)
                val foregroundPixel = taperedBody || tail
                val sourcePixel = roi.getPixel(x, y)
                val alpha = if (foregroundPixel) 255 else 0
                if (alpha > 0) foreground += 1
                colors[y * width + x] = Color.argb(
                    alpha,
                    Color.red(sourcePixel),
                    Color.green(sourcePixel),
                    Color.blue(sourcePixel),
                )
            }
        }
        output.setPixels(colors, 0, width, 0, 0, width, height)
        val file = File(evidenceDir, "real_subject_alpha_fixture.png")
        file.outputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        output.recycle()
        roi.recycle()
        val total = width * height
        return FishSubjectResult(
            status = SubjectStatus.READY,
            bitmapPath = file.absolutePath,
            width = width,
            height = height,
            roiWidth = width,
            roiHeight = height,
            maskSize = total,
            expectedMaskSize = total,
            maskAreaRatio = foreground.toFloat() / total.coerceAtLeast(1),
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
        assertTrue("screenshot capture failed: $name", device.takeScreenshot(output))
        assertTrue("empty screenshot: $name", output.isFile && output.length() > 0L)
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        assertTrue("unreadable screenshot: $name", bitmap != null && bitmap.width > 0 && bitmap.height > 0)
        assertEquals(device.displayWidth, bitmap.width)
        assertEquals(device.displayHeight, bitmap.height)
        bitmap?.recycle()

        // Evidence remains in the target app cache during instrumentation.
        // CI exports it after the test through adb run-as; do not make the
        // instrumentation process depend on writing /data/local/tmp.
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
                onSave = { _, _ -> },
                onViewGuide = {},
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
                onRetry = {},
            )
        }
    }
    }
}
