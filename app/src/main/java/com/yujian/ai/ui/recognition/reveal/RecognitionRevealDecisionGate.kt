package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.subject.SubjectStatus

enum class RecognitionClassifierTaskState { WAITING, READY, FAILED, CANCELLED }

enum class RecognitionRevealDecisionState {
    WAITING_CLASSIFIER,
    WAITING_SLIMSAM,
    WAITING_BOTH,
    FISH_FOCUS_READY,
    ORB_ONLY_READY,
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
    ): RecognitionRevealDecisionState {
        if (!identityCurrent || classifier == RecognitionClassifierTaskState.CANCELLED) {
            return RecognitionRevealDecisionState.CANCELLED
        }
        if (classifier == RecognitionClassifierTaskState.FAILED) {
            return RecognitionRevealDecisionState.RECOGNITION_FAILED
        }
        val subjectTerminal = subject == SubjectStatus.READY || subject == SubjectStatus.FAILED
        if (classifier != RecognitionClassifierTaskState.READY) {
            return if (subjectTerminal) RecognitionRevealDecisionState.WAITING_CLASSIFIER
            else RecognitionRevealDecisionState.WAITING_BOTH
        }
        if (!subjectTerminal) return RecognitionRevealDecisionState.WAITING_SLIMSAM
        return if (subject == SubjectStatus.READY && maskTrusted && contourValid) {
            RecognitionRevealDecisionState.FISH_FOCUS_READY
        } else {
            RecognitionRevealDecisionState.ORB_ONLY_READY
        }
    }

    fun isReady(decision: RecognitionRevealDecisionState): Boolean =
        decision == RecognitionRevealDecisionState.FISH_FOCUS_READY ||
            decision == RecognitionRevealDecisionState.ORB_ONLY_READY
}
