package com.yujian.ai

import android.content.ContentValues
import android.content.Context
import android.content.ComponentName
import android.net.Uri
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.yujian.ai.media.RecognitionImageStore
import org.junit.After
import org.junit.Rule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Drives the installed production app from Home through the system picker and Result route. */
@RunWith(AndroidJUnit4::class)
class RecognitionResultHandoffProductionNavigationTest {
    private data class ExpectedPhotoIdentity(
        val sourceBytesSha256: String,
        val canonicalPixelSha256: String,
        val sourceExifOrientationRaw: String?,
        val sourceExifOrientation: Int,
        val sourceOrientationDegreesApplied: Int,
        val normalizationVersion: String,
        val photoContentVersion: Long,
        val width: Int,
        val height: Int,
    )

    private var seededPhotoUri: Uri? = null
    private var activeEvidenceDir: File? = null
    private var activeDevice: UiDevice? = null
    private var activeTargetContext: android.content.Context? = null
    private val checkpointSequence = AtomicLong(0L)
    private val lastCheckpointNs = AtomicLong(0L)
    private val lastWatchdogCaptureNs = AtomicLong(0L)
    private val lastCheckpointEvent = AtomicReference("NOT_STARTED")
    private val checkpointLock = Any()
    private var flowStartedNs = 0L
    private var flowPhotoSha256: String? = null
    private var flowPhotoSourceBytesSha256: String? = null
    private var flowSessionId: String? = null
    private var flowAttemptId: String? = null
    private var watchdog: ScheduledExecutorService? = null

