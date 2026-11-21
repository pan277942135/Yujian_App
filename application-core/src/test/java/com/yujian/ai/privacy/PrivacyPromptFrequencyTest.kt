package com.yujian.ai.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPromptFrequencyTest {
    private val now = 1_000_000L

    @Test
    fun prompts_only_after_corrected_record_is_saved() {
        val state = PrivacyPromptFrequency()
        assertFalse(state.canPrompt(now, consentEnabled = false, speciesCorrected = false, fishRecordSaved = true))
        assertFalse(state.canPrompt(now, consentEnabled = false, speciesCorrected = true, fishRecordSaved = false))
        assertTrue(state.canPrompt(now, consentEnabled = false, speciesCorrected = true, fishRecordSaved = true))
    }

    @Test
    fun three_dismissals_are_never_prompted_again() {
        val state = PrivacyPromptFrequency().recordDismissal(now)
            .recordDismissal(now + PRIVACY_PROMPT_COOLDOWN_MILLIS)
            .recordDismissal(now + PRIVACY_PROMPT_COOLDOWN_MILLIS * 2)
        assertFalse(state.canPrompt(now + PRIVACY_PROMPT_COOLDOWN_MILLIS * 3, false, true, true))
    }

    @Test
    fun seven_day_cooldown_and_consent_gate_are_enforced() {
        val state = PrivacyPromptFrequency().recordDismissal(now)
        assertFalse(state.canPrompt(now + PRIVACY_PROMPT_COOLDOWN_MILLIS - 1, false, true, true))
        assertTrue(state.canPrompt(now + PRIVACY_PROMPT_COOLDOWN_MILLIS, false, true, true))
        assertFalse(state.canPrompt(now + PRIVACY_PROMPT_COOLDOWN_MILLIS, true, true, true))
    }

    @Test
    fun manual_withdrawal_suppresses_future_proactive_prompts() {
        val state = PrivacyPromptFrequency().suppressAfterManualWithdrawal()
        assertFalse(state.canPrompt(now + PRIVACY_PROMPT_COOLDOWN_MILLIS * 4, false, true, true))
    }
}

