package com.yujian.ai.ai

/** Terminal routes exposed by the recognition result and issue screens. */
enum class RecognitionTerminal {
    SUCCESS,
    CONFIRM,
    UNKNOWN,
    NO_FISH,
    TOO_FAR,
    ERROR,
}

enum class DetectionRoute {
    OUTLINE,
    NO_FISH,
    TOO_FAR,
}

enum class ClassificationRoute {
    SUCCESS,
    CONFIRM,
    UNKNOWN,
}

enum class RecognitionFailureCode {
    EMPTY_IMAGE,
    DETECTOR_FAILED,
    INVALID_CROP,
    CLASSIFIER_FAILED,
}

sealed interface RecognitionEvent {
    data object CaptureReady : RecognitionEvent
    data object DetectionStarted : RecognitionEvent
    data class DetectionFinished(val route: DetectionRoute) : RecognitionEvent
    data object ClassificationStarted : RecognitionEvent
    data class ClassificationFinished(val route: ClassificationRoute) : RecognitionEvent
    data class Failed(val code: RecognitionFailureCode) : RecognitionEvent
}

data class RecognitionState(
    val phase: RecognitionPhase,
    val terminal: RecognitionTerminal? = null,
    val failureCode: RecognitionFailureCode? = null,
)

/**
 * Small deterministic reducer for the recognition runtime.
 *
 * Invalid events are ignored so a late detector callback cannot move a terminal
 * result back into an intermediate visual state.
 */
class RecognitionStateMachine(
    initial: RecognitionState = RecognitionState(RecognitionPhase.CAPTURED),
) {
    var state: RecognitionState = initial
        private set

    fun dispatch(event: RecognitionEvent): RecognitionState {
        if (state.terminal != null) return state
        state = when (event) {
            RecognitionEvent.CaptureReady -> state.takeIf { it.phase == RecognitionPhase.CAPTURED } ?: state
            RecognitionEvent.DetectionStarted ->
                state.takeIf { it.phase == RecognitionPhase.CAPTURED }
                    ?.copy(phase = RecognitionPhase.DETECTING)
                    ?: state
            is RecognitionEvent.DetectionFinished -> when (event.route) {
                DetectionRoute.OUTLINE ->
                    state.takeIf { it.phase == RecognitionPhase.DETECTING }
                        ?.copy(phase = RecognitionPhase.OUTLINE)
                        ?: state
                DetectionRoute.NO_FISH -> terminal(RecognitionTerminal.NO_FISH)
                DetectionRoute.TOO_FAR -> terminal(RecognitionTerminal.TOO_FAR)
            }
            RecognitionEvent.ClassificationStarted ->
                state.takeIf { it.phase == RecognitionPhase.OUTLINE }
                    ?.copy(phase = RecognitionPhase.CLASSIFYING)
                    ?: state
            is RecognitionEvent.ClassificationFinished -> when (event.route) {
                ClassificationRoute.SUCCESS -> terminal(RecognitionTerminal.SUCCESS)
                ClassificationRoute.CONFIRM -> terminal(RecognitionTerminal.CONFIRM)
                ClassificationRoute.UNKNOWN -> terminal(RecognitionTerminal.UNKNOWN)
            }
            is RecognitionEvent.Failed -> state.copy(
                phase = RecognitionPhase.RESULT,
                terminal = RecognitionTerminal.ERROR,
                failureCode = event.code,
            )
        }
        return state
    }

    private fun terminal(route: RecognitionTerminal): RecognitionState =
        state.copy(phase = RecognitionPhase.RESULT, terminal = route)
}
