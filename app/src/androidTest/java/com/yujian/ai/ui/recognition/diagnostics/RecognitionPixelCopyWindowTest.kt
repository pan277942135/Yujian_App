package com.yujian.ai.ui.recognition.diagnostics

import android.content.Intent
import android.view.PixelCopy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.MainActivity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecognitionPixelCopyWindowTest {
    @Test fun autoRecorderCapturesAndBindsRealActivityWindowWhileManualFrameIsSuppressed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        ) as MainActivity
        val recorder = RecognitionSessionEvidenceRecorder(context)
        val runId = UUID.randomUUID().toString()
        try {
            instrumentation.waitForIdleSync()
            waitForReadyWindow(activity)

            val manualIdentity = evidenceIdentity("$runId-manual")
            recorder.beginAttempt(manualIdentity)
            val manualFrame = sceneFrame(manualIdentity, ScenePlaybackMode.MANUAL)
            instrumentation.runOnMainSync {
                recorder.onFramePresented(activity.window, manualFrame, { manualFrame }, allowPixelCopyCapture = true)
            }
            assertTrue(recorder.flush(4_000L))
            val manualDir = requireNotNull(recorder.activeAttemptDirectory())
            val manualManifest = File(manualDir, "frames/pixelcopy_manifest.jsonl")
            assertTrue(!manualManifest.isFile || manualManifest.length() == 0L)
            val manualEvents = File(manualDir, "logs/recognition_events.jsonl").readLines().map(::JSONObject)
            assertTrue(manualEvents.any {
                it.optString("eventType") == "PIXELCOPY_SUPPRESSED_NON_AUTO_FRAME" &&
                    it.optString("sourceDomain") == "MANUAL"
            })
            recorder.finishAttempt(RecognitionAttemptTerminalState.CANCELLED, "INSTRUMENTATION_MANUAL_FRAME_CASE")
            assertTrue(recorder.flush(4_000L))

            val autoIdentity = evidenceIdentity("$runId-auto")
            recorder.beginAttempt(autoIdentity)
            val autoFrame = sceneFrame(autoIdentity, ScenePlaybackMode.AUTO)
            instrumentation.runOnMainSync {
                recorder.onFramePresented(activity.window, autoFrame, { autoFrame }, allowPixelCopyCapture = true)
            }
            val autoDir = requireNotNull(recorder.activeAttemptDirectory())
            val manifestFile = File(autoDir, "frames/pixelcopy_manifest.jsonl")
            val deadline = System.currentTimeMillis() + 8_000L
            var pixelRow: JSONObject? = null
            while (System.currentTimeMillis() < deadline && pixelRow == null) {
                pixelRow = runCatching {
                    manifestFile.readLines().map(::JSONObject).firstOrNull { it.optString("pixelCopyStatus") == "SUCCESS" }
                }.getOrNull()
                if (pixelRow == null) Thread.sleep(50L)
            }
            val captured = requireNotNull(pixelRow) { "AUTO recorder did not persist a successful real-window PixelCopy" }
            assertEquals("AUTO", captured.getString("sourceDomain"))
            assertEquals("BOUND", captured.getString("evidenceBindingStatus"))
            assertEquals("STAGE_ENTRY", captured.getString("captureType"))
            assertTrue(File(autoDir, captured.getString("relativePath")).isFile)
            assertEquals(RecognitionSceneKey.from(autoIdentity).value, captured.getString("sceneKey"))
            assertTrue(recorder.flush(6_000L))

            val autoEvents = File(autoDir, "logs/recognition_events.jsonl").readLines().map(::JSONObject)
            val captureId = captured.getString("captureId")
            val request = autoEvents.single { it.optString("eventType") == "PIXELCOPY_REQUESTED" && it.optString("captureId") == captureId }
            val saved = autoEvents.single { it.optString("eventType") == "PIXELCOPY_SAVED" && it.optString("captureId") == captureId }
            assertEquals("AUTO", request.getString("sourceDomain"))
            assertEquals(request.getString("sceneKey"), saved.getString("sceneKey"))
            assertTrue(request.getLong("evidenceSequence") < saved.getLong("evidenceSequence"))
        } finally {
            recorder.finishAttempt(RecognitionAttemptTerminalState.CANCELLED, "INSTRUMENTATION_CLEANUP")
            recorder.flush(4_000L)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun realWindowCaptureAndInjectedTimeoutAndPlatformErrorRemainExplicit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        ) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            val frame = RecognitionSceneCoordinator.frameForStage(
                identity = RecognitionSceneIdentity("pixelcopy-session", "pixelcopy-attempt", "photo-1", "a".repeat(64), 1L),
                sceneElapsedMs = 0L,
                opticalReferenceElapsedMs = 0L,
                geometryVersion = 77L,
                stage = RecognitionSceneStage.PHOTO_STABLE,
                stageElapsedMs = 0L,
                stageProgress = 1f,
                monotonicTimestampMs = android.os.SystemClock.uptimeMillis(),
            )
            waitForReadyWindow(activity)

            val realResult = AtomicReference<RecognitionPixelCopyResult?>()
            val realDone = CountDownLatch(1)
            instrumentation.runOnMainSync {
                RecognitionPixelCopyCapture.request(activity.window, frame, { frame }, timeoutMs = 4_000L) {
                    realResult.set(it)
                    realDone.countDown()
                }
            }
            assertTrue("real Android Window PixelCopy callback timed out", realDone.await(6, TimeUnit.SECONDS))
            val captured = requireNotNull(realResult.get())
            assertEquals("SUCCESS", captured.status)
            assertTrue(captured.sameSceneIdentity)
            assertNotNull(captured.bitmap)
            captured.bitmap?.recycle()

            val errorResult = runInjectedCapture(activity, frame, timeoutMs = 1_500L) { _, _, _, callback ->
                callback(PixelCopy.ERROR_SOURCE_NO_DATA)
            }
            assertEquals("PIXELCOPY_ERROR", errorResult.status)

            val backendCalled = CountDownLatch(1)
            val timeoutResult = AtomicReference<RecognitionPixelCopyResult?>()
            val timeoutDone = CountDownLatch(1)
            instrumentation.runOnMainSync {
                RecognitionPixelCopyCapture.requestWithBackend(
                    window = activity.window,
                    frame = frame,
                    currentFrame = { frame },
                    timeoutMs = 1_000L,
                    backend = RecognitionPixelCopyRequestBackend { _, _, _, _ -> backendCalled.countDown() },
                ) {
                    timeoutResult.set(it)
                    timeoutDone.countDown()
                }
            }
            assertTrue("timeout fault backend was not reached", backendCalled.await(2, TimeUnit.SECONDS))
            assertTrue("PixelCopy timeout callback was not delivered", timeoutDone.await(2, TimeUnit.SECONDS))
            assertEquals("PIXELCOPY_TIMEOUT", requireNotNull(timeoutResult.get()).status)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun runInjectedCapture(
        activity: MainActivity,
        frame: com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame,
        timeoutMs: Long,
        backend: RecognitionPixelCopyRequestBackend,
    ): RecognitionPixelCopyResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<RecognitionPixelCopyResult?>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            RecognitionPixelCopyCapture.requestWithBackend(
                window = activity.window,
                frame = frame,
                currentFrame = { frame },
                timeoutMs = timeoutMs,
                backend = backend,
            ) {
                result.set(it)
                done.countDown()
            }
        }
        assertTrue("injected PixelCopy callback timed out", done.await(timeoutMs + 2_000L, TimeUnit.MILLISECONDS))
        return requireNotNull(result.get())
    }

    private fun waitForReadyWindow(activity: MainActivity) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (activity.window.decorView.isAttachedToWindow && activity.window.decorView.hasWindowFocus() &&
                activity.window.decorView.width > 0 && activity.window.decorView.height > 0
            ) return
            Thread.sleep(50L)
        }
        throw AssertionError("MainActivity Window did not become ready for PixelCopy")
    }

    private fun evidenceIdentity(label: String) = RecognitionEvidenceIdentity(
        sessionId = "pixelcopy-session-$label",
        attemptId = "pixelcopy-attempt-$label",
        imageId = "photo-$label",
        photoSha256 = sha256("photo-bytes-$label"),
        photoWidthPx = 640,
        photoHeightPx = 480,
        orientationDegrees = 0,
    )

    private fun sceneFrame(identity: RecognitionEvidenceIdentity, playbackMode: ScenePlaybackMode) =
        RecognitionSceneCoordinator.frameForStage(
            identity = RecognitionSceneIdentity(
                identity.sessionId,
                identity.attemptId,
                identity.imageId,
                identity.photoSha256,
                1L,
            ),
            sceneElapsedMs = 0L,
            opticalReferenceElapsedMs = 0L,
            geometryVersion = 77L,
            stage = RecognitionSceneStage.PHOTO_STABLE,
            stageElapsedMs = 0L,
            stageProgress = 1f,
            playbackMode = playbackMode,
            monotonicTimestampMs = android.os.SystemClock.uptimeMillis(),
        )
}
