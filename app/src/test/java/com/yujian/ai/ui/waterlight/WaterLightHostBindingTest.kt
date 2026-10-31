package com.yujian.ai.ui.waterlight

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightHostBindingTest {
    @Test fun everyHostRequiresCurrentSessionAndValidOwnGeometry() {
        val engine = UnifiedWaterLightEngine().apply { activate("session", 4L) }
        val frame = WaterLightFrame("session", "photo", 950L, 950L,
            RecognitionPhase.OUTLINE, 0f, false, 4L)
        WaterLightHost.entries.forEach { host ->
            val binding = WaterLightHostBinding(host,
                WaterLightMaterialProfile.ORB_LEGACY.copy(host = host), engine)
            val rect = WaterLightHostGeometry(host, 4L, 20f, 30f, 180f, 190f)
            assertTrue(host.name, binding.accepts(frame, rect))
            assertFalse(binding.accepts(frame.copy(sessionId = "old"), rect))
            assertFalse(binding.accepts(frame, rect.copy(version = 3L)))
            assertFalse(binding.accepts(frame, rect.copy(rightPx = 19f)))
            assertFalse(binding.accepts(frame, rect.copy(host = WaterLightHost.entries.first { it != host })))
        }
        engine.release()
    }
}
