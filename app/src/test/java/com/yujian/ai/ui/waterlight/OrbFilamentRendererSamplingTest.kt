package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.clearR06SampleValidity
import com.yujian.ai.ui.waterlight.renderer.r06ValidSampleRuns
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbFilamentRendererSamplingTest {
    @Test
    fun releaseTrailAtStartAndReturnTrailAtEndKeepOnlyCurrentFrameSamples() {
        val valid = BooleanArray(97)
        clearR06SampleValidity(valid, 96)
        for (i in 0..12) valid[i] = true // outward release entering from u=0
        assertEquals(listOf(0..12), r06ValidSampleRuns(valid, 0, 20))

        clearR06SampleValidity(valid, 96)
        for (i in 84..96) valid[i] = true // return moving toward u=1
        assertEquals(listOf(84..96), r06ValidSampleRuns(valid, 76, 96))
        assertFalse(valid[12]) // no sample survives from the previous animation frame
    }

    @Test
    fun clippedSamplesSplitPathsInsteadOfConnectingAcrossTheGap() {
        val valid = BooleanArray(97)
        for (i in 0..3) valid[i] = true
        for (i in 7..11) valid[i] = true
        assertEquals(listOf(0..3, 7..11), r06ValidSampleRuns(valid, 0, 11))
    }

    @Test
    fun reducedAndFullSamplingClearEveryIndexUsedByTheCurrentFrame() {
        val valid = BooleanArray(97) { true }
        clearR06SampleValidity(valid, 48)
        assertTrue(valid.take(49).none { it })
        assertTrue(valid.drop(49).all { it }) // indices outside this frame's sampling tier are never read
        clearR06SampleValidity(valid, 96)
        assertTrue(valid.none { it })
    }

    @Test
    fun stageLocalReleaseMovesOutwardThenReturnsWithoutChangingTheFrozenPath() {
        val spec = OrbRuntimeProfileV1.paths.first()
        val sceneMs = 1_200L
        val base = OrbFilamentPathMathV1.movement(
            spec, sceneMs, reduced = false,
            event = OrbRuntimeProfileV1.MotionEvent.NONE, eventStartMs = 0L,
        )
        val outward = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, sceneMs, RecognitionSceneStage.ORB_RELEASE, .45f,
            reduced = false, pathLengthD = spec.pathLengthD, diameterDp = 120f,
        )
        val returning = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, sceneMs, RecognitionSceneStage.ORB_RELEASE, .88f,
            reduced = false, pathLengthD = spec.pathLengthD, diameterDp = 120f,
        )
        val outsideRelease = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, sceneMs, RecognitionSceneStage.CONTOUR_A, .45f,
            reduced = false, pathLengthD = spec.pathLengthD, diameterDp = 120f,
        )

        assertTrue("release expands outward from the cached path", outward.unclampedU > base.unclampedU)
        assertTrue("return follows the same cached path in reverse", returning.direction < 0f)
        assertEquals("outside release stage, R06 returns to its base geometry",
            base.unclampedU, outsideRelease.unclampedU, .0001f)
    }

    @Test
    fun bothSamplingTiersPreserveOnlyCurrentEndpointRuns() {
        listOf(48, 96).forEach { steps ->
            val valid = BooleanArray(97)
            clearR06SampleValidity(valid, steps)
            valid[0] = true
            valid[1] = true
            valid[steps - 1] = true
            valid[steps] = true
            assertEquals("start endpoint at $steps samples", listOf(0..1, (steps - 1)..steps),
                r06ValidSampleRuns(valid, 0, steps))
            clearR06SampleValidity(valid, steps)
            assertTrue("frame changes invalidate stale samples at $steps", valid.take(steps + 1).none { it })
        }
    }
}
