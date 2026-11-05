package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFishFocusTest {
    @Test
    fun goldStrengthFollowsContourGoldPeakAndOrbReturnTiming() {
        assertEquals(0f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_A, 0f)), 0f)
        assertEquals(.15f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_A, 1f)), 0f)
        assertEquals(.15f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_B, 0f)), 0f)
        assertEquals(.45f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_B, 1f)), .0001f)
        assertEquals(.75f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_C, 1f)), .0001f)
        assertEquals(.75f, recognitionFishGoldStrength(scene(RecognitionSceneStage.FISH_GOLD_PEAK, 0f)), .0001f)
        assertEquals(1f, recognitionFishGoldStrength(scene(RecognitionSceneStage.FISH_GOLD_PEAK, .30f)), .0001f)
        assertEquals(1f, recognitionFishGoldStrength(scene(RecognitionSceneStage.FISH_GOLD_PEAK, .80f)), 0f)
        assertEquals(.92f, recognitionFishGoldStrength(scene(RecognitionSceneStage.FISH_GOLD_PEAK, 1f)), .0001f)
        assertEquals(.92f, recognitionFishGoldStrength(scene(RecognitionSceneStage.ORB_RETURN, 0f)), .0001f)
        assertEquals(0f, recognitionFishGoldStrength(scene(RecognitionSceneStage.ORB_RETURN, 1f)), 0f)
        assertEquals(0f, recognitionFishGoldStrength(scene(RecognitionSceneStage.RESIDUE_SETTLE, 1f)), 0f)
        assertEquals(.18f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_C, 1f), reduceMotion = true), 0f)
    }

    @Test
    fun untrustedMaskNeverEnablesAnyFishShapeAndTrustedMaskIsPhaseGated() {
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = false))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.CLASSIFYING, maskTrusted = false))
        assertTrue(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = true))
        assertTrue(recognitionMaskContourEnabled(RecognitionPhase.CLASSIFYING, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.CAPTURED, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.RESULT, maskTrusted = true))
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.FAILURE, maskTrusted = true))
    }

    @Test
    fun maskAcceptanceClosesWhenAdaptiveTimelineLeavesTheContourStage() {
        assertTrue(recognitionMaskAcceptanceOpen(RecognitionPhase.CAPTURED))
        assertTrue(recognitionMaskAcceptanceOpen(RecognitionPhase.DETECTING))
        assertTrue(recognitionMaskAcceptanceOpen(RecognitionPhase.OUTLINE))
        assertTrue(recognitionMaskAcceptanceOpen(RecognitionPhase.CLASSIFYING))
        assertFalse(recognitionMaskAcceptanceOpen(RecognitionPhase.RESULT))
    }

    @Test
    fun trustedMaskContourRevealsAcrossTheThreeSemanticContourStages() {
        assertEquals(0f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_A, 0f, true), 0f)
        assertEquals(1f / 3f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_A, 1f, true), .0001f)
        assertEquals(.5f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_B, .5f, true), .0001f)
        assertEquals(1f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_C, 1f, true), 0f)
        assertEquals(1f, recognitionMaskContourRevealProgress(
            RecognitionSceneStage.CONTOUR_A, 0f, true, reduceMotion = true,
        ), 0f)
        assertEquals(0f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_A, 1f, false), 0f)
    }

    @Test
    fun contourRevealStartsAtTailAndExpandsAlongBothSidesUntilTheyMeet() {
        val start = requireNotNull(recognitionContourRevealPlan(contourLength = 100f, tailDistance = 95f, progress = 0f))
        assertEquals(95f, start.forwardFromTail.startDistance, 0f)
        assertEquals(95f, start.forwardFromTail.endDistance, 0f)
        assertEquals(95f, start.backwardFromTail.startDistance, 0f)
        assertEquals(95f, start.backwardFromTail.endDistance, 0f)

        val middle = requireNotNull(recognitionContourRevealPlan(contourLength = 100f, tailDistance = 95f, progress = .4f))
        assertEquals(20f, middle.forwardFromTail.endDistance - middle.forwardFromTail.startDistance, .0001f)
        assertEquals(20f, middle.backwardFromTail.endDistance - middle.backwardFromTail.startDistance, .0001f)
        assertEquals(95f, middle.forwardFromTail.startDistance, 0f)
        assertEquals(95f, middle.backwardFromTail.endDistance, 0f)
        assertTrue(middle.forwardFromTail.endDistance > 100f) // the renderer wraps the path at its close.

        val complete = requireNotNull(recognitionContourRevealPlan(contourLength = 100f, tailDistance = 95f, progress = 1f))
        assertEquals(50f, complete.forwardFromTail.endDistance - complete.forwardFromTail.startDistance, .0001f)
        assertEquals(50f, complete.backwardFromTail.endDistance - complete.backwardFromTail.startDistance, .0001f)
    }

    @Test
    fun contourOpticalLayersHaveSeparateProgressiveAlphaEnvelopes() {
        val start = recognitionContourLayerAlphas(energyStrength = 1f, contourRevealProgress = 0f)
        assertEquals(0f, start.core, 0f)
        assertEquals(0f, start.soft, 0f)
        assertEquals(0f, start.halo, 0f)

        val early = recognitionContourLayerAlphas(energyStrength = 1f, contourRevealProgress = .2f)
        assertTrue(early.core > early.soft)
        assertEquals(0f, early.halo, 0f)

        val mid = recognitionContourLayerAlphas(energyStrength = 1f, contourRevealProgress = .55f)
        assertTrue(mid.core > early.core)
        assertTrue(mid.soft > early.soft)
        assertTrue(mid.halo > early.halo)

        val complete = recognitionContourLayerAlphas(energyStrength = .5f, contourRevealProgress = 1f)
        assertEquals(.29f, complete.core, .0001f)
        assertEquals(.125f, complete.soft, .0001f)
        assertEquals(.0525f, complete.halo, .0001f)
    }

    @Test
    fun orbOnlyCannotRevealContourEvenAcrossContourStages() {
        assertEquals(0f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_A, 1f, trustedMask = false), 0f)
        assertEquals(0f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_B, 1f, trustedMask = false), 0f)
        assertEquals(0f, recognitionMaskContourRevealProgress(RecognitionSceneStage.CONTOUR_C, 1f, trustedMask = false), 0f)
        assertEquals(0f, recognitionContourLayerAlphas(energyStrength = 0f, contourRevealProgress = 1f).core, 0f)
    }

    @Test
    fun noMaskMayBeRecordedAsLowCapabilityButDoesNotPromoteToAContour() {
        assertEquals(
            RecognitionFishFocusLevel.B,
            recognitionDisplayedFishFocusLevel(
                RecognitionFishFocusLevel.A,
                levelAAvailable = false,
                phaseElapsedMs = 0L,
                phaseOverrideActive = false,
            ),
        )
        assertFalse(recognitionMaskContourEnabled(RecognitionPhase.OUTLINE, maskTrusted = false))
    }

    @Test
    fun contourMaterialCandidateMatchesTheAuditedWebLayerContract() {
        assertEquals(0xFF063B46L, RecognitionContourMaterialCandidate.UNDER_STROKE_COLOR)
        assertEquals(.80f, RecognitionContourMaterialCandidate.UNDER_STROKE_OPACITY, 0f)
        assertEquals(
            listOf(
                0xFF0284C7L, 0xFF38BDF8L, 0xFFFDE68AL, 0xFFF59E0BL,
                0xFF0284C7L, 0xFF38BDF8L, 0xFFFDE68AL, 0xFFF59E0BL, 0xFF0284C7L,
            ),
            RecognitionContourMaterialCandidate.CORE_GRADIENT_COLORS,
        )
        assertEquals(2_200L, RecognitionContourMaterialCandidate.GOLD_GLINT_PERIOD_MS)
        assertEquals(.20f, RecognitionContourMaterialCandidate.GOLD_GLINT_ARC_RATIO, 0f)
    }

    @Test
    fun contourCacheIdentityInvalidatesOnEveryRecognitionBoundary() {
        val base = RecognitionContourCacheIdentity(
            sessionId = "session-1",
            attemptId = "attempt-1",
            imageId = "image-1",
            photoSha256 = "a".repeat(64),
            maskVersion = 7L,
            contourHash = 13,
            geometryVersion = 11L,
        )
        assertEquals(base, base.copy())
        assertNotEquals(base, base.copy(sessionId = "session-2"))
        assertNotEquals(base, base.copy(attemptId = "attempt-2"))
        assertNotEquals(base, base.copy(imageId = "image-2"))
        assertNotEquals(base, base.copy(photoSha256 = "b".repeat(64)))
        assertNotEquals(base, base.copy(maskVersion = 8L))
        assertNotEquals(base, base.copy(contourHash = 14))
        assertNotEquals(base, base.copy(geometryVersion = 12L))
    }

    private fun scene(stage: RecognitionSceneStage, progress: Float) =
        RecognitionSceneCoordinator.frameForStage(
            identity = RecognitionSceneIdentity("session", "attempt", "image", "a".repeat(64), 0L),
            sceneElapsedMs = 0L,
            opticalReferenceElapsedMs = 0L,
            geometryVersion = 0L,
            stage = stage,
            stageElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
            stageProgress = progress,
        )
}
