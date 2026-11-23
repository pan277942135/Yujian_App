package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.RecognitionOrbOnlyReturnPathSnapshot
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRevealPhotoGeometryTest {
    private val handoff = RecognitionRevealHandoff(
        key = RecognitionRevealKey("photo-geometry-session", "photo-geometry-attempt"),
        imageId = "yellow-fish-image",
        photoSourceIdentity = "sha256=photo",
        normalizedSourceWidth = 640,
        normalizedSourceHeight = 427,
        sourceOrientationDegreesApplied = 0,
        trustedDetectorBBox = null,
        detectorConfidence = null,
        subjectMaskOrContour = emptyList(),
        subjectRoiInSource = null,
        subjectAnchorNormalizedSource = null,
        contourSourceSpace = "SOURCE_NORMALIZED",
        geometryTrustState = RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
        processingPhotoViewportBounds = RecognitionRevealWindowRect(10f, 20f, 330f, 500f),
        processingPhotoTransform = calculateRecognitionImageTransform(
            containerWidth = 320f,
            containerHeight = 480f,
            imageWidth = 640,
            imageHeight = 427,
        ),
        processingSubjectAnchor = null,
        resultIdentity = "yellow_catfish",
        eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY,
        handoffTimestampUptimeMs = 100L,
    )
    private val root = RecognitionRevealWindowRect(0f, 0f, 800f, 1400f)

    @Test
    fun yellowFishCropResolvesPhotoGeometryBeforeAndAfterHeroRasterCallback() {
        val heroBounds = RecognitionRevealWindowRect(100f, 600f, 422f, 810f)
        val crop = NormalizedSourceRect(0f, .00684f, 1f, .98433f)

        val beforeRaster = RecognitionRevealGeometryResolver.resolvePhotoDetailed(
            handoff,
            RecognitionRevealHeroSnapshot(heroBounds, crop, renderReady = false),
            root,
        )
        val afterRaster = RecognitionRevealGeometryResolver.resolvePhotoDetailed(
            handoff,
            RecognitionRevealHeroSnapshot(heroBounds, crop, renderReady = true),
            root,
        )

        assertNull(beforeRaster.failureReason)
        assertNull(afterRaster.failureReason)
        assertNotNull(beforeRaster.geometry)
        assertEquals(beforeRaster.geometry, afterRaster.geometry)
        assertEquals(beforeRaster.geometry, RecognitionRevealGeometryResolver.resolvePhoto(
            handoff,
            RecognitionRevealHeroSnapshot(heroBounds, crop, renderReady = false),
            root,
        ))
    }

    @Test
    fun fullFrameFitFallbackResolvesForPortraitAndLandscapeSources() {
        listOf(640 to 427, 427 to 640, 1200 to 800).forEach { (width, height) ->
            val transform = calculateRecognitionImageTransform(320f, 480f, width, height)
            val result = RecognitionRevealGeometryResolver.resolvePhotoDetailed(
                handoff.copy(
                    normalizedSourceWidth = width,
                    normalizedSourceHeight = height,
                    processingPhotoTransform = transform,
                ),
                RecognitionRevealHeroSnapshot(
                    RecognitionRevealWindowRect(120f, 580f, 440f, 1060f),
                    cropRect = null,
                    renderReady = false,
                ),
                root,
            )
            assertNull("${width}x$height FIT fallback: ${result.failureDetails}", result.failureReason)
            assertNotNull(result.geometry)
            assertTrue(requireNotNull(result.geometry).heroSourceTransform.scale.isFinite())
        }
    }

    @Test
    fun photoGeometryReportsTheConcretePermanentFailureReason() {
        val hero = RecognitionRevealHeroSnapshot(
            RecognitionRevealWindowRect(100f, 600f, 422f, 810f),
            NormalizedSourceRect(0f, .00684f, 1f, .98433f),
            renderReady = false,
        )
        val cases = listOf(
            handoff.copy(normalizedSourceWidth = 0) to hero to "SOURCE_DIMENSION_MISMATCH",
            handoff.copy(processingPhotoViewportBounds = RecognitionRevealWindowRect(1f, 1f, 1f, 1f)) to hero to "PROCESSING_BOUNDS_INVALID",
            handoff to hero.copy(boundsInWindow = RecognitionRevealWindowRect(1f, 1f, 1f, 1f)) to "HERO_BOUNDS_INVALID",
            handoff to hero.copy(cropRect = NormalizedSourceRect(-.01f, 0f, 1f, 1f)) to "CROP_OUT_OF_RANGE",
        )

        cases.forEach { (input, expectedReason) ->
            val (caseHandoff, caseHero) = input
            val result = RecognitionRevealGeometryResolver.resolvePhotoDetailed(caseHandoff, caseHero, root)
            assertEquals(expectedReason, result.failureReason)
            assertNotNull(result.failureDetails)
            assertNull(result.geometry)
        }
    }

    @Test
    fun invalidResolvedTransformIsReportedWithoutHidingTheFailure() {
        val hero = RecognitionRevealHeroSnapshot(
            RecognitionRevealWindowRect(-3.0e38f, 600f, 3.0e38f, 810f),
            cropRect = null,
            renderReady = false,
        )
        val result = RecognitionRevealGeometryResolver.resolvePhotoDetailed(handoff, hero, root)

        assertEquals("IMAGE_TRANSFORM_FAILED", result.failureReason)
        assertTrue(requireNotNull(result.failureDetails).isNotBlank())
        assertNull(result.geometry)
    }

    @Test
    fun orbOnlyProjectionConsumesTheLedgerRouteAndRejectsStaleIdentity() {
        val route = RecognitionOrbOnlyReturnPathSnapshot(
            sessionId = handoff.key.recognitionSessionId,
            attemptId = handoff.key.recognitionAttemptId,
            imageId = handoff.imageId,
            photoSha256 = "photo",
            photoContentVersion = 0L,
            pathId = "ledger:orb-only:route-1",
            geometryVersion = 23L,
            segment = geometry(12f, 24f, 112f, 224f),
            sourceCoordinateSpace = "RECOGNITION_VIEWPORT_PX",
            creationTimestampMonotonicMs = 300L,
            creationEventSequence = 17L,
        )
        val orbOnlyHandoff = handoff.copy(
            revealDecision = RecognitionRevealDecisionState.ORB_ONLY_READY,
            orbOnlyReturnPathSnapshot = route,
        )

        val projected = RecognitionRevealReturnPathProjector.projectOrbOnly(orbOnlyHandoff, route, root)

        assertNotNull(projected)
        assertEquals(DisplayPoint(route.segment.start.x + 10f, route.segment.start.y + 20f), projected?.start)
        assertEquals(DisplayPoint(route.segment.end.x + 10f, route.segment.end.y + 20f), projected?.end)
        assertNull(
            RecognitionRevealReturnPathProjector.projectOrbOnly(
                orbOnlyHandoff.copy(key = RecognitionRevealKey("old-session", "old-attempt")),
                route,
                root,
            ),
        )
        assertNull(
            RecognitionRevealReturnPathProjector.projectOrbOnly(
                orbOnlyHandoff.copy(fishPathSnapshot = fullPathSnapshot()),
                route,
                root,
            ),
        )
    }

    private fun fullPathSnapshot(): RecognitionFishPathSnapshot {
        val release = geometry(10f, 20f, 30f, 40f)
        val perimeter = geometry(30f, 40f, 70f, 80f)
        val fish = geometry(70f, 80f, 12f, 24f)
        return RecognitionFishPathSnapshot(
            sessionId = handoff.key.recognitionSessionId,
            attemptId = handoff.key.recognitionAttemptId,
            imageId = handoff.imageId,
            photoSha256 = "photo",
            photoContentVersion = 0L,
            maskSha256 = "a".repeat(64),
            maskVersion = 1L,
            geometryVersion = 2L,
            pStarNormalizedSource = WaterLightPathPoint(.2f, .3f),
            fishPathId = "ledger:fish-path",
            fishPathGeometry = fish,
            perimeterBranch = perimeter,
            orbReleasePath = release,
            returnPathId = "ledger:fish-path",
            returnPathSegments = listOf(
                geometry(12f, 24f, 70f, 80f),
                geometry(70f, 80f, 30f, 40f),
                geometry(30f, 40f, 10f, 20f),
            ),
            sourceCoordinateSpace = "RECOGNITION_VIEWPORT_PX",
            creationTimestampMonotonicMs = 100L,
            creationEventSequence = 1L,
        )
    }

    private fun geometry(sx: Float, sy: Float, ex: Float, ey: Float): WaterLightPathGeometry =
        requireNotNull(
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(sx, sy),
                WaterLightPathPoint(ex, ey),
                seed = 9,
                bendFraction = .1f,
            ),
        )
}
