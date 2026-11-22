package com.yujian.ai.ui.waterlight.core

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPathGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphBounds
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphPathDescriptor
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage04ArrivalPathWithoutMaskTest {
    @Test fun arrivalCoincidentWithPhotoEdgeTravelsAlongBoundaryWithoutEnteringPhoto() {
        val transform = RecognitionImageTransform(
            sourceWidthPx = 1_000, sourceHeightPx = 800,
            viewportWidthPx = 500f, viewportHeightPx = 600f,
            contentScaleMode = RecognitionContentScaleMode.FIT,
            scale = .5f, translationX = 0f, translationY = 100f,
            drawnWidth = 500f, drawnHeight = 400f,
            visibleSourceRect = NormalizedSourceRect(0f, 0f, 1f, 1f),
        )
        val photo = transform.photoContentRect
        val release = WaterLightPathPoint(250f, photo.bottom)
        for (box in listOf(
            NormalizedFishBox(.3f, .2f, .7f, .8f),
            NormalizedFishBox(.9f, .9f, 1f, 1f),
            NormalizedFishBox(0f, 0f, .1f, .1f),
        )) {
            val path = requireNotNull(RecognitionRevealPathGeometry.perimeterArrival(transform, box, release, 12))
            assertTrue(path.isValid())
            val onBoundary = { point: WaterLightPathPoint ->
                kotlin.math.abs(point.x - photo.left) < .01f ||
                    kotlin.math.abs(point.x - photo.right) < .01f ||
                    kotlin.math.abs(point.y - photo.top) < .01f ||
                    kotlin.math.abs(point.y - photo.bottom) < .01f
            }
            assertTrue(onBoundary(path.end))
            assertFalse(path.end.x > photo.left && path.end.x < photo.right &&
                path.end.y > photo.top && path.end.y < photo.bottom)
        }
    }

    @Test fun stage04ArrivalPathAndEventCompleteWhileSlimSamIsPendingThenOrbOnlySkipsContours() {
        val fixture = stage04ReadyFixture()
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, fixture.controller.currentStage)
        assertEquals(SceneMaskState.PENDING, fixture.controller.modelGates.slimSam)
        assertNotNull(fixture.ledger.perimeterArrivalPath(fixture.arrivalFrame))
        assertNull(fixture.ledger.fishPath(fixture.arrivalFrame, "c".repeat(64), 9L, WaterLightPathPoint(.5f, .5f)))

        fixture.controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = fixture.identity,
        )
        fixture.controller.onRevealDecision(
            RecognitionRevealDecisionState.ORB_ONLY_READY,
            nowMs = 2_075L,
            currentIdentity = fixture.identity,
        )
        assertEquals(RecognitionSceneStage.ORB_RETURN, fixture.controller.currentSceneStage(2_200L))
        assertFalse(fixture.controller.stageTimelineSnapshot().any {
            it.stage in setOf(
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C,
                RecognitionSceneStage.FISH_GOLD_PEAK,
            )
        })
    }

    @Test fun fullRevealRequiresRealMaskClosedContourAndBoundPStarThroughOrbReturn() {
        val fixture = stage04ReadyFixture()
        val maskSha = "a".repeat(64)
        val maskVersion = 42L
        val contact = WaterLightPathPoint(.50f, .56f)
        fixture.controller.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = true,
            contourValid = true,
            contourClosed = true,
            currentIdentity = fixture.identity,
            maskSha256 = maskSha,
            maskVersion = maskVersion,
        )
        val taskSplitPoint = requireNotNull(fixture.ledger.perimeterArrivalPath(fixture.arrivalFrame)).geometry.end
        val fishGeometry = requireNotNull(
            RecognitionRevealPathGeometry.fishContact(
                fixture.transform,
                contact,
                seed = fixture.identity.sessionId.hashCode() xor fixture.identity.imageId.hashCode(),
                taskSplitPoint = taskSplitPoint,
            ),
        )
        val pathFrame = controllerFrame(fixture.controller, fixture.identity, fixture.geometryVersion, 2_200L)
        assertTrue(
            fixture.ledger.recordFishPath(
                frame = pathFrame,
                geometry = fishGeometry,
                trustedMask = true,
                maskSha256 = maskSha,
                maskVersion = maskVersion,
                contactPointNormalizedSource = contact,
            ),
        )
        val fishPathId = requireNotNull(fixture.ledger.pathId(pathFrame, maskSha, maskVersion, contact))
        assertTrue(
            fixture.controller.onFishContactPathAvailable(
                pathId = fishPathId,
                maskSha256 = maskSha,
                maskVersion = maskVersion,
                geometryVersion = fixture.geometryVersion,
                contactPointNormalizedSource = contact,
                currentIdentity = fixture.identity,
            ),
        )
        fixture.controller.onRevealDecision(
            RecognitionRevealDecisionState.FISH_FOCUS_READY,
            nowMs = 2_200L,
            currentIdentity = fixture.identity,
        )
        val absorbFrame = controllerFrame(fixture.controller, fixture.identity, fixture.geometryVersion, 2_200L)
        val absorbEvent = requireNotNull(
            WaterLightEnergy.at(
                frame = absorbFrame,
                trustedMask = true,
                pathGeometry = fishGeometry,
                pathIdOverride = fishPathId,
                pathMaskSha256 = maskSha,
                pathMaskVersion = maskVersion,
                pathGeometryVersion = fixture.geometryVersion,
                pathContactPointNormalizedSource = contact,
            ),
        )
        assertEquals(EnergyFeedback.ABSORB, absorbEvent.feedback)
        assertTrue(fixture.controller.recordEnergyEventWithReason(absorbFrame.sceneFrame, absorbEvent).accepted)
        assertEquals(RecognitionSceneStage.CONTOUR_A, fixture.controller.currentSceneStage(2_200L))

        val snapshotFrame = controllerFrame(fixture.controller, fixture.identity, fixture.geometryVersion, 2_200L)
        val pathSnapshot = requireNotNull(
            fixture.ledger.exportRecognitionFishPathSnapshot(snapshotFrame, maskSha, maskVersion, contact),
        )
        assertTrue(pathSnapshot.isValid())
        assertTrue(fixture.controller.onFishPathSnapshot(pathSnapshot, fixture.identity))

        assertEquals(RecognitionSceneStage.CONTOUR_A, fixture.controller.currentSceneStage(2_200L))
        assertFalse(fixture.controller.onContourRenderComplete(
            contourReceipt(fixture, maskSha, maskVersion, fishPathId, RecognitionSceneStage.CONTOUR_B, 2_650L, 1f / 3f, 1L),
            fixture.identity,
        ))
        assertTrue(fixture.controller.onContourRenderComplete(
            contourReceipt(fixture, maskSha, maskVersion, fishPathId, RecognitionSceneStage.CONTOUR_A, 2_650L, 1f / 3f, 2L),
            fixture.identity,
        ))
        assertEquals(RecognitionSceneStage.CONTOUR_B, fixture.controller.currentSceneStage(2_650L))
        assertTrue(fixture.controller.onContourRenderComplete(
            contourReceipt(fixture, maskSha, maskVersion, fishPathId, RecognitionSceneStage.CONTOUR_B, 3_100L, 2f / 3f, 3L),
            fixture.identity,
        ))
        assertEquals(RecognitionSceneStage.CONTOUR_C, fixture.controller.currentSceneStage(3_100L))
        assertTrue(fixture.controller.onContourRenderComplete(
            contourReceipt(fixture, maskSha, maskVersion, fishPathId, RecognitionSceneStage.CONTOUR_C, 3_550L, 1f, 4L),
            fixture.identity,
        ))
        assertEquals(RecognitionSceneStage.FISH_GOLD_PEAK, fixture.controller.currentSceneStage(3_550L))
        assertTrue(fixture.controller.onFishGoldRenderComplete(
            RecognitionFishGoldRenderReceipt(
                identity = fixture.identity,
                stage = RecognitionSceneStage.FISH_GOLD_PEAK,
                maskSha256 = maskSha,
                maskVersion = maskVersion,
                geometryVersion = fixture.geometryVersion,
                fishPathId = fishPathId,
                renderedProgress = 1f,
                contourDrawn = true,
                goldCoreDrawn = true,
                softGlowDrawn = true,
                outerHaloDrawn = true,
                glintDrawn = true,
                drawPassSequence = 5L,
            ),
            fixture.identity,
        ))
        val glyphDescriptor = RecognitionGlyphPathDescriptor(
            identity = fixture.identity,
            speciesKey = "yellow_catfish",
            speciesName = "黄颡鱼",
            pathId = "glyph:${fixture.identity.attemptId}:yellow_catfish",
            geometryVersion = fixture.geometryVersion,
            expectedCharacterCount = 3,
            builtCharacterCount = 3,
            contourCount = 18,
            totalPathLengthPx = 421f,
            bounds = RecognitionGlyphBounds(0f, 0f, 120f, 42f),
        )
        fixture.controller.onGlyphPathReady(
            RecognitionGlyphReadiness(
                speciesResultReady = true,
                glyphPathReady = true,
                descriptor = glyphDescriptor,
            ),
            fixture.identity,
        )
        fixture.controller.setResultGeometryState(
            SceneGeometryState.READY,
            nowMs = 3_550L,
            currentIdentity = fixture.identity,
            geometryVersions = setOf(fixture.geometryVersion),
        )
        assertEquals(RecognitionSceneStage.ORB_RETURN, fixture.controller.currentSceneStage(4_550L))

        val returnFrame = controllerFrame(fixture.controller, fixture.identity, fixture.geometryVersion, 4_900L)
        val returnEvent = requireNotNull(
            WaterLightEnergy.at(
                frame = returnFrame,
                trustedMask = true,
                successfulRevealAllowed = true,
                pathIdOverride = pathSnapshot.returnPathId,
                pathSegments = pathSnapshot.returnPathSegments,
                pathMaskSha256 = pathSnapshot.maskSha256,
                pathMaskVersion = pathSnapshot.maskVersion,
                pathGeometryVersion = pathSnapshot.geometryVersion,
                pathContactPointNormalizedSource = pathSnapshot.pStarNormalizedSource,
            ),
        )
        assertEquals(EnergyFeedback.RETURN, returnEvent.feedback)
        val renderedReturn = RecognitionOrbReturnRenderReceipt(
            identity = fixture.identity,
            stage = RecognitionSceneStage.ORB_RETURN,
            pathId = pathSnapshot.returnPathId,
            maskSha256 = pathSnapshot.maskSha256,
            maskVersion = pathSnapshot.maskVersion,
            pathGeometryVersion = pathSnapshot.geometryVersion,
            resultGeometryVersion = returnEvent.geometryVersion,
            pathGeometry = requireNotNull(returnEvent.pathGeometry),
            pathSegments = pathSnapshot.returnPathSegments,
            renderedSegmentCount = 3,
            renderedProgress = 1f,
            renderedPathLength = 500f,
            pathCanvasSubmitted = true,
            drawPassSequence = 6L,
        )
        assertTrue(fixture.controller.onOrbReturnRenderComplete(renderedReturn, fixture.identity))
        val returnFrameAfterReceipt = controllerFrame(
            fixture.controller, fixture.identity, fixture.geometryVersion, 4_900L,
        )
        val eventAfterReceipt = returnEvent.copy(
            eventSequence = returnFrameAfterReceipt.sceneFrame.eventSequence,
            monotonicTimestampMs = returnFrameAfterReceipt.sceneFrame.monotonicTimestampMs,
            referenceMs = returnFrameAfterReceipt.sceneFrame.opticalReferenceElapsedMs,
        )
        assertTrue(fixture.controller.recordEnergyEventWithReason(
            returnFrameAfterReceipt.sceneFrame,
            eventAfterReceipt,
        ).accepted)
        assertEquals(RecognitionSceneStage.ORB_TO_NAME, fixture.controller.currentSceneStage(4_900L))
        val visited = fixture.controller.stageTimelineSnapshot().map { it.stage }
        assertTrue(visited.containsAll(listOf(
            RecognitionSceneStage.PATH_TO_FISH,
            RecognitionSceneStage.CONTOUR_A,
            RecognitionSceneStage.CONTOUR_B,
            RecognitionSceneStage.CONTOUR_C,
            RecognitionSceneStage.FISH_GOLD_PEAK,
            RecognitionSceneStage.ORB_RETURN,
        )))
        assertEquals(ScenePlaybackMode.AUTO, fixture.controller.mode)
        assertEquals(6_000L, RecognitionSceneStage.entries.sumOf { it.minimumVisibleMs })
    }

    private data class Fixture(
        val controller: RecognitionSceneAnimationController,
        val identity: RecognitionSceneIdentity,
        val geometryVersion: Long,
        val ledger: WaterLightPathLedger,
        val transform: RecognitionImageTransform,
        val arrivalFrame: WaterLightFrame,
    )

    private fun stage04ReadyFixture(): Fixture {
        val controller = RecognitionSceneAnimationController()
        val identity = RecognitionSceneIdentity(
            sessionId = "session-stage04",
            attemptId = "attempt-stage04",
            imageId = "image-stage04",
            photoSha256 = "b".repeat(64),
            photoContentVersion = 4L,
        )
        val geometryVersion = 37L
        val ledger = WaterLightPathLedger()
        val transform = RecognitionImageTransform(
            sourceWidthPx = 1_000,
            sourceHeightPx = 800,
            viewportWidthPx = 500f,
            viewportHeightPx = 600f,
            contentScaleMode = RecognitionContentScaleMode.FIT,
            scale = .5f,
            translationX = 0f,
            translationY = 100f,
            drawnWidth = 500f,
            drawnHeight = 400f,
            visibleSourceRect = NormalizedSourceRect(0f, 0f, 1f, 1f),
        )
        val detectorBox = NormalizedFishBox(.30f, .20f, .70f, .80f)
        controller.beginNewSession(identity, debugBuild = false, nowMs = 1_000L)
        assertTrue(controller.markPhotoFirstFramePresented(identity, 1_000L))
        controller.onOrbAnchorAvailable(true, identity)
        controller.onDetectorTargetAvailable(true, identity)
        controller.onPhotoTransformAvailable(true, identity, geometryVersion)

        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentSceneStage(1_000L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.currentSceneStage(1_350L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, controller.currentSceneStage(1_950L))

        val releaseFrame = controllerFrame(controller, identity, geometryVersion, 1_950L)
        val releasePath = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(250f, 550f),
            WaterLightPathPoint(250f, 500f),
            seed = 11,
        )!!
        assertTrue(ledger.recordRelease(releaseFrame, releasePath))
        val releaseEvent = requireNotNull(
            WaterLightEnergy.at(releaseFrame, trustedMask = false, pathGeometry = releasePath),
        )
        assertEquals(EnergyFeedback.RELEASE, releaseEvent.feedback)
        assertTrue(controller.recordEnergyEventWithReason(releaseFrame.sceneFrame, releaseEvent).accepted)
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, controller.currentSceneStage(1_950L))

        val arrivalFrame = controllerFrame(controller, identity, geometryVersion, 2_075L)
        val arrivalGeometry = requireNotNull(
            RecognitionRevealPathGeometry.perimeterArrival(
                transform = transform,
                detectorBox = detectorBox,
                releaseEndpoint = requireNotNull(ledger.releasePath(arrivalFrame)).end,
                seed = 12,
            ),
        )
        assertTrue(ledger.recordPerimeterArrivalPath(arrivalFrame, arrivalGeometry))
        val arrival = requireNotNull(ledger.perimeterArrivalPath(arrivalFrame))
        assertTrue(arrival.isValid())
        val photo = transform.photoContentRect
        assertTrue(
            "Arrival must terminate at the photo boundary, not inside the detector box",
            kotlin.math.abs(arrival.geometry.end.x - photo.left) < .01f ||
                kotlin.math.abs(arrival.geometry.end.x - photo.right) < .01f ||
                kotlin.math.abs(arrival.geometry.end.y - photo.top) < .01f ||
                kotlin.math.abs(arrival.geometry.end.y - photo.bottom) < .01f,
        )
        assertTrue(controller.onPerimeterArrivalPathAvailable(
            pathId = arrival.pathId,
            pathGeometry = arrival.geometry,
            geometryVersion = arrival.geometryVersion,
            currentIdentity = identity,
        ))
        val arriveEvent = requireNotNull(
            WaterLightEnergy.at(
                frame = arrivalFrame,
                trustedMask = false,
                pathGeometry = arrival.geometry,
                pathIdOverride = arrival.pathId,
            ),
        )
        assertEquals(EnergyFeedback.ARRIVE, arriveEvent.feedback)
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, arriveEvent.source)
        assertEquals(EnergyAnchor.PHOTO_PATH, arriveEvent.target)
        assertNull(arriveEvent.pathMaskSha256)
        assertNull(arriveEvent.pathMaskVersion)
        assertNull(arriveEvent.pathContactPointNormalizedSource)
        val rejected = controller.recordEnergyEventWithReason(
            arrivalFrame.sceneFrame,
            arriveEvent.copy(pathId = "${arrival.pathId}:stale"),
        )
        assertEquals(EnergyEventRejectionReason.EVENT_PATH_INVALID, rejected.rejectionReason)
        assertEquals(EnergyEventRejectionReason.EVENT_PATH_INVALID, controller.lastRejectedEnergyEvent?.reason)
        assertTrue(controller.recordEnergyEventWithReason(arrivalFrame.sceneFrame, arriveEvent).accepted)
        assertEquals(EnergyEventRejectionReason.EVENT_PATH_INVALID, controller.lastRejectedEnergyEvent?.reason)
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, controller.currentSceneStage(2_075L))
        return Fixture(controller, identity, geometryVersion, ledger, transform, arrivalFrame)
    }

    private fun controllerFrame(
        controller: RecognitionSceneAnimationController,
        identity: RecognitionSceneIdentity,
        geometryVersion: Long,
        nowMs: Long,
    ): WaterLightFrame {
        val scene = controller.currentFrame(identity, nowMs, geometryVersion)
        return WaterLightFrame(
            sessionId = identity.sessionId,
            photoSha256 = identity.photoSha256,
            elapsedMs = scene.sceneElapsedMs,
            opticalReferenceElapsedMs = scene.opticalReferenceElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = .5f,
            reducedMotion = false,
            geometryVersion = geometryVersion,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            eventSequence = scene.eventSequence,
            sceneFrameOverride = scene,
        )
    }

    private fun contourReceipt(
        fixture: Fixture,
        maskSha: String,
        maskVersion: Long,
        fishPathId: String,
        stage: RecognitionSceneStage,
        nowMs: Long,
        renderedProgress: Float,
        drawPassSequence: Long,
    ) = RecognitionContourRenderReceipt(
        identity = fixture.identity,
        stage = stage,
        maskSha256 = maskSha,
        maskVersion = maskVersion,
        geometryVersion = fixture.geometryVersion,
        fishPathId = fishPathId,
        stageProgress = controllerFrame(
            fixture.controller, fixture.identity, fixture.geometryVersion, nowMs,
        ).sceneFrame.stageProgress,
        renderedContourProgress = renderedProgress,
        contourStrokeDrawn = true,
        drawPassSequence = drawPassSequence,
    )
}
