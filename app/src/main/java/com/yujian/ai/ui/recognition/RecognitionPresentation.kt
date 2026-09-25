package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionFailureCode
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ai.RecognitionTerminal

enum class RecognitionProcessingState {
    CAPTURE_TRANSITION,
    AI_UNDERSTANDING,
    FISH_HIGHLIGHT,
    FISH_IDENTIFYING,
}

enum class RecognitionResultLevel {
    HIGH,
    MEDIUM,
    LOW,
}

enum class RecognitionErrorState {
    NO_FISH,
    IMAGE_QUALITY,
    TECHNICAL,
}

data class RecognitionProcessingPresentation(
    val state: RecognitionProcessingState,
    val title: String,
    val subtitle: String,
)

/** Pure mapping between domain events and the frozen Recognition Flow V1 UI vocabulary. */
object RecognitionPresentation {
    fun processing(phase: RecognitionPhase): RecognitionProcessingPresentation = when (phase) {
        RecognitionPhase.CAPTURED -> RecognitionProcessingPresentation(
            RecognitionProcessingState.CAPTURE_TRANSITION,
            "正在准备识别",
            "AI 已获取这张照片",
        )
        RecognitionPhase.DETECTING -> RecognitionProcessingPresentation(
            RecognitionProcessingState.AI_UNDERSTANDING,
            "正在理解照片",
            "查找鱼获线索",
        )
        RecognitionPhase.OUTLINE -> RecognitionProcessingPresentation(
            RecognitionProcessingState.FISH_HIGHLIGHT,
            "已定位到鱼体",
            "正在分析这次鱼获",
        )
        RecognitionPhase.CLASSIFYING -> RecognitionProcessingPresentation(
            RecognitionProcessingState.FISH_IDENTIFYING,
            "正在认识这条鱼",
            "分析鱼体特征",
        )
        RecognitionPhase.RESULT -> RecognitionProcessingPresentation(
            RecognitionProcessingState.FISH_IDENTIFYING,
            "认识完成",
            "分析鱼体特征",
        )
    }

    fun resultLevel(result: ProductionRecognitionResult): RecognitionResultLevel = when (result.terminal) {
        RecognitionTerminal.SUCCESS -> RecognitionResultLevel.HIGH
        RecognitionTerminal.CONFIRM -> RecognitionResultLevel.MEDIUM
        RecognitionTerminal.UNKNOWN -> RecognitionResultLevel.LOW
        else -> when {
            result.prediction == null -> RecognitionResultLevel.LOW
            result.prediction.top1.confidence < 0.45f -> RecognitionResultLevel.LOW
            result.prediction.candidates.getOrNull(1)?.let {
                result.prediction.top1.confidence - it.confidence < 0.12f
            } == true -> RecognitionResultLevel.MEDIUM
            else -> RecognitionResultLevel.HIGH
        }
    }

    fun errorState(result: ProductionRecognitionResult): RecognitionErrorState = when {
        result.terminal == RecognitionTerminal.NO_FISH || result.status == FishInputStatus.NO_FISH ->
            RecognitionErrorState.NO_FISH
        result.failureCode != null && result.failureCode != RecognitionFailureCode.INVALID_CROP ->
            RecognitionErrorState.TECHNICAL
        else -> RecognitionErrorState.IMAGE_QUALITY
    }
}
