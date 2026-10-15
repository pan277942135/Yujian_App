package com.yujian.ai.ui.identify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFirstCaptureGuidePolicyTest {
    @Test
    fun guideAppearsOnlyAfterCameraPreviewIsReadyForTheFirstCameraEntry() {
        fun decision(permission: Boolean, preview: Boolean, galleryPending: Boolean, completed: Boolean) =
            shouldShowRecognitionFirstCaptureGuide(permission, preview, galleryPending, completed)

        assertFalse(decision(permission = false, preview = true, galleryPending = false, completed = false))
        assertFalse(decision(permission = true, preview = false, galleryPending = false, completed = false))
        assertFalse(decision(permission = true, preview = true, galleryPending = true, completed = false))
        assertFalse(decision(permission = true, preview = true, galleryPending = false, completed = true))
        assertTrue(decision(permission = true, preview = true, galleryPending = false, completed = false))
    }
}
