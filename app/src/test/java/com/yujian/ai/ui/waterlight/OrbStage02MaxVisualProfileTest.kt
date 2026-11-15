package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.OrbInteriorOpticalField
import com.yujian.ai.ui.waterlight.renderer.OrbStage02MaxVisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import com.yujian.ai.ui.waterlight.renderer.r06GlassTransmissionBodyAlpha
import com.yujian.ai.ui.waterlight.renderer.r06MaximumRawCoreAlphaInRuns
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
            RecognitionSceneStage.ORB_RELEASE, 40f / 600f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 390L,
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

    @Test fun continuousFlowUsesSceneClockWithoutChangingWakeSemantics() {
        val early = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, 1f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 350L,
        )
        val later = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, 1f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 1_850L,
        )
        assertEquals(early.wakeStrength, later.wakeStrength, 0f)
        assertTrue("fixed StageProgress must not freeze the shared flow phase",
            abs(early.semanticPhase - later.semanticPhase) > .01f)
        val earlyDirection = OrbInteriorOpticalField.sharedFlowDirectionAt(.31f, .68f, early.semanticPhase, 350L)
        val laterDirection = OrbInteriorOpticalField.sharedFlowDirectionAt(.31f, .68f, later.semanticPhase, 1_850L)
        assertTrue("same-progress R03 sample direction must evolve with the existing scene clock",
            abs(earlyDirection.x - laterDirection.x) + abs(earlyDirection.y - laterDirection.y) > .01f)
        assertEquals(0f, OrbInteriorOpticalField.continuousFlowPhase(1_850L, reducedMotion = true), 0f)

        val sameClockEarlyWake = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, .25f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 1_000L,
        )
        val sameClockLateWake = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_WAKE, .75f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 1_000L,
        )
        assertTrue("StageProgress controls wake strength separately from continuous phase",
            sameClockLateWake.wakeStrength > sameClockEarlyWake.wakeStrength)

        val releaseStart = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_RELEASE, 0f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 350L,
        )
        val releaseMid = OrbInteriorOpticalField.frame(
            RecognitionSceneStage.ORB_RELEASE, 40f / 600f, false, OrbStage02VisualProfile.MAX, sceneElapsedMs = 390L,
        )
        assertEquals("Stage02 end and Stage03 start must inherit the same scene phase",
            early.semanticPhase, releaseStart.semanticPhase, 0f)
        val expectedReleaseMidPhase = early.semanticPhase + OrbInteriorOpticalField.continuousFlowPhase(390L, false) -
            OrbInteriorOpticalField.continuousFlowPhase(350L, false)
        assertEquals("Stage03 fade changes strength while the shared phase keeps moving forward",
            expectedReleaseMidPhase, releaseMid.semanticPhase, .00001f)
        assertTrue("new Wake phase must not run backward during the Stage03 fade",
            releaseMid.semanticPhase > releaseStart.semanticPhase)
    }

    @Test fun glassTransmissionUsesOneSourceTransmissionFactorBeforeOnePixelEdgeMask() {
        assertEquals(.28f, r06GlassTransmissionBodyAlpha(.70f, .50f), .00001f)
        assertEquals(0f, r06GlassTransmissionBodyAlpha(.70f, 0f), 0f)
        assertEquals(.80f, r06GlassTransmissionBodyAlpha(1f, 1f), .00001f)
    }

    @Test fun glassTransmissionUsesHighestRawCoreAlphaInTheCurrentDrawableRunAndExcludesGaps() {
        val coreAlphaBySample = FloatArray(9)
        coreAlphaBySample[4] = .2196f // frozen A01 midpoint sample at radius 0.4723D
        coreAlphaBySample[7] = .9882f // frozen A01 sample at radius 0.4656D in the same run
        val sameRun = listOf(0..8)
        val splitRuns = listOf(0..3, 5..8)

        assertEquals(.9882f, r06MaximumRawCoreAlphaInRuns(coreAlphaBySample, sameRun), .00001f)
        assertEquals(0f, r06MaximumRawCoreAlphaInRuns(coreAlphaBySample, splitRuns), 0f)
        assertEquals(
            .70f * .80f * .9882f,
            r06GlassTransmissionBodyAlpha(.70f, r06MaximumRawCoreAlphaInRuns(coreAlphaBySample, sameRun)),
            .00001f,
        )
        assertEquals(0f, r06GlassTransmissionBodyAlpha(
            .70f, r06MaximumRawCoreAlphaInRuns(coreAlphaBySample, splitRuns),
        ), 0f)
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
