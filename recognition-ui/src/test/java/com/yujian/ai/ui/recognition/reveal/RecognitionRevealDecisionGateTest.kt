package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.subject.SubjectStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionRevealDecisionGateTest {
    @Test fun pStarStateDistinguishesPendingReadyAndTerminalUnavailable() {
        assertEquals(
            RecognitionPStarPathState.PSTAR_PENDING,
            RecognitionRevealDecisionGate.pStarState(hasBoundPath = false, terminalUnavailable = false),
        )
        assertEquals(
            RecognitionPStarPathState.PSTAR_READY,
            RecognitionRevealDecisionGate.pStarState(hasBoundPath = true, terminalUnavailable = false),
        )
        assertEquals(
            RecognitionPStarPathState.PSTAR_TERMINAL_UNAVAILABLE,
            RecognitionRevealDecisionGate.pStarState(hasBoundPath = false, terminalUnavailable = true),
        )
        assertEquals(
            RecognitionPStarPathState.PSTAR_READY,
            RecognitionRevealDecisionGate.pStarState(hasBoundPath = true, terminalUnavailable = true),
        )
    }

    @Test fun readySubjectWithoutItsGeometrySnapshotStaysPendingInsteadOfDowngrading() {
        assertEquals(
            SubjectStatus.PROCESSING,
            RecognitionRevealDecisionGate.effectiveSubjectStatus(
                SubjectStatus.READY,
                hasAuthoritativeGeometry = false,
            ),
        )
        assertEquals(
            SubjectStatus.READY,
            RecognitionRevealDecisionGate.effectiveSubjectStatus(
                SubjectStatus.READY,
                hasAuthoritativeGeometry = true,
            ),
        )
    }

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
            contourClosed = true,
            pStarPathReady = true,
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
            contourClosed = true,
            pStarPathReady = true,
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
            contourClosed = true,
            pStarPathReady = true,
        )
        assertEquals(RecognitionRevealDecisionState.FISH_FOCUS_READY, decision)
        assertEquals(true, RecognitionRevealDecisionGate.isReady(decision))
    }

    @Test fun openContourCannotEnterFullRevealAndPStarWaitIsExplicitlyTerminalizable() {
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            contourClosed = false,
            pStarPathReady = true,
            expected = RecognitionRevealDecisionState.ORB_ONLY_READY,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            contourClosed = true,
            pStarPathReady = false,
            expected = RecognitionRevealDecisionState.WAITING_PSTAR_PATH,
        )
        assertDecision(
            RecognitionClassifierTaskState.READY,
            SubjectStatus.READY,
            trusted = true,
            contourValid = true,
            contourClosed = true,
            pStarPathReady = false,
            pStarPathTerminalUnavailable = true,
            expected = RecognitionRevealDecisionState.ORB_ONLY_READY,
        )
    }

    @Test fun mediumOrLowClassifierResultUsesNavigableStaticRouteWithoutAnimationPass() {
        val decision = RecognitionRevealDecisionGate.decide(
            classifier = RecognitionClassifierTaskState.READY,
            subject = SubjectStatus.PROCESSING,
            maskTrusted = false,
            contourValid = false,
            highConfidenceResult = false,
        )
        assertEquals(RecognitionRevealDecisionState.STATIC_RESULT, decision)
        assertEquals(false, RecognitionRevealDecisionGate.isReady(decision))
        assertEquals(true, RecognitionRevealDecisionGate.isNavigable(decision))
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
        contourClosed: Boolean = false,
        pStarPathReady: Boolean = false,
        pStarPathTerminalUnavailable: Boolean = false,
        highConfidenceResult: Boolean = true,
        expected: RecognitionRevealDecisionState,
    ) {
        assertEquals(
            expected,
            RecognitionRevealDecisionGate.decide(
                classifier = classifier,
                subject = subject,
                maskTrusted = trusted,
                contourValid = contourValid,
                contourClosed = contourClosed,
                pStarPathReady = pStarPathReady,
                pStarPathTerminalUnavailable = pStarPathTerminalUnavailable,
                highConfidenceResult = highConfidenceResult,
            ),
        )
    }
}
