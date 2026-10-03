package com.yujian.ai.ui.recognition

import com.yujian.ai.ai.RecognitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAmbientFieldTest {
    @Test
    fun locatedAndClassifyingFieldStrengthsRecedeBelowOpeningField() {
        val opening = recognitionAmbientStateCalibration(RecognitionPhase.CAPTURED)
        val located = recognitionAmbientStateCalibration(RecognitionPhase.OUTLINE)
        val classifying = recognitionAmbientStateCalibration(RecognitionPhase.CLASSIFYING)

        assertTrue(located.strength < opening.strength)
        assertTrue(classifying.strength < located.strength)
        assertEquals(.58f, located.strength, .001f)
        assertEquals(.36f, classifying.strength, .001f)
        assertEquals(.62f, located.speed, .001f)
        assertEquals(.42f, classifying.speed, .001f)
    }
}
