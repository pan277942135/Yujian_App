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
    fun recoverableErrorReturnsToReadyOnlyWhenCameraIsUsable() {
        val errorState = RecognitionCameraCaptureContract.completeError()

        assertEquals(RecognitionCameraCaptureState.ERROR, errorState)
        assertFalse(RecognitionCameraCaptureContract.canStartCapture(errorState))
        assertEquals(
            RecognitionCameraCaptureState.ERROR,
            RecognitionCameraCaptureContract.reconcileReadiness(
                state = errorState,
                cameraReady = false,
            ),
        )
        assertEquals(
            RecognitionCameraCaptureState.READY,
            RecognitionCameraCaptureContract.reconcileReadiness(
                state = errorState,
                cameraReady = true,
            ),
        )
        assertTrue(
            RecognitionCameraCaptureContract.canStartCapture(
                RecognitionCameraCaptureContract.reconcileReadiness(
                    state = errorState,
                    cameraReady = true,
                ),
            ),
        )
    }

    @Test
    fun previewRemainsMountedThroughCaptureAndLeavesOnlyAtHandoff() {
        assertTrue(
            RecognitionCameraCaptureContract.shouldKeepPreviewMounted(
                captureState = RecognitionCameraCaptureState.CAPTURING,
                cameraBound = true,
                galleryLoading = false,
                handoffImageAvailable = false,
            ),
        )
        assertFalse(
            RecognitionCameraCaptureContract.shouldKeepPreviewMounted(
                captureState = RecognitionCameraCaptureState.SUCCESS,
                cameraBound = true,
                galleryLoading = false,
                handoffImageAvailable = true,
            ),
        )
        assertFalse(
            RecognitionCameraCaptureContract.canStartCapture(
                RecognitionCameraCaptureState.CAPTURING,
            ),
        )
    }

    @Test
    fun galleryLoadingIsIndependentFromCameraCaptureReadiness() {
        val cameraState = RecognitionCameraCaptureState.READY

        assertEquals(
            RecognitionCameraCaptureState.READY,
            RecognitionCameraCaptureContract.reconcileReadiness(
                state = cameraState,
                cameraReady = true,
            ),
        )
        assertFalse(
            RecognitionCameraCaptureContract.shouldKeepPreviewMounted(
                captureState = cameraState,
                cameraBound = true,
                galleryLoading = true,
                handoffImageAvailable = false,
            ),
        )
    }
}
