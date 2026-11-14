package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.OrbInteriorOpticalField
import com.yujian.ai.ui.waterlight.renderer.OrbStage02MaxVisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbStage02MaxVisualProfileTest {
    @Test fun maxProfileKeepsTheRequestedCandidateValues() {
        assertEquals(.018f, OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D, 0f)
        assertEquals(.92f, OrbStage02MaxVisualProfile.FLUID_INTERIOR_MIX_MAX, 0f)
        assertEquals(80f, OrbStage02MaxVisualProfile.FLUID_WAKE_START_MS, 0f)
        assertEquals(280f, OrbStage02MaxVisualProfile.FLUID_WAKE_FULL_MS, 0f)
        assertEquals(.65f, OrbStage02MaxVisualProfile.FLUID_WAKE_PHASE_CYCLES, 0f)
        assertEquals(.34f, OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_START_D, 0f)
        assertEquals(.47f, OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_END_D, 0f)
        assertEquals(0f, OrbStage02MaxVisualProfile.FLUID_OUTLINE_DISPLACEMENT_D, 0f)
        assertEquals(.80f, OrbStage02MaxVisualProfile.GLASS_BACK_TRANSMISSION_MAX, 0f)
        assertEquals(.006f, OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D, 0f)
        assertEquals(.65f, OrbStage02MaxVisualProfile.GLASS_BACK_SOFTEN_MAX_DP, 0f)
        assertEquals(.45f, OrbStage02MaxVisualProfile.R06_FRONT_BODY_ALPHA_TARGET, 0f)
        assertEquals(.38f, OrbStage02MaxVisualProfile.R06_BACK_BODY_ALPHA_TARGET, 0f)
        assertEquals(1.35f, OrbStage02MaxVisualProfile.R06_BODY_STROKE_GAIN_MAX, 0f)
        assertEquals(80f, OrbStage02MaxVisualProfile.STAGE03_TRANSITION_MS, 0f)
    }

    @Test fun stage02SemanticWakeUsesFrozen350msBoundaryForFormalAndDiagnosticPlayback() {
        assertEquals(350L, RecognitionSceneStage.ORB_WAKE.minimumVisibleMs)
        assertEquals(3_000L, DIAGNOSTIC_STEP_DURATION_MS)
        assertEquals(0f, OrbInteriorOpticalField.maxFluidWakeEnvelope(
            RecognitionSceneStage.ORB_WAKE, 79f / 350f,
        ), 0f)
        val progressPoints = listOf(0f, 80f / 350f, 180f / 350f, 280f / 350f, 1f)
        val expected = listOf(0f, 0f, .5f, 1f, 1f)
        progressPoints.zip(expected).forEach { (progress, value) ->
            val formal = OrbInteriorOpticalField.maxFluidWakeEnvelope(RecognitionSceneStage.ORB_WAKE, progress)
            val diagnostic = OrbInteriorOpticalField.maxFluidWakeEnvelope(
                RecognitionSceneStage.ORB_WAKE,
                (progress * DIAGNOSTIC_STEP_DURATION_MS.toFloat()) / DIAGNOSTIC_STEP_DURATION_MS.toFloat(),
            )
            assertEquals(value, formal, .00001f)
            assertEquals(formal, diagnostic, .00001f)
        }
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(
            RecognitionSceneStage.ORB_WAKE, 220f / 350f,
        ).waterCore, 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(
            RecognitionSceneStage.ORB_WAKE, 221f / 350f,
        ).waterCore > 0f)
        assertEquals(80f, OrbStage02MaxVisualProfile.FLUID_WAKE_START_MS, 0f)
    }

    @Test fun maxFluidFieldIsBoundedLocallyAndContinuesIntoStage03Fade() {
        val wakeEnd = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, 1f, false, OrbStage02VisualProfile.MAX,
        )
        val releaseStart = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_RELEASE, 0f, false, OrbStage02VisualProfile.MAX,
        )
        val releaseMid = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_RELEASE, 40f / 600f, false, OrbStage02VisualProfile.MAX,
        )
        val releaseEnd = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_RELEASE, 80f / 600f, false, OrbStage02VisualProfile.MAX,
        )
        assertEquals(1f, wakeEnd.wakeStrength, 0f)
        assertEquals(wakeEnd.wakeStrength, releaseStart.wakeStrength, 0f)
        assertEquals(wakeEnd.semanticPhase, releaseStart.semanticPhase, 0f)
        assertEquals(0f, wakeEnd.baselineOpticalMix, 0f)
        assertEquals(0f, releaseStart.baselineOpticalMix, 0f)
        assertEquals(.5f, releaseMid.baselineOpticalMix, .00001f)
        assertEquals(1f, releaseEnd.baselineOpticalMix, 0f)
        assertTrue(OrbInteriorOpticalField.maxFluidShaderActive(RecognitionSceneStage.ORB_RELEASE, 80f / 600f))
        assertFalse(OrbInteriorOpticalField.maxFluidShaderActive(RecognitionSceneStage.ORB_RELEASE, 81f / 600f))
        assertEquals(OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D, wakeEnd.maxDisplacementD, 0f)
        assertEquals(OrbStage02MaxVisualProfile.FLUID_INTERIOR_MIX_MAX, wakeEnd.interiorMix, 0f)
        assertEquals(.5f, OrbInteriorOpticalField.maxFluidWakeEnvelope(
            RecognitionSceneStage.ORB_RELEASE, 40f / 600f,
        ), .00001f)
        assertEquals(0f, OrbInteriorOpticalField.maxFluidWakeEnvelope(
            RecognitionSceneStage.ORB_RELEASE, 80f / 600f,
        ), 0f)
        assertEquals(0f, OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, 1f, true, OrbStage02VisualProfile.MAX,
        ).maxDisplacementD, 0f)
        assertFalse(OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, 1f, false, OrbStage02VisualProfile.BASELINE,
        ).enabled)
        assertEquals(1f, OrbInteriorOpticalField.interiorWeight(.34f), 0f)
        assertEquals(0f, OrbInteriorOpticalField.interiorWeight(.47f), 0f)
        assertEquals(0f, OrbStage02MaxVisualProfile.FLUID_OUTLINE_DISPLACEMENT_D, 0f)
        assertTrue(OrbInteriorOpticalField.interiorWeight(.40f) in 0f..1f)
    }

    @Test fun semanticWakeFlowChangesSpatialSamplingBetweenStage02Checkpoints() {
        val quarter = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, .25f, false, OrbStage02VisualProfile.MAX,
        )
        val half = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, .50f, false, OrbStage02VisualProfile.MAX,
        )
        val x1 = OrbInteriorOpticalField.flowXAt(.31f, .68f, quarter.semanticPhase)
        val y1 = OrbInteriorOpticalField.flowYAt(.31f, .68f, quarter.semanticPhase)
        val x2 = OrbInteriorOpticalField.flowXAt(.31f, .68f, half.semanticPhase)
        val y2 = OrbInteriorOpticalField.flowYAt(.31f, .68f, half.semanticPhase)
        assertTrue(abs(x1 - x2) + abs(y1 - y2) > .01f)
        assertEquals(0f, OrbInteriorOpticalField.frame(
            RecognitionSceneStage.PHOTO_STABLE, 1f, false, OrbStage02VisualProfile.MAX,
        ).wakeStrength, 0f)
    }

    @Test fun shaderBlendIsContinuousAtStage02ToStage03AndReturnsToBaselineAfter80ms() {
        val stage2End = OrbInteriorOpticalField.shaderOverlayAlpha(
            OrbStage02VisualProfile.MAX, RecognitionSceneStage.ORB_WAKE, 1f, 1f,
        )
        val stage3Start = OrbInteriorOpticalField.shaderOverlayAlpha(
            OrbStage02VisualProfile.MAX, RecognitionSceneStage.ORB_RELEASE, 0f, 1f,
        )
        val stage3After80ms = OrbInteriorOpticalField.shaderOverlayAlpha(
            OrbStage02VisualProfile.MAX, RecognitionSceneStage.ORB_RELEASE, 80f / 600f, 1f,
        )
        assertEquals(1f, stage2End, 0f)
        assertEquals(stage2End, stage3Start, 0f)
        assertEquals(OrbStage02MaxVisualProfile.BASELINE_SHADER_OVERLAY_ALPHA, stage3After80ms, .00001f)
        assertEquals(.25f, OrbInteriorOpticalField.shaderOverlayAlpha(
            OrbStage02VisualProfile.BASELINE, RecognitionSceneStage.ORB_WAKE, .75f, 1f,
        ), .00001f)
    }

    @Test fun maxR06BodyKeepsNaturalFadesAndStage03AlphaContinuity() {
        val stage2End = OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, 0f, 1f, 1f, 1f, 1f, 1f, true,
        )
        val stage3Start = OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_RELEASE, 0f, 0f, 1f, 1f, 1f, 1f, 1f, true,
        )
        assertEquals(OrbStage02MaxVisualProfile.R06_FRONT_BODY_ALPHA_TARGET, stage2End, .00001f)
        assertEquals(stage2End, stage3Start, .001f)
        assertEquals(.225f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_RELEASE, 40f / 600f, 0f, 1f, 1f, 1f, 1f, 1f, true,
        ), .00001f)
        assertEquals(.38f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, 0f, 1f, 1f, 1f, 1f, 1f, false,
        ), .00001f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, .11f, 0f, 1f, 1f, 1f, 1f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, .11f, 1f, 0f, 1f, 1f, 1f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, .11f, 1f, 1f, 0f, 1f, 1f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, .11f, 1f, 1f, 1f, 0f, 1f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_WAKE, 1f, .11f, 1f, 1f, 1f, 1f, 0f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.ORB_RELEASE, 80f / 600f, .11f, 1f, 1f, 1f, 1f, 1f, true,
        ), 0f)
        assertEquals(.11f, OrbWakeBreathReleaseV11.r06MaxBodyAlpha(
            RecognitionSceneStage.CONTOUR_A, .5f, .11f, 1f, 1f, 1f, 1f, 1f, true,
        ), 0f)
    }
}
