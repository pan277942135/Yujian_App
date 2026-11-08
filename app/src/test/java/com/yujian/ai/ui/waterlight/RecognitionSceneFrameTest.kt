package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneTimelineProjection
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionSceneFrameTest {
    private val identity = RecognitionSceneIdentity("s1", "a1", "i1", "sha", 4L)

    @Test fun semanticStageSetAndOrderRemainExactlyThirteen() {
        assertEquals(
            listOf(
                "PHOTO_STABLE", "ORB_WAKE", "ORB_RELEASE", "PERIMETER_ARRIVAL", "PATH_TO_FISH",
                "CONTOUR_A", "CONTOUR_B", "CONTOUR_C", "FISH_GOLD_PEAK", "ORB_RETURN",
                "ORB_TO_NAME", "SPECIES_NAME_REVEAL", "RESIDUE_SETTLE",
            ),
            RecognitionSceneStage.entries.map(RecognitionSceneStage::name),
        )
    }

    @Test fun frozenFullPathMinimumsAreExactlySixSeconds() {
        assertEquals(
            listOf(0L, 350L, 600L, 125L, 125L, 450L, 450L, 450L, 1_000L, 350L, 850L, 800L, 450L),
            RecognitionSceneStage.entries.map(RecognitionSceneStage::minimumVisibleMs),
        )
        assertEquals(6_000L, RecognitionSceneStage.entries.sumOf(RecognitionSceneStage::minimumVisibleMs))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, RecognitionSceneStage.entries[3])
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, RecognitionSceneStage.entries[4])
    }

    @Test fun explicitSemanticStageIsIndependentOfCumulativeSceneTime() {
        val frame = RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = 45_000L,
            opticalReferenceElapsedMs = 45_000L,
            geometryVersion = 9L,
            stage = RecognitionSceneStage.ORB_RETURN,
            stageElapsedMs = 190L,
            stageProgress = .5f,
        )
        assertEquals(RecognitionSceneStage.ORB_RETURN, frame.stage)
        assertEquals(.5f, frame.stageProgress, .0001f)
        assertEquals(45_000L, frame.sceneElapsedMs)
        assertEquals(9L, frame.geometryVersion)
    }

    @Test fun projectionsRemainDeterministicAndPhotoOrbMotionUsesSceneTime() {
        val first = RecognitionSceneCoordinator.frameForStage(
            identity, 1_550L, 1_550L, 9L, RecognitionSceneStage.CONTOUR_B, 125L, .5f,
        )
        assertEquals(first, RecognitionSceneCoordinator.frameForStage(
            identity, 1_550L, 1_550L, 9L, RecognitionSceneStage.CONTOUR_B, 125L, .5f,
        ))
        assertTrue(first.isIdentityValid)
        assertEquals(RecognitionSceneStage.CONTOUR_B, first.stage)

        listOf(0L, 150L, 350L, 650L, 700L, 899L, 900L).forEach { time ->
            val frame = RecognitionSceneCoordinator.frameForStage(
                identity, time, time, 9L, RecognitionSceneStage.PHOTO_STABLE, 0L, 0f,
            )
            assertEquals((time.toFloat() / 700f).coerceIn(0f, 1f),
                RecognitionSceneTimelineProjection.photoEntryProgress(frame.sceneElapsedMs), .0001f)
            assertEquals(time.coerceAtMost(900L), RecognitionSceneTimelineProjection.orbEntranceElapsedMs(frame.sceneElapsedMs))
        }
        assertEquals(1f, RecognitionSceneTimelineProjection.photoEntryProgress(0L, settled = true), 0f)
    }

    @Test fun newDebugSessionWaitsForRealPhotoFrameThenStaysManualPastThirtySeconds() {
        val playback = SceneMotionCoordinator()
        playback.beginNewSession(identity, debugBuild = true, nowMs = 1_000L)
        assertEquals(ScenePlaybackMode.MANUAL, playback.mode)
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        assertEquals(0L, playback.previewElapsedMs(31_000L))
        assertFalse(playback.canControl())

        assertTrue(playback.markPhotoFirstFramePresented(identity, 40_000L))
        assertEquals(0L, playback.previewElapsedMs(40_000L))
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentSceneStage(70_000L))
        assertTrue(playback.previewCompleted)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.advanceToNextStage(70_000L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentStage)
        assertEquals(0L, playback.previewElapsedMs(70_000L))
    }

    @Test fun productionStageDoesNotAdvanceUntilTheRenderedReleaseEventCompletes() {
        val playback = SceneMotionCoordinator()
        playback.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        playback.markPhotoFirstFramePresented(identity, 0L)
        playback.onOrbAnchorAvailable(true, identity)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentSceneStage(0L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentSceneStage(350L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentSceneStage(949L))

        val path = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(20f, 200f), WaterLightPathPoint(250f, 360f), seed = 4,
        )!!
        val scene = playback.currentFrame(identity, 950L, 9L)
        assertEquals(1f, scene.stageProgress, .0001f)
        val frame = WaterLightFrame(
            sessionId = identity.sessionId,
            photoSha256 = identity.photoSha256,
            elapsedMs = scene.sceneElapsedMs,
            opticalReferenceElapsedMs = scene.opticalReferenceElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = .5f,
            reducedMotion = false,
            geometryVersion = 9L,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoContentVersion = identity.photoContentVersion,
            eventSequence = scene.eventSequence,
            sceneFrameOverride = scene,
        )
        val event = WaterLightEnergy.at(frame, trustedMask = false, pathGeometry = path)
        assertNotNull(event)
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, event?.target)
        assertTrue(playback.recordEnergyEvent(scene, requireNotNull(event)))
        assertFalse(playback.recordEnergyEvent(scene, requireNotNull(event)))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, playback.currentSceneStage(950L))
    }

    @Test fun perimeterArrivalCanBecomeRenderableAfterDetectorTargetArrives() {
        val playback = SceneMotionCoordinator()
        playback.beginNewSession(identity, debugBuild = false, nowMs = 0L)
        playback.markPhotoFirstFramePresented(identity, 0L)
        playback.onOrbAnchorAvailable(true, identity)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentSceneStage(0L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentSceneStage(350L))

        val releaseScene = playback.currentFrame(identity, 950L, 9L)
        val releasePath = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(200f, 360f), WaterLightPathPoint(200f, 24f), seed = 7,
        )!!
        val release = WaterLightEnergy.at(waterLightFrame(releaseScene), false, pathGeometry = releasePath)!!
        assertTrue(playback.recordEnergyEvent(releaseScene, release))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, playback.currentSceneStage(950L))
        playback.onDetectorTargetAvailable(true, identity)
        playback.onPhotoTransformAvailable(true, identity)

        val pendingScene = playback.currentFrame(identity, 1_074L, 9L)
        val pending = WaterLightEnergy.at(waterLightFrame(pendingScene), trustedMask = false)!!
        assertEquals(EnergyFeedback.ARRIVE, pending.feedback)
        assertEquals("PATH_PENDING", pending.renderStatus)
        assertFalse(playback.recordEnergyEvent(pendingScene, pending))
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, playback.currentSceneStage(1_074L))

        val readyScene = playback.currentFrame(identity, 1_075L, 9L)
        val arrivalPath = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(200f, 24f), WaterLightPathPoint(200f, 220f), seed = 7,
        )!!
        val ready = WaterLightEnergy.at(
            waterLightFrame(readyScene), trustedMask = false, pathGeometry = arrivalPath,
        )!!
        assertEquals(EnergyFeedback.ARRIVE, ready.feedback)
        assertEquals("READY", ready.renderStatus)
        assertTrue(playback.recordEnergyEvent(readyScene, ready))
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, playback.currentSceneStage(1_075L))
    }

    private fun waterLightFrame(scene: com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame) = WaterLightFrame(
        sessionId = scene.identity.sessionId,
        photoSha256 = scene.identity.photoSha256,
        elapsedMs = scene.sceneElapsedMs,
        opticalReferenceElapsedMs = scene.opticalReferenceElapsedMs,
        phase = WaterLightPhase.PROCESSING,
        completion = scene.stageProgress,
        reducedMotion = scene.reducedMotion,
        geometryVersion = scene.geometryVersion,
        attemptId = scene.identity.attemptId,
        imageId = scene.identity.imageId,
        photoContentVersion = scene.identity.photoContentVersion,
        eventSequence = scene.eventSequence,
        sceneFrameOverride = scene,
    )

    @Test fun resultHandoffAndFramesCarryTheExactControllerStageAndIdentity() {
        val playback = SceneMotionCoordinator()
        playback.beginNewSession(identity, debugBuild = true, nowMs = 0L)
        playback.markPhotoFirstFramePresented(identity, 1L)
        playback.updateModelJoin(
            detectorReady = true,
            classifierReady = true,
            slimSamTerminal = true,
            maskTrusted = false,
            contourValid = false,
            contourClosed = false,
            currentIdentity = identity,
        )
        playback.onRevealDecisionReady(identity)
        playback.onOrbAnchorAvailable(true, identity)
        playback.beginStepPreview(RecognitionSceneStage.ORB_RETURN, 2L)
        assertTrue(playback.resultVisualReady(2L))
        val frame = playback.currentFrame(identity, 200L, 15L)
        assertEquals(RecognitionSceneStage.ORB_RETURN, frame.stage)
        assertEquals(identity, frame.identity)
        assertEquals(15L, frame.geometryVersion)
        playback.beginStepPreview(RecognitionSceneStage.ORB_TO_NAME, 201L)
        assertFalse(playback.resultVisualReady(201L))
    }
}
