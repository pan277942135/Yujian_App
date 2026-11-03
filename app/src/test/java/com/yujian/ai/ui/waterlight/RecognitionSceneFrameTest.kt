package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.EnergyAnchor
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

    @Test fun mapsAllThirteenStagesAtFrozenBoundaries() {
        val boundaries = listOf(
            0L to RecognitionSceneStage.PHOTO_STABLE,
            1L to RecognitionSceneStage.ORB_WAKE,
            350L to RecognitionSceneStage.ORB_RELEASE,
            950L to RecognitionSceneStage.PERIMETER_ARRIVAL,
            1_075L to RecognitionSceneStage.PATH_TO_FISH,
            1_200L to RecognitionSceneStage.CONTOUR_A,
            1_450L to RecognitionSceneStage.CONTOUR_B,
            1_700L to RecognitionSceneStage.CONTOUR_C,
            1_950L to RecognitionSceneStage.FISH_GOLD_PEAK,
            2_800L to RecognitionSceneStage.ORB_RETURN,
            3_180L to RecognitionSceneStage.ORB_TO_NAME,
            3_350L to RecognitionSceneStage.SPECIES_NAME_REVEAL,
            3_480L to RecognitionSceneStage.RESIDUE_SETTLE,
        )
        boundaries.forEach { (time, stage) ->
            val frame = RecognitionSceneCoordinator.frameAt(identity, time, time, 9L)
            assertEquals("reference=$time", stage, frame.stage)
            assertTrue(frame.stageProgress in 0f..1f)
            assertEquals(9L, frame.geometryVersion)
        }
        assertEquals(1f, RecognitionSceneCoordinator.frameAt(identity, 3_650L, 3_650L, 9L).stageProgress, .0001f)
    }

    @Test fun projectionsRemainDeterministicAndPhotoOrbMotionUsesSceneTime() {
        val first = RecognitionSceneCoordinator.frameAt(identity, 1_550L, 1_550L, 9L)
        assertEquals(first, RecognitionSceneCoordinator.frameAt(identity, 1_550L, 1_550L, 9L))
        assertTrue(first.isIdentityValid)
        assertEquals(RecognitionSceneStage.CONTOUR_B, first.stage)

        listOf(0L, 150L, 350L, 650L, 700L, 899L, 900L).forEach { time ->
            val frame = RecognitionSceneCoordinator.frameAt(identity, time, time, 9L)
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
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentSceneStage(120L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentSceneStage(470L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentSceneStage(1_070L))

        val path = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(20f, 200f), WaterLightPathPoint(250f, 360f), seed = 4,
        )!!
        val scene = playback.currentFrame(identity, 1_071L, 9L)
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
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, playback.currentSceneStage(1_072L))
    }

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
