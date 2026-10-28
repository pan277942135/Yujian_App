package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import kotlinx.coroutines.awaitCancellation

/**
 * Measures live V3.1 compositing on API 28 in FULL and LITE profiles. Window
 * frame durations are evidence only: the product contract defines no frame budget.
 */
class RecognitionOpticalFidelityRuntimeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var frameMetricsThread: HandlerThread? = null
    private var frameMetricsWindow: Window? = null
    private var frameMetricsDecorView: View? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    @After
    fun releaseFrameMetricsListener() {
        val window = frameMetricsWindow
        val listener = frameMetricsListener
        if (window != null && listener != null) {
            frameMetricsDecorView?.post { window.removeOnFrameMetricsAvailableListener(listener) }
        }
        frameMetricsThread?.quitSafely()
        frameMetricsThread = null
        frameMetricsWindow = null
        frameMetricsDecorView = null
        frameMetricsListener = null
    }

    @Test
    fun recordsLiveFullAndLiteOpticalFidelityFramesWithoutInventingABudget() {
        Log.i(LOG_TAG, "Milestone: test-start")
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
        val classificationPresented = CountDownLatch(1)
        val unexpectedResultCount = AtomicInteger(0)
        val fullMotionSampleCount = AtomicInteger(0)
        val liteMotionSampleCount = AtomicInteger(0)
        val presentedPhases = Collections.synchronizedList(mutableListOf<RecognitionPhase>())
        val refreshRate = AtomicReference(60.0)
        val activeProfile = AtomicReference<String?>(null)
        val fullFrameDurationsMs = Collections.synchronizedList(mutableListOf<Double>())
        val liteFrameDurationsMs = Collections.synchronizedList(mutableListOf<Double>())
        val fullDroppedFrames = AtomicInteger(0)
        val liteDroppedFrames = AtomicInteger(0)
        val fullFramesReady = CountDownLatch(1)
        val liteFramesReady = CountDownLatch(1)
        frameMetricsThread = HandlerThread("recognition-frame-metrics").apply { start() }
        val metricsHandler = Handler(requireNotNull(frameMetricsThread).looper)
        val metricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedFrames ->
            val frameDurationMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            if (frameDurationMs > 0.0) {
                when (activeProfile.get()) {
                    "FULL" -> synchronized(fullFrameDurationsMs) {
                        if (fullFrameDurationsMs.size < FRAME_METRICS_SAMPLES) {
                            fullFrameDurationsMs += frameDurationMs
                            fullDroppedFrames.addAndGet(droppedFrames)
                            if (fullFrameDurationsMs.size >= FRAME_METRICS_SAMPLES) fullFramesReady.countDown()
                        }
                    }
                    "LITE" -> synchronized(liteFrameDurationsMs) {
                        if (liteFrameDurationsMs.size < FRAME_METRICS_SAMPLES) {
                            liteFrameDurationsMs += frameDurationMs
                            liteDroppedFrames.addAndGet(droppedFrames)
                            if (liteFrameDurationsMs.size >= FRAME_METRICS_SAMPLES) liteFramesReady.countDown()
                        }
                    }
                }
            }
        }
        frameMetricsListener = metricsListener

        activityRule.scenario.onActivity { activity ->
            Log.i(LOG_TAG, "Milestone: install-production-scene")
            refreshRate.set(activity.windowManager.defaultDisplay.refreshRate.toDouble())
            frameMetricsWindow = activity.window
            frameMetricsDecorView = activity.window.decorView
            activity.window.addOnFrameMetricsAvailableListener(metricsListener, metricsHandler)
            activity.setContent {
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
                        onVisualPhasePresented = { phase, _ ->
                            presentedPhases += phase
                            Log.i(LOG_TAG, "Milestone: phase-presented=$phase")
                            if (phase == RecognitionPhase.CLASSIFYING) classificationPresented.countDown()
                        },
                        motionPolicyOverride = policy.value,
                        onMotionFrame = { sample ->
                            if (sample.qualityLevel.name == "LITE") {
                                if (liteMotionSampleCount.incrementAndGet() == 1) {
                                    Log.i(LOG_TAG, "Milestone: first-motion-sample=LITE")
                                }
                            } else {
                                if (fullMotionSampleCount.incrementAndGet() == 1) {
                                    Log.i(LOG_TAG, "Milestone: first-motion-sample=FULL")
                                }
                            }
                        },
                    )
                }
            }
        }

        Log.i(LOG_TAG, "Milestone: scene-installed")
        assertTrue("recognition pipeline did not enter CLASSIFYING", classificationStarted.await(3, TimeUnit.SECONDS))
        Log.i(LOG_TAG, "Milestone: classification-started")
        assertTrue(
            "CLASSIFYING was not presented by the live scene",
            classificationPresented.await(8, TimeUnit.SECONDS),
        )
        Log.i(LOG_TAG, "Milestone: classification-presented")

        val refreshRateHz = refreshRate.get()
        Log.i(LOG_TAG, "Milestone: full-screenshot-start")
        saveScreenshot(outputDir, "optical_fidelity_full.png")
        Log.i(LOG_TAG, "Milestone: full-screenshot-complete")
        activeProfile.set("FULL")
        assertTrue(
            "timed out while collecting FULL Window.FrameMetrics",
            fullFramesReady.await(FRAME_METRICS_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        activeProfile.set(null)
        val fullFrames = synchronized(fullFrameDurationsMs) { fullFrameDurationsMs.toList() }
        Log.i(LOG_TAG, "Milestone: full-frame-metrics-complete count=${fullFrames.size}")
        assertTrue("live FULL compositing did not report motion samples", fullMotionSampleCount.get() >= MIN_MOTION_SAMPLES)

        activityRule.scenario.onActivity {
            Log.i(LOG_TAG, "Milestone: lite-profile-change-start")
            activeProfile.set("LITE")
            policy.value = RecognitionMotionPolicy(degradationLevel = RecognitionDegradationLevel.D2)
        }
        Log.i(LOG_TAG, "Milestone: lite-profile-change-complete")
        Log.i(LOG_TAG, "Milestone: lite-screenshot-start")
        saveScreenshot(outputDir, "optical_fidelity_lite.png")
        Log.i(LOG_TAG, "Milestone: lite-screenshot-complete")
        assertTrue(
            "timed out while collecting LITE Window.FrameMetrics",
            liteFramesReady.await(FRAME_METRICS_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        activeProfile.set(null)
        val liteFrames = synchronized(liteFrameDurationsMs) { liteFrameDurationsMs.toList() }
        Log.i(LOG_TAG, "Milestone: lite-frame-metrics-complete count=${liteFrames.size}")
        assertTrue("live LITE compositing did not report motion samples", liteMotionSampleCount.get() >= MIN_MOTION_SAMPLES)
        assertTrue(
            "production scene left CLASSIFYING before LITE samples were captured",
            synchronized(presentedPhases) { presentedPhases.lastOrNull() == RecognitionPhase.CLASSIFYING },
        )
        assertEquals("suspended recognition must not report a result", 0, unexpectedResultCount.get())
        val presentedSnapshot = synchronized(presentedPhases) { presentedPhases.map { it.name }.distinct() }
        assertTrue("production scene never presented CLASSIFYING", "CLASSIFYING" in presentedSnapshot)

        val document = JSONObject()
            .put("contract_version", "recognition_processing_state_machine_v3_1")
            .put("measurement_method", "Window.FrameMetrics TOTAL_DURATION while production RecognitionProcessingScene is live")
            .put("phase", "CLASSIFYING")
            .put("fixture", "real_photo_with_controlled_ellipse_alpha_mask")
            .put("refresh_rate_hz", refreshRateHz)
            .put("budget_policy", "MEASURED_ONLY_NO_FROZEN_THRESHOLD")
            .put("profiles", JSONArray()
                .put(profileMetrics("FULL", fullFrames, fullMotionSampleCount.get(), refreshRateHz, fullDroppedFrames.get()))
                .put(profileMetrics("LITE", liteFrames, liteMotionSampleCount.get(), refreshRateHz, liteDroppedFrames.get())))
            .put("presented_phases", JSONArray(presentedSnapshot))
            .put("result_ready", false)
        File(outputDir, METRICS_FILENAME).writeText(document.toString(2) + "\n", Charsets.UTF_8)
        Log.i(LOG_TAG, "Milestone: metrics-written")
    }

    private fun saveScreenshot(directory: File, name: String) {
        // captureToImage() waits for Compose idleness; the production animation
        // is intentionally live for timing, so capture the rendered device
        // surface directly through UiAutomation instead.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("UiAutomation could not capture the live recognition surface: $name")
        try {
            require(bitmap.width >= 320 && bitmap.height >= 640) {
                "live recognition screenshot has implausible dimensions: ${bitmap.width}x${bitmap.height}"
            }
            File(directory, name).outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
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
        frameDurationsMs: List<Double>,
        motionSampleCount: Int,
        refreshRateHz: Double,
        droppedFrames: Int,
    ): JSONObject {
        assertTrue("$name profile returned too few Window.FrameMetrics samples", frameDurationsMs.size >= FRAME_METRICS_SAMPLES)
        val sorted = frameDurationsMs.sorted()
        val mean = sorted.average()
        val p95 = sorted[(kotlin.math.ceil(sorted.size * .95).toInt() - 1).coerceAtLeast(0)]
        val p99 = sorted[(kotlin.math.ceil(sorted.size * .99).toInt() - 1).coerceAtLeast(0)]
        val frameBudgetMs = 1_000.0 / refreshRateHz.coerceAtLeast(1.0)
        return JSONObject()
            .put("profile", name)
            .put("phase", "CLASSIFYING")
            .put("sample_count", sorted.size)
            .put("motion_sample_count", motionSampleCount)
            .put("refresh_rate_hz", refreshRateHz)
            .put("fps_estimate", if (mean > 0.0) 1_000.0 / mean else 0.0)
            .put("frame_metrics_ms", JSONObject()
                .put("mean", mean)
                .put("p95", p95)
                .put("p99", p99)
                .put("max", sorted.last())
                .put("frame_budget", frameBudgetMs)
                .put("frames_over_budget", sorted.count { it > frameBudgetMs })
                .put("frames_over_32ms", sorted.count { it > 32.0 })
                .put("dropped_frames_before_callback", droppedFrames))
            .put("frame_duration_samples_ms", JSONArray(sorted))
            .put("budget_policy", "MEASURED_ONLY_NO_FROZEN_THRESHOLD")
    }

    private companion object {
        const val LOG_TAG = "RecognitionOpticalFidelity"
        const val FRAME_METRICS_SAMPLES = 60
        const val FRAME_METRICS_TIMEOUT_SECONDS = 150L
        const val MIN_MOTION_SAMPLES = 20
        const val METRICS_FILENAME = "recognition_optical_fidelity_v3_1.json"
    }
}
