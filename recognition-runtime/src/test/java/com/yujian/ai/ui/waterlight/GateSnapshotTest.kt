package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneAnimationController
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateSnapshotTest {
    @Test fun firstBlockedGateNamesMissingOrbAnchorWithoutAdvancing() {
        val controller = RecognitionSceneAnimationController(mapOf(
            RecognitionSceneStage.PHOTO_STABLE to 0L, RecognitionSceneStage.ORB_WAKE to 10L,
        ))
        controller.reset(0L)
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentSceneStage(0L))
        assertEquals(RecognitionSceneStage.ORB_WAKE, controller.currentSceneStage(11L))
        val snapshot = controller.gateSnapshot(11L)
        assertEquals("ORB_WAKE", snapshot["stage"])
        assertEquals(listOf("orbAnchorReady"), snapshot["unmetConditions"])
        assertFalse(snapshot["orbAnchorReady"] == true)
        assertTrue((snapshot["stageElapsedMs"] as Long) >= 10L)
    }
}
