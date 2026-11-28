package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTaskFilamentStateTest {
    @Test fun taskStateCarriesAttemptMaskAndPathIdentity() {
        val event = event(
            stage = RecognitionSceneStage.CONTOUR_B,
            feedback = EnergyFeedback.ABSORB,
            level = .72f,
            pathId = "task:attempt-1:pstar-7",
            withMask = true,
        )
        val frame = frame(event)
        val state = requireNotNull(RecognitionTaskFilamentState.from(frame, event))

        assertEquals(RecognitionTaskFilamentPhase.MASK_CONTACT, state.phase)
        assertEquals("task:session-1:attempt-1:task:attempt-1:pstar-7", state.taskId)
        assertEquals(event.pathId, state.taskPathId)
        assertEquals(event.pathId, state.pStarPathId)
        assertEquals(event.pathMaskSha256, state.maskSha256)
        assertEquals(event.pathMaskVersion, state.maskVersion)
        assertEquals(event.geometryVersion, state.geometryVersion)
        assertEquals(event.eventSequence, state.eventSequence)
        assertEquals(.72f, state.contactProgress, 0f)
        assertEquals(.72f, state.taskAlpha, 0f)
        assertTrue(state.matches(frame))
    }

    @Test fun taskMergeIsIndependentAndClearsOnlyTaskAlpha() {
        val event = event(
            stage = RecognitionSceneStage.ORB_RETURN,
            feedback = EnergyFeedback.RETURN,
            level = 1f,
            pathId = "task:attempt-1:pstar-7",
            withMask = true,
        )
        val returning = requireNotNull(RecognitionTaskFilamentState.from(frame(event), event))
        val merged = requireNotNull(RecognitionTaskFilamentState.fromEvent(event, taskEnergyMerged = true))

        assertEquals(RecognitionTaskFilamentPhase.RETURN, returning.phase)
        assertEquals(1f, returning.returnProgress, 0f)
        assertEquals(RecognitionTaskFilamentPhase.MERGED, merged.phase)
        assertEquals(0f, merged.taskAlpha, 0f)
        assertEquals(returning.taskPathId, merged.taskPathId)
    }

    @Test fun staleGeometryAndResidentIdsCannotBecomeTaskPaths() {
        val taskEvent = event(
            stage = RecognitionSceneStage.ORB_RELEASE,
            feedback = EnergyFeedback.RELEASE,
            level = .4f,
            pathId = "task:attempt-1:release",
        )
        assertNull(RecognitionTaskFilamentState.from(frame(taskEvent).copy(geometryVersion = 8L), taskEvent))

        val copiedResident = taskEvent.copy(pathId = "G01")
        assertNull(RecognitionTaskFilamentState.from(frame(copiedResident), copiedResident))
        assertFalse(requireNotNull(RecognitionTaskFilamentState.fromEvent(taskEvent)).matches(
            frame(taskEvent).copy(attemptId = "attempt-2"),
        ))
    }

    private fun frame(event: EnergyEvent): WaterLightFrame {
        val identity = RecognitionSceneIdentity(
            sessionId = event.sessionId,
            attemptId = event.attemptId,
            imageId = event.imageId,
            photoSha256 = event.photoSha256,
            photoContentVersion = event.photoContentVersion,
        )
        val scene = RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = 2_000L,
            opticalReferenceElapsedMs = event.referenceMs,
            geometryVersion = event.geometryVersion,
            stage = event.stage,
            stageElapsedMs = (event.level * event.stage.minimumVisibleMs).toLong(),
            stageProgress = event.level,
            eventSequence = event.eventSequence,
            monotonicTimestampMs = event.monotonicTimestampMs,
            energyEvent = event,
        )
        return WaterLightFrame(
            sessionId = event.sessionId,
            photoSha256 = event.photoSha256,
            elapsedMs = 2_000L,
            opticalReferenceElapsedMs = event.referenceMs,
            phase = WaterLightPhase.PROCESSING,
            completion = event.level,
            reducedMotion = false,
            geometryVersion = event.geometryVersion,
            attemptId = event.attemptId,
            imageId = event.imageId,
            photoContentVersion = event.photoContentVersion,
            eventSequence = event.eventSequence,
            sceneFrameOverride = scene,
        )
    }

    private fun event(
        stage: RecognitionSceneStage,
        feedback: EnergyFeedback,
        level: Float,
        pathId: String,
        withMask: Boolean = false,
    ) = EnergyEvent(
        sessionId = "session-1",
        photoSha256 = "photo-sha-1",
        source = if (feedback == EnergyFeedback.RETURN) EnergyAnchor.FISH_CONTOUR else EnergyAnchor.ORB_RIBBON,
        target = if (feedback == EnergyFeedback.RETURN) EnergyAnchor.ORB_CORE else EnergyAnchor.PHOTO_PATH,
        feedback = feedback,
        level = level,
        referenceMs = 2_000L,
        geometryVersion = 7L,
        attemptId = "attempt-1",
        imageId = "image-1",
        photoContentVersion = 1L,
        pathId = pathId,
        pathGeometry = geometry,
        pathMaskSha256 = if (withMask) "a".repeat(64) else null,
        pathMaskVersion = if (withMask) 3L else null,
        pathGeometryVersion = if (withMask) 7L else null,
        pathContactPointNormalizedSource = if (withMask) WaterLightPathPoint(.4f, .6f) else null,
        eventSequence = 9L,
        monotonicTimestampMs = 2_000L,
        renderStatus = "READY",
        stage = stage,
    )

    private companion object {
        val geometry = WaterLightPathGeometry(
            start = WaterLightPathPoint(0f, 0f),
            control1 = WaterLightPathPoint(.2f, .3f),
            control2 = WaterLightPathPoint(.5f, .5f),
            end = WaterLightPathPoint(1f, 1f),
        )
    }
}
