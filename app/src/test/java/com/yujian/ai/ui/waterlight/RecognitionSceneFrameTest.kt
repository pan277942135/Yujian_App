package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
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
}
