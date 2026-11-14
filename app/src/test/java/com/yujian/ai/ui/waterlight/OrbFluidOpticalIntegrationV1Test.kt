package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbFluidOpticalProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbGrassMotionProfileV11
import com.yujian.ai.ui.waterlight.renderer.OrbGrassSharedCurrentV12
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.WaterCausticsField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OrbFluidOpticalIntegrationV1Test {
    @Test fun candidateOpticsStayInsideFrozenR03Budgets() {
        OrbFluidOpticalProfileV1.validateBudgets()
        assertTrue(OrbFluidOpticalProfileV1.TOTAL_SAMPLE_MIX_MAX <= OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX)
        assertTrue(OrbFluidOpticalProfileV1.CAUSTIC_AUX_ALPHA_MAX <= OrbRuntimeProfileV1.R03_CAUSTIC_RESPONSE_ALPHA_MAX)
        assertTrue(
            OrbFluidOpticalProfileV1.SUNLIGHT_RAY_ALPHA_SHARE * OrbRuntimeProfileV1.R03_CORE_REFRACTION_ALPHA_MAX <=
                OrbFluidOpticalProfileV1.SUNLIGHT_MIX_MAX,
        )
        assertEquals("CALIBRATION_CANDIDATE", OrbFluidOpticalProfileV1.PARAMETER_STATUS)
    }

    @Test fun sharedGrassCurrentIsSeekableAndNeverExceedsFrozenLeafAmplitudes() {
        val leaves = OrbGrassMotionProfileV11.leaves
        assertEquals(listOf("BACK_B1", "BACK_B2", "FRONT_F1", "FRONT_F2"), leaves.map { it.id })
        val sharedAt = OrbGrassSharedCurrentV12.sharedLowFrequency(.4f, .6f, 1700L)
        assertEquals(sharedAt, OrbGrassSharedCurrentV12.sharedLowFrequency(.4f, .6f, 1700L), 0f)
        assertEquals(0f, OrbGrassSharedCurrentV12.bendEnvelope(0f), 0f)
        assertEquals(0f, OrbGrassSharedCurrentV12.bendEnvelope(.1f), 0f)
        assertEquals(1f, OrbGrassSharedCurrentV12.bendEnvelope(1f), 0f)
        leaves.forEach { leaf ->
            for (elapsed in 0L..20_000L step 137L) {
                val displacement = OrbGrassSharedCurrentV12.displacementD(leaf, 1f, elapsed)
                assertTrue("${leaf.id} exceeds V1.1 max at $elapsed ms", kotlin.math.abs(displacement) <= leaf.amplitudeD + 1e-6f)
                assertEquals(displacement, OrbGrassSharedCurrentV12.displacementD(leaf, 1f, elapsed), 0f)
            }
        }
        assertTrue("front-leaf root allowance stays frozen", leaves.filter { it.id.startsWith("FRONT") }.all { it.rootMaxD == .003f })
    }

    @Test fun softDomainWarpedCausticIsFiniteSoftAndBounded() {
        val color = WaterCausticsField.Rgb(.78, .94, .91)
        val tint = WaterCausticsField.Rgb(.10, .32, .36)
        val values = buildList {
            for (y in 0 until 24) for (x in 0 until 24) {
                add(WaterCausticsField.sampleSoftDomainWarpedCaustic(
                    fragX = x + .5, fragY = y + .5, resX = 24.0, resY = 24.0,
                    time = 3.2, scale = OrbFluidOpticalProfileV1.CAUSTIC_FLOW_SCALE,
                    intensity = OrbFluidOpticalProfileV1.CAUSTIC_AUX_INTENSITY,
                    color = color, tint = tint, tintStrength = 0.0,
                    domainWarp = OrbFluidOpticalProfileV1.CAUSTIC_DOMAIN_WARP,
                    softEdgeLow = OrbFluidOpticalProfileV1.CAUSTIC_SOFT_EDGE_LOW,
                    softEdgeHigh = OrbFluidOpticalProfileV1.CAUSTIC_SOFT_EDGE_HIGH,
                ))
            }
        }
        assertTrue(values.all { it.alpha.isFinite() && it.alpha in 0.0..1.0 })
        assertTrue(values.all { it.red.isFinite() && it.green.isFinite() && it.blue.isFinite() })
        assertTrue(values.map { it.alpha }.distinct().size > 20)
    }

    @Test fun agslAndComposeUseOneFrameAndKeepOriginalAlpha() {
        val root = moduleFile("")
        val fluidShader = File(root, "app/src/main/assets/orb_fluid_v1/orb_fluid_refraction_v1.agsl").readText()
        val grassShader = File(root, "app/src/main/assets/orb_r01b_v1_2/orb_grass_warp_v1_2.agsl").readText()
        val program = File(root, "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFluidRefractionProgramV1.kt").readText()
        assertTrue(fluidShader.contains("return half4(half3(clamp(outputStraight, 0.0, 1.0) * baseAlpha), half(baseAlpha))"))
        assertTrue(fluidShader.contains("leafCoverage(refractedCoord)"))
        assertTrue(grassShader.contains("sharedCurrent(uv)"))
        assertTrue(grassShader.contains("clamp(local * sharedLocalWeight + shared * sharedCurrentWeight + flutter * flutterWeight"))
        assertTrue(program.contains("createChainEffect("))
        assertTrue(program.contains("fluid.update(frame, surfaceSizePx, visualProfile)"))
        assertTrue(program.contains("grass.update(frame, s2Progress, grassLight, surfaceSizePx)"))
        assertFalse(program.contains("Handler("))
        assertFalse(program.contains("postDelayed("))

        val sunlight = File(root, "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbCausticsAdapter.kt").readText()
        assertTrue(sunlight.contains("OrbRuntimeProfileV1.R03_SUN_SOURCE_U"))
        assertTrue(sunlight.contains("OrbRuntimeProfileV1.R03_SUN_SOURCE_V"))
        assertTrue(sunlight.contains("SUNLIGHT_MIX_MAX * wakeAlpha"))
        assertFalse(sunlight.contains("canvas.rotate("))
    }

    private fun moduleFile(relative: String): File {
        val candidates = listOf(File("."), File(".."), File("../.."))
        val repoRoot = requireNotNull(candidates.firstOrNull { File(it, "app/src/main").exists() }) {
            "Could not resolve repository root from ${System.getProperty("user.dir")}"
        }
        return if (relative.isBlank()) repoRoot else File(repoRoot, relative)
    }
}
