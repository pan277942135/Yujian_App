package com.yujian.ai

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Debug
import android.content.ComponentCallbacks2
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectPreviewEngine
import com.yujian.ai.ai.subject.SlimSamRuntimeEvent
import com.yujian.ai.ai.subject.SlimSamRuntimeManager
import com.yujian.ai.ai.subject.SlimSamSegmenter
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.knowledge.FishKnowledgeRepository
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.recognition.domain.CanonicalPhotoPixelsV1
import java.io.ByteArrayInputStream
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolution
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionSubjectGeometrySnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionResultProductionEvidenceWriter
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadinessContract
import com.yujian.ai.ui.recognition.reveal.RevealPlaybackStatus
import com.yujian.ai.ui.screens.RecognitionProcessingScene
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.extractContour
import com.yujian.ai.ui.theme.YujianTheme
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionPathBindingCommitCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionPathBindingCommitReceipt
import com.yujian.ai.ui.waterlight.core.RecognitionPathBindingIdentity
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffConsumer
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealSessionStore
import com.yujian.ai.ui.recognition.reveal.RecognitionFishPathSnapshotBindingState
import com.yujian.ai.ui.recognition.reveal.RecognitionHandoffTransactionStatus
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Runtime proof using the registered night-catch photo, production detector/classifier and ML Kit mask. */
@RunWith(AndroidJUnit4::class)
class RecognitionRevealProductionRuntimeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun realNightCatchHandoffDrivesTheNativeResultReveal() = runProductionRuntimeFlow()

    /** Keep the large orchestration body out of a single generated coroutine state machine. */
    private fun runProductionRuntimeFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val slimSamRuntime = (targetContext.applicationContext as YujianApplication).slimSamRuntimeManager
        slimSamRuntime.startPreload()
        runBlocking { slimSamRuntime.awaitReady() }
        val startupRuntimeState = slimSamRuntime.state.value
        assertEquals("application startup preload must create both ONNX Sessions", com.yujian.ai.ai.subject.SlimSamRuntimeState.READY, startupRuntimeState.state)
        assertTrue(startupRuntimeState.visionSessionReady && startupRuntimeState.decoderSessionReady)
        assertEquals(SlimSamSegmenter.VISION_ENCODER_SHA256, startupRuntimeState.visionModelSha256)
        assertEquals(SlimSamSegmenter.DECODER_MODEL_SHA256, startupRuntimeState.decoderModelSha256)
        assertEquals(1, slimSamRuntime.metricsSnapshot().visionSessionCount)
        assertEquals(1, slimSamRuntime.metricsSnapshot().decoderSessionCount)
        val evidence = File(targetContext.cacheDir, "rr09-gold-transfer-production").apply {
            deleteRecursively()
            mkdirs()
        }
        val slimSamEvidence = File(targetContext.cacheDir, "YuJian_SlimSAM_Preload_Evidence").apply {
            deleteRecursively()
            mkdirs()
        }
        val sourceBytes = testContext.assets.open(SOURCE_ASSET).use { it.readBytes() }
        val sourceSha256 = sourceBytes.sha256()
        assertEquals(SOURCE_SHA256, sourceSha256)
        val selectedImage = runBlocking { RecognitionImageStore.normalizeAsset(testContext, targetContext, SOURCE_ASSET) }
        val bitmap = selectedImage.bitmap
        val imageId = selectedImage.imageId
        val canonicalPixelSha256 = selectedImage.photoSha256
        val photoIdentity = requireNotNull(selectedImage.photoIdentity)
        assertEquals(sourceSha256, photoIdentity.sourceBytesSha256)
        val rawExif = ExifInterface(ByteArrayInputStream(sourceBytes))
        assertEquals(rawExif.getAttribute(ExifInterface.TAG_ORIENTATION), selectedImage.sourceExifOrientationRaw)
        Log.i("YujianExifAudit", "reveal raw_orientation=${rawExif.getAttribute(ExifInterface.TAG_ORIENTATION)} parsed_orientation=${rawExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)} normalized_orientation=${selectedImage.sourceExifOrientation}")
        assertEquals(ExifInterface.ORIENTATION_NORMAL, selectedImage.sourceExifOrientation)
        assertEquals(selectedImage.sourceExifOrientation, photoIdentity.sourceOrientation)
        assertEquals(bitmap.width, photoIdentity.width)
        assertEquals(bitmap.height, photoIdentity.height)
        assertEquals(canonicalPixelSha256, CanonicalPhotoPixelsV1.sha256(bitmap.width, bitmap.height) { row, pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, row, bitmap.width, 1)
        })
        assertTrue("registered night-catch PhotoIdentity must be valid", photoIdentity.isValid())
        val recognitionModelIdentity = RecognitionModelIdentity(
            sessionId = SESSION_ID,
            attemptId = ATTEMPT_ID,
            imageId = selectedImage.imageId,
            photoSha256 = canonicalPixelSha256,
            photoIdentity = photoIdentity,
        )
        val subjectEngine = FishSubjectPreviewEngine(slimSamRuntime)
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
        val navigationRequests = AtomicInteger(0)
        val navigationRequestElapsedMs = AtomicReference<Long?>()
        val slimSamTerminalUptimeMs = AtomicReference<Long?>()
        val pathBoundUptimeMs = AtomicReference<Long?>()
        val automaticHandoff = AtomicReference<RecognitionRevealHandoff?>()
        val handoffFailure = AtomicReference<String?>()
        val processingCaptureFailure = AtomicReference<String?>()
        val processingCaptureStarted = AtomicBoolean(false)
        val rr09Evidence = File(evidence, "shared-stage-reveal").apply { mkdirs() }
        val processingCaptureReady = CountDownLatch(1)
        val screenshotExecutor = Executors.newSingleThreadExecutor()
        val pipelineStageEvents = Collections.synchronizedList(mutableListOf<JSONObject>())
        val repeatTimingsMs = Collections.synchronizedList(mutableListOf<Double>())
        val distinctPhotoWorkload = Collections.synchronizedList(mutableListOf<JSONObject>())
        val cancellationEvidence = AtomicReference<JSONObject?>()
        val memorySamples = Collections.synchronizedList(mutableListOf<JSONObject>())
        val pipelineDiagnosticEvents = Collections.synchronizedList(mutableListOf<JSONObject>())
        val memorySampler = Executors.newSingleThreadScheduledExecutor()
        fun captureMemory(label: String) {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val managed = Runtime.getRuntime()
            val procStatus = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
            fun procKb(name: String): Long? = procStatus.firstOrNull { it.startsWith("$name:") }
                ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
            val metrics = slimSamRuntime.metricsSnapshot()
            memorySamples += JSONObject()
                .put("label", label)
                .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                .put("java_heap_used_bytes", managed.totalMemory() - managed.freeMemory())
                .put("java_heap_max_bytes", managed.maxMemory())
                .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
                .put("native_heap_size_bytes", Debug.getNativeHeapSize())
                .put("total_pss_kb", info.totalPss)
                .put("native_pss_kb", info.nativePss)
                .put("dalvik_pss_kb", info.dalvikPss)
                .put("rss_kb", procKb("VmRSS") ?: JSONObject.NULL)
                .put("rss_peak_kb", procKb("VmHWM") ?: JSONObject.NULL)
                .put("swap_kb", procKb("VmSwap") ?: JSONObject.NULL)
                .put("vision_session_count", metrics.visionSessionCount)
                .put("decoder_session_count", metrics.decoderSessionCount)
                .put("embedding_buffer_bytes", metrics.embeddingBufferBytes)
                .put("cached_photo_count", metrics.cachedPhotoCount)
                .put("active_operation_count", metrics.activeOperationCount)
        }
        captureMemory("APP_STARTUP_PRELOAD_READY")
        val memorySampleFuture = memorySampler.scheduleAtFixedRate({
            runCatching { captureMemory("PERIODIC") }
        }, 5L, 5L, TimeUnit.SECONDS)
        var failureScreenshotPath: String? = null
        var failureScreenshotError: String? = null
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
        val pathBindingCommitCoordinator = RecognitionPathBindingCommitCoordinator()
        val controllerPathBindingReceipt = AtomicReference<RecognitionPathBindingCommitReceipt?>()
        val storePathBindingReceipt = AtomicReference<RecognitionPathBindingCommitReceipt?>()
        val coordinatorPathBindingReceipt = AtomicReference<RecognitionPathBindingCommitReceipt?>()

        fun assertPathBindingReceiptIdentity() {
            val state = requireNotNull(revealSessionStore.snapshot)
            val boundPath = requireNotNull(state.fishPathSnapshot)
            val controllerReceipt = requireNotNull(controllerPathBindingReceipt.get())
            val storeReceipt = requireNotNull(storePathBindingReceipt.get())
            val coordinatorReceipt = requireNotNull(coordinatorPathBindingReceipt.get())
            val expectedIdentity = RecognitionPathBindingIdentity(
                recognitionSessionId = boundPath.sessionId,
                attemptId = boundPath.attemptId,
                photoId = boundPath.imageId,
                photoIdentityHash = boundPath.photoSha256,
                pathSnapshotContentHash = boundPath.calculateContentSha256(),
                fishPathId = boundPath.fishPathId,
                maskSha256 = boundPath.maskSha256,
                maskVersion = boundPath.maskVersion,
                geometryVersion = boundPath.geometryVersion,
            )

            assertEquals(RecognitionFishPathSnapshotBindingState.BOUND, state.fishPathSnapshotBindingState)
            assertEquals(state.fishPathBindingCommitReceiptId, coordinatorReceipt.receiptId)
            assertEquals(SESSION_ID, coordinatorReceipt.identity.recognitionSessionId)
            assertEquals(ATTEMPT_ID, coordinatorReceipt.identity.attemptId)
            assertEquals(selectedImage.imageId, coordinatorReceipt.identity.photoId)
            assertEquals(selectedImage.photoSha256, coordinatorReceipt.identity.photoIdentityHash)
            assertSame("Controller and SessionStore must consume the exact same receipt instance", controllerReceipt, storeReceipt)
            assertSame("Coordinator must return the receipt consumed by both endpoints", storeReceipt, coordinatorReceipt)
            assertSame(coordinatorReceipt, pathBindingCommitCoordinator.committedReceipt(boundPath.attemptId))
            assertEquals(expectedIdentity, controllerReceipt.identity)
            assertEquals(expectedIdentity, storeReceipt.identity)
            assertEquals(expectedIdentity, coordinatorReceipt.identity)
            assertEquals(boundPath, state.fishPathSnapshot)
            assertEquals(boundPath.snapshotSha256, state.fishPathSnapshotProducerSha256)
            assertEquals(boundPath.calculateContentSha256(), state.fishPathSnapshotContentSha256)
            assertTrue("Controller must have committed the path named by the shared receipt", sceneController.fishPathSnapshotReady())
            assertEquals(
                boundPath.fishPathId,
                sceneController.gateSnapshot(SystemClock.uptimeMillis())["fishPathId"],
            )
        }
        val flowStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val navigationPrerequisitesReadyElapsedRealtimeMs = AtomicReference<Long?>()
        fun markNavigationPrerequisitesReady() {
            val state = revealSessionStore.snapshot ?: return
            if (state.productionResult?.ready == true && state.fishPathSnapshot != null &&
                state.revealDecision == com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState.FISH_FOCUS_READY
            ) {
                navigationPrerequisitesReadyElapsedRealtimeMs.compareAndSet(null, SystemClock.elapsedRealtime())
            }
        }
        val realTimeStages = Collections.synchronizedSet(EnumSet.of(sceneController.currentStage))
        val realTimeStageTimeline = Collections.synchronizedList(mutableListOf<JSONObject>())
        val lastObservedStage = AtomicReference<RecognitionSceneStage?>(null)
        val stageMonitorRunning = AtomicBoolean(true)
        val stageMonitorHandler = Handler(Looper.getMainLooper())
        val stageMonitor = object : Runnable {
            override fun run() {
                if (!stageMonitorRunning.get()) return
                val stage = sceneController.currentStage
                realTimeStages.add(stage)
                if (lastObservedStage.getAndSet(stage) != stage) {
                    realTimeStageTimeline += JSONObject()
                        .put("stage", stage.name)
                        .put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
                        .put("elapsed_since_flow_start_ms", SystemClock.elapsedRealtime() - flowStartElapsedRealtimeMs)
                }
                stageMonitorHandler.postDelayed(this, 30L)
            }
        }
        stageMonitorHandler.post(stageMonitor)
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

        val catalog = runBlocking {
            runCatching { FishKnowledgeRepository().listSpecies() }.getOrDefault(emptyList())
        }
        var failureReason: String? = null
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
                                    pipelineStageEvents += JSONObject()
                                        .put("phase", "PIPELINE_START")
                                        .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                        .put("thread_name", Thread.currentThread().name)
                                    pipeline.recognize(
                                        bitmap = selectedImage.bitmap,
                                        onProgress = { progress ->
                                            val atNs = SystemClock.elapsedRealtimeNanos()
                                            val phase = progress.phase.name
                                            pipelineStageEvents += JSONObject()
                                                .put("phase", phase)
                                                .put("uptime_ns", atNs)
                                                .put("thread_name", Thread.currentThread().name)
                                            onProgress(progress)
                                        },
                                        modelIdentity = recognitionModelIdentity,
                                        onDiagnostic = { name, stage, status, details, error ->
                                            pipelineDiagnosticEvents += JSONObject()
                                                .put("name", name)
                                                .put("stage", stage)
                                                .put("status", status)
                                                .put("details", JSONObject(details))
                                                .put("error", error?.javaClass?.simpleName ?: JSONObject.NULL)
                                                .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                        },
                                    ).also { result ->
                                        val productionPrediction = checkNotNull(result.prediction)
                                        val inputReceipt = checkNotNull(productionPrediction.classifierInputReceipt) {
                                            "production Classifier Input Receipt missing before SessionStore consumption"
                                        }
                                        val outputReceipt = checkNotNull(productionPrediction.classifierOutputReceipt) {
                                            "production Classifier Output Receipt missing before SessionStore consumption"
                                        }
                                        check(inputReceipt.isValid() && outputReceipt.isValid())
                                        check(inputReceipt.inferenceId == outputReceipt.inferenceId)
                                        check(inputReceipt.recognitionSessionId == SESSION_ID && outputReceipt.recognitionSessionId == SESSION_ID)
                                        check(inputReceipt.attemptId == ATTEMPT_ID && outputReceipt.attemptId == ATTEMPT_ID)
                                        check(inputReceipt.photoId == selectedImage.imageId && outputReceipt.photoId == selectedImage.imageId)
                                        check(inputReceipt.canonicalPixelSha256 == canonicalPixelSha256)
                                        check(outputReceipt.canonicalPixelSha256 == canonicalPixelSha256)
                                        val missingReceiptResult = result.copy(
                                            prediction = productionPrediction.copy(classifierInputReceipt = null),
                                        )
                                        check(revealSessionStore.recordClassifierResult(
                                            key = revealKey,
                                            result = missingReceiptResult,
                                            inferenceAsset = null,
                                            highConfidenceResult = true,
                                        ) == null) { "SessionStore accepted a production classifier result without its Input Receipt" }
                                        val staleAttemptResult = result.copy(
                                            prediction = productionPrediction.copy(
                                                classifierInputReceipt = inputReceipt.copy(attemptId = "$ATTEMPT_ID-stale"),
                                            ),
                                        )
                                        check(revealSessionStore.recordClassifierResult(
                                            key = revealKey,
                                            result = staleAttemptResult,
                                            inferenceAsset = null,
                                            highConfidenceResult = true,
                                        ) == null) { "SessionStore accepted a classifier Receipt from a stale Attempt" }
                                        pipelineStageEvents += JSONObject()
                                            .put("phase", "PIPELINE_END")
                                            .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                            .put("thread_name", Thread.currentThread().name)
                                        actualResult.set(result)
                                        val decision = revealSessionStore.recordClassifierResult(
                                            key = revealKey,
                                            result = result,
                                            inferenceAsset = null,
                                            highConfidenceResult = result.prediction?.lowConfidence == false,
                                        )
                                        checkNotNull(decision) { "production classifier result lost its Session/Attempt/PhotoSession identity" }
                                        markNavigationPrerequisitesReady()
                                    }
                                } finally {
                                    pipeline.close()
                                }
                            },
                            generateSubject = { selected, box ->
                                subjectEngine.generate(selected, box, SESSION_ID, ATTEMPT_ID).also(actualSubject::set)
                            },
                            prepareSubject = { selected ->
                                subjectEngine.prepare(
                                    selected,
                                    recognitionModelIdentity,
                                )
                            },
                            onSlimSamTiming = { identity, stage, event, startNs, endNs, outcome ->
                                slimSamRuntime.recordPhotoStage(
                                    identity,
                                    stage,
                                    event,
                                    startNs,
                                    endNs,
                                    outcome,
                                )
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
                                    assertEquals(RecognitionSceneStage.ORB_RETURN, sceneController.currentStage)
                                    val stage10Gate = sceneController.gateSnapshot(SystemClock.uptimeMillis())
                                    val blockedBeforeResultMount = stage10Gate["unmetConditions"] as? List<*>
                                    assertTrue(
                                        "Stage10 must navigate while Result-only gates are pending: $stage10Gate",
                                        blockedBeforeResultMount.orEmpty().containsAll(listOf(
                                            "orbReturnRenderReceiptReady",
                                            "orbReturnComplete",
                                            "glyphAvailable",
                                            "resultGeometryReady",
                                        )),
                                    )
                                    assertTrue(!sceneController.resultGeometryIsReady())
                                    assertTrue(!sceneController.orbReturnRenderReceiptReady())
                                    assertTrue(!sceneController.orbReturnComplete())
                                    assertEquals(
                                        RecognitionFishPathSnapshotBindingState.BOUND,
                                        revealSessionStore.snapshot?.fishPathSnapshotBindingState,
                                    )
                                    assertPathBindingReceiptIdentity()
                                    pathBoundUptimeMs.compareAndSet(null, SystemClock.uptimeMillis())
                                    markNavigationPrerequisitesReady()
                                    val prerequisitesReadyAt = requireNotNull(
                                        navigationPrerequisitesReadyElapsedRealtimeMs.get(),
                                    )
                                    val navigationNowElapsed = SystemClock.elapsedRealtime()
                                    val navigationNowUptime = SystemClock.uptimeMillis()
                                    val stage10EntryElapsed = navigationNowElapsed -
                                        sceneController.stageElapsedMs(navigationNowUptime)
                                    val navigationReadyAt = maxOf(prerequisitesReadyAt, stage10EntryElapsed)
                                    val navigationLatency = navigationNowElapsed - navigationReadyAt
                                    assertTrue(
                                        "Stage10 waited beyond the effective navigation cycle: ${navigationLatency}ms",
                                        navigationLatency < 2_000L,
                                    )
                                    assertTrue(revealSessionStore.requestResultNavigation(
                                        revealKey,
                                        SystemClock.uptimeMillis(),
                                    ))
                                    navigationRequests.incrementAndGet()
                                    navigationRequestElapsedMs.set(SystemClock.elapsedRealtime() - flowStartElapsedRealtimeMs)
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
                                if (subject.status == SubjectStatus.READY && geometry?.contour?.isNotEmpty() == true) {
                                    slimSamTerminalUptimeMs.compareAndSet(null, SystemClock.uptimeMillis())
                                }
                                markNavigationPrerequisitesReady()
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
                                markNavigationPrerequisitesReady()
                            },
                            onFishPathSnapshot = { pathSnapshot, prepareController, commitController ->
                                val controllerPrepared = prepareController()
                                val storePrepared = controllerPrepared && revealSessionStore.prepareFishPathSnapshot(
                                    revealKey,
                                    pathSnapshot,
                                )
                                val contentHash = pathSnapshot.calculateContentSha256()
                                val committedReceipt = pathBindingCommitCoordinator.commit(
                                    identity = RecognitionPathBindingIdentity(
                                        recognitionSessionId = pathSnapshot.sessionId,
                                        attemptId = pathSnapshot.attemptId,
                                        photoId = pathSnapshot.imageId,
                                        photoIdentityHash = pathSnapshot.photoSha256,
                                        pathSnapshotContentHash = contentHash,
                                        fishPathId = pathSnapshot.fishPathId,
                                        maskSha256 = pathSnapshot.maskSha256,
                                        maskVersion = pathSnapshot.maskVersion,
                                        geometryVersion = pathSnapshot.geometryVersion,
                                    ),
                                    nowUptimeMs = SystemClock.uptimeMillis(),
                                    controllerPrepared = controllerPrepared,
                                    storePrepared = storePrepared,
                                    commitController = { receipt ->
                                        controllerPathBindingReceipt.set(receipt)
                                        commitController(receipt)
                                    },
                                    commitStore = { receipt ->
                                        storePathBindingReceipt.set(receipt)
                                        revealSessionStore.commitPreparedFishPathSnapshot(revealKey, receipt)
                                    },
                                    compensateController = { receipt ->
                                        sceneController.abortFishPathBinding(receipt)
                                    },
                                    abortStore = { receipt ->
                                        revealSessionStore.abortPreparedFishPathSnapshot(revealKey, receipt?.receiptId)
                                    },
                                )
                                coordinatorPathBindingReceipt.set(committedReceipt)
                                val accepted = committedReceipt != null
                                markNavigationPrerequisitesReady()
                                if (!accepted) {
                                    handoffFailure.set(
                                        "production Processing PATH_BOUND controller/store transaction did not commit",
                                    )
                                }
                                accepted
                            },
                        )
                    } else {
                        val completed = resultState.value
                        val prediction = completed?.prediction
                        val resultSnapshot = revealSessionStore.resultSnapshot(revealKey)
                        if (completed != null && prediction != null && resultSnapshot != null) {
                            LaunchedEffect(resultSnapshot.key) {
                                check(revealSessionStore.markResultAttached(
                                    resultSnapshot.key,
                                    SystemClock.uptimeMillis(),
                                )) { "Result route attachment was rejected after Stage10 navigation" }
                            }
                            RecognitionResultScreen(
                                image = selectedImage,
                                prediction = prediction,
                                productionResult = completed,
                                subjectResult = actualSubject.get() ?: FishSubjectResult(SubjectStatus.IDLE),
                                availableSpecies = catalog,
                                revealHandoff = handoffState.value,
                                resultSnapshot = resultSnapshot,
                                initialResultHandoffEventSequence = resultSnapshot.handoffState.lastEventSequence,
                                initialResultHandoffEventTimestampMs = resultSnapshot.handoffState.lastEventTimestampMs,
                                onResultHandoffEvent = { event ->
                                    RecognitionResultHandoffConsumer.consume(
                                        key = revealKey,
                                        event = event,
                                        store = revealSessionStore,
                                        controller = sceneController,
                                    )
                                },
                                sceneFrameOverrideForTest = resultSceneFrame.value,
                                diagnosticPlaybackController = sceneController,
                                revealAlreadyPlayed = false,
                                onClaimReveal = revealSessionStore::claimPlayback,
                                onRevealFinished = { key ->
                                    revealSessionStore.releaseAnimationGeometry(key, SystemClock.uptimeMillis())
                                },
                                onPlaybackStart = revealSessionStore::startPlayback,
                                onPlaybackProgress = revealSessionStore::updatePlaybackProgress,
                                onPlaybackInterrupted = revealSessionStore::interruptPlayback,
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
            assertTrue("production Detector must return the registered fish", result.detectorRun.detections.isNotEmpty())
            assertEquals("production Detector output SHA must be present", 64, result.detectorRun.onnxSha256.length)
            val cropPixels = requireNotNull(result.cropPixels)
            assertEquals("production Crop must carry its four source pixel bounds", 4, cropPixels.size)
            assertTrue(cropPixels[0] >= 0 && cropPixels[1] >= 0)
            assertTrue(cropPixels[2] <= bitmap.width && cropPixels[3] <= bitmap.height)
            assertEquals("yellow_catfish", prediction.top1.speciesKey)
            assertTrue("registered night catch must qualify as HIGH", prediction.top1.confidence >= .55f)
            assertTrue("registered night catch must have a clear production margin", !prediction.lowConfidence)
            assertTrue("Top1 class index must be a real classifier output class", prediction.top1.classIndex in 0 until 15)
            val classifierInputReceipt = requireNotNull(prediction.classifierInputReceipt)
            val classifierOutputReceipt = requireNotNull(prediction.classifierOutputReceipt)
            assertEquals("serving_default_args_0", classifierInputReceipt.tensorName)
            assertEquals("FLOAT32", classifierInputReceipt.dataType)
            assertEquals(listOf(1L, 3L, 224L, 224L), classifierInputReceipt.shape)
            assertEquals(602112L, classifierInputReceipt.byteSize)
            assertEquals("serving_default_output_0_output", classifierOutputReceipt.tensorName)
            assertEquals("FLOAT32", classifierOutputReceipt.dataType)
            assertEquals(listOf(1L, 15L), classifierOutputReceipt.shape)
            assertEquals(60L, classifierOutputReceipt.byteSize)
            assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256,
                classifierInputReceipt.modelIdentity.runtimeVerifiedModelSha256)
            assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256,
                classifierOutputReceipt.modelIdentity.actualAssetSha256)
            assertEquals(FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256,
                classifierOutputReceipt.modelIdentity.expectedModelSha256)
            assertEquals("MODEL_CROP_M1_v0.2", classifierInputReceipt.modelIdentity.modelId)
            assertEquals(classifierInputReceipt.modelIdentity, classifierOutputReceipt.modelIdentity)
            assertEquals("yellow_catfish Top1 must come from the receipt-backed production Interpreter output",
                classifierInputReceipt.inferenceId, classifierOutputReceipt.inferenceId)
            assertEquals(recognitionModelIdentity.sessionId, classifierInputReceipt.recognitionSessionId)
            assertEquals(recognitionModelIdentity.attemptId, classifierInputReceipt.attemptId)
            assertEquals(selectedImage.imageId, classifierInputReceipt.photoId)
            assertEquals(canonicalPixelSha256, classifierInputReceipt.canonicalPixelSha256)
            assertEquals(selectedImage.imageId, classifierOutputReceipt.photoId)
            assertEquals(canonicalPixelSha256, classifierOutputReceipt.canonicalPixelSha256)
            assertTrue(classifierInputReceipt.contentHash.matches(Regex("[a-fA-F0-9]{64}")))
            assertTrue(classifierOutputReceipt.contentHash.matches(Regex("[a-fA-F0-9]{64}")))
            assertTrue("SessionStore consumer must see the validated receipt acceptance event", synchronized(pipelineDiagnosticEvents) {
                pipelineDiagnosticEvents.any { it.optString("name") == "CLASSIFIER_RECEIPTS_ACCEPTED" && it.optString("status") == "PASS" }
            })
            assertTrue("production diagnostics must record Detector and Crop producers", synchronized(pipelineDiagnosticEvents) {
                pipelineDiagnosticEvents.any { it.optString("name") == "DETECTOR_END" && it.optString("status") == "PASS" } &&
                    pipelineDiagnosticEvents.any { it.optString("name") == "CROP_SUCCESS" && it.optString("status") == "PASS" } &&
                    pipelineDiagnosticEvents.any { it.optString("name") == "CLASSIFIER_END" && it.optString("status") == "PASS" }
            })
            val maskResult = requireNotNull(actualSubject.get())
            assertEquals("production mask must be ready for the trusted contour reveal", SubjectStatus.READY, maskResult.status)
            assertEquals("production mask quality must be trusted", com.yujian.ai.ai.subject.FishSubjectQuality.GOOD, maskResult.quality)
            assertTrue("production Mask geometry must map to the original PhotoSession", subjectGeometry.get()?.contour?.isNotEmpty() == true)
            val executionEvents = slimSamRuntime.snapshotEvents()
            val encoderStartNs = executionEvents.firstOrNull {
                it.imageId == selectedImage.imageId && it.eventName == "VISION_ENCODER_START"
            }?.startUptimeNs
            val encoderEndNs = executionEvents.firstOrNull {
                it.imageId == selectedImage.imageId && it.eventName == "VISION_ENCODER_END"
            }?.endUptimeNs
            fun phaseAt(name: String): Long? = synchronized(pipelineStageEvents) {
                pipelineStageEvents.firstOrNull { it.optString("phase") == name }?.optLong("uptime_ns")
            }
            val detectorOverlapMs = overlapDurationMs(
                encoderStartNs, encoderEndNs, phaseAt("DETECTING"), phaseAt("OUTLINE"),
            )
            val classifierOverlapMs = overlapDurationMs(
                encoderStartNs, encoderEndNs, phaseAt("CLASSIFYING"), phaseAt("RESULT"),
            )
            assertTrue("Vision Encoder must overlap the production Detector interval; overlap=${detectorOverlapMs}ms", detectorOverlapMs > 0.0)
            assertTrue("Vision Encoder must overlap the production Classifier interval; overlap=${classifierOverlapMs}ms", classifierOverlapMs > 0.0)
            captureMemory("AFTER_REAL_VISION_AND_DECODER")
            val encoderCountBeforeRepeat = slimSamRuntime.snapshotEvents().count {
                it.imageId == selectedImage.imageId && it.eventName == "VISION_ENCODER_END"
            }
            assertEquals("one PhotoSession must execute one Vision Encoder", 1, encoderCountBeforeRepeat)
            repeat(5) { repeatIndex ->
                val repeatStartNs = SystemClock.elapsedRealtimeNanos()
                runBlocking {
                    slimSamRuntime.prepare(
                        selectedImage,
                        recognitionModelIdentity,
                    )
                }
                val repeatEndNs = SystemClock.elapsedRealtimeNanos()
                repeatTimingsMs += (repeatEndNs - repeatStartNs) / 1_000_000.0
                assertEquals("same PhotoSession repeat ${repeatIndex + 1} must reuse Embedding", 1,
                    slimSamRuntime.snapshotEvents().count {
                        it.imageId == selectedImage.imageId && it.eventName == "VISION_ENCODER_END"
                    })
            }
            assertEquals(5, repeatTimingsMs.size)
            assertTrue("same-photo repeats must hit the active Embedding", slimSamRuntime.snapshotEvents().count {
                it.imageId == selectedImage.imageId && it.eventName == "EMBEDDING_CACHE_HIT"
            } >= 5)
            captureMemory("AFTER_FIVE_SAME_PHOTO_CACHE_HITS")
            slimSamRuntime.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            assertTrue("critical trim must release inactive Embedding memory", waitUntil(5_000L) {
                slimSamRuntime.metricsSnapshot().cachedPhotoCount == 0
            })
            assertEquals(1, slimSamRuntime.metricsSnapshot().visionSessionCount)
            assertEquals(1, slimSamRuntime.metricsSnapshot().decoderSessionCount)
            captureMemory("AFTER_CRITICAL_TRIM_EMBEDDING_RELEASE")
            runBlocking {
                slimSamRuntime.prepare(
                    selectedImage,
                    recognitionModelIdentity,
                )
            }
            assertEquals("session reuse after cache trim", 1, slimSamRuntime.metricsSnapshot().visionSessionCount)
            assertEquals(1, slimSamRuntime.metricsSnapshot().decoderSessionCount)
            captureMemory("AFTER_EMBEDDING_RECREATION_SESSIONS_REUSED")
            assertTrue("production Processing screenshot was not captured", processingCaptureReady.await(15, TimeUnit.SECONDS))
            assertNull("production Processing screenshot failed: ${processingCaptureFailure.get()}", processingCaptureFailure.get())
            val processingPhotoSnapshot = requireNotNull(processingPhoto.get())
            val geometryVersion = WaterLightGeometryVersion.of(processingPhotoSnapshot.sourceToViewport)
            val handoff = requireNotNull(automaticHandoff.get())
            val resultEntryElapsedRealtimeMs = SystemClock.elapsedRealtime()
            assertEquals("production onFinished must enter Result exactly once", 1, resultEntries.get())
            assertEquals("Stage10 must issue exactly one Result navigation request", 1, navigationRequests.get())
            val navigationElapsed = requireNotNull(navigationRequestElapsedMs.get())
            assertTrue("result screen was not entered by the production callback", waitUntil(20_000L) { showResult.value })
            assertEquals(
                RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                revealSessionStore.snapshot?.resultHandoffState?.transactionStatus,
            )
            assertTrue("measured Result geometry did not open its controller gate", waitUntil(20_000L) {
                val value = geometryResolution.get()
                value?.geometry != null && sceneController.resultGeometryIsReady()
            })
            assertTrue(
                "production AUTO playback did not visit all 13 stages; observed=${synchronized(realTimeStages) { realTimeStages.map { it.name } }}",
                waitUntil(15_000L) { synchronized(realTimeStages) { realTimeStages.containsAll(RecognitionSceneStage.entries) } },
            )
            assertTrue(
                "Stage 11–13.5 did not complete its measured render and handoff receipts; " +
                    "stage=${sceneController.currentStage} events=${sceneController.completedResultHandoffSubstagesSnapshot()}",
                waitUntil(45_000L) { sceneController.sceneComplete() },
            )
            assertEquals(
                ResultHandoffSubstage.entries.toSet(),
                sceneController.completedResultHandoffSubstagesSnapshot(),
            )
            saveScreenshot(evidence.resolve("Recognition_Result_Automatic_Handoff.png"))
            assertEquals(selectedImage.imageId, handoff.imageId)
            assertEquals(SESSION_ID, handoff.key.recognitionSessionId)
            assertEquals(ATTEMPT_ID, handoff.key.recognitionAttemptId)
            assertEquals(selectedImage.photoSha256, handoff.photoSourceIdentity.substringAfter("|sha256="))
            assertEquals("Processing photo identity must cross the handoff unchanged", processingPhotoSnapshot.imageId, handoff.imageId)
            val trustedGeometry = requireNotNull(subjectGeometry.get())
            val fishPathSnapshot = requireNotNull(handoff.fishPathSnapshot)
            assertPathBindingReceiptIdentity()
            assertEquals(fishPathSnapshot, revealSessionStore.snapshot?.fishPathSnapshot)
            val bindingProbe = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
            }
            assertTrue(bindingProbe.updateFishPathSnapshot(revealKey, fishPathSnapshot))
            assertEquals(
                RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                bindingProbe.snapshot?.fishPathSnapshotBindingState,
            )
            assertEquals(fishPathSnapshot, bindingProbe.snapshot?.pendingFishPathSnapshot)
            bindingProbe.updateSubject(revealKey, maskResult, trustedGeometry)
            assertEquals(
                RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                bindingProbe.snapshot?.fishPathSnapshotBindingState,
            )
            val incompleteProcessingPhoto = processingPhotoSnapshot.copy(
                contactPointNormalizedSource = null,
                contactPointMaskSha256 = "",
                contactPointMaskVersion = -1L,
                contactPointGeometryVersion = 0L,
            )
            bindingProbe.updateProcessingPhoto(revealKey, incompleteProcessingPhoto)
            assertEquals(
                "default photo-side P*/Mask/geometry fields keep the exact received path pending",
                RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                bindingProbe.snapshot?.fishPathSnapshotBindingState,
            )
            assertEquals(fishPathSnapshot, bindingProbe.snapshot?.pendingFishPathSnapshot)
            assertTrue(
                bindingProbe.snapshot?.fishPathSnapshotBindingDiagnostic?.get("pendingInputs")
                    ?.contains("PHOTO_PSTAR_SOURCE") == true,
            )
            bindingProbe.updateProcessingPhoto(revealKey, processingPhotoSnapshot)
            assertEquals(
                RecognitionFishPathSnapshotBindingState.BOUND,
                bindingProbe.snapshot?.fishPathSnapshotBindingState,
            )
            assertEquals(fishPathSnapshot, bindingProbe.snapshot?.fishPathSnapshot)
            assertEquals(fishPathSnapshot.fishPathId, bindingProbe.snapshot?.fishPathSnapshotPathId)
            assertEquals(fishPathSnapshot.snapshotSha256, bindingProbe.snapshot?.fishPathSnapshotProducerSha256)
            assertEquals(fishPathSnapshot.calculateContentSha256(), bindingProbe.snapshot?.fishPathSnapshotContentSha256)
            assertTrue(requireNotNull(bindingProbe.snapshot?.fishPathSnapshotReceivedAtUptimeMs) > 0L)
            assertEquals("BOUND", bindingProbe.snapshot?.fishPathSnapshotBindingDiagnostic?.get("state"))
            assertEquals(
                fishPathSnapshot.snapshotSha256,
                bindingProbe.snapshot?.fishPathSnapshotBindingDiagnostic?.get("producerSnapshotSha256"),
            )
            val digestProbe = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
            }
            assertTrue(
                "SessionStore must fail closed on a corrupted producer Snapshot SHA",
                !digestProbe.updateFishPathSnapshot(revealKey, fishPathSnapshot.copy(snapshotSha256 = "f".repeat(64))),
            )
            assertEquals(
                RecognitionFishPathSnapshotBindingState.REJECTED_GEOMETRY,
                digestProbe.snapshot?.fishPathSnapshotBindingState,
            )
            assertEquals(
                "PATH_SNAPSHOT_SHA256_MISMATCH",
                digestProbe.snapshot?.fishPathSnapshotBindingDiagnostic?.get("reason"),
            )
            val callbackOrders = listOf(
                listOf("PATH", "SUBJECT", "PHOTO"),
                listOf("PATH", "PHOTO", "SUBJECT"),
                listOf("SUBJECT", "PATH", "PHOTO"),
                listOf("SUBJECT", "PHOTO", "PATH"),
                listOf("PHOTO", "PATH", "SUBJECT"),
                listOf("PHOTO", "SUBJECT", "PATH"),
            )
            callbackOrders.forEachIndexed { index, order ->
                val raceProbe = RecognitionRevealSessionStore().apply {
                    beginAttempt(SESSION_ID, "$ATTEMPT_ID-order-$index", selectedImage)
                }
                val raceKey = RecognitionRevealKey(SESSION_ID, "$ATTEMPT_ID-order-$index")
                val racePathWithoutDigest = fishPathSnapshot.copy(
                    attemptId = raceKey.recognitionAttemptId,
                    fishPathId = "${fishPathSnapshot.fishPathId}:callback-order-$index",
                    returnPathId = "${fishPathSnapshot.returnPathId}:callback-order-$index",
                    snapshotSha256 = "",
                )
                val racePath = racePathWithoutDigest.copy(
                    snapshotSha256 = racePathWithoutDigest.calculateContentSha256(),
                )
                order.forEach { callback ->
                    when (callback) {
                        "PATH" -> raceProbe.updateFishPathSnapshot(raceKey, racePath)
                        "SUBJECT" -> raceProbe.updateSubject(raceKey, maskResult, trustedGeometry.copy(
                            recognitionAttemptId = raceKey.recognitionAttemptId,
                        ))
                        "PHOTO" -> raceProbe.updateProcessingPhoto(raceKey, processingPhotoSnapshot)
                    }
                }
                assertEquals(
                    "real SessionStore callback order $order must converge to BOUND",
                    RecognitionFishPathSnapshotBindingState.BOUND,
                    raceProbe.snapshot?.fishPathSnapshotBindingState,
                )
                assertEquals(racePath, raceProbe.snapshot?.fishPathSnapshot)
            }
            val handoffRaceProbe = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
            }
            assertTrue(handoffRaceProbe.updateFishPathSnapshot(revealKey, fishPathSnapshot))
            handoffRaceProbe.updateSubject(revealKey, maskResult, trustedGeometry)
            assertEquals(
                com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState.WAITING_PSTAR_PATH,
                handoffRaceProbe.recordClassifierResult(
                    revealKey,
                    result,
                    inferenceAsset = null,
                    highConfidenceResult = true,
                ),
            )
            assertNull("Result cannot freeze a FULL_REVEAL handoff while its path is still pending",
                handoffRaceProbe.completeAttempt(
                    revealKey, result, true, null, SystemClock.uptimeMillis(),
                ))
            assertNull(handoffRaceProbe.snapshot?.handoff)
            handoffRaceProbe.updateProcessingPhoto(revealKey, processingPhotoSnapshot)
            assertEquals(RecognitionFishPathSnapshotBindingState.BOUND,
                handoffRaceProbe.snapshot?.fishPathSnapshotBindingState)
            val pathAfterRace = requireNotNull(handoffRaceProbe.completeAttempt(
                revealKey, result, true, null, SystemClock.uptimeMillis(),
            ))
            assertEquals(fishPathSnapshot, pathAfterRace.fishPathSnapshot)
            assertEquals(true, handoffRaceProbe.snapshot?.handoffFreezePathBound)

            val concurrentProbe = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
            }
            val concurrentStart = CountDownLatch(1)
            val concurrentExecutor = Executors.newFixedThreadPool(5)
            try {
                val concurrentCallbacks = listOf(
                    concurrentExecutor.submit {
                        concurrentStart.await()
                        concurrentProbe.updateFishPathSnapshot(revealKey, fishPathSnapshot)
                    },
                    concurrentExecutor.submit {
                        concurrentStart.await()
                        concurrentProbe.updateSubject(revealKey, maskResult, trustedGeometry)
                    },
                    concurrentExecutor.submit {
                        concurrentStart.await()
                        concurrentProbe.updateProcessingPhoto(revealKey, processingPhotoSnapshot)
                    },
                    concurrentExecutor.submit {
                        concurrentStart.await()
                        concurrentProbe.recordClassifierResult(
                            revealKey,
                            result,
                            inferenceAsset = null,
                            highConfidenceResult = result.prediction?.lowConfidence == false,
                        )
                    },
                    concurrentExecutor.submit {
                        concurrentStart.await()
                        concurrentProbe.completeAttempt(
                            revealKey,
                            result,
                            result.prediction?.lowConfidence == false,
                            null,
                            SystemClock.uptimeMillis(),
                        )
                    },
                )
                concurrentStart.countDown()
                concurrentCallbacks.forEach { it.get(10, TimeUnit.SECONDS) }
            } finally {
                concurrentExecutor.shutdownNow()
            }
            assertEquals(
                RecognitionFishPathSnapshotBindingState.BOUND,
                concurrentProbe.snapshot?.fishPathSnapshotBindingState,
            )
            val concurrentHandoff = concurrentProbe.snapshot?.handoff ?: concurrentProbe.completeAttempt(
                revealKey,
                result,
                result.prediction?.lowConfidence == false,
                null,
                SystemClock.uptimeMillis(),
            )
            assertEquals(fishPathSnapshot, concurrentHandoff?.fishPathSnapshot)
            assertEquals(true, concurrentProbe.snapshot?.handoffFreezePathBound)

            // A terminal SlimSAM failure must not freeze ORB_ONLY before its Processing-ledger
            // perimeter route arrives. The follow-up handoff must carry that route without Mask/P*.
            val orbOnlyAttemptId = "$ATTEMPT_ID-orb-only-race"
            val orbOnlyKey = RecognitionRevealKey(SESSION_ID, orbOnlyAttemptId)
            val orbOnlyStore = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, orbOnlyAttemptId, selectedImage)
                updateSubject(
                    orbOnlyKey,
                    FishSubjectResult(
                        status = SubjectStatus.FAILED,
                        errorCode = "SLIMSAM_RUNTIME_INIT_FAILED",
                        errorMessage = "instrumented terminal fallback fixture",
                    ),
                    geometry = null,
                )
            }
            assertEquals(
                com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState.ORB_ONLY_READY,
                orbOnlyStore.recordClassifierResult(
                    orbOnlyKey,
                    result,
                    inferenceAsset = null,
                    highConfidenceResult = result.prediction?.lowConfidence == false,
                ),
            )
            assertNull("ORB_ONLY cannot freeze a handoff before its real perimeter route arrives",
                orbOnlyStore.completeAttempt(
                    orbOnlyKey,
                    result,
                    result.prediction?.lowConfidence == false,
                    null,
                    SystemClock.uptimeMillis(),
                ))
            assertNull(orbOnlyStore.snapshot?.handoff)

            val orbOnlyFrame = com.yujian.ai.ui.waterlight.core.WaterLightFrame(
                sessionId = SESSION_ID,
                photoSha256 = selectedImage.photoSha256,
                elapsedMs = 1_000L,
                opticalReferenceElapsedMs = 1_000L,
                phase = com.yujian.ai.ui.waterlight.core.WaterLightPhase.PROCESSING,
                completion = 1f,
                reducedMotion = false,
                geometryVersion = geometryVersion.coerceAtLeast(1L),
                attemptId = orbOnlyAttemptId,
                imageId = selectedImage.imageId,
                photoContentVersion = selectedImage.bitmap.generationId.toLong().coerceAtLeast(0L),
                eventSequence = 9L,
            )
            val orbOnlyLedger = com.yujian.ai.ui.waterlight.core.WaterLightPathLedger()
            val releaseGeometry = requireNotNull(
                com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory.between(
                    com.yujian.ai.ui.waterlight.core.WaterLightPathPoint(20f, 40f),
                    com.yujian.ai.ui.waterlight.core.WaterLightPathPoint(220f, 340f),
                    seed = 346,
                    bendFraction = .1f,
                ),
            )
            assertTrue(orbOnlyLedger.recordRelease(orbOnlyFrame, releaseGeometry))
            val orbOnlyRoute = requireNotNull(orbOnlyLedger.exportOrbOnlyReturnPathSnapshot(orbOnlyFrame))
            assertTrue(orbOnlyRoute.isValid())
            assertTrue(orbOnlyStore.updateOrbOnlyReturnPathSnapshot(orbOnlyKey, orbOnlyRoute))
            val orbOnlyHandoff = requireNotNull(orbOnlyStore.completeAttempt(
                orbOnlyKey,
                result,
                result.prediction?.lowConfidence == false,
                null,
                SystemClock.uptimeMillis(),
            ))
            assertEquals(
                com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState.ORB_ONLY_READY,
                orbOnlyHandoff.revealDecision,
            )
            assertEquals(orbOnlyRoute, orbOnlyHandoff.orbOnlyReturnPathSnapshot)
            assertNull(orbOnlyHandoff.fishPathSnapshot)
            assertTrue(orbOnlyHandoff.maskSha256.isBlank())
            assertEquals(0L, orbOnlyHandoff.maskVersion)
            assertNull(orbOnlyHandoff.subjectAnchorNormalizedSource)
            assertEquals(true, orbOnlyStore.snapshot?.handoffFreezePathBound == false)

            val cancelledAttemptProbe = RecognitionRevealSessionStore().apply {
                beginAttempt(SESSION_ID, ATTEMPT_ID, selectedImage)
                beginAttempt(SESSION_ID, "$ATTEMPT_ID-replaced", selectedImage)
            }
            assertTrue(
                "late Path from a cancelled attempt must not enter the replacement Attempt",
                !cancelledAttemptProbe.updateFishPathSnapshot(revealKey, fishPathSnapshot),
            )
            assertEquals(SESSION_ID, fishPathSnapshot.sessionId)
            assertEquals(ATTEMPT_ID, fishPathSnapshot.attemptId)
            assertEquals(selectedImage.imageId, fishPathSnapshot.imageId)
            assertEquals(selectedImage.photoSha256, fishPathSnapshot.photoSha256)
            assertEquals("the original trusted Mask must cross the handoff unchanged",
                trustedGeometry.maskSha256, fishPathSnapshot.maskSha256)
            assertEquals(trustedGeometry.maskVersion, fishPathSnapshot.maskVersion)
            assertEquals(processingPhotoSnapshot.contactPointGeometryVersion, fishPathSnapshot.geometryVersion)
            assertTrue("the actual Handoff must record when the path binding was frozen",
                requireNotNull(revealSessionStore.snapshot?.handoffFreezeAtUptimeMs) > 0L)
            assertEquals(true, revealSessionStore.snapshot?.handoffFreezePathBound)
            assertEquals(
                com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState.FISH_FOCUS_READY,
                bindingProbe.recordClassifierResult(
                    revealKey,
                    result,
                    inferenceAsset = null,
                    highConfidenceResult = result.prediction?.lowConfidence == false,
                ),
            )
            val frozenProbeHandoff = requireNotNull(bindingProbe.completeAttempt(
                key = revealKey,
                result = result,
                highConfidenceResult = result.prediction?.lowConfidence == false,
                inferenceAsset = null,
                handoffTimestampUptimeMs = SystemClock.uptimeMillis(),
            ))
            assertTrue("only a bound path may freeze a full-reveal handoff", frozenProbeHandoff.fishPathSnapshot != null)
            val latePathWithoutDigest = fishPathSnapshot.copy(
                creationEventSequence = fishPathSnapshot.creationEventSequence + 1L,
                snapshotSha256 = "",
            )
            val lateDifferentPath = latePathWithoutDigest.copy(
                snapshotSha256 = latePathWithoutDigest.calculateContentSha256(),
            )
            assertTrue(
                "a distinct Path callback after handoff freeze must be rejected",
                !bindingProbe.updateFishPathSnapshot(revealKey, lateDifferentPath),
            )
            assertEquals(
                RecognitionFishPathSnapshotBindingState.REJECTED_FROZEN_HANDOFF,
                bindingProbe.snapshot?.fishPathSnapshotBindingState,
            )
            assertEquals(fishPathSnapshot, bindingProbe.snapshot?.handoff?.fishPathSnapshot)
            assertEquals("Processing RETURN must preserve its ledger-owned path ID",
                fishPathSnapshot.fishPathId, fishPathSnapshot.returnPathId)
            assertEquals(3, fishPathSnapshot.returnPathSegments.size)
            assertEquals("SessionStore accessor must return the same immutable Processing snapshot",
                fishPathSnapshot, revealSessionStore.fishPathSnapshot(revealKey))
            val acceptedAutomaticReturn = sceneController.acceptedEnergyEventsSnapshot().singleOrNull {
                it.stage == RecognitionSceneStage.ORB_RETURN && it.feedback == EnergyFeedback.RETURN
            }
            assertNotNull("the automatic Result host must submit its RETURN event to the shared controller",
                acceptedAutomaticReturn)
            assertTrue("RETURN Canvas receipt must precede EnergyEvent acceptance",
                sceneController.orbReturnRenderReceiptReady())
            assertTrue("accepted RETURN event must complete Stage 10", sceneController.orbReturnComplete())
            assertEquals("Result RETURN must render and submit all three Processing ledger segments",
                3, acceptedAutomaticReturn?.pathSegments?.size)
            assertEquals(fishPathSnapshot.returnPathId, acceptedAutomaticReturn?.pathId)
            assertEquals(fishPathSnapshot.returnPathSegments, acceptedAutomaticReturn?.pathSegments)
            assertEquals(fishPathSnapshot.maskSha256, acceptedAutomaticReturn?.pathMaskSha256)
            assertEquals(fishPathSnapshot.maskVersion, acceptedAutomaticReturn?.pathMaskVersion)
            assertEquals(fishPathSnapshot.geometryVersion, acceptedAutomaticReturn?.pathGeometryVersion)
            assertEquals(fishPathSnapshot.pStarNormalizedSource, acceptedAutomaticReturn?.pathContactPointNormalizedSource)
            assertEquals("production SessionStore must retain the same geometry snapshot", processingPhotoSnapshot,
                revealSessionStore.snapshot?.processingPhoto)
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
                .put("real_time_stage_timeline", org.json.JSONArray(synchronized(realTimeStageTimeline) { realTimeStageTimeline.toList() }))
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
                .put("stage10_navigation_request_elapsed_ms", navigationElapsed)
                .put("stage10_navigation_requests", navigationRequests.get())
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
            assertTrue("Result scene did not release geometry after its stable receipt", waitUntil(5_000L) {
                revealSessionStore.snapshot?.resultHandoffState?.animationGeometryReleased == true
            })
            writeApi36BusinessContract(
                targetContext = targetContext,
                evidence = evidence,
                key = revealKey,
                store = revealSessionStore,
                controller = sceneController,
                maskResult = maskResult,
                trustedGeometry = trustedGeometry,
                geometryResolution = requireNotNull(geometryResolution.get()),
                resultEntries = resultEntries.get(),
                navigationRequests = navigationRequests.get(),
                slimSamTerminalUptimeMs = requireNotNull(slimSamTerminalUptimeMs.get()),
                pathBoundUptimeMs = requireNotNull(pathBoundUptimeMs.get()),
                realTimeStages = synchronized(realTimeStages) { realTimeStages.toSet() },
            )
            runConsecutiveRealPhotoWorkload(
                targetContext = targetContext,
                testContext = testContext,
                runtime = slimSamRuntime,
                reportSink = distinctPhotoWorkload,
                captureMemory = ::captureMemory,
            )
            cancellationEvidence.set(runCancelledPhotoReplacement(
                targetContext = targetContext,
                runtime = slimSamRuntime,
                captureMemory = ::captureMemory,
            ))
        } catch (failure: Throwable) {
            failureReason = "${failure.javaClass.simpleName}: ${failure.message.orEmpty()}"
            throw failure
        } finally {
            stageMonitorRunning.set(false)
            stageMonitorHandler.removeCallbacks(stageMonitor)
            screenshotExecutor.shutdownNow()
            memorySampleFuture.cancel(true)
            memorySampler.shutdownNow()
            runCatching { captureMemory("FINAL") }
            runCatching { screenshotExecutor.awaitTermination(5L, TimeUnit.SECONDS) }
            if (failureReason != null) {
                val failureCapture = evidence.resolve("Recognition_Runtime_Failure.png")
                runCatching { saveScreenshot(failureCapture) }
                    .onSuccess { failureScreenshotPath = failureCapture.absolutePath }
                    .onFailure { failureScreenshotError = it.message ?: it.javaClass.simpleName }
            }
            // Dispose the Compose tree before releasing the source bitmap it is drawing.
            runCatching { activityRule.scenario.close() }
            subjectEngine.close()
            runCatching {
                writeSlimSamEvidence(
                    output = slimSamEvidence,
                    resultEvidence = evidence,
                    runtime = slimSamRuntime,
                    sourceImageId = selectedImage.imageId,
                    sourcePhotoSha256 = selectedImage.photoSha256,
                    memorySamples = synchronized(memorySamples) { memorySamples.toList() },
                    pipelineEvents = synchronized(pipelineStageEvents) { pipelineStageEvents.toList() },
                    repeatTimingsMs = synchronized(repeatTimingsMs) { repeatTimingsMs.toList() },
                    distinctPhotoWorkload = synchronized(distinctPhotoWorkload) { distinctPhotoWorkload.toList() },
                    cancellationEvidence = cancellationEvidence.get(),
                    failure = failureReason,
                )
            }.onFailure { error ->
                runCatching { File(slimSamEvidence, "evidence-write-failure.txt").writeText(error.stackTraceToString()) }
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            val diagnostic = JSONObject()
                .put("source_sha256", sourceSha256)
                .put("failure", failureReason)
                .put("result_ready", actualResult.get()?.ready)
                .put("species_id", actualResult.get()?.prediction?.top1?.speciesKey)
                .put("mask_status", actualSubject.get()?.status?.name)
                .put("mask_error", actualSubject.get()?.errorCode)
                .put("mask_quality", actualSubject.get()?.quality?.name)
                .put("contour_segments", subjectGeometry.get()?.contour?.size ?: 0)
                .put("automatic_handoff_observed", automaticHandoff.get() != null)
                .put("result_entry_mode", "PRODUCTION_ON_FINISHED_SESSIONSTORE_COMPLETE_ATTEMPT_SHARED_CONTROLLER")
                .put("result_entries", resultEntries.get())
                .put("handoff_session_id", automaticHandoff.get()?.key?.recognitionSessionId)
                .put("handoff_attempt_id", automaticHandoff.get()?.key?.recognitionAttemptId)
                .put("handoff_image_id", automaticHandoff.get()?.imageId)
                .put("automatic_handoff_gate", revealSessionStore.snapshot?.revealDecision?.name)
                .put("handoff_geometry_trust", automaticHandoff.get()?.geometryTrustState?.name)
                .put("handoff_eligibility", automaticHandoff.get()?.eligibility?.name)
                .put("processing_photo_image_id", processingPhoto.get()?.imageId)
                .put("result_geometry_status", geometryResolution.get()?.let { resolution ->
                    if (resolution.geometry != null) "RESOLVED" else resolution.failureReason ?: "PENDING"
                } ?: "NOT_OBSERVED")
                .put("processing_result_photo_identity_matches", automaticHandoff.get()?.imageId == processingPhoto.get()?.imageId)
                .put("controller_final_stage", sceneController.currentStage.name)
                .put("real_time_stages_observed", org.json.JSONArray(synchronized(realTimeStages) { realTimeStages.map { it.name }.sorted() }))
                .put("real_time_stage_timeline", org.json.JSONArray(synchronized(realTimeStageTimeline) { realTimeStageTimeline.toList() }))
                .put("failure_screenshot", failureScreenshotPath)
                .put("failure_screenshot_error", failureScreenshotError)
                .put("runtime_mode", "PRODUCTION_MODEL_AND_SLIMSAM_WITH_FIXED_60_SECOND_ATTEMPT_DEADLINE")
            runCatching { evidence.resolve("Runtime_Test_Diagnostic.json").writeText(diagnostic.toString(2) + "\n") }
        }
    }

    private fun overlapDurationMs(
        firstStart: Long?,
        firstEnd: Long?,
        secondStart: Long?,
        secondEnd: Long?,
    ): Double {
        if (firstStart == null || firstEnd == null || secondStart == null || secondEnd == null) return 0.0
        return (minOf(firstEnd, secondEnd) - maxOf(firstStart, secondStart)).coerceAtLeast(0L) / 1_000_000.0
    }

    private fun runConsecutiveRealPhotoWorkload(
        targetContext: android.content.Context,
        testContext: android.content.Context,
        runtime: SlimSamRuntimeManager,
        reportSink: MutableList<JSONObject>,
        captureMemory: (String) -> Unit,
    ) = runBlocking {
        val fixtures = listOf(
            testContext to "recognition_real_catch_fixture.jpg",
            targetContext to "test_catches/test_fish_01_catfish.jpg",
            targetContext to "test_catches/test_fish_02_yellow_catfish.jpg",
            targetContext to "test_catches/test_fish_03_mandarin_fish.jpg",
            targetContext to "test_catches/test_fish_04_mandarin_perch.jpg",
        )
        val photoHashes = mutableSetOf<String>()
        fixtures.forEachIndexed { index, (assetContext, assetName) ->
            val bytes = assetContext.assets.open(assetName).use { it.readBytes() }
            val photoSha = bytes.sha256()
            assertTrue("five-photo instrumentation must use five distinct source images", photoHashes.add(photoSha))
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "could not decode sequential photo fixture $assetName"
            }
            val imageId = "slimsam-sequential-${index + 1}-$photoSha"
            val attemptId = "slimsam-sequential-attempt-${index + 1}"
            val identity = RecognitionModelIdentity(SESSION_ID, attemptId, imageId, photoSha)
            val selected = SelectedImage(
                filePath = assetName,
                bitmap = bitmap,
                source = "androidTest_sequential_fish_photo",
                imageId = imageId,
                photoSha256 = photoSha,
            )
            val pipelineDiagnostics = Collections.synchronizedList(mutableListOf<JSONObject>())
            val startedNs = SystemClock.elapsedRealtimeNanos()
            val pipeline = FishRecognitionPipeline(targetContext)
            try {
                val recognition = coroutineScope {
                    val embedding = async(Dispatchers.Default) { runtime.prepare(selected, identity) }
                    val modelResult = async(Dispatchers.Default) {
                        pipeline.recognize(
                            bitmap = bitmap,
                            onProgress = { progress ->
                                pipelineDiagnostics += JSONObject()
                                    .put("event", "PIPELINE_PHASE")
                                    .put("phase", progress.phase.name)
                                    .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                    .put("thread_name", Thread.currentThread().name)
                            },
                            modelIdentity = identity,
                            onDiagnostic = { event, stage, result, metadata, error ->
                                pipelineDiagnostics += JSONObject()
                                    .put("event", event)
                                    .put("stage", stage)
                                    .put("result", result)
                                    .put("uptime_ns", SystemClock.elapsedRealtimeNanos())
                                    .put("thread_name", Thread.currentThread().name)
                                    .put("metadata", JSONObject(metadata))
                                    .put("error", error?.toString() ?: JSONObject.NULL)
                            },
                        )
                    }
                    embedding.await()
                    modelResult.await()
                }
                val detector = recognition.assessment.primary
                val evidence = JSONObject()
                    .put("sequence", index + 1)
                    .put("asset", assetName)
                    .put("image_id", imageId)
                    .put("photo_sha256", photoSha)
                    .put("width", bitmap.width)
                    .put("height", bitmap.height)
                    .put("recognition_status", recognition.status.name)
                    .put("recognition_ready", recognition.ready)
                    .put("failure_code", recognition.failureCode?.name ?: JSONObject.NULL)
                    .put("detector_eligible", recognition.assessment.isClassifierEligible)
                    .put("detector_box", detector?.box?.toString() ?: JSONObject.NULL)
                    .put("species", recognition.prediction?.top1?.speciesKey ?: JSONObject.NULL)
                    .put("recognition_duration_ms", (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0)
                    .put("pipeline_events", org.json.JSONArray(synchronized(pipelineDiagnostics) { pipelineDiagnostics.toList() }))
                reportSink += evidence
                assertTrue("production Detector/Classifier did not recognize $assetName", recognition.ready)
                val box = requireNotNull(detector?.box) { "production Detector returned no BBox for $assetName" }.normalized()
                val mask = runtime.generate(selected, box, identity)
                val photoEvents = runtime.snapshotEvents().filter { it.imageId == imageId && it.photoSha256 == photoSha }
                val encoderCount = photoEvents.count { it.eventName == "VISION_ENCODER_END" }
                val decoderCount = photoEvents.count { it.eventName == "MASK_DECODER_END" }
                val encoderStart = photoEvents.firstOrNull { it.eventName == "VISION_ENCODER_START" }?.startUptimeNs
                val encoderEnd = photoEvents.firstOrNull { it.eventName == "VISION_ENCODER_END" }?.endUptimeNs
                fun diagnosticAt(eventName: String): Long? = synchronized(pipelineDiagnostics) {
                    pipelineDiagnostics.firstOrNull { it.optString("event") == eventName }?.optLong("uptime_ns")
                }
                val detectorOverlapMs = overlapDurationMs(
                    encoderStart, encoderEnd, diagnosticAt("DETECTOR_START"), diagnosticAt("DETECTOR_END"),
                )
                val classifierOverlapMs = overlapDurationMs(
                    encoderStart, encoderEnd, diagnosticAt("CLASSIFIER_START"), diagnosticAt("CLASSIFIER_END"),
                )
                evidence
                    .put("mask_status", mask.status.name)
                    .put("mask_quality", mask.quality?.name ?: JSONObject.NULL)
                    .put("vision_encoder_count", encoderCount)
                    .put("mask_decoder_count", decoderCount)
                    .put("encoder_detector_overlap_ms", detectorOverlapMs)
                    .put("encoder_classifier_overlap_ms", classifierOverlapMs)
                    .put("vision_session_count", runtime.metricsSnapshot().visionSessionCount)
                    .put("decoder_session_count", runtime.metricsSnapshot().decoderSessionCount)
                    .put("cached_photo_count", runtime.metricsSnapshot().cachedPhotoCount)
                    .put("completed_uptime_ns", SystemClock.elapsedRealtimeNanos())
                assertEquals("one actual encoder execution is expected for $assetName", 1, encoderCount)
                assertEquals("one actual decoder execution is expected for $assetName", 1, decoderCount)
                assertTrue("$assetName Embedding must overlap Detector", detectorOverlapMs > 0.0)
                assertTrue("$assetName Embedding must overlap Classifier", classifierOverlapMs > 0.0)
                assertEquals(1, runtime.metricsSnapshot().visionSessionCount)
                assertEquals(1, runtime.metricsSnapshot().decoderSessionCount)
                assertEquals(1, runtime.metricsSnapshot().cachedPhotoCount)
                captureMemory("AFTER_DISTINCT_PHOTO_${index + 1}")
            } finally {
                pipeline.close()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        assertEquals("five distinct real photos must complete", 5, reportSink.size)
    }

    private fun runCancelledPhotoReplacement(
        targetContext: android.content.Context,
        runtime: SlimSamRuntimeManager,
        captureMemory: (String) -> Unit,
    ): JSONObject = runBlocking {
        val oldAsset = "test_catches/test_fish_01_catfish.jpg"
        val newAsset = "test_catches/test_fish_04_mandarin_perch.jpg"
        val oldBytes = targetContext.assets.open(oldAsset).use { it.readBytes() }
        val newBytes = targetContext.assets.open(newAsset).use { it.readBytes() }
        val oldSha = oldBytes.sha256()
        val newSha = newBytes.sha256()
        val oldBitmap = requireNotNull(BitmapFactory.decodeByteArray(oldBytes, 0, oldBytes.size))
        val newBitmap = requireNotNull(BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size))
        val oldImageId = "cancelled-photo-$oldSha"
        val newImageId = "replacement-photo-$newSha"
        val oldIdentity = RecognitionModelIdentity(SESSION_ID, "cancelled-photo-attempt", oldImageId, oldSha)
        val newIdentity = RecognitionModelIdentity(SESSION_ID, "replacement-photo-attempt", newImageId, newSha)
        val oldSelected = SelectedImage(oldAsset, oldBitmap, "androidTest_cancelled_photo", oldImageId, photoSha256 = oldSha)
        val newSelected = SelectedImage(newAsset, newBitmap, "androidTest_replacement_photo", newImageId, photoSha256 = newSha)
        var oldEncoderStarted = false
        var cancellationRequested = false
        var cancelledTaskObserved = false
        try {
            coroutineScope {
                val oldTask = async(Dispatchers.Default) { runtime.prepare(oldSelected, oldIdentity) }
                val deadline = SystemClock.elapsedRealtime() + 30_000L
                while (SystemClock.elapsedRealtime() < deadline && !oldEncoderStarted) {
                    oldEncoderStarted = runtime.snapshotEvents().any {
                        it.imageId == oldImageId && it.eventName == "VISION_ENCODER_START"
                    }
                    if (!oldEncoderStarted && oldTask.isCompleted) break
                    delay(5L)
                }
                assertTrue("cancel fixture never entered real Vision Encoder", oldEncoderStarted)
                oldTask.cancel()
                cancellationRequested = true
                val replacementTask = async(Dispatchers.Default) { runtime.prepare(newSelected, newIdentity) }
                runCatching { oldTask.await() }
                cancelledTaskObserved = oldTask.isCancelled
                replacementTask.await()
                assertEquals(0, runtime.metricsSnapshot().activeOperationCount)
                assertEquals(1, runtime.metricsSnapshot().cachedPhotoCount)
            }
            val replacementEvents = runtime.snapshotEvents().filter { it.imageId == newImageId && it.photoSha256 == newSha }
            assertTrue("replacement photo did not become the active Embedding", replacementEvents.any {
                it.eventName == "EMBEDDING_CACHE_MISS" || it.eventName == "EMBEDDING_CACHE_HIT"
            })
            JSONObject()
                .put("old_photo_sha256", oldSha)
                .put("replacement_photo_sha256", newSha)
                .put("old_encoder_started", oldEncoderStarted)
                .put("cancellation_requested_after_encoder_start", cancellationRequested)
                .put("cancelled_task_observed", cancelledTaskObserved)
                .put("replacement_embedding_ready", replacementEvents.any { it.eventName == "VISION_EMBEDDING_READY" })
                .put("active_operation_count_after_replace", runtime.metricsSnapshot().activeOperationCount)
                .put("cached_photo_count_after_replace", runtime.metricsSnapshot().cachedPhotoCount)
        } finally {
            if (!oldBitmap.isRecycled) oldBitmap.recycle()
            if (!newBitmap.isRecycled) newBitmap.recycle()
            captureMemory("AFTER_CANCELLATION_REPLACEMENT")
        }
    }

    private fun writeSlimSamEvidence(
        output: File,
        resultEvidence: File,
        runtime: SlimSamRuntimeManager,
        sourceImageId: String,
        sourcePhotoSha256: String,
        memorySamples: List<JSONObject>,
        pipelineEvents: List<JSONObject>,
        repeatTimingsMs: List<Double>,
        distinctPhotoWorkload: List<JSONObject>,
        cancellationEvidence: JSONObject?,
        failure: String?,
    ) {
        output.mkdirs()
        val runtimeSnapshot = runtime.state.value
        val metrics = runtime.metricsSnapshot()
        val events = runtime.snapshotEvents()
        fun JSONObject.longOrNull(name: String): Long? = optLong(name).takeIf { has(name) && !isNull(name) }
        fun stageStart(name: String) = pipelineEvents.firstOrNull { it.optString("phase") == name }?.longOrNull("uptime_ns")
        fun stageEnd(name: String) = pipelineEvents.firstOrNull { it.optString("phase") == name }?.longOrNull("uptime_ns")
        fun overlapMs(aStart: Long?, aEnd: Long?, bStart: Long?, bEnd: Long?): Double {
            if (aStart == null || aEnd == null || bStart == null || bEnd == null) return 0.0
            return (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0L) / 1_000_000.0
        }
        val photoEvents = events.filter { it.imageId == sourceImageId && it.photoSha256 == sourcePhotoSha256 }
        val encoderStart = photoEvents.firstOrNull { it.eventName == "VISION_ENCODER_START" }?.startUptimeNs
        val encoderEnd = photoEvents.firstOrNull { it.eventName == "VISION_ENCODER_END" }?.endUptimeNs
        val detectorStart = stageStart("DETECTING")
        val detectorEnd = stageEnd("OUTLINE")
        val classifierStart = stageStart("CLASSIFYING")
        val classifierEnd = stageEnd("RESULT")
        val pipelineStarted = stageStart("PIPELINE_START")
        val pipelineEnded = stageEnd("PIPELINE_END")
        val eventJson = org.json.JSONArray().apply {
            events.forEach { event ->
                put(JSONObject()
                    .put("recognitionSessionId", event.recognitionSessionId)
                    .put("recognitionAttemptId", event.recognitionAttemptId)
                    .put("imageId", event.imageId)
                    .put("photoSha256", event.photoSha256)
                    .put("modelVersion", event.modelVersion)
                    .put("stageName", event.stageName)
                    .put("eventName", event.eventName)
                    .put("startUptimeNs", event.startUptimeNs)
                    .put("endUptimeNs", event.endUptimeNs)
                    .put("durationMs", event.durationMs)
                    .put("threadName", event.threadName)
                    .put("result", event.result)
                    .put("errorCode", event.errorCode))
            }
        }
        val stateJson = JSONObject()
            .put("status", runtimeSnapshot.state.name)
            .put("attempt", runtimeSnapshot.attempt)
            .put("stage", runtimeSnapshot.stage)
            .put("visionSessionReady", runtimeSnapshot.visionSessionReady)
            .put("decoderSessionReady", runtimeSnapshot.decoderSessionReady)
            .put("visionModelSha256", runtimeSnapshot.visionModelSha256)
            .put("decoderModelSha256", runtimeSnapshot.decoderModelSha256)
            .put("intraOpThreads", runtimeSnapshot.intraOpThreads ?: JSONObject.NULL)
            .put("errorCode", runtimeSnapshot.errorCode ?: JSONObject.NULL)
            .put("errorMessage", runtimeSnapshot.errorMessage ?: JSONObject.NULL)
            .put("updatedUptimeNs", runtimeSnapshot.updatedUptimeNs)
            .put("visionSessionCount", metrics.visionSessionCount)
            .put("decoderSessionCount", metrics.decoderSessionCount)
            .put("activeOperationCount", metrics.activeOperationCount)
            .put("embeddingBufferBytes", metrics.embeddingBufferBytes)
            .put("cachedPhotoCount", metrics.cachedPhotoCount)
            .put("sourceImageId", sourceImageId)
            .put("sourcePhotoSha256", sourcePhotoSha256)
            .put("failure", failure ?: JSONObject.NULL)
            .put("events", eventJson)
        File(output, "model_runtime_state.json").writeText(stateJson.toString(2) + "\n")

        val encoderRows = photoEvents.filter { it.stageName == "VISION_ENCODER" }
        val decoderRows = photoEvents.filter { it.stageName == "MASK_DECODER" }
        fun runtimeEvent(name: String) = events.firstOrNull { it.eventName == name }
        val appCreate = runtimeEvent("APP_CREATE_START")
        val appFirstFrame = runtimeEvent("APP_FIRST_FRAME")
        val preloadReady = runtimeEvent("SLIMSAM_RUNTIME_READY")
        File(output, "slimsam_timing.json").writeText(JSONObject()
            .put("schema_version", 1)
            .put("source_image_id", sourceImageId)
            .put("source_photo_sha256", sourcePhotoSha256)
            .put("startup_timing", JSONObject()
                .put("app_create_start_uptime_ns", appCreate?.startUptimeNs ?: JSONObject.NULL)
                .put("app_first_frame_uptime_ns", appFirstFrame?.endUptimeNs ?: JSONObject.NULL)
                .put("app_first_frame_duration_ms", appFirstFrame?.durationMs ?: JSONObject.NULL)
                .put("runtime_ready_uptime_ns", preloadReady?.endUptimeNs ?: JSONObject.NULL)
                .put("preload_completed", runtimeSnapshot.state == com.yujian.ai.ai.subject.SlimSamRuntimeState.READY))
            .put("events", org.json.JSONArray().apply {
                photoEvents.forEach { event -> put(JSONObject()
                    .put("stageName", event.stageName)
                    .put("eventName", event.eventName)
                    .put("startUptimeNs", event.startUptimeNs)
                    .put("endUptimeNs", event.endUptimeNs)
                    .put("durationMs", event.durationMs)
                    .put("threadName", event.threadName)
                    .put("result", event.result)
                    .put("errorCode", event.errorCode)) }
            })
            .put("visionEncoderExecutionCount", encoderRows.count { it.eventName == "VISION_ENCODER_END" })
            .put("decoderExecutionCount", decoderRows.count { it.eventName == "MASK_DECODER_END" })
            .put("samePhotoCacheHitCount", photoEvents.count { it.eventName == "EMBEDDING_CACHE_HIT" })
            .put("samePhotoRepeatTimingsMs", org.json.JSONArray(repeatTimingsMs))
            .put("distinctPhotoCount", distinctPhotoWorkload.map { it.optString("photo_sha256") }.distinct().size)
            .put("distinctPhotoWorkload", org.json.JSONArray(distinctPhotoWorkload))
            .put("cancelledPhotoReplacement", cancellationEvidence ?: JSONObject.NULL)
            .toString(2) + "\n")

        val visualStages = resultEvidence.resolve("Stage_Timeline.json")
            .takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val recognizerInterval = JSONObject()
            .put("pipelineStartUptimeNs", pipelineStarted ?: JSONObject.NULL)
            .put("pipelineEndUptimeNs", pipelineEnded ?: JSONObject.NULL)
            .put("pipelineDurationMs", if (pipelineStarted != null && pipelineEnded != null) (pipelineEnded - pipelineStarted) / 1_000_000.0 else JSONObject.NULL)
            .put("detectorStartUptimeNs", detectorStart ?: JSONObject.NULL)
            .put("detectorEndUptimeNs", detectorEnd ?: JSONObject.NULL)
            .put("detectorDurationMs", if (detectorStart != null && detectorEnd != null) (detectorEnd - detectorStart) / 1_000_000.0 else JSONObject.NULL)
            .put("classifierStartUptimeNs", classifierStart ?: JSONObject.NULL)
            .put("classifierEndUptimeNs", classifierEnd ?: JSONObject.NULL)
            .put("classifierDurationMs", if (classifierStart != null && classifierEnd != null) (classifierEnd - classifierStart) / 1_000_000.0 else JSONObject.NULL)
            .put("encoderStartUptimeNs", encoderStart ?: JSONObject.NULL)
            .put("encoderEndUptimeNs", encoderEnd ?: JSONObject.NULL)
            .put("encoderDetectorOverlapMs", overlapMs(encoderStart, encoderEnd, detectorStart, detectorEnd))
            .put("encoderClassifierOverlapMs", overlapMs(encoderStart, encoderEnd, classifierStart, classifierEnd))
            .put("fiveDistinctPhotoWorkload", org.json.JSONArray(distinctPhotoWorkload))
            .put("visualStageTimeline", visualStages?.optJSONArray("stages") ?: org.json.JSONArray())
            .put("pipelineEvents", org.json.JSONArray().apply {
                pipelineEvents.forEach { put(it) }
            })
        File(output, "recognition_stage_timing.json").writeText(recognizerInterval.toString(2) + "\n")

        val consecutiveSamples = memorySamples.filter { it.optString("label").startsWith("AFTER_DISTINCT_PHOTO_") }
            .sortedBy { it.optString("label").substringAfterLast('_').toIntOrNull() ?: Int.MAX_VALUE }
        fun deltaLong(key: String): Long? {
            if (consecutiveSamples.size < 2) return null
            val first = consecutiveSamples.first()
            val last = consecutiveSamples.last()
            if (!first.has(key) || first.isNull(key) || !last.has(key) || last.isNull(key)) return null
            return last.optLong(key) - first.optLong(key)
        }
        File(output, "memory_profile.json").writeText(JSONObject()
            .put("schema_version", 1)
            .put("samples", org.json.JSONArray().apply { memorySamples.forEach { put(it) } })
            .put("peak_total_pss_kb", memorySamples.mapNotNull { if (it.has("total_pss_kb")) it.optInt("total_pss_kb") else null }.maxOrNull() ?: JSONObject.NULL)
            .put("peak_native_heap_allocated_bytes", memorySamples.mapNotNull { if (it.has("native_heap_allocated_bytes")) it.optLong("native_heap_allocated_bytes") else null }.maxOrNull() ?: JSONObject.NULL)
            .put("min_rss_kb", memorySamples.mapNotNull { if (it.has("rss_kb") && !it.isNull("rss_kb")) it.optLong("rss_kb") else null }.minOrNull() ?: JSONObject.NULL)
            .put("max_rss_kb", memorySamples.mapNotNull { if (it.has("rss_kb") && !it.isNull("rss_kb")) it.optLong("rss_kb") else null }.maxOrNull() ?: JSONObject.NULL)
            .put("five_distinct_photo_samples", org.json.JSONArray().apply { consecutiveSamples.forEach { put(it) } })
            .put("five_photo_growth_delta", JSONObject()
                .put("sample_count", consecutiveSamples.size)
                .put("total_pss_kb", deltaLong("total_pss_kb") ?: JSONObject.NULL)
                .put("native_heap_allocated_bytes", deltaLong("native_heap_allocated_bytes") ?: JSONObject.NULL)
                .put("rss_kb", deltaLong("rss_kb") ?: JSONObject.NULL))
            .put("session_and_embedding_state_after_five_photos", JSONObject()
                .put("vision_session_count", metrics.visionSessionCount)
                .put("decoder_session_count", metrics.decoderSessionCount)
                .put("embedding_buffer_bytes", metrics.embeddingBufferBytes)
                .put("cached_photo_count", metrics.cachedPhotoCount))
            .put("lmk_events", "See host-pulled logcat.txt and lowmemorykiller-events.txt")
            .toString(2) + "\n")

        val csv = buildString {
            appendLine("recognitionSessionId,recognitionAttemptId,imageId,photoSha256,modelVersion,stageName,eventName,startUptimeNs,endUptimeNs,durationMs,threadName,result,errorCode")
            events.forEach { event ->
                val values = listOf(
                    event.recognitionSessionId, event.recognitionAttemptId, event.imageId, event.photoSha256,
                    event.modelVersion, event.stageName, event.eventName, event.startUptimeNs.toString(),
                    event.endUptimeNs.toString(), event.durationMs.toString(), event.threadName, event.result, event.errorCode,
                )
                appendLine(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
        File(output, "slimsam_execution_trace.csv").writeText(csv)
        val screenshots = File(output, "screenshots").apply { mkdirs() }
        listOf("Recognition_Processing_Photo.png", "Recognition_Result_Automatic_Handoff.png")
            .map { resultEvidence.resolve(it) }
            .filter { it.isFile }
            .forEach { source -> source.copyTo(File(screenshots, source.name), overwrite = true) }
        resultEvidence.resolve("Stage_Timeline.json").takeIf { it.isFile }?.copyTo(File(output, "stage_timeline_visual.json"), overwrite = true)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100L)
        }
        return condition()
    }

    private fun writeApi36BusinessContract(
        targetContext: android.content.Context,
        evidence: File,
        key: RecognitionRevealKey,
        store: RecognitionRevealSessionStore,
        controller: com.yujian.ai.ui.waterlight.core.RecognitionSceneAnimationController,
        maskResult: FishSubjectResult,
        trustedGeometry: RecognitionSubjectGeometrySnapshot,
        geometryResolution: RecognitionRevealGeometryResolution,
        resultEntries: Int,
        navigationRequests: Int,
        slimSamTerminalUptimeMs: Long,
        pathBoundUptimeMs: Long,
        realTimeStages: Set<RecognitionSceneStage>,
    ) {
        val snapshot = requireNotNull(store.snapshot)
        val state = snapshot.resultHandoffState
        assertEquals(RecognitionFishPathSnapshotBindingState.BOUND, snapshot.fishPathSnapshotBindingState)
        assertEquals(ResultHandoffSubstage.entries, state.acceptedEvents.map { it.substage })
        assertEquals(5, state.acceptedEvents.size)
        assertEquals(5, state.eventTrace.size)
        assertTrue(state.eventTrace.all { it.accepted })
        assertTrue(state.resultSceneStable)
        assertEquals(RevealPlaybackStatus.COMPLETED, state.playbackStatus)
        assertTrue(state.animationGeometryReleased)
        assertTrue("production SessionStore evidence did not validate", RecognitionResultProductionEvidenceWriter.writeStableResult(
            targetContext,
            key,
            store,
            controller,
        ))

        val sourceBuildSha = BuildConfig.SOURCE_REVISION
        val modelContract = JSONObject(targetContext.assets.open("model_release_contract.json").bufferedReader().use { it.readText() })
        assertEquals("installed APK model contract must be built from this source", sourceBuildSha, modelContract.getString("build_sha"))
        val path = requireNotNull(snapshot.fishPathSnapshot)
        val navigation = state.navigationTrace
        val stage10 = navigation.single { it.status == RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED }
        val routeAttached = navigation.single { it.status == RecognitionHandoffTransactionStatus.RESULT_ATTACHED }
        val completed = navigation.single { it.status == RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED }
        assertEquals(1, navigationRequests)
        assertEquals(1, resultEntries)
        assertEquals(1, navigation.count { it.status == RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED })
        assertEquals(1, navigation.count { it.status == RecognitionHandoffTransactionStatus.RESULT_ATTACHED })

        val nameStableEvent = state.acceptedEvents.single { it.substage == ResultHandoffSubstage.NAME_STABLE }
        val glyphReadiness = requireNotNull(nameStableEvent.glyphReadiness)
        val glyphDescriptor = requireNotNull(glyphReadiness.descriptor)
        val glyphReceipt = requireNotNull(glyphReadiness.renderReceipt)
        assertTrue("glyph must have an actual accepted Canvas draw receipt", RecognitionGlyphReadinessContract.isNameStableClaim(
            nameStableEvent.identity,
            nameStableEvent.resultIdentity,
            glyphDescriptor.speciesName,
            glyphReadiness,
        ))
        val resultGeometry = requireNotNull(geometryResolution.geometry)
        val stageRows = controller.stageTimelineSnapshot()
        assertEquals(RecognitionSceneStage.entries, stageRows.map { it.stage })
        assertTrue(realTimeStages.containsAll(RecognitionSceneStage.entries))
        val orbToName = stageRows.single { it.stage == RecognitionSceneStage.ORB_TO_NAME }
        val returnEvent = controller.acceptedEnergyEventsSnapshot().single {
            it.stage == RecognitionSceneStage.ORB_RETURN && it.feedback == EnergyFeedback.RETURN
        }
        assertTrue(controller.orbReturnRenderReceiptReady())
        assertEquals(3, returnEvent.pathSegments.size)
        assertEquals(path.returnPathId, returnEvent.pathId)
        val stableEvent = state.acceptedEvents.single { it.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE }

        evidence.resolve("Stage_Timeline.json").writeText(JSONObject()
            .put("status", "PASS")
            .put("stages", org.json.JSONArray().apply {
                stageRows.forEach { row -> put(JSONObject()
                    .put("stage", row.stage.name)
                    .put("stage_ordinal", row.stageOrdinal)
                    .put("stage_start_timestamp_ms", row.stageStartTimestampMs)
                    .put("stage_end_timestamp_ms", row.stageEndTimestampMs)
                    .put("stage_duration_ms", row.stageDurationMs)
                    .put("required_duration_ms", row.requiredDurationMs)
                    .put("active_duration_ms", row.activeDurationMs)
                    .put("gate_wait_duration_ms", row.gateWaitDurationMs)
                    .put("event_sequence", row.eventSequence)) }
            }).toString(2) + "\n")
        evidence.resolve("Result_Navigation_Trace.json").writeText(JSONObject()
            .put("status", "PASS")
            .put("transitions", org.json.JSONArray().apply {
                navigation.forEach { row -> put(JSONObject()
                    .put("status", row.status.name)
                    .put("monotonicTimestampMs", row.monotonicTimestampMs)
                    .put("source", row.source)
                    .put("eventSequence", row.eventSequence)) }
            }).toString(2) + "\n")
        evidence.resolve("Result_Geometry.json").writeText(JSONObject()
            .put("status", "RESOLVED")
            .put("failure_reason", JSONObject.NULL)
            .put("geometry_version", nameStableEvent.geometryVersion)
            .put("source_width_px", resultGeometry.sourceWidth)
            .put("source_height_px", resultGeometry.sourceHeight)
            .put("title_bounds", JSONObject()
                .put("left", resultGeometry.titleBounds.left.toDouble())
                .put("top", resultGeometry.titleBounds.top.toDouble())
                .put("right", resultGeometry.titleBounds.right.toDouble())
                .put("bottom", resultGeometry.titleBounds.bottom.toDouble()))
            .put("return_path_id", resultGeometry.returnPathId)
            .put("return_path_segment_count", resultGeometry.returnPathSegments.size)
            .toString(2) + "\n")
        evidence.resolve("Return_Event.json").writeText(JSONObject()
            .put("accepted", true)
            .put("feedback", returnEvent.feedback.name)
            .put("source", returnEvent.source.name)
            .put("target", returnEvent.target.name)
            .put("render_canvas_receipt_ready", controller.orbReturnRenderReceiptReady())
            .put("segment_count", returnEvent.pathSegments.size)
            .put("path_id", returnEvent.pathId)
            .put("event_sequence", returnEvent.eventSequence)
            .put("timestamp_uptime_ms", returnEvent.monotonicTimestampMs)
            .toString(2) + "\n")
        val handoffTrace = JSONObject(requireNotNull(store.resultHandoffTraceJson(key)))
        evidence.resolve("Result_Handoff_Trace.json").writeText(handoffTrace.toString(2) + "\n")

        val facts = JSONObject()
            .put("slimsam_terminal", JSONObject()
                .put("status", maskResult.status.name)
                .put("quality", maskResult.quality?.name)
                .put("contour_segments", trustedGeometry.contour.size)
                .put("timestamp_uptime_ms", slimSamTerminalUptimeMs))
            .put("path_bound", JSONObject()
                .put("binding_state", snapshot.fishPathSnapshotBindingState.name)
                .put("path_id", path.returnPathId)
                .put("segment_count", path.returnPathSegments.size)
                .put("timestamp_uptime_ms", pathBoundUptimeMs))
            .put("stage10", JSONObject()
                .put("navigation_status", stage10.status.name)
                .put("navigation_requests", navigationRequests)
                .put("timestamp_uptime_ms", stage10.monotonicTimestampMs))
            .put("result_route_attached", JSONObject()
                .put("status", routeAttached.status.name)
                .put("result_entries", resultEntries)
                .put("timestamp_uptime_ms", routeAttached.monotonicTimestampMs))
            .put("return_event", JSONObject()
                .put("accepted", true)
                .put("feedback", returnEvent.feedback.name)
                .put("source", returnEvent.source.name)
                .put("render_canvas_receipt_ready", controller.orbReturnRenderReceiptReady())
                .put("segment_count", returnEvent.pathSegments.size)
                .put("path_id", returnEvent.pathId)
                .put("timestamp_uptime_ms", returnEvent.monotonicTimestampMs))
            .put("orb_to_name", JSONObject()
                .put("stage", orbToName.stage.name)
                .put("timestamp_uptime_ms", orbToName.stageStartTimestampMs))
            .put("glyph", JSONObject()
                .put("glyph_path_ready", glyphReadiness.glyphPathReady)
                .put("glyph_reveal_rendered", glyphReadiness.glyphRevealRendered)
                .put("name_stable", glyphReadiness.nameStable)
                .put("rendered_progress", glyphReceipt.renderedProgress.toDouble())
                .put("fill_path_drawn", glyphReceipt.fillPathDrawn)
                .put("contour_path_drawn", glyphReceipt.contourPathDrawn)
                .put("draw_pass_sequence", glyphReceipt.drawPassSequence)
                .put("timestamp_uptime_ms", nameStableEvent.monotonicTimestampMs))
            .put("name_stable", JSONObject()
                .put("accepted", true)
                .put("event_sequence", nameStableEvent.eventSequence)
                .put("timestamp_uptime_ms", nameStableEvent.monotonicTimestampMs))
            .put("geometry", JSONObject()
                .put("status", "RESOLVED")
                .put("geometry_version", nameStableEvent.geometryVersion)
                .put("return_path_segment_count", resultGeometry.returnPathSegments.size))
            .put("result_scene", JSONObject()
                .put("accepted", true)
                .put("result_scene_stable", state.resultSceneStable)
                .put("all_stages_visited", realTimeStages.containsAll(RecognitionSceneStage.entries))
                .put("stage_count", stageRows.size)
                .put("completed_handoff_substages", org.json.JSONArray().apply {
                    ResultHandoffSubstage.entries.forEach { put(it.name) }
                })
                .put("navigation_status", completed.status.name)
                .put("timestamp_uptime_ms", stableEvent.monotonicTimestampMs))
        val chain = org.json.JSONArray().apply {
            listOf(
                "SLIMSAM_TERMINAL" to slimSamTerminalUptimeMs,
                "PATH_BOUND" to pathBoundUptimeMs,
                "STAGE10" to stage10.monotonicTimestampMs,
                "RESULT_ROUTE_ATTACHED" to routeAttached.monotonicTimestampMs,
                "REAL_3_SEGMENT_RETURN_ACCEPTED" to returnEvent.monotonicTimestampMs,
                "ORB_TO_NAME" to orbToName.stageStartTimestampMs,
                "GLYPH_RENDERED" to nameStableEvent.monotonicTimestampMs,
                "NAME_STABLE" to nameStableEvent.monotonicTimestampMs,
                "RESULT_SCENE_STABLE" to stableEvent.monotonicTimestampMs,
            ).forEach { (name, timestamp) ->
                put(JSONObject().put("name", name).put("timestamp_uptime_ms", timestamp))
            }
        }
        evidence.resolve("AUTO_SMOKE_BUSINESS_CONTRACT.json").writeText(JSONObject()
            .put("schema_version", 1)
            .put("status", "PASS")
            .put("test", "com.yujian.ai.RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal")
            .put("source_build_sha", sourceBuildSha)
            .put("model_contract_build_sha", modelContract.getString("build_sha"))
            .put("event_chain", chain)
            .put("facts", facts)
            .put("evidence_files", JSONObject()
                .put("stage_timeline", "Stage_Timeline.json")
                .put("navigation", "Result_Navigation_Trace.json")
                .put("geometry", "Result_Geometry.json")
                .put("return_event", "Return_Event.json")
                .put("handoff_trace", "Result_Handoff_Trace.json"))
            .toString(2) + "\n")
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
