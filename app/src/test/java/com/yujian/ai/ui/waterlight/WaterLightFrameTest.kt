package com.yujian.ai.ui.waterlight

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightFrameTest {
    private fun frame(session: String, version: Long) = WaterLightFrame(
        sessionId = session, photoSha256 = "photo", elapsedMs = 1100,
        opticalReferenceElapsedMs = 950, phase = RecognitionPhase.OUTLINE,
        completion = .25f, reducedMotion = false, geometryVersion = version,
    )

    @Test fun existingClockAndStaleSessionAreIsolated() {
        val engine = UnifiedWaterLightEngine()
        engine.activate("one", 7)
        assertTrue(engine.accepts(frame("one", 7)))
        assertFalse(engine.accepts(frame("one", 8)))
        engine.activate("two", 8)
        assertFalse(engine.accepts(frame("one", 7)))
        assertTrue(engine.accepts(frame("two", 8)))
        assertEquals(1.1f, frame("two", 8).timeSeconds, .00001f)
        assertEquals(0f, frame("two", 8).copy(reducedMotion = true).timeSeconds, 0f)
        engine.release()
        assertFalse(engine.accepts(frame("two", 8)))
    }

    @Test fun orbDefaultsRemainTheExistingParameters() {
        val orb = WaterLightMaterialProfile.ORB_LEGACY
        assertEquals(0xFFD8BC87, orb.goldColor)
        assertEquals(0xFF5B8F78, orb.greenColor)
        assertEquals(5.5f, orb.goldPeriodSeconds, 0f)
        assertEquals(6.5f, orb.greenPeriodSeconds, 0f)
        assertEquals(.9f, orb.goldCoreWidthDp, 0f)
        assertEquals(1.6f, orb.greenCoreWidthDp, 0f)
    }
}
