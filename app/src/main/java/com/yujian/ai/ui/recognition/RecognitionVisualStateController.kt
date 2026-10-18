package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionRuntimeContract

/** V3.1 minimum presentation beats gated by corresponding real pipeline events. */
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

    /** Applies the configured visual Resolve only when it fits inside CLASSIFYING's minimum beat. */
    fun resolveStrength(nowMs: Long): Float {
        val fadeStartAtMs = resolveFadeStartAtMs() ?: return 1f
        val progress = ((nowMs - fadeStartAtMs).toFloat() / RESOLVE_FADE_MS)
            .coerceIn(0f, 1f)
        val remaining = 1f - progress
        return remaining * remaining
    }

    /** A late real result must not delay the V3.1 handoff with an extra timed phase. */
    fun resolveFadeStartAtMs(): Long? {
        if (presented != RecognitionPhase.CLASSIFYING || resultReceivedAtMs == UNSET) return null
        val latestFullFadeStartAtMs =
            phaseStartedAtMs + minimum(RecognitionPhase.CLASSIFYING) - RESOLVE_FADE_MS
        return latestFullFadeStartAtMs.takeIf { resultReceivedAtMs <= it }
    }

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
        const val CAPTURED_MIN_MS = RecognitionRuntimeContract.CAPTURED_MIN_MS
        const val DETECTING_MIN_MS = RecognitionRuntimeContract.DETECTING_MIN_MS
        const val OUTLINE_MIN_MS = RecognitionRuntimeContract.OUTLINE_MIN_MS
        const val CLASSIFYING_MIN_MS = RecognitionRuntimeContract.CLASSIFYING_MIN_MS
        const val RESOLVE_FADE_MS = RecognitionRuntimeContract.RESOLVE_FADE_MS
        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to CAPTURED_MIN_MS,
            RecognitionPhase.DETECTING to DETECTING_MIN_MS,
            RecognitionPhase.OUTLINE to OUTLINE_MIN_MS,
            RecognitionPhase.CLASSIFYING to CLASSIFYING_MIN_MS,
        )
    }
}