    @get:Rule
    val failureWatcher = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            checkpoint("TEST_FAILED", "FAIL", mapOf(
                "testClass" to description.className,
                "testMethod" to description.methodName,
                "errorType" to error.javaClass.name,
                "errorMessage" to error.message,
            ), error)
            captureDiagnosticSnapshot("TEST_FAILED")
            writeTraceSummary("FAIL")
        }

        override fun succeeded(description: Description) {
            checkpoint("TEST_COMPLETED", "PASS", mapOf(
                "testClass" to description.className,
                "testMethod" to description.methodName,
            ))
            writeTraceSummary("PASS")
        }
    }

    @After
    fun removeSeededPhoto() {
        watchdog?.shutdownNow()
        watchdog = null
        writeTraceSummary("AFTER_TEST")
        seededPhotoUri?.let { uri ->
            runCatching {
                InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.delete(uri, null, null)
            }
        }
        seededPhotoUri = null
    }

    @Test
    fun productionHomePickerModelPipelineAndResultHandoffCompleteOnDevice() = runProductionFlow(true)

    @Test
    fun hostedApi36HomePickerAutoRecognitionCompletesResultHandoff() = runProductionFlow(false)

    private fun runProductionFlow(requirePhysicalHuawei: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val device = UiDevice.getInstance(instrumentation)
        val evidenceDir = File(targetContext.cacheDir, EVIDENCE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        activeEvidenceDir = evidenceDir
        activeDevice = device
        activeTargetContext = targetContext
        flowStartedNs = SystemClock.elapsedRealtimeNanos()
        checkpointSequence.set(0L)
        lastWatchdogCaptureNs.set(0L)
        checkpoint("TEST_STARTED", "STARTED", mapOf("apiLevel" to Build.VERSION.SDK_INT))
        startWatchdog()
        File(targetContext.cacheDir, "recognition-runtime-result-handoff").deleteRecursively()

        val sourceBytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(FIXTURE_SHA256, sourceBytes.sha256())
        checkpoint("PHOTO_FIXTURE_VERIFIED", "PASS", mapOf(
            "filename" to FIXTURE,
            "sourceBytesSha256" to FIXTURE_SHA256,
            "byteSize" to sourceBytes.size,
        ))
        val displayName = "yujian-v3-${SystemClock.uptimeMillis()}.jpg"
        val photoUri = publishPhoto(targetContext, sourceBytes, displayName)
        seededPhotoUri = photoUri
        checkpoint("PHOTO_URI_PUBLISHED_TO_MEDIastore", "PASS", mapOf(
            "displayName" to displayName,
            "uriScheme" to photoUri.scheme,
            "uriAuthority" to photoUri.authority,
        ))
        val expectedPhotoIdentity = runCatching {
            kotlinx.coroutines.runBlocking {
                val normalized = RecognitionImageStore.normalize(targetContext, photoUri, "gallery")
                try {
                    val identity = requireNotNull(normalized.photoIdentity)
                    ExpectedPhotoIdentity(
                        sourceBytesSha256 = identity.sourceBytesSha256,
                        canonicalPixelSha256 = identity.canonicalPixelSha256,
                        sourceExifOrientationRaw = normalized.sourceExifOrientationRaw,
                        sourceExifOrientation = identity.sourceOrientation,
                        sourceOrientationDegreesApplied = normalized.sourceOrientationDegrees,
                        normalizationVersion = identity.normalizationVersion,
                        photoContentVersion = identity.photoContentVersion,
                        width = identity.width,
                        height = identity.height,
                    )
                } finally {
                    File(normalized.filePath).delete()
                    normalized.bitmap.recycle()
                }
            }
        }.getOrElse { error ->
            throw AssertionError("Seeded production photo could not be normalized: ${error.message}", error)
        }
        flowPhotoSha256 = expectedPhotoIdentity.canonicalPixelSha256
        flowPhotoSourceBytesSha256 = expectedPhotoIdentity.sourceBytesSha256
        checkpoint("PHOTO_IDENTITY_PRECOMPUTED", "PASS", mapOf(
            "photoSha256" to expectedPhotoIdentity.canonicalPixelSha256,
            "sourceBytesSha256" to expectedPhotoIdentity.sourceBytesSha256,
            "width" to expectedPhotoIdentity.width,
            "height" to expectedPhotoIdentity.height,
            "exifOrientation" to expectedPhotoIdentity.sourceExifOrientation,
        ))

        val pickerRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val pickerIntent = ActivityResultContracts.PickVisualMedia().createIntent(targetContext, pickerRequest)
        val resolvedPicker = targetContext.packageManager.resolveActivity(pickerIntent, 0)?.activityInfo
            ?: throw AssertionError("Production PickVisualMedia intent has no resolved system activity: $pickerIntent")
        val resolvedPickerComponent = ComponentName(resolvedPicker.packageName, resolvedPicker.name)
        val recognizedPickerPackages = setOf(resolvedPicker.packageName, GOOGLE_PHOTO_PICKER_PACKAGE)
        checkpoint("HOME_ACTIVITY_LAUNCH_REQUESTED", "STARTED", mapOf("pickerAction" to pickerIntent.action))

        val launchIntent = android.content.Intent(targetContext, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ActivityScenario.launch<MainActivity>(launchIntent).use {
            assertTrue("Production Home did not expose its album action", waitFor(device, 60_000L) {
                device.findObject(By.desc("从相册选择照片")) != null
            })
            checkpoint("HOME_ACTIVITY_RESUMED", "PASS", mapOf("currentPackage" to device.currentPackageName))
            requireNotNull(device.findObject(By.desc("从相册选择照片"))).click()

            // A fresh install presents the real first capture guide. Complete it through its UI,
            // then use the production camera page's album action; no app route or result is injected.
            if (device.wait(Until.hasObject(By.desc("跳过首次拍照教学")), 4_000L)) {
                requireNotNull(device.findObject(By.desc("跳过首次拍照教学"))).click()
                assertTrue("Production capture page did not expose its album action", waitFor(device, 30_000L) {
                    device.findObject(By.desc("从相册选择")) != null
                })
                requireNotNull(device.findObject(By.desc("从相册选择"))).click()
            }

            checkpoint("PHOTO_PICKER_OPEN_REQUESTED", "STARTED", mapOf(
                "resolvedPicker" to resolvedPickerComponent.flattenToString(),
                "pickerAction" to pickerIntent.action,
            ))
            val pickerOpened = waitFor(device, 30_000L) {
                device.currentPackageName in recognizedPickerPackages
            }
            val pickerActivityDump = device.executeShellCommand("dumpsys activity activities")
            File(evidenceDir, "picker_activity_dump.txt").writeText(pickerActivityDump)
            if (!pickerOpened) {
                val hierarchy = File(evidenceDir, "picker_hierarchy.xml")
                device.dumpWindowHierarchy(hierarchy)
                throw AssertionError(
                    "Resolved photo picker did not become visible; expected=$resolvedPickerComponent " +
                        "currentPackage=${device.currentPackageName}; action=${pickerIntent.action}; " +
                        "hierarchy=${hierarchy.name}",
                )
            }
            val resumedPicker = requireNotNull(resumedActivityComponent(pickerActivityDump)) {
                "Unable to identify a resumed system activity from dumpsys; expected=$resolvedPickerComponent"
            }
            assertTrue(
                "The resumed activity is not a recognized system Photo Picker alias: " +
                    "resolved=$resolvedPickerComponent resumed=$resumedPicker visiblePackage=${device.currentPackageName}",
                resumedPicker.packageName in recognizedPickerPackages,
            )
            assertEquals("The resumed picker does not match the visible package", device.currentPackageName, resumedPicker.packageName)
            assertTrue(
                "The resumed picker activity dump does not carry the contract action ${pickerIntent.action}",
                pickerActivityDump.contains("act=${pickerIntent.action}"),
            )
            checkpoint("PHOTO_PICKER_ACTIVITY_RESUMED", "PASS", mapOf(
                "component" to resumedPicker.flattenToString(),
                "package" to device.currentPackageName,
            ))
            selectNewestPhoto(device, displayName, resumedPicker.packageName)
            checkpoint("PHOTO_SELECTION_TAPPED", "STARTED", mapOf("displayName" to displayName))

            assertTrue("Production Processing page did not display the selected photo", waitFor(device, 60_000L) {
                device.findObject(By.desc("正在识别的鱼获照片")) != null
            })
            checkpoint("PHOTO_INPUT_READY_PROCESSING_VISIBLE", "PASS", mapOf("currentPackage" to device.currentPackageName))
            val processingScreenshot = File(evidenceDir, "processing_page.png")
            assertTrue("Unable to capture real Processing screen", device.takeScreenshot(processingScreenshot))
            assertTrue(processingScreenshot.length() > 0L)

            val qualificationFile = File(
                targetContext.cacheDir,
                "recognition-runtime-result-handoff/qualification_report.json",
            )
            val failureFile = File(targetContext.cacheDir, "recognition-runtime-result-handoff/attempt_failure.json")
            val resultDeadline = SystemClock.elapsedRealtime() + PRODUCTION_WAIT_MS
            checkpoint("WAITING_FOR_PRODUCTION_RESULT", "WAITING", mapOf("deadlineMs" to PRODUCTION_WAIT_MS))
            while (SystemClock.elapsedRealtime() < resultDeadline && !qualificationFile.isFile && !failureFile.isFile) {
                SystemClock.sleep(250L)
            }
            checkpoint(
                if (qualificationFile.isFile) "PRODUCTION_RESULT_EVIDENCE_READY" else "PRODUCTION_ATTEMPT_FAILURE_RECORDED",
                if (qualificationFile.isFile) "PASS" else "FAIL",
                mapOf(
                    "qualificationFileExists" to qualificationFile.isFile,
                    "failureFileExists" to failureFile.isFile,
                    "failureFile" to failureFile.takeIf(File::isFile)?.readText()?.take(4096),
                ),
            )
            assertTrue(
                "Production Result did not reach its evidence-producing stable state; " +
                    "terminalFailure=${failureFile.takeIf(File::isFile)?.readText() ?: "NONE"}; " +
                    "lastRuntime=${latestRuntimeState(targetContext)}",
                qualificationFile.isFile,
            )

            val evidenceRoot = qualificationFile.parentFile ?: error("app evidence folder missing")
            val runtimeMatrix = JSONObject(File(evidenceRoot, "runtime_matrix.json").readText())
            val runtimeIdentity = runtimeMatrix.getJSONObject("identity")
            flowSessionId = runtimeIdentity.getString("recognitionSessionId")
            flowAttemptId = runtimeIdentity.getString("recognitionAttemptId")
            assertEquals(expectedPhotoIdentity.canonicalPixelSha256, runtimeIdentity.getString("photoSha256"))
            assertEquals(expectedPhotoIdentity.sourceBytesSha256, runtimeIdentity.getString("sourceBytesSha256"))
            assertEquals(expectedPhotoIdentity.canonicalPixelSha256, runtimeIdentity.getString("canonicalPixelSha256"))
            assertTrue("App-selected PhotoIdentity must be valid", runtimeIdentity.getBoolean("photoIdentityValid"))
            val actualRawExifOrientation = if (runtimeIdentity.isNull("sourceExifOrientationRaw")) {
                null
            } else {
                runtimeIdentity.getString("sourceExifOrientationRaw")
            }
            assertEquals(expectedPhotoIdentity.sourceExifOrientationRaw, actualRawExifOrientation)
            assertEquals(expectedPhotoIdentity.sourceExifOrientation, runtimeIdentity.getInt("sourceExifOrientation"))
            assertEquals(expectedPhotoIdentity.sourceOrientationDegreesApplied,
                runtimeIdentity.getInt("sourceOrientationDegreesApplied"))
            assertEquals(expectedPhotoIdentity.normalizationVersion, runtimeIdentity.getString("normalizationVersion"))
            assertEquals(expectedPhotoIdentity.photoContentVersion, runtimeIdentity.getLong("photoContentVersion"))
            assertEquals(expectedPhotoIdentity.width, runtimeIdentity.getInt("width"))
            assertEquals(expectedPhotoIdentity.height, runtimeIdentity.getInt("height"))
            assertTrue("Production selection must create a recognition session",
                runtimeIdentity.getString("recognitionSessionId").isNotBlank())
            assertTrue("Production selection must create a recognition attempt",
                runtimeIdentity.getString("recognitionAttemptId").isNotBlank())
            assertEquals(13, runtimeMatrix.getInt("stageCount"))
            assertEquals(6_000L, runtimeMatrix.getLong("requiredAnimationBudgetMs"))
            assertTrue(runtimeMatrix.getBoolean("stageSequencePass"))
            assertTrue(runtimeMatrix.getBoolean("stageDurationContractPass"))

            val provenance = JSONObject(File(evidenceRoot, "runtime_provenance.json").readText())
            val runtimeDevice = provenance.getJSONObject("device")
            assertEquals(36, runtimeDevice.getInt("sdkInt"))
            if (requirePhysicalHuawei) {
                assertEquals("HUAWEI", runtimeDevice.getString("manufacturer").uppercase())
                assertEquals("CMS-AL20", runtimeDevice.getString("model"))
            } else {
                assertEquals(Build.MODEL, runtimeDevice.getString("model"))
                assertTrue("Hosted test must run on the API36 emulator",
                    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                        Build.HARDWARE.contains("ranchu", ignoreCase = true))
            }
            assertEquals(targetContext.packageName, provenance.getString("packageName"))

            val modelContract = JSONObject(File(evidenceRoot, "model_release_contract.json").readText())
            assertTrue("Packaged Classifier release identity is missing", modelContract.has("sha256"))
            assertTrue("Packaged Classifier release identity is missing", modelContract.getString("sha256").isNotBlank())

            val pathIdentity = JSONObject(File(evidenceRoot, "path_identity_trace.json").readText())
            assertTrue("The real SlimSAM path did not qualify for FULL_REVEAL", pathIdentity.getBoolean("fullReveal"))
            assertEquals("PASS", pathIdentity.getString("status"))
            val fishPathId = pathIdentity.getString("fishPathId")
            val returnId = pathIdentity.getString("returnPathId")
            assertEquals(fishPathId, returnId)
            assertTrue(pathIdentity.getJSONObject("acceptedAutomaticReturn").getString("pathId") == fishPathId)
            checkpoint("PSTAR_PATH_PRODUCED_AND_VALIDATED", "PASS", mapOf(
                "fishPathId" to fishPathId,
                "returnPathId" to returnId,
                "photoSha256" to runtimeIdentity.getString("photoSha256"),
                "attemptId" to runtimeIdentity.getString("recognitionAttemptId"),
            ))

            val stability = JSONObject(File(evidenceRoot, "result_stability_report.json").readText())
            assertEquals("PASS", stability.getString("status"))
            val handoff = JSONObject(File(evidenceRoot, "result_handoff_trace.json").readText())
            assertTrue(handoff.getBoolean("resultSceneStable"))
            val events = handoff.getJSONArray("events")
            assertEquals(5, events.length())
            val expectedSubstages = listOf(
                "NAME_STABLE", "PHOTO_SETTLE", "RESULT_SCENE_HANDOFF", "RESULT_INFO_ARRIVAL", "RESULT_SCENE_STABLE",
            )
            var previousTimestamp = 0L
            var acceptedGlyphPathId: String? = null
            var acceptedGlyphGeometryVersion: Long? = null
            for (index in 0 until events.length()) {
                val event = events.getJSONObject(index)
                assertTrue("Result handoff event was rejected", event.getBoolean("accepted"))
                assertEquals(index + 1L, event.getLong("eventSequence"))
                assertEquals(expectedSubstages[index], event.getString("substage"))
                assertEquals("RecognitionResultScreen.emitResultHandoffMilestone", event.getString("eventProducer"))
                assertEquals("RecognitionResultHandoffConsumer.consume", event.getString("eventConsumer"))
                assertEquals("yellow_catfish", event.getString("resultIdentity"))
                assertEquals("yellow_catfish", event.getString("glyphSpeciesKey"))
                assertEquals("黄颡鱼", event.getString("glyphSpeciesName"))
                assertTrue("The Glyph Path must be built for the current Result", event.getBoolean("glyphPathReady"))
                assertTrue("The Glyph Path must be actually drawn before handoff", event.getBoolean("glyphRevealRendered"))
                assertTrue("The accepted name event must carry NAME_STABLE", event.getBoolean("glyphNameStable"))
                assertTrue(event.getDouble("glyphDrawProgress") >= .999)
                assertTrue(event.getLong("glyphDrawPassSequence") > 0L)
                assertTrue(event.getString("glyphPathId").startsWith("glyph:"))
                assertTrue(event.getLong("glyphPathGeometryVersion") != 0L)
                if (acceptedGlyphPathId == null) {
                    acceptedGlyphPathId = event.getString("glyphPathId")
                    acceptedGlyphGeometryVersion = event.getLong("glyphPathGeometryVersion")
                } else {
                    assertEquals(acceptedGlyphPathId, event.getString("glyphPathId"))
                    assertEquals(acceptedGlyphGeometryVersion, event.getLong("glyphPathGeometryVersion"))
                }
                assertEquals(runtimeIdentity.getString("recognitionSessionId"), event.getString("sessionId"))
                assertEquals(runtimeIdentity.getString("recognitionAttemptId"), event.getString("attemptId"))
                val timestamp = event.getLong("timestamp")
                assertTrue("Handoff monotonic timestamps did not increase", timestamp > previousTimestamp)
                previousTimestamp = timestamp
            }
            checkpoint("SESSION_SNAPSHOT_AND_HANDOFF_ACCEPTED", "PASS", mapOf(
                "sessionId" to runtimeIdentity.getString("recognitionSessionId"),
                "attemptId" to runtimeIdentity.getString("recognitionAttemptId"),
                "photoSha256" to runtimeIdentity.getString("photoSha256"),
                "handoffEventCount" to events.length(),
            ))

            val navigation = JSONObject(File(evidenceRoot, "result_navigation_trace.json").readText())
            assertEquals("PASS", navigation.getString("status"))
            val transitions = navigation.getJSONArray("transitions")
            assertEquals(
                listOf("CREATED", "PROCESSING_COMPLETE", "HANDOFF_READY", "NAVIGATION_REQUESTED", "RESULT_ATTACHED", "HANDOFF_COMPLETED"),
                transitions.strings("status"),
            )

            val report = JSONObject(qualificationFile.readText())
            val checks = report.getJSONArray("checks")
            for (index in 0 until checks.length()) {
                val check = checks.getJSONObject(index)
                if (check.getString("id") !in setOf("T02_ATTEMPT_ISOLATION", "T13_60S_DEADLINE", "T28_EVIDENCE_INTEGRITY")) {
                    assertEquals("${check.getString("id")} did not pass in the actual production run", "PASS", check.getString("status"))
                }
            }

            assertTrue("The app did not navigate to a visible Result page", waitFor(device, 30_000L) {
                device.findObject(By.textContains("黄颡鱼")) != null ||
                    device.findObject(By.descContains("黄颡鱼")) != null
            })
            checkpoint("RESULT_PAGE_RESUMED_AND_VISIBLE", "PASS", mapOf("currentPackage" to device.currentPackageName))
            val resultScreenshot = File(evidenceDir, "result_page.png")
            assertTrue("Unable to capture real Result screen", device.takeScreenshot(resultScreenshot))
            assertTrue(resultScreenshot.length() > 0L)
            checkpoint("RESULT_RENDERED", "PASS", mapOf("screenshotBytes" to resultScreenshot.length()))

            JSONObject().apply {
                put("status", "PASS")
                put("test", if (requirePhysicalHuawei) "ProductionHomePickerModelPipelineAndResultHandoff" else "HostedApi36HomePickerAutoRecognitionAndResultHandoff")
                put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                put("device", runtimeDevice)
                put("sourceFixture", FIXTURE)
                put("sourceFixtureSha256", FIXTURE_SHA256)
                put("photoUriScheme", photoUri.scheme)
                put("photoUriAuthority", photoUri.authority)
                put("pickerAction", pickerIntent.action)
                put("resolvedPickerComponent", resolvedPickerComponent.flattenToString())
                put("resumedPickerComponent", resumedPicker.flattenToString())
                put("selectedPhotoSha256", runtimeIdentity.getString("photoSha256"))
                put("sourceBytesSha256", runtimeIdentity.getString("sourceBytesSha256"))
                put("canonicalPixelSha256", runtimeIdentity.getString("canonicalPixelSha256"))
                put("sourceExifOrientation", runtimeIdentity.getInt("sourceExifOrientation"))
                put("width", runtimeIdentity.getInt("width"))
                put("height", runtimeIdentity.getInt("height"))
                put("recognitionSessionId", runtimeIdentity.getString("recognitionSessionId"))
                put("recognitionAttemptId", runtimeIdentity.getString("recognitionAttemptId"))
                put("processingScreenshotSha256", processingScreenshot.readBytes().sha256())
                put("resultScreenshotSha256", resultScreenshot.readBytes().sha256())
                put("acceptedHandoffEventCount", events.length())
                put("navigationTransitions", JSONArray(transitions.strings("status")))
            }.write(File(evidenceDir, "production_e2e_test_report.json"))
        }
    }

    private fun startWatchdog() {
        val device = activeDevice ?: return
        val executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "api36-e2e-watchdog").apply { isDaemon = true }
        }
        watchdog = executor
        executor.scheduleWithFixedDelay({
            runCatching {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val progressAgeMs = ((nowNs - lastCheckpointNs.get()).coerceAtLeast(0L)) / 1_000_000L
                val lastEvent = lastCheckpointEvent.get()
                if (progressAgeMs >= 30_000L) {
                    val previousCaptureNs = lastWatchdogCaptureNs.get()
                    if (previousCaptureNs == 0L || nowNs - previousCaptureNs >= 30_000_000_000L) {
                        lastWatchdogCaptureNs.set(nowNs)
                        checkpoint("WATCHDOG_NO_PROGRESS", "DIAGNOSTIC", mapOf(
                            "lastCheckpoint" to lastEvent,
                            "noProgressMs" to progressAgeMs,
                        ))
                        captureDiagnosticSnapshot("NO_PROGRESS_${lastEvent}")
                    }
                }

                val runtimeLog = device.executeShellCommand("logcat -d -s SlimSamTiming:I")
                val lines = runtimeLog.lineSequence().toList()
                val encoderStart = lines.lastOrNull { "event=VISION_ENCODER_START" in it }
                val encoderRunId = encoderStart?.let {
                    Regex("runOptionsId=([^ ]+)").find(it)?.groupValues?.get(1)
                }
                val nativeEndForCurrentRun = encoderRunId?.let { runId ->
                    lines.lastOrNull { line ->
                        "event=VISION_ENCODER_NATIVE_END" in line && "runOptionsId=$runId" in line
                    }
                }
                if (encoderStart != null && encoderRunId != null && nativeEndForCurrentRun == null) {
                    val startNs = encoderStart.substringAfter("startUptimeNs=", "").substringBefore(' ').toLongOrNull()
                    val activeElapsedMs = startNs?.let { (SystemClock.elapsedRealtimeNanos() - it).coerceAtLeast(0L) / 1_000_000L }
                    if (activeElapsedMs != null && activeElapsedMs >= 45_000L) {
                        for (threshold in listOf(45, 55)) {
                            if (activeElapsedMs >= threshold * 1_000L) {
                                val marker = File(requireNotNull(activeEvidenceDir), "instrumentation/encoder-${threshold}s-captured")
                                if (!marker.exists()) {
                                    marker.parentFile?.mkdirs()
                                    marker.writeText("activeEncoderMs=$activeElapsedMs\n")
                                    checkpoint("ENCODER_WATCHDOG_${threshold}S", "DIAGNOSTIC", mapOf(
                                        "activeEncoderMs" to activeElapsedMs,
                                        "nativeRunStartLog" to encoderStart.take(2048),
                                    ))
                                    captureDiagnosticSnapshot("ENCODER_${threshold}S")
                                }
                            }
                        }
                    }
                }
                val nativeHeap = lines.asReversed().firstNotNullOfOrNull { line ->
                    Regex("nativeHeapAllocatedBytes=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                }
                if (nativeHeap != null && nativeHeap >= 2L * 1024L * 1024L * 1024L) {
                    val marker = File(requireNotNull(activeEvidenceDir), "instrumentation/native-heap-redline-captured")
                    if (!marker.exists()) {
                        marker.parentFile?.mkdirs()
                        marker.writeText("nativeHeapAllocatedBytes=$nativeHeap\n")
                        checkpoint("NATIVE_HEAP_2GB_REDLINE", "DIAGNOSTIC", mapOf("nativeHeapAllocatedBytes" to nativeHeap))
                        captureDiagnosticSnapshot("NATIVE_HEAP_2GB_REDLINE")
                    }
                }
            }
        }, 5L, 5L, TimeUnit.SECONDS)
    }

    private fun checkpoint(
        event: String,
        status: String,
        details: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        val root = activeEvidenceDir ?: return
        synchronized(checkpointLock) {
            runCatching {
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val sequence = checkpointSequence.incrementAndGet()
                val safeDetails = JSONObject().apply {
                    details.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                }
                val row = JSONObject()
                    .put("schemaVersion", 1)
                    .put("traceId", "api36-${Process.myPid()}-$flowStartedNs")
                    .put("sessionId", flowSessionId ?: JSONObject.NULL)
                    .put("attemptId", flowAttemptId ?: JSONObject.NULL)
                    .put("photoSha256", flowPhotoSha256 ?: JSONObject.NULL)
                    .put("sourceBytesSha256", flowPhotoSourceBytesSha256 ?: JSONObject.NULL)
                    .put("sequence", sequence)
                    .put("event", event)
                    .put("stage", event.substringBefore('_'))
                    .put("status", status)
                    .put("elapsedRealtimeNs", nowNs)
                    .put("durationMs", ((nowNs - flowStartedNs).coerceAtLeast(0L) / 1_000_000.0))
                    .put("processPid", Process.myPid())
                    .put("threadName", Thread.currentThread().name)
                    .put("currentPackage", runCatching { activeDevice?.currentPackageName }.getOrNull())
                    .put("details", safeDetails)
                if (error != null) {
                    row.put("exceptionType", error.javaClass.name)
                        .put("exceptionMessage", error.message ?: "")
                        .put("stackTrace", error.stackTraceToString().take(32_768))
                }
                val traceDir = File(root, "runtime_trace").apply { mkdirs() }
                File(traceDir, "events.jsonl").appendText(row.toString() + "\n")
                if (status != "DIAGNOSTIC") {
                    val instrumentationDir = File(root, "instrumentation").apply { mkdirs() }
                    val checkpointFile = File(instrumentationDir, "last_checkpoint.json")
                    val temporary = File(instrumentationDir, "last_checkpoint.json.tmp")
                    temporary.writeText(row.toString(2) + "\n")
                    if (!temporary.renameTo(checkpointFile)) {
                        temporary.copyTo(checkpointFile, overwrite = true)
                        temporary.delete()
                    }
                    lastCheckpointEvent.set(event)
                    lastCheckpointNs.set(nowNs)
                }
            }
        }
    }

    private fun captureDiagnosticSnapshot(reason: String) {
        val root = activeEvidenceDir ?: return
        val device = activeDevice ?: return
        val target = activeTargetContext ?: return
        runCatching {
            val instrumentationDir = File(root, "instrumentation").apply { mkdirs() }
            val threadDump = Thread.getAllStackTraces().entries
                .sortedBy { it.key.name }
                .joinToString("\n\n") { (thread, stack) ->
                    buildString {
                        append('"').append(thread.name).append('"')
                        append(" id=").append(thread.id).append(" state=").append(thread.state)
                        append(" daemon=").append(thread.isDaemon).append('\n')
                        stack.forEach { append("    at ").append(it).append('\n') }
                    }
                }
            File(instrumentationDir, "thread_dump.txt").writeText(threadDump)

            runCatching { device.dumpWindowHierarchy(File(instrumentationDir, "ui_hierarchy.xml")) }
            runCatching { device.takeScreenshot(File(instrumentationDir, "screen.png")) }
            val activityState = buildString {
                appendLine("reason=$reason")
                appendLine("sampleElapsedRealtimeNs=${SystemClock.elapsedRealtimeNanos()}")
                appendLine("instrumentationPid=${Process.myPid()}")
                appendLine("currentPackage=${runCatching { device.currentPackageName }.getOrDefault("UNKNOWN")}")
                appendLine("--- activity ---")
                appendLine(runCatching { device.executeShellCommand("dumpsys activity activities") }.getOrDefault("UNAVAILABLE"))
                appendLine("--- window ---")
                appendLine(runCatching { device.executeShellCommand("dumpsys window windows") }.getOrDefault("UNAVAILABLE"))
                appendLine("--- app memory ---")
                appendLine(runCatching { device.executeShellCommand("dumpsys meminfo ${target.packageName}") }.getOrDefault("UNAVAILABLE"))
                appendLine("--- app proc status ---")
                val pids = runCatching { device.executeShellCommand("pidof ${target.packageName}").trim() }.getOrDefault("")
                appendLine("pids=$pids")
                pids.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { pid ->
                    appendLine(runCatching {
                        device.executeShellCommand("cat /proc/$pid/status")
                    }.getOrDefault("UNAVAILABLE pid=$pid"))
                }
            }
            File(instrumentationDir, "activity_state.txt").writeText(activityState)

            val logcat = runCatching {
                device.executeShellCommand("logcat -d -b all -v threadtime -t 2000")
            }.getOrDefault("LOGCAT_UNAVAILABLE")
            File(root, "logcat").mkdirs()
            File(root, "logcat/logcat_${reason.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.txt")
                .writeText(logcat)
            val runtimeLines = runCatching { device.executeShellCommand("logcat -d -s SlimSamTiming:I") }
                .getOrDefault("").lineSequence().toList().takeLast(50)
            File(root, "runtime_trace/recent_runtime_events.log").apply {
                parentFile?.mkdirs()
                writeText(runtimeLines.joinToString("\n", postfix = if (runtimeLines.isEmpty()) "" else "\n"))
            }

            val exitInfo = JSONObject().apply {
                put("packageName", target.packageName)
                put("capturedElapsedRealtimeNs", SystemClock.elapsedRealtimeNanos())
                put("records", JSONArray().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val manager = target.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        manager.getHistoricalProcessExitReasons(target.packageName, 0, 10).forEach { info ->
                            put(JSONObject()
                                .put("processName", info.processName)
                                .put("reasonCode", info.reason)
                                .put("status", info.status)
                                .put("timestamp", info.timestamp)
                                .put("pssKb", info.pss)
                                .put("rssKb", info.rss)
                                .put("importance", info.importance)
                                .put("description", info.description ?: ""))
                        }
                    }
                })
            }
            exitInfo.write(File(root, "memory/process_exit_info.json"))

            val runtimeEvidence = File(target.cacheDir, "recognition-runtime-result-handoff")
            if (runtimeEvidence.isDirectory) {
                runtimeEvidence.copyRecursively(File(root, "handoff/app_runtime_evidence"), overwrite = true)
            }
            val attemptEvidence = File(target.filesDir, "recognition_attempt_diagnostics")
            attemptEvidence.listFiles()?.filter(File::isFile)?.maxByOrNull(File::lastModified)?.let { latest ->
                latest.copyTo(File(root, "runtime_trace/${latest.name}"), overwrite = true)
            }
            JSONObject().put("reason", reason)
                .put("lastCheckpointEvent", lastCheckpointEvent.get())
                .put("lastCheckpointAgeMs", ((SystemClock.elapsedRealtimeNanos() - lastCheckpointNs.get()).coerceAtLeast(0L) / 1_000_000L))
                .put("capturedElapsedRealtimeNs", SystemClock.elapsedRealtimeNanos())
                .write(File(root, "instrumentation/failure.json"))
        }
    }

    private fun writeTraceSummary(testStatus: String) {
        val root = activeEvidenceDir ?: return
        runCatching {
            val traceDir = File(root, "runtime_trace").apply { mkdirs() }
            val eventsFile = File(traceDir, "events.jsonl")
            val rows = eventsFile.takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }
            var previousNs: Long? = null
            val timeline = JSONArray().apply {
                rows.forEach { row ->
                    val elapsedNs = row.optLong("elapsedRealtimeNs").takeIf { it > 0L }
                    put(JSONObject()
                        .put("sequence", row.optLong("sequence"))
                        .put("event", row.optString("event"))
                        .put("stage", row.optString("stage"))
                        .put("status", row.optString("status"))
                        .put("elapsedRealtimeNs", elapsedNs ?: JSONObject.NULL)
                        .put("deltaFromPreviousMs", elapsedNs?.let { current ->
                            previousNs?.let { previous -> (current - previous).coerceAtLeast(0L) / 1_000_000.0 }
                        } ?: JSONObject.NULL)
                    )
                    if (elapsedNs != null) previousNs = elapsedNs
                }
            }
            JSONObject().put("schemaVersion", 1)
                .put("measurement", "instrumentation checkpoints from SystemClock.elapsedRealtimeNanos")
                .put("events", timeline)
                .write(File(traceDir, "stage_timing.json"))
            val checkpointFile = File(root, "instrumentation/last_checkpoint.json")
            val last = checkpointFile.takeIf(File::isFile)?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            JSONObject().put("testStatus", testStatus)
                .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                .put("traceId", last?.optString("traceId").orEmpty())
                .put("lastCheckpoint", last ?: JSONObject.NULL)
                .put("checkpointCount", rows.size)
                .put("processPid", Process.myPid())
                .put("currentPackage", runCatching { activeDevice?.currentPackageName }.getOrNull())
                .write(File(traceDir, "runtime_state.json"))
            val target = activeTargetContext
            if (target != null) {
                JSONObject().apply {
                    put("packageName", target.packageName)
                    put("capturedElapsedRealtimeNs", SystemClock.elapsedRealtimeNanos())
                    put("records", JSONArray().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val manager = target.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            manager.getHistoricalProcessExitReasons(target.packageName, 0, 10).forEach { info ->
                                put(JSONObject()
                                    .put("processName", info.processName)
                                    .put("reasonCode", info.reason)
                                    .put("status", info.status)
                                    .put("timestamp", info.timestamp)
                                    .put("pssKb", info.pss)
                                    .put("rssKb", info.rss)
                                    .put("importance", info.importance)
                                    .put("description", info.description ?: ""))
                            }
                        }
                    })
                }.write(File(root, "memory/process_exit_info.json"))
            }
        }
    }

    private fun publishPhoto(context: android.content.Context, bytes: ByteArray, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YuJianRecognitionV3")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Unable to publish the production test photo to MediaStore"
        }
        resolver.openOutputStream(uri, "w").use { stream ->
            requireNotNull(stream) { "Unable to write the production test photo" }.write(bytes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
        }
        return uri
    }

    private fun selectNewestPhoto(device: UiDevice, displayName: String, pickerPackage: String) {
        val filenameObject = device.findObject(By.textContains(displayName))
            ?: device.findObject(By.descContains(displayName))
        if (filenameObject != null) {
            filenameObject.click()
            return
        }
        val cells = device.findObjects(By.pkg(pickerPackage).clickable(true)).filter { item ->
            val bounds = item.visibleBounds
            bounds.width() >= 180 && bounds.height() >= 180 && bounds.top >= 0
        }.sortedWith(compareBy<UiObject2>({ it.visibleBounds.top }, { it.visibleBounds.left }))
        if (cells.isNotEmpty()) {
            cells.first().click()
            return
        }
        val hierarchy = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "picker_hierarchy.xml")
        device.dumpWindowHierarchy(hierarchy)
        throw AssertionError("System picker exposed no selectable photo cells; hierarchy=${hierarchy.absolutePath}")
    }

    private fun resumedActivityComponent(activityDump: String): ComponentName? {
        val activityLines = activityDump.lineSequence().filter { line ->
            line.contains("topResumedActivity") || line.contains("mResumedActivity")
        }
        val componentPattern = Regex("([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)")
        return activityLines.mapNotNull { line ->
            val match = componentPattern.find(line) ?: return@mapNotNull null
            val packageName = match.groupValues[1]
            val className = match.groupValues[2].let { if (it.startsWith('.')) packageName + it else it }
            ComponentName(packageName, className)
        }.firstOrNull()
    }

    private fun waitFor(device: UiDevice, timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(250L)
        }
        return condition()
    }

    private fun latestRuntimeState(context: android.content.Context): String {
        val root = File(context.cacheDir, "recognition-runtime-result-handoff")
        return listOf("attempt_failure.json", "runtime_matrix.json", "path_identity_trace.json")
            .joinToString("; ") { name ->
                val file = File(root, name)
                "$name=${file.takeIf(File::isFile)?.readText()?.take(2048) ?: "MISSING"}"
            }
    }

    private fun JSONArray.strings(key: String): List<String> =
        (0 until length()).map { getJSONObject(it).getString(key) }

    private fun JSONObject.write(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toString(2) + "\n")
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val GOOGLE_PHOTO_PICKER_PACKAGE = "com.google.android.photopicker"
        const val EVIDENCE_DIR = "recognition-result-handoff"
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val FIXTURE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
        const val PRODUCTION_WAIT_MS = 90_000L
    }
}
