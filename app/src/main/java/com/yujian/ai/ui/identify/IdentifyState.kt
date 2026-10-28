package com.yujian.ai.ui.identify

import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.model.RecognitionPrediction

enum class IdentifyState {
    CAPTURED,
    DETECTING,
    OUTLINE,
    CLASSIFYING,
    SUCCESS,
    CONFIRM,
    UNKNOWN,
    NO_FISH,
    TOO_FAR,
}

fun resolveIdentifyResultState(
    status: FishInputStatus,
    prediction: RecognitionPrediction?,
): IdentifyState {
    if (status == FishInputStatus.NO_FISH) return IdentifyState.NO_FISH
    if (status == FishInputStatus.FISH_TOO_SMALL) return IdentifyState.TOO_FAR
    if (prediction == null) return IdentifyState.UNKNOWN

    val top = prediction.top1
    val second = prediction.candidates.getOrNull(1)
    if (top.confidence < 0.45f) return IdentifyState.UNKNOWN
    if (second != null && (top.confidence - second.confidence) < 0.12f) {
        return IdentifyState.CONFIRM
    }
    return IdentifyState.SUCCESS
}

fun IdentifyState.title(): String = when (this) {
    IdentifyState.SUCCESS -> "认识到了"
    IdentifyState.CONFIRM -> "有两个结果很接近"
    IdentifyState.UNKNOWN -> "暂时无法确认"
    IdentifyState.NO_FISH -> "没有找到鱼获主体"
    IdentifyState.TOO_FAR -> "鱼距离太远"
    else -> "正在认识这条鱼"
}
