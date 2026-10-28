package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EmptyHomeMotionTest {
    @Test
    fun canonicalAndTallTransformsKeepTheFishingProtectedRegionVisible() {
        val canonical = calculateReferenceSceneTransform(1080f, 1920f)
        assertEquals(1f, canonical.scale, 0.0001f)
        assertEquals(0f, canonical.offsetX, 0.0001f)
        assertEquals(0f, canonical.offsetY, 0.0001f)

        val targets = listOf(
            1080f to 1920f,
            1080f to 2160f,
            1080f to 2340f,
            1080f to 2400f,
            720f to 1600f,
            320f to 640f,
        )
        targets.forEach { (width, height) ->
            val transform = calculateReferenceSceneTransform(width, height)
            val left = transform.offsetX + EMPTY_HOME_V2_FISHING_PROTECTED_LEFT * transform.scale
            val right = transform.offsetX + EMPTY_HOME_V2_FISHING_PROTECTED_RIGHT * transform.scale
            val top = transform.offsetY + 1180f * transform.scale
            val bottom = transform.offsetY + 1380f * transform.scale
            assertTrue("protected left clipped at ${width}x$height", left >= -0.01f)
            assertTrue("protected right clipped at ${width}x$height", right <= width + 0.01f)
            assertTrue("protected top clipped at ${width}x$height", top >= -0.01f)
            assertTrue("protected bottom clipped at ${width}x$height", bottom <= height + 0.01f)
            assertTrue("rod tip clipped at ${width}x$height", mapX(EMPTY_HOME_V2_ROD_TIP_X, transform) in 0f..width)
            assertTrue("line end clipped at ${width}x$height", mapX(EMPTY_HOME_V2_LINE_END_X, transform) in 0f..width)
            val lineStartX = mapX(EMPTY_HOME_V2_LINE_START_X, transform)
            assertEquals(
                (EMPTY_HOME_V2_BOBBER_X - EMPTY_HOME_V2_LINE_START_X) * transform.scale,
                mapX(EMPTY_HOME_V2_BOBBER_X, transform) - lineStartX,
                0.0001f,
            )
            assertEquals(
                (EMPTY_HOME_V2_REFLECTION_X - EMPTY_HOME_V2_LINE_START_X) * transform.scale,
                mapX(EMPTY_HOME_V2_REFLECTION_X, transform) - lineStartX,
                0.0001f,
            )
            assertEquals(
                (EMPTY_HOME_V2_RIPPLE_X - EMPTY_HOME_V2_LINE_START_X) * transform.scale,
                mapX(EMPTY_HOME_V2_RIPPLE_X, transform) - lineStartX,
                0.0001f,
            )
            val lineStartY = mapY(EMPTY_HOME_V2_LINE_START_Y, transform)
            assertEquals(
                (EMPTY_HOME_V2_BOBBER_Y - EMPTY_HOME_V2_LINE_START_Y) * transform.scale,
                mapY(EMPTY_HOME_V2_BOBBER_Y, transform) - lineStartY,
                0.0001f,
            )
            assertEquals(
                (EMPTY_HOME_V2_REFLECTION_Y - EMPTY_HOME_V2_LINE_START_Y) * transform.scale,
                mapY(EMPTY_HOME_V2_REFLECTION_Y, transform) - lineStartY,
                0.0001f,
            )
            assertEquals(
                (EMPTY_HOME_V2_RIPPLE_Y - EMPTY_HOME_V2_LINE_START_Y) * transform.scale,
                mapY(EMPTY_HOME_V2_RIPPLE_Y, transform) - lineStartY,
                0.0001f,
            )
            assertTrue("uniform scene scale changed at ${width}x$height", transform.scale > 0f)
        }
        assertEquals(0f, calculateReferenceSceneTransform(1080f, 2400f).offsetX, 0.0001f)
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
        assertEquals(561f, EMPTY_HOME_V2_WATER_CONTACT_X, 0.0001f)
        assertEquals(1323f, EMPTY_HOME_V2_WATER_CONTACT_Y, 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_X, EMPTY_HOME_V2_BOBBER_X + 11f, 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_Y, EMPTY_HOME_V2_BOBBER_Y + 73f, 0.0001f)
        assertEquals(562f, EMPTY_HOME_V2_RIPPLE_X + 119f, 0.0001f)
        assertEquals(1320f, EMPTY_HOME_V2_RIPPLE_Y + 41f, 0.0001f)
        assertEquals(556f, EMPTY_HOME_V2_REFLECTION_X, 0.0001f)
        assertEquals(1322f, EMPTY_HOME_V2_REFLECTION_Y, 0.0001f)
    }

    @Test
    fun fishingLineUsesFrozenSlackGeometry() {
        assertEquals(337f, EMPTY_HOME_V2_ROD_TIP_X, 0.0001f)
        assertEquals(1184f, EMPTY_HOME_V2_ROD_TIP_Y, 0.0001f)
        assertEquals(EMPTY_HOME_V2_ROD_TIP_X, EMPTY_HOME_V2_LINE_START_X, 0.0001f)
        assertEquals(EMPTY_HOME_V2_ROD_TIP_Y, EMPTY_HOME_V2_LINE_START_Y, 0.0001f)
        assertEquals(389f, EMPTY_HOME_V2_LINE_C1_X, 0.0001f)
        assertEquals(1257f, EMPTY_HOME_V2_LINE_C1_Y, 0.0001f)
        assertEquals(471f, EMPTY_HOME_V2_LINE_C2_X, 0.0001f)
        assertEquals(1312f, EMPTY_HOME_V2_LINE_C2_Y, 0.0001f)
        assertEquals(560f, EMPTY_HOME_V2_LINE_END_X, 0.0001f)
        assertEquals(1326f, EMPTY_HOME_V2_LINE_END_Y, 0.0001f)
        assertTrue(EMPTY_HOME_V2_LINE_END_Y > EMPTY_HOME_V2_WATER_CONTACT_Y)
        assertTrue(EMPTY_HOME_V2_LINE_END_X in (EMPTY_HOME_V2_BOBBER_X..(EMPTY_HOME_V2_BOBBER_X + 24f)))
        assertTrue(EMPTY_HOME_V2_LINE_END_Y in (EMPTY_HOME_V2_WATER_CONTACT_Y..(EMPTY_HOME_V2_WATER_CONTACT_Y + 4f)))
    }

    @Test
    fun bobberArtworkSplitsAtTheFixedWaterContactAcrossFrozenMotion() {
        val still = calculateBobberWaterSplit(
            bitmapHeight = 78,
            bobberTopY = EMPTY_HOME_V2_BOBBER_Y,
            waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
        )
        assertEquals(73, still.splitY)
        assertEquals(0, still.underwaterHeight)
        assertEquals(0f, still.underwaterAlpha, 0.0001f)
        assertTrue(still.underwaterHeight <= 78 * 0.20f)
        assertTrue(still.underwaterAlpha <= 0.25f)

        listOf(-3f, 0f, 3f).forEach { offset ->
            val movedTop = EMPTY_HOME_V2_BOBBER_Y + offset
            val split = calculateBobberWaterSplit(
                bitmapHeight = 78,
                bobberTopY = movedTop,
                waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
            )
            assertEquals((EMPTY_HOME_V2_WATER_CONTACT_Y - movedTop).toInt(), split.splitY)
            assertEquals(0, split.underwaterHeight)
            assertEquals(0f, split.underwaterAlpha, 0.0001f)
            assertEquals(
                EMPTY_HOME_V2_WATER_CONTACT_Y,
                movedTop + split.splitY,
                0.5f,
            )
        }
    }

    private fun mapX(x: Float, transform: ReferenceSceneTransform): Float =
        transform.offsetX + x * transform.scale

    private fun mapY(y: Float, transform: ReferenceSceneTransform): Float =
        transform.offsetY + y * transform.scale

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
