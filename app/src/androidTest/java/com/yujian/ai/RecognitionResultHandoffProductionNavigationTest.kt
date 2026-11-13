package com.yujian.ai

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
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
import java.security.MessageDigest

/** Drives the installed production app from Home through the system picker and Result route. */
@RunWith(AndroidJUnit4::class)
class RecognitionResultHandoffProductionNavigationTest {
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
    fun productionHomePickerModelPipelineAndResultHandoffCompleteOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val device = UiDevice.getInstance(instrumentation)
        val evidenceDir = File(testContext.cacheDir, EVIDENCE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        File(targetContext.cacheDir, "recognition-runtime-result-handoff").deleteRecursively()

        val sourceBytes = testContext.assets.open(FIXTURE).use { it.readBytes() }
        assertEquals(FIXTURE_SHA256, sourceBytes.sha256())
        val displayName = "yujian-v3-${SystemClock.uptimeMillis()}.jpg"
        val photoUri = publishPhoto(targetContext, sourceBytes, displayName)
        seededPhotoUri = photoUri
        val expectedSelectedSha256 = runCatching {
            val normalized = kotlinx.coroutines.runBlocking {
                RecognitionImageStore.normalize(targetContext, photoUri, "gallery")
            }
            try {
                normalized.photoSha256
            } finally {
                File(normalized.filePath).delete()
                normalized.bitmap.recycle()
            }
        }.getOrElse { error ->
            throw AssertionError("Seeded production photo could not be normalized: ${error.message}", error)
        }

        val launchIntent = android.content.Intent(targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PRODUCTION_AUTO_PLAYBACK, true)
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

            assertTrue("System photo picker did not open", waitFor(device, 30_000L) {
                pickerPackages().any { pkg -> device.findObject(By.pkg(pkg)) != null }
            })
            selectNewestPhoto(device, displayName)

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
            val resultDeadline = SystemClock.elapsedRealtime() + PRODUCTION_WAIT_MS
            while (SystemClock.elapsedRealtime() < resultDeadline && !qualificationFile.isFile) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(500L)
            }
            assertTrue("Production Result did not reach its evidence-producing stable state", qualificationFile.isFile)

            val evidenceRoot = qualificationFile.parentFile ?: error("app evidence folder missing")
            val runtimeMatrix = JSONObject(File(evidenceRoot, "runtime_matrix.json").readText())
            val runtimeIdentity = runtimeMatrix.getJSONObject("identity")
            assertEquals(expectedSelectedSha256, runtimeIdentity.getString("photoSha256"))
            assertEquals(13, runtimeMatrix.getInt("stageCount"))
            assertEquals(6_000L, runtimeMatrix.getLong("requiredAnimationBudgetMs"))
            assertTrue(runtimeMatrix.getBoolean("stageSequencePass"))
            assertTrue(runtimeMatrix.getBoolean("stageDurationContractPass"))

            val provenance = JSONObject(File(evidenceRoot, "runtime_provenance.json").readText())
            val runtimeDevice = provenance.getJSONObject("device")
            assertEquals(36, runtimeDevice.getInt("sdkInt"))
            assertEquals("HUAWEI", runtimeDevice.getString("manufacturer").uppercase())
            assertEquals("CMS-AL20", runtimeDevice.getString("model"))
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
            for (index in 0 until events.length()) {
                val event = events.getJSONObject(index)
                assertTrue("Result handoff event was rejected", event.getBoolean("accepted"))
                assertEquals(index + 1L, event.getLong("eventSequence"))
                assertEquals(expectedSubstages[index], event.getString("substage"))
                assertEquals("RecognitionResultScreen.emitResultHandoffMilestone", event.getString("eventProducer"))
                assertEquals("RecognitionResultHandoffConsumer.consume", event.getString("eventConsumer"))
                assertEquals("yellow_catfish", event.getString("resultIdentity"))
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
                put("test", "ProductionHomePickerModelPipelineAndResultHandoff")
                put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
                put("device", runtimeDevice)
                put("sourceFixture", FIXTURE)
                put("sourceFixtureSha256", FIXTURE_SHA256)
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

    private fun selectNewestPhoto(device: UiDevice, displayName: String) {
        val filenameObject = device.findObject(By.textContains(displayName))
            ?: device.findObject(By.descContains(displayName))
        if (filenameObject != null) {
            filenameObject.click()
            return
        }
        val packages = pickerPackages()
        val cells = packages.flatMap { pkg ->
            device.findObjects(By.pkg(pkg).clickable(true))
        }.filter { item ->
            val bounds = item.visibleBounds
            bounds.width() >= 180 && bounds.height() >= 180 && bounds.top >= 0
        }.sortedWith(compareBy<UiObject2>({ it.visibleBounds.top }, { it.visibleBounds.left }))
        if (cells.isNotEmpty()) {
            cells.first().click()
            return
        }
        val hierarchy = File(InstrumentationRegistry.getInstrumentation().context.cacheDir, "picker_hierarchy.xml")
        device.dumpWindowHierarchy(hierarchy)
        throw AssertionError("System picker exposed no selectable photo cells; hierarchy=${hierarchy.absolutePath}")
    }

    private fun pickerPackages(): List<String> = listOf(
        "com.google.android.providers.media.module",
        "com.android.providers.media",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.huawei.photos",
        "com.huawei.photoshare",
        "com.huawei.filemanager",
    )

    private fun waitFor(device: UiDevice, timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(250L)
        }
        return condition()
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
        const val PRODUCTION_WAIT_MS = 420_000L
    }
}
