package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.subject.SubjectStatus

enum class RecognitionClassifierTaskState { WAITING, READY, FAILED, CANCELLED }

enum class RecognitionRevealDecisionState {
    WAITING_CLASSIFIER,
    WAITING_SLIMSAM,
    WAITING_BOTH,
    WAITING_PSTAR_PATH,
    FISH_FOCUS_READY,
    ORB_ONLY_READY,
    STATIC_RESULT,
    RECOGNITION_FAILED,
    CANCELLED,
}

/** The sole production join point for real Classifier and SlimSAM completion events. */
object RecognitionRevealDecisionGate {
    fun decide(
        classifier: RecognitionClassifierTaskState,
        subject: SubjectStatus,
        maskTrusted: Boolean,
        contourValid: Boolean,
        identityCurrent: Boolean = true,
        contourClosed: Boolean = false,
        pStarPathReady: Boolean = false,
        pStarPathTerminalUnavailable: Boolean = false,
        highConfidenceResult: Boolean = true,
    ): RecognitionRevealDecisionState {
        if (!identityCurrent || classifier == RecognitionClassifierTaskState.CANCELLED) {
            return RecognitionRevealDecisionState.CANCELLED
        }
        if (classifier == RecognitionClassifierTaskState.FAILED) {
            return RecognitionRevealDecisionState.RECOGNITION_FAILED
        }
        // A real Classifier result below the high-confidence threshold still has a
        // complete business destination; it does not wait for visual Mask work.
        if (classifier == RecognitionClassifierTaskState.READY && !highConfidenceResult) {
            return RecognitionRevealDecisionState.STATIC_RESULT
        }
        val subjectTerminal = subject == SubjectStatus.READY || subject == SubjectStatus.FAILED
        if (classifier != RecognitionClassifierTaskState.READY) {
            return if (subjectTerminal) RecognitionRevealDecisionState.WAITING_CLASSIFIER
            else RecognitionRevealDecisionState.WAITING_BOTH
        }
        if (!subjectTerminal) return RecognitionRevealDecisionState.WAITING_SLIMSAM
        if (subject != SubjectStatus.READY || !maskTrusted || !contourValid) {
            return RecognitionRevealDecisionState.ORB_ONLY_READY
        }
        if (!contourClosed || pStarPathTerminalUnavailable) return RecognitionRevealDecisionState.ORB_ONLY_READY
        if (!pStarPathReady) return RecognitionRevealDecisionState.WAITING_PSTAR_PATH
        return RecognitionRevealDecisionState.FISH_FOCUS_READY
    }

    fun isReady(decision: RecognitionRevealDecisionState): Boolean =
        decision == RecognitionRevealDecisionState.FISH_FOCUS_READY ||
            decision == RecognitionRevealDecisionState.ORB_ONLY_READY

    fun isNavigable(decision: RecognitionRevealDecisionState): Boolean =
        isReady(decision) || decision == RecognitionRevealDecisionState.STATIC_RESULT
}
