package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase

/**
 * Presentation controller for the frozen three-state Recognition story.
 *
 * The production pipeline still emits CAPTURED -> DETECTING -> OUTLINE ->
 * CLASSIFYING -> RESULT / FAILURE. Presentation intentionally merges CAPTURED
 * and DETECTING into the single user-visible opening state "图片识别中".
 *
 * User-visible presentation sequence:
 *
 * CAPTURED (图片识别中) -> OUTLINE (已定位到鱼体)
 * -> CLASSIFYING (鱼种识别中) -> 200ms RESOLVE -> RESULT
 *
 * Slow pipeline work gates advancement. The controller may hold a real phase for
 * minimum perceptual duration, but it never invents a later semantic state.
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
        if (phase == RecognitionPhase.RESULT && resultReceivedAtMs == UNSET) {
            resultReceivedAtMs = nowMs
        }
        if (phase == RecognitionPhase.FAILURE) {
            presented = RecognitionPhase.FAILURE
            phaseStartedAtMs = nowMs
        }
    }

    fun current(nowMs: Long): RecognitionPhase {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        if (presented == RecognitionPhase.FAILURE || presented == RecognitionPhase.RESULT) {
            return presented
        }

        while (canAdvance(nowMs)) {
            presented = next(presented)
            phaseStartedAtMs = nowMs
            if (presented == RecognitionPhase.RESULT) break
        }
        return presented
    }

    fun presentedPhase(): RecognitionPhase = presented

    fun phaseElapsedMs(nowMs: Long): Long =
        (nowMs - phaseStartedAtMs).coerceAtLeast(0L)

    /**
     * RESOLVE is not a fourth Processing state. It begins only after the
     * 鱼种识别中 minimum beat has completed and only when a real RESULT exists.
     */
    fun resolveProgress(nowMs: Long): Float {
        if (presented != RecognitionPhase.CLASSIFYING || actual != RecognitionPhase.RESULT) {
            return 0f
        }
        val elapsed = phaseElapsedMs(nowMs)
        return (
            (elapsed - minimum(RecognitionPhase.CLASSIFYING)).toFloat() /
                RESOLVE_FADE_MS.toFloat()
            ).coerceIn(0f, 1f)
    }

    private fun canAdvance(nowMs: Long): Boolean {
        val target = next(presented)
        if (target == presented) return false

        val elapsed = phaseElapsedMs(nowMs)

        if (target == RecognitionPhase.RESULT) {
            if (actual != RecognitionPhase.RESULT) return false
            return elapsed >= minimum(RecognitionPhase.CLASSIFYING) + RESOLVE_FADE_MS
        }

        if (rank(actual) < rank(target)) return false
        return elapsed >= minimum(presented)
    }

    private fun minimum(phase: RecognitionPhase): Long =
        minVisibleMs[phase] ?: 0L

    /**
     * DETECTING is a pipeline phase only. Presentation skips it because
     * CAPTURED + DETECTING are one product state: 图片识别中.
     */
    private fun next(phase: RecognitionPhase): RecognitionPhase = when (phase) {
        RecognitionPhase.CAPTURED,
        RecognitionPhase.DETECTING -> RecognitionPhase.OUTLINE
        RecognitionPhase.OUTLINE -> RecognitionPhase.CLASSIFYING
        RecognitionPhase.CLASSIFYING -> RecognitionPhase.RESULT
        else -> phase
    }

    private fun rank(phase: RecognitionPhase) = when (phase) {
        RecognitionPhase.CAPTURED -> 0
        RecognitionPhase.DETECTING -> 1
        RecognitionPhase.OUTLINE -> 2
        RecognitionPhase.CLASSIFYING -> 3
        RecognitionPhase.RESULT,
        RecognitionPhase.FAILURE -> 4
    }

    companion object {
        /**
         * Retained as a compatibility constant for callers that still expose
         * the former V1.1 bound. The current nominal fast story is 2950ms.
         */
        const val MAX_POST_RESULT_HOLD_MS = 2_950L
        const val RESOLVE_FADE_MS = 200L
        const val IMAGE_RECOGNIZING_MIN_MS = 900L
        const val FISH_LOCATED_MIN_MS = 600L
        const val SPECIES_RECOGNIZING_MIN_MS = 1_250L

        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to IMAGE_RECOGNIZING_MIN_MS,
            RecognitionPhase.DETECTING to 0L,
            RecognitionPhase.OUTLINE to FISH_LOCATED_MIN_MS,
            RecognitionPhase.CLASSIFYING to SPECIES_RECOGNIZING_MIN_MS,
        )
    }
}
