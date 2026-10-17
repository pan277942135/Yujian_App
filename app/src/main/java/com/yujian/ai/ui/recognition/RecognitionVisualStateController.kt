package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase

/** V3.1 presentation state driven by pipeline events and minimum visual beats. */
class RecognitionVisualStateController(
    private val minVisibleMs: Map<RecognitionPhase, Long> = DEFAULT_MIN_VISIBLE_MS,
) {
    private var presented = RecognitionPhase.CAPTURED
    private var phaseStartedAtMs = UNSET
    private var detectionStarted = false
    private var realFishFocusAvailable = false
    private var classificationStarted = false
    private var resultReceivedAtMs = UNSET

    fun reset(nowMs: Long) {
        presented = RecognitionPhase.CAPTURED
        phaseStartedAtMs = nowMs
        detectionStarted = false
        realFishFocusAvailable = false
        classificationStarted = false
        resultReceivedAtMs = UNSET
    }

    fun onPipelinePhase(
        phase: RecognitionPhase,
        nowMs: Long,
        hasRealFishFocus: Boolean = false,
    ) {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        when (phase) {
            RecognitionPhase.DETECTING -> detectionStarted = true
            RecognitionPhase.OUTLINE -> realFishFocusAvailable = hasRealFishFocus
            RecognitionPhase.CLASSIFYING -> {
                classificationStarted = true
                realFishFocusAvailable = realFishFocusAvailable || hasRealFishFocus
            }
            RecognitionPhase.RESULT -> {
                if (resultReceivedAtMs == UNSET) resultReceivedAtMs = nowMs
                realFishFocusAvailable = realFishFocusAvailable || hasRealFishFocus
            }
            RecognitionPhase.FAILURE -> {
                presented = RecognitionPhase.FAILURE
                phaseStartedAtMs = nowMs
            }
            RecognitionPhase.CAPTURED -> Unit
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

    private fun canAdvance(nowMs: Long): Boolean {
        val elapsed = phaseElapsedMs(nowMs)
        if (elapsed < minimum(presented)) return false
        return when (presented) {
            RecognitionPhase.CAPTURED -> detectionStarted
            RecognitionPhase.DETECTING -> realFishFocusAvailable
            RecognitionPhase.OUTLINE -> classificationStarted && realFishFocusAvailable
            RecognitionPhase.CLASSIFYING -> resultReceivedAtMs != UNSET
            RecognitionPhase.RESULT,
            RecognitionPhase.FAILURE -> false
        }
    }

    private fun minimum(phase: RecognitionPhase): Long = minVisibleMs[phase] ?: 0L

    private fun next(phase: RecognitionPhase): RecognitionPhase = when (phase) {
        RecognitionPhase.CAPTURED -> RecognitionPhase.DETECTING
        RecognitionPhase.DETECTING -> RecognitionPhase.OUTLINE
        RecognitionPhase.OUTLINE -> RecognitionPhase.CLASSIFYING
        RecognitionPhase.CLASSIFYING -> RecognitionPhase.RESULT
        RecognitionPhase.RESULT,
        RecognitionPhase.FAILURE -> phase
    }

    companion object {
        const val CAPTURED_MIN_MS = 220L
        const val DETECTING_MIN_MS = 320L
        const val OUTLINE_MIN_MS = 350L
        const val CLASSIFYING_MIN_MS = 250L
        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to CAPTURED_MIN_MS,
            RecognitionPhase.DETECTING to DETECTING_MIN_MS,
            RecognitionPhase.OUTLINE to OUTLINE_MIN_MS,
            RecognitionPhase.CLASSIFYING to CLASSIFYING_MIN_MS,
        )
    }
}
