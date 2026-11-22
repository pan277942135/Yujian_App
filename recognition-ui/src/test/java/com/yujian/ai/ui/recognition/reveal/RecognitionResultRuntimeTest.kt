package com.yujian.ai.ui.recognition.reveal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCompletionContract
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import org.junit.Test

class RecognitionResultRuntimeTest {
    @Test
    fun fiveResultMilestonesHaveStableContractIdsAndStrictOrder() {
        val sequence = ResultHandoffSubstage.entries
        assertEquals(listOf("13.1", "13.2", "13.3", "13.4", "13.5"), sequence.map { it.contractId })

        var completed = emptySet<ResultHandoffSubstage>()
        sequence.forEach { stage ->
            assertTrue("${stage.contractId} must be the only next event", RecognitionResultHandoffContract.isNext(stage, completed))
            sequence.getOrNull(stage.ordinal + 1)?.let { skipped ->
                assertFalse("a skipped milestone must be rejected", RecognitionResultHandoffContract.isNext(skipped, completed))
            }
            completed = completed + stage
            assertFalse("a completed milestone cannot replay", RecognitionResultHandoffContract.isNext(stage, completed))
        }
        assertTrue(RecognitionResultHandoffContract.isComplete(completed))
        assertFalse(RecognitionResultHandoffContract.isNext(sequence.last(), completed))
    }

    @Test
    fun malformedCompletedSetCannotSkipAnEarlierSubstage() {
        val completed = setOf(ResultHandoffSubstage.NAME_STABLE, ResultHandoffSubstage.RESULT_INFO_ARRIVAL)
        assertFalse(RecognitionResultHandoffContract.isNext(ResultHandoffSubstage.RESULT_SCENE_STABLE, completed))
        assertFalse(RecognitionResultHandoffContract.isComplete(completed))
    }

    @Test
    fun opticalResidueIsNotFinalCompletionWithoutAllFiveResultEventsAndStableScene() {
        val events = ResultHandoffSubstage.entries.toSet()
        assertFalse(RecognitionSceneCompletionContract.isComplete(true, events - ResultHandoffSubstage.RESULT_SCENE_STABLE, true))
        assertFalse(RecognitionSceneCompletionContract.isComplete(true, events, false))
        assertFalse(RecognitionSceneCompletionContract.isComplete(false, events, true))
        assertTrue(RecognitionSceneCompletionContract.isComplete(true, events, true))
    }

