package com.yujian.ai.ui.designsystem.components.aiorb

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AIOrbPhotoStableRenderingPolicyTest {
    @Test fun photoStableRendersTheOriginalCoreWithoutAnOpticalEffect() {
        assertFalse(
            orbCoreOpticalEffectEnabled(
                RecognitionSceneStage.PHOTO_STABLE,
            ),
        )
    }

    @Test fun orbWakeKeepsItsExistingOpticalRendererEnabled() {
        assertTrue(orbCoreOpticalEffectEnabled(RecognitionSceneStage.ORB_WAKE))
    }

    @Test fun nonIdleStagesKeepTheSharedOpticalRendererAvailable() {
        assertTrue(
            orbCoreOpticalEffectEnabled(
                RecognitionSceneStage.ORB_RELEASE,
            ),
        )
    }

    @Test fun threeWakeModesKeepTheOriginalCoreAndGateOnlyTheOptionalLayers() {
        val stage = RecognitionSceneStage.ORB_WAKE
        assertFalse(orbOpticsVisible(stage, OrbWakeRenderMode.CORE_ONLY))
        assertFalse(orbWakeDetailsVisible(OrbWakeRenderMode.CORE_ONLY))
        assertTrue(orbOpticsVisible(stage, OrbWakeRenderMode.CORE_WITH_OPTICS))
        assertFalse(orbWakeDetailsVisible(OrbWakeRenderMode.CORE_WITH_OPTICS))
        assertTrue(orbOpticsVisible(stage, OrbWakeRenderMode.FULL_ORB_WAKE))
        assertTrue(orbWakeDetailsVisible(OrbWakeRenderMode.FULL_ORB_WAKE))
        assertFalse(orbOpticsVisible(RecognitionSceneStage.PHOTO_STABLE, OrbWakeRenderMode.FULL_ORB_WAKE))
        assertTrue(orbOpticsVisible(RecognitionSceneStage.ORB_RELEASE, OrbWakeRenderMode.FULL_ORB_WAKE))
        assertTrue(aiOrbMotionFrame(350L, false, false, 0f).waterAlpha == 1f)
    }
}
