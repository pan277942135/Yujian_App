package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneTimelineProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class RecognitionSceneFrameTest {
    private val identity = RecognitionSceneIdentity("s1", "a1", "i1", "sha", 4L)

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

    @Test fun projectionIsDeterministicAndCarriesIdentityAndPlaybackState() {
        val first = RecognitionSceneCoordinator.frameAt(identity, 1_550L, 1_550L, 9L)
        val replay = RecognitionSceneCoordinator.frameAt(identity, 1_550L, 1_550L, 9L)
        assertEquals(first, replay)
        assertTrue(first.isIdentityValid)
        assertEquals(RecognitionSceneStage.CONTOUR_B, first.stage)
    }

    @Test fun photoSettleAndOrbEntranceAreParallelProjectionsOfTheSameFrame() {
        val samples = listOf(0L, 150L, 350L, 650L, 700L, 899L, 900L)
        samples.forEach { time ->
            val frame = RecognitionSceneCoordinator.frameAt(identity, time, time, 9L)
            assertEquals(
                (time.toFloat() / 700f).coerceIn(0f, 1f),
                RecognitionSceneTimelineProjection.photoEntryProgress(frame.sceneElapsedMs),
                .0001f,
            )
            assertEquals(
                time.coerceAtMost(900L),
                RecognitionSceneTimelineProjection.orbEntranceElapsedMs(frame.sceneElapsedMs),
            )
        }
        assertEquals(1f, RecognitionSceneTimelineProjection.photoEntryProgress(0L, settled = true), 0f)
    }

    @Test fun stageMetadataMatchesTheFrozenThirteenStageContract() {
        assertEquals(
            listOf(0L, 0L, 350L, 950L, 1_075L, 1_200L, 1_450L, 1_700L, 1_950L,
                2_800L, 3_180L, 3_350L, 3_480L),
            RecognitionSceneStage.entries.map { it.startMs },
        )
        assertEquals("鱼体轮廓初显", RecognitionSceneStage.CONTOUR_A.displayName)
        assertEquals("1200–1450ms", RecognitionSceneStage.CONTOUR_A.timeAnchorLabel)
    }

    @Test fun debugDiagnosticSessionDefaultsToAutoAndTracksTheProductionTimeline() {
        val playback = SceneMotionCoordinator()
        assertEquals(ScenePlaybackMode.AUTO, playback.mode)
        assertEquals(0L, playback.sample(0L, 10_000L))
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        assertEquals(349L, playback.sample(349L, 10_349L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentStage)
        assertEquals(350L, playback.sample(350L, 10_350L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentStage)
        playback.beginStepPreview(RecognitionSceneStage.PHOTO_STABLE, 10_351L)
        assertEquals(ScenePlaybackMode.MANUAL, playback.mode)
        assertEquals(0L, playback.previewElapsedMs(10_351L))
    }

    @Test fun everyStageHasAThreeSecondBoundedPreviewAndFreezesAtItsLastFrame() {
        assertEquals(3_000L, com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS)
        RecognitionSceneStage.entries.forEach { stage ->
            val playback = SceneMotionCoordinator()
            playback.beginStepPreview(stage, 10_000L)
            val start = playback.sample(0L, 10_000L)
            assertEquals("$stage start", RecognitionSceneCoordinator.previewReferenceMs(stage, 0L), start)
            assertEquals("$stage midpoint", RecognitionSceneCoordinator.previewReferenceMs(stage, 1_500L),
                playback.sample(0L, 11_500L))
            assertEquals("$stage before end", RecognitionSceneCoordinator.previewReferenceMs(stage, 2_999L),
                playback.sample(0L, 12_999L))
            assertFalse("$stage completed early", playback.previewCompleted)
            val finalReference = playback.sample(0L, 13_000L)
            assertEquals("$stage final frame", RecognitionSceneCoordinator.previewReferenceMs(stage, 3_000L), finalReference)
            assertTrue("$stage did not complete", playback.previewCompleted)
            assertTrue("$stage did not pause", playback.paused)
            assertEquals("$stage stays frozen", finalReference, playback.sample(99_999L, 30_000L))
            assertEquals(stage, playback.currentStage)
        }
    }

    @Test fun pauseAndResumePreserveTheRemainingPreviewDuration() {
        val playback = SceneMotionCoordinator()
        playback.beginStepPreview(RecognitionSceneStage.CONTOUR_B, 1_000L)
        playback.pause(1_500L, 1_500L)
        val frozen = playback.sample(0L, 7_000L)
        assertEquals(500L, playback.previewElapsedMs(7_000L))
        playback.resume(0L, 7_000L)
        assertEquals(frozen, playback.sample(0L, 7_000L))
        assertEquals(2_999L, playback.previewElapsedMs(9_499L))
        assertEquals(
            RecognitionSceneCoordinator.previewReferenceMs(RecognitionSceneStage.CONTOUR_B, 2_999L),
            playback.sample(0L, 9_499L),
        )
        assertEquals(1_699L, playback.sample(0L, 9_500L))
        assertTrue(playback.previewCompleted)
        assertEquals(3_000L, playback.previewElapsedMs(20_000L))
    }

    @Test fun nextPreviousReplayAndProductionModeUseTheSharedController() {
        val playback = SceneMotionCoordinator()
        playback.prepareInitialStepPreview()
        playback.startPreparedStepPreview(0L)
        playback.sample(0L, 3_000L)
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.advanceToNextStage(3_000L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.advanceToNextStage(3_100L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentStage)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.returnToPreviousStage(3_200L))
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.returnToPreviousStage(3_300L))
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        playback.restartCurrentStage(4_000L)
        assertEquals(0L, playback.previewElapsedMs(4_000L))
        playback.playProductionTimeline(1_450L, 8_000L)
        assertEquals(ScenePlaybackMode.AUTO, playback.mode)
        assertEquals(1_450L, playback.sample(1_450L, 8_000L))
        assertEquals(1_550L, playback.sample(1_550L, 8_100L))
        assertEquals(RecognitionSceneStage.CONTOUR_B, playback.currentStage)
    }

    @Test fun diagnosticAutoKeepsThreeSecondStageFloorAndWaitsForAuthoritativeModelGate() {
        val playback = SceneMotionCoordinator()
        playback.playDiagnosticAuto(authoritativeReferenceMs = 0L, nowMs = 0L)

        assertEquals(0L, playback.sample(authoritativeReferenceMs = 1_199L, nowMs = 2_999L))
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)

        assertEquals(
            RecognitionSceneCoordinator.previewReferenceMs(RecognitionSceneStage.ORB_WAKE, 0L),
            playback.sample(authoritativeReferenceMs = 1_199L, nowMs = 3_000L),
        )
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.currentStage)

        // Even after a long diagnostic AUTO run, a production controller held at
        // PATH_TO_FISH cannot be advanced into contour stages by a timer.
        playback.sample(authoritativeReferenceMs = 1_199L, nowMs = 20_000L)
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, playback.currentStage)
        assertEquals(
            RecognitionSceneCoordinator.previewReferenceMs(RecognitionSceneStage.PATH_TO_FISH, 3_000L),
            playback.sample(authoritativeReferenceMs = 1_199L, nowMs = 20_000L),
        )
        playback.sample(authoritativeReferenceMs = 1_199L, nowMs = 60_000L)
        assertEquals(RecognitionSceneStage.PATH_TO_FISH, playback.currentStage)
    }

    @Test fun nextAdvancesOnlyAfterEachThreeSecondStageAndVisitsAllThirteen() {
        val playback = SceneMotionCoordinator()
        playback.prepareInitialStepPreview()
        playback.startPreparedStepPreview(0L)
        var nowMs = 0L
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        RecognitionSceneStage.entries.drop(1).forEach { expected ->
            playback.sample(0L, nowMs + 3_000L)
            assertTrue("${playback.currentStage} should be complete before Next", playback.previewCompleted)
            assertEquals(expected, playback.advanceToNextStage(nowMs + 3_000L))
            assertEquals(expected, playback.currentStage)
            assertEquals(
                RecognitionSceneCoordinator.previewReferenceMs(expected, 0L),
                playback.sample(0L, nowMs + 3_000L),
            )
            nowMs += 3_000L
        }
        assertEquals(RecognitionSceneStage.RESIDUE_SETTLE, playback.currentStage)
        assertNull(playback.advanceToNextStage(nowMs + 3_000L))
    }

    @Test fun manualStageHoldsAfterThirtySecondsAndNextNeverWaitsForMaskOrTimer() {
        val playback = SceneMotionCoordinator()
        playback.prepareInitialStepPreview()
        playback.startPreparedStepPreview(0L)
        assertEquals(RecognitionSceneStage.PHOTO_STABLE, playback.currentStage)
        assertEquals(RecognitionSceneStage.ORB_WAKE, playback.advanceToNextStage(100L))
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.advanceToNextStage(101L))
        playback.sample(0L, 30_101L)
        assertEquals(RecognitionSceneStage.ORB_RELEASE, playback.currentStage)
        assertTrue(playback.previewCompleted)
        playback.pause(0L, 30_102L)
        assertEquals(RecognitionSceneStage.PERIMETER_ARRIVAL, playback.advanceToNextStage(30_103L))
    }

    @Test fun rr09PreviewStepsMapToTheOriginalR0ThroughR5Timeline() {
        val expected = listOf(
            RecognitionSceneStage.ORB_RETURN to (2_800L to 3_179L),
            RecognitionSceneStage.ORB_TO_NAME to (3_180L to 3_349L),
            RecognitionSceneStage.SPECIES_NAME_REVEAL to (3_350L to 3_479L),
            RecognitionSceneStage.RESIDUE_SETTLE to (3_480L to 3_650L),
        )
        expected.forEach { (stage, range) ->
            assertEquals(range.first, RecognitionSceneCoordinator.previewReferenceMs(stage, 0L))
            assertEquals(range.second, RecognitionSceneCoordinator.previewReferenceMs(stage, 3_000L))
        }
        assertEquals(0L, RecognitionSceneCoordinator.previewReferenceMs(RecognitionSceneStage.ORB_RETURN, 0L) - 2_800L)
        assertEquals(380L, RecognitionSceneStage.ORB_TO_NAME.startMs - 2_800L)
        assertEquals(550L, RecognitionSceneStage.SPECIES_NAME_REVEAL.startMs - 2_800L)
        assertEquals(680L, RecognitionSceneStage.RESIDUE_SETTLE.startMs - 2_800L)
        assertEquals(850L, RecognitionSceneCoordinator.previewReferenceMs(RecognitionSceneStage.RESIDUE_SETTLE, 3_000L) - 2_800L)
    }

    @Test fun modeAndPhotoIdentityDoNotChangeWhenReprojectingSameFrame() {
        val normal = RecognitionSceneCoordinator.frameAt(identity, 1_650L, 1_650L, 11L)
        val diagnostic = RecognitionSceneCoordinator.frameAt(
            identity, 1_650L, 1_650L, 11L, ScenePlaybackMode.MANUAL, paused = true,
        )
        assertEquals(normal.stage, diagnostic.stage)
        assertEquals(normal.stageProgress, diagnostic.stageProgress)
        assertEquals(normal.opticalReferenceElapsedMs, diagnostic.opticalReferenceElapsedMs)
        assertEquals(normal.identity, diagnostic.identity)
        assertEquals(normal.geometryVersion, diagnostic.geometryVersion)
    }
}
