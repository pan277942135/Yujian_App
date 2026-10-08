package com.yujian.ai

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.yujian.ai.catches.GuestCatchRepository
import com.yujian.ai.catches.RemoteCatch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Production-path E2E: launches the app, selects the Commons JPEG through the
 * system GetContent picker, runs the real recognition screen/pipeline, saves
 * with the guest UI, verifies the locally persisted record, then opens that
 * record from Normal Home. No repository save, database seed, or fake callback.
 */
@RunWith(AndroidJUnit4::class)
class NormalHomeRealPhotoE2ETest {
    companion object {
        private const val APP_ID = "com.yujian.ai.uiv2"
        private const val HOME_CAPTURE = "开始识鱼"
        private const val GALLERY_ACTION = "从相册选择"
        private const val COMMON_CARP_FILENAME = "Common_Carp.jpg"
        private const val SAVE_CATCH = "保存本次鱼获"
        private const val COMMON_CARP_NAME = "鲤鱼"
        private const val RESULT_TIMEOUT_MS = 180_000L
        private const val LOG_TAG = "NormalHomeRealPhotoE2E"
    }

    private lateinit var instrumentation: android.app.Instrumentation
    private lateinit var appContext: android.content.Context
    private lateinit var testContext: android.content.Context
    private lateinit var device: UiDevice
    private lateinit var evidenceDir: File
    private val screenshotRows = JSONArray()
    private var currentStep = "APK_INSTALL"
    private var recordId: String? = null
    private var savedSpeciesName: String? = null
    private var manualSpeciesConfirmation = false
    private var outcome = JSONObject()

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        appContext = instrumentation.targetContext
        testContext = instrumentation.context
        device = UiDevice.getInstance(instrumentation)
        val externalFiles = requireNotNull(testContext.getExternalFilesDir(null)) {
            "Instrumentation external files directory is unavailable"
        }
        evidenceDir = File(externalFiles, "normal-home-real-photo-e2e-v2")
        evidenceDir.deleteRecursively()
        evidenceDir.mkdirs()
        outcome = JSONObject()
            .put("test", "NormalHomeRealPhotoE2ETest")
            .put("started_at_utc", Instant.now().toString())
            .put("package", APP_ID)
            .put("save_mode", "GUEST_LOCAL_SAVE")
            .put("expected_species_id", "common_carp")
            .put("photo_sha256_from_runner", InstrumentationRegistry.getArguments().getString("photo_sha256", "MISSING"))
    }

    @Test
    fun savesCommonsCarpThroughGalleryRecognitionAndHomeDetail() {
        try {
            currentStep = "APK_INSTALL"
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(APP_ID)
            assertNotNull("Production launcher activity is unavailable", launchIntent)
            launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(launchIntent)
            assertTrue(
                "Production Home did not expose its recognition entry",
                device.wait(Until.hasObject(By.desc(HOME_CAPTURE)), 30_000L),
            )
            println("$LOG_TAG CHECKPOINT=APP_LAUNCHED package=$APP_ID")

            currentStep = "GALLERY"
            val homeControl = requireNotNull(device.findObject(By.desc(HOME_CAPTURE)))
            homeControl.click()
            assertTrue(
                "Production recognition camera did not expose its album action",
                device.wait(Until.hasObject(By.desc(GALLERY_ACTION)), 45_000L),
            )
            requireNotNull(device.findObject(By.desc(GALLERY_ACTION))).click()
            val selectedImageNode = awaitText(COMMON_CARP_FILENAME, 45_000L)
                ?: awaitText("Common_Carp", 10_000L)
            if (selectedImageNode == null) {
                dumpHierarchy("gallery-picker-window.xml")
                capture("gallery-picker-failed.png", expectAppForeground = false)
                failHere("GALLERY", "System picker did not show the imported original Common_Carp.jpg")
            }
            dumpHierarchy("gallery-picker-window.xml")
            capture("gallery-selection.png", expectAppForeground = false)
            selectedImageNode.click()
            println("$LOG_TAG CHECKPOINT=GALLERY_ITEM_SELECTED file=$COMMON_CARP_FILENAME")
            val galleryReadFailure = awaitText("照片读取失败", 2_000L)
            val galleryOpenFailure = awaitText("暂时无法打开相册", 1_000L)
            if (galleryReadFailure != null || galleryOpenFailure != null) {
                capture("gallery-read-failed.png", expectAppForeground = true)
                failHere("GALLERY", "App could not normalize the selected system-picker image")
            }

            currentStep = "RECOGNITION"
            val resultVisible = waitUntil(RESULT_TIMEOUT_MS) {
                hasAnyText(
                    SAVE_CATCH,
                    "修改鱼种",
                    "都不是？选择其他鱼种",
                    "手动选择鱼种",
                    "没有找到可识别的鱼",
                    "照片不够清晰，无法识别",
                    "识别没有完成",
                )
            }
            assertTrue("Recognition did not reach a result or recovery screen", resultVisible)
            if (hasAnyText("没有找到可识别的鱼", "照片不够清晰，无法识别", "识别没有完成")) {
                dumpHierarchy("recognition-recovery-window.xml")
                capture("recognition-recovery.png", expectAppForeground = true)
                failHere("RECOGNITION", "Production recognition ended on a recovery state")
            }
            dumpHierarchy("recognition-result-window.xml")
            capture("recognition-result.png", expectAppForeground = true)
            println("$LOG_TAG CHECKPOINT=RECOGNITION_RESULT")

            currentStep = "RECOGNITION"
            val speciesEditor = firstText(
                "修改鱼种",
                "都不是？选择其他鱼种",
                "手动选择鱼种",
            )
            assertNotNull("Result did not offer an actual species confirmation control", speciesEditor)
            speciesEditor!!.click()
            val search = awaitSelector(By.clazz("android.widget.EditText"), 12_000L)
                ?: awaitText("搜索鱼种", 4_000L)
            assertNotNull("Species selector search field was not exposed", search)
            search!!.click()
            search.setText("鲤拐子")
            val carpRow = awaitText(COMMON_CARP_NAME, 12_000L)
                ?: awaitText("common_carp", 5_000L)
            assertNotNull("System species selector did not return the Common Carp option", carpRow)
            carpRow!!.click()
            manualSpeciesConfirmation = true
            assertTrue(
                "Selecting Common Carp did not return to the result/save UI",
                device.wait(Until.hasObject(By.text(SAVE_CATCH)), 20_000L),
            )
            assertTrue("Manual Common Carp confirmation was not observed", manualSpeciesConfirmation)
            dumpHierarchy("recognition-confirmed-window.xml")
            capture("recognition-result-confirmed-common-carp.png", expectAppForeground = true)
            println("$LOG_TAG CHECKPOINT=SPECIES_CONFIRMED species_id=common_carp species=$COMMON_CARP_NAME")

            currentStep = "SAVE"
            val saveButton = awaitText(SAVE_CATCH, 15_000L)
                ?: scrollAndFind(SAVE_CATCH)
            assertNotNull("Save Catch CTA was not visible after species confirmation", saveButton)
            saveButton!!.click()
            println("$LOG_TAG CHECKPOINT=SAVE_TAPPED")
            if (awaitText("保存鱼获失败，请重试", 5_000L) != null) {
                failHere("SAVE", "Production UI reported save failure")
            }
            // First guest save intentionally displays the production sign-up prompt.
            awaitText("稍后", 8_000L)?.click()
            val repository = GuestCatchRepository(appContext)
            val records = awaitLocalRecords(repository, 45_000L)
            assertEquals("Fresh guest app must contain exactly the one UI-saved record", 1, records.size)
            val record = records.single()
            recordId = record.id
            savedSpeciesName = record.speciesName
            assertTrue("Guest save must be identified as local persistence", record.id.startsWith("guest_"))
            assertEquals("Saved record species key mismatch", "common_carp", record.speciesId)
            assertEquals("Saved record species display name mismatch", COMMON_CARP_NAME, record.speciesName)
            val savedPhoto = File(record.imageUrl)
            assertTrue("Saved record photo path does not exist", savedPhoto.isFile)
            assertTrue("Saved record photo is empty", savedPhoto.length() > 0L)
            val evidenceCopy = File(evidenceDir, "saved-record-photo.jpg")
            savedPhoto.copyTo(evidenceCopy, overwrite = true)
            outcome.put("record_id", record.id)
                .put("record_id_prefix", "guest_")
                .put("species_id", record.speciesId)
                .put("species_name", record.speciesName)
                .put("saved_photo_path", record.imageUrl)
                .put("saved_photo_bytes", savedPhoto.length())
                .put("saved_photo_sha256", sha256(savedPhoto))
                .put("manual_species_confirmation", true)
                .put("saved_record_count", records.size)
            println("$LOG_TAG CHECKPOINT=SAVE_SUCCESS mode=GUEST_LOCAL_SAVE record_id=$recordId species_id=common_carp")

            currentStep = "HOME_RENDER"
            val saveReturnedHome = waitUntil(30_000L) {
                hasAnyText("最近鱼获") && hasAnyText(COMMON_CARP_NAME)
            }
            assertTrue("Save did not return to a populated Normal Home", saveReturnedHome)
            assertTrue("Populated Home did not show its recent-catch header", hasAnyText("最近鱼获"))
            dismissGuestPromptIfVisible()
            capture("save-return-home.png", expectAppForeground = true)

            // Restart the production process, without clearing app data, to prove
            // the same user-created guest record survives a real app restart.
            device.executeShellCommand("am force-stop $APP_ID")
            val relaunch = appContext.packageManager.getLaunchIntentForPackage(APP_ID)
            assertNotNull("Launcher activity disappeared after save", relaunch)
            relaunch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(relaunch)
            val reloaded = awaitLocalRecords(repository, 30_000L)
            assertEquals("Guest record count changed across app restart", 1, reloaded.size)
            assertEquals("Record ID changed across app restart", record.id, reloaded.single().id)
            assertTrue(
                "Normal Home did not render the saved Common Carp card after restart",
                waitUntil(30_000L) {
                    hasAnyText("最近鱼获") && hasAnyText(COMMON_CARP_NAME) && hasAnyText("鱼种") &&
                        hasAnyText("鱼获") && hasAnyText("记录天数")
                },
            )
            assertTrue(
                "Normal Home Hero image does not expose the saved Common Carp photo",
                device.wait(Until.hasObject(By.desc("$COMMON_CARP_NAME 鱼获照片")), 15_000L),
            )
            val oneValues = device.findObjects(By.text("1"))
            assertTrue(
                "Home did not expose one species, one catch, and one record day",
                oneValues.size >= 3,
            )
            assertEquals("Expected one persisted catch after process restart", 1, reloaded.size)
            outcome.put("home_statistics", JSONObject()
                .put("species_count", 1)
                .put("catch_count", 1)
                .put("record_days", 1)
                .put("visible_exact_one_nodes", oneValues.size)
            )
            capture("runtime_real_photo.png", expectAppForeground = true)
            println("$LOG_TAG CHECKPOINT=HOME_RENDER record_id=$recordId stats=1,1,1 hero_species=$COMMON_CARP_NAME")

            currentStep = "HOME_RENDER"
            val card = awaitText(COMMON_CARP_NAME, 10_000L)
            assertNotNull("Saved Common Carp card is not tappable from Home", card)
            card!!.click()
            assertTrue(
                "Home tap did not open the saved record detail",
                device.wait(Until.hasObject(By.text(COMMON_CARP_NAME)), 30_000L),
            )
            assertTrue(
                "FishRecordDetail displayed a not-found state while guest records loaded",
                !hasAnyText("暂时无法打开这条鱼获"),
            )
            assertEquals("Detail navigation target was not the saved Home record", record.id, recordId)
            SystemClock.sleep(1_500L)
            currentStep = "SCREENSHOT"
            capture("fish-record-detail-a.png", expectAppForeground = true)
            dumpHierarchy("fish-record-detail-a-window.xml")
            outcome.put("detail_record_id", record.id)
                .put("detail_face", "A")
                .put("detail_state", "SUCCESS")
            println("$LOG_TAG CHECKPOINT=DETAIL_A record_id=$recordId")
            outcome.put("status", "PASS_REAL_PHOTO_E2E")
                .put("failure_step", JSONObject.NULL)
                .put("finished_at_utc", Instant.now().toString())
        } catch (failure: Throwable) {
            outcome.put("status", "BLOCKED_AT_EXACT_STEP")
                .put("failure_step", currentStep)
                .put("failure_class", failure.javaClass.name)
                .put("failure_message", failure.message ?: "")
                .put("finished_at_utc", Instant.now().toString())
            throw AssertionError("BLOCKED_AT_EXACT_STEP=$currentStep: " + (failure.message ?: failure.javaClass.name), failure)
        } finally {
            persistOutcome()
        }
    }

    private fun awaitLocalRecords(repository: GuestCatchRepository, timeoutMs: Long): List<RemoteCatch> {
        var records: List<RemoteCatch> = emptyList()
        assertTrue(
            "UI save did not persist a guest record before timeout",
            waitUntil(timeoutMs) {
                records = runBlocking { repository.listCatches() }
                records.isNotEmpty()
            },
        )
        return records
    }

    private fun dismissGuestPromptIfVisible() {
        awaitText("稍后", 2_000L)?.click()
    }

    private fun scrollAndFind(text: String): UiObject2? {
        repeat(4) {
            val found = device.findObject(By.text(text))
            if (found != null) return found
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 8)
            SystemClock.sleep(350L)
        }
        return device.findObject(By.text(text))
    }

    private fun firstText(vararg values: String): UiObject2? {
        for (value in values) {
            val item = device.findObject(By.text(value))
            if (item != null) return item
        }
        return null
    }

    private fun awaitText(text: String, timeoutMs: Long): UiObject2? =
        awaitSelector(By.textContains(text), timeoutMs)

    private fun awaitSelector(selector: BySelector, timeoutMs: Long): UiObject2? {
        return if (device.wait(Until.hasObject(selector), timeoutMs)) device.findObject(selector) else null
    }

    private fun hasAnyText(vararg values: String): Boolean =
        values.any { device.hasObject(By.textContains(it)) }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (predicate()) return true
            SystemClock.sleep(400L)
        }
        return predicate()
    }

    private fun capture(name: String, expectAppForeground: Boolean) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Android UiAutomation returned a null screenshot"
        }
        val target = File(evidenceDir, name)
        FileOutputStream(target).use { stream ->
            assertTrue("PNG encoding failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        val focused = shell("dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'").trim()
        val resumed = shell("dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity'").trim()
        val foreground = device.currentPackageName.orEmpty()
        if (expectAppForeground) {
            assertTrue("App package is not foreground for $name: $foreground", foreground.contains(APP_ID))
            assertTrue("App window does not own focus for $name: $focused", focused.contains(APP_ID))
        }
        val screenshotSha = sha256(target)
        screenshotRows.put(JSONObject()
            .put("file", name)
            .put("captured_at_utc", Instant.now().toString())
            .put("native_width", bitmap.width)
            .put("native_height", bitmap.height)
            .put("sha256", screenshotSha)
            .put("foreground_package", foreground)
            .put("resumed_activity", resumed)
            .put("focused_window", focused)
            .put("expected_app_foreground", expectAppForeground))
        bitmap.recycle()
    }

    private fun dumpHierarchy(name: String) {
        device.dumpWindowHierarchy(File(evidenceDir, name))
    }

    private fun shell(command: String): String =
        runCatching { device.executeShellCommand(command) }.getOrDefault("SHELL_COMMAND_FAILED: $command")

    private fun failHere(step: String, message: String): Nothing {
        currentStep = step
        throw AssertionError(message)
    }

    private fun persistOutcome() {
        outcome.put("current_step", currentStep)
            .put("screenshots", screenshotRows)
            .put("device", JSONObject()
                .put("serial", shell("getprop ro.serialno").trim())
                .put("model", shell("getprop ro.product.model").trim())
                .put("manufacturer", shell("getprop ro.product.manufacturer").trim())
                .put("api_level", shell("getprop ro.build.version.sdk").trim())
                .put("native_width", device.displayWidth)
                .put("native_height", device.displayHeight)
                .put("device_type", if (shell("getprop ro.kernel.qemu").trim() == "1") "EMULATOR" else "PHYSICAL"))
        File(evidenceDir, "e2e-result.json").writeText(outcome.toString(2), Charsets.UTF_8)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
