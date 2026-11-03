package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbGrassMotionProfileV11
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbWakeBreathReleaseV11
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbWakeBreathReleaseV11Test {
    @Test fun wakeAndS1ToS2EnvelopesUseFrozenOpticalTimes() {
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(0L).optics, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(79L).optics, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(80L).optics, 0f)
        assertTrue(OrbWakeBreathReleaseV11.wake(81L).optics > 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(219L).waterCore, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(219L).grassLight, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.wake(220L).waterCore, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(350L).optics, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(350L).waterCore, 0f)
        assertEquals(1f, OrbWakeBreathReleaseV11.wake(350L).grassLight, 0f)
        assertEquals(0f, OrbWakeBreathReleaseV11.s2Progress(0L), 0f)
        assertTrue(OrbWakeBreathReleaseV11.s2Progress(350L) > .85f)
        assertEquals(1f, OrbWakeBreathReleaseV11.s2Progress(450L), 0f)
    }

    @Test fun breathIsSceneClockedAndUsesFrozenS1AndS2Profiles() {
        val s1Quarter = OrbWakeBreathReleaseV11.breathScale(1500L, 0L, false)
        val s2Quarter = OrbWakeBreathReleaseV11.breathScale(1200L, 450L, false)
        assertEquals(1.008f, s1Quarter, .00001f)
        assertEquals(1.018f, s2Quarter, .00001f)
        assertEquals(1f, OrbWakeBreathReleaseV11.breathScale(1500L, 0L, true), 0f)
        // A MANUAL pause holds the shared scene timestamp; resuming from that timestamp
        // and changing the recognition stage do not start a new breathing cycle at zero.
        val beforePause = OrbWakeBreathReleaseV11.breathScale(7137L, 450L, false)
        val resumedFromSameClock = OrbWakeBreathReleaseV11.breathScale(7137L, 450L, false)
        val afterStageSwitch = OrbWakeBreathReleaseV11.breathScale(7137L, 620L, false)
        assertEquals(beforePause, resumedFromSameClock, 0f)
        assertEquals(beforePause, afterStageSwitch, 0f)
        assertTrue(abs(beforePause - 1f) > .001f)
        assertTrue(abs(OrbWakeBreathReleaseV11.breathScale(7138L, 620L, false) - beforePause) > 0f)
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

    @Test fun localReleaseTimelineDoesNotChangeRecognitionStageAndReturnsWithoutResettingScenePhase() {
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.AMBIENT,
            OrbWakeBreathReleaseV11.localRelease(349L))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.OUTWARD,
            OrbWakeBreathReleaseV11.localRelease(350L))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.OUTWARD,
            OrbWakeBreathReleaseV11.localRelease(450L))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.RETURN,
            OrbWakeBreathReleaseV11.localRelease(690L))
        assertEquals(OrbWakeBreathReleaseV11.LocalRelease.AMBIENT,
            OrbWakeBreathReleaseV11.localRelease(950L))
        val pathLengthD = .7f
        val diameterDp = 80f
        val peakArcFraction = OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP / (pathLengthD * diameterDp)
        assertEquals(0f, OrbWakeBreathReleaseV11.releaseOffset(350L, peakArcFraction), 0f)
        assertEquals(peakArcFraction, OrbWakeBreathReleaseV11.releaseOffset(690L, peakArcFraction), .0001f)
        assertEquals(peakArcFraction * .5f, OrbWakeBreathReleaseV11.releaseOffset(820L, peakArcFraction), .0001f)
        assertEquals(0f, OrbWakeBreathReleaseV11.releaseOffset(950L, peakArcFraction), 0f)
        assertEquals(.5f, OrbWakeBreathReleaseV11.returnDirectionProgress(820L), .0001f)
        assertEquals(1.28f, OrbWakeBreathReleaseV11.R06_ALPHA_GAIN, 0f)
        assertEquals(.82f, OrbWakeBreathReleaseV11.R06_ALPHA_CAP, 0f)
        assertEquals(1.15f, OrbWakeBreathReleaseV11.R06_MOVING_HIGHLIGHT_GAIN, 0f)
        assertEquals(1.18f, OrbWakeBreathReleaseV11.R06_RELEASE_HEAD_BRIGHTNESS_GAIN, 0f)
        assertEquals(14f, OrbWakeBreathReleaseV11.R06_MAX_EXTENSION_DP, 0f)
        assertEquals(5, OrbRuntimeProfileV1.paths.size)
        assertEquals(listOf("G01", "G02", "A01", "A02", "C01"), OrbRuntimeProfileV1.paths.map { it.id })
        assertEquals(RecognitionSceneStage.ORB_RELEASE, RecognitionSceneCoordinator.stageAt(350L))
        assertEquals(RecognitionSceneStage.ORB_RETURN, RecognitionSceneCoordinator.stageAt(2800L))

        val spec = OrbRuntimeProfileV1.paths.first()
        val before = OrbFilamentPathMathV1.localReleaseMovement(spec, 5000L, 349L, false, pathLengthD, diameterDp)
        val start = OrbFilamentPathMathV1.localReleaseMovement(spec, 5000L, 350L, false, pathLengthD, diameterDp)
        val returnStart = OrbFilamentPathMathV1.localReleaseMovement(spec, 5000L, 690L, false, pathLengthD, diameterDp)
        val end = OrbFilamentPathMathV1.localReleaseMovement(spec, 8000L, 950L, false, pathLengthD, diameterDp)
        assertEquals(before.u, start.u, .0001f)
        assertTrue(returnStart.u > start.u)
        assertTrue(abs(end.unclampedU - OrbFilamentPathMathV1.movement(
            spec, 8000L, false, OrbRuntimeProfileV1.MotionEvent.NONE, 0L,
        ).unclampedU) < .0001f)
    }
}
