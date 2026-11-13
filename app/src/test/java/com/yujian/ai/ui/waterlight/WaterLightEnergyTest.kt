package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.EnergyAnchor
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.*
import org.junit.Test

class WaterLightEnergyTest {
    private fun frame(
        stage: RecognitionSceneStage,
        progress: Float = .5f,
        session: String = "photo-A",
    ): WaterLightFrame {
        val localElapsedMs = (stage.minimumVisibleMs * progress).toLong()
        return WaterLightFrame(
            sessionId = session,
            photoSha256 = "photo-hash-A",
            elapsedMs = localElapsedMs,
            opticalReferenceElapsedMs = localElapsedMs,
            phase = WaterLightPhase.PROCESSING,
            completion = progress,
            reducedMotion = false,
            geometryVersion = 7L,
            attemptId = "attempt-A",
            imageId = session,
            photoContentVersion = 1L,
            sceneStageOverride = stage,
            sceneStageElapsedMsOverride = localElapsedMs,
            sceneStageProgressOverride = progress,
        )
    }

    @Test fun energyIsDrivenBySharedSemanticStageAndPhotoIdentity() {
        assertNull(WaterLightEnergy.at(frame(RecognitionSceneStage.ORB_WAKE, 1f), true))
        val releaseFrame = frame(RecognitionSceneStage.ORB_RELEASE, .5f)
        val release = WaterLightEnergy.at(releaseFrame, false)!!
        assertEquals(EnergyAnchor.ORB_RIBBON, release.source)
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, release.target)
        assertEquals(EnergyFeedback.RELEASE, release.feedback)
        assertEquals(.5f, release.level, .001f)
        assertEquals(release, WaterLightEnergy.at(releaseFrame, false))
        assertEquals("photo-hash-A", release.photoSha256)
        assertEquals(7L, release.geometryVersion)
        assertEquals("attempt-A", release.attemptId)
        assertEquals("photo-A", release.imageId)
        assertEquals(1L, release.photoContentVersion)
        assertEquals("orb-to-photo", release.pathId)

        val arrival = WaterLightEnergy.at(frame(RecognitionSceneStage.PERIMETER_ARRIVAL, 1f), false)!!
        assertEquals(EnergyFeedback.ARRIVE, arrival.feedback)
        assertEquals(1f, arrival.level, 0f)

        assertNull(WaterLightEnergy.at(frame(RecognitionSceneStage.PATH_TO_FISH), false))
        val fishPath = WaterLightEnergy.at(frame(RecognitionSceneStage.PATH_TO_FISH), true)!!
        assertEquals(EnergyAnchor.FISH_CONTOUR, fishPath.target)
        assertEquals(EnergyFeedback.ABSORB, fishPath.feedback)
        assertEquals(.125f, fishPath.level, .001f)
        assertNull(WaterLightEnergy.at(frame(RecognitionSceneStage.CONTOUR_A), false))
        assertEquals(EnergyFeedback.ABSORB, WaterLightEnergy.at(frame(RecognitionSceneStage.CONTOUR_B), true)?.feedback)

