package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.subject.SubjectStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionRevealDecisionGateTest {
    @Test fun classifierFirstWaitsForTrustedSlimSamThenChoosesFishFocus() {
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.PROCESSING,
            expected = RecognitionRevealDecisionState.WAITING_SLIMSAM,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            expected = RecognitionRevealDecisionState.FISH_FOCUS_READY,
        )
    }

    @Test fun classifierFirstWithUntrustedMaskChoosesOrbOnly() {
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.PROCESSING,
            expected = RecognitionRevealDecisionState.WAITING_SLIMSAM,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = false,
            contourValid = false,
            expected = RecognitionRevealDecisionState.ORB_ONLY_READY,
        )
    }

    @Test fun classifierFirstWithSlimSamFailureKeepsRealSpeciesOnOrbPath() {
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.FAILED,
            expected = RecognitionRevealDecisionState.ORB_ONLY_READY,
        )
    }

    @Test fun trustedSlimSamFirstWaitsForClassifier() {
        assertDecision(
            RecognitionClassifierTaskState.WAITING,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            expected = RecognitionRevealDecisionState.WAITING_CLASSIFIER,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            expected = RecognitionRevealDecisionState.FISH_FOCUS_READY,
        )
    }

    @Test fun untrustedSlimSamFirstWaitsForClassifierThenChoosesOrbOnly() {
        assertDecision(
            RecognitionClassifierTaskState.WAITING,
            SubjectStatus.READY,
            expected = RecognitionRevealDecisionState.WAITING_CLASSIFIER,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            expected = RecognitionRevealDecisionState.ORB_ONLY_READY,
        )
    }

    @Test fun simultaneousResultsMakeOneDeterministicDecision() {
        val decision = RecognitionRevealDecisionGate.decide(
            classifier = RecognitionClassifierTaskState.READY,
            subject = SubjectStatus.READY,
            maskTrusted = true,
            contourValid = true,
        )
        assertEquals(RecognitionRevealDecisionState.FISH_FOCUS_READY, decision)
        assertEquals(true, RecognitionRevealDecisionGate.isReady(decision))
    }

    @Test fun processingIsNeverTreatedAsSlimSamFailureEvenAfterLongWait() {
        repeat(5) {
            assertDecision(
                RecognitionClassifierTaskState.READY,
                SubjectStatus.PROCESSING,
                expected = RecognitionRevealDecisionState.WAITING_SLIMSAM,
            )
        }
        assertEquals(
            RecognitionRevealDecisionState.WAITING_BOTH,
            RecognitionRevealDecisionGate.decide(
                RecognitionClassifierTaskState.WAITING,
                SubjectStatus.IDLE,
                maskTrusted = false,
                contourValid = false,
            ),
        )
    }

    @Test fun staleOrCancelledAttemptCannotCompleteReveal() {
        assertEquals(
            RecognitionRevealDecisionState.CANCELLED,
            RecognitionRevealDecisionGate.decide(
                RecognitionClassifierTaskState.READY,
                SubjectStatus.FAILED,
                maskTrusted = false,
                contourValid = false,
                identityCurrent = false,
            ),
        )
        assertEquals(
            RecognitionRevealDecisionState.CANCELLED,
            RecognitionRevealDecisionGate.decide(
                RecognitionClassifierTaskState.CANCELLED,
                SubjectStatus.READY,
                maskTrusted = true,
                contourValid = true,
            ),
        )
    }

    @Test fun failedClassifierDoesNotRevealEvenIfMaskIsTrusted() {
        assertDecision(
            RecognitionClassifierTaskState.FAILED,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            expected = RecognitionRevealDecisionState.RECOGNITION_FAILED,
        )
    }

    private fun assertDecision(
        classifier: RecognitionClassifierTaskState,
        subject: SubjectStatus,
        trusted: Boolean = false,
        contourValid: Boolean = false,
        expected: RecognitionRevealDecisionState,
    ) {
        assertEquals(
            expected,
            RecognitionRevealDecisionGate.decide(
                classifier = classifier,
                subject = subject,
                maskTrusted = trusted,
                contourValid = contourValid,
            ),
        )
    }
}
