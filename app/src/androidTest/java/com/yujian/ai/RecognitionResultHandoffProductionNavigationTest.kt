package com.yujian.ai

import android.content.ContentValues
import android.content.ComponentName
import android.net.Uri
import android.os.Build
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest

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

    @After
    fun removeSeededPhoto() {
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
        File(targetContext.cacheDir, "recognition-runtime-result-handoff").deleteRecursively()

        val sourceBytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(FIXTURE_SHA256, sourceBytes.sha256())
        val displayName = "yujian-v3-${SystemClock.uptimeMillis()}.jpg"
        val photoUri = publishPhoto(targetContext, sourceBytes, displayName)
        seededPhotoUri = photoUri
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

        val pickerRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val pickerIntent = ActivityResultContracts.PickVisualMedia().createIntent(targetContext, pickerRequest)
        val resolvedPicker = targetContext.packageManager.resolveActivity(pickerIntent, 0)?.activityInfo
            ?: throw AssertionError("Production PickVisualMedia intent has no resolved system activity: $pickerIntent")
        val resolvedPickerComponent = ComponentName(resolvedPicker.packageName, resolvedPicker.name)

        val launchIntent = android.content.Intent(targetContext, MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ActivityScenario.launch<MainActivity>(launchIntent).use {
            assertTrue("Production Home did not expose its album action", waitFor(device, 60_000L) {
                device.findObject(By.desc("从相册选择照片")) != null
            })
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

            val pickerOpened = waitFor(device, 30_000L) {
                device.findObjects(By.pkg(resolvedPicker.packageName)).isNotEmpty()
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
            assertEquals(
                "The actual resumed system picker differs from the ActivityResultContract resolution",
                resolvedPickerComponent,
                resumedPicker,
            )
            assertTrue(
                "The resumed picker activity dump does not carry the contract action ${pickerIntent.action}",
                pickerActivityDump.contains("act=${pickerIntent.action}"),
            )
            selectNewestPhoto(device, displayName, resolvedPicker.packageName)

            assertTrue("Production Processing page did not display the selected photo", waitFor(device, 60_000L) {
                device.findObject(By.desc("正在识别的鱼获照片")) != null
            })
            val processingScreenshot = File(evidenceDir, "processing_page.png")
            assertTrue("Unable to capture real Processing screen", device.takeScreenshot(processingScreenshot))
            assertTrue(processingScreenshot.length() > 0L)

            val qualificationFile = File(
                targetContext.cacheDir,
                "recognition-runtime-result-handoff/qualification_report.json",
            )
            val failureFile = File(targetContext.cacheDir, "recognition-runtime-result-handoff/attempt_failure.json")
            val resultDeadline = SystemClock.elapsedRealtime() + PRODUCTION_WAIT_MS
            while (SystemClock.elapsedRealtime() < resultDeadline && !qualificationFile.isFile && !failureFile.isFile) {
                SystemClock.sleep(250L)
            }
            assertTrue(
                "Production Result did not reach its evidence-producing stable state; " +
                    "terminalFailure=${failureFile.takeIf(File::isFile)?.readText() ?: "NONE"}; " +
                    "lastRuntime=${latestRuntimeState(targetContext)}",
                qualificationFile.isFile,
            )

            val evidenceRoot = qualificationFile.parentFile ?: error("app evidence folder missing")
            val runtimeMatrix = JSONObject(File(evidenceRoot, "runtime_matrix.json").readText())
            val runtimeIdentity = runtimeMatrix.getJSONObject("identity")
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
            val resultScreenshot = File(evidenceDir, "result_page.png")
            assertTrue("Unable to capture real Result screen", device.takeScreenshot(resultScreenshot))
            assertTrue(resultScreenshot.length() > 0L)

            JSONObject().apply {
                put("status", "PASS")
                put("test", if (requirePhysicalHuawei) "ProductionHomePickerModelPipelineAndResultHandoff" else "HostedApi36HomePickerAutoRecognitionAndResultHandoff")
                put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                put("device", runtimeDevice)
                put("sourceFixture", FIXTURE)
                put("sourceFixtureSha256", FIXTURE_SHA256)
                put("pickerAction", pickerIntent.action)
                put("resolvedPickerComponent", resolvedPickerComponent.flattenToString())
                put("resumedPickerComponent", resumedPicker.flattenToString())
                put("selectedPhotoSha256", runtimeIdentity.getString("photoSha256"))
                put("recognitionSessionId", runtimeIdentity.getString("recognitionSessionId"))
                put("recognitionAttemptId", runtimeIdentity.getString("recognitionAttemptId"))
                put("processingScreenshotSha256", processingScreenshot.readBytes().sha256())
                put("resultScreenshotSha256", resultScreenshot.readBytes().sha256())
                put("acceptedHandoffEventCount", events.length())
                put("navigationTransitions", JSONArray(transitions.strings("status")))
            }.write(File(evidenceDir, "production_e2e_test_report.json"))
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
        const val EVIDENCE_DIR = "recognition-result-handoff"
        const val FIXTURE = "detector_case_b_night_flash.jpg"
        const val FIXTURE_SHA256 = "a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b"
        const val PRODUCTION_WAIT_MS = 90_000L
    }
}
