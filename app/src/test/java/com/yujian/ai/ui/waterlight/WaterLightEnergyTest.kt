package com.yujian.ai.ui.waterlight

import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import org.junit.Assert.*
import org.junit.Test

class WaterLightEnergyTest {
    private fun frame(ms: Long, session: String = "photo-A") = WaterLightFrame(
        session, "photo-hash-A", ms, ms, RecognitionPhase.OUTLINE, .5f, false, 7L,
    )

    @Test fun stageAndPhotoIdentityAreDeterministic() {
        assertNull(WaterLightEnergy.at(frame(349), true))
        val release = WaterLightEnergy.at(frame(650), false)!!
        assertEquals(EnergyAnchor.ORB_RIBBON, release.source)
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, release.target)
        assertEquals(EnergyFeedback.RELEASE, release.feedback)
        assertEquals(.5f, release.level, .001f)
        assertEquals(release, WaterLightEnergy.at(frame(650), false))
        assertEquals("photo-hash-A", release.photoSha256)
        assertEquals(7L, release.geometryVersion)
        assertEquals(EnergyFeedback.ARRIVE, WaterLightEnergy.at(frame(1000), false)?.feedback)
        assertNull(WaterLightEnergy.at(frame(1550), false))
        assertEquals(EnergyAnchor.FISH_CONTOUR, WaterLightEnergy.at(frame(1550), true)?.target)
        assertNull(WaterLightEnergy.at(frame(2800), true))
        assertEquals("photo-B", WaterLightEnergy.at(frame(650, "photo-B"), true)?.sessionId)
    }
}