    @Test
    fun handoffSequenceAndMonotonicClockRejectDuplicateSkippedOrNonIncreasingEvents() {
        assertTrue(RecognitionResultHandoffContract.isNextEventSequence(requested = 1L, lastAccepted = 0L))
        assertFalse(RecognitionResultHandoffContract.isNextEventSequence(requested = 2L, lastAccepted = 0L))
        assertFalse(RecognitionResultHandoffContract.isNextEventSequence(requested = 1L, lastAccepted = 1L))
        assertTrue(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 101L, lastAccepted = 100L))
        assertFalse(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 100L, lastAccepted = 100L))
        assertFalse(RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(requested = 99L, lastAccepted = 100L))
    }
    @Test
    fun glyphReadinessRequiresClassifierPathAndCompletedCanvasDrawForSameAttempt() {
        val identity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", "photo-sha", 7L)
        val speciesKey = "yellow_catfish"
        val speciesName = "黄颡鱼"
        val descriptor = RecognitionGlyphPathDescriptor(
            identity = identity,
            speciesKey = speciesKey,
            speciesName = speciesName,
            pathId = "glyph:path-1",
            geometryVersion = 19L,
            expectedCharacterCount = 3,
            builtCharacterCount = 3,
            contourCount = 18,
            totalPathLengthPx = 421f,
            bounds = RecognitionGlyphBounds(0f, 0f, 120f, 42f),
        )
        val pathReady = RecognitionGlyphReadiness(
            speciesResultReady = true,
            glyphPathReady = true,
            descriptor = descriptor,
        )
        assertTrue(RecognitionGlyphReadinessContract.isPathReady(identity, speciesKey, speciesName, pathReady))
        assertFalse(RecognitionGlyphReadinessContract.isRendered(identity, speciesKey, speciesName, pathReady))
        assertFalse(RecognitionGlyphReadinessContract.isNameStableClaim(identity, speciesKey, speciesName, pathReady))
        assertFalse(
            RecognitionGlyphReadinessContract.isPathReady(
                identity.copy(attemptId = "attempt-old"), speciesKey, speciesName, pathReady,
            ),
        )
        assertFalse(
            RecognitionGlyphReadinessContract.isPathReady(
                identity, "wrong-species", speciesName, pathReady,
            ),
        )
        assertFalse(
            RecognitionGlyphReadinessContract.isPathReady(
                identity, speciesKey, speciesName,
                pathReady.copy(descriptor = descriptor.copy(builtCharacterCount = 2)),
            ),
        )

        val receipt = RecognitionGlyphDrawReceipt(
            identity = identity,
            speciesKey = speciesKey,
            speciesName = speciesName,
            pathId = descriptor.pathId,
            geometryVersion = descriptor.geometryVersion,
            renderedProgress = 1f,
            fillPathDrawn = true,
            contourPathDrawn = true,
            drawPassSequence = 1L,
        )
        val rendered = pathReady.copy(
            glyphRevealRendered = true,
            nameStable = true,
            renderReceipt = receipt,
        )
        assertTrue(RecognitionGlyphReadinessContract.isNameStableClaim(identity, speciesKey, speciesName, rendered))
        assertFalse(
            RecognitionGlyphReadinessContract.isNameStableClaim(
                identity, speciesKey, speciesName,
                rendered.copy(renderReceipt = receipt.copy(renderedProgress = .98f)),
            ),
        )
        assertFalse(
            RecognitionGlyphReadinessContract.isNameStableClaim(
                identity, speciesKey, speciesName,
                rendered.copy(renderReceipt = receipt.copy(contourPathDrawn = false)),
            ),
        )
    }

    @Test
    fun orbCoreDissolveReceiptRequiresTheCurrentAttemptFullStretchAndCompletedDraw() {
        val identity = RecognitionSceneIdentity("session", "attempt", "image", "photo-sha", 1L)
        val complete = RecognitionOrbCoreDissolveReceipt(
            identity = identity,
            stage = RecognitionSceneStage.ORB_TO_NAME,
            horizontalStretchProgress = 1f,
            coreAlpha = 0f,
            drawPassSequence = 1L,
        )
        assertTrue(RecognitionOrbCoreDissolveContract.isComplete(identity, complete))
        assertFalse(RecognitionOrbCoreDissolveContract.isComplete(identity.copy(attemptId = "old-attempt"), complete))
        assertFalse(RecognitionOrbCoreDissolveContract.isComplete(identity, complete.copy(horizontalStretchProgress = .98f)))
        assertFalse(RecognitionOrbCoreDissolveContract.isComplete(identity, complete.copy(coreAlpha = .35f)))
        assertFalse(RecognitionOrbCoreDissolveContract.isComplete(identity, complete.copy(drawPassSequence = 0L)))
        assertFalse(RecognitionOrbCoreDissolveContract.isComplete(identity, complete.copy(stage = RecognitionSceneStage.ORB_RETURN)))
    }

    @Test
    fun resultInfoEntranceStartsOnlyAfterSceneHandoffAndCompletesBeforeInfoArrival() {
        val beforeInfo = setOf(
            ResultHandoffSubstage.NAME_STABLE,
            ResultHandoffSubstage.PHOTO_SETTLE,
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
        )
        assertFalse(RecognitionResultHandoffContract.mayExposeResultInfo(true, emptySet()))
        assertTrue(RecognitionResultHandoffContract.mayExposeResultInfo(true, beforeInfo))
        assertTrue(
            RecognitionResultHandoffContract.mayExposeResultInfo(
                true, beforeInfo + ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            ),
        )
        assertTrue(RecognitionResultHandoffContract.mayExposeResultInfo(false, emptySet()))
    }

    @Test
    fun resultInfoAndStableMilestonesRequireMeasuredDrawReceipts() {
        val identity = RecognitionSceneIdentity("session-1", "attempt-1", "image-1", "photo-sha", 7L)
        val info = RecognitionResultInfoRenderReceipt(
            identity = identity,
            resultIdentity = "grass_carp",
            geometryVersion = 19L,
            renderedProgress = 1f,
            layoutWidthPx = 1080,
            layoutHeightPx = 720,
            contentDrawn = true,
            drawPassSequence = 3L,
        )
        assertTrue(RecognitionResultInfoRenderContract.isComplete(identity, "grass_carp", 19L, info))
        assertFalse(RecognitionResultInfoRenderContract.isComplete(identity.copy(attemptId = "old"), "grass_carp", 19L, info))
        assertFalse(RecognitionResultInfoRenderContract.isComplete(identity, "grass_carp", 20L, info))
        assertFalse(RecognitionResultInfoRenderContract.isComplete(identity, "grass_carp", 19L, info.copy(drawPassSequence = 0L)))

        val stable = RecognitionResultStableRenderReceipt(
            identity = identity,
            resultIdentity = "grass_carp",
            geometryVersion = 19L,
            completedPrerequisites = setOf(
                ResultHandoffSubstage.NAME_STABLE,
                ResultHandoffSubstage.PHOTO_SETTLE,
                ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
                ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            ),
            photoProgress = 1f,
            resultInfoProgress = 1f,
            opticalResidueComplete = true,
            layoutWidthPx = 1080,
            layoutHeightPx = 1920,
            contentDrawn = true,
            drawPassSequence = 8L,
        )
        assertTrue(RecognitionResultStableRenderContract.isComplete(identity, "grass_carp", 19L, stable))
        assertFalse(RecognitionResultStableRenderContract.isComplete(identity, "grass_carp", 19L, stable.copy(opticalResidueComplete = false)))
        assertFalse(RecognitionResultStableRenderContract.isComplete(identity, "grass_carp", 19L, stable.copy(completedPrerequisites = emptySet())))
        assertFalse(RecognitionResultStableRenderContract.isComplete(identity, "grass_carp", 19L, stable.copy(drawPassSequence = 0L)))
    }

    @Test
    fun measuredResultGeometryCanOpenBeforeTheHeroRasterReceipt() {
        assertTrue(
            RecognitionResultGeometryReadiness.isReady(
                photoIdentityMatches = true,
                transitionGeometryReady = true,
                heroBoundsReady = true,
                titleBoundsReady = true,
                revealGeometryReady = true,
            ),
        )
        val stable = ResultStableReadiness.evaluateResultSceneStable(
            opticalResidue = OpticalResidueStatus(complete = true),
            resultInfoLayoutReady = true,
            resultGeometryReady = true,
            photoTransformAtTarget = true,
            heroRenderReady = false,
            photoProgress = 1f,
            resultInfoEntranceProgress = 1f,
            eventSequenceSynced = true,
            completedSubstages = setOf(
                ResultHandoffSubstage.NAME_STABLE,
                ResultHandoffSubstage.PHOTO_SETTLE,
                ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
                ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            ),
            alreadyStable = false,
        )
        assertFalse(stable.ready)
        assertEquals("HERO_RENDER_PENDING", stable.waitingReason)
    }

    @Test
    fun terminalReturnEnergyWaitsForCanvasReceiptAndIsConsumedOnlyOnce() {
        assertFalse(
            RecognitionReturnEventSubmissionReadiness.isReady(
                feedback = com.yujian.ai.ui.waterlight.core.EnergyFeedback.RETURN,
                level = 1f,
                hasResultSceneFrame = true,
                returnCanvasReceiptAccepted = false,
                returnEnergyEventAlreadyAccepted = false,
            ),
        )
        assertTrue(
            RecognitionReturnEventSubmissionReadiness.isReady(
                feedback = com.yujian.ai.ui.waterlight.core.EnergyFeedback.RETURN,
                level = 1f,
                hasResultSceneFrame = true,
                returnCanvasReceiptAccepted = true,
                returnEnergyEventAlreadyAccepted = false,
            ),
        )
        assertFalse(
            RecognitionReturnEventSubmissionReadiness.isReady(
                feedback = com.yujian.ai.ui.waterlight.core.EnergyFeedback.RETURN,
                level = 1f,
                hasResultSceneFrame = true,
                returnCanvasReceiptAccepted = true,
                returnEnergyEventAlreadyAccepted = true,
            ),
        )
        assertFalse(
            RecognitionReturnEventSubmissionReadiness.isReady(
                feedback = com.yujian.ai.ui.waterlight.core.EnergyFeedback.ABSORB,
                level = .98f,
                hasResultSceneFrame = true,
                returnCanvasReceiptAccepted = true,
                returnEnergyEventAlreadyAccepted = false,
            ),
        )
    }

    @Test
    fun fullRevealReturnProjectionConsumesAllThreeOriginalSegmentsAndKeepsIdentity() {
        val identity = RecognitionSceneIdentity("session", "attempt", "image", "photo-sha", 42L)
        val maskSha = "a".repeat(64)
        val pathId = "path:attempt:fish"
        fun path(sx: Float, sy: Float, ex: Float, ey: Float) = requireNotNull(
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(sx, sy), WaterLightPathPoint(ex, ey), 5, .04f,
            ),
        )
        val release = path(30f, 40f, 90f, 100f)
        val branch = path(90f, 100f, 180f, 200f)
        val processingTransform = calculateRecognitionImageTransform(400f, 300f, 1200, 800)
        val mappedPStar = processingTransform.mapNormalized(.62f, .43f)
        val fish = path(180f, 200f, mappedPStar.x, mappedPStar.y)
        val returns = listOf(
            path(mappedPStar.x, mappedPStar.y, 180f, 200f),
            path(180f, 200f, 90f, 100f),
            path(90f, 100f, 30f, 40f),
        )
        val snapshot = com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot(
            sessionId = identity.sessionId,
            attemptId = identity.attemptId,
            imageId = identity.imageId,
            photoSha256 = requireNotNull(identity.photoSha256),
            photoContentVersion = identity.photoContentVersion,
            maskSha256 = maskSha,
            maskVersion = 8L,
            geometryVersion = 19L,
            pStarNormalizedSource = WaterLightPathPoint(.62f, .43f),
            fishPathId = pathId,
            fishPathGeometry = fish,
            perimeterBranch = branch,
            orbReleasePath = release,
            returnPathId = pathId,
            returnPathSegments = returns,
            sourceCoordinateSpace = "RECOGNITION_VIEWPORT_PX",
            creationTimestampMonotonicMs = 1_000L,
            creationEventSequence = 7L,
        )
        val processingBounds = RecognitionRevealWindowRect(120f, 240f, 520f, 540f)
        val handoff = RecognitionRevealHandoff(
            key = RecognitionRevealKey(identity.sessionId, identity.attemptId),
            imageId = identity.imageId,
            photoSourceIdentity = "${identity.imageId}|source|1200x800|sha256=${identity.photoSha256}",
            normalizedSourceWidth = 1200,
            normalizedSourceHeight = 800,
            sourceOrientationDegreesApplied = 0,
            trustedDetectorBBox = null,
            detectorConfidence = .96f,
            subjectMaskOrContour = emptyList(),
            subjectRoiInSource = null,
            subjectAnchorNormalizedSource = DisplayPoint(.62f, .43f),
            contourSourceSpace = "SEGMENTATION_ROI_NORMALIZED",
            maskSha256 = maskSha,
            geometryTrustState = RecognitionGeometryTrustState.TRUSTED_MASK,
            processingPhotoViewportBounds = processingBounds,
            processingPhotoTransform = processingTransform,
            processingSubjectAnchor = DisplayPoint(368f, 369f),
            resultIdentity = "grass_carp",
            eligibility = RecognitionRevealEligibility.FULL_REVEAL,
            handoffTimestampUptimeMs = 2_000L,
            maskVersion = 8L,
            contactPointGeometryVersion = 19L,
            fishPathSnapshot = snapshot,
            photoContentVersion = identity.photoContentVersion,
        )

        val projected = requireNotNull(
            RecognitionRevealReturnPathProjector.project(
                handoff,
                snapshot,
                RecognitionRevealWindowRect(100f, 200f, 600f, 900f),
            ),
        )
        assertEquals(3, projected.segments.size)
        assertEquals(pathId, projected.pathId)
        assertEquals(pathId, projected.fishPathId)
        assertEquals(snapshot.pStarNormalizedSource, projected.pStarNormalizedSource)
        assertEquals(maskSha, projected.maskSha256)
        assertEquals(snapshot.maskVersion, projected.maskVersion)
        assertEquals(snapshot.geometryVersion, projected.geometryVersion)
        assertEquals(DisplayPoint(mappedPStar.x + 20f, mappedPStar.y + 40f), projected.segments.first().start)
        assertEquals(DisplayPoint(200f, 240f), projected.segments.first().end)
        assertEquals(DisplayPoint(200f, 240f), projected.segments[1].start)
        assertEquals(DisplayPoint(50f, 80f), projected.segments.last().end)
        assertEquals(projected.segments[0].end, projected.segments[1].start)
        assertEquals(projected.segments[1].end, projected.segments[2].start)
        assertEquals(null, RecognitionRevealReturnPathProjector.project(
            handoff.copy(maskVersion = 9L), snapshot,
            RecognitionRevealWindowRect(100f, 200f, 600f, 900f),
        ))
    }

}
