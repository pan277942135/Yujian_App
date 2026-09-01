package com.yujian.ai.contracts

import com.yujian.ai.feedback.FeedbackDraft
import org.json.JSONObject

data class FeedbackContract(
    val aiPrediction: String,
    val userLabel: String?,
    val isError: Boolean,
    val hardCase: Boolean,
    val feedbackType: String,
    val userNote: String? = null,
) {
    init {
        require(aiPrediction.isNotBlank()) { "ai_prediction is required" }
        require(feedbackType.isNotBlank()) { "feedback_type is required" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("ai_prediction", aiPrediction)
        put("user_label", userLabel ?: JSONObject.NULL)
        put("is_error", isError)
        put("hard_case", hardCase)
        put("feedback_type", feedbackType)
        put("user_note", userNote ?: JSONObject.NULL)
    }

    companion object {
        fun fromDraft(draft: FeedbackDraft): FeedbackContract {
            val corrected = draft.correctedSpecies?.trim().orEmpty()
            val isError = corrected.isNotBlank() && !corrected.equals(draft.predictedSpecies.trim(), ignoreCase = true)
            return FeedbackContract(
                aiPrediction = draft.predictedSpecies,
                userLabel = corrected.ifBlank { null },
                isError = isError,
                hardCase = isError,
                feedbackType = draft.feedbackType,
                userNote = draft.userNote,
            )
        }
    }
}
