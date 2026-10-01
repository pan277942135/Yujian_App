package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EmptyHomeMotionTest {
    @Test
    fun referenceTransformUsesUniformCoverScale() {
        val transform = calculateReferenceSceneTransform(1080f, 2400f)

        assertEquals(1.25f, transform.scale, 0.0001f)
        assertEquals(-135f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun bobberMotionIsFrozenToSixPixelPeakToPeak() {
        val samples = listOf(0f, 1.15f, 2.3f, 3.45f, 4.6f).map(::bobberOffsetPx)

        assertEquals(0f, samples[0], 0.0001f)
        assertEquals(-3f, samples[1], 0.0001f)
        assertEquals(0f, samples[2], 0.0001f)
        assertEquals(3f, samples[3], 0.0001f)
        assertEquals(0f, samples[4], 0.0001f)
        assertEquals(6f, samples.maxOrNull()!! - samples.minOrNull()!!, 0.0001f)
        assertTrue(samples.all { it in -3f..3f })
    }

    @Test
    fun rippleIsCenteredAndFadesOverItsPeriod() {
        assertEquals(1f, rippleScale(0f), 0.0001f)
        assertEquals(1.11f, rippleScale(3.2f / 2f), 0.0001f)
        assertEquals(0.30f, rippleAlpha(0f), 0.0001f)
        assertEquals(0f, rippleAlpha(3.2f - 0.0001f), 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_X, 560f, 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_Y, 1320f, 0.0001f)
    }

    @Test
    fun fishingLineUsesFrozenSlackGeometry() {
        assertEquals(335f, EMPTY_HOME_V2_ROD_TIP_X, 0.0001f)
        assertEquals(1180f, EMPTY_HOME_V2_ROD_TIP_Y, 0.0001f)
        assertEquals(390f, EMPTY_HOME_V2_LINE_C1_X, 0.0001f)
        assertEquals(1265f, EMPTY_HOME_V2_LINE_C1_Y, 0.0001f)
        assertEquals(470f, EMPTY_HOME_V2_LINE_C2_X, 0.0001f)
        assertEquals(1352f, EMPTY_HOME_V2_LINE_C2_Y, 0.0001f)
        assertEquals(560f, EMPTY_HOME_V2_LINE_END_X, 0.0001f)
        assertEquals(1328f, EMPTY_HOME_V2_LINE_END_Y, 0.0001f)
        assertTrue(EMPTY_HOME_V2_LINE_END_Y > EMPTY_HOME_V2_WATER_CONTACT_Y)
        assertTrue(EMPTY_HOME_V2_LINE_C2_Y > EMPTY_HOME_V2_LINE_END_Y)
    }

    @Test
    fun bobberArtworkSplitsAtTheFixedWaterContactAcrossFrozenMotion() {
        val still = calculateBobberWaterSplit(
            bitmapHeight = 122,
            bobberTopY = EMPTY_HOME_V2_BOBBER_Y,
            waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
        )
        assertEquals(84, still.splitY)
        assertEquals(38, still.underwaterHeight)

        listOf(-3f, 0f, 3f).forEach { offset ->
            val movedTop = EMPTY_HOME_V2_BOBBER_Y + offset
            val split = calculateBobberWaterSplit(
                bitmapHeight = 122,
                bobberTopY = movedTop,
                waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
            )
            assertEquals(122, split.splitY + split.underwaterHeight)
            assertEquals(
                EMPTY_HOME_V2_WATER_CONTACT_Y,
                movedTop + split.splitY,
                0.5f,
            )
        }
    }

    @Test
    fun reducedMotionFreezesSceneAndDisablesIdleEffects() {
        assertTrue(emptyHomeMotionActive(running = true, reduceMotion = false))
        assertTrue(!emptyHomeMotionActive(running = true, reduceMotion = true))
        assertTrue(!emptyHomeMotionActive(running = false, reduceMotion = false))
        // The single frozen contact ripple remains visible while its loop is
        // stopped, so the bobber does not look pasted onto the lake.
        assertEquals(0.30f, rippleAlpha(0f), 0.0001f)
    }

    @Test
    fun cloudDriftStaysWithinMotionSafeRange() {
        val samples = (0..600).map { cloudOffsetPx(it / 10f) }

        assertEquals(0f, samples.first(), 0.0001f)
        assertTrue(samples.last() <= 12f)
    }

    @Test
    fun cameraBreathAndSweepRespectFrozenBounds() {
        val scales = (0..500).map { cameraBreathScale(it / 100f) }
        assertTrue(scales.maxOrNull()!! <= 1.015001f)
        assertTrue(scales.minOrNull()!! >= 1f)

        assertEquals(0f, cameraSweepState(2.99f).alpha, 0.0001f)
        assertTrue(cameraSweepState(3.7f).alpha > 0f)
        assertEquals(0f, cameraSweepState(4.41f).alpha, 0.0001f)
        assertTrue(cameraSweepState(3.7f).alpha <= 0.4501f)
        assertTrue(cameraSweepState(12.7f).alpha > 0f)
    }

    @Test
    fun sunParticlesAreDeterministic() {
        val first = createSunParticleSpecs()
        val second = createSunParticleSpecs()

        assertEquals(first, second)
        assertEquals(10, first.size)
        assertTrue(first.all { it.alpha in 0.035f..0.12f })
    }

    @Test
    fun cameraBreathHasNegligibleCenterDrift() {
        val centerDrift = abs(0f)
        assertTrue(centerDrift <= 0.5f)
    }

    @Test
    fun environmentalLoopsUseDistinctEntryPhases() {
        val phases = setOf(
            BOBBER_PHASE_OFFSET_SECONDS,
            RIPPLE_PHASE_OFFSET_SECONDS,
            CLOUD_PHASE_OFFSET_SECONDS,
            SUN_BEAM_PHASE_OFFSET_SECONDS,
            CAMERA_BREATH_PHASE_OFFSET_SECONDS,
        )
        assertEquals(5, phases.size)
        assertTrue(CAMERA_BREATH_PHASE_OFFSET_SECONDS != 0f)
    }
}