        assertNull(WaterLightEnergy.at(frame(RecognitionSceneStage.ORB_RETURN, .5f), true))
        assertNull(WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_RETURN, .5f), true, successfulRevealAllowed = true,
        ))
        val maskSha = "a".repeat(64)
        val contact = WaterLightPathPoint(.42f, .61f)
        val returnSegments = listOf(
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(640f, 480f), WaterLightPathPoint(500f, 410f), seed = 1,
            )!!,
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(500f, 410f), WaterLightPathPoint(320f, 300f), seed = 2,
            )!!,
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(320f, 300f), WaterLightPathPoint(120f, 220f), seed = 3,
            )!!,
        )
        val returned = WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_RETURN, .5f),
            trustedMask = true,
            successfulRevealAllowed = true,
            pathGeometry = returnSegments.first(),
            pathSegments = returnSegments,
            pathIdOverride = "processing-return-path",
            pathMaskSha256 = maskSha,
            pathMaskVersion = 42L,
            pathGeometryVersion = 7L,
            pathContactPointNormalizedSource = contact,
        )!!
        assertEquals(EnergyAnchor.FISH_CONTOUR, returned.source)
        assertEquals(EnergyAnchor.ORB_CORE, returned.target)
        assertEquals(EnergyFeedback.RETURN, returned.feedback)
        assertEquals(.5f, returned.level, 0f)
        assertEquals("processing-return-path", returned.pathId)
        assertEquals(maskSha, returned.pathMaskSha256)
        assertEquals(42L, returned.pathMaskVersion)
        assertEquals(7L, returned.pathGeometryVersion)
        assertEquals(contact, returned.pathContactPointNormalizedSource)

        val translate = WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_TO_NAME, .25f), true, successfulRevealAllowed = true,
        )!!
        assertEquals(EnergyFeedback.TRANSLATE, translate.feedback)
        assertEquals(.25f, translate.level, 0f)
        val reveal = WaterLightEnergy.at(
            frame(RecognitionSceneStage.SPECIES_NAME_REVEAL, .75f), true, successfulRevealAllowed = true,
        )!!
        assertEquals(EnergyFeedback.REVEAL, reveal.feedback)
        assertEquals(.75f, reveal.level, 0f)
        val settle = WaterLightEnergy.at(
            frame(RecognitionSceneStage.RESIDUE_SETTLE, 1f), true, successfulRevealAllowed = true,
        )!!
        assertEquals(EnergyFeedback.SETTLE, settle.feedback)
        assertEquals(1f, settle.level, 0f)
        assertNull(WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_TO_NAME), false, successfulRevealAllowed = true,
        ))

        assertEquals("photo-B", WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_RELEASE, session = "photo-B"), true,
        )?.sessionId)
    }

    @Test fun missingAttemptOrImageIdentityCannotEmitEnergy() {
        val valid = frame(RecognitionSceneStage.ORB_RELEASE)
        assertNull(WaterLightEnergy.at(valid.copy(attemptId = ""), false))
        assertNull(WaterLightEnergy.at(valid.copy(imageId = ""), false))
    }

    @Test fun orbOnlyBranchReturnsPerimeterEnergyAndKeepsRealGlyphEvents() {
        val perimeterPath = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(40f, 420f),
            WaterLightPathPoint(260f, 520f),
            seed = 17,
        )!!
        val returnFrame = frame(RecognitionSceneStage.ORB_RETURN, .5f)
        val returned = WaterLightEnergy.at(
            returnFrame,
            trustedMask = false,
            successfulRevealAllowed = true,
            pathGeometry = perimeterPath,
            orbOnlyRevealAllowed = true,
        )!!
        assertEquals(EnergyAnchor.PHOTO_PERIMETER, returned.source)
        assertEquals(EnergyAnchor.ORB_CORE, returned.target)
        assertEquals(EnergyFeedback.RETURN, returned.feedback)
        assertEquals("photo-perimeter-to-original-orb", returned.pathId)
        assertTrue(returned.matches(returnFrame))

        assertEquals(EnergyFeedback.TRANSLATE, WaterLightEnergy.at(
            frame(RecognitionSceneStage.ORB_TO_NAME), false, successfulRevealAllowed = true,
            orbOnlyRevealAllowed = true,
        )?.feedback)
        assertEquals(EnergyFeedback.REVEAL, WaterLightEnergy.at(
            frame(RecognitionSceneStage.SPECIES_NAME_REVEAL), false, successfulRevealAllowed = true,
            orbOnlyRevealAllowed = true,
        )?.feedback)
        assertEquals(EnergyFeedback.SETTLE, WaterLightEnergy.at(
            frame(RecognitionSceneStage.RESIDUE_SETTLE), false, successfulRevealAllowed = true,
            orbOnlyRevealAllowed = true,
        )?.feedback)
        assertNull(WaterLightEnergy.at(
            returnFrame, false, successfulRevealAllowed = true, pathGeometry = perimeterPath,
        ))
        assertNull(WaterLightEnergy.at(
            returnFrame, false, successfulRevealAllowed = false,
            pathGeometry = perimeterPath, orbOnlyRevealAllowed = true,
        ))
    }

    @Test fun energyPathCarriesMeasuredAnchorsAndRejectsInvalidGeometry() {
        val start = WaterLightPathPoint(112f, 568f)
        val end = WaterLightPathPoint(308f, 276f)
        val geometry = WaterLightPathGeometryFactory.between(start, end, seed = 42)!!
        val eventFrame = frame(RecognitionSceneStage.ORB_RELEASE)
        val event = WaterLightEnergy.at(eventFrame, trustedMask = false, pathGeometry = geometry)!!
        assertEquals(start, event.pathGeometry?.start)
        assertEquals(end, event.pathGeometry?.end)
        assertTrue(event.pathGeometry!!.isValid())
        assertEquals("photo-A", event.sessionId)
        assertEquals("attempt-A", event.attemptId)
        assertEquals("photo-hash-A", event.photoSha256)
        assertEquals(7L, event.geometryVersion)
        assertTrue(event.matches(eventFrame))
        assertFalse(event.matches(eventFrame.copy(attemptId = "attempt-old")))
        assertFalse(event.matches(eventFrame.copy(photoContentVersion = 2L)))
        assertFalse(event.matches(eventFrame.copy(geometryVersion = 8L)))

        assertNull(WaterLightPathGeometryFactory.between(start, WaterLightPathPoint(Float.NaN, 1f), seed = 42))
        val badSpace = geometry.copy(coordinateSpace = "RAW_BITMAP")
        assertFalse(badSpace.isValid())
        assertNull(WaterLightEnergy.at(eventFrame, false, pathGeometry = badSpace)?.pathGeometry)
        assertNull(WaterLightEnergy.at(eventFrame.copy(sessionId = ""), false))
    }
}
