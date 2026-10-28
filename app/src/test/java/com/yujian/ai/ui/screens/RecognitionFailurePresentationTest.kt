package com.yujian.ai.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionFailurePresentationTest {
    @Test
    fun internalRuntimeMessageIsNeverRendered() {
        val internal = "fish_detector_yolox_nano_v0_1.onnx: java.io.IOException: stack trace"
        val productMessage = recognitionFailureMessage(IllegalStateException(internal))

        assertEquals(GENERIC_RECOGNITION_FAILURE_MESSAGE, productMessage)
        assertFalse(productMessage.contains(".onnx"))
        assertFalse(productMessage.contains(".tflite"))
        assertFalse(productMessage.contains(internal))
    }
}
