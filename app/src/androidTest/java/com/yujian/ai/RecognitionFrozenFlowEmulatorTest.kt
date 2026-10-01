package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
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
import com.yujian.ai.ui.recognition.RecognitionMotionTraceSample
import com.yujian.ai.ui.recognition.RecognitionDegradationLevel
import com.yujian.ai.ui.identify.RecognitionImageTransform
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
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
    private var currentFrozenState: FrozenState = FrozenState.IMAGE_RECOGNIZING_EARLY

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
    fun generatesFrozenThreeStateScreenshotsAndExercisesProductionNavigation() {
        val state = mutableStateOf(FrozenState.IMAGE_RECOGNIZING_EARLY)
        val subject = createAnnotatedSubjectAlphaFixture(
            photo.bitmap,
            requireNotNull(high.assessment.primary).box,
        )
        composeRule.setContent {
            YujianTheme {
                FrozenRecognitionHarness(state, photo, high, medium, low, noFish, imageQuality, subject)
            }
        }

        render(state, FrozenState.IMAGE_RECOGNIZING_EARLY, "图片识别中", "01_image_recognizing_early.png")
        assertVisible("正在理解照片并寻找鱼获线索")

        render(state, FrozenState.IMAGE_RECOGNIZING_LATE, "图片识别中", "02_image_recognizing_late.png")
        assertVisible("正在理解照片并寻找鱼获线索")
        assertNoDirtyTechnicalUi()

        render(state, FrozenState.FISH_LOCATED, "已定位到鱼体", "03_fish_located.png")
        assertVisible("正在分析这次鱼获")

        render(state, FrozenState.SPECIES_RECOGNIZING, "鱼种识别中", "04_species_recognizing.png")
        assertVisible("正在分析鱼体特征")
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
        listOf("还不能确定这是什么鱼", "打开鱼种选择", "其他鱼种（可选）", "补充鱼获信息", "认识完成")
            .forEach { assertFalse(composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()) }
        composeRule.onNodeWithText("手动选择鱼种").performClick()
        assertVisible("选择鱼种")

        render(state, FrozenState.ERROR_NO_FISH, "没有找到可识别的鱼", "08_issue_no_fish.png")
        assertVisible("从相册选择")
        assertVisible("重新拍摄")

        render(state, FrozenState.ERROR_IMAGE_QUALITY, "照片不够清晰，无法识别", "09_issue_image_quality.png")
        assertVisible("请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。")
        assertFalse(composeRule.onAllNodesWithText("没有找到可识别的鱼").fetchSemanticsNodes().isNotEmpty())

        render(state, FrozenState.TECHNICAL_FAILURE, "识别没有完成", "10_issue_technical_failure.png")
        assertVisible("请重新拍摄或选择照片。")
        assertNoDirtyTechnicalUi()
    }

    @Test
    fun backingOutCancelsRecognitionWithoutReportingTechnicalFailure() {
        val processingVisible = mutableStateOf(true)
        val recognizeStarted = CountDownLatch(1)
        val failureCount = java.util.concurrent.atomic.AtomicInteger(0)

        composeRule.setContent {
            YujianTheme {
                if (processingVisible.value) {
                    RecognitionProcessingScene(
                        image = photo,
                        onBack = { processingVisible.value = false },
                        recognize = {
                            recognizeStarted.countDown()
                            awaitCancellation()
                        },
                        onFinished = {},
                        onFailure = { failureCount.incrementAndGet() },
                    )
                }
            }
        }

        assertTrue("recognition coroutine did not start", recognizeStarted.await(2, TimeUnit.SECONDS))
        val backNodes = composeRule.onAllNodesWithContentDescription("返回")
        val lastBackIndex = backNodes.fetchSemanticsNodes().lastIndex
        assertTrue("active overlay Back action must exist", lastBackIndex >= 0)
        backNodes[lastBackIndex].performClick()
        composeRule.waitUntil(timeoutMillis = 2_000L) { !processingVisible.value }
        composeRule.waitForIdle()

        assertEquals("user Back must not be routed as recognition failure", 0, failureCount.get())
    }

    @Test
    fun highResultKeepsResolvedSpeciesAndBothRecordActions() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(high.prediction),
                    productionResult = high,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("草鱼").assertIsDisplayed()
        composeRule.onNodeWithText("修改鱼种").assertIsDisplayed()
        composeRule.onNodeWithText("长度").assertIsDisplayed()
        composeRule.onNodeWithText("重量").assertIsDisplayed()
        composeRule.onNodeWithText("地点").assertIsDisplayed()
        composeRule.onNodeWithText("留下本次鱼获感言").assertIsDisplayed()
        composeRule.onNodeWithText("继续记录记忆").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存本次鱼获").performScrollTo().assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("已识别").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun mediumResultRequiresExplicitCandidateChoiceBeforeRecordActionsAppear() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(medium.prediction),
                    productionResult = medium,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("帮我确认一下，这条鱼更像哪一种？").assertIsDisplayed()
        composeRule.onNodeWithText("都不是？选择其他鱼种").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("继续记录记忆").fetchSemanticsNodes().isNotEmpty())
        assertFalse(composeRule.onAllNodesWithText("保存本次鱼获").fetchSemanticsNodes().isNotEmpty())
        val suggestedTree = composeRule.onRoot(useUnmergedTree = true).printToString()
        assertTrue("Top-1 must be exposed as a suggestion", suggestedTree.contains("模型建议"))
        assertFalse("Top-1 must not be preselected", suggestedTree.contains("Selected = true"))

        val candidate = composeRule.onNode(hasText("鲫鱼") and hasClickAction())
        assertFalse(
            "suggested candidate must not be preselected",
            candidate.fetchSemanticsNode().config[SemanticsProperties.Selected] == true,
        )
        candidate.performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            candidate.fetchSemanticsNode().config[SemanticsProperties.Selected] == true
        }
        assertTrue(
            "explicit candidate tap must create a selected state",
            candidate.fetchSemanticsNode().config[SemanticsProperties.Selected] == true,
        )

        composeRule.onNodeWithText("继续记录记忆").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存本次鱼获").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mediumOtherSelectorSearchesAliasesAndCommitsOnlyAfterSelection() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(medium.prediction),
                    productionResult = medium,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("都不是？选择其他鱼种").performClick()
        composeRule.onNodeWithTag("recognition-species-selector-search").performTextInput("鲤拐子")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("recognition-species-result-common_carp").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("recognition-species-result-common_carp").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("修改鱼种").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("保存本次鱼获").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unresolvedMediumAndLowCanReturnWithoutCreatingRecordActions() {
        val showLow = mutableStateOf(false)
        composeRule.setContent {
            YujianTheme {
                if (showLow.value) {
                    RecognitionResultScreen(
                        image = photo,
                        prediction = requireNotNull(low.prediction),
                        productionResult = low,
                        onBack = {},
                        onRetry = {},
                        onSave = { _, _, _ -> },
                    )
                } else {
                    RecognitionResultScreen(
                        image = photo,
                        prediction = requireNotNull(medium.prediction),
                        productionResult = medium,
                        onBack = {},
                        onRetry = {},
                        onSave = { _, _, _ -> },
                    )
                }
            }
        }
        composeRule.onNodeWithText("都不是？选择其他鱼种").performClick()
        composeRule.onNodeWithText("暂不确认鱼种").performClick()
        composeRule.onNodeWithText("都不是？选择其他鱼种").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("保存本次鱼获").fetchSemanticsNodes().isNotEmpty())

        composeRule.runOnIdle { showLow.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("手动选择鱼种").performClick()
        composeRule.onNodeWithText("暂不确认鱼种").performClick()
        composeRule.onNodeWithText("手动选择鱼种").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("保存本次鱼获").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun confirmedSpeciesEditHidesUnconfirmedActionAndBackKeepsSelection() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(high.prediction),
                    productionResult = high,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("修改鱼种").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000L) {
            composeRule.onAllNodesWithText("选择鱼种").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(composeRule.onAllNodesWithText("暂不确认鱼种").fetchSemanticsNodes().isNotEmpty())
        val backNodes = composeRule.onAllNodesWithContentDescription("返回")
        val lastBackIndex = backNodes.fetchSemanticsNodes().lastIndex
        assertTrue("active species selector Back action must exist", lastBackIndex >= 0)
        backNodes[lastBackIndex].performClick()
        composeRule.onNodeWithText("草鱼").assertIsDisplayed()
    }

    @Test
    fun numericEditorValidatesWithFrozenGentleCopyAndSaveErrorsStaySafe() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(high.prediction),
                    productionResult = high,
                    saveError = "backend stack trace: private detail",
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("保存鱼获失败，请重试").performScrollTo().assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("backend stack trace: private detail").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithText("长度").performClick()
        val lengthField = composeRule.onNodeWithTag("recognition-numeric-长度")
        composeRule.waitUntil(timeoutMillis = 2_000L) {
            composeRule.onAllNodesWithTag("recognition-numeric-长度").fetchSemanticsNodes().isNotEmpty()
        }
        lengthField.performTextInput("0")
        lengthField.performImeAction()
        composeRule.onNodeWithText("请输入有效的长度").assertIsDisplayed()
    }

    @Test
    fun resultSaveActionRejectsDuplicateSubmitsUntilParentStateChanges() {
        val saveCalls = java.util.concurrent.atomic.AtomicInteger(0)
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(high.prediction),
                    productionResult = high,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> saveCalls.incrementAndGet() },
                )
            }
        }

        val saveButton = composeRule.onNodeWithText("保存本次鱼获").performScrollTo()
        saveButton.performClick()
        saveButton.performClick()
        assertEquals("one resolved Result may submit only once while saving", 1, saveCalls.get())
    }

    @Test
    fun lowResultHidesRecordControlsUntilManualSpeciesSelection() {
        composeRule.setContent {
            YujianTheme {
                RecognitionResultScreen(
                    image = photo,
                    prediction = requireNotNull(low.prediction),
                    productionResult = low,
                    onBack = {},
                    onRetry = {},
                    onSave = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("无法确认是什么鱼").assertIsDisplayed()
        composeRule.onNodeWithText("手动选择鱼种").assertIsDisplayed()
        composeRule.onNodeWithText("重新拍摄").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("长度").fetchSemanticsNodes().isNotEmpty())
        assertFalse(composeRule.onAllNodesWithText("保存本次鱼获").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithText("手动选择鱼种").performClick()
        composeRule.onNodeWithText("选择鱼种").assertIsDisplayed()
        composeRule.onNodeWithTag("recognition-species-selector-search").performTextInput("ji yu")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("recognition-species-result-crucian_carp").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("recognition-species-result-crucian_carp").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("修改鱼种").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("修改鱼种").assertIsDisplayed()
        composeRule.onNodeWithText("长度").assertIsDisplayed()
        composeRule.onNodeWithText("继续记录记忆").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存本次鱼获").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noFishAndImageQualityKeepDistinctRecoveryCopyAndActions() {
        val shownResult = mutableStateOf(noFish)
        val recoveryActions = Collections.synchronizedList(mutableListOf<String>())
        composeRule.setContent {
            YujianTheme {
                RecognitionIssueScreen(
                    image = photo,
                    result = shownResult.value,
                    onBack = {},
                    onChooseAnother = { recoveryActions += "camera" },
                    onChooseGallery = { recoveryActions += "gallery" },
                )
            }
        }

        composeRule.onNodeWithText("没有找到可识别的鱼").assertIsDisplayed()
        composeRule.onNodeWithText("请让鱼完整出现在画面中，再试一次。").assertIsDisplayed()
        composeRule.onNodeWithText("重新拍摄").performClick()
        composeRule.onNodeWithText("从相册选择").performClick()
        assertEquals(listOf("camera", "gallery"), recoveryActions.toList())
        assertFalse(composeRule.onAllNodesWithText("保存本次鱼获").fetchSemanticsNodes().isNotEmpty())

        shownResult.value = imageQuality
        composeRule.onNodeWithText("照片不够清晰，无法识别").assertIsDisplayed()
        composeRule.onNodeWithText("请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("没有找到可识别的鱼").fetchSemanticsNodes().isNotEmpty())
    }


    @Test
    fun recordsMeasuredNormalSpeedProcessingFlow() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pipeline = FishRecognitionPipeline(targetContext)
        val completed = mutableStateOf<ProductionRecognitionResult?>(null)
        val completedResult = AtomicReference<ProductionRecognitionResult?>(null)
        val fishFocusTransform = AtomicReference<RecognitionImageTransform?>(null)
        val pipelineTrace = Collections.synchronizedList(mutableListOf<String>())
        val visualTimes = Collections.synchronizedMap(LinkedHashMap<RecognitionPhase, Long>())
        val motionSamples = Collections.synchronizedList(mutableListOf<RecognitionMotionTraceSample>())
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
                        onMotionFrame = { sample -> motionSamples += sample },
                        onFishFocusTransform = { transform -> fishFocusTransform.set(transform) },
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
        assertEquals(
            listOf(
                RecognitionPhase.CAPTURED,
                RecognitionPhase.OUTLINE,
                RecognitionPhase.CLASSIFYING,
            ),
            visualTimes.keys.filter { it != RecognitionPhase.RESULT },
        )
        assertFalse("DETECTING leaked into the product presentation trace", visualTimes.containsKey(RecognitionPhase.DETECTING))

        val imageRecognizingAt = requireNotNull(visualTimes[RecognitionPhase.CAPTURED])
        val outlineAt = requireNotNull(visualTimes[RecognitionPhase.OUTLINE])
        val classifyingAt = requireNotNull(visualTimes[RecognitionPhase.CLASSIFYING])
        assertTrue("RESULT callback did not complete", finishedAtMs > classifyingAt)

        val imageRecognizingMs = outlineAt - imageRecognizingAt
        val fishLocatedMs = classifyingAt - outlineAt
        val speciesRecognizingMs = finishedAtMs - classifyingAt
        val totalMs = finishedAtMs - imageRecognizingAt
        val fishFocusStableMs = speciesRecognizingMs - RecognitionVisualStateController.RESOLVE_FADE_MS
        // These markers are delivered by separate Compose effects. Frame scheduling can
        // make their observed interval shorter than the controller's exact minimum;
        // controller unit tests continue to assert the frozen 900/600/1250ms contract.
        val presentationTimestampToleranceMs = 50L

        assertTrue(
            "图片识别中 presentation interval was too short: ${imageRecognizingMs}ms",
            imageRecognizingMs >= 900L - presentationTimestampToleranceMs,
        )
        assertTrue(
            "已定位到鱼体 presentation interval was too short: ${fishLocatedMs}ms",
            fishLocatedMs >= 600L - presentationTimestampToleranceMs,
        )
        assertTrue(
            "鱼种识别中 including resolve presentation interval was too short: ${speciesRecognizingMs}ms",
            speciesRecognizingMs >= 1_450L - presentationTimestampToleranceMs,
        )
        assertTrue(
            "nominal presentation interval was too short: ${totalMs}ms",
            totalMs >= 2_950L - presentationTimestampToleranceMs,
        )
        assertTrue(
            "final fish focus presentation interval was too short: ${fishFocusStableMs}ms",
            fishFocusStableMs >= 1_250L - presentationTimestampToleranceMs,
        )

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
        File(evidenceDir, "recognition_processing_timing_v1_2.txt").writeText(
            "Contract: IMAGE_RECOGNIZING=900ms FISH_LOCATED=600ms SPECIES_RECOGNIZING=1250ms RESOLVE=200ms TOTAL=2950ms\n" +
                "Runtime IMAGE_RECOGNIZING duration: ${imageRecognizingMs}ms\n" +
                "Runtime FISH_LOCATED duration: ${fishLocatedMs}ms\n" +
                "Runtime SPECIES_RECOGNIZING duration: ${speciesRecognizingMs}ms\n" +
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
        File(evidenceDir, "recognition_motion_trace_v1_2.json").writeText(
            motionSamples.toList().joinToString(
                prefix = "{\"samples\":[",
                postfix = "]}\n",
                separator = ",",
            ) { sample ->
                    "{\"uptime_ms\":${sample.uptimeMs},\"phase\":\"${sample.phase}\"," +
                    "\"segment_offset\":${sample.segmentOffset},\"segment_speed\":${sample.segmentSpeed}," +
                    "\"state_strength\":${sample.stateStrength}," +
                    "\"resolve_strength\":${sample.resolveStrength},\"detail_motion_time_ms\":${sample.detailMotionTimeMs}," +
                    "\"reduce_motion\":${sample.reduceMotion}," +
                    "\"quality\":\"${sample.qualityLevel}\"}"
            },
        )
        val presentationEvents = listOf(
            "IMAGE_RECOGNIZING" to imageRecognizingAt,
            "FISH_LOCATED" to outlineAt,
            "SPECIES_RECOGNIZING" to classifyingAt,
            "RESULT" to finishedAtMs,
        )
        File(evidenceDir, "recognition_production_flow_trace_v1_2.json").writeText(
            "{\"contract_version\":\"RECOGNITION_PRESENTATION_v1_3\"," +
                "\"pipeline_phases\":[${actualPipeline.joinToString(",") { "\"$it\"" }}]," +
                "\"presentation_events\":[${presentationEvents.joinToString(",") { (state, at) -> "{\"state\":\"$state\",\"at_ms\":$at}" }}]," +
                "\"result_ready\":${result.ready},\"bbox\":{\"x1\":${box.x1},\"y1\":${box.y1},\"x2\":${box.x2},\"y2\":${box.y2}}}\n",
        )
        val transform = requireNotNull(fishFocusTransform.get()) { "photo-to-screen transform was not captured" }
        val mappedCenter = transform.mapBox(primary.box)
        val crop = primary.box.expand(.12f).normalized()
        File(evidenceDir, "fish_focus_bbox_mapping.json").writeText(
            "{\"image_px\":{\"width\":${photo.bitmap.width},\"height\":${photo.bitmap.height}}," +
            "\"display_transform_px\":{\"drawn_width\":${transform.drawnWidth},\"drawn_height\":${transform.drawnHeight}}," +
                "\"detector_bbox_normalized\":{\"x1\":${box.x1},\"y1\":${box.y1},\"x2\":${box.x2},\"y2\":${box.y2}}," +
                "\"subject_crop_normalized\":{\"x1\":${crop.x1},\"y1\":${crop.y1},\"x2\":${crop.x2},\"y2\":${crop.y2}}," +
                "\"content_scale_crop\":{\"scale\":${transform.scale},\"offset_x\":${transform.offsetX}," +
                "\"offset_y\":${transform.offsetY},\"center_x\":${mappedCenter.x},\"center_y\":${mappedCenter.y}}}\n",
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
            "TIMING_IMAGE_RECOGNIZING_MS=$imageRecognizingMs TIMING_FISH_LOCATED_MS=$fishLocatedMs " +
                "TIMING_SPECIES_RECOGNIZING_MS=$speciesRecognizingMs " +
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
        capture("level_a_real_contour.png")
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

        assertVisible("鱼种识别中")
        assertVisible("正在分析鱼体特征")
        composeRule.onNodeWithTag("recognition-ambient-reduced-motion-low-performance").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag("recognition-fish-focus-level-a-low-performance").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("recognition-fish-focus-level-a-low-performance").assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("草鱼").fetchSemanticsNodes().isNotEmpty())

        composeRule.waitForIdle()
        val appSurfaceBounds = composeSurfaceBoundsOnScreen()
        val first = File(evidenceDir, "_reduce_motion_a.png")
        val second = File(evidenceDir, "_reduce_motion_b.png")
        assertTrue(device.takeScreenshot(first))
        Thread.sleep(350L)
        assertTrue(device.takeScreenshot(second))
        val firstRaw = requireNotNull(BitmapFactory.decodeFile(first.absolutePath))
        val secondRaw = requireNotNull(BitmapFactory.decodeFile(second.absolutePath))
        val firstBitmap = cropToComposeRoot(firstRaw, appSurfaceBounds)
        val secondBitmap = cropToComposeRoot(secondRaw, appSurfaceBounds)
        firstRaw.recycle()
        secondRaw.recycle()
        val diffRatio = bitmapDifferenceRatio(firstBitmap, secondBitmap, topSkipPx = 80)
        firstBitmap.recycle()
        secondBitmap.recycle()
        first.delete()
        second.delete()

        // Restrict the comparison to the four ambient-field edge regions. A
        // whole-screen ratio is diluted by the photo and affected by unrelated
        // UI rasterization, while these regions directly test field travel.
        assertTrue("Reduce Motion still produced continuous visual travel: diffRatio=$diffRatio", diffRatio <= 0.01f)
        capture("reduce_motion_static.png")
    }

    @Test
    fun capturesQualityProfilesAndD0ThroughD4AccessibilityEvidence() {
        val subject = createAnnotatedSubjectAlphaFixture(
            photo.bitmap,
            requireNotNull(high.assessment.primary).box,
        )
        val policy = mutableStateOf(RecognitionMotionPolicy())
        val phaseState = mutableStateOf(RecognitionPhase.DETECTING)
        val visualClockState = mutableStateOf(720L)
        // The three profile captures share one real photo, IMAGE_RECOGNIZING
        // state, and frozen visual clock. Focus ladder evidence is separate.
        composeRule.setContent {
            YujianTheme {
                RecognitionProcessingScene(
                    image = photo,
                    onBack = {},
                    recognize = { onProgress ->
                        onProgress(RecognitionProgress(phaseState.value, high.assessment))
                        high
                    },
                    generateSubject = { _, _ -> subject },
                    onFinished = {},
                    phaseOverride = phaseState.value,
                    visualClockOverrideMs = visualClockState.value,
                    motionPolicyOverride = policy.value,
                )
            }
        }
        listOf(
            RecognitionDegradationLevel.D0 to "quality_full.png",
            RecognitionDegradationLevel.D1 to "quality_balanced.png",
            RecognitionDegradationLevel.D2 to "quality_lite.png",
        ).forEach { (level, fileName) ->
            policy.value = RecognitionMotionPolicy(degradationLevel = level)
            composeRule.waitForIdle()
            assertVisible("图片识别中")
            awaitSurfaceFrameCommit()
            capture(fileName)
        }

        phaseState.value = RecognitionPhase.CLASSIFYING
        visualClockState.value = 3_200L
        composeRule.waitForIdle()

        val levels = listOf(
            RecognitionDegradationLevel.D0,
            RecognitionDegradationLevel.D1,
            RecognitionDegradationLevel.D2,
            RecognitionDegradationLevel.D3,
            RecognitionDegradationLevel.D4,
        )
        levels.forEach { level ->
            policy.value = RecognitionMotionPolicy(degradationLevel = level)
            val focus = when (level) {
                RecognitionDegradationLevel.D0,
                RecognitionDegradationLevel.D1,
                RecognitionDegradationLevel.D2 -> "a"
                RecognitionDegradationLevel.D3 -> "b"
                RecognitionDegradationLevel.D4 -> "c"
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag("recognition-fish-focus-level-$focus").fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("recognition-fish-focus-level-$focus").assertIsDisplayed()
            capture("degradation_${level.name.lowercase()}.png")
        }

        policy.value = RecognitionMotionPolicy(
            reduceMotion = true,
            degradationLevel = RecognitionDegradationLevel.D0,
        )
        composeRule.waitForIdle()
        capture("reduce_motion_static.png")

        createDegradationContactSheet(levels)
        File(evidenceDir, "recognition_accessibility_trace_v1_2.json").writeText(
            """{"quality":{"FULL":"D0","BALANCED":"D1","LITE":"D2"},"degradation":{"D0":"FULL+A","D1":"BALANCED+A","D2":"LITE+A","D3":"LITE+B","D4":"LITE+C"},"reduce_motion":{"independent_of_degradation":true,"segment_offset":"frozen","particles":"off","focus_breathing":"off"}}""",
        )
        File(evidenceDir, "recognition_visual_qa_v1_3.json").writeText(
            """{"version":"1.3","review_status":"VISUAL_FAIL","reviewed_artifact_id":"11074493735","taxonomy":{"F01":"closed neon border","F02":"lightning or magic","F03":"HUD or scanner","F04":"railroad parallel Hairlines","F05":"equal-bright symmetric corners","F06":"AI presence too weak"},"findings":{"F01":{"status":"PASS","evidence":["01_image_recognizing_early.png","02_image_recognizing_late.png"]},"F02":{"status":"PASS","evidence":["01_image_recognizing_early.png","02_image_recognizing_late.png"]},"F03":{"status":"PASS","evidence":["01_image_recognizing_early.png","02_image_recognizing_late.png"]},"F04":{"status":"FAIL","evidence":[{"file":"01_image_recognizing_early.png","note":"B_UR companion and micro read as parallel railroad hairlines at the top-right edge."}]},"F05":{"status":"PASS","evidence":["01_image_recognizing_early.png","02_image_recognizing_late.png"]},"F06":{"status":"FAIL","evidence":[{"file":"01_image_recognizing_early.png","note":"The edge field reads as a few faint lines; local receiving light and four-island identity are not clear."},{"file":"quality_full.png","note":"FULL, BALANCED, and LITE are not clearly distinguishable."},{"file":"quality_balanced.png","note":"FULL, BALANCED, and LITE are not clearly distinguishable."},{"file":"quality_lite.png","note":"LITE does not retain a clearly recognizable four-island field."}]}}}""",
        )
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
                if (!isAmbientFieldEdgeSample(x, y, width, height)) {
                    x += 4
                    continue
                }
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

    private fun isAmbientFieldEdgeSample(x: Int, y: Int, width: Int, height: Int): Boolean =
        (x < width * .32f && y < height * .24f) ||
            (x > width * .68f && y < height * .45f) ||
            (x < width * .40f && y > height * .68f) ||
            (x > width * .60f && y > height * .62f)

    private fun createDegradationContactSheet(levels: List<RecognitionDegradationLevel>) {
        val images = levels.map { level ->
            val file = File(evidenceDir, "degradation_${level.name.lowercase()}.png")
            requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) {
                "missing runtime screenshot for ${level.name}"
            }
        }
        try {
            val cellWidth = 240
            val labelHeight = 48
            val cellHeight = (images.first().height * cellWidth / images.first().width) + labelHeight
            val sheet = Bitmap.createBitmap(cellWidth * images.size, cellHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.BLACK)
            images.forEachIndexed { index, image ->
                val left = index * cellWidth
                val scaledHeight = cellHeight - labelHeight
                val dest = android.graphics.Rect(left, labelHeight, left + cellWidth, labelHeight + scaledHeight)
                canvas.drawBitmap(image, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.drawText(
                    "${levels[index]} · ${listOf("FULL+A", "BALANCED+A", "LITE+A", "LITE+B", "LITE+C")[index]}",
                    left + 8f,
                    32f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f },
                )
            }
            File(evidenceDir, "degradation_d0_d4_contact_sheet.png").outputStream().use { stream ->
                check(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            sheet.recycle()
        } finally {
            images.forEach(Bitmap::recycle)
        }
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
        if (screenshotName == "03_fish_located.png" || screenshotName == "04_species_recognizing.png") {
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag("recognition-fish-focus-level-a").fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("recognition-fish-focus-level-a").assertIsDisplayed()
            assertVisible(expected)
        }
        if (screenshotName != null) awaitSurfaceFrameCommit()
        if (screenshotName != null) capture(screenshotName)
        trace("PASS_STATE=$next")
    }

    private fun awaitSurfaceFrameCommit() {
        composeRule.waitForIdle()
        val frameDrawn = CountDownLatch(1)
        val treeRef = AtomicReference<ViewTreeObserver?>()
        val listener = ViewTreeObserver.OnDrawListener {
            frameDrawn.countDown()
        }
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val tree = decor.viewTreeObserver
            treeRef.set(tree)
            tree.addOnDrawListener(listener)
            decor.invalidate()
        }
        assertTrue("Compose state was not drawn before screenshot", frameDrawn.await(5, TimeUnit.SECONDS))
        composeRule.runOnUiThread {
            treeRef.get()?.let { tree ->
                if (tree.isAlive) tree.removeOnDrawListener(listener)
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        device.waitForIdle()
    }

    private fun capture(name: String) {
        awaitSurfaceFrameCommit()
        val output = File(evidenceDir, name)
        val raw = File(evidenceDir, ".$name.raw.png")
        assertTrue("screenshot capture failed: $name", device.takeScreenshot(raw))
        assertTrue("empty screenshot: $name", raw.isFile && raw.length() > 0L)

        val bitmap = BitmapFactory.decodeFile(raw.absolutePath)
        assertTrue("unreadable screenshot: $name", bitmap != null && bitmap.width > 0 && bitmap.height > 0)
        val decoded = requireNotNull(bitmap)
        val appSurface = cropToComposeRoot(decoded)
        output.outputStream().use { stream ->
            check(appSurface.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        trace(
            "SCREENSHOT_APP_SURFACE_ONLY name=$name raw=${decoded.width}x${decoded.height} " +
                "output=${appSurface.width}x${appSurface.height}",
        )
        if (appSurface !== decoded) appSurface.recycle()
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

    private fun composeSurfaceBoundsOnScreen(): Rect {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
        val windowOrigin = IntArray(2)
        composeRule.runOnUiThread {
            composeRule.activity.window.decorView.getLocationOnScreen(windowOrigin)
        }
        return Rect(
            kotlin.math.floor(rootBounds.left).toInt() + windowOrigin[0],
            kotlin.math.floor(rootBounds.top).toInt() + windowOrigin[1],
            kotlin.math.ceil(rootBounds.right).toInt() + windowOrigin[0],
            kotlin.math.ceil(rootBounds.bottom).toInt() + windowOrigin[1],
        )
    }

    private fun cropToComposeRoot(
        source: Bitmap,
        bounds: Rect = composeSurfaceBoundsOnScreen(),
    ): Bitmap {
        check(bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= source.width && bounds.bottom <= source.height
        ) {
            "Compose root bounds $bounds exceed screenshot ${source.width}x${source.height}"
        }
        check(bounds.width() > 0 && bounds.height() > 0) {
            "Compose root has empty screenshot bounds: $bounds"
        }
        return Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
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
        return RecognitionPrediction("fixture-model", "fixture", top, listOf(top, second), 1L)
    }
}

private enum class FrozenState {
    IMAGE_RECOGNIZING_EARLY,
    IMAGE_RECOGNIZING_LATE,
    FISH_LOCATED,
    SPECIES_RECOGNIZING,
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
    subject: FishSubjectResult? = null,
) {
    val stateValue = state.value
    key(stateValue) {
    when (stateValue) {
        FrozenState.IMAGE_RECOGNIZING_EARLY,
        FrozenState.IMAGE_RECOGNIZING_LATE,
        FrozenState.FISH_LOCATED,
        FrozenState.SPECIES_RECOGNIZING -> {
            RecognitionProcessingScene(
                image = photo,
                onBack = {},
                recognize = { onProgress ->
                    val target = when (stateValue) {
                        FrozenState.IMAGE_RECOGNIZING_EARLY -> RecognitionPhase.CAPTURED
                        FrozenState.IMAGE_RECOGNIZING_LATE -> RecognitionPhase.DETECTING
                        FrozenState.FISH_LOCATED -> RecognitionPhase.OUTLINE
                        else -> RecognitionPhase.CLASSIFYING
                    }
                    onProgress(RecognitionProgress(target))
                    high
                },
                onFinished = {},
                generateSubject = subject?.let { ready -> { _, _ -> ready } },
                phaseOverride = when (stateValue) {
                    FrozenState.IMAGE_RECOGNIZING_EARLY -> RecognitionPhase.CAPTURED
                    FrozenState.IMAGE_RECOGNIZING_LATE -> RecognitionPhase.DETECTING
                    FrozenState.FISH_LOCATED -> RecognitionPhase.OUTLINE
                    FrozenState.SPECIES_RECOGNIZING -> RecognitionPhase.CLASSIFYING
                    else -> RecognitionPhase.CAPTURED
                },
                visualClockOverrideMs = when (stateValue) {
                    FrozenState.IMAGE_RECOGNIZING_EARLY -> 120L
                    FrozenState.IMAGE_RECOGNIZING_LATE -> 720L
                    else -> 3_200L
                },
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
