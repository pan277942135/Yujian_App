package com.yujian.ai

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.RecognitionAttemptDeadline
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device-side attempt isolation and shared 60-second deadline guardrails. */
@RunWith(AndroidJUnit4::class)
class RecognitionResultHandoffGuardrailRuntimeTest {
    @Test
    fun staleAttemptIsRejectedAndModelStagesShareOneSixtySecondDeadline() {
        val oldIdentity = RecognitionSceneIdentity("session-old", "attempt-old", "image-old", "a", 1L)
        val currentIdentity = RecognitionSceneIdentity("session-current", "attempt-current", "image-current", "b", 2L)
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(oldIdentity, debugBuild = false, nowMs = 10L)
        controller.markPhotoFirstFramePresented(oldIdentity, 11L)
        controller.beginNewSession(currentIdentity, debugBuild = false, nowMs = 20L)
        val staleEvent = RecognitionResultHandoffEvent(
            identity = oldIdentity,
            maskSha256 = null,
            maskVersion = null,
            geometryVersion = 1L,
            pathId = null,
            pathGeometryVersion = null,
            pStarNormalizedSource = null,
            pathSegments = emptyList(),
            stage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            substage = ResultHandoffSubstage.NAME_STABLE,
            eventSequence = 1L,
            monotonicTimestampMs = 21L,
            eventProducer = "guardrail-test",
            source = "GLYPH",
            target = "RESULT_NAME",
            feedback = "NAME_STABLE",
            progress = 1f,
            renderStatus = "READY",
            resultIdentity = "yellow_catfish",
        )
        assertEquals("STALE_OR_MISMATCHED_ATTEMPT", controller.acceptResultHandoffEvent(staleEvent).rejectionReason)
        assertTrue(controller.acceptedResultHandoffEventsSnapshot().isEmpty())

        val deadline = RecognitionAttemptDeadline(startedAtUptimeMs = 100L)
        assertEquals(60_000L, deadline.remainingMs(100L))
        assertEquals(1L, deadline.remainingMs(60_099L))
        assertEquals(0L, deadline.remainingMs(60_100L))

        val report = JSONObject()
            .put("status", "PASS")
            .put("test", "StaleAttemptIsolationAndSharedDeadline")
            .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
            .put("capturedAtUptimeMs", SystemClock.uptimeMillis())
            .put("attemptIsolation", "PASS")
            .put("staleAttemptRejection", "STALE_OR_MISMATCHED_ATTEMPT")
            .put("deadlineMs", RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS)
            .put("deadlineExpiryBoundary", "PASS")
        val output = File(
            InstrumentationRegistry.getInstrumentation().context.cacheDir,
            "recognition-result-handoff/guardrail_test_report.json",
        )
        output.parentFile?.mkdirs()
        output.writeText(report.toString(2) + "\n")
    }
}
