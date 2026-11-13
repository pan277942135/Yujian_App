package com.yujian.ai.ui.designsystem.components.aiorb

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbRenderVisibilityContractTest {
    @Test
    fun sourceCoreOpticsWakeOnlyAfterPhotoStableAndFirstEightyWakeMilliseconds() {
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.PHOTO_STABLE, 10_000L))
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 0L))
        assertFalse(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 79L))
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE, 80L))
        assertTrue(aiOrbOpticalEffectEnabled(RecognitionSceneStage.ORB_RELEASE, 0L))
    }

    @Test
    fun completionCannotHideOrbBeforeGoldPeakAndClampsAfterward() {
        RecognitionSceneStage.entries.takeWhile { it.ordinal <= RecognitionSceneStage.FISH_GOLD_PEAK.ordinal }
            .forEach { stage -> assertEquals(stage.name, 0f, aiOrbPresentationCompletion(stage, 1f), 0f) }
        assertEquals(0.4f, aiOrbPresentationCompletion(RecognitionSceneStage.ORB_RETURN, .4f), 0f)
        assertEquals(1f, aiOrbPresentationCompletion(RecognitionSceneStage.RESIDUE_SETTLE, 2f), 0f)
    }
}
