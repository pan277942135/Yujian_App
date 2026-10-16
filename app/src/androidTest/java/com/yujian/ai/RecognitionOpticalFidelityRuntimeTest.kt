package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Choreographer
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.recognition.RecognitionDegradationLevel
import com.yujian.ai.ui.recognition.RecognitionMotionPolicy
import com.yujian.ai.ui.recognition.RecognitionMotionTraceSample
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.theme.YujianTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation

/**
 * Measures live V3.1 compositing on API 28 in FULL and LITE profiles. Frame
 * intervals are evidence only: the product contract defines no frame budget.
 */
class RecognitionOpticalFidelityRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recordsLiveFullAndLiteOpticalFidelityFramesWithoutInventingABudget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val outputDir = File(context.cacheDir, "recognition-optical-fidelity").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = testContext.assets.open("recognition_real_catch_fixture.jpg").use(BitmapFactory::decodeStream)
            ?: error("real catch photo fixture is unavailable")
        val selectedImage = SelectedImage("recognition-optical-fidelity-fixture", source, "instrumentation")
        val assessment = FishDetectionQualityGate.assess(
            listOf(FishDetection(0.92f, NormalizedFishBox(0.16f, 0.24f, 0.84f, 0.82f))),
        )
        assertTrue("controlled focus box must pass the real input quality gate", assessment.isClassifierEligible)
        val subject = createFocusMask(source, requireNotNull(assessment.primary).box, outputDir)
        val policy = mutableStateOf(
            RecognitionMotionPolicy(degradationLevel = RecognitionDegradationLevel.D0),
        )
        val classificationStarted = CountDownLatch(1)
        val unexpectedResultCount = AtomicInteger(0)
        val motionSamples = Collections.synchronizedList(mutableListOf<RecognitionMotionTraceSample>())
        val presentedPhases = Collections.synchronizedList(mutableListOf<RecognitionPhase>())

        composeRule.setContent {
            YujianTheme {
                RecognitionProcessingScene(
                    image = selectedImage,
                    onBack = {},
                    recognize = { onProgress ->
                        onProgress(RecognitionProgress(RecognitionPhase.CAPTURED))
                        onProgress(RecognitionProgress(RecognitionPhase.DETECTING))
                        onProgress(RecognitionProgress(RecognitionPhase.OUTLINE, assessment))
                        onProgress(RecognitionProgress(RecognitionPhase.CLASSIFYING, assessment))
                        classificationStarted.countDown()
                        awaitCancellation()
                    },
                    generateSubject = { _, _ -> subject },
                    onFinished = { unexpectedResultCount.incrementAndGet() },
                    onVisualPhasePresented = { phase, _ -> presentedPhases += phase },
                    motionPolicyOverride = policy.value,
                    onMotionFrame = { sample -> motionSamples += sample },
                )
            }
        }

        assertTrue("recognition pipeline did not enter CLASSIFYING", classificationStarted.await(3, TimeUnit.SECONDS))
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithText("正在认识这条鱼").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("正在认识这条鱼").assertIsDisplayed()

        val refreshRateHz = composeRule.activity.windowManager.defaultDisplay.refreshRate.toDouble()
        saveScreenshot(outputDir, "optical_fidelity_full.png")
        val fullIntervals = collectFrameIntervals(FRAME_INTERVAL_SAMPLES)
        val fullMotionSamples = synchronized(motionSamples) {
            motionSamples.filter { it.qualityLevel.name == "FULL" }
        }
        assertTrue("live FULL compositing did not report motion samples", fullMotionSamples.size >= MIN_MOTION_SAMPLES)

        composeRule.runOnUiThread {
            policy.value = RecognitionMotionPolicy(degradationLevel = RecognitionDegradationLevel.D2)
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            synchronized(motionSamples) {
                motionSamples.count { it.qualityLevel.name == "LITE" } >= MIN_MOTION_SAMPLES
            }
        }
        composeRule.onNodeWithText("正在认识这条鱼").assertIsDisplayed()
        saveScreenshot(outputDir, "optical_fidelity_lite.png")
        val liteIntervals = collectFrameIntervals(FRAME_INTERVAL_SAMPLES)
        val liteMotionSamples = synchronized(motionSamples) {
            motionSamples.filter { it.qualityLevel.name == "LITE" }
        }
        assertTrue("live LITE compositing did not report motion samples", liteMotionSamples.size >= MIN_MOTION_SAMPLES)
        assertEquals("suspended recognition must not report a result", 0, unexpectedResultCount.get())
        val presentedSnapshot = synchronized(presentedPhases) { presentedPhases.map { it.name }.distinct() }
        assertTrue("production scene never presented CLASSIFYING", "CLASSIFYING" in presentedSnapshot)

        val document = JSONObject()
            .put("contract_version", "recognition_processing_state_machine_v3_1")
            .put("measurement_method", "Choreographer.FrameCallback frame intervals while production RecognitionProcessingScene is live")
            .put("phase", "CLASSIFYING")
            .put("fixture", "real_photo_with_controlled_ellipse_alpha_mask")
            .put("refresh_rate_hz", refreshRateHz)
            .put("budget_policy", "MEASURED_ONLY_NO_FROZEN_THRESHOLD")
            .put("profiles", JSONArray()
                .put(profileMetrics("FULL", fullIntervals, fullMotionSamples.size, refreshRateHz))
                .put(profileMetrics("LITE", liteIntervals, liteMotionSamples.size, refreshRateHz)))
            .put("presented_phases", JSONArray(presentedSnapshot))
            .put("result_ready", false)
        File(outputDir, METRICS_FILENAME).writeText(document.toString(2) + "\n", Charsets.UTF_8)
    }

    private fun collectFrameIntervals(expectedCount: Int): List<Double> {
        val intervalsNanos = Collections.synchronizedList(mutableListOf<Long>())
        val completed = CountDownLatch(1)
        composeRule.runOnUiThread {
            val choreographer = Choreographer.getInstance()
            var previousFrameNanos = 0L
            lateinit var callback: Choreographer.FrameCallback
            callback = Choreographer.FrameCallback { frameTimeNanos ->
                if (previousFrameNanos != 0L) {
                    intervalsNanos += frameTimeNanos - previousFrameNanos
                }
                previousFrameNanos = frameTimeNanos
                if (intervalsNanos.size >= expectedCount) {
                    completed.countDown()
                } else {
                    choreographer.postFrameCallback(callback)
                }
            }
            choreographer.postFrameCallback(callback)
        }
        assertTrue("timed out while collecting Choreographer frames", completed.await(12, TimeUnit.SECONDS))
        return synchronized(intervalsNanos) { intervalsNanos.map { it / 1_000_000.0 } }
    }

    private fun saveScreenshot(directory: File, name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
    }

    private fun createFocusMask(source: Bitmap, box: NormalizedFishBox, outputDir: File): FishSubjectResult {
        val crop = FishDetectionQualityGate.cropBoxPixels(box.expand(.12f), source.width, source.height)
        val width = crop[2] - crop[0]
        val height = crop[3] - crop[1]
        val foreground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(foreground).drawBitmap(
            source,
            android.graphics.Rect(crop[0], crop[1], crop[2], crop[3]),
            android.graphics.Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawOval(
            RectF(width * .08f, height * .08f, width * .92f, height * .92f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE },
        )
        Canvas(foreground).drawBitmap(
            mask,
            0f,
            0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
        )
        val path = File(outputDir, "controlled_focus_mask.png")
        path.outputStream().use { stream ->
            check(foreground.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        mask.recycle()
        foreground.recycle()
        return FishSubjectResult(
            status = SubjectStatus.READY,
            bitmapPath = path.absolutePath,
            width = width,
            height = height,
            roiWidth = width,
            roiHeight = height,
            maskSize = width * height,
            expectedMaskSize = width * height,
            maskAreaRatio = .60f,
            quality = FishSubjectQuality.GOOD,
        )
    }

    private fun profileMetrics(
        name: String,
        intervals: List<Double>,
        motionSampleCount: Int,
        refreshRateHz: Double,
    ): JSONObject {
        assertTrue("$name profile returned too few frame intervals", intervals.size >= FRAME_INTERVAL_SAMPLES)
        val sorted = intervals.sorted()
        val mean = sorted.average()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }
        val p95 = sorted[(kotlin.math.ceil(sorted.size * .95).toInt() - 1).coerceAtLeast(0)]
        return JSONObject()
            .put("profile", name)
            .put("phase", "CLASSIFYING")
            .put("sample_count", sorted.size)
            .put("motion_sample_count", motionSampleCount)
            .put("refresh_rate_hz", refreshRateHz)
            .put("interval_ms", JSONObject()
                .put("mean", mean)
                .put("median", median)
                .put("p95", p95)
                .put("max", sorted.last())
                .put("frames_over_32ms", sorted.count { it > 32.0 }))
            .put("samples_ms", JSONArray(sorted))
            .put("budget_policy", "MEASURED_ONLY_NO_FROZEN_THRESHOLD")
    }

    private companion object {
        const val FRAME_INTERVAL_SAMPLES = 120
        const val MIN_MOTION_SAMPLES = 20
        const val METRICS_FILENAME = "recognition_optical_fidelity_v3_1.json"
    }
}
