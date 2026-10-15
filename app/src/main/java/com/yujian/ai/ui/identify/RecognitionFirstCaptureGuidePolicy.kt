package com.yujian.ai.ui.identify

internal fun shouldShowRecognitionFirstCaptureGuide(
    cameraPermissionGranted: Boolean,
    previewReady: Boolean,
    galleryPickerPending: Boolean,
    guideCompleted: Boolean,
): Boolean = cameraPermissionGranted && previewReady && !galleryPickerPending && !guideCompleted
