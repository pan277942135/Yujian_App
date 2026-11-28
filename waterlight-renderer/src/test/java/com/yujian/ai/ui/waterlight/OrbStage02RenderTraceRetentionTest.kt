package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbStage02BoundedEventBuffer
import com.yujian.ai.ui.waterlight.renderer.OrbStage02TraceFrameSample
import com.yujian.ai.ui.waterlight.renderer.manualPausedOrbFrameFingerprint
import com.yujian.ai.ui.waterlight.renderer.nearestOrbWakeCheckpointTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrbStage02RenderTraceRetentionTest {
    @Test
    fun boundedAttemptStreamRetainsRecentRowsAndReportsDroppedCount() {
        val buffer = OrbStage02BoundedEventBuffer(capacity = 3)
        (0..4).forEach { buffer.append("event-$it") }

        assertEquals(listOf("event-2", "event-3", "event-4"), buffer.snapshot())
        assertEquals(2L, buffer.droppedCount)
    }

    @Test
    fun checkpointsPointToNearestRealProductionFramesWithoutSynthesizingFrames() {
        val realFrames = listOf(
            OrbStage02TraceFrameSample("frame-0", 0L),
            OrbStage02TraceFrameSample("frame-79", 79L),
            OrbStage02TraceFrameSample("frame-126", 126L),
            OrbStage02TraceFrameSample("frame-220", 220L),
            OrbStage02TraceFrameSample("frame-281", 281L),
            OrbStage02TraceFrameSample("frame-349", 349L),
        )

        val assignments = nearestOrbWakeCheckpointTargets(realFrames)

        assertEquals(listOf(0L), assignments["frame-0"])
        assertEquals(listOf(80L), assignments["frame-79"])
        assertEquals(listOf(125L), assignments["frame-126"])
        assertEquals(listOf(220L), assignments["frame-220"])
        assertEquals(listOf(280L), assignments["frame-281"])
        assertEquals(listOf(350L), assignments["frame-349"])
        assertEquals(null, assignments["synthetic-frame-350"])
        assertEquals(emptyMap<String, List<Long>>(), nearestOrbWakeCheckpointTargets(emptyList()))
    }

    @Test
    fun manualPausedFrameKeyDeduplicatesOnlyTheSamePausedState() {
        fun key(
            mode: String = "MANUAL",
            paused: Boolean = true,
            geometryVersion: Long = 7L,
        ) = manualPausedOrbFrameFingerprint(
            playbackMode = mode,
            paused = paused,
            stage = "ORB_WAKE",
            stageProgress = 1f,
            stageElapsedMs = 3_000L,
            sceneElapsedMs = 11_097L,
            opticalReferenceElapsedMs = 11_097L,
            geometryVersion = geometryVersion,
            visualProfile = "MAX",
        )

        assertEquals(key(), key())
        assertNotEquals(key(), key(geometryVersion = 8L))
        assertNull(key(mode = "AUTO"))
        assertNull(key(paused = false))
    }
}
