package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.SceneGeometryState
import com.yujian.ai.ui.waterlight.core.SceneMaskState
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.SceneRunState
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionVisualStateControllerTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "image", "sha256", 12L)

    @Test fun manualCanInspectAllStagesWithoutModelDataAndNeverAutoAdvances() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        assertEquals(ScenePlaybackMode.MANUAL, controller.mode)
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, controller.currentSceneStage(30_100L))
        assertTrue(controller.previewCompleted)

        RecognitionSceneStage.entries.drop(1).forEachIndexed { index, expected ->
            assertEquals(expected, controller.advanceToNextStage(30_100L + index))
            assertEquals(expected, controller.currentSceneStage(60_000L + index))
            assertEquals(expected, controller.currentFrame(identity, 60_000L + index, 7L).stage)
        }
        assertEquals(RecognitionSceneStage.RESIDUE_SETTLE, controller.currentStage)
        assertFalse(controller.modelGates.mask == com.yujian.ai.ui.waterlight.core.SceneMaskState.TRUSTED)
        assertEquals(null, controller.advanceToNextStage(90_000L))
    }

    @Test fun pauseFreezesTheSharedStageFrameAndResumeContinuesTheSamePreview() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.CONTOUR_B, 200L)
        val beforePause = controller.currentFrame(identity, 700L, 4L)
        assertEquals(500L, beforePause.stageElapsedMs)

        controller.pause(0L, 700L)
        val frozen = controller.currentFrame(identity, 10_700L, 4L)
        assertEquals(beforePause.stage, frozen.stage)
        assertEquals(beforePause.stageElapsedMs, frozen.stageElapsedMs)
        assertEquals(beforePause.stageProgress, frozen.stageProgress)
        assertTrue(frozen.paused)

        controller.resume(0L, 10_700L)
        assertEquals(500L, controller.stageElapsedMs(10_700L))
        assertEquals(750L, controller.stageElapsedMs(10_950L))
    }

    @Test fun previousAndReplayTouchOnlyTheSelectedManualStage() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.advanceToNextStage(200L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.advanceToNextStage(300L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.currentStage)
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.returnToPreviousStage(400L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentStage)
        controller.restartCurrentStage(500L)
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentStage)
        assertEquals(0L, controller.previewElapsedMs(500L))
    }

    @Test fun resultRevealTimeIsLocalToTheSelectedStageAndReplayRestartsThatStage() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)

        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        assertEquals(175L, controller.revealElapsedMs(1_700L))
        assertEquals(350L, controller.revealElapsedMs(3_200L))
        controller.beginStepPreview(RecognitionSceneStage.ORB_TO_NAME, 400L)
        assertEquals(0L, controller.revealElapsedMs(400L))
        assertEquals(425L, controller.revealElapsedMs(1_900L))
        controller.restartCurrentStage(500L)
        assertEquals(0L, controller.revealElapsedMs(500L))
        assertEquals(850L, controller.revealElapsedMs(3_500L))

        controller.beginStepPreview(RecognitionSceneStage.SPECIES_NAME_REVEAL, 600L)
        assertEquals(0L, controller.revealElapsedMs(600L))
        controller.beginStepPreview(RecognitionSceneStage.RESIDUE_SETTLE, 700L)
        assertEquals(0L, controller.revealElapsedMs(700L))
        assertEquals(450L, controller.revealElapsedMs(3_700L))
    }

    @Test fun geometryGateRecoversLateAndDoesNotOverrideUserPause() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        assertEquals(500L, controller.stageElapsedMs(700L))

        controller.setResultGeometryState(SceneGeometryState.PENDING, 700L, identity)
        assertEquals(500L, controller.stageElapsedMs(2_000L))
        controller.pause(0L, 2_000L)
        controller.setResultGeometryState(SceneGeometryState.READY, 5_000L, identity)
        assertEquals(500L, controller.stageElapsedMs(6_000L))
        assertTrue(controller.paused)
        assertEquals(SceneGeometryState.READY, controller.modelGates.geometry)

        controller.resume(0L, 6_000L)
        assertEquals(750L, controller.stageElapsedMs(6_250L))
    }

    @Test fun geometryGatePreservesDiagnosticAutoProgressWhenPausedBeforeLateRecovery() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        controller.playDiagnosticAuto(0L, 700L)
        controller.setResultGeometryState(SceneGeometryState.PENDING, 700L, identity)

        controller.pause(0L, 2_000L)
        controller.setResultGeometryState(SceneGeometryState.READY, 5_000L, identity)
        assertEquals(500L, controller.stageElapsedMs(6_000L))
        assertTrue(controller.paused)

        controller.resume(0L, 6_000L)
        assertEquals(750L, controller.stageElapsedMs(6_250L))
    }

    @Test fun handoffIsValidOnlyAtExactStageTenAfterClassifierMaskJoinAndOrbAnchor() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        controller.onRevealDecisionReady(identity)
        controller.onOrbAnchorAvailable(true, identity)

        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        assertEquals(SceneGeometryState.PENDING, controller.modelGates.geometry)
        assertTrue(controller.resultVisualReady(200L))
        controller.beginStepPreview(RecognitionSceneStage.ORB_TO_NAME, 201L)
        assertFalse(controller.resultVisualReady(201L))

        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 202L)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = false,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        assertFalse(controller.resultVisualReady(202L))
    }

    @Test fun explicitUntrustedMaskTerminalAllowsOrbOnlyJoinButPendingMaskDoesNot() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = false,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        controller.onRevealDecisionReady(identity)
        controller.onOrbAnchorAvailable(true, identity)
        assertEquals(SceneMaskState.PENDING, controller.modelGates.slimSam)
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        assertFalse(controller.resultVisualReady(200L))

        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            slimSamFailed = true,
            currentIdentity = identity,
        )
        assertEquals(SceneMaskState.FAILED, controller.modelGates.slimSam)
        assertTrue(controller.resultVisualReady(201L))
    }

    @Test fun failureIsTechnicalAndLateModelReadinessCannotNavigateFromStageThirteen() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.RESIDUE_SETTLE, 200L)
        controller.onPipelinePhase(com.yujian.ai.ai.RecognitionPhase.FAILURE, 300L, currentIdentity = identity)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            slimSamFailed = true,
            currentIdentity = identity,
        )
        controller.onRevealDecisionReady(identity)
        controller.onOrbAnchorAvailable(true, identity)
        assertEquals(SceneRunState.FAILED, controller.modelGates.runState)
        assertFalse(controller.resultVisualReady(30_000L))
        assertEquals(RecognitionSceneStage.RESIDUE_SETTLE, controller.currentSceneStage(30_000L))
        assertEquals(null, controller.advanceToNextStage(30_000L))
    }

    @Test fun explicitInvalidResultGeometryIsNotReportedAsReady() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        controller.setResultGeometryState(SceneGeometryState.INVALID, 600L, identity)
        assertEquals(SceneGeometryState.INVALID, controller.modelGates.geometry)
        assertEquals("RESULT_GEOMETRY_INVALID", controller.modelGates.waitingReason)
        assertFalse(controller.resultVisualReady(700L))
    }

    @Test fun aNewAttemptInvalidatesThePreviousIdentityAndItsEvents() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        val next = identity.copy(attemptId = "attempt-2", imageId = "image-2", photoSha256 = "sha256-2")
        controller.beginNewSession(next, debugBuild = true, nowMs = 200L)
        assertFalse(controller.markPhotoFirstFramePresented(identity, 300L))
        assertTrue(controller.markPhotoFirstFramePresented(next, 300L))
        assertEquals(next, controller.currentFrame(next, 300L, 1L).identity)
    }

    @Test fun diagnosticAutoWaitsThreeSecondsAndStillHonorsRealEventGates() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.playDiagnosticAuto(0L, 100L)

        assertEquals(RecognitionSceneStage.PHOTO_STABLE, controller.currentSceneStage(3_099L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentSceneStage(3_100L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentSceneStage(20_000L))

        controller.onOrbAnchorAvailable(true, identity)
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.currentSceneStage(23_000L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.currentSceneStage(60_000L))
    }

    @Test fun manualAndAutoKeepTheRecognitionIdentityWhenPlaybackModeChanges() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.CONTOUR_B, 200L)
        controller.playDiagnosticAuto(0L, 500L)
        assertEquals(identity, controller.currentFrame(identity, 600L, 7L).identity)
        assertEquals(RecognitionSceneStage.CONTOUR_B, controller.currentStage)

        controller.switchToStepDiagnostic(0L, 700L)
        assertEquals(identity, controller.currentFrame(identity, 800L, 7L).identity)
        assertEquals(RecognitionSceneStage.CONTOUR_B, controller.currentStage)
    }

    @Test fun oldResultGeometryVersionCannotCompleteAnOrbReturnEvent() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        controller.onRevealDecisionReady(identity)
        controller.onOrbAnchorAvailable(true, identity)
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 200L)
        controller.setResultGeometryState(SceneGeometryState.READY, 200L, identity, setOf(100L))

        // recordEnergyEvent only accepts a stage-complete sample, not an arbitrary mid-stage frame.
        val firstFrame = controller.currentFrame(identity, 3_200L, 100L)
        val path = WaterLightPathGeometry(
            WaterLightPathPoint(10f, 20f), WaterLightPathPoint(20f, 30f),
            WaterLightPathPoint(30f, 40f), WaterLightPathPoint(40f, 50f),
        )
        val event = EnergyEvent(
            sessionId = identity.sessionId,
            photoSha256 = requireNotNull(identity.photoSha256),
            source = EnergyAnchor.PHOTO_PERIMETER,
            target = EnergyAnchor.ORB_CORE,
            feedback = EnergyFeedback.RETURN,
            level = 1f,
            referenceMs = firstFrame.opticalReferenceElapsedMs,
            geometryVersion = 100L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            pathId = "photo-perimeter-to-original-orb",
            pathGeometry = path,
            eventSequence = firstFrame.eventSequence,
            monotonicTimestampMs = firstFrame.monotonicTimestampMs,
            sourceHost = EnergyAnchor.PHOTO_PERIMETER.name,
            targetHost = EnergyAnchor.ORB_CORE.name,
            renderStatus = "READY",
            stage = RecognitionSceneStage.ORB_RETURN,
        )
        assertTrue(controller.recordEnergyEvent(firstFrame, event))

        controller.setResultGeometryState(SceneGeometryState.READY, 3_201L, identity, setOf(200L))
        val nextFrame = controller.currentFrame(identity, 3_202L, 100L)
        assertFalse(controller.recordEnergyEvent(
            nextFrame,
            event.copy(eventSequence = nextFrame.eventSequence, monotonicTimestampMs = nextFrame.monotonicTimestampMs),
        ))
    }

    @Test fun fishContactPathIsBoundToExactMaskPStarAndSignedGeometryVersion() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)

        val maskSha = "a".repeat(64)
        val nextMaskSha = "b".repeat(64)
        val geometryVersion = -7L
        val contact = WaterLightPathPoint(.42f, .61f)
        val pathId = "waterlight:${identity.sessionId}:${identity.attemptId}:${identity.imageId}:" +
            "${identity.photoContentVersion}:$geometryVersion:mask:${maskSha.take(16)}:42:0.420000:0.610000"

        controller.onPhotoTransformAvailable(true, identity, geometryVersion = geometryVersion)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = identity,
            maskSha256 = maskSha,
            maskVersion = 42L,
        )
        controller.onRevealDecisionReady(identity)
        controller.beginStepPreview(RecognitionSceneStage.PATH_TO_FISH, 200L)

        assertFalse(controller.onFishContactPathAvailable(
            pathId, nextMaskSha, 42L, geometryVersion, contact, identity,
        ))
        assertFalse(controller.onFishContactPathAvailable(
            pathId, maskSha, 43L, geometryVersion, contact, identity,
        ))
        assertFalse(controller.onFishContactPathAvailable(
            pathId, maskSha, 42L, geometryVersion + 1L, contact, identity,
        ))
        assertFalse(controller.onFishContactPathAvailable(
            pathId, maskSha, 42L, geometryVersion, WaterLightPathPoint(.44f, .61f), identity,
        ))
        assertTrue(controller.onFishContactPathAvailable(
            pathId, maskSha, 42L, geometryVersion, contact, identity,
        ))

        val frame = controller.currentFrame(identity, 3_200L, geometryVersion)
        assertEquals(1f, frame.stageProgress, 0f)
        val path = WaterLightPathGeometry(
            WaterLightPathPoint(100f, 120f),
            WaterLightPathPoint(150f, 180f),
            WaterLightPathPoint(200f, 240f),
            WaterLightPathPoint(250f, 300f),
        )
        val event = EnergyEvent(
            sessionId = identity.sessionId,
            photoSha256 = requireNotNull(identity.photoSha256),
            source = EnergyAnchor.PHOTO_PATH,
            target = EnergyAnchor.FISH_CONTOUR,
            feedback = EnergyFeedback.ABSORB,
            level = .25f,
            referenceMs = frame.opticalReferenceElapsedMs,
            geometryVersion = geometryVersion,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            pathId = pathId,
            pathGeometry = path,
            eventSequence = frame.eventSequence,
            monotonicTimestampMs = frame.monotonicTimestampMs,
            sourceHost = EnergyAnchor.PHOTO_PATH.name,
            targetHost = EnergyAnchor.FISH_CONTOUR.name,
            renderStatus = "READY",
            stage = RecognitionSceneStage.PATH_TO_FISH,
        )
        // PATH_TO_FISH optical level is capped at .25; stageProgress, not level, is
        // the authoritative completion gate for this event.
        assertTrue(controller.recordEnergyEvent(frame, event))
        assertEquals(null, controller.modelGates.waitingReason)

        controller.beginStepPreview(RecognitionSceneStage.CONTOUR_A, 3_201L)
        controller.onPhotoTransformAvailable(true, identity, geometryVersion = geometryVersion + 1L)
        assertEquals("WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT", controller.modelGates.waitingReason)
        assertFalse(controller.onFishContactPathAvailable(
            pathId, maskSha, 42L, geometryVersion, contact, identity,
        ))
        val nextGeometryPathId = pathId.replace(":$geometryVersion:mask:", ":${geometryVersion + 1L}:mask:")
        assertTrue(controller.onFishContactPathAvailable(
            nextGeometryPathId, maskSha, 42L, geometryVersion + 1L, contact, identity,
        ))
        val contourFrame = controller.currentFrame(identity, 6_201L, geometryVersion + 1L)
        val contourEvent = event.copy(
            level = .5f,
            referenceMs = contourFrame.opticalReferenceElapsedMs,
            geometryVersion = geometryVersion + 1L,
            pathId = nextGeometryPathId,
            eventSequence = contourFrame.eventSequence,
            monotonicTimestampMs = contourFrame.monotonicTimestampMs,
            stage = RecognitionSceneStage.CONTOUR_A,
        )
        assertTrue(controller.recordEnergyEvent(contourFrame, contourEvent))
        assertEquals(null, controller.modelGates.waitingReason)

        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = identity,
            maskSha256 = nextMaskSha,
            maskVersion = 43L,
        )
        assertFalse(controller.onFishContactPathAvailable(
            nextGeometryPathId, maskSha, 42L, geometryVersion + 1L, contact, identity,
        ))
    }

}
