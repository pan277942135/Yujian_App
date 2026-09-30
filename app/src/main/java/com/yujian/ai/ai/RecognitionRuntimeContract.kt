package com.yujian.ai.ai

/** User-visible Processing contract. Runtime pipeline phases remain unchanged. */
object RecognitionRuntimeContract {
    const val CONTRACT_VERSION = "RECOGNITION_PRESENTATION_v1_3"

    const val FISH_LOCATED_START_MS = 900L
    const val SPECIES_RECOGNIZING_START_MS = 1_500L
    const val RESOLVE_START_MS = 2_750L
    const val RESULT_START_MS = 2_950L

    val timeline: List<RecognitionPresentationStep> = listOf(
        RecognitionPresentationStep(
            RecognitionPresentationState.IMAGE_RECOGNIZING,
            0L,
            "图片识别中",
        ),
        RecognitionPresentationStep(
            RecognitionPresentationState.FISH_LOCATED,
            FISH_LOCATED_START_MS,
            "已定位到鱼体",
        ),
        RecognitionPresentationStep(
            RecognitionPresentationState.SPECIES_RECOGNIZING,
            SPECIES_RECOGNIZING_START_MS,
            "鱼种识别中",
        ),
        RecognitionPresentationStep(
            RecognitionPresentationState.RESOLVE,
            RESOLVE_START_MS,
            "",
        ),
        RecognitionPresentationStep(
            RecognitionPresentationState.RESULT,
            RESULT_START_MS,
            "识别结果",
        ),
    )

    init {
        require(timeline.first().startMs == 0L)
        require(timeline.zipWithNext().all { (current, next) -> current.startMs < next.startMs })
        require(
            timeline.count {
                it.state == RecognitionPresentationState.IMAGE_RECOGNIZING ||
                    it.state == RecognitionPresentationState.FISH_LOCATED ||
                    it.state == RecognitionPresentationState.SPECIES_RECOGNIZING
            } == 3,
        )
        require(timeline.none { it.state.name == "DETECTING" })
    }

    fun stateAt(elapsedMs: Long): RecognitionPresentationState {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        return timeline.lastOrNull { elapsed >= it.startMs }?.state
            ?: RecognitionPresentationState.IMAGE_RECOGNIZING
    }

    fun labelFor(state: RecognitionPresentationState): String =
        timeline.first { it.state == state }.label
}

enum class RecognitionPresentationState {
    IMAGE_RECOGNIZING,
    FISH_LOCATED,
    SPECIES_RECOGNIZING,
    RESOLVE,
    RESULT,
}

data class RecognitionPresentationStep(
    val state: RecognitionPresentationState,
    val startMs: Long,
    val label: String,
)
