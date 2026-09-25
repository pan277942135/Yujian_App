package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionStateMachineTest {
    @Test
    fun happyPathIsMonotonicAndTerminal() {
        val machine = RecognitionStateMachine()

        machine.dispatch(RecognitionEvent.CaptureReady)
        machine.dispatch(RecognitionEvent.DetectionStarted)
        machine.dispatch(RecognitionEvent.DetectionFinished(DetectionRoute.OUTLINE))
        machine.dispatch(RecognitionEvent.ClassificationStarted)
        val result = machine.dispatch(RecognitionEvent.ClassificationFinished(ClassificationRoute.SUCCESS))

        assertEquals(RecognitionPhase.RESULT, result.phase)
        assertEquals(RecognitionTerminal.SUCCESS, result.terminal)
        assertEquals(result, machine.dispatch(RecognitionEvent.DetectionStarted))
    }

    @Test
    fun qualityRoutesTerminateBeforeClassification() {
        val noFish = RecognitionStateMachine()
        noFish.dispatch(RecognitionEvent.DetectionStarted)
        assertEquals(
            RecognitionTerminal.NO_FISH,
            noFish.dispatch(RecognitionEvent.DetectionFinished(DetectionRoute.NO_FISH)).terminal,
        )

        val tooFar = RecognitionStateMachine()
        tooFar.dispatch(RecognitionEvent.DetectionStarted)
        assertEquals(
            RecognitionTerminal.TOO_FAR,
            tooFar.dispatch(RecognitionEvent.DetectionFinished(DetectionRoute.TOO_FAR)).terminal,
        )
    }

    @Test
    fun classifierRoutesExposeHighMediumAndLowResultStates() {
        listOf(
            ClassificationRoute.SUCCESS to RecognitionTerminal.SUCCESS,
            ClassificationRoute.CONFIRM to RecognitionTerminal.CONFIRM,
            ClassificationRoute.UNKNOWN to RecognitionTerminal.UNKNOWN,
        ).forEach { (route, terminal) ->
            val machine = RecognitionStateMachine()
            machine.dispatch(RecognitionEvent.CaptureReady)
            machine.dispatch(RecognitionEvent.DetectionStarted)
            machine.dispatch(RecognitionEvent.DetectionFinished(DetectionRoute.OUTLINE))
            machine.dispatch(RecognitionEvent.ClassificationStarted)

            val result = machine.dispatch(RecognitionEvent.ClassificationFinished(route))

            assertEquals(RecognitionPhase.RESULT, result.phase)
            assertEquals(terminal, result.terminal)
        }
    }

    @Test
    fun invalidEventsDoNotSkipStagesAndFailuresAreTerminal() {
        val machine = RecognitionStateMachine()
        assertEquals(RecognitionPhase.CAPTURED, machine.dispatch(RecognitionEvent.ClassificationStarted).phase)
        assertEquals(RecognitionPhase.CAPTURED, machine.state.phase)

        val failed = machine.dispatch(RecognitionEvent.Failed(RecognitionFailureCode.DETECTOR_FAILED))
        assertEquals(RecognitionPhase.RESULT, failed.phase)
        assertEquals(RecognitionTerminal.ERROR, failed.terminal)
        assertEquals(RecognitionFailureCode.DETECTOR_FAILED, failed.failureCode)
    }
}
