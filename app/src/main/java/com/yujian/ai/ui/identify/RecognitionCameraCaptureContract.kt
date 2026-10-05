package com.yujian.ai.ui.identify

/** Runtime states for the real CameraX capture control. */
enum class RecognitionCameraCaptureState {
    INITIALIZING,
    READY,
    CAPTURING,
    SUCCESS,
    ERROR,
}

/**
 * Evidence required before a camera callback may enter recognition.
 *
 * A callback alone is not a successful capture: the file must be present,
 * non-empty, decodable, dimensionally valid, and converted to SelectedImage.
 */
data class RecognitionCameraCaptureOutput(
    val callbackSucceeded: Boolean,
    val fileExists: Boolean,
    val fileBytes: Long,
    val decodedWidth: Int,
    val decodedHeight: Int,
    val selectedImageCreated: Boolean,
) {
    val isValid: Boolean
        get() = callbackSucceeded &&
            fileExists &&
            fileBytes > 0L &&
            decodedWidth > 0 &&
            decodedHeight > 0 &&
            selectedImageCreated
}

object RecognitionCameraCaptureContract {
    fun canStartCapture(state: RecognitionCameraCaptureState): Boolean =
        state == RecognitionCameraCaptureState.READY

    fun beginCapture(state: RecognitionCameraCaptureState): RecognitionCameraCaptureState? =
        if (canStartCapture(state)) RecognitionCameraCaptureState.CAPTURING else null

    fun reconcileReadiness(
        state: RecognitionCameraCaptureState,
        cameraReady: Boolean,
    ): RecognitionCameraCaptureState = when {
        state == RecognitionCameraCaptureState.CAPTURING ||
            state == RecognitionCameraCaptureState.SUCCESS -> state
        cameraReady -> RecognitionCameraCaptureState.READY
        state == RecognitionCameraCaptureState.ERROR -> RecognitionCameraCaptureState.ERROR
        else -> RecognitionCameraCaptureState.INITIALIZING
    }

    fun shouldKeepPreviewMounted(
        captureState: RecognitionCameraCaptureState,
        cameraBound: Boolean,
        galleryLoading: Boolean,
        handoffImageAvailable: Boolean,
    ): Boolean =
        cameraBound &&
            !galleryLoading &&
            !handoffImageAvailable &&
            captureState != RecognitionCameraCaptureState.SUCCESS

    fun completeCapture(output: RecognitionCameraCaptureOutput): RecognitionCameraCaptureState =
        if (output.isValid) {
            RecognitionCameraCaptureState.SUCCESS
        } else {
            RecognitionCameraCaptureState.ERROR
        }

    fun completeError(): RecognitionCameraCaptureState = RecognitionCameraCaptureState.ERROR
}
