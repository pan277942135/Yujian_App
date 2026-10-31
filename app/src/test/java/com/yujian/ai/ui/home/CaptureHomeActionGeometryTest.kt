package com.yujian.ai.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHomeActionGeometryTest {
    @Test fun enlargedCompactActionDoesNotCollideWithAlbumOrScaledPrompt() {
        for (scale in listOf(1f, 1.3f, 1.5f)) {
            val result = resolveCaptureHomeActionY(369f, 88f, 408f, 305f, 24f * scale)
            assertTrue(result.cameraTop + 88f + 8f <= 408f)
            assertTrue(result.promptTop + 24f * scale + 8f <= result.cameraTop)
            assertTrue(result.promptTop >= 0f)
        }
    }
    @Test fun roomyFrozenAnchorsRemainUnchanged() {
        val result = resolveCaptureHomeActionY(720f, 96f, 800f, 630f, 24f)
        assertEquals(672f, result.cameraTop, 0f)
        assertEquals(630f, result.promptTop, 0f)
    }
}
