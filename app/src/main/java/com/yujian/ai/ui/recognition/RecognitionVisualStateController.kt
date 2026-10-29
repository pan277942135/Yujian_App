package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase

/** Adaptive presentation clock: phase beats start at the stable photo frame and end on real model readiness. */
class RecognitionVisualStateController(
    private val minVisibleMs: Map<RecognitionPhase, Long> = DEFAULT_MIN_VISIBLE_MS,
) {
    private var presented = RecognitionPhase.CAPTURED
    private var phaseStartedAtMs = UNSET
    private var detectionStarted = false
    private var detectorBoxAvailable = false
    private var classificationStarted = false
    private var resultReceivedAtMs = UNSET
    private var outlineStartedAtMs = UNSET
    private var trustedMaskAcceptedAtMs = UNSET

    fun reset(nowMs: Long) {
        presented = RecognitionPhase.CAPTURED
        phaseStartedAtMs = nowMs
        detectionStarted = false
        detectorBoxAvailable = false
        classificationStarted = false
        resultReceivedAtMs = UNSET
        outlineStartedAtMs = UNSET
        trustedMaskAcceptedAtMs = UNSET
    }

    fun onTrustedMaskAvailable(nowMs: Long) {
        if (trustedMaskAcceptedAtMs == UNSET) trustedMaskAcceptedAtMs = nowMs
    }

    fun onPipelinePhase(
        phase: RecognitionPhase,
        nowMs: Long,
        hasRealFishFocus: Boolean = false,
    ) {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        when (phase) {
            RecognitionPhase.DETECTING -> detectionStarted = true
            RecognitionPhase.OUTLINE -> {
                // A later model callback can arrive before P0 and be replayed
                // as the only known phase at the stable-photo boundary.
                detectionStarted = true
                detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            }
            RecognitionPhase.CLASSIFYING -> {
                detectionStarted = true
                classificationStarted = true
                detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            }
            RecognitionPhase.RESULT -> {
                detectionStarted = true
                classificationStarted = true
                if (resultReceivedAtMs == UNSET) resultReceivedAtMs = nowMs
                detectorBoxAvailable = detectorBoxAvailable || hasRealFishFocus
            }
            RecognitionPhase.FAILURE -> {
                presented = RecognitionPhase.FAILURE
                phaseStartedAtMs = nowMs
            }
            RecognitionPhase.CAPTURED -> Unit
        }
    }

    fun current(nowMs: Long, trustedMask: Boolean = false): RecognitionPhase {
        if (phaseStartedAtMs == UNSET) reset(nowMs)
        if (presented == RecognitionPhase.FAILURE || presented == RecognitionPhase.RESULT) {
            return presented
        }

        while (canAdvance(nowMs, trustedMask)) {
            // Preserve the scheduled stage boundary instead of resetting it to this
            // frame's timestamp. If a frame arrives late, this carries elapsed time
            // forward without shortening any stage or stretching the whole timeline.
            val completedStageDurationMs = minimumFor(presented, trustedMask, nowMs)
            presented = next(presented)
            phaseStartedAtMs += completedStageDurationMs
            if (presented == RecognitionPhase.OUTLINE) outlineStartedAtMs = phaseStartedAtMs
            if (presented == RecognitionPhase.RESULT) break
        }
        return presented
    }

    fun presentedPhase(): RecognitionPhase = presented

    fun phaseElapsedMs(nowMs: Long): Long =
        (nowMs - phaseStartedAtMs).coerceAtLeast(0L)

    /** Maps adaptive stage progress onto the frozen P0–P7 relative optical curve. */
    fun opticalReferenceElapsedMs(nowMs: Long, trustedMask: Boolean = false): Long {
        val elapsed = phaseElapsedMs(nowMs)
        val start: Long
        val end: Long
        val duration: Long
        when (presented) {
            RecognitionPhase.CAPTURED -> {
                start = 0L; end = 350L; duration = minimum(RecognitionPhase.CAPTURED)
            }
            RecognitionPhase.DETECTING -> {
                start = 350L; end = 950L; duration = minimum(RecognitionPhase.DETECTING)
            }
            RecognitionPhase.OUTLINE -> {
                start = 950L; end = 1_550L; duration = minimum(RecognitionPhase.OUTLINE)
            }
            RecognitionPhase.CLASSIFYING -> {
                start = if (detectorBoxAvailable) 1_550L else 950L
                end = 2_800L
                duration = minimumFor(RecognitionPhase.CLASSIFYING, trustedMask, nowMs)
            }
            RecognitionPhase.RESULT,
            RecognitionPhase.FAILURE -> return 2_800L
        }
        if (duration <= 0L) return end
        val p = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        return (start + ((end - start) * p)).toLong()
    }

    private fun canAdvance(nowMs: Long, trustedMask: Boolean): Boolean {
        val elapsed = phaseElapsedMs(nowMs)
        if (elapsed < minimumFor(presented, trustedMask, nowMs)) return false
        return when (presented) {
            RecognitionPhase.CAPTURED -> detectionStarted || resultReceivedAtMs != UNSET
            RecognitionPhase.DETECTING -> detectorBoxAvailable || resultReceivedAtMs != UNSET
            RecognitionPhase.OUTLINE -> classificationStarted || resultReceivedAtMs != UNSET
            RecognitionPhase.CLASSIFYING -> resultReceivedAtMs != UNSET
            RecognitionPhase.RESULT,
            RecognitionPhase.FAILURE -> false
        }
    }

    private fun minimum(phase: RecognitionPhase): Long = minVisibleMs[phase] ?: 0L

    private fun minimumFor(phase: RecognitionPhase, trustedMask: Boolean, nowMs: Long): Long = when (phase) {
        RecognitionPhase.CLASSIFYING -> if (trustedMask) {
            val outlineStart = outlineStartedAtMs.takeUnless { it == UNSET } ?: nowMs
            val maskAccepted = trustedMaskAcceptedAtMs.takeUnless { it == UNSET } ?: outlineStart
            val contourStart = maxOf(outlineStart, maskAccepted)
            val goldPeakHoldEndsAt = contourStart + RECOGNITION_MASK_CONTOUR_REVEAL_MS + GOLD_PEAK_HOLD_MS
            maxOf(minimum(phase), (goldPeakHoldEndsAt - phaseStartedAtMs).coerceAtLeast(0L))
        } else {
            UNMASKED_CLASSIFYING_MIN_MS
        }
        else -> minimum(phase)
    }

    private fun next(phase: RecognitionPhase): RecognitionPhase = when (phase) {
        RecognitionPhase.CAPTURED -> RecognitionPhase.DETECTING
        RecognitionPhase.DETECTING -> if (detectorBoxAvailable) RecognitionPhase.OUTLINE else RecognitionPhase.CLASSIFYING
        RecognitionPhase.OUTLINE -> RecognitionPhase.CLASSIFYING
        RecognitionPhase.CLASSIFYING -> RecognitionPhase.RESULT
        RecognitionPhase.RESULT,
        RecognitionPhase.FAILURE -> phase
    }

    companion object {
        const val CAPTURED_MIN_MS = 180L
        const val DETECTING_MIN_MS = 700L
        const val OUTLINE_MIN_MS = 750L
        const val MASKED_CLASSIFYING_MIN_MS = 800L
        const val UNMASKED_CLASSIFYING_MIN_MS = 350L
        const val GOLD_PEAK_HOLD_MS = 450L
        const val LEGACY_REFERENCE_END_MS = 2_800L
        private const val UNSET = Long.MIN_VALUE

        val DEFAULT_MIN_VISIBLE_MS = mapOf(
            RecognitionPhase.CAPTURED to CAPTURED_MIN_MS,
            RecognitionPhase.DETECTING to DETECTING_MIN_MS,
            RecognitionPhase.OUTLINE to OUTLINE_MIN_MS,
            RecognitionPhase.CLASSIFYING to MASKED_CLASSIFYING_MIN_MS,
        )
    }
}
