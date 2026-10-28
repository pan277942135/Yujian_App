package com.yujian.ai.ui.identify

import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.model.RecognitionPrediction

/** Frozen product states. Technical failure stays outside the product flow. */
enum class RecognitionUiState {
    RESULT_HIGH,
    RESULT_MEDIUM,
    RESULT_LOW,
    ERROR_NO_FISH,
    ERROR_IMAGE_QUALITY,
    TECHNICAL_FAILURE,
}

fun resolveRecognitionResultState(prediction: RecognitionPrediction): RecognitionUiState {
    val second = prediction.candidates.getOrNull(1)
    return when {
        prediction.top1.confidence < 0.45f -> RecognitionUiState.RESULT_LOW
        second != null && prediction.top1.confidence - second.confidence < 0.12f ->
            RecognitionUiState.RESULT_MEDIUM
        else -> RecognitionUiState.RESULT_HIGH
    }
}

fun resolveRecognitionUiState(result: ProductionRecognitionResult): RecognitionUiState {
    result.failureCode?.let { return RecognitionUiState.TECHNICAL_FAILURE }
    if (result.status == FishInputStatus.NO_FISH) return RecognitionUiState.ERROR_NO_FISH
    if (!result.assessment.isClassifierEligible || result.prediction == null) {
        return RecognitionUiState.ERROR_IMAGE_QUALITY
    }
    return resolveRecognitionResultState(result.prediction)
}

fun recognitionFeedbackType(predictedSpeciesKey: String, selectedSpeciesKey: String): String =
    if (predictedSpeciesKey == selectedSpeciesKey) "confirmed" else "corrected"
