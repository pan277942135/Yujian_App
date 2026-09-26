package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
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
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.screens.RecognitionIssueScreen
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.theme.YujianTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Deterministic emulator coverage for the production Recognition composables.
 * The harness injects pipeline state, but never substitutes a test-only screen.
 */
class RecognitionFrozenFlowEmulatorTest {
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

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        device = UiDevice.getInstance(instrumentation)
        evidenceDir = File(targetContext.cacheDir, "recognition-evidence").apply {
            deleteRecursively()
            mkdirs()
        }
        device.executeShellCommand("rm -rf /data/local/tmp/recognition-evidence && mkdir -p /data/local/tmp/recognition-evidence")
        val bitmap = testContext.assets.open("golden_yellow_catfish_224.jpg").use(BitmapFactory::decodeStream)
            ?: error("golden photo fixture is unavailable")
        photo = SelectedImage("recognition-emulator-fixture", bitmap, "instrumentation")

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

        render(state, FrozenState.AI_UNDERSTANDING, "正在理解照片", "02_ai_understanding.png")
        assertVisible("查找鱼获线索")
        assertNoDirtyTechnicalUi()

        render(state, FrozenState.FISH_HIGHLIGHT, "已定位到鱼体", "03_fish_highlight.png")
        assertVisible("正在分析这次鱼获")

        render(state, FrozenState.FISH_IDENTIFYING, "正在认识这条鱼", "04_fish_identifying.png")
        assertVisible("分析鱼体特征")
        assertFalse(composeRule.onAllNodesWithText("草鱼").fetchSemanticsNodes().isNotEmpty())

        render(state, FrozenState.RESULT_HIGH, "修改鱼种", "05_result_high.png")
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
        assertVisible("请拍摄更清晰的照片")
        assertFalse(composeRule.onAllNodesWithText("没有找到可识别的鱼").fetchSemanticsNodes().isNotEmpty())

        render(state, FrozenState.TECHNICAL_FAILURE, "识别没有完成", null)
        assertVisible("请重新拍摄或选择照片")
        assertNoDirtyTechnicalUi()
    }

    private fun render(state: MutableState<FrozenState>, next: FrozenState, expected: String, screenshotName: String?) {
        composeRule.runOnUiThread { state.value = next }
        composeRule.waitForIdle()
        assertVisible(expected)
        if (screenshotName != null) capture(screenshotName)
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
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        try {
            composeRule.onNodeWithText(text).assertIsDisplayed()
        } catch (error: AssertionError) {
            val tree = composeRule.onRoot(useUnmergedTree = true).printToString()
            throw AssertionError("Expected visible component: $text\n$tree", error)
        }
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
    val nav = rememberNavController()
    val stateValue = state.value
    LaunchedEffect(stateValue) {
        val route = when (stateValue) {
            FrozenState.CAPTURE_TRANSITION,
            FrozenState.AI_UNDERSTANDING,
            FrozenState.FISH_HIGHLIGHT,
            FrozenState.FISH_IDENTIFYING -> "recognizing"
            FrozenState.RESULT_HIGH,
            FrozenState.RESULT_MEDIUM,
            FrozenState.RESULT_LOW -> "result"
            FrozenState.ERROR_NO_FISH,
            FrozenState.ERROR_IMAGE_QUALITY,
            FrozenState.TECHNICAL_FAILURE -> "recognition_issue"
        }
        nav.navigate(route) { launchSingleTop = true }
    }
    NavHost(nav, startDestination = "recognizing") {
        composable("recognizing") {
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
            )
        }
        composable("result") {
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
        composable("recognition_issue") {
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
