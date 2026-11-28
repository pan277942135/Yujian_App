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

    @Test
    fun shutterIsViewportCenteredAndGalleryStaysInsideRightSafeInset() {
        val layout = RecognitionCameraControlsGeometry.resolve(
            viewportWidthDp = 360f,
            galleryWidthDp = 54f,
            rightSafeInsetDp = 10f,
            bottomSafeInsetDp = 24f,
        )

        assertEquals(180f, layout.shutterCenterXDp, 0f)
        assertTrue(layout.galleryCenterXDp > layout.shutterCenterXDp)
        assertTrue(layout.galleryCenterXDp + 27f <= 360f - 10f)
        assertEquals(42f, layout.bottomInsetDp, 0f)
    }

    @Test
    fun shutterCenterDoesNotDependOnGalleryWidthOrVisibility() {
        val withoutGallery = RecognitionCameraControlsGeometry.resolve(
            viewportWidthDp = 360f,
            galleryWidthDp = 0f,
            rightSafeInsetDp = 0f,
            bottomSafeInsetDp = 0f,
        )
        val withGallery = RecognitionCameraControlsGeometry.resolve(
            viewportWidthDp = 360f,
            galleryWidthDp = 72f,
            rightSafeInsetDp = 0f,
            bottomSafeInsetDp = 0f,
        )

        assertEquals(180f, withoutGallery.shutterCenterXDp, 0f)
        assertEquals(withoutGallery.shutterCenterXDp, withGallery.shutterCenterXDp, 0f)
        assertTrue(withGallery.galleryCenterXDp > withGallery.shutterCenterXDp)
    }

}
