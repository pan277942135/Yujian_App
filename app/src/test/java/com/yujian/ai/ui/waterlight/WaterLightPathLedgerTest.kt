package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathLedger
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightPathLedgerTest {
    private fun frame(attempt: String = "attempt-1", geometryVersion: Long = 7L) = WaterLightFrame(
        sessionId = "session-1",
        photoSha256 = "photo-hash-1",
        elapsedMs = 1_800L,
        opticalReferenceElapsedMs = 1_800L,
        phase = WaterLightPhase.PROCESSING,
        completion = .5f,
        reducedMotion = false,
        geometryVersion = geometryVersion,
        attemptId = attempt,
        imageId = "image-1",
        photoContentVersion = 3L,
    )

    @Test fun returnRouteReversesFishThenPerimeterThenOriginalOrb() {
        val current = frame()
        val release = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(120f, 220f),
            WaterLightPathPoint(420f, 300f),
            seed = 13,
        )!!
        val fish = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(380f, 300f),
            WaterLightPathPoint(640f, 480f),
            seed = 19,
        )!!
        val ledger = WaterLightPathLedger()

        assertTrue(ledger.recordRelease(current, release))
        assertTrue(ledger.recordFishPath(current, fish, trustedMask = true))
        val route = ledger.returnRoute(current, trustedMask = true)

        assertNotNull(route)
        val segments = requireNotNull(route).segments
        assertEquals(3, segments.size)
        assertEquals(fish.end, segments[0].start)
        assertEquals(fish.control2, segments[0].control1)
        assertEquals(fish.control1, segments[0].control2)
        assertEquals(fish.start, segments[0].end)
        assertEquals(segments[0].end, segments[1].start)
        assertEquals(segments[1].end, segments[2].start)
        assertEquals(release.end, segments[2].start)
        assertEquals(release.control2, segments[2].control1)
        assertEquals(release.control1, segments[2].control2)
        assertEquals(release.start, segments[2].end)
        assertTrue(route.isValid())
        assertEquals(ledger.pathId(current), route.id)
    }

    @Test fun orbOnlyReturnUsesOnlyTheRecordedPerimeterToOrbSegment() {
        val current = frame()
        val release = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(120f, 220f),
            WaterLightPathPoint(420f, 300f),
            seed = 13,
        )!!
        val fish = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(380f, 300f),
            WaterLightPathPoint(640f, 480f),
            seed = 19,
        )!!
        val ledger = WaterLightPathLedger()
        ledger.recordRelease(current, release)
        ledger.recordFishPath(current, fish, trustedMask = true)

        val route = ledger.returnRoute(current, trustedMask = false)!!
        assertEquals(1, route.segments.size)
        assertEquals(release.end, route.segments.single().start)
        assertEquals(release.start, route.segments.single().end)
    }

    @Test fun identityChangeInvalidatesEveryStoredSegment() {
        val current = frame()
        val release = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(120f, 220f),
            WaterLightPathPoint(420f, 300f),
            seed = 13,
        )!!
        val ledger = WaterLightPathLedger()
        assertTrue(ledger.recordRelease(current, release))
        assertNotNull(ledger.returnRoute(current, trustedMask = false))
        assertNull(ledger.returnRoute(frame(attempt = "attempt-2"), trustedMask = false))
        assertNull(ledger.returnRoute(frame(geometryVersion = 8L), trustedMask = false))
    }

    @Test fun untrustedMaskCannotCreateFishPath() {
        val current = frame()
        val release = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(120f, 220f),
            WaterLightPathPoint(420f, 300f),
            seed = 13,
        )!!
        val fish = WaterLightPathGeometryFactory.between(
            WaterLightPathPoint(380f, 300f),
            WaterLightPathPoint(640f, 480f),
            seed = 19,
        )!!
        val ledger = WaterLightPathLedger()
        ledger.recordRelease(current, release)
        assertTrue(!ledger.recordFishPath(current, fish, trustedMask = false))
        val route = ledger.returnRoute(current, trustedMask = false)!!
        assertEquals(1, route.segments.size)
    }
}
