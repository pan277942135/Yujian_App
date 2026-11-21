package com.yujian.ai.ui.waterlight.renderer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbCrystalRenderResultAndroidTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "image", "photo-sha", 1L)
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

    @Test fun androidTestVariantKeepsComposeOnNotReadyAndTransfersOnlyAfterValidRetry() {
        val ownership = OrbRenderOwnershipState()
        var glOwnsOrb = false
        var composeVisible = true

        fun applyOwner(result: OrbCrystalRenderResult) {
            glOwnsOrb = ownership.apply(result, frame)
            composeVisible = !glOwnsOrb
            assertTrue("exactly one renderer owns the visible Orb", composeVisible.xor(glOwnsOrb))
        }

        val incomplete = completeFixture(frame, 1L).copy(coreTextureStatus = RenderPassStatus.NOT_READY)
        val rejectedGate = GlFramePresentationGate.beforeSwap(
            execution = GlFrameExecution(RenderPassStatus.PASS, incomplete),
            expectedFrame = frame,
            expectedFrameSequence = 1L,
            requireResult = true,
        )

        assertFalse(rejectedGate.maySwap)
        val rejected = GlFramePresentationGate.afterSwap(
            result = incomplete,
            swapStatus = RenderPassStatus.NOT_RUN,
            presentedSequence = null,
            failureReason = rejectedGate.rejectionReason,
        )
        applyOwner(rejected)
        assertFalse(glOwnsOrb)
        assertTrue(composeVisible)

        val retry = completeFixture(frame, 2L).withPresentation(RenderPassStatus.PASS, 2L)
        val retryGate = GlFramePresentationGate.beforeSwap(
            execution = GlFrameExecution(RenderPassStatus.PASS, retry),
            expectedFrame = frame,
            expectedFrameSequence = 2L,
            requireResult = true,
        )
        assertTrue(retryGate.maySwap)

        val presented = GlFramePresentationGate.afterSwap(
            result = retry,
            swapStatus = RenderPassStatus.PASS,
            presentedSequence = 2L,
        )
        applyOwner(presented)
        assertTrue(glOwnsOrb)
        assertFalse(composeVisible)
    }

    private fun completeFixture(frame: RecognitionSceneFrame, sequence: Long) = OrbCrystalRenderResult(
        identity = frame.identity,
        sceneClockMs = frame.sceneElapsedMs,
        frameSequence = sequence,
        stage = frame.stage,
        stageProgress = frame.stageProgress,
        eventSequence = frame.eventSequence,
        geometryVersion = frame.geometryVersion,
        timestampMs = 1_000L,
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
