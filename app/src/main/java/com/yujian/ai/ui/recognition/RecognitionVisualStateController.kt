package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase

/**
 * Presents the real recognition phases long enough to be perceived, without
 * ever delaying detector or classifier work. This class has no model access.
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

    private fun canAdvance(nowMs: Long): Boolean {
        val next = next(presented)
        if (next == RecognitionPhase.RESULT) {
            if (actual != RecognitionPhase.RESULT) return false
        } else if (rank(actual) < rank(next)) {
            return false
        }
        val elapsed = nowMs - phaseStartedAtMs
        val required = effectiveMinimum(presented)
        return elapsed >= required || (resultReceivedAtMs != UNSET && nowMs - resultReceivedAtMs >= maxPostResultHoldMs)
    }

    private fun effectiveMinimum(phase: RecognitionPhase): Long {
        val normal = minVisibleMs[phase] ?: 0L
        if (resultReceivedAtMs == UNSET) return normal
        // A fast model may finish before all visual states have been displayed.
        // Compress evenly into the bounded post-result window, retaining order.
        val sequenceTotal = DEFAULT_SEQUENCE.sumOf { minVisibleMs[it] ?: 0L }.coerceAtLeast(1L)
        return (normal * maxPostResultHoldMs / sequenceTotal).coerceAtLeast(MIN_COMPRESSED_VISIBLE_MS)
    }

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
        const val MAX_POST_RESULT_HOLD_MS = 900L
        private const val MIN_COMPRESSED_VISIBLE_MS = 80L
        private const val UNSET = Long.MIN_VALUE
        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to 220L,
            RecognitionPhase.DETECTING to 320L,
            RecognitionPhase.OUTLINE to 350L,
            RecognitionPhase.CLASSIFYING to 250L,
        )
        private val DEFAULT_SEQUENCE = listOf(
            RecognitionPhase.CAPTURED,
            RecognitionPhase.DETECTING,
            RecognitionPhase.OUTLINE,
            RecognitionPhase.CLASSIFYING,
        )
    }
}
