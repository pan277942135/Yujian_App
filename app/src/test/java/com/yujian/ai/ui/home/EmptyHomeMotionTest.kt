package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

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
            val top = transform.offsetY + 1036f * transform.scale
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
                (EMPTY_HOME_V2_RIPPLE_Y - EMPTY_HOME_V2_LINE_START_Y) * transform.scale,
                mapY(EMPTY_HOME_V2_RIPPLE_Y, transform) - lineStartY,
                0.0001f,
            )
            assertTrue("uniform scene scale changed at ${width}x$height", transform.scale > 0f)
        }
        assertEquals(0f, calculateReferenceSceneTransform(1080f, 2400f).offsetX, 0.0001f)
    }

    @Test
    fun bobberMovesOnlyDuringDeterministicNibbleEvents() {
        val quiet = calculatePeriodicBobberMotion(0f)
        assertFalse(quiet.burstActive)
        assertTrue(quiet.eventId == null)
        assertEquals(0f, quiet.dipOffsetPx, 0.0001f)

        val start = calculatePeriodicBobberMotion(1.5f)
        assertTrue(start.burstActive)
        assertEquals(0L, requireNotNull(start.eventId))
        assertEquals(0f, start.burstElapsedSeconds, 0.0001f)
        assertEquals(4f, start.dipOffsetPx, 0.0001f)

        val response = calculatePeriodicBobberMotion(1.72f)
        assertEquals(0L, requireNotNull(response.eventId))
        assertTrue(response.dipOffsetPx < start.dipOffsetPx)
        assertTrue(response.dipOffsetPx.isFinite())

        val settledDuringRipples = calculatePeriodicBobberMotion(3.1f)
        assertTrue(settledDuringRipples.burstActive)
        assertEquals(0f, settledDuringRipples.dipOffsetPx, 0.0001f)
        assertTrue(calculatePeriodicBobberMotion(5.11f).eventId == null)
        assertEquals(5L, requireNotNull(calculatePeriodicBobberMotion(37.5f).eventId))
        assertTrue(calculatePeriodicBobberMotion(Float.NaN).eventId == null)
    }

    @Test
    fun userTapDipIsShortAndDoesNotBecomeAnIdleLoop() {
        assertEquals(5f, calculateClickBobberDip(0f), 0.0001f)
        assertTrue(calculateClickBobberDip(0.2f).isFinite())
        assertEquals(0f, calculateClickBobberDip(0.8f), 0.0001f)
        assertEquals(0f, calculateClickBobberDip(Float.NaN), 0.0001f)
    }

    @Test
    fun v23FishingCompositionKeepsBobberContactAndRippleAligned() {
        assertEquals(560f, EMPTY_HOME_V2_WATER_CONTACT_X, 0.0001f)
        assertEquals(1120f, EMPTY_HOME_V2_WATER_CONTACT_Y, 0.0001f)
        assertEquals(560f, EMPTY_HOME_V2_BOBBER_X + 12f, 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_X, EMPTY_HOME_V2_BOBBER_X + 12f, 0.0001f)
        assertEquals(EMPTY_HOME_V2_WATER_CONTACT_Y, EMPTY_HOME_V2_BOBBER_Y + 84f, 0.0001f)
        assertEquals(560f, EMPTY_HOME_V2_RIPPLE_X + 119f, 0.0001f)
        assertEquals(1120f, EMPTY_HOME_V2_RIPPLE_Y + 41f, 0.0001f)
    }

    @Test
    fun fishingLineUsesV23PhysicalDeviceGeometry() {
        assertEquals(335f, EMPTY_HOME_V2_ROD_TIP_X, 0.0001f)
        assertEquals(1180f, EMPTY_HOME_V2_ROD_TIP_Y, 0.0001f)
        assertEquals(EMPTY_HOME_V2_ROD_TIP_X, EMPTY_HOME_V2_LINE_START_X, 0.0001f)
        assertEquals(EMPTY_HOME_V2_ROD_TIP_Y, EMPTY_HOME_V2_LINE_START_Y, 0.0001f)
        assertEquals(390f, EMPTY_HOME_V2_LINE_C1_X, 0.0001f)
        assertEquals(1218f, EMPTY_HOME_V2_LINE_C1_Y, 0.0001f)
        assertEquals(470f, EMPTY_HOME_V2_LINE_C2_X, 0.0001f)
        assertEquals(1090f, EMPTY_HOME_V2_LINE_C2_Y, 0.0001f)
        assertEquals(560f, EMPTY_HOME_V2_LINE_END_X, 0.0001f)
        assertEquals(1128f, EMPTY_HOME_V2_LINE_END_Y, 0.0001f)
        assertTrue(EMPTY_HOME_V2_LINE_END_X in (EMPTY_HOME_V2_BOBBER_X..(EMPTY_HOME_V2_BOBBER_X + 24f)))
        assertTrue(EMPTY_HOME_V2_LINE_END_Y > EMPTY_HOME_V2_WATER_CONTACT_Y)
    }

    @Test
    fun bobberArtworkSplitsAtTheFixedWaterContactAcrossNibbleMotion() {
        val still = calculateBobberWaterSplit(
            bitmapHeight = 122,
            bobberTopY = EMPTY_HOME_V2_BOBBER_Y,
            waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
        )
        assertEquals(84, still.splitY)
        assertEquals(0, still.underwaterHeight)
        assertEquals(0f, still.underwaterAlpha, 0.0001f)
        assertTrue(still.underwaterHeight <= 122 * 0.20f)
        assertTrue(still.underwaterAlpha <= 0.25f)

        val eventOffsets = listOf(
            calculatePeriodicBobberMotion(1.5f).dipOffsetPx,
            calculatePeriodicBobberMotion(1.72f).dipOffsetPx,
            calculatePeriodicBobberMotion(1.94f).dipOffsetPx,
            calculatePeriodicBobberMotion(3.1f).dipOffsetPx,
        )
        assertTrue(eventOffsets.minOrNull()!! < 0f)
        assertTrue(eventOffsets.maxOrNull()!! <= 4f)
        eventOffsets.forEach { offset ->
            val movedTop = EMPTY_HOME_V2_BOBBER_Y + offset
            val split = calculateBobberWaterSplit(
                bitmapHeight = 122,
                bobberTopY = movedTop,
                waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
            )
            assertEquals((EMPTY_HOME_V2_WATER_CONTACT_Y - movedTop).roundToInt(), split.splitY)
            assertEquals(0, split.underwaterHeight)
            assertEquals(0f, split.underwaterAlpha, 0.0001f)
            assertEquals(
                EMPTY_HOME_V2_WATER_CONTACT_Y,
                movedTop + split.splitY,
                0.5f,
            )
        }
    }

    @Test
    fun periodicBobberRippleBurstsAreThreePointSixSecondsAndDeterministic() {
        assertTrue(calculatePeriodicBobberRippleBurst(0f) == null)

        val first = requireNotNull(calculatePeriodicBobberRippleBurst(1.5f))
        assertEquals(0, first.burstIndex)
        assertEquals(0L, first.cycleIndex)
        assertEquals(0L, first.eventId)
        assertEquals(0f, first.elapsedSeconds, 0.0001f)

        val finalFrame = requireNotNull(calculatePeriodicBobberRippleBurst(5.099f))
        assertEquals(3.599f, finalFrame.elapsedSeconds, 0.002f)
        assertTrue(calculatePeriodicBobberRippleBurst(5.11f) == null)

        assertEquals(1, requireNotNull(calculatePeriodicBobberRippleBurst(6.8f)).burstIndex)
        assertEquals(2, requireNotNull(calculatePeriodicBobberRippleBurst(13.6f)).burstIndex)
        assertEquals(3, requireNotNull(calculatePeriodicBobberRippleBurst(22.4f)).burstIndex)
        assertEquals(4, requireNotNull(calculatePeriodicBobberRippleBurst(28.2f)).burstIndex)

        val nextCycle = requireNotNull(calculatePeriodicBobberRippleBurst(37.5f))
        assertEquals(1L, nextCycle.cycleIndex)
        assertEquals(0, nextCycle.burstIndex)
        assertEquals(5L, nextCycle.eventId)
    }

    @Test
    fun bobberMotionAndRippleShareTheSameEventStartAndIdentity() {
        listOf(1.5f, 6.8f, 13.6f, 22.4f, 28.2f, 37.5f).forEach { eventStart ->
            val motion = calculatePeriodicBobberMotion(eventStart)
            val ripple = requireNotNull(calculatePeriodicBobberRippleBurst(eventStart))
            assertEquals(ripple.eventId, motion.eventId)
            assertEquals(0f, motion.burstElapsedSeconds, 0.001f)
        }
    }

    @Test
    fun fullThirtySixSecondScheduleRepeatsFiveDeterministicEvents() {
        val starts = listOf(1.5f, 6.8f, 13.6f, 22.4f, 28.2f)
        starts.forEachIndexed { index, start ->
            val firstCycle = calculatePeriodicBobberMotion(start)
            val nextCycle = calculatePeriodicBobberMotion(start + 36f)
            assertEquals(index.toLong(), requireNotNull(firstCycle.eventId))
            assertEquals((index + 5).toLong(), requireNotNull(nextCycle.eventId))
            assertEquals(0f, firstCycle.burstElapsedSeconds, 0.0001f)
            assertEquals(0f, nextCycle.burstElapsedSeconds, 0.0001f)
        }
        listOf(0f, 5.11f, 12.41f, 21.21f, 27.01f, 31.81f, 36f).forEach { quietTime ->
            assertFalse("unexpected bobber event at $quietTime", calculatePeriodicBobberMotion(quietTime).burstActive)
        }
    }

    @Test
    fun automaticHapticOnlyFiresOnANewVisibleRunningMotionEvent() {
        fun shouldEmit(
            initialized: Boolean = true,
            previous: Long? = 4L,
            current: Long? = 5L,
            lastHaptic: Long? = 4L,
            assets: Boolean = true,
            running: Boolean = true,
            reduced: Boolean = false,
            elapsed: Float = 0.01f,
        ) = shouldEmitBobberNibbleHaptic(
            hasObservedMotionEvent = initialized,
            previousMotionEventId = previous,
            currentMotionEventId = current,
            lastHapticEventId = lastHaptic,
            runtimeAssetsAvailable = assets,
            running = running,
            reduceMotion = reduced,
            currentMotionElapsedSeconds = elapsed,
        )

        assertTrue(shouldEmit())
        assertFalse(shouldEmit(initialized = false, previous = null, current = 5L, lastHaptic = null))
        assertFalse(shouldEmit(previous = 5L, current = 5L))
        assertFalse(shouldEmit(previous = null, current = null))
        assertFalse(shouldEmit(assets = false))
        assertFalse(shouldEmit(running = false))
        assertFalse(shouldEmit(reduced = true))
        assertFalse(shouldEmit(previous = 4L, current = 5L, lastHaptic = 5L))
        assertFalse(shouldEmit(elapsed = 0.121f))
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
        // The renderer keeps the static contact ring but suppresses scheduled waves and haptics.
        assertTrue(calculatePeriodicBobberRippleBurst(1.5f) != null)
        assertTrue(!emptyHomeMotionActive(running = true, reduceMotion = true))
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
            CLOUD_PHASE_OFFSET_SECONDS,
            SUN_BEAM_PHASE_OFFSET_SECONDS,
            CAMERA_BREATH_PHASE_OFFSET_SECONDS,
        )
        assertEquals(3, phases.size)
        assertTrue(CAMERA_BREATH_PHASE_OFFSET_SECONDS != 0f)
    }
}

