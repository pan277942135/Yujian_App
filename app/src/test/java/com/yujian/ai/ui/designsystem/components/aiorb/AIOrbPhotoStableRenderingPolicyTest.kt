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
}
