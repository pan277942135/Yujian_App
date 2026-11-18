package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbCrystalRenderResultTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "image", "photo-sha", 3L)
    private val frame = RecognitionSceneCoordinator.frameForStage(
        identity = identity,
        sceneElapsedMs = 700L,
        opticalReferenceElapsedMs = 700L,
        geometryVersion = 9L,
        stage = RecognitionSceneStage.ORB_WAKE,
        stageElapsedMs = 100L,
        stageProgress = .25f,
        eventSequence = 4L,
    )

    @Test fun completeRenderAndRealPresentAcceptsGlOwner() {
        val result = successfulResult(frame, sequence = 8L).withPresentation(
            RenderPassStatus.PASS,
            presentedSequence = 8L,
        )

        assertTrue(result.ownerAccepted)
        assertEquals(OrbRenderFallbackStatus.RELEASED, result.fallbackStatus)
        assertEquals(null, result.failureReason)
        assertTrue(OrbRenderOwnershipPolicy.accept(result, frame))
    }

    @Test fun productionPresentationGateNeverSwapsAnUnreadyStage3aFrame() {
        val result = successfulResult(frame, sequence = 8L)
            .copy(coreTextureStatus = RenderPassStatus.NOT_READY, coreAlphaStatus = RenderPassStatus.NOT_READY)
        val decision = GlFramePresentationGate.beforeSwap(GlFrameExecution(RenderPassStatus.PASS, result), frame)
        assertFalse(decision.maySwap)
        assertEquals("PASS_NOT_READY:coreTextureStatus,coreAlphaStatus", decision.rejectionReason)

        val completed = GlFramePresentationGate.afterSwap(
            result = result,
            swapStatus = RenderPassStatus.NOT_RUN,
            presentedSequence = null,
            failureReason = decision.rejectionReason,
        )
        assertFalse(completed.ownerAccepted)
        assertEquals(RenderPassStatus.NOT_RUN, completed.eglSwapStatus)
        assertEquals(OrbRenderFallbackStatus.ACTIVE, completed.fallbackStatus)
    }

    @Test fun productionPresentationGateAcceptsOnlyCompleteFrameThenRealSwap() {
        val result = successfulResult(frame, sequence = 23L)
        val decision = GlFramePresentationGate.beforeSwap(GlFrameExecution(RenderPassStatus.PASS, result), frame, 23L)
        assertTrue(decision.maySwap)
        val completed = GlFramePresentationGate.afterSwap(
            result = result,
            swapStatus = RenderPassStatus.PASS,
            presentedSequence = result.frameSequence,
        )
        assertTrue(completed.ownerAccepted)
        assertEquals(23L, completed.presentedFrameSequence)

        val mismatchedSequence = GlFramePresentationGate.beforeSwap(
            GlFrameExecution(RenderPassStatus.PASS, result),
            frame,
            expectedFrameSequence = 24L,
        )
        assertFalse(mismatchedSequence.maySwap)
        assertEquals("FRAME_SEQUENCE_MISMATCH", mismatchedSequence.rejectionReason)

        val missingReceipt = GlFramePresentationGate.beforeSwap(
            GlFrameExecution(RenderPassStatus.PASS),
            expectedFrame = frame,
            expectedFrameSequence = 25L,
            requireResult = true,
        )
        assertFalse(missingReceipt.maySwap)
        assertEquals("RENDER_RESULT_MISSING", missingReceipt.rejectionReason)
    }

    @Test fun stage3aUnimplementedFrozenInputsRemainNotReadyAndKeepComposeFallback() {
        val result = successfulResult(frame, sequence = 9L)
            .copy(coreTextureStatus = RenderPassStatus.NOT_READY, coreAlphaStatus = RenderPassStatus.NOT_READY)
            .withPresentation(RenderPassStatus.PASS, presentedSequence = 9L)

        assertFalse(result.ownerAccepted)
        assertEquals(OrbRenderFallbackStatus.ACTIVE, result.fallbackStatus)
        assertEquals("PASS_NOT_READY:coreTextureStatus,coreAlphaStatus", result.failureReason)
        assertFalse(OrbRenderOwnershipPolicy.accept(result, frame))
    }

    @Test fun eachRequiredPassFailureRejectsFirstFrameAndRetainsFallback() {
        val failingPasses = listOf(
            "coreTextureStatus", "coreAlphaStatus", "shaderCompileLinkStatus", "backFboStatus", "residentBackStatus", "coreFluidStatus",
            "refractionStatus", "r01bMaskStatus", "r02DropletStatus", "r03LightStatus",
            "r05CausticsStatus", "residentFrontStatus", "bloomExtractStatus", "bloomBlurHStatus",
            "bloomBlurVStatus", "compositeStatus",
        )

        failingPasses.forEach { passName ->
            val result = statusWithFailure(passName).withPresentation(RenderPassStatus.PASS, 10L)
            assertFalse("$passName must reject GL ownership", result.ownerAccepted)
            assertEquals(OrbRenderFallbackStatus.ACTIVE, result.fallbackStatus)
            assertTrue("$passName failure reason=${result.failureReason}", result.failureReason!!.contains(passName))
        }
    }

    @Test fun eglSwapFailureOrMissingPresentationRejectsFrame() {
        val swapFailed = successfulResult(frame, 11L).withPresentation(RenderPassStatus.FAIL, null)
        val notPresented = successfulResult(frame, 12L).withPresentation(RenderPassStatus.NOT_RUN, null)

        assertEquals("EGL_SWAP_FAIL", swapFailed.failureReason)
        assertEquals("EGL_SWAP_NOT_RUN", notPresented.failureReason)
        assertFalse(swapFailed.ownerAccepted)
        assertFalse(notPresented.ownerAccepted)
    }

    @Test fun contextLossAndSurfaceFailureKeepFallbackAndPreserveCause() {
        val contextLost = successfulResult(frame, 31L).withPresentation(
            RenderPassStatus.FAIL,
            presentedSequence = null,
            hostFailureReason = "EGL_CONTEXT_LOST",
        )
        val surfaceLost = successfulResult(frame, 32L).withPresentation(
            RenderPassStatus.NOT_RUN,
            presentedSequence = null,
            hostFailureReason = "SURFACE_LOST",
        )
        assertFalse(contextLost.ownerAccepted)
        assertEquals("EGL_CONTEXT_LOST", contextLost.failureReason)
        assertFalse(surfaceLost.ownerAccepted)
        assertEquals("SURFACE_LOST", surfaceLost.failureReason)
    }

    @Test fun hostFrameReceiptSequenceIsMonotonicAndIndependentOfSceneClock() {
        val sceneClockBefore = frame.sceneElapsedMs
        val first = GlRenderFrameSequence.next()
        val second = GlRenderFrameSequence.next()
        assertTrue(second > first)
        assertEquals(sceneClockBefore, frame.sceneElapsedMs)
    }

    @Test fun mismatchedIdentityAndStaleFrameResultsCannotTakeOrRetainOwnership() {
        val valid = successfulResult(frame, 20L).withPresentation(RenderPassStatus.PASS, 20L)
        val ownership = OrbRenderOwnershipState()
        assertTrue(ownership.apply(valid, frame))

        val staleFailure = successfulResult(frame, 19L)
            .copy(compositeStatus = RenderPassStatus.FAIL)
            .withPresentation(RenderPassStatus.FAIL, null)
        assertTrue(ownership.apply(staleFailure, frame))

        val otherAttemptFrame = frame.copy(identity = frame.identity.copy(attemptId = "attempt-2"))
        assertTrue(ownership.apply(valid, otherAttemptFrame))

        val rejectedLatest = successfulResult(frame, 21L)
            .copy(eglSwapStatus = RenderPassStatus.FAIL)
            .withPresentation(RenderPassStatus.FAIL, null)
        assertFalse(ownership.apply(rejectedLatest, frame))
    }

    @Test fun diagnosticPayloadIncludesEveryRequiredPassAndNoRawPhotoIdentity() {
        val result = successfulResult(frame, 22L).withPresentation(RenderPassStatus.PASS, 22L)
        val payload = result.diagnosticFields()

        listOf(
            "frameSequence", "presentedFrameSequence", "sceneClockMs", "stage", "eventSequence",
            "geometryVersion", "coreTextureStatus", "coreAlphaStatus", "shaderCompileLinkStatus",
            "backFboStatus", "residentBackStatus", "coreFluidStatus", "refractionStatus",
            "r01bMaskStatus", "r02DropletStatus", "r03LightStatus", "r05CausticsStatus",
            "residentFrontStatus", "bloomExtractStatus", "bloomBlurHStatus", "bloomBlurVStatus",
            "compositeStatus", "eglSwapStatus", "fallbackStatus", "failureReason",
        ).forEach { assertTrue("missing $it", payload.containsKey(it)) }
        assertFalse(payload.values.contains(identity.photoSha256))
    }

    private fun statusWithFailure(passName: String): OrbCrystalRenderResult {
        val base = successfulResult(frame, 10L)
        return when (passName) {
            "coreTextureStatus" -> base.copy(coreTextureStatus = RenderPassStatus.FAIL)
            "coreAlphaStatus" -> base.copy(coreAlphaStatus = RenderPassStatus.FAIL)
            "shaderCompileLinkStatus" -> base.copy(shaderCompileLinkStatus = RenderPassStatus.FAIL)
            "backFboStatus" -> base.copy(backFboStatus = RenderPassStatus.FAIL)
            "residentBackStatus" -> base.copy(residentBackStatus = RenderPassStatus.FAIL)
            "coreFluidStatus" -> base.copy(coreFluidStatus = RenderPassStatus.FAIL)
            "refractionStatus" -> base.copy(refractionStatus = RenderPassStatus.FAIL)
            "r01bMaskStatus" -> base.copy(r01bMaskStatus = RenderPassStatus.FAIL)
            "r02DropletStatus" -> base.copy(r02DropletStatus = RenderPassStatus.FAIL)
            "r03LightStatus" -> base.copy(r03LightStatus = RenderPassStatus.FAIL)
            "r05CausticsStatus" -> base.copy(r05CausticsStatus = RenderPassStatus.FAIL)
            "residentFrontStatus" -> base.copy(residentFrontStatus = RenderPassStatus.FAIL)
            "bloomExtractStatus" -> base.copy(bloomExtractStatus = RenderPassStatus.FAIL)
            "bloomBlurHStatus" -> base.copy(bloomBlurHStatus = RenderPassStatus.FAIL)
            "bloomBlurVStatus" -> base.copy(bloomBlurVStatus = RenderPassStatus.FAIL)
            "compositeStatus" -> base.copy(compositeStatus = RenderPassStatus.FAIL)
            else -> error("Unknown pass: $passName")
        }
    }

    private fun successfulResult(frame: RecognitionSceneFrame, sequence: Long) = OrbCrystalRenderResult(
        identity = frame.identity,
        sceneClockMs = frame.sceneElapsedMs,
        frameSequence = sequence,
        stage = frame.stage,
        stageProgress = frame.stageProgress,
        eventSequence = frame.eventSequence,
        geometryVersion = frame.geometryVersion,
        timestampMs = 9_000L,
        coreTextureStatus = RenderPassStatus.PASS,
        coreAlphaStatus = RenderPassStatus.PASS,
        shaderCompileLinkStatus = RenderPassStatus.PASS,
        backFboStatus = RenderPassStatus.PASS,
        residentBackStatus = RenderPassStatus.PASS,
        coreFluidStatus = RenderPassStatus.PASS,
        refractionStatus = RenderPassStatus.PASS,
        r01bMaskStatus = RenderPassStatus.PASS,
        r02DropletStatus = RenderPassStatus.PASS,
        r03LightStatus = RenderPassStatus.PASS,
        r05CausticsStatus = RenderPassStatus.PASS,
        residentFrontStatus = RenderPassStatus.PASS,
        bloomExtractStatus = RenderPassStatus.PASS,
        bloomBlurHStatus = RenderPassStatus.PASS,
        bloomBlurVStatus = RenderPassStatus.PASS,
        compositeStatus = RenderPassStatus.PASS,
    )
}
