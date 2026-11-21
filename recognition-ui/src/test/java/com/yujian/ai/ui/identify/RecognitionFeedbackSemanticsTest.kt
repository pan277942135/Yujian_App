package com.yujian.ai.ui.identify

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionFeedbackSemanticsTest {
    @Test
    fun candidateConfirmationKeepsConfirmedFeedback() {
        assertEquals("confirmed", recognitionFeedbackType("grass_carp", "grass_carp"))
        assertEquals("corrected", recognitionFeedbackType("grass_carp", "crucian_carp"))
    }
}
