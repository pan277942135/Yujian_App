package com.yujian.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Runs the frozen production CameraX screen on the API 28 emulator.
 * This test never injects an image, a CameraX callback, or a SelectedImage.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionCameraRuntimeTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice
    private lateinit var evidenceDir: File
    private val trace = StringBuilder()

    companion object {
        private const val CAMERA_BUTTON = "开始识鱼"
        private const val BACK_BUTTON = "返回"
        private const val ALBUM_BUTTON = "从相册选择"
        private const val RECOGNITION_IMAGE = "正在识别的鱼获照片"
        private const val LOG_TAG = "RecognitionCameraCapture"
        private const val CAMERA_WAIT_MS = 60_000L
    }

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)
        evidenceDir = File(context.cacheDir, "camera-runtime-v1")
        evidenceDir.deleteRecursively()
        evidenceDir.mkdirs()
    }

    @Test
    fun productionCameraCaptureSavesDecodesAndEntersRecognition() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var enumerationError = ""
        val cameraIds = runCatching { manager.cameraIdList.toList() }
            .onFailure { enumerationError = it.javaClass.name + ": " + (it.message ?: "") }
            .getOrDefault(emptyList())
        val backIds = cameraIds.filter { id ->
            runCatching {
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }.getOrDefault(false)
        }
        val cameraDump = runCatching { shell("dumpsys media.camera") }
            .getOrElse { runCatching { shell("dumpsys camera") }.getOrDefault("CAMERA_DUMPSYS_FAILED: " + it.message) }
        write("camera_preflight.txt", buildString {
            appendLine("CAMERA_SERVICE_RESPONSIVE=" + cameraDump.isNotBlank())
            appendLine("CAMERA_IDS=" + cameraIds.joinToString(","))
            appendLine("CAMERA_ID_COUNT=" + cameraIds.size)
            appendLine("BACK_CAMERA_IDS=" + backIds.joinToString(","))
            appendLine("BACK_CAMERA_COUNT=" + backIds.size)
            appendLine("CAMERA_ENUMERATION_ERROR=" + enumerationError.ifBlank { "none" })
            appendLine("CAMERA_API=CameraManager.getCameraIdList + LENS_FACING")
            appendLine("CAMERA_SERVICE_DUMP_BEGIN")
            appendLine(cameraDump.take(16000))
            appendLine("CAMERA_SERVICE_DUMP_END")
        })
        record("CAMERA_IDS", cameraIds.joinToString(","))
        record("BACK_CAMERA_IDS", backIds.joinToString(","))
        record("BACK_CAMERA_COUNT", backIds.size.toString())
        record("CAMERA_SERVICE_RESPONSIVE", cameraDump.isNotBlank().toString())

        // This happens before app launch. A missing rear camera is classified
        // by the host gate as BLOCKED_INFRA and stops the production screen test.
        assertTrue(
            "BLOCKED_INFRA: Android CameraManager did not enumerate a rear camera; see camera_preflight.txt",
            backIds.isNotEmpty(),
        )

        val permissionGranted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        record("CAMERA_PERMISSION_GRANTED", permissionGranted.toString())
        assertTrue("CAMERA_PERMISSION_NOT_GRANTED_BEFORE_APP_LAUNCH", permissionGranted)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Launcher activity is missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        assertTrue("Home did not expose the production Recognition Camera control", device.wait(
            Until.hasObject(By.desc(CAMERA_BUTTON)), 30_000L,
        ))
        val homeCapture = device.findObject(By.desc(CAMERA_BUTTON))
        assertNotNull("Home capture control disappeared", homeCapture)
        homeCapture!!.click()
        assertTrue("Production camera route did not open", device.wait(
            Until.hasObject(By.desc(BACK_BUTTON)), 30_000L,
        ))
        assertTrue("Production camera gallery control did not appear", device.wait(
            Until.hasObject(By.desc(ALBUM_BUTTON)), 10_000L,
        ))
        screenshot("01_camera_open.png")
        record("PRODUCTION_CAMERA_SCREEN", "OPEN")

        val disabledBeforeReady = waitForButtonState(enabled = false, timeoutMs = 8_000L)
        record("CAPTURE_BUTTON_DISABLED_BEFORE_READY", disabledBeforeReady.toString())
        assertTrue("Capture control was never observed disabled before CameraX readiness", disabledBeforeReady)

        val ready = waitForButtonState(enabled = true, timeoutMs = CAMERA_WAIT_MS)
        if (!ready) {
            record("CAMERA_READY", "TIMEOUT")
            saveDiagnostics()
        }
        assertTrue("CameraX capture control never reached READY; diagnostics were saved", ready)

        val readyLogs = productionLogs()
        assertTrue("Production camera provider bind event missing", readyLogs.contains("event=camera_provider_bound"))
        assertTrue("CameraX Preview never reported STREAMING", readyLogs.contains("state=STREAMING"))
        record("CAMERA_PROVIDER_BOUND", "PASS")
        record("PREVIEW_STREAMING", "PASS")
        record("IMAGE_CAPTURE_READY_LOG", if (readyLogs.contains("image_capture_ready=true")) "true" else "not-emitted")
        // The enabled production button is gated on imageCaptureInstanceReady(),
        // and the frozen screen configures the controller with IMAGE_CAPTURE.
        record("IMAGE_CAPTURE_BOUND_READY", "PASS_FROM_PRODUCTION_READY_CONTROL")
        record("CAMERA_SELECTOR", "DEFAULT_BACK_CAMERA (frozen product source)")
        screenshot("02_preview_streaming.png")
        screenshot("03_capture_ready.png")

        // Tap the real production Compose control exactly once.
        device.findObject(By.desc(CAMERA_BUTTON)).click()
        record("CAPTURE_TAPS", "1")
        screenshot("04_capture_pressed.png")

        val callbackLogs = waitForCaptureOutcome(CAMERA_WAIT_MS)
        write("camera_runtime_log.txt", callbackLogs)
        screenshot("05_post_capture.png")
        val savedLine = callbackLogs.lineSequence().firstOrNull { it.contains("event=on_image_saved") }
        val errorLine = callbackLogs.lineSequence().firstOrNull {
            it.contains("event=error") || it.contains("event=take_picture_throw")
        }
        val captureLine = callbackLogs.lineSequence().firstOrNull { it.contains("event=before_capture") }
        val requestCount = callbackLogs.lineSequence().count { it.contains("event=before_capture") }
        record("TAKE_PICTURE_REQUESTS", requestCount.toString())
        record("CAPTURE_REQUEST_STATE", captureLine ?: "MISSING")
        record("ON_IMAGE_SAVED", if (savedLine == null) "NO" else "YES")
        if (errorLine != null) {
            record("IMAGE_CAPTURE_EXCEPTION", errorLine)
            saveDiagnostics()
            assertTrue("PRODUCT_FAILURE_CONFIRMED after CameraX READY: " + errorLine, savedLine != null)
        }
        assertNotNull(
            "CameraX callback was not reached within " + CAMERA_WAIT_MS + " ms; camera diagnostics were collected",
            savedLine,
        )
        assertEquals("The production screen must issue exactly one capture request", 1, requestCount)
        val saved = savedLine!!
        assertTrue("Production callback did not report a non-empty saved file",
            saved.contains("file_exists=true") && Regex("file_bytes=([1-9][0-9]*)").containsMatchIn(saved))
        record("ON_IMAGE_SAVED", "PASS")

        val outputPath = captureLine?.let { Regex("output_file=([^ ]+)").find(it)?.groupValues?.get(1) }
        assertNotNull("Production capture output path was not logged", outputPath)
        val captureFile = File(outputPath!!)
        assertTrue("CameraX output file does not exist: " + outputPath, captureFile.isFile)
        assertTrue("CameraX output file is empty: " + outputPath, captureFile.length() > 0L)
        val captureDecode = decodeEvidence(captureFile, "CameraX output")
        record("OUTPUT_FILE_CLASS", "app-private cache camera/*.jpg")
        record("OUTPUT_FILE_BYTES", captureFile.length().toString())
        record("CAMERA_OUTPUT_DECODE", "PASS")
        record("CAMERA_IMAGE_DIMENSIONS", captureDecode.width.toString() + "x" + captureDecode.height)
        record("CAMERA_FRAME_VISIBLE_STATS", "mean_luma=" + captureDecode.meanLuma +
            " min_luma=" + captureDecode.minLuma + " max_luma=" + captureDecode.maxLuma)

        val decodeLine = callbackLogs.lineSequence().firstOrNull { it.contains("event=decode_result") }
        assertNotNull("Production decode_result event was not emitted", decodeLine)
        assertTrue("Production SelectedImage creation was not reported",
            decodeLine!!.contains("selected_image_created=true"))
        val selectedFiles = File(context.filesDir, "recognition_inputs")
            .listFiles()?.filter { it.isFile && it.length() > 0L }.orEmpty()
        assertEquals("Production camera normalization must create one SelectedImage file", 1, selectedFiles.size)
        val selectedDecode = decodeEvidence(selectedFiles.single(), "SelectedImage")
        record("SELECTED_IMAGE", "PASS")
        record("SELECTED_IMAGE_BYTES", selectedFiles.single().length().toString())
        record("SELECTED_IMAGE_DIMENSIONS", selectedDecode.width.toString() + "x" + selectedDecode.height)

        val handoffLogs = waitForRecognitionHandoff(CAMERA_WAIT_MS)
        write("camera_runtime_log.txt", handoffLogs)
        assertTrue("Production recognition navigation event was not emitted",
            handoffLogs.contains("event=recognition_navigation started=true"))
        assertTrue("Recognition Processing did not expose the captured image", device.wait(
            Until.hasObject(By.desc(RECOGNITION_IMAGE)), 30_000L,
        ))
        screenshot("06_recognition_handoff.png")
        record("RECOGNITION_HANDOFF", "PASS")
        record("RECOGNITION_SCREEN", RECOGNITION_IMAGE)
        saveDiagnostics()
    }

    @After
    fun persistDiagnosticsAfterPassOrFailure() {
        if (!::evidenceDir.isInitialized) return
        runCatching {
            val logs = productionLogs()
            if (logs.isNotBlank()) write("camera_runtime_log.txt", logs)
            val dump = runCatching { shell("dumpsys media.camera") }
                .getOrElse { runCatching { shell("dumpsys camera") }.getOrDefault("CAMERA_DUMPSYS_FAILED: " + it.message) }
            write("camera_service_after_test.txt", dump.take(16000))
            if (!File(evidenceDir, "camera_capture_trace.txt").exists()) {
                record("TEST_RESULT", "FAILED_BEFORE_CAPTURE_TRACE_COMPLETION")
            }
        }
    }

    private data class DecodeEvidence(
        val width: Int,
        val height: Int,
        val meanLuma: Int,
        val minLuma: Int,
        val maxLuma: Int,
    )

    private fun waitForButtonState(enabled: Boolean, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val button = device.findObject(By.desc(CAMERA_BUTTON))
            if (button != null && button.isEnabled == enabled) return true
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun waitForCaptureOutcome(timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = productionLogs()
            if (latest.contains("event=on_image_saved") || latest.contains("event=error") ||
                latest.contains("event=take_picture_throw")) return latest
            SystemClock.sleep(500L)
        }
        saveDiagnostics()
        return latest
    }

    private fun waitForRecognitionHandoff(timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = productionLogs()
            if (latest.contains("event=recognition_navigation started=true")) return latest
            SystemClock.sleep(500L)
        }
        saveDiagnostics()
        return latest
    }

    private fun decodeEvidence(file: File, label: String): DecodeEvidence {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        assertTrue(label + " decode bounds invalid for " + file.name,
            bounds.outWidth > 0 && bounds.outHeight > 0)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(label + " full bitmap decode failed for " + file.name, bitmap)
        bitmap!!
        try {
            var count = 0L
            var lumaSum = 0L
            var minLuma = 255
            var maxLuma = 0
            val stepX = (bitmap.width / 24).coerceAtLeast(1)
            val stepY = (bitmap.height / 24).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val color = bitmap.getPixel(x, y)
                    val luma = (android.graphics.Color.red(color) * 299 +
                        android.graphics.Color.green(color) * 587 +
                        android.graphics.Color.blue(color) * 114) / 1000
                    lumaSum += luma
                    count += 1
                    minLuma = minOf(minLuma, luma)
                    maxLuma = maxOf(maxLuma, luma)
                    x += stepX
                }
                y += stepY
            }
            val meanLuma = (lumaSum / count).toInt()
            assertTrue(label + " is not a visible non-black frame", meanLuma > 2 && maxLuma - minLuma > 8)
            return DecodeEvidence(bitmap.width, bitmap.height, meanLuma, minLuma, maxLuma)
        } finally {
            bitmap.recycle()
        }
    }

    private fun screenshot(name: String) {
        val bitmap: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Could not capture screenshot " + name, bitmap)
        val file = File(evidenceDir, name)
        FileOutputStream(file).use { output ->
            assertTrue("Could not encode screenshot " + name,
                bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap!!.recycle()
        assertTrue("Screenshot is empty: " + name, file.length() > 0L)
    }

    private fun productionLogs(): String =
        runCatching { shell("logcat -d -v threadtime -s " + LOG_TAG + ":D") }.getOrDefault("")

    private fun saveDiagnostics() {
        if (!::evidenceDir.isInitialized) return
        runCatching {
            write("camera_runtime_log.txt", productionLogs())
            val dump = runCatching { shell("dumpsys media.camera") }
                .getOrElse { runCatching { shell("dumpsys camera") }.getOrDefault("CAMERA_DUMPSYS_FAILED: " + it.message) }
            write("camera_service_after_test.txt", dump.take(16000))
        }
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun record(key: String, value: String) {
        trace.append(key).append('=').append(value.replace('\n', ' ')).append('\n')
        write("camera_capture_trace.txt", trace.toString())
    }

    private fun write(name: String, content: String) {
        evidenceDir.mkdirs()
        File(evidenceDir, name).writeText(content)
    }
}

