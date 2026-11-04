package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightHostBindingTest {
    @Test fun everyHostRequiresCurrentSessionAndValidOwnGeometry() {
        val frame = WaterLightFrame("session", "photo", 950L, 950L,
            WaterLightPhase.PROCESSING, 0f, false, 4L)
        val engine = UnifiedWaterLightEngine().apply { activate(frame) }
        WaterLightHost.entries.forEach { host ->
            val binding = WaterLightHostBinding(host,
                WaterLightMaterialProfile.ORB_LEGACY.copy(host = host), engine)
            val rect = WaterLightHostGeometry(host, 4L, 20f, 30f, 180f, 190f)
            assertTrue(host.name, binding.accepts(frame, rect))
            assertFalse(binding.accepts(frame.copy(sessionId = "old"), rect))
            assertFalse(binding.accepts(frame, rect.copy(version = 3L)))
            assertFalse(binding.accepts(frame, rect.copy(rightPx = 19f)))
            assertFalse(binding.accepts(frame, rect.copy(host = WaterLightHost.entries.first { it != host })))
            assertEquals("GEOMETRY_HOST_MISMATCH(expected=$host actual=${WaterLightHost.entries.first { it != host }})",
                binding.rejectionReason(frame, rect.copy(host = WaterLightHost.entries.first { it != host })))
            assertEquals("FRAME_GEOMETRY_VERSION_MISMATCH(frame=4 geometry=3)",
                binding.rejectionReason(frame, rect.copy(version = 3L)))
            assertEquals("FRAME_IDENTITY_SESSION_ID_MISMATCH",
                binding.rejectionReason(frame.copy(sessionId = "old"), rect))
            assertEquals("FRAME_IDENTITY_ATTEMPT_ID_MISMATCH",
                binding.rejectionReason(frame.copy(attemptId = "old"), rect))
            assertEquals("FRAME_IDENTITY_IMAGE_ID_MISMATCH",
                binding.rejectionReason(frame.copy(imageId = "old"), rect))
            assertEquals("FRAME_IDENTITY_PHOTO_SHA256_MISMATCH",
                binding.rejectionReason(frame.copy(photoSha256 = "old"), rect))
            assertEquals("FRAME_IDENTITY_PHOTO_CONTENT_VERSION_MISMATCH",
                binding.rejectionReason(frame.copy(photoContentVersion = 1L), rect))
            val wrongMaterial = WaterLightHostBinding(host,
                WaterLightMaterialProfile.ORB_LEGACY.copy(host = WaterLightHost.entries.first { it != host }), engine)
            assertFalse(wrongMaterial.accepts(frame, rect))
            assertEquals("MATERIAL_HOST_MISMATCH(expected=$host actual=${WaterLightHost.entries.first { it != host }})",
                wrongMaterial.rejectionReason(frame, rect))
        }
        engine.release()
    }
}
