package com.yujian.ai.ai

/** Android timing projection of the frozen Recognition Processing State Machine V3.1. */
object RecognitionRuntimeContract {
    const val CONTRACT_VERSION = "recognition_processing_state_machine_v3_1"
    const val CAPTURED_MIN_MS = 220L
    const val DETECTING_MIN_MS = 320L
    const val OUTLINE_MIN_MS = 350L
    const val CLASSIFYING_MIN_MS = 250L
    /** Frozen visual handoff parameter; not an additional V3.1 presentation state. */
    const val RESOLVE_FADE_MS = 200L
    const val CLASSIFYING_BREATH_PERIOD_MS = 1_900L

    val states: List<RecognitionProcessingStateContract> = listOf(
        RecognitionProcessingStateContract(
            RecognitionPresentationState.CAPTURED,
            CAPTURED_MIN_MS,
            "正在准备识别",
            "AI 已获取这张照片",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.DETECTING,
            DETECTING_MIN_MS,
            "正在理解这张照片",
            "寻找这次鱼获的线索",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.OUTLINE,
            OUTLINE_MIN_MS,
            "已定位到鱼体",
            "正在分析这次鱼获",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.CLASSIFYING,
            CLASSIFYING_MIN_MS,
            "正在认识这条鱼",
            "分析鱼体特征",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.RESULT_READY,
            0L,
            "",
            "",
        ),
    )

    val presentationSequence: List<RecognitionPresentationState> = states.map { it.state }

    val productionPipeline: List<String> = listOf(
        "CAPTURED",
        "DETECTING",
        "OUTLINE",
        "CLASSIFYING",
        "RESULT",
        "FAILURE",
    )

    fun state(state: RecognitionPresentationState): RecognitionProcessingStateContract =
        states.first { it.state == state }
}

enum class RecognitionPresentationState {
    CAPTURED,
    DETECTING,
    OUTLINE,
    CLASSIFYING,
    RESULT_READY,
}

data class RecognitionProcessingStateContract(
    val state: RecognitionPresentationState,
    val minimumDurationMs: Long,
    val title: String,
    val subtitle: String,
)
