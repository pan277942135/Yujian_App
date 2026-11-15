package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.clearR06SampleValidity
import com.yujian.ai.ui.waterlight.renderer.r06ValidSampleRuns
import com.yujian.ai.ui.waterlight.renderer.r06MaximumRawCoreAlphaInRuns
import com.yujian.ai.ui.waterlight.renderer.r06GlassTransmissionBodyAlpha
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

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
    fun glassTransmissionKeepsInteriorSamplesWhenTheChunkMidpointIsOutsideTheMask() {
        val spec = OrbRuntimeProfileV1.paths.single { it.id == "A01" }
        val material = OrbRuntimeProfileV1.materials.getValue(spec.material)
        val lut = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(spec, 0L, it)
        }
        val movement = OrbFilamentPathMathV1.movement(
            spec,
            opticalMs = 0L,
            reduced = false,
            event = OrbRuntimeProfileV1.MotionEvent.NONE,
            eventStartMs = 0L,
        )
        val alphaBySample = FloatArray(97)
        val validSamples = BooleanArray(97)
        val radiusBySample = FloatArray(97) { Float.NaN }
        val scratch = FloatArray(6)
        val steps = 96
        for (i in 0..steps) {
            val u = movement.u + movement.direction * material.trailLengthFraction * (i.toFloat() / steps)
            if (u !in 0f..1f) continue
            validSamples[i] = true
            OrbFilamentPathMathV1.sampleAt(lut, u, scratch, 0)
            OrbFilamentPathMathV1.sampleAt(lut, (u - .003f).coerceIn(0f, 1f), scratch, 2)
            OrbFilamentPathMathV1.sampleAt(lut, (u + .003f).coerceIn(0f, 1f), scratch, 4)
            val dx = scratch[4] - scratch[2]
            val dy = scratch[5] - scratch[3]
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
            val nx = -dy / length
            val ny = dx / length
            val wave = sin(
                (2.0 * PI * (spec.phase + u * 1.7f)).toFloat(),
            ) * spec.normalAmplitudeD
            val x = scratch[0] + nx * wave
            val y = scratch[1] + ny * wave
            val radius = sqrt(x * x + y * y)
            radiusBySample[i] = radius
            // Frozen WebP alpha samples measured for this legal A01 Stage02 frame.
            alphaBySample[i] = when (i) {
                36 -> .2196f
                38 -> .7608f
                39 -> .9294f
                40 -> .9882f
                else -> 0f
            }
        }

        val chunkFirst = 4 * 8
        val chunkLast = 5 * 8
        val representativeIndex = (chunkFirst + chunkLast) / 2
        val representativeRadius = radiusBySample[representativeIndex]
        assertTrue("A01 chunk midpoint is outside the .47D edge mask", representativeRadius > .47f)
        assertTrue("the same current valid run has samples inside the mask",
            (chunkFirst..chunkLast).any { radiusBySample[it] < .47f })

        val runs = r06ValidSampleRuns(validSamples, chunkFirst, chunkLast)
            .filter { it.last - it.first + 1 >= 2 }
        assertTrue("the samples form a current drawable run", runs.isNotEmpty())
        assertTrue("the run includes the midpoint and its inside continuation",
            runs.any { representativeIndex in it && it.last >= 40 })
        val runAlpha = r06MaximumRawCoreAlphaInRuns(alphaBySample, runs)
        assertEquals(.9882f, runAlpha, .00001f)
        assertTrue("the current run contribution must not be scaled by its outside midpoint",
            r06GlassTransmissionBodyAlpha(.45f, runAlpha) >
                r06GlassTransmissionBodyAlpha(.45f, alphaBySample[representativeIndex]))
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
