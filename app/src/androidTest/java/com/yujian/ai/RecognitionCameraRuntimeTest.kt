package com.yujian.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.yujian.ai.ui.identify.RecognitionFirstCaptureGuidePage
import com.yujian.ai.ui.identify.RecognitionFirstCaptureGuideStore
import com.yujian.ai.ui.identify.mapRecognitionGuideReferenceSpace
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val guidePages = JSONArray()
    private lateinit var guideStore: RecognitionFirstCaptureGuideStore

    companion object {
        private const val HOME_CAMERA_BUTTON = "开始识鱼"
        private const val SHUTTER_BUTTON = "拍摄"
        private const val BACK_BUTTON = "返回"
        private const val ALBUM_BUTTON = "从相册选择"
        private const val GUIDE_SKIP_BUTTON = "跳过首次拍照教学"
        private const val GUIDE_NEXT_BUTTON = "下一张教学示例"
        private const val GUIDE_PREVIOUS_BUTTON = "上一张教学示例"
        private const val GUIDE_START_BUTTON = "开始拍摄，返回拍摄页面"
        private const val RECOGNITION_IMAGE = "正在识别的鱼获照片"
        private const val RECOGNITION_NO_FISH = "没有找到可识别的鱼"
        private const val RECOGNITION_IMAGE_QUALITY = "照片不够清晰，无法识别"
        private const val RECOGNITION_TECHNICAL_FAILURE = "识别没有完成"
        private const val LOG_TAG = "RecognitionCameraCapture"
        private const val CAMERA_WAIT_MS = 60_000L
        private const val CAPTURE_INVOCATION_WAIT_MS = 10_000L
    }

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)
        guideStore = RecognitionFirstCaptureGuideStore(context)
        context.getSharedPreferences(
            RecognitionFirstCaptureGuideStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        while (guidePages.length() > 0) guidePages.remove(guidePages.length() - 1)
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
            Until.hasObject(By.desc(HOME_CAMERA_BUTTON)), 30_000L,
        ))
        val homeCapture = device.findObject(By.desc(HOME_CAMERA_BUTTON))
        assertNotNull("Home capture control disappeared", homeCapture)
        homeCapture!!.click()
        assertTrue("First-use guide did not appear after the production CameraX preview became READY", device.wait(
            Until.hasObject(By.desc(GUIDE_SKIP_BUTTON)), 30_000L,
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
        record("CAMERA_READY", "PASS")
        assertTrue("Production camera provider was not bound", readySnapshot.providerBound)
        assertTrue("CameraX Preview was not STREAMING", readySnapshot.previewStreaming)
        assertTrue("Production ImageCapture readiness was not reported", readySnapshot.imageCaptureReady)
        record("CAMERA_PROVIDER_BOUND", "PASS")
        record("PREVIEW_STREAMING", "PASS")
        record("IMAGE_CAPTURE_BOUND", "PASS_FROM_PRODUCTION_READY_EVENT")
        record("IMAGE_CAPTURE_READY_LOG", "true")
        record(
            "CAPTURE_BUTTON_ENABLED_AT_READY",
            if (readySnapshot.guideVisible) "BLOCKED_BY_FIRST_USE_GUIDE" else "PASS",
        )
        record("CAMERA_SELECTOR", "DEFAULT_BACK_CAMERA (frozen product source)")

        val ready = exerciseProductionFirstUseGuide()
        assertTrue("Guide Start did not return to a ready production shutter", ready.isReady)
        assertFalse("Guide should be dismissed after Start", ready.guideVisible)
        assertTrue("Guide Start must persist completion", guideStore.isCompleted())
        record("FIRST_USE_GUIDE_START", "PASS_NO_SHUTTER_REQUEST")
        screenshot("02_preview_streaming.png")
        screenshot("03_capture_ready.png")

        // Tap the real production Compose control exactly once, only after all
        // production READY signals and the enabled UI control have been verified.
        val captureButtonAtReady = device.findObject(By.desc(SHUTTER_BUTTON))
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
        assertTrue(
            "PreviewView/controller must remain alive while capture is CAPTURING",
            captureLine != null && captureLine.contains("capture_state=CAPTURING") &&
                captureLine.contains("camera_provider_bound=true") &&
                captureLine.contains("camera_controller_alive=true") &&
                captureLine.contains("preview_view_attached=true") &&
                captureLine.contains("preview_attached=true") &&
                captureLine.contains("controller_attached=true"),
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
            assertTrue("Capture error must leave the app on the Camera route",
                !device.hasObject(By.desc(RECOGNITION_IMAGE)))
            val cameraWasHealthy = errorLine.contains("camera_ready_for_retry=true")
            if (cameraWasHealthy) {
                assertTrue("Healthy CameraX must remain retryable after onError", device.wait(
                    Until.findObject(By.desc(SHUTTER_BUTTON)), 5_000L,
                )?.isEnabled == true)
            }
            record("CAMERA_ERROR_RECOVERY", if (cameraWasHealthy) "RETRYABLE_NO_NAVIGATION" else "REBIND_REQUESTED_NO_NAVIGATION")
            saveDiagnostics()
            throw AssertionError("PRODUCT_FAILURE_CONFIRMED after READY and takePicture invocation: " + errorLine)
        }
        assertTrue(
            "PreviewView/controller must stay attached from before_capture through the terminal callback",
            captureLine?.contains("preview_attached=true") == true &&
                captureLine?.contains("controller_attached=true") == true &&
                captureLineIndex >= 0 && terminalLineIndex > captureLineIndex &&
                !detachedWhileCaptureActive && !controllerDisposedWhileCaptureActive,
        )
        assertNotNull("onImageSaved was not reached after the terminal result", savedLine)
        val saved = savedLine!!
        assertTrue("Terminal callback must report the live PreviewView and controller",
            terminalStateLine?.contains("camera_controller_alive=true") == true &&
                terminalStateLine.contains("preview_view_attached=true") &&
                terminalStateLine.contains("preview_attached=true") &&
                terminalStateLine.contains("controller_attached=true"))
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
        assertTrue(
            "SelectedImage pixels must be normalized; EXIF may be normal or undefined",
            selectedOrientation == ExifInterface.ORIENTATION_NORMAL ||
                selectedOrientation == ExifInterface.ORIENTATION_UNDEFINED,
        )
        record(
            "ORIENTATION_NORMALIZATION",
            if (selectedOrientation == ExifInterface.ORIENTATION_NORMAL) {
                "PASS_EXIF_NORMAL"
            } else {
                "PASS_NORMALIZED_PIXELS_EXIF_UNDEFINED"
            },
        )
        record("SOURCE_ROTATION_DEGREES", rotationDegrees.toString())
        record("SELECTED_IMAGE", "PASS")
        record("SELECTED_IMAGE_BYTES", selectedFiles.single().length().toString())
        record("SELECTED_IMAGE_DIMENSIONS", selectedDecode.width.toString() + "x" + selectedDecode.height)
        record("SELECTED_IMAGE_FRAME_STATS", "mean_luma=" + selectedDecode.meanLuma +
            " min_luma=" + selectedDecode.minLuma + " max_luma=" + selectedDecode.maxLuma)

        assertTrue("Production recognition navigation event was not emitted",
            handoffLogs.contains(requestPrefix + "event=recognition_navigation started=true"))
        val recognitionDestination = waitForRecognitionDestination(CAMERA_WAIT_MS)
        if (recognitionDestination == null || recognitionDestination == "technical_failure") {
            saveUiHierarchy("ui_hierarchy_recognition_handoff_failure.xml")
            screenshot("06_recognition_handoff.png")
            record("RECOGNITION_HANDOFF", "FAIL_DESTINATION_NOT_ACCEPTABLE")
            record("RECOGNITION_SCREEN", recognitionDestination ?: "NOT_OBSERVED")
            saveDiagnostics()
            throw AssertionError(
                "Recognition did not reach the processing image or an accepted No Fish/Image Quality screen; " +
                    "observed=" + recognitionDestination,
            )
        }
        screenshot("06_recognition_handoff.png")
        record("RECOGNITION_HANDOFF", "PASS")
        record("RECOGNITION_SCREEN", recognitionDestination)
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
        val guideVisible: Boolean,
        val productionLogs: String,
    ) {
        val isReady: Boolean
            get() = providerBound && previewStreaming && imageCaptureReady &&
                captureButtonPresent && captureButtonEnabled

        fun captureButtonState(): String = when {
            guideVisible -> "guide_overlay_ready"
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
        val guideVisible = device.findObject(By.desc(GUIDE_SKIP_BUTTON)) != null
        val button = device.findObject(By.desc(SHUTTER_BUTTON))
        return ReadySnapshot(
            providerBound = providerBound,
            previewStreaming = previewStreaming,
            imageCaptureReady = imageCaptureReady,
            captureButtonPresent = button != null || guideVisible,
            captureButtonEnabled = button?.isEnabled == true || guideVisible,
            guideVisible = guideVisible,
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
        val button = device.findObject(By.desc(SHUTTER_BUTTON))
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

    private fun waitForRecognitionDestination(timeoutMs: Long): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            when {
                device.findObject(By.desc(RECOGNITION_IMAGE)) != null ->
                    return "PROCESSING_IMAGE_VISIBLE"
                device.findObject(By.text(RECOGNITION_NO_FISH)) != null ->
                    return "NO_FISH"
                device.findObject(By.text(RECOGNITION_IMAGE_QUALITY)) != null ->
                    return "IMAGE_QUALITY"
                device.findObject(By.text(RECOGNITION_TECHNICAL_FAILURE)) != null ->
                    return "TECHNICAL_FAILURE"
            }
            SystemClock.sleep(150L)
        }
        return null
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

    private fun exerciseProductionFirstUseGuide(): ReadySnapshot {
        val firstReady = waitForProductionReady(CAMERA_WAIT_MS)
        assertNotNull("CameraX never reached READY", firstReady)
        assertTrue("Guide must be gated on permission + live CameraX READY", waitForGuideVisible(10_000L))
        assertFalse("Fresh app data must show the first-use guide", guideStore.isCompleted())
        assertTrue("P01 must be the first page", device.wait(
            Until.hasObject(By.text("竖屏拍摄示意")), 10_000L,
        ))
        assertEquals("No shutter request may precede guide action", 0, countCaptureRequests(productionLogs()))
        assertEquals("Shutter must be removed while guide is visible", null, device.findObject(By.desc(SHUTTER_BUTTON)))
        assertEquals("Gallery must be removed while guide is visible", null, device.findObject(By.desc(ALBUM_BUTTON)))
        record("FIRST_USE_GUIDE_CAMERA_READY_TRIGGER", "PASS")
        record("FIRST_USE_GUIDE_TOUCH_ISOLATION", "PASS_SHUTTER_AND_GALLERY_NOT_COMPOSED")

        captureGuidePage(
            page = RecognitionFirstCaptureGuidePage.P01,
            title = "竖屏拍摄示意",
            actionDescription = GUIDE_NEXT_BUTTON,
            screenshotName = "P01_runtime.png",
        )

        // A system Back closes the overlay without writing completion.
        device.pressBack()
        assertTrue("System Back did not close the guide", waitForGuideHidden(10_000L))
        assertFalse("System Back must not persist completion", guideStore.isCompleted())
        assertEquals("System Back must not trigger capture", 0, countCaptureRequests(productionLogs()))
        record("FIRST_USE_GUIDE_SYSTEM_BACK", "DISMISSED_WITHOUT_COMPLETION")

        val cameraBack = device.findObject(By.desc(BACK_BUTTON))
        assertNotNull("Camera route Back control disappeared after guide dismissal", cameraBack)
        cameraBack!!.click()
        assertTrue("Camera route did not return to Home", device.wait(
            Until.hasObject(By.desc(HOME_CAMERA_BUTTON)), 10_000L,
        ))
        device.findObject(By.desc(HOME_CAMERA_BUTTON))!!.click()
        assertTrue("Camera route did not reopen", device.wait(
            Until.hasObject(By.desc(GUIDE_SKIP_BUTTON)), 30_000L,
        ))
        val reopened = waitForProductionReady(CAMERA_WAIT_MS)
        assertNotNull("Reopened camera did not become READY", reopened)
        assertTrue("Dismissal must show the guide again on re-entry", waitForGuideVisible(10_000L))
        assertFalse("Re-entry before completion must not mark the guide completed", guideStore.isCompleted())
        assertTrue("Re-entered guide did not start on P01", device.wait(
            Until.hasObject(By.text("竖屏拍摄示意")), 10_000L,
        ))

        device.findObject(By.desc(GUIDE_NEXT_BUTTON))!!.click()
        assertTrue("P01 next did not show P02", device.wait(
            Until.hasObject(By.text("竖拍鱼类拍摄示意")), 10_000L,
        ))
        captureGuidePage(
            page = RecognitionFirstCaptureGuidePage.P02,
            title = "竖拍鱼类拍摄示意",
            actionDescription = GUIDE_NEXT_BUTTON,
            screenshotName = "P02_runtime.png",
        )
        device.findObject(By.desc(GUIDE_PREVIOUS_BUTTON))!!.click()
        assertTrue("P02 previous did not return to P01", device.wait(
            Until.hasObject(By.text("竖屏拍摄示意")), 10_000L,
        ))
        device.findObject(By.desc(GUIDE_NEXT_BUTTON))!!.click()
        assertTrue("P01 next did not return to P02", device.wait(
            Until.hasObject(By.text("竖拍鱼类拍摄示意")), 10_000L,
        ))
        device.findObject(By.desc(GUIDE_NEXT_BUTTON))!!.click()
        assertTrue("P02 next did not show P03", device.wait(
            Until.hasObject(By.text("横屏拍摄示意")), 10_000L,
        ))
        captureGuidePage(
            page = RecognitionFirstCaptureGuidePage.P03,
            title = "横屏拍摄示意",
            actionDescription = GUIDE_START_BUTTON,
            screenshotName = "P03_runtime.png",
        )
        assertEquals("Guide navigation must not invoke the shutter", 0, countCaptureRequests(productionLogs()))
        device.findObject(By.desc(GUIDE_START_BUTTON))!!.click()
        assertTrue("Final Start did not return to the live camera shutter", device.wait(
            Until.hasObject(By.desc(SHUTTER_BUTTON)), 10_000L,
        ))
        assertTrue("Final Start must persist completion", guideStore.isCompleted())
        assertTrue("Guide did not dismiss after final Start", waitForGuideHidden(5_000L))
        assertEquals("Final Start must not invoke capture", 0, countCaptureRequests(productionLogs()))
        record("FIRST_USE_GUIDE_NAVIGATION", "P01_TO_P02_TO_P03_PASS")
        writeFirstUseGuideRuntimeReport()

        return waitForProductionReady(CAMERA_WAIT_MS)
            ?: throw AssertionError("Camera shutter did not remain ready after guide completion")
    }

    private fun captureGuidePage(
        page: RecognitionFirstCaptureGuidePage,
        title: String,
        actionDescription: String,
        screenshotName: String,
    ) {
        assertTrue("Guide page title did not become visible on ${page.name}", device.wait(
            Until.hasObject(By.text(title)), 10_000L,
        ))
        val titleNode = device.findObject(By.text(title))
        val actionNode = device.findObject(By.desc(actionDescription))
        assertNotNull("Guide title missing on ${page.name}", titleNode)
        assertNotNull("Guide action missing on ${page.name}", actionNode)
        // Let the 180 ms entrance and Pager transition settle before taking
        // the production-route visual evidence frame.
        SystemClock.sleep(250L)
        screenshot(screenshotName)
        val screenshotFile = File(evidenceDir, screenshotName)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(screenshotFile.absolutePath, options)
        val mapping = mapRecognitionGuideReferenceSpace(page, options.outWidth, options.outHeight)
        val windowBounds = appWindowBounds()
        val titleScreenBounds = titleNode!!.visibleBounds
        val actionScreenBounds = actionNode!!.visibleBounds
        guidePages.put(
            JSONObject()
                .put("page", page.name)
                .put("screenshot", screenshotName)
                .put("screenshot_px", JSONObject().put("width", options.outWidth).put("height", options.outHeight))
                .put("boundsInWindow", JSONObject()
                    .put("page_title", rectInWindowJson(titleScreenBounds, windowBounds))
                    .put("primary_action", rectInWindowJson(actionScreenBounds, windowBounds)))
                .put("runtime_coordinates_px", JSONObject()
                    .put("page_title", rectJson(titleScreenBounds))
                    .put("primary_action", rectJson(actionScreenBounds)))
                .put("frozen_reference_coordinates_px", JSONObject()
                    .put("page_title", referenceRectJson(titleScreenBounds, mapping))
                    .put("primary_action", referenceRectJson(actionScreenBounds, mapping)))
                .put("reference_to_runtime", JSONObject()
                    .put("reference_width_px", mapping.referenceWidthPx)
                    .put("reference_height_px", mapping.referenceHeightPx)
                    .put("uniform_scale", mapping.uniformScale)
                    .put("offset_x_px", mapping.offsetXPx)
                    .put("offset_y_px", mapping.offsetYPx)
                    .put("runtime_width_px", mapping.runtimeWidthPx)
                    .put("runtime_height_px", mapping.runtimeHeightPx))
                .put("visual_parity", "BLOCKED_ASSET_SOURCE"),
        )
        record("FIRST_USE_GUIDE_${page.name}_SCREENSHOT", "PASS_REAL_IDENTIFY_ROUTE")
    }

    private fun writeFirstUseGuideRuntimeReport() {
        val appApk = File(context.applicationInfo.sourceDir)
        val report = JSONObject()
            .put("schema_version", "1.0")
            .put("task_id", "RECOGNITION-FIRST-USE-GUIDE-V1")
            .put("status", "ASSET_SOURCE_BLOCKED")
            .put("runtime_evidence_status", "REAL_CAMERAX_IDENTIFY_ROUTE")
            .put("runtime_route_evidence", "PASS")
            .put("visual_parity", "BLOCKED_ASSET_SOURCE")
            .put("source_commit_sha", JSONObject.NULL)
            .put("apk_sha256", sha256(appApk))
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api_level", Build.VERSION.SDK_INT)
            .put("viewport_px", JSONObject().put("width", device.displayWidth).put("height", device.displayHeight))
            .put("density", context.resources.displayMetrics.density)
            .put("font_scale", context.resources.configuration.fontScale)
            .put("safe_insets_px", safeInsetsJson())
            .put("pages", guidePages)
            .put("camera_ready_gate", "permission_granted_and_CameraX_preview_STREAMING_and_ImageCapture_ready")
            .put("shutter_invocation_count_while_guide_visible", 0)
            .put("completion_persisted_by", "P03_START")
            .put("independent_photo_sources", "ASSET_SOURCE_BLOCKED")
            .put("visual_observations", JSONArray()
                .put("Screenshots were captured from the production IdentifyScreen after CameraX reported READY.")
                .put("The guide overlays the live camera route and removes the underlying shutter and Gallery controls while visible.")
                .put("Independent page-specific hero and error-example photographs are unavailable; visual parity is not accepted.")
                .put("Frozen full-page PNGs were not used as runtime artwork."))
            .put("pass", false)
            .put("failure_classification", "ASSET_SOURCE_BLOCKED")
        write("first_use_camera_guide_runtime_report.json", report.toString(2) + "\n")
    }

    private fun waitForGuideHidden(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device.findObject(By.desc(GUIDE_SKIP_BUTTON)) == null) return true
            SystemClock.sleep(100L)
        }
        return device.findObject(By.desc(GUIDE_SKIP_BUTTON)) == null
    }

    private fun waitForGuideVisible(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device.findObject(By.desc(GUIDE_SKIP_BUTTON)) != null) return true
            SystemClock.sleep(100L)
        }
        return device.findObject(By.desc(GUIDE_SKIP_BUTTON)) != null
    }

    private fun appWindowBounds(): Rect {
        val window = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return if (window == null) {
            Rect(0, 0, device.displayWidth, device.displayHeight)
        } else {
            Rect().also(window::getBoundsInScreen)
        }
    }

    private fun rectJson(bounds: Rect): JSONObject = JSONObject()
        .put("left", bounds.left)
        .put("top", bounds.top)
        .put("right", bounds.right)
        .put("bottom", bounds.bottom)

    private fun rectInWindowJson(bounds: Rect, window: Rect): JSONObject = JSONObject()
        .put("left", bounds.left - window.left)
        .put("top", bounds.top - window.top)
        .put("right", bounds.right - window.left)
        .put("bottom", bounds.bottom - window.top)

    private fun referenceRectJson(bounds: Rect, mapping: com.yujian.ai.ui.identify.RecognitionGuideReferenceMapping): JSONObject = JSONObject()
        .put("left", (bounds.left - mapping.offsetXPx) / mapping.uniformScale)
        .put("top", (bounds.top - mapping.offsetYPx) / mapping.uniformScale)
        .put("right", (bounds.right - mapping.offsetXPx) / mapping.uniformScale)
        .put("bottom", (bounds.bottom - mapping.offsetYPx) / mapping.uniformScale)

    private fun safeInsetsJson(): JSONObject {
        fun systemDimension(name: String): Int {
            val id = context.resources.getIdentifier(name, "dimen", "android")
            return if (id == 0) 0 else context.resources.getDimensionPixelSize(id)
        }
        return JSONObject()
            .put("left", 0)
            .put("top", systemDimension("status_bar_height"))
            .put("right", 0)
            .put("bottom", systemDimension("navigation_bar_height"))
            .put("source", "API 28 Android system dimension resources; Pixel_2 emulator profile")
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
