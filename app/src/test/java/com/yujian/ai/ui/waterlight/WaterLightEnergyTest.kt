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
        attemptId = "attempt-A", imageId = session, photoContentVersion = 1L,
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
        assertEquals("attempt-A", release.attemptId)
        assertEquals("photo-A", release.imageId)
        assertEquals(1L, release.photoContentVersion)
        assertEquals("orb-to-photo", release.pathId)
        assertEquals(EnergyFeedback.ARRIVE, WaterLightEnergy.at(frame(1000), false)?.feedback)
        assertNull(WaterLightEnergy.at(frame(1_100L), false))
        assertNull(WaterLightEnergy.at(frame(1550), false))
        assertNull(WaterLightEnergy.at(frame(1550), true))
        assertEquals(EnergyAnchor.FISH_CONTOUR, WaterLightEnergy.at(frame(1549), true)?.target)
        assertEquals(EnergyFeedback.RETURN, WaterLightEnergy.at(frame(2800), true)?.feedback)
        assertEquals(EnergyAnchor.ORB_CORE, WaterLightEnergy.at(frame(3000), true)?.target)
        assertNull(WaterLightEnergy.at(frame(3180), true))
        assertNull(WaterLightEnergy.at(frame(3350), true))
        assertNull(WaterLightEnergy.at(frame(3650), true))
        assertEquals(EnergyFeedback.TRANSLATE, WaterLightEnergy.at(frame(3180), true, realResultReady = true)?.feedback)
        assertEquals(EnergyFeedback.REVEAL, WaterLightEnergy.at(frame(3350), true, realResultReady = true)?.feedback)
        assertEquals(EnergyFeedback.SETTLE, WaterLightEnergy.at(frame(3650), true, realResultReady = true)?.feedback)
        assertNull(WaterLightEnergy.at(frame(3651), true))
        assertNull(WaterLightEnergy.at(frame(3000), false))
        assertEquals("photo-B", WaterLightEnergy.at(frame(650, "photo-B"), true)?.sessionId)
    }

    @Test fun missingAttemptOrImageIdentityCannotEmitEnergy() {
        val missingAttempt = frame(650).copy(attemptId = "")
        val missingImage = frame(650).copy(imageId = "")
        assertNull(WaterLightEnergy.at(missingAttempt, false))
        assertNull(WaterLightEnergy.at(missingImage, false))
    }
}
