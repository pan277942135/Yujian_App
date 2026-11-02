package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage

/**
 * Production recognition lifecycle controller.
 * The 13 stages advance from real model/geometry readiness plus per-stage minimum
 * presentation time. No absolute Processing/RR09 duration is used as a completion gate.
 */
class RecognitionVisualStateController(
    private val minVisibleMs: Map<RecognitionSceneStage, Long> = DEFAULT_MIN_VISIBLE_MS,
) {
    private var stage = RecognitionSceneStage.PHOTO_STABLE
    private var stageStartedAtMs = UNSET
    private var virtualReferenceBaseMs = 0L
    private var detectorBoxAvailable = false
    private var maskTerminal = false
    private var trustedMaskAvailable = false
    private var classifierReady = false
    private var revealDecisionReady = false
    private var failed = false

    fun reset(nowMs: Long) {
        stage = RecognitionSceneStage.PHOTO_STABLE
        stageStartedAtMs = nowMs
        virtualReferenceBaseMs = 0L
        detectorBoxAvailable = false
        maskTerminal = false
        trustedMaskAvailable = false
        classifierReady = false
        revealDecisionReady = false
        failed = false
    }

    fun onTrustedMaskAvailable(@Suppress("UNUSED_PARAMETER") nowMs: Long) {
        trustedMaskAvailable = true
        maskTerminal = true
    }

    /** Called only when the Classifier × SlimSAM decision gate is genuinely terminal. */
    fun onRevealDecisionReady() {
        revealDecisionReady = true
    }

    fun onPipelinePhase(
        phase: RecognitionPhase,
        nowMs: Long,
        hasRealFishFocus: Boolean = false,
    ) {
        if (stageStartedAtMs == UNSET) reset(nowMs)
        when (phase) {
            RecognitionPhase.DETECTING ->
                detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING -> detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            RecognitionPhase.RESULT -> {
                classifierReady = true
                detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            }
            RecognitionPhase.FAILURE -> failed = true
            RecognitionPhase.CAPTURED -> Unit
        }
    }

    /**
     * Samples and advances at most one stage. subjectTerminal means SlimSAM has returned
     * READY or FAILED; trustedMask is independently validated against this photo.
     */
    fun currentSceneStage(
        nowMs: Long,
        trustedMask: Boolean = false,
        subjectTerminal: Boolean = true,
        successfulRevealAllowed: Boolean = true,
    ): RecognitionSceneStage {
        if (stageStartedAtMs == UNSET) reset(nowMs)
        if (trustedMask) trustedMaskAvailable = true
        if (subjectTerminal) maskTerminal = true
        if (failed) return stage

        val elapsed = stageElapsedMs(nowMs)
        if (elapsed < minimum(stage)) return stage
        if (!canAdvance(stage)) return stage

        // Keep an unbounded monotonic reference for shaders/logging. It is never a global cutoff.
        virtualReferenceBaseMs += elapsed
        stage = next(stage, successfulRevealAllowed)
        stageStartedAtMs = nowMs
        return stage
    }

    fun current(
        nowMs: Long,
        trustedMask: Boolean = false,
        subjectTerminal: Boolean = true,
        successfulRevealAllowed: Boolean = true,
    ): RecognitionPhase = if (failed) RecognitionPhase.FAILURE else {
        currentSceneStage(nowMs, trustedMask, subjectTerminal, successfulRevealAllowed).toRecognitionPhase()
    }

    fun presentedStage(): RecognitionSceneStage = stage

    fun presentedPhase(): RecognitionPhase = if (failed) RecognitionPhase.FAILURE else stage.toRecognitionPhase()

    fun phaseElapsedMs(nowMs: Long): Long = stageElapsedMs(nowMs)

    fun stageElapsedMs(nowMs: Long): Long =
        if (stageStartedAtMs == UNSET) 0L else (nowMs - stageStartedAtMs).coerceAtLeast(0L)

    fun stageProgress(nowMs: Long): Float {
        val duration = minimum(stage)
        if (duration <= 0L) return 1f
        return (stageElapsedMs(nowMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    /** Monotonic, unbounded reference coordinate. Stage identity is supplied separately. */
    fun opticalReferenceElapsedMs(
        nowMs: Long,
        @Suppress("UNUSED_PARAMETER") trustedMask: Boolean = false,
        @Suppress("UNUSED_PARAMETER") subjectTerminal: Boolean = true,
    ): Long = virtualReferenceBaseMs + stageElapsedMs(nowMs)

    /** Processing hands off only after the real model join and Orb return beat are complete. */
    fun resultVisualReady(nowMs: Long): Boolean =
        !failed && stage == RecognitionSceneStage.ORB_RETURN &&
            classifierReady && revealDecisionReady &&
            stageElapsedMs(nowMs) >= minimum(RecognitionSceneStage.ORB_RETURN)

    private fun canAdvance(current: RecognitionSceneStage): Boolean = when (current) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE -> true
        RecognitionSceneStage.PERIMETER_ARRIVAL ->
            detectorBoxAvailable || (maskTerminal && classifierReady && revealDecisionReady)
        RecognitionSceneStage.PATH_TO_FISH ->
            trustedMaskAvailable || (maskTerminal && classifierReady && revealDecisionReady)
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B -> trustedMaskAvailable
        RecognitionSceneStage.CONTOUR_C ->
            trustedMaskAvailable && classifierReady && revealDecisionReady
        RecognitionSceneStage.FISH_GOLD_PEAK ->
            classifierReady && revealDecisionReady
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> false
    }

    private fun next(current: RecognitionSceneStage, successfulRevealAllowed: Boolean): RecognitionSceneStage =
        when (current) {
            RecognitionSceneStage.PHOTO_STABLE -> RecognitionSceneStage.ORB_WAKE
            RecognitionSceneStage.ORB_WAKE -> RecognitionSceneStage.ORB_RELEASE
            RecognitionSceneStage.ORB_RELEASE -> RecognitionSceneStage.PERIMETER_ARRIVAL
            RecognitionSceneStage.PERIMETER_ARRIVAL -> RecognitionSceneStage.PATH_TO_FISH
            RecognitionSceneStage.PATH_TO_FISH -> if (trustedMaskAvailable) {
                RecognitionSceneStage.CONTOUR_A
            } else {
                RecognitionSceneStage.ORB_RETURN
            }
            RecognitionSceneStage.CONTOUR_A -> RecognitionSceneStage.CONTOUR_B
            RecognitionSceneStage.CONTOUR_B -> RecognitionSceneStage.CONTOUR_C
            RecognitionSceneStage.CONTOUR_C -> if (successfulRevealAllowed) {
                RecognitionSceneStage.FISH_GOLD_PEAK
            } else {
                RecognitionSceneStage.ORB_RETURN
            }
            RecognitionSceneStage.FISH_GOLD_PEAK -> RecognitionSceneStage.ORB_RETURN
            RecognitionSceneStage.ORB_RETURN,
            RecognitionSceneStage.ORB_TO_NAME,
            RecognitionSceneStage.SPECIES_NAME_REVEAL,
            RecognitionSceneStage.RESIDUE_SETTLE -> current
        }

    private fun minimum(value: RecognitionSceneStage): Long =
        minVisibleMs[value] ?: DEFAULT_MIN_VISIBLE_MS[value] ?: 0L

    private fun RecognitionSceneStage.toRecognitionPhase(): RecognitionPhase = when (this) {
        RecognitionSceneStage.PHOTO_STABLE -> RecognitionPhase.CAPTURED
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE,
        RecognitionSceneStage.PERIMETER_ARRIVAL,
        RecognitionSceneStage.PATH_TO_FISH -> RecognitionPhase.DETECTING
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C -> RecognitionPhase.OUTLINE
        RecognitionSceneStage.FISH_GOLD_PEAK,
        RecognitionSceneStage.ORB_RETURN -> RecognitionPhase.CLASSIFYING
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL,
        RecognitionSceneStage.RESIDUE_SETTLE -> RecognitionPhase.RESULT
    }

    companion object {
        const val PHOTO_STABLE_MIN_MS = 120L
        const val ORB_WAKE_MIN_MS = 350L
        const val PERIMETER_SPREAD_MIN_MS = 1_200L
        const val PERIMETER_ARRIVAL_MIN_MS = 180L
        const val PATH_TO_FISH_MIN_MS = 220L
        const val CONTOUR_STAGE_MIN_MS = 250L
        const val GOLD_PEAK_HOLD_MS = 450L
        const val ORB_RETURN_MIN_MS = 380L
        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionSceneStage.PHOTO_STABLE to PHOTO_STABLE_MIN_MS,
            RecognitionSceneStage.ORB_WAKE to ORB_WAKE_MIN_MS,
            RecognitionSceneStage.ORB_RELEASE to PERIMETER_SPREAD_MIN_MS,
            RecognitionSceneStage.PERIMETER_ARRIVAL to PERIMETER_ARRIVAL_MIN_MS,
            RecognitionSceneStage.PATH_TO_FISH to PATH_TO_FISH_MIN_MS,
            RecognitionSceneStage.CONTOUR_A to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.CONTOUR_B to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.CONTOUR_C to CONTOUR_STAGE_MIN_MS,
            RecognitionSceneStage.FISH_GOLD_PEAK to GOLD_PEAK_HOLD_MS,
            RecognitionSceneStage.ORB_RETURN to ORB_RETURN_MIN_MS,
        )
    }
}
