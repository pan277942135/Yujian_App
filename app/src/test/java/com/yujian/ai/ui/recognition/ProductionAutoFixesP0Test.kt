package com.yujian.ai.ui.recognition

import com.yujian.ai.ui.recognition.diagnostics.RecognitionEvidenceCapturePolicy
import com.yujian.ai.ui.recognition.diagnostics.RecognitionPixelCopyCapture
import com.yujian.ai.ui.recognition.reveal.RecognitionFishPathSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphBounds
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphDrawReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphPathDescriptor
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.recognition.reveal.ResultStableReadiness
import com.yujian.ai.ui.waterlight.core.EnergyEventRejectionReason
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneMaskState
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRole
import com.yujian.ai.ui.waterlight.renderer.OrbResidentFilamentMotionV1
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import com.yujian.ai.ui.waterlight.renderer.usesProductionOptics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionAutoFixesP0Test {
    private val identity = RecognitionSceneIdentity(
        sessionId = "session-p0",
        attemptId = "attempt-p0",
        imageId = "image-p0",
        photoSha256 = "b".repeat(64),
        photoContentVersion = 1L,
    )

    @Test
    fun p0_0_pixelCopyTimeoutBudgetAndStatusPolicyRemainExplicit() {
        assertEquals(2_000L, RecognitionPixelCopyCapture.DEFAULT_TIMEOUT_MS)
        assertEquals(
            "PIXELCOPY_TIMEOUT",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus(
                pixelCopyStatus = "PIXELCOPY_TIMEOUT",
                requestedStage = "ORB_WAKE",
                completedStage = "ORB_WAKE",
                sameSceneIdentityAndGeometry = true,
            ),
        )
        assertEquals(
            "STAGE_IDENTITY_UNVERIFIED",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus(
                pixelCopyStatus = "SUCCESS",
                requestedStage = "ORB_WAKE",
                completedStage = "ORB_RELEASE",
                sameSceneIdentityAndGeometry = true,
            ),
        )
        assertTrue(
            RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(
                requestedIdentity = identity,
                completedIdentity = identity,
                requestedGeometryVersion = 17L,
                completedGeometryVersion = 17L,
            ),
        )
        assertFalse(
            RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(
                requestedIdentity = identity,
                completedIdentity = identity,
                requestedGeometryVersion = 17L,
                completedGeometryVersion = 18L,
            ),
        )
    }

    @Test
    fun p0_1_absorbRejectedUnderOrbOnlyReadyAndAcceptedUnderFishFocusWithContourC() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 0L)
        controller.onPhotoTransformAvailable(true, identity, geometryVersion = 17L)
        val maskSha = "a".repeat(64)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = identity,
            maskSha256 = maskSha,
            maskVersion = 3L,
        )
        val pStar = WaterLightPathPoint(.42f, .61f)
        val expectedPathId =
            "waterlight:${identity.sessionId}:${identity.attemptId}:${identity.imageId}:${identity.photoContentVersion}:17:mask:${maskSha.take(16)}:3:0.420000:0.610000"
        assertTrue(
            controller.onFishContactPathAvailable(
                pathId = expectedPathId,
                maskSha256 = maskSha,
                maskVersion = 3L,
                geometryVersion = 17L,
                contactPointNormalizedSource = pStar,
                currentIdentity = identity,
            ),
        )
        controller.onRevealDecision(RecognitionRevealDecisionState.FISH_FOCUS_READY, 10L, identity)
        assertEquals(RecognitionRevealDecisionState.FISH_FOCUS_READY, controller.currentRevealDecision())
        controller.beginStepPreview(RecognitionSceneStage.CONTOUR_C, 100L)

        val sceneFrame = controller.currentFrame(identity, 700L, 17L).copy(stageProgress = 1f)
        val wlFrame = WaterLightFrame(
            sessionId = identity.sessionId,
            photoSha256 = identity.photoSha256,
            elapsedMs = sceneFrame.opticalReferenceElapsedMs,
            opticalReferenceElapsedMs = sceneFrame.opticalReferenceElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = 1f,
            reducedMotion = false,
            geometryVersion = 17L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            eventSequence = sceneFrame.eventSequence,
            sceneFrameOverride = sceneFrame,
        )
        val pathGeometry = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(10f, 20f),
            WaterLightPathPoint(110f, 120f),
            seed = 1,
        )!!
        val absorbC = WaterLightEnergy.at(
            frame = wlFrame,
            trustedMask = true,
            pathGeometry = pathGeometry,
            pathIdOverride = expectedPathId,
            pathMaskSha256 = maskSha,
            pathMaskVersion = 3L,
            pathGeometryVersion = 17L,
            pathContactPointNormalizedSource = pStar,
        )!!
        assertEquals(EnergyFeedback.ABSORB, absorbC.feedback)
        assertEquals(1f, absorbC.level, .001f)
        assertTrue(controller.recordEnergyEventWithReason(sceneFrame, absorbC).accepted)

        // Switching same Attempt to ORB_ONLY_READY explicitly rejects ABSORB without accepting fake paths
        controller.onRevealDecision(RecognitionRevealDecisionState.ORB_ONLY_READY, 750L, identity)
        assertEquals(RecognitionRevealDecisionState.ORB_ONLY_READY, controller.currentRevealDecision())
        val rejectedFrame = controller.currentFrame(identity, 800L, 17L).copy(stageProgress = 1f)
        val dupAttempt = absorbC.copy(
            pathId = "$expectedPathId:alt",
            eventSequence = rejectedFrame.eventSequence,
            monotonicTimestampMs = rejectedFrame.monotonicTimestampMs,
            referenceMs = rejectedFrame.opticalReferenceElapsedMs,
        )
        val rejection = controller.recordEnergyEventWithReason(rejectedFrame, dupAttempt)
        assertFalse(rejection.accepted)
        assertEquals(EnergyEventRejectionReason.EVENT_MASK_IDENTITY_MISMATCH, rejection.rejectionReason)
    }

    @Test
    fun p0_2_productionV1AppliesR06WakeStrokeEnvelopeAndBodyAlphaReadability() {
        assertTrue(OrbStage02VisualProfile.PRODUCTION_V1.usesProductionOptics())
        assertFalse(OrbStage02VisualProfile.BASELINE.usesProductionOptics())
        assertEquals(OrbFilamentRole.RESIDENT_AMBIENT, OrbResidentFilamentMotionV1.role("G01"))

        val wakeEnvelope = OrbWakeBreathReleaseV11.r06WakeReadabilityEnvelope(RecognitionSceneStage.ORB_WAKE, 1f)
        assertEquals(1f, wakeEnvelope, .0001f)

        val wakeBodyAlpha = OrbWakeBreathReleaseV11.r06WakeBodyAlpha(
            stage = RecognitionSceneStage.ORB_WAKE,
            stageProgress = 1f,
            originalLayerAlpha = .03f,
            layerId = "translucent_body",
            front = true,
            active = 1f,
            endFade = 1f,
            tailAlpha = 1f,
            profileOpacity = 1f,
            crossOpacity = 1f,
        )
        assertEquals(.12f, wakeBodyAlpha, .0001f)
        assertTrue(wakeBodyAlpha > .03f)
    }

    @Test
    fun p0_3_nonReadyOrDecodeFailureMaskTerminalMarksSlimSamFailedImmediately() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 0L)
        controller.onMaskTerminal(
            trusted = false,
            contourValid = false,
            contourClosed = false,
            failedTerminal = true,
            currentIdentity = identity,
        )
        assertEquals(SceneMaskState.FAILED, controller.modelGates.slimSam)
        assertFalse(controller.modelGates.maskTrusted)
    }

    @Test
    fun p0_4_onGlyphRevealRenderedReturnsBooleanAndRequiresMatchingStage() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 0L)
        controller.beginStepPreview(RecognitionSceneStage.ORB_TO_NAME, 10L)

        val descriptor = RecognitionGlyphPathDescriptor(
            identity = identity,
            speciesKey = "species-1",
            speciesName = "黄颡鱼",
            pathId = "glyph:test",
            geometryVersion = 9L,
            expectedCharacterCount = 3,
            builtCharacterCount = 3,
            contourCount = 12,
            totalPathLengthPx = 300f,
            bounds = RecognitionGlyphBounds(0f, 0f, 100f, 32f),
        )
        val pathReady = RecognitionGlyphReadiness(
            speciesResultReady = true,
            glyphPathReady = true,
            descriptor = descriptor,
        )
        controller.onGlyphPathReady(pathReady, identity)
        val receipt = RecognitionGlyphDrawReceipt(
            identity = identity,
            speciesKey = "species-1",
            speciesName = "黄颡鱼",
            pathId = "glyph:test",
            geometryVersion = 9L,
            renderedProgress = 1f,
            fillPathDrawn = true,
            contourPathDrawn = true,
            drawPassSequence = 1L,
        )
        val rendered = pathReady.copy(glyphRevealRendered = true, renderReceipt = receipt)

        // Rejected in ORB_TO_NAME so caller can retry cleanly when Stage 12 begins
        assertFalse(controller.onGlyphRevealRendered(rendered, identity))
        controller.beginStepPreview(RecognitionSceneStage.SPECIES_NAME_REVEAL, 20L)
        assertTrue(controller.onGlyphRevealRendered(rendered, identity))
    }

    @Test
    fun p0_5_resultStableReadinessSeparatesOpticalResidueAndSceneStable() {
        val incompleteResidue = ResultStableReadiness.evaluateOpticalResidue(
            stage = RecognitionSceneStage.RESIDUE_SETTLE,
            stageEventComplete = true,
            stageProgress = 1f,
            afterglowAlpha = .05f,
        )
        assertFalse(incompleteResidue.complete)
        assertEquals("AFTERGLOW_ACTIVE", incompleteResidue.waitingReason)

        val completeResidue = ResultStableReadiness.evaluateOpticalResidue(
            stage = RecognitionSceneStage.RESIDUE_SETTLE,
            stageEventComplete = true,
            stageProgress = 1f,
            afterglowAlpha = 0f,
        )
        assertTrue(completeResidue.complete)

        val prereqs = setOf(
            ResultHandoffSubstage.NAME_STABLE,
            ResultHandoffSubstage.PHOTO_SETTLE,
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
            ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
        )
        val waitingInfoEntrance = ResultStableReadiness.evaluateResultSceneStable(
            opticalResidue = completeResidue,
            resultInfoLayoutReady = true,
            resultGeometryReady = true,
            photoTransformAtTarget = true,
            heroRenderReady = true,
            photoProgress = 1f,
            resultInfoEntranceProgress = .6f,
            eventSequenceSynced = true,
            completedSubstages = prereqs,
            alreadyStable = false,
        )
        assertFalse(waitingInfoEntrance.ready)
        assertEquals("INFO_ENTRANCE_INCOMPLETE", waitingInfoEntrance.waitingReason)

        val ready = ResultStableReadiness.evaluateResultSceneStable(
            opticalResidue = completeResidue,
            resultInfoLayoutReady = true,
            resultGeometryReady = true,
            photoTransformAtTarget = true,
            heroRenderReady = true,
            photoProgress = 1f,
            resultInfoEntranceProgress = 1f,
            eventSequenceSynced = true,
            completedSubstages = prereqs,
            alreadyStable = false,
        )
        assertTrue(ready.ready)
    }

    @Test
    fun p0_6_resultVisualReadyWaitsForFishPathSnapshotWhenMaskIsTrusted() {
        val controller = SceneMotionCoordinator()
        controller.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        controller.markPhotoFirstFramePresented(identity, 0L)
        controller.onPhotoTransformAvailable(true, identity, geometryVersion = 21L)
        val maskSha = "c".repeat(64)
        controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = identity,
            maskSha256 = maskSha,
            maskVersion = 4L,
        )
        val pStar = WaterLightPathPoint(.5f, .5f)
        val expectedPathId =
            "waterlight:${identity.sessionId}:${identity.attemptId}:${identity.imageId}:${identity.photoContentVersion}:21:mask:${maskSha.take(16)}:4:0.500000:0.500000"
        assertTrue(controller.onFishContactPathAvailable(expectedPathId, maskSha, 4L, 21L, pStar, identity))
        controller.onRevealDecision(RecognitionRevealDecisionState.FISH_FOCUS_READY, 10L, identity)
        controller.onOrbAnchorAvailable(true, identity)
        controller.beginStepPreview(RecognitionSceneStage.CONTOUR_A, 15L)

        val contourFrame = controller.currentFrame(identity, 500L, 21L).copy(stageProgress = 1f)
        val absorb = WaterLightEnergy.at(
            frame = WaterLightFrame(
                sessionId = identity.sessionId,
                photoSha256 = identity.photoSha256,
                elapsedMs = contourFrame.opticalReferenceElapsedMs,
                opticalReferenceElapsedMs = contourFrame.opticalReferenceElapsedMs,
                phase = WaterLightPhase.PROCESSING,
                completion = 1f,
                reducedMotion = false,
                geometryVersion = 21L,
                attemptId = identity.attemptId,
                imageId = identity.imageId,
                photoContentVersion = identity.photoContentVersion,
                eventSequence = contourFrame.eventSequence,
                sceneFrameOverride = contourFrame,
            ),
            trustedMask = true,
            pathGeometry = WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(20f, 30f),
                WaterLightPathPoint(120f, 130f),
                seed = 2,
            ),
            pathIdOverride = expectedPathId,
            pathMaskSha256 = maskSha,
            pathMaskVersion = 4L,
            pathGeometryVersion = 21L,
            pathContactPointNormalizedSource = pStar,
        )!!
        assertTrue(controller.recordEnergyEventWithReason(contourFrame, absorb).accepted)

        controller.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 600L)
        assertFalse(controller.fishPathSnapshotReady())
        assertFalse(controller.resultVisualReady(600L))

        val seg1 = WaterLightPathGeometryFactory.between(WaterLightPathPoint(100f, 100f), WaterLightPathPoint(80f, 80f), seed = 1)!!
        val seg2 = WaterLightPathGeometryFactory.between(WaterLightPathPoint(80f, 80f), WaterLightPathPoint(50f, 50f), seed = 2)!!
        val seg3 = WaterLightPathGeometryFactory.between(WaterLightPathPoint(50f, 50f), WaterLightPathPoint(10f, 10f), seed = 3)!!
        val snapshot = RecognitionFishPathSnapshot(
            sessionId = identity.sessionId,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoSha256 = identity.photoSha256,
            photoContentVersion = identity.photoContentVersion,
            maskSha256 = maskSha,
            maskVersion = 4L,
            geometryVersion = 21L,
            pStarNormalizedSource = pStar,
            fishPathId = expectedPathId,
            fishPathGeometry = seg3,
            perimeterBridge = seg2,
            orbReleasePath = seg1,
            returnPathId = expectedPathId,
            returnPathSegments = listOf(seg1, seg2, seg3),
            sourceCoordinateSpace = "RECOGNITION_VIEWPORT_PX",
            creationTimestampMonotonicMs = 600L,
            creationEventSequence = contourFrame.eventSequence,
        )
        assertTrue(controller.onFishPathSnapshot(snapshot, identity))
        assertTrue(controller.fishPathSnapshotReady())
        assertTrue(controller.resultVisualReady(600L))
    }
}
