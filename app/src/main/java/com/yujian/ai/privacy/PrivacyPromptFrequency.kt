package com.yujian.ai.privacy

import android.content.Context

/**
 * Local UI frequency state for the post-correction AI model improvement prompt.
 *
 * This is intentionally separate from the server consent value. The server is
 * the source of truth for consent; this value only decides whether the app may
 * proactively show the prompt again.
 */
data class PrivacyPromptFrequency(
    val promptCount: Int = 0,
    val lastPromptAtMillis: Long? = null,
    val suppressed: Boolean = false,
)

const val PRIVACY_PROMPT_COOLDOWN_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

fun PrivacyPromptFrequency.canPrompt(
    nowMillis: Long,
    consentEnabled: Boolean,
    speciesCorrected: Boolean,
    fishRecordSaved: Boolean,
): Boolean {
    if (consentEnabled || suppressed || promptCount >= 3) return false
    if (!speciesCorrected || !fishRecordSaved) return false
    val last = lastPromptAtMillis ?: return true
    return nowMillis - last >= PRIVACY_PROMPT_COOLDOWN_MILLIS
}

fun PrivacyPromptFrequency.recordDismissal(nowMillis: Long): PrivacyPromptFrequency = copy(
    promptCount = (promptCount + 1).coerceAtMost(3),
    lastPromptAtMillis = nowMillis,
)

fun PrivacyPromptFrequency.suppressAfterManualWithdrawal(): PrivacyPromptFrequency = copy(suppressed = true)

class PrivacyPromptFrequencyStore(context: Context) {
    private val preferences = context.getSharedPreferences("yujian_ai_prompt_frequency", Context.MODE_PRIVATE)

    fun load(): PrivacyPromptFrequency = PrivacyPromptFrequency(
        promptCount = preferences.getInt(KEY_COUNT, 0),
        lastPromptAtMillis = preferences.getLong(KEY_LAST, 0L).takeIf { it > 0L },
        suppressed = preferences.getBoolean(KEY_SUPPRESSED, false),
    )

    fun save(state: PrivacyPromptFrequency) {
        preferences.edit()
            .putInt(KEY_COUNT, state.promptCount)
            .putLong(KEY_LAST, state.lastPromptAtMillis ?: 0L)
            .putBoolean(KEY_SUPPRESSED, state.suppressed)
            .apply()
    }

    private companion object {
        const val KEY_COUNT = "prompt_count"
        const val KEY_LAST = "last_prompt_at"
        const val KEY_SUPPRESSED = "prompt_suppressed"
    }
}
