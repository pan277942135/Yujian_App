package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbGrassMotionProfileV11
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbWakeBreathReleaseV11Test {
    @Test fun wakeAndS1ToS2EnvelopesUseSemanticStageProgress() {
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.PHOTO_STABLE, 1f).optics, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 0f).optics, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 80f / 350f).optics, 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 81f / 350f).optics > 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 220f / 350f).waterCore, 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 221f / 350f).waterCore > 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, .24f).optics > 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, .62f).waterCore, 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, .64f).grassLight > 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 1f).optics, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 1f).waterCore, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_RELEASE, 0f).grassLight, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.s2Progress(RecognitionSceneStage.PHOTO_STABLE, 1f), 0f)
        assertTrue(OrbWakeBreathReleaseV11.s2Progress(RecognitionSceneStage.ORB_WAKE, .5f) in 0f..1f)
        assertEquals(1f, OrbWakeBreathReleaseV11.s2Progress(RecognitionSceneStage.ORB_RELEASE, 1f), 0f)
    }

    @Test fun breathUsesOneSceneClockAcrossStageChangesAndFrozenProfiles() {
        val s1Quarter = OrbWakeBreathReleaseV11.breathScale(1500L, RecognitionSceneStage.ORB_WAKE, 0f, false)
        val s2Quarter = OrbWakeBreathReleaseV11.breathScale(1200L, RecognitionSceneStage.ORB_RELEASE, 1f / 6f, false)
        assertEquals(1.008f, s1Quarter, .00001f)
        assertEquals(1.018f, s2Quarter, .00001f)
        assertEquals(1f, OrbWakeBreathReleaseV11.breathScale(
            1500L, RecognitionSceneStage.ORB_WAKE, 0f, true,
        ), 0f)
        // A MANUAL pause holds the shared scene timestamp; resuming from that timestamp
        // and changing the recognition stage do not start a new breathing cycle at zero.
        val beforePause = OrbWakeBreathReleaseV11.breathScale(7137L, RecognitionSceneStage.ORB_WAKE, 1f, false)
        val resumedFromSameClock = OrbWakeBreathReleaseV11.breathScale(7137L, RecognitionSceneStage.ORB_WAKE, 1f, false)
        val afterStageSwitch = OrbWakeBreathReleaseV11.breathScale(7137L, RecognitionSceneStage.ORB_RELEASE, 0f, false)
        assertEquals(beforePause, resumedFromSameClock, 0f)
        assertEquals(beforePause, afterStageSwitch, 0f)
        assertTrue(abs(beforePause - 1f) > .001f)
        assertTrue(abs(OrbWakeBreathReleaseV11.breathScale(
            7138L, RecognitionSceneStage.ORB_RELEASE, 0f, false,
        ) - beforePause) > 0f)
        assertTrue(abs(s2Quarter - s1Quarter) > .009f)
    }

    @Test fun fourSourceDerivedLeavesKeepIndependentPhaseAndBoundedAmplitudeAtRuntimeSizes() {
        val leaves = OrbGrassMotionProfileV11.leaves
        assertEquals(listOf("BACK_B1", "BACK_B2", "FRONT_F1", "FRONT_F2"), leaves.map { it.id })
        assertEquals(listOf(6200L, 6200L, 4800L, 4800L), leaves.map { it.periodMs })
        assertEquals(listOf(0f, 100f, 120f, 240f), leaves.map { it.phaseDegrees })
        assertEquals(listOf(.018f, .018f, .030f, .027f), leaves.map { it.amplitudeD })
        assertEquals(listOf(0f, 0f, .003f, .003f), leaves.map { it.rootMaxD })
        leaves.forEach { leaf ->
            val points = listOf(leaf.rootU to leaf.rootV, leaf.c1U to leaf.c1V,
                leaf.c2U to leaf.c2V, leaf.tipU to leaf.tipV)
            points.forEach { (u, v) -> assertTrue("${leaf.id} UV", u in 0f..1f && v in 0f..1f) }
            for (diameterDp in listOf(68f, 80f, 74f)) {
                val tipShiftDp = leaf.amplitudeD * diameterDp
                val rootShiftDp = leaf.rootMaxD * diameterDp
                assertTrue("${leaf.id} tip at ${diameterDp}dp", tipShiftDp <= .03f * diameterDp + .0001f)
                assertTrue("${leaf.id} root at ${diameterDp}dp", rootShiftDp <= .003f * diameterDp + .0001f)
                val imageSizeDp = diameterDp / .84f
                val rootDisplayedDp = leaf.rootU * imageSizeDp
                val tipDisplayedDp = leaf.tipU * imageSizeDp
                assertTrue(rootDisplayedDp in 0f..imageSizeDp)
                assertTrue(tipDisplayedDp in 0f..imageSizeDp)
                assertEquals(leaf.rootU * OrbGrassMotionProfileV11.RUNTIME_SIZE,
                    leaf.rootU * OrbGrassMotionProfileV11.MASTER_SIZE *
                        OrbGrassMotionProfileV11.SOURCE_TO_RUNTIME_SCALE, .001f)
            }
        }
        assertFalse(leaves[0].phaseDegrees == leaves[1].phaseDegrees)
        assertFalse(leaves[2].phaseDegrees == leaves[3].phaseDegrees)
    }

    @Test fun orbReleaseCurveUsesStageLocalProgressAndReturnsWithoutResettingScenePhase() {
        val pathLengthD = .7f
        val diameterDp = 80f
        val peakArcFraction = OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP / (pathLengthD * diameterDp)
        val turnProgress = 34f / 60f
        val halfReturnProgress = turnProgress + (1f - turnProgress) * .5f
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.AMBIENT,
            OrbWakeBreathReleaseV11.localRelease(RecognitionSceneStage.ORB_WAKE, 1f))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.OUTWARD,
            OrbWakeBreathReleaseV11.localRelease(RecognitionSceneStage.ORB_RELEASE, .25f))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.RETURN,
            OrbWakeBreathReleaseV11.localRelease(RecognitionSceneStage.ORB_RELEASE, .75f))
        assertEquals(0f, OrbWakeBreathReleaseV11.releaseOffset(
            RecognitionSceneStage.ORB_RELEASE, 0f, peakArcFraction,
        ), 0f)
        assertEquals(peakArcFraction, OrbWakeBreathReleaseV11.releaseOffset(
            RecognitionSceneStage.ORB_RELEASE, turnProgress, peakArcFraction,
        ), .0001f)
        assertEquals(peakArcFraction * .5f, OrbWakeBreathReleaseV11.releaseOffset(
            RecognitionSceneStage.ORB_RELEASE, halfReturnProgress, peakArcFraction,
        ), .0001f)
        assertEquals(0f, OrbWakeBreathReleaseV11.releaseOffset(
            RecognitionSceneStage.ORB_RELEASE, 1f, peakArcFraction,
        ), 0f)
        assertEquals(.5f, OrbWakeBreathReleaseV11.returnDirectionProgress(
            RecognitionSceneStage.ORB_RELEASE, halfReturnProgress,
        ), .0001f)
        assertEquals(1.28f, OrbWakeBreathReleaseV11.R06_ALPHA_GAIN, 0f)
        assertEquals(.82f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP, 0f)
        assertEquals(1.15f, OrbWakeBreathReleaseV11.R06_MOVING_HIGHLIGHT_GAIN, 0f)
        assertEquals(1.18f, OrbWakeBreathReleaseV11.R06_RELEASE_HEAD_BRIGHTNESS_GAIN, 0f)
        assertEquals(14f, OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP, 0f)
        assertEquals(5, OrbRuntimeProfileV1.paths.size)
        assertEquals(listOf("G01", "G02", "A01", "A02", "C01"), OrbRuntimeProfileV1.paths.map { it.id })
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.OUTWARD,
            OrbWakeBreathReleaseV11.localRelease(RecognitionSceneStage.ORB_RELEASE, .25f))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.RETURN,
            OrbWakeBreathReleaseV11.localRelease(RecognitionSceneStage.ORB_RELEASE, .75f))
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(RecognitionSceneStage.ORB_WAKE, 1f).optics, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.s2Progress(RecognitionSceneStage.ORB_RETURN, 0f), 0f)

        val spec = OrbRuntimeProfileV1.paths.first()
        val before = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, 5000L, RecognitionSceneStage.ORB_WAKE, 1f, false, pathLengthD, diameterDp,
        )
        val start = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, 5000L, RecognitionSceneStage.ORB_RELEASE, 0f, false, pathLengthD, diameterDp,
        )
        val outward = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, 5000L, RecognitionSceneStage.ORB_RELEASE, .25f, false, pathLengthD, diameterDp,
        )
        val returnStart = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, 5000L, RecognitionSceneStage.ORB_RELEASE, turnProgress, false, pathLengthD, diameterDp,
        )
        val end = OrbFilamentPathMathV1.localReleaseMovementForStage(
            spec, 8000L, RecognitionSceneStage.ORB_RELEASE, 1f, false, pathLengthD, diameterDp,
        )
        assertEquals(before.u, start.u, .0001f)
        assertTrue(returnStart.u > outward.u && outward.u > start.u)
        assertTrue(abs(end.unclampedU - OrbFilamentPathMathV1.movement(
            spec, 8000L, false, OrbRuntimeProfileV1.MotionEvent.NONE, 0L,
        ).unclampedU) < .0001f)
    }
}
