package com.yujian.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
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
        private const val CAPTURE_INVOCATION_WAIT_MS = 10_000L
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

        val initialReadiness = readReadySnapshot()
        val preReadyDisabledObserved = !initialReadiness.isReady &&
            initialReadiness.captureButtonPresent && !initialReadiness.captureButtonEnabled
        record("PRE_READY_DISABLED_STATE_OBSERVED", preReadyDisabledObserved.toString())
        record("PRE_READY_OBSERVATION_REQUIRED", "false")
        if (!initialReadiness.isReady) {
            val prematureRequests = countCaptureRequests(initialReadiness.productionLogs)
            record("PRE_READY_CAPTURE_REQUEST_COUNT", prematureRequests.toString())
            assertEquals("Production issued a capture request before READY", 0, prematureRequests)
        }

        val readySnapshot = if (initialReadiness.isReady) {
            initialReadiness
        } else {
            waitForProductionReady(CAMERA_WAIT_MS)
        }
        if (readySnapshot == null) {
            record("CAMERA_READY", "TIMEOUT")
            saveReadinessFailureDiagnostics(initialReadiness)
            throw AssertionError(
                "CameraX READY timeout; provider=" + initialReadiness.providerBound +
                    " previewStreaming=" + initialReadiness.previewStreaming +
                    " imageCaptureReady=" + initialReadiness.imageCaptureReady +
                    " captureButton=" + initialReadiness.captureButtonState(),
            )
        }
        val ready = readySnapshot
        record("CAMERA_READY", "PASS")
        assertTrue("Production camera provider was not bound", ready.providerBound)
        assertTrue("CameraX Preview was not STREAMING", ready.previewStreaming)
        assertTrue("Production ImageCapture readiness was not reported", ready.imageCaptureReady)
        assertTrue("Production capture control was not enabled at READY", ready.captureButtonEnabled)
        record("CAMERA_PROVIDER_BOUND", "PASS")
        record("PREVIEW_STREAMING", "PASS")
        record("IMAGE_CAPTURE_BOUND", "PASS_FROM_PRODUCTION_READY_EVENT")
        record("IMAGE_CAPTURE_READY_LOG", "true")
        record("CAPTURE_BUTTON_ENABLED_AT_READY", "PASS")
        record("CAMERA_SELECTOR", "DEFAULT_BACK_CAMERA (frozen product source)")
        screenshot("02_preview_streaming.png")
        screenshot("03_capture_ready.png")

        // Tap the real production Compose control exactly once, only after all
        // production READY signals and the enabled UI control have been verified.
        val captureButtonAtReady = device.findObject(By.desc(CAMERA_BUTTON))
        assertNotNull("Production capture control disappeared at READY", captureButtonAtReady)
        assertTrue("Production capture control is disabled at READY", captureButtonAtReady!!.isEnabled)
        captureButtonAtReady.click()
        record("CAPTURE_TAPS", "1")
        screenshot("04_capture_pressed.png")

        val invocationLogs = waitForCaptureInvocation(CAPTURE_INVOCATION_WAIT_MS)
        val invocationCount = countCaptureRequests(invocationLogs)
        record("TAKE_PICTURE_REQUESTS", invocationCount.toString())
        val captureLine = invocationLogs.lineSequence().firstOrNull { it.contains("event=before_capture") }
        val requestId = captureLine?.let { Regex("request=([^ ]+)").find(it)?.groupValues?.get(1) }
        record("CAPTURE_REQUEST_STATE", captureLine ?: "MISSING")
        record("CAPTURE_REQUEST_ID", requestId ?: "MISSING")
        record(
            "PREVIEW_ATTACHED_AT_CAPTURE_REQUEST",
            (captureLine?.contains("preview_attached=true") == true).toString(),
        )
        record(
            "CAMERA_CONTROLLER_ATTACHED_AT_CAPTURE_REQUEST",
            (captureLine?.contains("controller_attached=true") == true).toString(),
        )
        if (invocationCount == 0) {
            record("TAKE_PICTURE_INVOKED", "false")
            saveCaptureInvocationFailureDiagnostics(invocationLogs)
            throw AssertionError(
                "FAIL_RUNTIME_HARNESS: one capture tap was executed, but no production before_capture " +
                    "event proving ImageCapture.takePicture() was invoked was observed",
            )
        }
        assertEquals("The production screen must issue exactly one capture request", 1, invocationCount)
        record("TAKE_PICTURE_INVOKED", "true")

        val callbackLogs = waitForCaptureOutcome(CAMERA_WAIT_MS, requestId!!)
        write("camera_runtime_log.txt", callbackLogs)
        screenshot("05_post_capture.png")
        val requestPrefix = "request=$requestId "
        val savedLine = callbackLogs.lineSequence().firstOrNull {
            it.contains(requestPrefix) && it.contains("event=on_image_saved")
        }
        val errorLine = callbackLogs.lineSequence().firstOrNull {
            it.contains(requestPrefix) &&
                (it.contains("event=error") || it.contains("event=take_picture_throw"))
        }
        val terminalLines = callbackLogs.lineSequence().filter {
            it.contains(requestPrefix) && (
                it.contains("event=on_image_saved") || it.contains("event=error") ||
                    it.contains("event=take_picture_throw")
            )
        }.toList()
        record(
            "TERMINAL_CALLBACK",
            when {
                savedLine != null -> "onImageSaved"
                errorLine != null -> "onError_or_takePictureThrow"
                else -> "timeout"
            },
        )
        record("TERMINAL_CALLBACK_COUNT", terminalLines.size.toString())
        if (terminalLines.isEmpty()) {
            saveDiagnostics()
            throw AssertionError(
                "No terminal CameraX capture result arrived within " + CAMERA_WAIT_MS +
                    " ms after takePicture(); camera and production diagnostics were saved",
            )
        }
        assertEquals("Exactly one terminal CameraX capture result is expected", 1, terminalLines.size)
        val callbackLogLines = callbackLogs.lineSequence().toList()
        val captureLineIndex = callbackLogLines.indexOfFirst {
            it.contains(requestPrefix) && it.contains("event=before_capture")
        }
        val terminalLineIndex = callbackLogLines.indexOfFirst {
            it.contains(requestPrefix) && (
                it.contains("event=on_image_saved") || it.contains("event=error") ||
                    it.contains("event=take_picture_throw")
            )
        }
        val detachedWhileCaptureActive = callbackLogLines.any {
            it.contains(requestPrefix) && it.contains("event=capture_surface_state") &&
                it.contains("preview_attached=false")
        }
        val controllerDisposedWhileCaptureActive = callbackLogLines.any {
            it.contains(requestPrefix) && it.contains("event=camera_controller_dispose") &&
                it.contains("teardown_during_capture=true")
        }
        record("PREVIEW_ATTACHED_THROUGH_TERMINAL", (
            captureLine?.contains("preview_attached=true") == true &&
                captureLine?.contains("controller_attached=true") == true &&
                captureLineIndex >= 0 && terminalLineIndex > captureLineIndex &&
                !detachedWhileCaptureActive && !controllerDisposedWhileCaptureActive
            ).toString())
        record("PREVIEW_DETACHED_DURING_CAPTURE", detachedWhileCaptureActive.toString())
        record("CAMERA_CONTROLLER_DISPOSED_DURING_CAPTURE", controllerDisposedWhileCaptureActive.toString())
        assertTrue(
            "PreviewView/controller must stay attached from before_capture through the terminal callback",
            captureLine?.contains("preview_attached=true") == true &&
                captureLine?.contains("controller_attached=true") == true &&
                captureLineIndex >= 0 && terminalLineIndex > captureLineIndex &&
                !detachedWhileCaptureActive && !controllerDisposedWhileCaptureActive,
        )
        val terminalStateLine = callbackLogLines.firstOrNull {
            it.contains(requestPrefix) && it.contains("event=terminal_callback")
        }
        record("CAMERA_STATE_AT_TERMINAL_CALLBACK", terminalStateLine ?: "NOT_REPORTED")
        val recoveryStateLine = callbackLogLines.firstOrNull {
            it.contains(requestPrefix) && it.contains("event=capture_recovery_state")
        }
        if (recoveryStateLine != null) record("CAMERA_RECOVERY_STATE", recoveryStateLine)
        if (errorLine != null) {
            record("IMAGE_CAPTURE_EXCEPTION", errorLine)
            record("IMAGE_CAPTURE_ERROR_CODE", Regex("image_capture_error=([^ ]+)").find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            record("IMAGE_CAPTURE_ERROR_MESSAGE", Regex("message=(.*?) cause_class=")
                .find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            record("IMAGE_CAPTURE_CAUSE_CLASS", Regex("cause_class=([^ ]+)").find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            record("IMAGE_CAPTURE_CAUSE_MESSAGE", Regex("cause_message=(.*?) (?:output_file_exists|temporary_file_cleanup)=")
                .find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            record("CAPTURE_STATE_AT_ERROR", "ERROR after READY request; preview=STREAMING; image_capture_ready=true")
            record("OUTPUT_FILE_EXISTS_AT_ERROR", Regex("output_file_exists=(true|false)")
                .find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            record("OUTPUT_FILE_BYTES_AT_ERROR", Regex("output_file_bytes=([0-9]+)")
                .find(errorLine)?.groupValues?.get(1) ?: "NOT_REPORTED")
            val failedOutputPath = captureLine?.let {
                Regex("output_file=([^ ]+)").find(it)?.groupValues?.get(1)
            }
            val failedOutputFile = failedOutputPath?.let(::File)
            record("OUTPUT_PATH_CLASS", "app-private cache camera/*.jpg")
            record("OUTPUT_PATH", failedOutputPath ?: "NOT_REPORTED")
            record("OUTPUT_FILE_EXISTS", (failedOutputFile?.isFile == true).toString())
            record("OUTPUT_FILE_BYTES", (failedOutputFile?.takeIf { it.isFile }?.length() ?: 0L).toString())
            saveDiagnostics()
            throw AssertionError("PRODUCT_FAILURE_CONFIRMED after READY and takePicture invocation: " + errorLine)
        }
        assertNotNull("onImageSaved was not reached after the terminal result", savedLine)
        val saved = savedLine!!
        assertTrue("Production callback did not report a non-empty saved file",
            saved.contains("file_exists=true") && Regex("file_bytes=([1-9][0-9]*)").containsMatchIn(saved))
        record("ON_IMAGE_SAVED", "PASS")

        val outputPath = captureLine?.let { Regex("output_file=([^ ]+)").find(it)?.groupValues?.get(1) }
        assertNotNull("Production capture output path was not logged", outputPath)
        val outputBytes = Regex("file_bytes=([1-9][0-9]*)").find(saved)?.groupValues?.get(1)
        assertNotNull("onImageSaved did not report a positive output byte length", outputBytes)
        record("OUTPUT_FILE_CLASS", "app-private cache camera/*.jpg")
        record("OUTPUT_PATH", outputPath!!)
        record("OUTPUT_FILE_EXISTS", "YES_AT_ON_IMAGE_SAVED")
        record("OUTPUT_FILE_BYTES", outputBytes!!)

        // Production normalizes and deletes its temporary CameraX file after
        // decoding it. Wait for the real production handoff, then validate its
        // decode diagnostics and the normalized SelectedImage file.
        val handoffLogs = waitForRecognitionHandoff(CAMERA_WAIT_MS, requestId!!)
        write("camera_runtime_log.txt", handoffLogs)
        val decodeLine = handoffLogs.lineSequence().firstOrNull {
            it.contains(requestPrefix) && it.contains("event=decode_result")
        }
        assertNotNull("Production decode_result event was not emitted", decodeLine)
        assertTrue("Production CameraX output did not decode successfully",
            decodeLine!!.contains("decode_success=true"))
        assertTrue("Production SelectedImage creation was not reported",
            decodeLine.contains("selected_image_created=true"))
        val decodedWidth = Regex("decoded_width=([1-9][0-9]*)")
            .find(decodeLine)?.groupValues?.get(1)?.toIntOrNull()
        val decodedHeight = Regex("decoded_height=([1-9][0-9]*)")
            .find(decodeLine)?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull("Production decode width was not positive", decodedWidth)
        assertNotNull("Production decode height was not positive", decodedHeight)
        record("CAMERA_OUTPUT_DECODE", "PASS_PRODUCTION")
        record("CAMERA_IMAGE_DIMENSIONS", decodedWidth.toString() + "x" + decodedHeight)

        val selectedFiles = File(context.filesDir, "recognition_inputs")
            .listFiles()?.filter { it.isFile && it.length() > 0L }.orEmpty()
        assertEquals("Production camera normalization must create one SelectedImage file", 1, selectedFiles.size)
        val selectedDecode = decodeEvidence(selectedFiles.single(), "SelectedImage")
        assertEquals("SelectedImage width does not match production's normalized decode width",
            decodedWidth, selectedDecode.width)
        assertEquals("SelectedImage height does not match production's normalized decode height",
            decodedHeight, selectedDecode.height)
        val rotationDegrees = Regex("rotation_degrees=(-?[0-9]+)")
            .find(decodeLine)?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull("Production camera orientation diagnostic was not emitted", rotationDegrees)
        assertTrue("Unexpected camera rotation diagnostic: " + rotationDegrees,
            rotationDegrees == 0 || rotationDegrees == 90 ||
                rotationDegrees == 180 || rotationDegrees == 270)
        val selectedOrientation = ExifInterface(selectedFiles.single()).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        assertEquals("SelectedImage must have normalized pixel orientation",
            ExifInterface.ORIENTATION_NORMAL, selectedOrientation)
        record("ORIENTATION_NORMALIZATION", "PASS")
        record("SOURCE_ROTATION_DEGREES", rotationDegrees.toString())
        record("SELECTED_IMAGE", "PASS")
        record("SELECTED_IMAGE_BYTES", selectedFiles.single().length().toString())
        record("SELECTED_IMAGE_DIMENSIONS", selectedDecode.width.toString() + "x" + selectedDecode.height)
        record("SELECTED_IMAGE_FRAME_STATS", "mean_luma=" + selectedDecode.meanLuma +
            " min_luma=" + selectedDecode.minLuma + " max_luma=" + selectedDecode.maxLuma)

        assertTrue("Production recognition navigation event was not emitted",
            handoffLogs.contains(requestPrefix + "event=recognition_navigation started=true"))
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

    private data class ReadySnapshot(
        val providerBound: Boolean,
        val previewStreaming: Boolean,
        val imageCaptureReady: Boolean,
        val captureButtonPresent: Boolean,
        val captureButtonEnabled: Boolean,
        val productionLogs: String,
    ) {
        val isReady: Boolean
            get() = providerBound && previewStreaming && imageCaptureReady &&
                captureButtonPresent && captureButtonEnabled

        fun captureButtonState(): String = when {
            !captureButtonPresent -> "missing"
            captureButtonEnabled -> "enabled"
            else -> "disabled"
        }
    }

    private fun readReadySnapshot(): ReadySnapshot {
        val logs = productionLogs()
        val lines = logs.lineSequence().toList()
        val providerBound = lines.any {
            it.contains("event=camera_provider_bound") && it.contains("bound=true")
        }
        val previewStreaming = lines.any {
            it.contains("event=preview_stream_state") && it.contains("state=STREAMING")
        }
        val imageCaptureReady = lines.any {
            it.contains("event=preview_stream_state") && it.contains("image_capture_ready=true")
        }
        val button = device.findObject(By.desc(CAMERA_BUTTON))
        return ReadySnapshot(
            providerBound = providerBound,
            previewStreaming = previewStreaming,
            imageCaptureReady = imageCaptureReady,
            captureButtonPresent = button != null,
            captureButtonEnabled = button?.isEnabled == true,
            productionLogs = logs,
        )
    }

    private fun waitForProductionReady(timeoutMs: Long): ReadySnapshot? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest = readReadySnapshot()
        while (SystemClock.elapsedRealtime() < deadline) {
            if (latest.isReady) return latest
            SystemClock.sleep(100L)
            latest = readReadySnapshot()
        }
        return latest.takeIf { it.isReady }
    }

    private fun countCaptureRequests(logs: String): Int =
        logs.lineSequence().count { it.contains("event=before_capture") }

    private fun waitForCaptureInvocation(timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = productionLogs()
            if (latest.contains("event=before_capture") ||
                latest.contains("event=capture_blocked") ||
                latest.contains("event=take_picture_throw")
            ) return latest
            SystemClock.sleep(100L)
        }
        return latest
    }

    private fun saveReadinessFailureDiagnostics(initial: ReadySnapshot) {
        val latest = readReadySnapshot()
        write(
            "readiness_failure_diagnostics.txt",
            "INITIAL_READY=" + initial.isReady + "\n" +
                "INITIAL_PROVIDER_BOUND=" + initial.providerBound + "\n" +
                "INITIAL_PREVIEW_STREAMING=" + initial.previewStreaming + "\n" +
                "INITIAL_IMAGE_CAPTURE_READY=" + initial.imageCaptureReady + "\n" +
                "INITIAL_CAPTURE_BUTTON=" + initial.captureButtonState() + "\n" +
                "LATEST_PROVIDER_BOUND=" + latest.providerBound + "\n" +
                "LATEST_PREVIEW_STREAMING=" + latest.previewStreaming + "\n" +
                "LATEST_IMAGE_CAPTURE_READY=" + latest.imageCaptureReady + "\n" +
                "LATEST_CAPTURE_BUTTON=" + latest.captureButtonState() + "\n" +
                latest.productionLogs,
        )
        saveUiHierarchy("ui_hierarchy_readiness_failure.xml")
        saveDiagnostics()
    }

    private fun saveCaptureInvocationFailureDiagnostics(logs: String) {
        val button = device.findObject(By.desc(CAMERA_BUTTON))
        val state = if (button == null) "missing" else if (button.isEnabled) "enabled" else "disabled"
        write(
            "capture_invocation_failure.txt",
            "FAIL_RUNTIME_HARNESS=TAKE_PICTURE_NOT_OBSERVED_AFTER_TAP\n" +
                "CAPTURE_BUTTON_STATE_AFTER_TAP=" + state + "\n" + logs,
        )
        saveUiHierarchy("ui_hierarchy_after_capture_tap.xml")
        saveDiagnostics()
    }

    private fun saveUiHierarchy(name: String) {
        val file = File(evidenceDir, name)
        runCatching { device.dumpWindowHierarchy(file) }
            .onFailure { write(name.replace(".xml", "_error.txt"), it.javaClass.name + ": " + it.message) }
    }

    private fun waitForCaptureOutcome(timeoutMs: Long, requestId: String): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val requestPrefix = "request=$requestId "
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = productionLogs()
            val hasTerminal = latest.lineSequence().any { line ->
                line.contains(requestPrefix) && (
                    line.contains("event=on_image_saved") || line.contains("event=error") ||
                        line.contains("event=take_picture_throw")
                )
            }
            if (hasTerminal) return latest
            SystemClock.sleep(500L)
        }
        saveDiagnostics()
        return latest
    }

    private fun waitForRecognitionHandoff(timeoutMs: Long, requestId: String): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val handoffEvent = "request=$requestId event=recognition_navigation started=true"
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = productionLogs()
            if (latest.contains(handoffEvent)) return latest
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

