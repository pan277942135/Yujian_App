package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEnergyPathRenderReceiptTest {
    @Test fun gateReceiptWaitsForAcceptedEventAndSuccessfulFullPathSwap() {
        val identity = RecognitionSceneIdentity(
            sessionId = "session-1",
            attemptId = "attempt-1",
            imageId = "photo-1",
            photoSha256 = SHA,
            photoContentVersion = 1,
        )
        val controller = RecognitionSceneAnimationController()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        assertTrue(controller.markPhotoFirstFrameCommitted(identity, 1L))
        controller.beginStepPreview(RecognitionSceneStage.ORB_RELEASE, 2L)
        val frame = controller.currentFrame(identity, 3_002L, geometryVersion = 9L)
        val event = EnergyEvent(
            sessionId = identity.sessionId,
            photoSha256 = SHA,
            source = EnergyAnchor.ORB_RIBBON,
            target = EnergyAnchor.PHOTO_PERIMETER,
            feedback = EnergyFeedback.RELEASE,
            level = 1f,
            referenceMs = frame.opticalReferenceElapsedMs,
            geometryVersion = frame.geometryVersion,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            pathId = "release-1",
            pathGeometry = geometry(),
            eventSequence = frame.eventSequence,
            monotonicTimestampMs = frame.monotonicTimestampMs,
            sourceHost = EnergyAnchor.ORB_RIBBON.name,
            targetHost = EnergyAnchor.PHOTO_PERIMETER.name,
            renderStatus = "READY",
            stage = RecognitionSceneStage.ORB_RELEASE,
        )
        val receipt = receipt(identity, event, frame.stageProgress, renderedProgress = 1f)
        val emitted = mutableListOf<RecognitionGateProducerEvent>()
        controller.setGateProducerObserver { emitted += it; true }

        assertFalse(controller.onEnergyPathRenderComplete(
            event,
            receipt.copy(renderedProgress = .98f),
            identity,
        ))
        assertTrue(controller.onEnergyPathRenderComplete(event, receipt, identity))
        assertTrue(emitted.isEmpty())
        assertTrue(controller.recordEnergyEvent(frame, event))
        assertEquals(1, emitted.size)
        assertEquals(RecognitionGateId.RELEASE_RENDER, emitted.single().gateId)
        assertEquals(receipt.pathContentSha256, emitted.single().contentHash)
        assertTrue(controller.onEnergyPathRenderComplete(event, receipt.copy(frameSequence = 2L), identity))
        assertEquals("one Gate success per accepted rendered event", 1, emitted.size)
    }

    @Test fun failedSurfaceSwapAndStaleAttemptCannotSatisfyRenderGate() {
        val identity = RecognitionSceneIdentity("session-1", "attempt-1", "photo-1", SHA, 1L)
        val event = EnergyEvent(
            sessionId = identity.sessionId,
            photoSha256 = SHA,
            source = EnergyAnchor.PHOTO_PERIMETER,
            target = EnergyAnchor.PHOTO_PATH,
            feedback = EnergyFeedback.ARRIVE,
            level = 1f,
            referenceMs = 3_002L,
            geometryVersion = 9L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            pathId = "arrival-1",
            pathGeometry = geometry(),
            eventSequence = 4L,
            monotonicTimestampMs = 3_002L,
            sourceHost = EnergyAnchor.PHOTO_PERIMETER.name,
            targetHost = EnergyAnchor.PHOTO_PATH.name,
            renderStatus = "READY",
            stage = RecognitionSceneStage.PERIMETER_ARRIVAL,
        )
        val receipt = receipt(identity, event, 1f, 1f)

        assertFalse(RecognitionEnergyPathRenderContract.isComplete(
            identity, event, receipt.copy(eglSwapSucceeded = false),
        ))
        assertFalse(RecognitionEnergyPathRenderContract.isComplete(
            identity,
            event.copy(attemptId = "old-attempt"),
            receipt,
        ))
        assertTrue(RecognitionEnergyPathRenderContract.isComplete(identity, event, receipt))
    }

    private fun receipt(
        identity: RecognitionSceneIdentity,
        event: EnergyEvent,
        stageProgress: Float,
        renderedProgress: Float,
    ) = RecognitionEnergyPathRenderReceipt(
        identity = identity,
        eventId = event.eventId,
        stage = event.stage,
        feedback = event.feedback,
        pathId = requireNotNull(event.pathId),
        geometryVersion = event.geometryVersion,
        pathGeometry = event.pathGeometry,
        pathSegments = event.pathSegments,
        pathContentSha256 = event.calculatePathContentSha256(),
        sourceStageProgress = stageProgress,
        renderedProgress = renderedProgress,
        renderedSegmentCount = 1,
        eglSwapSucceeded = true,
        frameSequence = 1L,
        observedAtUptimeMs = event.monotonicTimestampMs + 1L,
    )

    private fun geometry() = WaterLightPathGeometry(
        start = WaterLightPathPoint(1f, 2f),
        control1 = WaterLightPathPoint(2f, 3f),
        control2 = WaterLightPathPoint(3f, 4f),
        end = WaterLightPathPoint(4f, 5f),
    )

    private companion object {
        val SHA = "a".repeat(64)
    }
}
