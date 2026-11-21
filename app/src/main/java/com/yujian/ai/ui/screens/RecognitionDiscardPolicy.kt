package com.yujian.ai.ui.screens

import com.yujian.ai.ai.recognitionRequiresDiscardConfirmation as recognitionRequiresDiscardConfirmationCore

internal fun recognitionRequiresDiscardConfirmation(
    speciesChanged: Boolean,
    length: String,
    weight: String,
    location: String,
    story: String,
    saveError: String?,
    saveRequested: Boolean,
    saving: Boolean,
): Boolean = recognitionRequiresDiscardConfirmationCore(
    speciesChanged = speciesChanged,
    length = length,
    weight = weight,
    location = location,
    story = story,
    saveError = saveError,
    saveRequested = saveRequested,
    saving = saving,
)
