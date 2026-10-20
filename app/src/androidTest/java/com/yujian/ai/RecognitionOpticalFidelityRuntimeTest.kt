package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishDetection
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionProgress
import com.yujian.ai.ai.subject.FishSubjectPreviewEngine
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionImageTransform
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

/**
 * Measures live V3.1 compositing on API 28 in FULL and LITE profiles. Frame
 * intervals are evidence only: the product contract defines no frame budget.
 */
class RecognitionOpticalFidelityRuntimeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

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
        val detectorFixtureBox = requireNotNull(assessment.primary).box
        val generatedSubject = FishSubjectPreviewEngine(context).use { engine ->
            runBlocking { engine.generate(source, detectorFixtureBox) }
        }
        val subject = if (generatedSubject.status == SubjectStatus.READY && !generatedSubject.bitmapPath.isNullOrBlank()) {
            generatedSubject
        } else {
            createFocusMask(source, detectorFixtureBox, outputDir)
        }
        val maskProvenance = if (subject === generatedSubject && generatedSubject.status == SubjectStatus.READY) {
            "MLKIT_SUBJECT_FOREGROUND_MASK_ON_MANUAL_TEST_BBOX"
        } else {
            "DEBUG_ELLIPSE_FIXTURE; NOT_A_PRODUCTION_FISH_MASK"
        }
        val policy = mutableStateOf(
            RecognitionMotionPolicy(degradationLevel = RecognitionDegradationLevel.D0),
        )
        val classificationStarted = CountDownLatch(1)
        val classificationPresented = CountDownLatch(1)
        val liteMotionSamplesReady = CountDownLatch(1)
        val unexpectedResultCount = AtomicInteger(0)
        val fullMotionSampleCount = AtomicInteger(0)
        val liteMotionSampleCount = AtomicInteger(0)
        val presentedPhases = Collections.synchronizedList(mutableListOf<RecognitionPhase>())
        val presentedPhaseTimes = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val refreshRate = AtomicReference(60.0)
        val photoTransform = AtomicReference<RecognitionImageTransform?>()
        val photoBounds = AtomicReference<Pair<Rect, Rect>?>()

        activityRule.scenario.onActivity { activity ->
            Log.i(LOG_TAG, "Milestone: install-production-scene")
            refreshRate.set(activity.windowManager.defaultDisplay.refreshRate.toDouble())
            activity.setContent {
                YujianTheme {
                    val baseDensity = LocalDensity.current
                    // Exercise the production scene under the requested 1.3× Compose font scale.
                    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, 1.3f)) {
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
                            onVisualPhasePresented = { phase, presentedAtMs ->
                                presentedPhases += phase
                                presentedPhaseTimes += phase.name to presentedAtMs
                                Log.i(LOG_TAG, "Milestone: phase-presented=$phase")
                                if (phase == RecognitionPhase.CLASSIFYING) classificationPresented.countDown()
                            },
                            motionPolicyOverride = policy.value,
                            onMotionFrame = { sample ->
                                if (sample.qualityLevel.name == "LITE") {
                                    if (liteMotionSampleCount.incrementAndGet() == 1) {
                                        Log.i(LOG_TAG, "Milestone: first-motion-sample=LITE")
                                    }
                                    if (liteMotionSampleCount.get() >= MIN_MOTION_SAMPLES) liteMotionSamplesReady.countDown()
                                } else {
                                    if (fullMotionSampleCount.incrementAndGet() == 1) {
                                        Log.i(LOG_TAG, "Milestone: first-motion-sample=FULL")
                                    }
                                }
                            },
                            onFishFocusTransform = { photoTransform.set(it) },
                            onPhotoBoundsInWindow = { photo, viewport -> photoBounds.set(photo to viewport) },
                        )
                    }
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

        val transform = requireNotNull(photoTransform.get()) { "recognition photo transform was not reported" }
        val (photoWindowBounds, viewportWindowBounds) = requireNotNull(photoBounds.get()) {
            "recognition photo boundsInWindow were not reported"
        }
        val photoRect = transform.photoContentRect
        assertEquals("photo left follows the actual photo content rect", viewportWindowBounds.left + photoRect.left, photoWindowBounds.left, 2f)
        assertEquals("photo top follows the actual photo content rect", viewportWindowBounds.top + photoRect.top, photoWindowBounds.top, 2f)
        assertEquals("photo width follows the actual photo content rect", photoRect.width, photoWindowBounds.width, 2f)
        assertEquals("photo height follows the actual photo content rect", photoRect.height, photoWindowBounds.height, 2f)
        assertTrue("photo bounds stay inside the clipped viewport", rectContains(viewportWindowBounds, photoWindowBounds))

        val refreshRateHz = refreshRate.get()
        Log.i(LOG_TAG, "Milestone: full-screenshot-start")
        saveScreenshot(outputDir, "optical_fidelity_full.png")
        Log.i(LOG_TAG, "Milestone: full-screenshot-complete")
        val fullIntervals = collectFrameIntervals(FRAME_INTERVAL_SAMPLES)
        Log.i(LOG_TAG, "Milestone: full-intervals-complete count=${fullIntervals.size}")
        assertTrue("live FULL compositing did not report motion samples", fullMotionSampleCount.get() >= MIN_MOTION_SAMPLES)

        activityRule.scenario.onActivity {
            Log.i(LOG_TAG, "Milestone: lite-profile-change-start")
            policy.value = RecognitionMotionPolicy(degradationLevel = RecognitionDegradationLevel.D2)
        }
        Log.i(LOG_TAG, "Milestone: lite-profile-change-complete")
        assertTrue("LITE profile did not emit motion samples", liteMotionSamplesReady.await(8, TimeUnit.SECONDS))
        Log.i(LOG_TAG, "Milestone: lite-samples-ready count=${liteMotionSampleCount.get()}")
        assertTrue(
            "production scene left CLASSIFYING before LITE samples were captured",
            synchronized(presentedPhases) { presentedPhases.lastOrNull() == RecognitionPhase.CLASSIFYING },
        )
        Log.i(LOG_TAG, "Milestone: lite-screenshot-start")
        saveScreenshot(outputDir, "optical_fidelity_lite.png")
        Log.i(LOG_TAG, "Milestone: lite-screenshot-complete")
        val liteIntervals = collectFrameIntervals(FRAME_INTERVAL_SAMPLES)
        Log.i(LOG_TAG, "Milestone: lite-intervals-complete count=${liteIntervals.size}")
        assertTrue("live LITE compositing did not report motion samples", liteMotionSampleCount.get() >= MIN_MOTION_SAMPLES)
        assertEquals("suspended recognition must not report a result", 0, unexpectedResultCount.get())
        val presentedSnapshot = synchronized(presentedPhases) { presentedPhases.map { it.name }.distinct() }
        assertTrue("production scene never presented CLASSIFYING", "CLASSIFYING" in presentedSnapshot)

        val document = JSONObject()
            .put("task_id", "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2")
            .put("optical_config_version", "2.0.0")
            .put("contract_version", "recognition_processing_state_machine_v3_1")
            .put("measurement_method", "Choreographer.FrameCallback frame intervals while production RecognitionProcessingScene is live")
            .put("phase", "CLASSIFYING")
            .put("fixture", "registered_real_photo; controlled_detection_bbox; mask provenance reported separately")
            .put("api_level", Build.VERSION.SDK_INT)
            .put("device_model", Build.MODEL)
            .put("compose_font_scale", 1.3)
            .put("photo_mapping", JSONObject()
                .put("source_coordinate_space", "EXIF_NORMALIZED_PHOTO")
                .put("source_dimensions_px", JSONArray(listOf(source.width, source.height)))
                .put("content_scale_mode", transform.contentScaleMode.name)
                .put("viewport_bounds_in_window_px", rectJson(viewportWindowBounds))
                .put("photo_bounds_in_window_px", rectJson(photoWindowBounds))
                .put("photo_content_rect_in_canvas_px", JSONObject()
                    .put("left", photoRect.left).put("top", photoRect.top)
                    .put("right", photoRect.right).put("bottom", photoRect.bottom))
                .put("photo_bounds_match_transform", true)
                .put("coordinate_binding", "Canvas/photo overlay shares RecognitionImageTransform.photoContentRect; FIT bands excluded"))
            .put("fish_mask", JSONObject()
                .put("detector_output", "MANUAL_TEST_BBOX; DETECTOR_NOT_RUN_BY_THIS_REPLAY")
                .put("production_segmentation_engine", "ML Kit SubjectSegmentation foregroundConfidenceMask")
                .put("runtime_mask_provenance", maskProvenance)
                .put("runtime_subject_status", generatedSubject.status.name)
                .put("runtime_error_code", generatedSubject.errorCode)
                .put("runtime_mask_area_ratio", generatedSubject.maskAreaRatio)
                .put("fish_specific_semantic_accuracy", "NOT_VALIDATED"))
            .put("presented_phase_timestamps", JSONArray().apply {
                synchronized(presentedPhaseTimes) {
                    presentedPhaseTimes.forEach { (phase, uptimeMs) ->
                        put(JSONObject().put("phase", phase).put("uptime_ms", uptimeMs))
                    }
                }
            })
            .put("refresh_rate_hz", refreshRateHz)
            .put("budget_policy", "MEASURED_ONLY_NO_FROZEN_THRESHOLD")
            .put("profiles", JSONArray()
                .put(profileMetrics("FULL", fullIntervals, fullMotionSampleCount.get(), refreshRateHz))
                .put(profileMetrics("LITE", liteIntervals, liteMotionSampleCount.get(), refreshRateHz)))
            .put("presented_phases", JSONArray(presentedSnapshot))
            .put("result_ready", false)
        File(outputDir, METRICS_FILENAME).writeText(document.toString(2) + "\n", Charsets.UTF_8)
        Log.i(LOG_TAG, "Milestone: metrics-written")
    }

    private fun collectFrameIntervals(expectedCount: Int): List<Double> {
        val intervalsNanos = Collections.synchronizedList(mutableListOf<Long>())
        val completed = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
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

    private fun rectJson(rect: Rect): JSONObject = JSONObject()
        .put("left", rect.left)
        .put("top", rect.top)
        .put("right", rect.right)
        .put("bottom", rect.bottom)
        .put("width", rect.width)
        .put("height", rect.height)

    private fun rectContains(outer: Rect, inner: Rect, tolerance: Float = 2f): Boolean =
        inner.left >= outer.left - tolerance && inner.top >= outer.top - tolerance &&
            inner.right <= outer.right + tolerance && inner.bottom <= outer.bottom + tolerance

    private companion object {
        const val LOG_TAG = "RecognitionOpticalFidelity"
        const val FRAME_INTERVAL_SAMPLES = 120
        const val MIN_MOTION_SAMPLES = 20
        const val METRICS_FILENAME = "recognition_optical_fidelity_v3_1.json"
    }
}
