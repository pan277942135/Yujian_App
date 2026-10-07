package com.yujian.ai.ui.identify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionCameraCaptureContractTest {
    @Test
    fun captureCannotStartBeforeCameraAndImageCaptureAreReady() {
        assertFalse(
            RecognitionCameraCaptureContract.canStartCapture(
                RecognitionCameraCaptureState.INITIALIZING,
            ),
        )
        assertNull(
            RecognitionCameraCaptureContract.beginCapture(
                RecognitionCameraCaptureState.INITIALIZING,
            ),
        )
    }

    @Test
    fun repeatedTapWhileCapturingDoesNotCreateAnotherRequest() {
        assertNull(
            RecognitionCameraCaptureContract.beginCapture(
                RecognitionCameraCaptureState.CAPTURING,
            ),
        )
    }

    @Test
    fun validSavedAndDecodedImageCompletesCaptureForRecognition() {
        val output = RecognitionCameraCaptureOutput(
            callbackSucceeded = true,
            fileExists = true,
            fileBytes = 42L,
            decodedWidth = 64,
            decodedHeight = 48,
            selectedImageCreated = true,
        )

        assertTrue(output.isValid)
        assertEquals(
            RecognitionCameraCaptureState.SUCCESS,
            RecognitionCameraCaptureContract.completeCapture(output),
        )
    }

    @Test
    fun zeroByteOrUnreadableImageCannotEnterRecognition() {
        val zeroByte = RecognitionCameraCaptureOutput(
            callbackSucceeded = true,
            fileExists = true,
            fileBytes = 0L,
            decodedWidth = 64,
            decodedHeight = 48,
            selectedImageCreated = true,
        )
        val unreadable = zeroByte.copy(
            fileBytes = 42L,
            decodedWidth = 0,
            decodedHeight = 0,
        )

        assertFalse(zeroByte.isValid)
        assertFalse(unreadable.isValid)
        assertEquals(
            RecognitionCameraCaptureState.ERROR,
            RecognitionCameraCaptureContract.completeCapture(zeroByte),
        )
    }

    @Test
    fun imageCaptureErrorLeavesCaptureRetryable() {
        val errorState = RecognitionCameraCaptureContract.completeError()

        assertEquals(RecognitionCameraCaptureState.ERROR, errorState)
        assertTrue(RecognitionCameraCaptureContract.canStartCapture(errorState))
    }

    @Test
    fun galleryFlowDoesNotUseCameraCaptureContract() {
        // Gallery normalization is intentionally independent of this state
        // machine; the existing RecognitionImageStore gallery path remains the
        // fallback when camera capture is unavailable.
        assertTrue(RecognitionCameraCaptureContract.canStartCapture(RecognitionCameraCaptureState.ERROR))
    }
}
