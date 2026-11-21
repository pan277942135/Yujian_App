package com.yujian.ai.ai

fun recognitionRequiresDiscardConfirmation(
    speciesChanged: Boolean,
    length: String,
    weight: String,
    location: String,
    story: String,
    saveError: String?,
    saveRequested: Boolean,
    saving: Boolean,
): Boolean = speciesChanged ||
    length.isNotBlank() ||
    weight.isNotBlank() ||
    location.isNotBlank() ||
    story.isNotBlank() ||
    !saveError.isNullOrBlank() ||
    saveRequested ||
    saving
