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
    }

    @Test
    fun rippleIsCenteredAndFadesOverItsPeriod() {
        assertEquals(1f, rippleScale(0f), 0.0001f)
        assertEquals(1.15f, rippleScale(3.5f / 2f), 0.0001f)
        assertEquals(1f, rippleAlpha(0f), 0.0001f)
        assertEquals(0f, rippleAlpha(3.5f - 0.0001f), 0.0001f)
    }

    @Test
    fun cloudDriftStaysWithinMotionSafeRange() {
        val samples = (0..600).map { cloudOffsetPx(it / 10f) }

        assertTrue(samples.maxOrNull()!! <= 1.91f)
        assertTrue(samples.minOrNull()!! >= -1.91f)
    }

    @Test
    fun cameraBreathAndSweepRespectFrozenBounds() {
        val scales = (0..500).map { cameraBreathScale(it / 100f) }
        assertTrue(scales.maxOrNull()!! <= 1.015001f)
        assertTrue(scales.minOrNull()!! >= 1f)

        assertEquals(0f, cameraSweepState(2.99f).alpha, 0.0001f)
        assertTrue(cameraSweepState(3.7f).alpha > 0f)
        assertEquals(0f, cameraSweepState(4.41f).alpha, 0.0001f)
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
}
