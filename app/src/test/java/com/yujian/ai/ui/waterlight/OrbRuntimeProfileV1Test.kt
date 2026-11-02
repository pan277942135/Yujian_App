package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbRuntimeProfileV1Test {
    @Test fun r02HasThreeFixedDeterministicLensDrops() {
        val drops = OrbRuntimeProfileV1.droplets
        assertEquals(listOf("D1", "D2", "D3"), drops.map { it.id })
        assertEquals(.32f, drops[0].u, 0f)
        assertEquals(.36f, drops[0].v, 0f)
        assertEquals(1.8f, drops[0].diameterDpAt80, 0f)
        assertEquals(.23f, drops[0].bodyAlpha, 0f)
        assertEquals(.38f, drops[0].rimAlpha, 0f)
        assertEquals(5200L, drops[0].periodMs)
        assertEquals(.49f, drops[1].phase, 0f)
        assertEquals(1, OrbRuntimeProfileV1.R02_LOW_PERFORMANCE_COUNT)
        assertEquals(.012f, OrbRuntimeProfileV1.R02_VERTICAL_DRIFT_MAX_D, 0f)
        assertEquals(.007f, OrbRuntimeProfileV1.R02_LATERAL_DRIFT_MAX_D, 0f)
    }

    @Test fun r03SunlightAndAdditiveLayersStayInsideTheSourceContract() {
        assertEquals(.24f, OrbRuntimeProfileV1.R03_SUN_SOURCE_U, 0f)
        assertEquals(.20f, OrbRuntimeProfileV1.R03_SUN_SOURCE_V, 0f)
        assertEquals(0xD8BC87L, OrbRuntimeProfileV1.sunColor)
        assertTrue(OrbRuntimeProfileV1.R03_FIXED_AZIMUTH)
        assertEquals(.11f, OrbRuntimeProfileV1.R03_GLASS_SPECULAR_ALPHA_MAX, 0f)
        assertEquals(.02f, OrbRuntimeProfileV1.R03_GLASS_MAX_TRANSLATION_D, 0f)
        assertEquals(.11f, OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX, 0f)
        assertEquals(.14f, OrbRuntimeProfileV1.R03_CAUSTIC_RESPONSE_ALPHA_MAX, 0f)
        assertEquals(.06f, OrbRuntimeProfileV1.R03_GRASS_RESPONSE_ALPHA_MAX, 0f)
        assertEquals(.32f, OrbRuntimeProfileV1.R03_DROPLET_SPECULAR_ALPHA_MAX, 0f)
        assertEquals(.22f, OrbRuntimeProfileV1.R03_MAX_EXTRA_ADDITIVE_ALPHA, 0f)
    }

    @Test fun r04HasOnlyTwoStatesAndUsesContractDurations() {
        assertEquals(listOf("S1_IDLE", "S2_ACTIVE"), OrbRuntimeProfileV1.LifeState.entries.map { it.name })
        assertEquals(450L, OrbRuntimeProfileV1.R04_ACTIVE_TRANSITION_MS)
        assertEquals(620L, OrbRuntimeProfileV1.R04_RETURN_TRANSITION_MS)
        assertEquals(900L, OrbRuntimeProfileV1.R04_ENTRANCE_MS)
        assertEquals(0f, OrbFilamentPathMathV1.smoothTransitionAlpha(0f, 1f, 0L, 450L), 0f)
        assertEquals(.5f, OrbFilamentPathMathV1.smoothTransitionAlpha(0f, 1f, 225L, 450L), .0001f)
        assertEquals(1f, OrbFilamentPathMathV1.smoothTransitionAlpha(0f, 1f, 450L, 450L), 0f)
        assertEquals(.5f, OrbFilamentPathMathV1.smoothTransitionAlpha(1f, 0f, 310L, 620L), .0001f)
    }

    @Test fun r05UsesTwoProfileLayersAndBoundedCoreMask() {
        assertEquals(2, OrbRuntimeProfileV1.causticLayers.size)
        assertEquals("deep_flow", OrbRuntimeProfileV1.causticLayers[0].id)
        assertEquals(6500L, OrbRuntimeProfileV1.causticLayers[0].periodMs)
        assertEquals(1.65f, OrbRuntimeProfileV1.causticLayers[0].frequency, 0f)
        assertEquals(.07f, OrbRuntimeProfileV1.causticLayers[0].alpha, 0f)
        assertEquals("surface_caustic", OrbRuntimeProfileV1.causticLayers[1].id)
        assertEquals(4100L, OrbRuntimeProfileV1.causticLayers[1].periodMs)
        assertEquals(3.05f, OrbRuntimeProfileV1.causticLayers[1].frequency, 0f)
        assertEquals(.12f, OrbRuntimeProfileV1.causticLayers[1].alpha, 0f)
        assertEquals(.385f, OrbRuntimeProfileV1.CAUSTIC_INNER_RADIUS_D, 0f)
        assertEquals(.035f, OrbRuntimeProfileV1.CAUSTIC_EDGE_FADE_D, 0f)
        assertEquals(2, OrbRuntimeProfileV1.CAUSTIC_BUFFER_COUNT)
        assertEquals(96, OrbRuntimeProfileV1.CAUSTIC_NORMAL_TEXTURE_PX_80DP)
        assertEquals(64, OrbRuntimeProfileV1.CAUSTIC_LOW_TEXTURE_PX_80DP)
        assertEquals(66L, OrbRuntimeProfileV1.CAUSTIC_NORMAL_MIN_INTERVAL_MS)
        assertEquals(120L, OrbRuntimeProfileV1.CAUSTIC_LOW_MIN_INTERVAL_MS)
    }

    @Test fun r06ContainsTheFiveOfficialBezierPathsAndArcLengthTables() {
        val paths = OrbRuntimeProfileV1.paths
        assertEquals(listOf("G01", "G02", "A01", "A02", "C01"), paths.map { it.id })
        assertEquals(listOf(false, true, false, true, true), paths.map { it.front })
        assertEquals(listOf(6100L, 5300L, 3700L, 2900L, 4400L), paths.map { it.periodMs })
        assertEquals(listOf(.07f, .34f, .59f, .81f, .23f), paths.map { it.phase })
        assertEquals(listOf(1.5f, 1.4f, .55f, .55f, 1.4f), paths.map { it.widthMinDp })
        assertEquals(listOf(3.2f, 2.7f, .92f, 1f, 2.8f), paths.map { it.widthMaxDp })
        assertEquals(3, OrbRuntimeProfileV1.materials.size)
        assertEquals(.675f, OrbRuntimeProfileV1.paths.maxOf { it.maxOuterRadiusD }, 0f)
        assertEquals(14f, OrbRuntimeProfileV1.R06_MAX_OUTSIDE_DP, 0f)
        assertEquals(96, OrbRuntimeProfileV1.R06_NORMAL_STEPS_PER_PATH)
        assertEquals(48, OrbRuntimeProfileV1.R06_LOW_STEPS_PER_PATH)

        paths.forEach { spec ->
            assertEquals(2, spec.segments.size)
            val lut = OrbFilamentPathMathV1.newLut()
            OrbFilamentPathMathV1.fillLut(spec, 0L, lut)
            assertEquals(128, lut.cumulativeD.size)
            for (i in 1 until lut.cumulativeD.size) {
                assertTrue("${spec.id}: arc-length LUT must be monotone", lut.cumulativeD[i] >= lut.cumulativeD[i - 1])
            }
            assertTrue("${spec.id}: LUT must preserve the JSON path length", kotlin.math.abs(lut.cumulativeD.last() - spec.pathLengthD) < .03f)
            val position = FloatArray(2)
            OrbFilamentPathMathV1.sampleAt(lut, 0f, position, 0)
            assertEquals(spec.start.x, position[0], .0001f)
            assertEquals(spec.start.y, position[1], .0001f)
            OrbFilamentPathMathV1.sampleAt(lut, 1f, position, 0)
            assertEquals(spec.segments.last().end.x, position[0], .0001f)
            assertEquals(spec.segments.last().end.y, position[1], .0001f)
        }
    }

    @Test fun r06PathClocksAreIndependentAndReleaseReturnSwitchWithoutJump() {
        val at = 1234L
        val positions = OrbRuntimeProfileV1.paths.map {
            OrbFilamentPathMathV1.movement(it, at, false, OrbRuntimeProfileV1.MotionEvent.NONE, 0L).u
        }
        assertTrue("path phases and periods must not synchronize", positions.distinct().size >= 4)

        val path = OrbRuntimeProfileV1.paths.first()
        val before = OrbFilamentPathMathV1.movement(path, 1200L, false, OrbRuntimeProfileV1.MotionEvent.NONE, 0L)
        val releaseAtSwitch = OrbFilamentPathMathV1.movement(path, 1200L, false, OrbRuntimeProfileV1.MotionEvent.RELEASE, 1200L)
        val returnAtSwitch = OrbFilamentPathMathV1.movement(path, 1200L, false, OrbRuntimeProfileV1.MotionEvent.RETURN, 1200L)
        assertEquals(before.u, releaseAtSwitch.u, 0f)
        assertEquals(before.u, returnAtSwitch.u, 0f)

        val releaseAfterRamp = OrbFilamentPathMathV1.movement(path, 1460L, false, OrbRuntimeProfileV1.MotionEvent.RELEASE, 1200L)
        val returnAfterRamp = OrbFilamentPathMathV1.movement(path, 1460L, false, OrbRuntimeProfileV1.MotionEvent.RETURN, 1200L)
        assertTrue(releaseAfterRamp.u > releaseAtSwitch.u)
        assertTrue(returnAfterRamp.u < returnAtSwitch.u)
        val frozen0 = OrbFilamentPathMathV1.movement(path, 0L, true, OrbRuntimeProfileV1.MotionEvent.NONE, 0L)
        val frozen10s = OrbFilamentPathMathV1.movement(path, 10_000L, true, OrbRuntimeProfileV1.MotionEvent.NONE, 0L)
        assertEquals(frozen0.u, frozen10s.u, 0f)
        assertEquals(.37f, OrbRuntimeProfileV1.R06_REDUCED_MOTION_PHASE, 0f)
    }
}
