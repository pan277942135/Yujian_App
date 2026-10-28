package com.yujian.ai.ai

/** Android projection of the frozen Recognition Processing V3.1 contract. */
object RecognitionRuntimeContract {
    const val CONTRACT_VERSION = "recognition_processing_state_machine_v3_1"

    val states: List<RecognitionProcessingStateContract> = listOf(
        RecognitionProcessingStateContract(
            RecognitionPresentationState.CAPTURED,
            220L,
            "正在准备识别",
            "AI 已获取这张照片",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.DETECTING,
            320L,
            "正在理解这张照片",
            "寻找这次鱼获的线索",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.OUTLINE,
            350L,
            "已定位到鱼体",
            "正在分析这次鱼获",
        ),
        RecognitionProcessingStateContract(
            RecognitionPresentationState.CLASSIFYING,
            250L,
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
