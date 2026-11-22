package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionOrbReturnRenderReceipt
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.SceneGeometryState
import com.yujian.ai.ui.waterlight.core.SceneMaskState
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.SceneRunState
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathLedger
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphBounds
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphPathDescriptor
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphDrawReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionOrbCoreDissolveReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionVisualStateControllerTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "image", "sha256", 12L)

    @Test fun explicitStepPreviewCanInspectStagesWithoutModelData() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)
        controller.beginStepPreview(RecognitionSceneStage.PHOTO_STABLE, 100L)
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
        controller.beginStepPreview(RecognitionSceneStage.PHOTO_STABLE, 100L)
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
        val withoutReceipt = controller.recordEnergyEventWithReason(firstFrame, event)
        assertFalse(withoutReceipt.accepted)
        assertEquals(
            com.yujian.ai.ui.waterlight.core.EnergyEventRejectionReason.EVENT_RETURN_RENDER_RECEIPT_MISSING,
            withoutReceipt.rejectionReason,
        )
        val returnReceipt = RecognitionOrbReturnRenderReceipt(
            identity = identity,
            stage = RecognitionSceneStage.ORB_RETURN,
            pathId = requireNotNull(event.pathId),
            maskSha256 = null,
            maskVersion = null,
            pathGeometryVersion = null,
            resultGeometryVersion = event.geometryVersion,
            pathGeometry = path,
            pathSegments = emptyList(),
            renderedSegmentCount = 1,
            renderedProgress = 1f,
            renderedPathLength = 42f,
            pathCanvasSubmitted = true,
            drawPassSequence = 1L,
        )
        assertTrue(controller.onOrbReturnRenderComplete(returnReceipt, identity))
        val frameAfterReceipt = controller.currentFrame(identity, 3_200L, 100L)
        assertTrue(controller.recordEnergyEvent(
            frameAfterReceipt,
            event.copy(
                eventSequence = frameAfterReceipt.eventSequence,
                monotonicTimestampMs = frameAfterReceipt.monotonicTimestampMs,
                referenceMs = frameAfterReceipt.opticalReferenceElapsedMs,
            ),
        ))

        controller.setResultGeometryState(SceneGeometryState.READY, 3_201L, identity, setOf(200L))
        val nextFrame = controller.currentFrame(identity, 3_202L, 100L)
        assertFalse(controller.recordEnergyEvent(
            nextFrame,
            event.copy(eventSequence = nextFrame.eventSequence, monotonicTimestampMs = nextFrame.monotonicTimestampMs),
        ))
    }

    @Test fun fishContactPathIsBoundToExactMaskPStarAndPositiveGeometryVersion() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 100L)

        val maskSha = "a".repeat(64)
        val nextMaskSha = "b".repeat(64)
        val geometryVersion = 7L
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
        controller.onOrbAnchorAvailable(true, identity)

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
        assertEquals(RecognitionSceneStage.ORB_RETURN, controller.currentSceneStage(3_202L))
        assertEquals("WAITING_FOR_RETURN_ENERGY_EVENT", controller.modelGates.waitingReason)
        assertFalse(controller.onFishContactPathAvailable(
            pathId, maskSha, 42L, geometryVersion, contact, identity,
        ))
        val nextGeometryPathId = pathId.replace(":$geometryVersion:mask:", ":${geometryVersion + 1L}:mask:")
        assertFalse(controller.onFishContactPathAvailable(
            nextGeometryPathId, maskSha, 42L, geometryVersion + 1L, contact, identity,
        ))

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
        val nextMaskPathId = nextGeometryPathId.replace(
            ":mask:${maskSha.take(16)}:42:", ":mask:${nextMaskSha.take(16)}:43:",
        )
        assertFalse(controller.onFishContactPathAvailable(
            nextMaskPathId, nextMaskSha, 43L, geometryVersion + 1L, contact, identity,
        ))
    }

    @Test fun fullRevealReturnConsumesTheProcessingLedgerSnapshotAndRejectsDefaultOrStaleIdentity() {
        val photoSha = "c".repeat(64)
        val maskSha = "a".repeat(64)
        val contact = WaterLightPathPoint(.42f, .61f)
        val pathGeometryVersion = 7L
        val resultGeometryVersion = 200L
        val runtimeIdentity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", photoSha, 3L)
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(runtimeIdentity, debugBuild = true, nowMs = 0L)
        controller.markPhotoFirstFramePresented(runtimeIdentity, 100L)
        controller.onPhotoTransformAvailable(true, runtimeIdentity, pathGeometryVersion)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = runtimeIdentity,
            maskSha256 = maskSha,
            maskVersion = 42L,
        )
        controller.onRevealDecisionReady(runtimeIdentity)
        controller.onOrbAnchorAvailable(true, runtimeIdentity)
        controller.beginStepPreview(RecognitionSceneStage.PATH_TO_FISH, 200L)

        val processingScene = controller.currentFrame(runtimeIdentity, 3_200L, pathGeometryVersion)
        val processingFrame = WaterLightFrame(
            sessionId = runtimeIdentity.sessionId,
            photoSha256 = photoSha,
            elapsedMs = processingScene.sceneElapsedMs,
            opticalReferenceElapsedMs = processingScene.opticalReferenceElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = 0f,
            reducedMotion = false,
            geometryVersion = pathGeometryVersion,
            attemptId = runtimeIdentity.attemptId,
            imageId = runtimeIdentity.imageId,
            photoContentVersion = runtimeIdentity.photoContentVersion,
            eventSequence = processingScene.eventSequence,
            sceneFrameOverride = processingScene,
        )
        val releasePath = WaterLightPathGeometry(
            WaterLightPathPoint(120f, 220f), WaterLightPathPoint(220f, 245f),
            WaterLightPathPoint(320f, 270f), WaterLightPathPoint(420f, 300f),
        )
        val fishPath = WaterLightPathGeometry(
            WaterLightPathPoint(380f, 300f), WaterLightPathPoint(450f, 350f),
            WaterLightPathPoint(550f, 420f), WaterLightPathPoint(640f, 480f),
        )
        val ledger = WaterLightPathLedger()
        assertTrue(ledger.recordRelease(processingFrame, releasePath))
        val perimeterArrival = requireNotNull(
            WaterLightPathGeometryFactory.between(releasePath.end, fishPath.start, seed = 19),
        )
        assertTrue(ledger.recordPerimeterArrivalPath(processingFrame, perimeterArrival))
        assertTrue(ledger.recordFishPath(processingFrame, fishPath, true, maskSha, 42L, contact))
        val fishPathId = requireNotNull(ledger.pathId(processingFrame, maskSha, 42L, contact))
        assertTrue(controller.onFishContactPathAvailable(
            fishPathId, maskSha, 42L, pathGeometryVersion, contact, runtimeIdentity,
        ))
        val absorb = EnergyEvent(
            sessionId = runtimeIdentity.sessionId,
            photoSha256 = photoSha,
            source = EnergyAnchor.PHOTO_PATH,
            target = EnergyAnchor.FISH_CONTOUR,
            feedback = EnergyFeedback.ABSORB,
            level = .25f,
            referenceMs = processingScene.opticalReferenceElapsedMs,
            geometryVersion = pathGeometryVersion,
            attemptId = runtimeIdentity.attemptId,
            imageId = runtimeIdentity.imageId,
            photoContentVersion = runtimeIdentity.photoContentVersion,
            pathId = fishPathId,
            pathGeometry = fishPath,
            eventSequence = processingScene.eventSequence,
            monotonicTimestampMs = processingScene.monotonicTimestampMs,
            sourceHost = EnergyAnchor.PHOTO_PATH.name,
            targetHost = EnergyAnchor.FISH_CONTOUR.name,
            renderStatus = "READY",
            stage = RecognitionSceneStage.PATH_TO_FISH,
        )
        assertTrue(controller.recordEnergyEvent(processingScene, absorb))

        val snapshot = requireNotNull(ledger.exportRecognitionFishPathSnapshot(processingFrame, maskSha, 42L, contact))
        assertTrue(controller.onFishPathSnapshot(snapshot, runtimeIdentity))
        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 4_000L)
        controller.setResultGeometryState(SceneGeometryState.READY, 4_000L, runtimeIdentity, setOf(resultGeometryVersion))
        val resultScene = controller.currentFrame(runtimeIdentity, 7_000L, resultGeometryVersion)
        val resultFrame = WaterLightFrame(
            sessionId = runtimeIdentity.sessionId,
            photoSha256 = photoSha,
            elapsedMs = resultScene.sceneElapsedMs,
            opticalReferenceElapsedMs = resultScene.opticalReferenceElapsedMs,
            phase = WaterLightPhase.HANDOFF,
            completion = 0f,
            reducedMotion = false,
            geometryVersion = resultGeometryVersion,
            attemptId = runtimeIdentity.attemptId,
            imageId = runtimeIdentity.imageId,
            photoContentVersion = runtimeIdentity.photoContentVersion,
            eventSequence = resultScene.eventSequence,
            sceneFrameOverride = resultScene,
        )
        fun returned(pathId: String) = WaterLightEnergy.at(
            frame = resultFrame,
            trustedMask = true,
            successfulRevealAllowed = true,
            pathGeometry = snapshot.returnPathSegments.first(),
            pathSegments = snapshot.returnPathSegments,
            pathIdOverride = pathId,
            pathMaskSha256 = snapshot.maskSha256,
            pathMaskVersion = snapshot.maskVersion,
            pathGeometryVersion = snapshot.geometryVersion,
            pathContactPointNormalizedSource = snapshot.pStarNormalizedSource,
        )!!

        val reconstructedDefault = returned("fish-to-original-orb")
        assertFalse(controller.recordEnergyEvent(resultScene, reconstructedDefault))
        assertFalse(controller.recordEnergyEvent(resultScene, reconstructedDefault.copy(pathMaskSha256 = "b".repeat(64))))
        assertFalse(controller.recordEnergyEvent(resultScene, reconstructedDefault.copy(pathGeometryVersion = 8L)))
        assertFalse(controller.recordEnergyEvent(resultScene, reconstructedDefault.copy(attemptId = "attempt-2")))

        val exactProcessingRoute = returned(snapshot.returnPathId)
        assertEquals(snapshot.returnPathSegments, exactProcessingRoute.pathSegments)
        val missingReceipt = controller.recordEnergyEventWithReason(resultScene, exactProcessingRoute)
        assertFalse(missingReceipt.accepted)
        assertEquals(
            com.yujian.ai.ui.waterlight.core.EnergyEventRejectionReason.EVENT_RETURN_RENDER_RECEIPT_MISSING,
            missingReceipt.rejectionReason,
        )
        val returnReceipt = RecognitionOrbReturnRenderReceipt(
            identity = runtimeIdentity,
            stage = RecognitionSceneStage.ORB_RETURN,
            pathId = snapshot.returnPathId,
            maskSha256 = snapshot.maskSha256,
            maskVersion = snapshot.maskVersion,
            pathGeometryVersion = snapshot.geometryVersion,
            resultGeometryVersion = resultGeometryVersion,
            pathGeometry = requireNotNull(exactProcessingRoute.pathGeometry),
            pathSegments = snapshot.returnPathSegments,
            renderedSegmentCount = 3,
            renderedProgress = 1f,
            renderedPathLength = 300f,
            pathCanvasSubmitted = true,
            drawPassSequence = 1L,
        )
        assertTrue(controller.onOrbReturnRenderComplete(returnReceipt, runtimeIdentity))
        val frameAfterReceipt = controller.currentFrame(runtimeIdentity, 7_000L, resultGeometryVersion)
        val exactRouteAfterReceipt = exactProcessingRoute.copy(
            eventSequence = frameAfterReceipt.eventSequence,
            monotonicTimestampMs = frameAfterReceipt.monotonicTimestampMs,
            referenceMs = frameAfterReceipt.opticalReferenceElapsedMs,
        )
        assertTrue(controller.recordEnergyEvent(frameAfterReceipt, exactRouteAfterReceipt))
        assertFalse(controller.recordEnergyEvent(frameAfterReceipt, exactRouteAfterReceipt))
        val acceptedReturn = controller.acceptedEnergyEventsSnapshot().single {
            it.stage == RecognitionSceneStage.ORB_RETURN && it.feedback == EnergyFeedback.RETURN
        }
        assertEquals(snapshot.returnPathId, acceptedReturn.pathId)
        assertEquals(snapshot.returnPathSegments, acceptedReturn.pathSegments)
        assertEquals(snapshot.maskSha256, acceptedReturn.pathMaskSha256)
        assertEquals(snapshot.maskVersion, acceptedReturn.pathMaskVersion)
        assertEquals(snapshot.geometryVersion, acceptedReturn.pathGeometryVersion)
        assertEquals(snapshot.pStarNormalizedSource, acceptedReturn.pathContactPointNormalizedSource)
        assertEquals(RecognitionSceneStage.ORB_TO_NAME, controller.advanceToNextStage(7_001L))
    }

    @Test fun orbToNameRequiresARealDrawReceiptForTheFullyDissolvedCore() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        assertTrue(controller.markPhotoFirstFramePresented(identity, 0L))
        controller.setResultGeometryState(SceneGeometryState.READY, 0L, identity, setOf(9L))
        controller.beginStepPreview(RecognitionSceneStage.ORB_TO_NAME, 10L)

        controller.onSceneTransitionComplete(RecognitionSceneStage.ORB_TO_NAME, identity)
        assertFalse("Clock completion cannot stand in for Orb Core dissolution", controller.currentFrame(identity, 900L, 9L).stageEventComplete)
        assertFalse(
            controller.onOrbCoreDissolveRendered(
                RecognitionOrbCoreDissolveReceipt(
                    identity = identity.copy(attemptId = "previous-attempt"),
                    stage = RecognitionSceneStage.ORB_TO_NAME,
                    horizontalStretchProgress = 1f,
                    coreAlpha = 0f,
                    drawPassSequence = 1L,
                ),
            ),
        )
        assertFalse(
            controller.onOrbCoreDissolveRendered(
                RecognitionOrbCoreDissolveReceipt(
                    identity = identity,
                    stage = RecognitionSceneStage.ORB_TO_NAME,
                    horizontalStretchProgress = 1f,
                    coreAlpha = .35f,
                    drawPassSequence = 1L,
                ),
            ),
        )

        val receipt = RecognitionOrbCoreDissolveReceipt(
            identity = identity,
            stage = RecognitionSceneStage.ORB_TO_NAME,
            horizontalStretchProgress = 1f,
            coreAlpha = 0f,
            drawPassSequence = 1L,
        )
        assertTrue(controller.onOrbCoreDissolveRendered(receipt))
        assertEquals(receipt, controller.orbCoreDissolveReceiptSnapshot())
        assertTrue(controller.currentFrame(identity, 900L, 9L).stageEventComplete)
    }

    @Test fun productionHoldsAtNameUntilMeasuredNameStableMilestoneIsAccepted() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        assertTrue(controller.markPhotoFirstFramePresented(identity, 0L))
        controller.setResultGeometryState(SceneGeometryState.READY, 0L, identity, setOf(9L))
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        controller.onRevealDecision(
            RecognitionRevealDecisionState.ORB_ONLY_READY,
            nowMs = 0L,
            currentIdentity = identity,
        )
        controller.beginStepPreview(RecognitionSceneStage.SPECIES_NAME_REVEAL, 0L)
        val descriptor = RecognitionGlyphPathDescriptor(
            identity = identity,
            speciesKey = "species-1",
            speciesName = "黄颡鱼",
            pathId = "glyph:test-path",
            geometryVersion = 17L,
            expectedCharacterCount = 3,
            builtCharacterCount = 3,
            contourCount = 18,
            totalPathLengthPx = 421f,
            bounds = RecognitionGlyphBounds(0f, 0f, 120f, 42f),
        )
        val pathReady = RecognitionGlyphReadiness(
            speciesResultReady = true,
            glyphPathReady = true,
            descriptor = descriptor,
        )
        controller.onGlyphPathReady(pathReady, identity)
        controller.onSceneTransitionComplete(RecognitionSceneStage.SPECIES_NAME_REVEAL, identity)
        controller.playProductionTimeline(0L, 0L)

        assertEquals(RecognitionSceneStage.SPECIES_NAME_REVEAL, controller.currentSceneStage(800L))
        assertFalse("Stage time and transition completion cannot stand in for a Canvas draw", controller.acceptedResultHandoffEventsSnapshot().isNotEmpty())
        val receipt = RecognitionGlyphDrawReceipt(
            identity = identity,
            speciesKey = descriptor.speciesKey,
            speciesName = descriptor.speciesName,
            pathId = descriptor.pathId,
            geometryVersion = descriptor.geometryVersion,
            renderedProgress = 1f,
            fillPathDrawn = true,
            contourPathDrawn = true,
            drawPassSequence = 1L,
        )
        val rendered = pathReady.copy(glyphRevealRendered = true, renderReceipt = receipt)
        controller.onGlyphRevealRendered(rendered, identity)
        val event = RecognitionResultHandoffEvent(
            identity = identity,
            maskSha256 = null,
            maskVersion = null,
            geometryVersion = 9L,
            pathId = null,
            pathGeometryVersion = null,
            pStarNormalizedSource = null,
            pathSegments = emptyList(),
            stage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            substage = ResultHandoffSubstage.NAME_STABLE,
            eventSequence = 1L,
            monotonicTimestampMs = 800L,
            eventProducer = "test.GlyphLayout",
            source = "GLYPH",
            target = "RESULT_NAME",
            feedback = "NAME_STABLE",
            progress = 1f,
            renderStatus = "READY",
            resultIdentity = "species-1",
            glyphReadiness = rendered.copy(nameStable = true),
        )
        assertTrue(controller.acceptResultHandoffEvent(event).accepted)
        assertEquals(RecognitionSceneStage.RESIDUE_SETTLE, controller.currentSceneStage(800L))
    }

}
