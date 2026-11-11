package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathLedger
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightPathLedgerTest {
    private val maskShaA = "a".repeat(64)
    private val maskShaB = "b".repeat(64)
    private val contactA = WaterLightPathPoint(.42f, .61f)
    private val contactB = WaterLightPathPoint(.44f, .63f)

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

    private fun release() = WaterLightPathGeometryFactory.between(
        WaterLightPathPoint(120f, 220f),
        WaterLightPathPoint(420f, 300f),
        seed = 13,
    )!!

    private fun fish(seed: Int = 19) = WaterLightPathGeometryFactory.between(
        WaterLightPathPoint(380f, 300f),
        WaterLightPathPoint(640f, 480f),
        seed = seed,
    )!!

    private fun recordFish(
        ledger: WaterLightPathLedger,
        frame: WaterLightFrame,
        geometry: com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry = fish(),
        maskSha: String = maskShaA,
        maskVersion: Long = 42L,
        contact: WaterLightPathPoint = contactA,
        trusted: Boolean = true,
    ) = ledger.recordFishPath(
        frame = frame,
        geometry = geometry,
        trustedMask = trusted,
        maskSha256 = maskSha,
        maskVersion = maskVersion,
        contactPointNormalizedSource = contact,
    )

    @Test fun returnRouteReversesFishThenPerimeterThenOriginalOrb() {
        val current = frame()
        val release = release()
        val fish = fish()
        val ledger = WaterLightPathLedger()

        assertTrue(ledger.recordRelease(current, release))
        assertTrue(recordFish(ledger, current, fish))
        assertEquals(fish, ledger.fishPath(current, maskShaA, 42L, contactA))
        val route = ledger.returnRoute(current, trustedMask = true, maskShaA, 42L, contactA)

        assertNotNull(route)
        val actual = requireNotNull(route)
        val segments = actual.segments
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
        assertTrue(actual.isValid())
        assertEquals(maskShaA, actual.maskSha256)
        assertEquals(42L, actual.maskVersion)
        assertEquals(contactA, actual.contactPointNormalizedSource)
        assertEquals(ledger.pathId(current, maskShaA, 42L, contactA), actual.id)
    }

    @Test fun changedMaskShaOrPStarCannotReusePreviousFishPathOrReturnRoute() {
        val current = frame()
        val ledger = WaterLightPathLedger()
        assertTrue(ledger.recordRelease(current, release()))
        assertTrue(recordFish(ledger, current, fish(seed = 19)))
        val firstPathId = ledger.pathId(current, maskShaA, 42L, contactA)
        assertNotNull(firstPathId)

        assertNull(ledger.fishPath(current, maskShaB, 42L, contactA))
        assertNull(ledger.returnRoute(current, trustedMask = true, maskShaB, 42L, contactA))
        assertNull(ledger.returnRoute(current, trustedMask = true, maskShaA, 42L, contactB))

        val nextFish = fish(seed = 23)
        assertTrue(recordFish(ledger, current, nextFish, maskShaB, 43L, contactB))
        val nextPathId = ledger.pathId(current, maskShaB, 43L, contactB)
        assertNotNull(nextPathId)
        assertTrue(firstPathId != nextPathId)
        val route = requireNotNull(ledger.returnRoute(current, true, maskShaB, 43L, contactB))
        assertEquals(nextFish.end, route.segments.first().start)
        assertEquals(maskShaB, route.maskSha256)
        assertEquals(43L, route.maskVersion)
        assertEquals(contactB, route.contactPointNormalizedSource)
    }

    @Test fun orbOnlyReturnUsesOnlyTheRecordedPerimeterToOrbSegment() {
        val current = frame()
        val release = release()
        val ledger = WaterLightPathLedger()
        ledger.recordRelease(current, release)
        recordFish(ledger, current, fish())

        val route = ledger.returnRoute(current, trustedMask = false)!!
        assertEquals(1, route.segments.size)
        assertEquals(release.end, route.segments.single().start)
        assertEquals(release.start, route.segments.single().end)
        assertNull(route.maskSha256)
    }

    @Test fun identityChangeInvalidatesEveryStoredSegment() {
        val current = frame()
        val ledger = WaterLightPathLedger()
        assertTrue(ledger.recordRelease(current, release()))
        assertNotNull(ledger.returnRoute(current, trustedMask = false))
        assertNull(ledger.returnRoute(frame(attempt = "attempt-2"), trustedMask = false))
        assertNull(ledger.returnRoute(frame(geometryVersion = 8L), trustedMask = false))
    }

    @Test fun untrustedMaskCannotCreateFishPath() {
        val current = frame()
        val ledger = WaterLightPathLedger()
        ledger.recordRelease(current, release())
        assertTrue(!recordFish(ledger, current, fish(), trusted = false))
        val route = ledger.returnRoute(current, trustedMask = false)!!
        assertEquals(1, route.segments.size)
    }

    @Test fun signedGeometryFingerprintIsAcceptedButZeroRemainsTheUnmeasuredSentinel() {
        val signedFrame = frame(geometryVersion = -7L)
        val ledger = WaterLightPathLedger()
        assertTrue(ledger.recordRelease(signedFrame, release()))
        assertTrue(recordFish(ledger, signedFrame, fish()))
        val route = ledger.returnRoute(signedFrame, true, maskShaA, 42L, contactA)
        assertNotNull(route)
        assertEquals(-7L, requireNotNull(route).geometryVersion)
        assertTrue(requireNotNull(route).isValid())

        val zeroGeometryLedger = WaterLightPathLedger()
        assertFalse(zeroGeometryLedger.recordRelease(frame(geometryVersion = 0L), release()))
    }

}
