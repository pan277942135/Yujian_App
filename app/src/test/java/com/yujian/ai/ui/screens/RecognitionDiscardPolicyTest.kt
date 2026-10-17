package com.yujian.ai.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDiscardPolicyTest {
    @Test
    fun unchangedHighResultCanExitWithoutDiscardPrompt() {
        assertFalse(
            recognitionRequiresDiscardConfirmation(
                speciesChanged = false,
                length = "",
                weight = "",
                location = "",
                story = "",
                saveError = null,
                saveRequested = false,
                saving = false,
            ),
        )
    }

    @Test
    fun manualSpeciesOrAnyEnteredMetadataRequiresConfirmation() {
        val common = mapOf(
            "length" to "",
            "weight" to "",
            "location" to "",
            "story" to "",
        )
        assertTrue(
            recognitionRequiresDiscardConfirmation(
                speciesChanged = true,
                length = common.getValue("length"),
                weight = common.getValue("weight"),
                location = common.getValue("location"),
                story = common.getValue("story"),
                saveError = null,
                saveRequested = false,
                saving = false,
            ),
        )
        assertTrue(
            recognitionRequiresDiscardConfirmation(
                speciesChanged = false,
                length = "32",
                weight = "",
                location = "",
                story = "",
                saveError = null,
                saveRequested = false,
                saving = false,
            ),
        )
    }

    @Test
    fun failedOrUnresolvedSaveRequiresConfirmationEvenWithoutFieldEdits() {
        assertTrue(
            recognitionRequiresDiscardConfirmation(
                speciesChanged = false,
                length = "",
                weight = "",
                location = "",
                story = "",
                saveError = "保存失败",
                saveRequested = false,
                saving = false,
            ),
        )
        assertTrue(
            recognitionRequiresDiscardConfirmation(
                speciesChanged = false,
                length = "",
                weight = "",
                location = "",
                story = "",
                saveError = null,
                saveRequested = false,
                saving = true,
            ),
        )
    }
}
