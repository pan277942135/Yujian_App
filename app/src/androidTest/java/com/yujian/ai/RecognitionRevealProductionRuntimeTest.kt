package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.subject.FishSubjectPreviewEngine
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.knowledge.FishKnowledgeRepository
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolution
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoffFactory
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.RecognitionSaveDestination
import com.yujian.ai.ui.screens.extractContour
import com.yujian.ai.ui.theme.YujianTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/** Runtime proof using the registered night-catch photo, production detector/classifier and ML Kit mask. */
@RunWith(AndroidJUnit4::class)
class RecognitionRevealProductionRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun realNightCatchHandoffDrivesTheNativeResultReveal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val evidence = File(targetContext.cacheDir, "rr09-gold-transfer-production").apply {
            deleteRecursively()
            mkdirs()
        }
        val sourceBytes = testContext.assets.open(SOURCE_ASSET).use { it.readBytes() }
        val sourceSha256 = sourceBytes.sha256()
        assertEquals(SOURCE_SHA256, sourceSha256)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size))
        val selectedImage = SelectedImage(
            filePath = SOURCE_ASSET,
            bitmap = bitmap,
            source = "androidTest_registered_photo",
            imageId = "rr09-production-night-catch-$sourceSha256",
        )
        val subjectEngine = FishSubjectPreviewEngine(targetContext)
        val actualResult = AtomicReference<ProductionRecognitionResult?>()
        val actualSubject = AtomicReference<FishSubjectResult?>()
        val subjectGeometry = AtomicReference<RecognitionSubjectGeometrySnapshot?>()
        val subjectCallbackComplete = AtomicInteger(0)
        val processingPhoto = AtomicReference<com.yujian.ai.ui.recognition.reveal.RecognitionProcessingPhotoSnapshot?>()
        val handoffState = mutableStateOf<RecognitionRevealHandoff?>(null)
        val resultState = mutableStateOf<ProductionRecognitionResult?>(null)
        val showResult = mutableStateOf(false)
        val elapsedOverride = mutableStateOf<Long?>(null)
        val geometryResolution = AtomicReference<RecognitionRevealGeometryResolution?>()
        val revealFinished = CountDownLatch(1)
        val resultEntries = AtomicInteger(0)
        val outputFiles = listOf(
            "RR09_Production_K01_processing.png",
            "RR09_Production_K02_120ms.png",
            "RR09_Production_K03_290ms.png",
            "RR09_Production_K04_465ms.png",
            "RR09_Production_K05_610ms.png",
            "RR09_Production_K06_850ms.png",
        )

        val catalog = runCatching { FishKnowledgeRepository().listSpecies() }.getOrDefault(emptyList())
        val flowStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        composeRule.setContent {
            YujianTheme {
                if (!showResult.value) {
                    RecognitionProcessingScene(
                        image = selectedImage,
                        onBack = {},
                        recognize = { onProgress ->
                            val pipeline = FishRecognitionPipeline(targetContext)
                            try {
                                pipeline.recognize(selectedImage.bitmap, onProgress).also(actualResult::set)
                            } finally {
                                pipeline.close()
                            }
                        },
                        generateSubject = { selected, box ->
                            subjectEngine.generate(selected.bitmap, box).also(actualSubject::set)
                        },
                        onFinished = {},
                        phaseOverride = RecognitionPhase.CLASSIFYING,
                        visualClockOverrideMs = 3_200L,
                        onVisualPhasePresented = { _, _ -> },
                        onSubjectGeometry = { subject, geometry ->
                            actualSubject.set(subject)
                            subjectGeometry.set(geometry)
                            subjectCallbackComplete.incrementAndGet()
                        },
                        onProcessingPhotoSnapshot = processingPhoto::set,
                    )
                } else {
                    val completed = resultState.value
                    if (completed?.prediction != null) {
                        RecognitionResultScreen(
                            image = selectedImage,
                            prediction = completed.prediction,
                            productionResult = completed,
                            subjectResult = actualSubject.get() ?: FishSubjectResult(SubjectStatus.IDLE),
                            availableSpecies = catalog,
                            revealHandoff = handoffState.value,
                            revealElapsedOverrideMs = elapsedOverride.value,
                            onRevealFinished = { revealFinished.countDown() },
                            revealGeometryResolutionObserver = geometryResolution::set,
                            onBack = {},
                            onRetry = {},
                            onSave = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        try {
            composeRule.waitUntil(timeoutMillis = 45_000L) {
                actualResult.get() != null && actualSubject.get() != null &&
                    subjectCallbackComplete.get() > 0 && processingPhoto.get() != null
            }
            val result = requireNotNull(actualResult.get())
            val prediction = requireNotNull(result.prediction)
            assertTrue("registered night catch did not reach production RESULT_READY: ${result.status}", result.ready)
            assertEquals("yellow_catfish", prediction.top1.speciesKey)
            assertTrue("registered night catch must qualify as HIGH", prediction.top1.confidence >= .55f)
            assertTrue("registered night catch must have a clear production margin", !prediction.lowConfidence)
            composeRule.waitForIdle()
            composeRule.onRoot().captureToFile(evidence.resolve(outputFiles[0]))

            val handoff = RecognitionRevealHandoffFactory.create(
                recognitionSessionId = "rr09-production-night-session",
                recognitionAttemptId = "rr09-production-night-attempt",
                image = selectedImage,
                result = result,
                subject = subjectGeometry.get(),
                processingPhoto = processingPhoto.get(),
                highConfidenceResult = prediction.lowConfidence.not(),
                handoffTimestampUptimeMs = SystemClock.uptimeMillis(),
            )
            Snapshot.withMutableSnapshot {
                handoffState.value = handoff
                resultState.value = result
                showResult.value = true
            }
            resultEntries.incrementAndGet()
            val resultEntryElapsedRealtimeMs = SystemClock.elapsedRealtime()
            assertEquals("the test must enter the real Result data once", 1, resultEntries.get())
            composeRule.waitUntil(timeoutMillis = 15_000L) {
                val value = geometryResolution.get()
                value != null && (value.geometry != null || value.failureReason?.endsWith("_PENDING") == false)
            }
            composeRule.onNodeWithTag("recognition-result-hero").assertIsDisplayed()
            assertEquals(selectedImage.imageId, handoff.imageId)

            // The API28 gate owns the single screenrecord process. This instrumentation test
            // records its own real monotonic flow window for the gate report to identify.
            assertTrue("live reveal did not complete", revealFinished.await(8, TimeUnit.SECONDS))
            val liveRevealFinishedElapsedRealtimeMs = SystemClock.elapsedRealtime()

            val actualFrames = listOf(120L, 290L, 465L, 610L, 850L)
            actualFrames.forEachIndexed { index, timeMs ->
                elapsedOverride.value = timeMs
                composeRule.waitForIdle()
                composeRule.onRoot().captureToFile(evidence.resolve(outputFiles[index + 1]))
            }
            composeRule.onNodeWithText(prediction.top1.speciesName).performScrollTo()
            val species = catalog.firstOrNull { it.id == prediction.top1.speciesKey }
            species?.scientificName?.takeIf(String::isNotBlank)?.let { latinName ->
                composeRule.onNodeWithText(latinName).performScrollTo()
            }

            val maskResult = actualSubject.get()
            val resolved = geometryResolution.get()
            val productionGeometryPassed = handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL &&
                resolved?.geometry != null
            val report = JSONObject()
                .put("status", if (productionGeometryPassed) "FULL_REVEAL_PRODUCTION_DATA_PASS" else "SAFE_FALLBACK")
                .put("source_asset", SOURCE_ASSET)
                .put("source_sha256", sourceSha256)
                .put("source_size_px", "${bitmap.width}x${bitmap.height}")
                .put("source_orientation_degrees_applied", selectedImage.sourceOrientationDegrees)
                .put("model_version", prediction.modelVersion)
                .put("model_sha256", prediction.modelSha256)
                .put("result_ready", result.ready)
                .put("species_id", prediction.top1.speciesKey)
                .put("species_name", prediction.top1.speciesName)
                .put("species_confidence", prediction.top1.confidence.toDouble())
                .put("result_is_high", !prediction.lowConfidence)
                .put("scientific_name", species?.scientificName)
                .put("subject_status", maskResult?.status?.name)
                .put("subject_error", maskResult?.errorCode)
                .put("mask_quality", maskResult?.quality?.name)
                .put("mask_area_ratio", maskResult?.maskAreaRatio)
                .put("mask_size", maskResult?.maskSize)
                .put("mask_expected_size", maskResult?.expectedMaskSize)
                .put("contour_segments", subjectGeometry.get()?.contour?.size ?: 0)
                .put("geometry_trust", handoff.geometryTrustState.name)
                .put("eligibility", handoff.eligibility.name)
                .put("photo_source_identity", handoff.photoSourceIdentity)
                .put("processing_photo_bounds_px", org.json.JSONArray(listOf(
                    processingPhoto.get()?.viewportBoundsInWindow?.left,
                    processingPhoto.get()?.viewportBoundsInWindow?.top,
                    processingPhoto.get()?.viewportBoundsInWindow?.right,
                    processingPhoto.get()?.viewportBoundsInWindow?.bottom,
                )))
                .put("result_geometry_status", resolved?.failureReason ?: "RESOLVED")
                .put("test_result_entries", resultEntries.get())
                .put("source_mode", "PRODUCTION_MODEL_AND_ML_KIT_MASK")
                .put("processing_phase_mode", "CLASSIFYING_TEST_HOLD_FOR_K01_CAPTURE")
                .put("result_entry_mode", "TEST_CONTROLLED_AFTER_PRODUCTION_RESULT_READY; automatic handoff is covered by RecognitionFrozenFlowEmulatorTest")
                .put("flow_start_elapsed_realtime_ms", flowStartElapsedRealtimeMs)
                .put("result_entry_elapsed_realtime_ms", resultEntryElapsedRealtimeMs)
                .put("live_reveal_finished_elapsed_realtime_ms", liveRevealFinishedElapsedRealtimeMs)
                .put("api28_gate_recording", "RR09_Recognition_Flow_Recording.mp4")
                .put("frame_clock", "K02-K06 deterministic test clock captures the real production Result data; live monotonic window is in the API28 gate recording")
                .put("frames", org.json.JSONArray(outputFiles))
                .put("recording", "RR09_Production_Recognition_Flow_Recording.mp4")
            evidence.resolve("RR09_Production_Qualification_Report.json")
                .writeText(report.toString(2) + "\n")

            composeRule.onNodeWithText("修改鱼种").assertHasClickAction()
            species?.scientificName?.takeIf(String::isNotBlank)?.let { latinName ->
                composeRule.onNodeWithText(latinName).assertIsDisplayed()
            }
            assertNotNull("production photo-source identity is required", handoff.photoSourceIdentity)
            if (!bitmap.isRecycled) bitmap.recycle()
        } finally {
            subjectEngine.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.captureToFile(file: File) {
        val bitmap = captureToImage().asAndroidBitmap()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ASSET = "detector_case_b_night_flash.jpg"
        const val SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
