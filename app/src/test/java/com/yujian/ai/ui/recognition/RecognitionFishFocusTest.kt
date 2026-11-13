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
    fun goldStrengthFollowsSemanticContourStagesAndHoldsThroughResultStages() {
        assertEquals(0f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_A, 0f)), 0f)
        assertEquals(.15f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_A, 1f)), 0f)
        assertEquals(.15f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_B, 0f)), 0f)
        assertEquals(.45f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_B, 1f)), .0001f)
        assertEquals(1f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_C, 1f)), 0f)
        assertEquals(1f, recognitionFishGoldStrength(scene(RecognitionSceneStage.RESIDUE_SETTLE, 1f)), 0f)
        assertEquals(.18f, recognitionFishGoldStrength(scene(RecognitionSceneStage.CONTOUR_C, 1f), reduceMotion = true), 0f)
    }

    @Test
    fun fishGoldPeakUsesCore300msSoft500msAndHalo200ms() {
        val start = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 0L))
        val coreComplete = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 300L))
        val peakHold = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 799L))
        val handoffStart = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 800L))
        val handoffMid = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 900L))
        val complete = recognitionFishGoldLayers(sceneAt(RecognitionSceneStage.FISH_GOLD_PEAK, 1_000L))

        assertEquals(0f, start.coreProgress, 0f)
        assertEquals(0f, start.softProgress, 0f)
        assertEquals(0f, start.haloProgress, 0f)
        assertEquals(1f, coreComplete.coreProgress, 0f)
        assertEquals(1f, coreComplete.softProgress, 0f)
        assertTrue(coreComplete.softPeakHoldActive)
        assertEquals(1f, peakHold.coreProgress, 0f)
        assertEquals(1f, peakHold.softProgress, 0f)
        assertTrue(peakHold.softPeakHoldActive)
        assertFalse(handoffStart.softPeakHoldActive)
        assertEquals(0f, handoffStart.haloProgress, 0f)
        assertEquals(.5f, handoffMid.haloProgress, .0001f)
        assertEquals(1f, complete.haloProgress, 0f)
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
            maskSha256 = "c".repeat(64),
            maskVersion = 7L,
            contourHash = 13,
            geometryVersion = 11L,
        )
        assertEquals(base, base.copy())
        assertNotEquals(base, base.copy(sessionId = "session-2"))
        assertNotEquals(base, base.copy(attemptId = "attempt-2"))
        assertNotEquals(base, base.copy(imageId = "image-2"))
        assertNotEquals(base, base.copy(photoSha256 = "b".repeat(64)))
        assertNotEquals(base, base.copy(maskSha256 = "d".repeat(64)))
        assertNotEquals(base, base.copy(maskVersion = 8L))
        assertNotEquals(base, base.copy(contourHash = 14))
        assertNotEquals(base, base.copy(geometryVersion = 12L))
    }

    private fun sceneAt(stage: RecognitionSceneStage, elapsedMs: Long) =
        RecognitionSceneCoordinator.frameForStage(
            identity = RecognitionSceneIdentity("session", "attempt", "image", "a".repeat(64), 0L),
            sceneElapsedMs = elapsedMs,
            opticalReferenceElapsedMs = elapsedMs,
            geometryVersion = 0L,
            stage = stage,
            stageElapsedMs = elapsedMs,
            stageProgress = (elapsedMs.toFloat() / stage.minimumVisibleMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
        )

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
