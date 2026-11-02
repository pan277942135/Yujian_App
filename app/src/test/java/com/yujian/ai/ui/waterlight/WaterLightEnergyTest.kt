package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.*
import org.junit.Test

class WaterLightEnergyTest {
    private fun frame(ms: Long, session: String = "photo-A") = WaterLightFrame(
        session, "photo-hash-A", ms, ms, WaterLightPhase.PROCESSING, .5f, false, 7L,
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
        val detectorPathStage = WaterLightEnergy.at(frame(1_100L), false)!!
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, detectorPathStage.source)
        assertEquals(EnergyAnchor.PHOTO_PATH, detectorPathStage.target)
        assertEquals(EnergyFeedback.ARRIVE, detectorPathStage.feedback)
        assertEquals(0.2f, detectorPathStage.level, .001f)
        assertNull(WaterLightEnergy.at(frame(1550), false))
        assertNull(WaterLightEnergy.at(frame(1550), true))
        assertEquals(EnergyAnchor.FISH_CONTOUR, WaterLightEnergy.at(frame(1549), true)?.target)
        assertNull(WaterLightEnergy.at(frame(2800), true)) // A slow real classifier holds at P7; no early return event.
        assertNull(WaterLightEnergy.at(frame(3000), true))
        assertEquals(EnergyFeedback.RETURN, WaterLightEnergy.at(frame(2800), true, successfulRevealAllowed = true)?.feedback)
        assertEquals(EnergyAnchor.ORB_CORE, WaterLightEnergy.at(frame(3000), true, successfulRevealAllowed = true)?.target)
        assertNull(WaterLightEnergy.at(frame(3180), true))
        assertNull(WaterLightEnergy.at(frame(3350), true))
        assertNull(WaterLightEnergy.at(frame(3650), true))
        assertEquals(EnergyFeedback.TRANSLATE, WaterLightEnergy.at(frame(3180), true, successfulRevealAllowed = true)?.feedback)
        assertEquals(EnergyFeedback.REVEAL, WaterLightEnergy.at(frame(3350), true, successfulRevealAllowed = true)?.feedback)
        assertEquals(EnergyFeedback.SETTLE, WaterLightEnergy.at(frame(3650), true, successfulRevealAllowed = true)?.feedback)
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

    @Test fun orbOnlyBranchReturnsPerimeterEnergyAndKeepsRealGlyphEvents() {
        val perimeterPath = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(40f, 420f),
            WaterLightPathPoint(260f, 520f),
            seed = 17,
        )!!
        val returned = WaterLightEnergy.at(
            frame(2_900L),
            trustedMask = false,
            successfulRevealAllowed = true,
            pathGeometry = perimeterPath,
            orbOnlyRevealAllowed = true,
        )!!
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, returned.source)
        assertEquals(EnergyAnchor.ORB_CORE, returned.target)
        assertEquals(EnergyFeedback.RETURN, returned.feedback)
        assertEquals("photo-perimeter-to-original-orb", returned.pathId)
        assertTrue(returned.matches(frame(2_900L)))

        assertEquals(
            EnergyFeedback.TRANSLATE,
            WaterLightEnergy.at(frame(3_180L), false, successfulRevealAllowed = true,
                orbOnlyRevealAllowed = true)?.feedback,
        )
        assertEquals(
            EnergyFeedback.REVEAL,
            WaterLightEnergy.at(frame(3_350L), false, successfulRevealAllowed = true,
                orbOnlyRevealAllowed = true)?.feedback,
        )
        assertEquals(
            EnergyFeedback.SETTLE,
            WaterLightEnergy.at(frame(3_650L), false, successfulRevealAllowed = true,
                orbOnlyRevealAllowed = true)?.feedback,
        )
        assertNull(WaterLightEnergy.at(frame(2_900L), false, successfulRevealAllowed = true,
            pathGeometry = perimeterPath))
        assertNull(WaterLightEnergy.at(frame(2_900L), false, successfulRevealAllowed = false,
            pathGeometry = perimeterPath, orbOnlyRevealAllowed = true))
    }

    @Test fun energyPathCarriesMeasuredAnchorsAndRejectsInvalidGeometry() {
        val start = WaterLightPathPoint(112f, 568f)
        val end = WaterLightPathPoint(308f, 276f)
        val geometry = WaterLightPathGeometryFactory.between(start, end, seed = 42)!!
        val event = WaterLightEnergy.at(frame(650), trustedMask = false, pathGeometry = geometry)!!
        assertEquals(start, event.pathGeometry?.start)
        assertEquals(end, event.pathGeometry?.end)
        assertTrue(event.pathGeometry!!.isValid())
        assertEquals("photo-A", event.sessionId)
        assertEquals("attempt-A", event.attemptId)
        assertEquals("photo-hash-A", event.photoSha256)
        assertEquals(7L, event.geometryVersion)
        assertTrue(event.matches(frame(650)))
        assertFalse(event.matches(frame(650).copy(attemptId = "attempt-old")))
        assertFalse(event.matches(frame(650).copy(photoContentVersion = 2L)))
        assertFalse(event.matches(frame(650).copy(geometryVersion = 8L)))

        assertNull(WaterLightPathGeometryFactory.between(start, WaterLightPathPoint(Float.NaN, 1f), seed = 42))
        val badSpace = geometry.copy(coordinateSpace = "RAW_BITMAP")
        assertFalse(badSpace.isValid())
        assertNull(WaterLightEnergy.at(frame(650), false, pathGeometry = badSpace)?.pathGeometry)
        assertNull(WaterLightEnergy.at(frame(650).copy(sessionId = ""), false))
    }

    @Test fun pathToFishCanCarryARealDetectorTargetBeforeMaskTrust() {
        val start = WaterLightPathPoint(84f, 420f)
        val detectorBoxCenter = WaterLightPathPoint(298f, 266f)
        val geometry = WaterLightPathGeometryFactory.between(start, detectorBoxCenter, seed = 314)!!
        val pathFrame = frame(1_137L)
        val event = WaterLightEnergy.at(pathFrame, trustedMask = false, pathGeometry = geometry)!!

        assertEquals(EnergyFeedback.ARRIVE, event.feedback)
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, event.source)
        assertEquals(EnergyAnchor.PHOTO_PATH, event.target)
        assertEquals(start, event.pathGeometry?.start)
        assertEquals(detectorBoxCenter, event.pathGeometry?.end)
        assertTrue(event.matches(pathFrame))
    }
}
