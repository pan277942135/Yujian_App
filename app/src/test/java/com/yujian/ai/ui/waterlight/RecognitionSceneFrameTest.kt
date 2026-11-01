package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneTimelineProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun manualSeekPauseResumeAndAutoReturnAreContinuous() {
        val playback = SceneMotionCoordinator()
        assertEquals(ScenePlaybackMode.AUTO, playback.mode)
        assertEquals(1_200L, playback.sample(1_200L, 10_000L))

        playback.step(1, RecognitionSceneStage.PATH_TO_FISH, 1_200L)
        assertEquals(ScenePlaybackMode.MANUAL, playback.mode)
        assertTrue(playback.paused)
        assertEquals(1_200L, playback.sample(1_600L, 10_000L))

        playback.resume(1_600L, 10_000L)
        assertEquals(1_200L, playback.sample(1_600L, 10_000L))
        assertEquals(1_400L, playback.sample(1_600L, 10_200L))
        playback.pause(1_600L, 10_250L)
        assertEquals(1_450L, playback.sample(2_000L, 20_000L))

        playback.playAuto(2_000L, 20_000L)
        assertEquals(ScenePlaybackMode.AUTO, playback.mode)
        assertEquals(1_450L, playback.sample(2_000L, 20_000L))
        assertEquals(1_550L, playback.sample(2_100L, 20_100L))
    }

    @Test fun nextAndPreviousVisitEverySharedSceneStageInOrder() {
        val playback = SceneMotionCoordinator()
        var current = RecognitionSceneStage.PHOTO_STABLE
        RecognitionSceneStage.entries.drop(1).forEachIndexed { index, expected ->
            playback.step(1, current, 1_000L + index)
            val reference = playback.sample(0L, 1_000L + index)
            val frame = RecognitionSceneCoordinator.frameAt(identity, reference, reference, 9L,
                playback.mode, playback.paused)
            assertEquals(expected, frame.stage)
            current = expected
        }
        playback.step(-1, current, 2_000L)
        val previousTime = playback.sample(0L, 2_000L)
        assertEquals(RecognitionSceneStage.SPECIES_NAME_REVEAL,
            RecognitionSceneCoordinator.frameAt(identity, previousTime, previousTime, 9L).stage)
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
