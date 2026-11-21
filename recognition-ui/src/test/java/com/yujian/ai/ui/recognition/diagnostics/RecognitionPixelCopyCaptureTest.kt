package com.yujian.ai.ui.recognition.diagnostics

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionPixelCopyCaptureTest {
    private val identity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", "photo-sha", 1L)

    @Test fun screenshotIsAcceptedOnlyWhenStageSessionAttemptImageAndGeometryMatch() {
        assertTrue(RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(identity, identity, 8L, 8L))
        assertFalse(RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(identity, null, 8L, 8L))
        assertFalse(
            RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(
                identity,
                identity.copy(attemptId = "attempt-2"),
                8L,
                8L,
            ),
        )
        assertFalse(RecognitionEvidenceCapturePolicy.sameSceneIdentityAndGeometry(identity, identity, 8L, 9L))
    }

    @Test fun pixelCopyFailuresAndIdentityChangesRemainExplicit() {
        assertEquals(
            "SUCCESS",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus("SUCCESS", "ORB_RELEASE", "ORB_RELEASE", true),
        )
        assertEquals(
            "STAGE_IDENTITY_UNVERIFIED",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus("SUCCESS", "ORB_RELEASE", "CONTOUR_A", true),
        )
        assertEquals(
            "STAGE_IDENTITY_UNVERIFIED",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus("SUCCESS", "ORB_RELEASE", "ORB_RELEASE", false),
        )
        assertEquals(
            "PIXELCOPY_ERROR",
            RecognitionEvidenceCapturePolicy.pixelCopyStatus("PIXELCOPY_ERROR", "ORB_RELEASE", "ORB_RELEASE", true),
        )
    }
}
