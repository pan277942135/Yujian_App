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
        state == RecognitionCameraCaptureState.READY ||
            state == RecognitionCameraCaptureState.ERROR

    fun beginCapture(state: RecognitionCameraCaptureState): RecognitionCameraCaptureState? =
        if (canStartCapture(state)) RecognitionCameraCaptureState.CAPTURING else null

    fun completeCapture(output: RecognitionCameraCaptureOutput): RecognitionCameraCaptureState =
        if (output.isValid) {
            RecognitionCameraCaptureState.SUCCESS
        } else {
            RecognitionCameraCaptureState.ERROR
        }

    fun completeError(): RecognitionCameraCaptureState = RecognitionCameraCaptureState.ERROR
}


/**
 * Independent viewport anchors for live Recognition camera controls.
 * The gallery width affects only its end-anchored center; it never moves the shutter.
 */
data class RecognitionCameraControlsPlacement(
    val shutterCenterXDp: Float,
    val galleryCenterXDp: Float,
    val galleryEndInsetDp: Float,
    val bottomInsetDp: Float,
)

object RecognitionCameraControlsGeometry {
    fun resolve(
        viewportWidthDp: Float,
        galleryWidthDp: Float,
        rightSafeInsetDp: Float,
        bottomSafeInsetDp: Float,
        galleryEdgeSpacingDp: Float = 16f,
        bottomSpacingDp: Float = 18f,
    ): RecognitionCameraControlsPlacement {
        require(viewportWidthDp > 0f)
        require(galleryWidthDp >= 0f)
        require(rightSafeInsetDp >= 0f)
        require(bottomSafeInsetDp >= 0f)
        require(galleryEdgeSpacingDp >= 0f)
        require(bottomSpacingDp >= 0f)
        val galleryEndInset = rightSafeInsetDp + galleryEdgeSpacingDp
        return RecognitionCameraControlsPlacement(
            shutterCenterXDp = viewportWidthDp / 2f,
            galleryCenterXDp = viewportWidthDp - galleryEndInset - galleryWidthDp / 2f,
            galleryEndInsetDp = galleryEndInset,
            bottomInsetDp = bottomSafeInsetDp + bottomSpacingDp,
        )
    }
}
