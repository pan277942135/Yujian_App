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
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
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
            photoSha256 = sourceSha256,
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
        val processingClockOverride = mutableStateOf<Long?>(3_200L)
        val geometryResolution = AtomicReference<RecognitionRevealGeometryResolution?>()
        val revealFinished = CountDownLatch(1)
        val resultEntries = AtomicInteger(0)
        val stageEvidence = File(evidence, "13-stage")
        val rr09Evidence = File(evidence, "rr09-v4")
        stageEvidence.mkdirs()
        rr09Evidence.mkdirs()
        val processingSamples = listOf(
            RecognitionSceneStage.PHOTO_STABLE to 0L,
            RecognitionSceneStage.ORB_WAKE to 1L,
            RecognitionSceneStage.ORB_RELEASE to 350L,
            RecognitionSceneStage.PERIMETER_ARRIVAL to 950L,
            RecognitionSceneStage.PATH_TO_FISH to 1_075L,
            RecognitionSceneStage.CONTOUR_A to 1_200L,
            RecognitionSceneStage.CONTOUR_B to 1_550L,
            RecognitionSceneStage.CONTOUR_C to 1_700L,
            RecognitionSceneStage.FISH_GOLD_PEAK to 1_950L,
        )
        val p0p7Samples = listOf(
            "P0" to 0L, "P1" to 350L, "P2" to 950L, "P3" to 1_200L,
            "P4" to 1_550L, "P5" to 2_050L, "P6" to 2_600L, "P7" to 2_800L,
        )
        val processingTimes = (processingSamples.map { it.second } + p0p7Samples.map { it.second })
            .distinct().sorted()
        val processingCaptureByTime = processingTimes.associateWith { timeMs ->
            File(stageEvidence, "processing_${timeMs.toString().padStart(4, '0')}ms.png")
        }
        val rr09Samples = listOf(
            "R0" to 0L, "R0.5" to 100L, "R1" to 200L, "R2" to 380L,
            "R3" to 550L, "R4" to 680L, "R5" to 850L,
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
                            subjectEngine.generate(selected, box, SESSION_ID, ATTEMPT_ID).also(actualSubject::set)
                        },
                        onFinished = {},
                        visualClockOverrideMs = processingClockOverride.value,
                        recognitionSessionId = SESSION_ID,
                        recognitionAttemptId = ATTEMPT_ID,
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
            val maskResult = requireNotNull(actualSubject.get())
            assertEquals("production mask must be ready for the trusted contour reveal", SubjectStatus.READY, maskResult.status)
            assertEquals("production mask quality must be trusted", com.yujian.ai.ai.subject.FishSubjectQuality.GOOD, maskResult.quality)
            assertTrue("production Mask geometry must map to the original PhotoSession", subjectGeometry.get()?.contour?.isNotEmpty() == true)
            composeRule.waitForIdle()
            composeRule.onRoot().captureToFile(evidence.resolve("RR09_Production_K01_processing.png"))

            processingTimes.forEach { timeMs ->
                processingClockOverride.value = timeMs
                composeRule.waitForIdle()
                composeRule.onRoot().captureToFile(requireNotNull(processingCaptureByTime[timeMs]))
            }
            val processingPhotoSnapshot = requireNotNull(processingPhoto.get())
            val geometryVersion = WaterLightGeometryVersion.of(processingPhotoSnapshot.sourceToViewport)
            val sceneIdentity = RecognitionSceneIdentity(
                SESSION_ID, ATTEMPT_ID, selectedImage.imageId, selectedImage.photoSha256,
                selectedImage.bitmap.generationId.toLong(),
            )
            val stageManifest = org.json.JSONArray().apply {
                processingSamples.forEach { (expectedStage, timeMs) ->
                    val frame = RecognitionSceneCoordinator.frameAt(sceneIdentity, timeMs, timeMs, geometryVersion)
                    assertEquals("runtime matrix stage at $timeMs ms", expectedStage, frame.stage)
                    put(JSONObject()
                        .put("stage_number", expectedStage.ordinal + 1)
                        .put("stage_id", frame.stage.name)
                        .put("stage_name", frame.stage.displayName)
                        .put("scene_time_ms", timeMs)
                        .put("geometry_version", geometryVersion)
                        .put("file", requireNotNull(processingCaptureByTime[timeMs]).name)
                    )
                }
            }
            val p0p7Manifest = org.json.JSONArray().apply {
                p0p7Samples.forEach { (keyframe, timeMs) ->
                    put(JSONObject().put("keyframe", keyframe).put("time_ms", timeMs)
                        .put("file", requireNotNull(processingCaptureByTime[timeMs]).name))
                }
            }

            val handoff = RecognitionRevealHandoffFactory.create(
                recognitionSessionId = SESSION_ID,
                recognitionAttemptId = ATTEMPT_ID,
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

            val rr09Manifest = org.json.JSONArray().apply {
                rr09Samples.forEach { (keyframe, timeMs) ->
                    elapsedOverride.value = timeMs
                    composeRule.waitForIdle()
                    val file = "${keyframe.replace('.', '_')}_${timeMs.toString().padStart(3, '0')}ms.png"
                    composeRule.onRoot().captureToFile(File(rr09Evidence, file))
                    put(JSONObject().put("keyframe", keyframe).put("rr09_elapsed_ms", timeMs).put("file", file))
                }
            }
            val fullStageManifest = org.json.JSONArray().apply {
                for (index in 0 until stageManifest.length()) put(stageManifest.getJSONObject(index))
                listOf(
                    Triple(RecognitionSceneStage.ORB_RETURN, 2_800L, 0L),
                    Triple(RecognitionSceneStage.ORB_TO_NAME, 3_180L, 380L),
                    Triple(RecognitionSceneStage.SPECIES_NAME_REVEAL, 3_350L, 550L),
                    Triple(RecognitionSceneStage.RESIDUE_SETTLE, 3_480L, 680L),
                ).forEach { (expectedStage, globalMs, rr09Ms) ->
                    val frame = RecognitionSceneCoordinator.frameAt(sceneIdentity, globalMs, globalMs, geometryVersion)
                    assertEquals("RR09 projection at $globalMs ms", expectedStage, frame.stage)
                    val revealFrame = rr09Manifest.getJSONObject(
                        (0 until rr09Manifest.length()).first { rr09Manifest.getJSONObject(it).getLong("rr09_elapsed_ms") == rr09Ms },
                    )
                    put(JSONObject()
                        .put("stage_number", expectedStage.ordinal + 1)
                        .put("stage_id", frame.stage.name)
                        .put("stage_name", frame.stage.displayName)
                        .put("scene_time_ms", globalMs)
                        .put("rr09_elapsed_ms", rr09Ms)
                        .put("geometry_version", geometryVersion)
                        .put("file", "rr09-v4/${revealFrame.getString("file")}")
                    )
                }
            }
            composeRule.onNodeWithText(prediction.top1.speciesName).performScrollTo()
            val species = catalog.firstOrNull { it.id == prediction.top1.speciesKey }
            species?.scientificName?.takeIf(String::isNotBlank)?.let { latinName ->
                composeRule.onNodeWithText(latinName).performScrollTo()
            }

            val resolved = geometryResolution.get()
            val productionGeometryPassed = handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL &&
                resolved?.geometry != null
            assertEquals("trusted Mask + HIGH result must enter RR09 V4 full reveal",
                RecognitionRevealEligibility.FULL_REVEAL, handoff.eligibility)
            assertTrue("real Result geometry must resolve from the Processing handoff", resolved?.geometry != null)
            val materialProfiles = JSONObject().apply {
                WaterLightHost.entries.forEach { host ->
                    val profile = WaterLightMaterialProfile.forHost(host)
                    put(host.name, JSONObject()
                        .put("primary_color", "#${profile.primaryColor.toString(16).takeLast(6)}")
                        .put("secondary_color", "#${profile.secondaryColor.toString(16).takeLast(6)}")
                        .put("highlight_color", "#${profile.highlightColor.toString(16).takeLast(6)}")
                        .put("core_alpha", profile.coreAlpha).put("glow_alpha", profile.glowAlpha)
                        .put("core_width", profile.coreWidth).put("glow_width", profile.glowWidth)
                        .put("blur_radius", profile.blurRadius).put("feather", profile.feather)
                        .put("speed", profile.speed).put("opacity", profile.opacity)
                        .put("coverage", profile.coverage).put("blend_mode", profile.blendMode.name)
                        .put("motion_scale", profile.motionScale).put("material_scale", profile.materialScale))
                }
            }
            val runtimeMatrix = JSONObject()
                .put("status", "PASS")
                .put("session_id", SESSION_ID)
                .put("attempt_id", ATTEMPT_ID)
                .put("image_id", selectedImage.imageId)
                .put("photo_sha256", sourceSha256)
                .put("source_asset", SOURCE_ASSET)
                .put("geometry_version", geometryVersion)
                .put("mask_status", maskResult.status.name)
                .put("mask_quality", maskResult.quality?.name)
                .put("mask_area_ratio", maskResult.maskAreaRatio)
                .put("contour_segments", subjectGeometry.get()?.contour?.size ?: 0)
                .put("species_id", prediction.top1.speciesKey)
                .put("species_name", prediction.top1.speciesName)
                .put("species_confidence", prediction.top1.confidence.toDouble())
                .put("result_is_high", !prediction.lowConfidence)
                .put("material_parameter_version", "B01_CANDIDATE_NOT_VISUALLY_FROZEN")
                .put("material_profiles", materialProfiles)
                .put("stages", fullStageManifest)
                .put("p0_p7", p0p7Manifest)
                .put("rr09_r0_r5", rr09Manifest)
            evidence.resolve("AI_Orb_WaterLight_Runtime_Matrix.json")
                .writeText(runtimeMatrix.toString(2) + "\n")
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
                .put("processing_phase_mode", "PRODUCTION_RECOGNITION_STATE_WITH_SHARED_SCENE_FRAME_SAMPLES")
                .put("result_entry_mode", "TEST_CONTROLLED_AFTER_PRODUCTION_RESULT_READY; automatic handoff is covered by RecognitionFrozenFlowEmulatorTest")
                .put("flow_start_elapsed_realtime_ms", flowStartElapsedRealtimeMs)
                .put("result_entry_elapsed_realtime_ms", resultEntryElapsedRealtimeMs)
                .put("live_reveal_finished_elapsed_realtime_ms", liveRevealFinishedElapsedRealtimeMs)
                .put("api28_gate_recording", "RR09_Recognition_Flow_Recording.mp4")
                .put("frame_clock", "13-stage Processing and P0-P7 use the shared RecognitionSceneFrame projection; R0-R5 use the RR09 V4 production Result renderer; live monotonic window is in the API28 gate recording")
                .put("frames", rr09Manifest)
                .put("runtime_matrix", "AI_Orb_WaterLight_Runtime_Matrix.json")
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
        const val SESSION_ID = "rr09-production-night-session"
        const val ATTEMPT_ID = "rr09-production-night-attempt"
        const val SOURCE_ASSET = "detector_case_b_night_flash.jpg"
        const val SOURCE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
    }
}
