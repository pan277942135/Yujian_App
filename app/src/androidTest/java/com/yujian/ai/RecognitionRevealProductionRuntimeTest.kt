package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.extractContour
import com.yujian.ai.ui.theme.YujianTheme
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.Collections
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealSessionStore
import kotlinx.coroutines.runBlocking

/** Runtime proof using the registered night-catch photo, production detector/classifier and ML Kit mask. */
@RunWith(AndroidJUnit4::class)
class RecognitionRevealProductionRuntimeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

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
        val resultSceneFrame = mutableStateOf<com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame?>(null)
        val geometryResolution = AtomicReference<RecognitionRevealGeometryResolution?>()
        val resultEntries = AtomicInteger(0)
        val automaticHandoff = AtomicReference<RecognitionRevealHandoff?>()
        val handoffFailure = AtomicReference<String?>()
        val processingCaptureFailure = AtomicReference<String?>()
        val processingCaptureStarted = AtomicBoolean(false)
        val rr09Evidence = File(evidence, "shared-stage-reveal").apply { mkdirs() }
        val processingCaptureReady = CountDownLatch(1)
        val screenshotExecutor = Executors.newSingleThreadExecutor()
        val processingSamples = RecognitionSceneStage.entries
        val p0p7Samples = listOf(
            "P0" to RecognitionSceneStage.PHOTO_STABLE,
            "P1" to RecognitionSceneStage.ORB_WAKE,
            "P2" to RecognitionSceneStage.ORB_RELEASE,
            "P3" to RecognitionSceneStage.PERIMETER_ARRIVAL,
            "P4" to RecognitionSceneStage.PATH_TO_FISH,
            "P5" to RecognitionSceneStage.CONTOUR_A,
            "P6" to RecognitionSceneStage.CONTOUR_B,
            "P7" to RecognitionSceneStage.CONTOUR_C,
        )
        val rr09Samples = listOf(
            "R0" to (RecognitionSceneStage.ORB_RETURN to 0f),
            "R1" to (RecognitionSceneStage.ORB_RETURN to .5f),
            "R2" to (RecognitionSceneStage.ORB_TO_NAME to .5f),
            "R3" to (RecognitionSceneStage.SPECIES_NAME_REVEAL to .5f),
            "R4" to (RecognitionSceneStage.RESIDUE_SETTLE to .5f),
            "R5" to (RecognitionSceneStage.RESIDUE_SETTLE to 1f),
        )
        val sceneIdentity = RecognitionSceneIdentity(
            SESSION_ID, ATTEMPT_ID, selectedImage.imageId, selectedImage.photoSha256,
            selectedImage.bitmap.generationId.toLong().coerceAtLeast(0L),
        )
        val revealKey = RecognitionRevealKey(SESSION_ID, ATTEMPT_ID)
        val revealSessionStore = RecognitionRevealSessionStore().apply {
            beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
        }
        val sceneController = SceneMotionCoordinator().apply {
            beginNewSession(sceneIdentity, debugBuild = false, nowMs = SystemClock.uptimeMillis())
        }
        val realTimeStages = Collections.synchronizedSet(EnumSet.of(sceneController.currentStage))
        val stageMonitor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({
                runCatching {
                    activityRule.scenario.onActivity { realTimeStages.add(sceneController.currentStage) }
                }
            }, 0L, 30L, TimeUnit.MILLISECONDS)
        }
        fun sceneFrame(stage: RecognitionSceneStage, progress: Float, geometryVersion: Long = 0L) =
            RecognitionSceneCoordinator.frameForStage(
                identity = sceneIdentity,
                sceneElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
                opticalReferenceElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
                geometryVersion = geometryVersion,
                stage = stage,
                stageElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
                stageProgress = progress,
                stageEventComplete = stage == RecognitionSceneStage.RESIDUE_SETTLE && progress >= 1f,
            )

        val catalog = runCatching { FishKnowledgeRepository().listSpecies() }.getOrDefault(emptyList())
        val flowStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        activityRule.scenario.onActivity { activity ->
            activity.setContent {
                YujianTheme {
                    if (!showResult.value) {
                        RecognitionProcessingScene(
                            image = selectedImage,
                            onBack = {},
                            recognize = { onProgress ->
                                val pipeline = FishRecognitionPipeline(targetContext)
                                try {
                                    pipeline.recognize(selectedImage.bitmap, onProgress).also { result ->
                                        actualResult.set(result)
                                        val decision = revealSessionStore.recordClassifierResult(
                                            key = revealKey,
                                            result = result,
                                            inferenceAsset = null,
                                            highConfidenceResult = result.prediction?.lowConfidence == false,
                                        )
                                        checkNotNull(decision) { "production classifier result lost its Session/Attempt/PhotoSession identity" }
                                    }
                                } finally {
                                    pipeline.close()
                                }
                            },
                            generateSubject = { selected, box ->
                                subjectEngine.generate(selected, box, SESSION_ID, ATTEMPT_ID).also(actualSubject::set)
                            },
                            onFinished = { completed ->
                                resultState.value = completed
                                val handoff = revealSessionStore.completeAttempt(
                                    key = revealKey,
                                    result = completed,
                                    highConfidenceResult = completed.prediction?.lowConfidence == false,
                                    inferenceAsset = null,
                                    handoffTimestampUptimeMs = SystemClock.uptimeMillis(),
                                )
                                if (handoff == null) {
                                    handoffFailure.set("production onFinished reached without a ready SessionStore classifier × SlimSAM gate")
                                } else {
                                    handoffState.value = handoff
                                    automaticHandoff.set(handoff)
                                    resultEntries.incrementAndGet()
                                    showResult.value = true
                                }
                            },
                            recognitionSessionId = SESSION_ID,
                            recognitionAttemptId = ATTEMPT_ID,
                            diagnosticPlaybackController = sceneController,
                            diagnosticDisplayMode = com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode.NORMAL_PROGRESS,
                            onVisualPhasePresented = { _, _ -> },
                            onSubjectGeometry = { subject, geometry ->
                                actualSubject.set(subject)
                                subjectGeometry.set(geometry)
                                subjectCallbackComplete.incrementAndGet()
                                revealSessionStore.updateSubject(revealKey, subject, geometry)
                                if (subject.status == SubjectStatus.READY && geometry?.contour?.isNotEmpty() == true &&
                                    processingCaptureStarted.compareAndSet(false, true)
                                ) {
                                    screenshotExecutor.execute {
                                        runCatching { saveScreenshot(evidence.resolve("Recognition_Processing_Photo.png")) }
                                            .onFailure { processingCaptureFailure.set(it.message ?: it.javaClass.simpleName) }
                                        processingCaptureReady.countDown()
                                    }
                                }
                            },
                            onProcessingPhotoSnapshot = { photo ->
                                processingPhoto.set(photo)
                                revealSessionStore.updateProcessingPhoto(revealKey, photo)
                            },
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
                                sceneFrameOverrideForTest = resultSceneFrame.value,
                                diagnosticPlaybackController = sceneController,
                                revealAlreadyPlayed = false,
                                onClaimReveal = revealSessionStore::claimPlayback,
                                revealGeometryResolutionObserver = geometryResolution::set,
                                onBack = {},
                                onRetry = {},
                                onSave = { _, _, _ -> },
                            )
                        }
                    }
                }
            }
        }

        try {
            assertTrue(
                "production Processing did not automatically complete its SessionStore gate: ${handoffFailure.get()}",
                waitUntil(210_000L) { automaticHandoff.get() != null || handoffFailure.get() != null },
            )
            assertNull("automatic Production → Result handoff was rejected", handoffFailure.get())
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
            assertTrue("production Processing screenshot was not captured", processingCaptureReady.await(15, TimeUnit.SECONDS))
            assertNull("production Processing screenshot failed: ${processingCaptureFailure.get()}", processingCaptureFailure.get())
            val processingPhotoSnapshot = requireNotNull(processingPhoto.get())
            val geometryVersion = WaterLightGeometryVersion.of(processingPhotoSnapshot.sourceToViewport)
            val handoff = requireNotNull(automaticHandoff.get())
            val resultEntryElapsedRealtimeMs = SystemClock.elapsedRealtime()
            assertEquals("production onFinished must enter Result exactly once", 1, resultEntries.get())
            assertTrue("result screen was not entered by the production callback", waitUntil(20_000L) { showResult.value })
            assertTrue("Result geometry did not resolve from the Processing snapshot", waitUntil(20_000L) {
                val value = geometryResolution.get()
                value != null && (value.geometry != null || value.failureReason?.endsWith("_PENDING") == false)
            })
            assertTrue(
                "production AUTO playback did not visit all 13 stages; observed=${synchronized(realTimeStages) { realTimeStages.map { it.name } }}",
                waitUntil(15_000L) { synchronized(realTimeStages) { realTimeStages.containsAll(RecognitionSceneStage.entries) } },
            )
            saveScreenshot(evidence.resolve("Recognition_Result_Automatic_Handoff.png"))
            assertEquals(selectedImage.imageId, handoff.imageId)
            assertEquals(SESSION_ID, handoff.key.recognitionSessionId)
            assertEquals(ATTEMPT_ID, handoff.key.recognitionAttemptId)
            assertEquals(selectedImage.photoSha256, handoff.photoSourceIdentity.substringAfter("|sha256="))
            assertEquals("Processing photo identity must cross the handoff unchanged", processingPhotoSnapshot.imageId, handoff.imageId)
            assertEquals("production SessionStore must retain the same geometry snapshot", processingPhotoSnapshot,
                revealSessionStore.snapshot?.processingPhoto)
            val trustedGeometry = requireNotNull(subjectGeometry.get())
            assertEquals(SESSION_ID, trustedGeometry.recognitionSessionId)
            assertEquals(ATTEMPT_ID, trustedGeometry.recognitionAttemptId)
            assertEquals(selectedImage.imageId, trustedGeometry.imageId)
            assertEquals("the real mask generation must survive the handoff", trustedGeometry.maskVersion, handoff.maskVersion)

            val stageManifest = org.json.JSONArray()
            val captureByStageAndProgress = mutableMapOf<Pair<RecognitionSceneStage, Int>, String>()
            processingSamples.forEach { expectedStage ->
                val captures = org.json.JSONArray()
                listOf(0f, .5f, 1f).forEach { progress ->
                    val frame = sceneFrame(expectedStage, progress, geometryVersion)
                    activityRule.scenario.onActivity {
                        Snapshot.withMutableSnapshot { resultSceneFrame.value = frame }
                    }
                    SystemClock.sleep(180L)
                    val file = String.format(Locale.US, "%02d_%s_p%03d.png",
                        expectedStage.ordinal + 1,
                        expectedStage.name.lowercase(),
                        (progress * 100).toInt(),
                    )
                    saveScreenshot(File(rr09Evidence, file))
                    captureByStageAndProgress[expectedStage to (progress * 100).toInt()] = file
                    captures.put(JSONObject()
                        .put("stage_progress", progress)
                        .put("capture_kind", "DETERMINISTIC_SCENE_FRAME_TEST_PROJECTION")
                        .put("file", file))
                }
                stageManifest.put(JSONObject()
                    .put("stage_number", expectedStage.ordinal + 1)
                    .put("stage_id", expectedStage.name)
                    .put("stage_name", expectedStage.displayName)
                    .put("geometry_version", geometryVersion)
                    .put("captures", captures))
            }
            val p0p7Manifest = org.json.JSONArray().apply {
                p0p7Samples.forEachIndexed { index, (keyframe, stage) ->
                    val progress = if (index == 0) 0 else 100
                    val file = requireNotNull(captureByStageAndProgress[stage to progress])
                    put(JSONObject().put("keyframe", keyframe).put("stage", stage.name)
                        .put("stage_progress", progress / 100f).put("capture_kind", "DETERMINISTIC_SCENE_FRAME_TEST_PROJECTION")
                        .put("file", file))
                }
            }
            val rr09Manifest = org.json.JSONArray().apply {
                rr09Samples.forEach { (keyframe, stageSample) ->
                    val (stage, progress) = stageSample
                    val progressPercent = (progress * 100).toInt()
                    val file = requireNotNull(captureByStageAndProgress[stage to progressPercent])
                    put(JSONObject().put("keyframe", keyframe).put("stage", stage.name)
                        .put("stage_progress", progress).put("capture_kind", "DETERMINISTIC_SCENE_FRAME_TEST_PROJECTION")
                        .put("file", file))
                }
            }
            val fullStageManifest = org.json.JSONArray().apply {
                for (index in 0 until stageManifest.length()) put(stageManifest.getJSONObject(index))
                rr09Samples.forEach { (keyframe, stageSample) ->
                    val (expectedStage, progress) = stageSample
                    val frame = sceneFrame(expectedStage, progress, geometryVersion)
                    assertEquals("shared semantic reveal stage", expectedStage, frame.stage)
                    val capture = rr09Manifest.getJSONObject(
                        (0 until rr09Manifest.length()).first { rr09Manifest.getJSONObject(it).getString("keyframe") == keyframe },
                    )
                    put(JSONObject()
                        .put("stage_number", expectedStage.ordinal + 1)
                        .put("stage_id", frame.stage.name)
                        .put("stage_name", frame.stage.displayName)
                        .put("stage_progress", progress)
                        .put("geometry_version", geometryVersion)
                        .put("file", "shared-stage-reveal/${capture.getString("file")}")
                    )
                }
            }
            val semanticFramesSampledElapsedRealtimeMs = SystemClock.elapsedRealtime()
            val species = catalog.firstOrNull { it.id == prediction.top1.speciesKey }

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
                .put("real_time_controller_mode", sceneController.mode.name)
                .put("real_time_controller_final_stage", sceneController.currentStage.name)
                .put("real_time_controller_shared_across_screens", true)
                .put("real_time_stages_observed", org.json.JSONArray(synchronized(realTimeStages) { realTimeStages.map { it.name }.sorted() }))
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
                .put("shared_controller_identity", "${sceneIdentity.sessionId}|${sceneIdentity.attemptId}|${sceneIdentity.imageId}|${sceneIdentity.photoSha256}")
                .put("automatic_handoff_gate", revealSessionStore.snapshot?.revealDecision?.name)
                .put("automatic_handoff_observed", automaticHandoff.get() != null)
                .put("source_mode", "PRODUCTION_MODEL_AND_ML_KIT_MASK")
                .put("processing_phase_mode", "PRODUCTION_RECOGNITION_STATE_WITH_SHARED_AUTO_CONTROLLER")
                .put("result_entry_mode", "PRODUCTION_ON_FINISHED_SESSIONSTORE_COMPLETE_ATTEMPT_SHARED_CONTROLLER")
                .put("flow_start_elapsed_realtime_ms", flowStartElapsedRealtimeMs)
                .put("result_entry_elapsed_realtime_ms", resultEntryElapsedRealtimeMs)
                .put("semantic_frames_sampled_elapsed_realtime_ms", semanticFramesSampledElapsedRealtimeMs)
                .put("runtime_visual_acceptance", "API36_NATIVE_SCREEN_CAPTURED; deterministic stage projections are marked separately")
                .put("real_time_playback", "CAPTURED_BY_NATIVE_SCREENRECORD; production AUTO controller and production handoff")
                .put("frame_clock", "One RecognitionSceneAnimationController shared by Processing and Result; deterministic screenshot override is used only for stage contact-sheet samples")
                .put("frames", rr09Manifest)
                .put("runtime_matrix", "AI_Orb_WaterLight_Runtime_Matrix.json")
                .put("recording", "HOSTED_API36_AVD_SCREENRECORD_CAPTURED_BY_RUNTIME_SCRIPT")
                .put("deterministic_capture_count", processingSamples.size * 3)
            evidence.resolve("RR09_Production_Qualification_Report.json")
                .writeText(report.toString(2) + "\n")

            assertNotNull("production photo-source identity is required", handoff.photoSourceIdentity)
            assertTrue("Result page must display captured native pixels", evidence.resolve("Recognition_Result_Automatic_Handoff.png").length() > 1024L)
            assertEquals("stage capture manifest must cover all semantic stages", 13, stageManifest.length())
            if (!bitmap.isRecycled) bitmap.recycle()
        } finally {
            stageMonitor.shutdownNow()
            screenshotExecutor.shutdownNow()
            subjectEngine.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100L)
        }
        return condition()
    }

    private fun saveScreenshot(file: File) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("UiAutomation could not capture the native recognition surface: ${file.name}")
        try {
            require(bitmap.width >= 320 && bitmap.height >= 640) {
                "native screenshot has implausible dimensions: ${bitmap.width}x${bitmap.height}"
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
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
