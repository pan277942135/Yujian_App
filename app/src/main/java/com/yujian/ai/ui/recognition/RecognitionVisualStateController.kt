package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
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
    private var detectorBoxAvailable = false
    private var maskTerminal = false
    private var trustedMaskAvailable = false
    private var classifierReady = false
    private var revealDecisionReady = false
    private var detectorPathPresented = false
    private var activeIdentity: RecognitionSceneIdentity? = null
    private var activeGeometryVersion: Long? = null
    private var resultHandoffAnchored = false
    private var failed = false

    fun reset(
        nowMs: Long,
        identity: RecognitionSceneIdentity? = null,
        geometryVersion: Long? = null,
    ) {
        stage = RecognitionSceneStage.PHOTO_STABLE
        stageStartedAtMs = nowMs
        detectorBoxAvailable = false
        maskTerminal = false
        trustedMaskAvailable = false
        classifierReady = false
        revealDecisionReady = false
        detectorPathPresented = false
        activeIdentity = identity
        activeGeometryVersion = geometryVersion
        resultHandoffAnchored = false
        failed = false
    }

    /** Geometry changes invalidate any detector path sampled against the old viewport. */
    fun onSceneGeometryChanged(identity: RecognitionSceneIdentity, geometryVersion: Long) {
        if (activeIdentity != null && activeIdentity != identity) return
        if (activeGeometryVersion != geometryVersion) {
            activeIdentity = identity
            activeGeometryVersion = geometryVersion
            detectorPathPresented = false
        }
    }

    /** Called after the current identity's real-BBox path has been emitted by the PATH host. */
    fun onDetectorPathPresented(identity: RecognitionSceneIdentity, geometryVersion: Long) {
        if (activeIdentity != identity || activeGeometryVersion != geometryVersion) return
        detectorPathPresented = true
    }

    /** Deterministic test and isolated-preview hook; production uses the identity overload. */
    fun onDetectorPathPresented() {
        detectorPathPresented = true
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
        if (!canAdvance(stage, successfulRevealAllowed)) return stage

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

    /** Canonical optical coordinate follows the current scene stage, independent of wall-clock dwell. */
    fun opticalReferenceElapsedMs(
        nowMs: Long,
        @Suppress("UNUSED_PARAMETER") trustedMask: Boolean = false,
        @Suppress("UNUSED_PARAMETER") subjectTerminal: Boolean = true,
    ): Long {
        if (stage == RecognitionSceneStage.PHOTO_STABLE) return 0L
        return stage.startMs + (stage.durationMs * stageProgress(nowMs)).toLong()
    }

    /** Processing hands off at the beginning of Orb return, preserving it as Result-frame T0. */
    fun resultVisualReady(@Suppress("UNUSED_PARAMETER") nowMs: Long): Boolean =
        !failed && stage == RecognitionSceneStage.ORB_RETURN &&
            classifierReady && revealDecisionReady

    /** Starts the shared ORB_RETURN beat at Result's first presented frame (RR09 R0). */
    fun anchorResultHandoffAt(nowMs: Long): Boolean {
        if (failed || stage != RecognitionSceneStage.ORB_RETURN || resultHandoffAnchored) return false
        stageStartedAtMs = nowMs
        resultHandoffAnchored = true
        return true
    }

    /** RR09 coordinate is a projection of the shared stage and its local progress. */
    fun revealElapsedMs(nowMs: Long): Long =
        (opticalReferenceElapsedMs(nowMs) - RecognitionSceneStage.ORB_RETURN.startMs).coerceAtLeast(0L)

    /** Completes only after the final stage's own minimum presentation beat. */
    fun revealSettled(nowMs: Long): Boolean = !failed &&
        stage == RecognitionSceneStage.RESIDUE_SETTLE && stageElapsedMs(nowMs) >= minimum(stage)

    private fun canAdvance(
        current: RecognitionSceneStage,
        successfulRevealAllowed: Boolean,
    ): Boolean = when (current) {
        RecognitionSceneStage.PHOTO_STABLE,
        RecognitionSceneStage.ORB_WAKE,
        RecognitionSceneStage.ORB_RELEASE -> true
        RecognitionSceneStage.PERIMETER_ARRIVAL ->
            detectorBoxAvailable || (maskTerminal && classifierReady && revealDecisionReady)
        RecognitionSceneStage.PATH_TO_FISH ->
            detectorPathPresented && maskTerminal && classifierReady && revealDecisionReady
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B -> trustedMaskAvailable && successfulRevealAllowed
        RecognitionSceneStage.CONTOUR_C ->
            trustedMaskAvailable && classifierReady && revealDecisionReady
        RecognitionSceneStage.FISH_GOLD_PEAK ->
            classifierReady && revealDecisionReady
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME,
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> classifierReady && revealDecisionReady
        RecognitionSceneStage.RESIDUE_SETTLE -> false
    }

    private fun next(current: RecognitionSceneStage, successfulRevealAllowed: Boolean): RecognitionSceneStage =
        when (current) {
            RecognitionSceneStage.PHOTO_STABLE -> RecognitionSceneStage.ORB_WAKE
            RecognitionSceneStage.ORB_WAKE -> RecognitionSceneStage.ORB_RELEASE
            RecognitionSceneStage.ORB_RELEASE -> RecognitionSceneStage.PERIMETER_ARRIVAL
            RecognitionSceneStage.PERIMETER_ARRIVAL -> if (detectorBoxAvailable) {
                RecognitionSceneStage.PATH_TO_FISH
            } else {
                RecognitionSceneStage.ORB_RETURN
            }
            RecognitionSceneStage.PATH_TO_FISH -> if (trustedMaskAvailable && successfulRevealAllowed) {
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
            RecognitionSceneStage.ORB_RETURN -> RecognitionSceneStage.ORB_TO_NAME
            RecognitionSceneStage.ORB_TO_NAME -> RecognitionSceneStage.SPECIES_NAME_REVEAL
            RecognitionSceneStage.SPECIES_NAME_REVEAL -> RecognitionSceneStage.RESIDUE_SETTLE
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
        const val ORB_TO_NAME_MIN_MS = 170L
        const val SPECIES_NAME_REVEAL_MIN_MS = 130L
        const val RESIDUE_SETTLE_MIN_MS = 170L
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
            RecognitionSceneStage.ORB_TO_NAME to ORB_TO_NAME_MIN_MS,
            RecognitionSceneStage.SPECIES_NAME_REVEAL to SPECIES_NAME_REVEAL_MIN_MS,
            RecognitionSceneStage.RESIDUE_SETTLE to RESIDUE_SETTLE_MIN_MS,
        )
    }
}
