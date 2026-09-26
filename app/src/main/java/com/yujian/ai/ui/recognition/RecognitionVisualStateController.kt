package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase

/**
 * Presents the real recognition phases long enough to be perceived, without
 * ever delaying detector or classifier work. This class has no model access.
 *
 * V1.1 intentionally has no fast-result compression: a completed model is
 * retained while the frozen 2.8s visual story plays. Slow model phases still
 * gate advancement, so the UI never invents an OUTLINE before a real bbox.
 */
class RecognitionVisualStateController(
    private val minVisibleMs: Map<RecognitionPhase, Long> = DEFAULT_MIN_VISIBLE_MS,
    private val maxPostResultHoldMs: Long = MAX_POST_RESULT_HOLD_MS,
) {
    private var actual = RecognitionPhase.CAPTURED
    private var presented = RecognitionPhase.CAPTURED
    private var phaseStartedAtMs = UNSET
    private var resultReceivedAtMs = UNSET

    fun reset(nowMs: Long) {
        actual = RecognitionPhase.CAPTURED
        presented = RecognitionPhase.CAPTURED
        phaseStartedAtMs = nowMs
        resultReceivedAtMs = UNSET
    }

    fun onPipelinePhase(phase: RecognitionPhase, nowMs: Long) {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        actual = phase
        if (phase == RecognitionPhase.RESULT && resultReceivedAtMs == UNSET) resultReceivedAtMs = nowMs
        if (phase == RecognitionPhase.FAILURE) presented = RecognitionPhase.FAILURE
    }

    fun current(nowMs: Long): RecognitionPhase {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        if (presented == RecognitionPhase.FAILURE || presented == RecognitionPhase.RESULT) return presented
        while (canAdvance(nowMs)) {
            presented = next(presented)
            phaseStartedAtMs = nowMs
            if (presented == RecognitionPhase.RESULT) break
        }
        return presented
    }

    fun presentedPhase(): RecognitionPhase = presented

    fun phaseElapsedMs(nowMs: Long): Long = (nowMs - phaseStartedAtMs).coerceAtLeast(0L)

    /** 0..1 for the final 200ms fade, but only once a real result exists. */
    fun resolveProgress(nowMs: Long): Float {
        if (presented != RecognitionPhase.CLASSIFYING || actual != RecognitionPhase.RESULT) return 0f
        val elapsed = (nowMs - phaseStartedAtMs).coerceAtLeast(0L)
        return ((elapsed - (minimum(RecognitionPhase.CLASSIFYING) - RESOLVE_FADE_MS)).toFloat() / RESOLVE_FADE_MS)
            .coerceIn(0f, 1f)
    }

    private fun canAdvance(nowMs: Long): Boolean {
        val next = next(presented)
        if (next == RecognitionPhase.RESULT) {
            if (actual != RecognitionPhase.RESULT) return false
        } else if (rank(actual) < rank(next)) {
            return false
        }
        return nowMs - phaseStartedAtMs >= minimum(presented)
    }

    private fun minimum(phase: RecognitionPhase): Long = minVisibleMs[phase] ?: 0L

    private fun next(phase: RecognitionPhase): RecognitionPhase = when (phase) {
        RecognitionPhase.CAPTURED -> RecognitionPhase.DETECTING
        RecognitionPhase.DETECTING -> RecognitionPhase.OUTLINE
        RecognitionPhase.OUTLINE -> RecognitionPhase.CLASSIFYING
        RecognitionPhase.CLASSIFYING -> RecognitionPhase.RESULT
        else -> phase
    }

    private fun rank(phase: RecognitionPhase) = when (phase) {
        RecognitionPhase.CAPTURED -> 0
        RecognitionPhase.DETECTING -> 1
        RecognitionPhase.OUTLINE -> 2
        RecognitionPhase.CLASSIFYING, RecognitionPhase.RESULT, RecognitionPhase.FAILURE -> 3
    }

    companion object {
        /** Kept as an API compatibility constant for callers/tests from V1. */
        const val MAX_POST_RESULT_HOLD_MS = 2_800L
        const val RESOLVE_FADE_MS = 200L
        private const val UNSET = Long.MIN_VALUE
        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to 350L,
            RecognitionPhase.DETECTING to 600L,
            RecognitionPhase.OUTLINE to 600L,
            RecognitionPhase.CLASSIFYING to 1_250L,
        )
        private val DEFAULT_SEQUENCE = listOf(
            RecognitionPhase.CAPTURED,
            RecognitionPhase.DETECTING,
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING,
        )
    }
}
