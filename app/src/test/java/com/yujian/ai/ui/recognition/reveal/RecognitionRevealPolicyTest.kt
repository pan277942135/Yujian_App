package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRevealPolicyTest {
    @Test
    fun sharedSemanticStagesDriveRevealWithoutAnAggregateTimeline() {
        val handoff = handoff()
        val policy = RecognitionRevealMotionPolicy()

        val returnStart = frame(RecognitionSceneStage.ORB_RETURN, 0f, handoff, policy)
        assertEquals(RecognitionRevealPhase.ORB_RETURN, returnStart.phase)
        assertEquals(0f, returnStart.photoProgress, 0f)
        assertEquals(RecognitionRevealClock.FULL_PARTICLE_LIMIT, returnStart.visibleParticleCount)
        val returnMiddle = frame(RecognitionSceneStage.ORB_RETURN, .5f, handoff, policy)
        assertEquals(0f, returnMiddle.photoProgress, 0f)
        assertEquals(.5f, returnMiddle.orbReturnProgress, .0001f)

        val toName = frame(RecognitionSceneStage.ORB_TO_NAME, .5f, handoff, policy)
        assertEquals(RecognitionRevealPhase.ORB_TO_NAME, toName.phase)
        assertEquals(RecognitionRevealTitleMode.FORMING, toName.titleMode)
        assertEquals(.5f, toName.orbToNameProgress, .0001f)
        assertEquals(0f, toName.photoProgress, 0f)
        val species = frame(RecognitionSceneStage.SPECIES_NAME_REVEAL, .5f, handoff, policy)
        assertEquals(RecognitionRevealPhase.SPECIES_NAME_REVEAL, species.phase)
        assertEquals(RecognitionRevealTitleMode.REVEALING, species.titleMode)
        assertEquals(0f, species.photoProgress, 0f)
        val residue = frame(RecognitionSceneStage.RESIDUE_SETTLE, .5f, handoff, policy)
        assertEquals(RecognitionRevealPhase.RESIDUE_SETTLE, residue.phase)
        assertEquals(.5f, residue.afterglowAlpha, .0001f)
        assertEquals(.5f, residue.photoProgress, .0001f)
        val resultNameHold = frame(RecognitionSceneStage.RESIDUE_SETTLE, 0f, handoff, policy)
        assertEquals(RecognitionRevealTitleMode.VISIBLE, resultNameHold.titleMode)
        assertEquals(0f, resultNameHold.photoProgress, 0f)
        assertTrue(residue.drawOpticalLayer)
        val settled = frame(RecognitionSceneStage.RESIDUE_SETTLE, 1f, handoff, policy, stageEventComplete = true)
        assertEquals(RecognitionRevealPhase.COMPLETE, settled.phase)
        assertFalse(settled.drawOpticalLayer)
    }

    @Test
    fun orbOnlyFallbackKeepsItsReturnAndResidueOpticsWithoutFishGold() {
        val titleOnly = handoff(RecognitionRevealEligibility.TITLE_FADE_ONLY)
        val titleFrame = frame(RecognitionSceneStage.ORB_RETURN, .5f, titleOnly, RecognitionRevealMotionPolicy())
        assertTrue(titleFrame.fallback)
        assertEquals(0, titleFrame.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.HIDDEN, titleFrame.titleMode)
        assertEquals(RecognitionRevealTitleMode.FORMING,
            frame(RecognitionSceneStage.ORB_TO_NAME, .5f, titleOnly, RecognitionRevealMotionPolicy()).titleMode)
        assertEquals(RecognitionRevealTitleMode.REVEALING,
            frame(RecognitionSceneStage.SPECIES_NAME_REVEAL, .5f, titleOnly, RecognitionRevealMotionPolicy()).titleMode)
        val residue = frame(RecognitionSceneStage.RESIDUE_SETTLE, .5f, titleOnly, RecognitionRevealMotionPolicy())
        assertTrue(residue.drawOpticalLayer)
        assertTrue(residue.afterglowAlpha > 0f)
        val settled = frame(RecognitionSceneStage.RESIDUE_SETTLE, 1f, titleOnly,
            RecognitionRevealMotionPolicy(), stageEventComplete = true)
        assertEquals(0f, settled.afterglowAlpha, 0f)
        assertFalse(settled.drawOpticalLayer)

        val nonHigh = handoff(RecognitionRevealEligibility.PHOTO_ONLY)
        val nonHighFrame = frame(RecognitionSceneStage.ORB_RETURN, .5f, nonHigh, RecognitionRevealMotionPolicy())
        assertEquals(1f, nonHighFrame.photoProgress, 0f)
        assertEquals(0f, nonHighFrame.transferProgress, 0f)
        assertEquals(0, nonHighFrame.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.STATIC, nonHighFrame.titleMode)
        assertFalse("PHOTO_ONLY must not acquire Orb-only reveal optics", nonHighFrame.drawOpticalLayer)
    }

    @Test
    fun reduceMotionIsImmediateAndLowPerformanceRespectsParticleBudget() {
        val full = handoff()
        val reduced = frame(RecognitionSceneStage.ORB_RETURN, .5f, full, RecognitionRevealMotionPolicy(reduceMotion = true))
        assertFalse(reduced.drawOpticalLayer)
        assertEquals(0, reduced.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.VISIBLE, reduced.titleMode)
        assertTrue(reduced.ctaEnabled)

        val low = frame(RecognitionSceneStage.ORB_RETURN, .5f, full, RecognitionRevealMotionPolicy(lowPerformance = true))
        val regular = frame(RecognitionSceneStage.ORB_RETURN, .5f, full, RecognitionRevealMotionPolicy())
        assertTrue(low.visibleParticleCount in 0..RecognitionRevealClock.LOW_PARTICLE_LIMIT)
        assertTrue(regular.visibleParticleCount in 0..RecognitionRevealClock.FULL_PARTICLE_LIMIT)
        assertTrue(low.ctaEnabled)
    }

    @Test
    fun geometryTrustRejectsStaleImageBadMasksAndInvalidBoxes() {
        val box = NormalizedFishBox(.12f, .18f, .82f, .91f)
        val trustedSubject = subject(box)
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_MASK,
            RecognitionRevealPolicy.geometryTrust(box, trustedSubject, "image-a", 1200, 800, .96f),
        )
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            RecognitionRevealPolicy.geometryTrust(box, trustedSubject, "image-b", 1200, 800, .96f),
        )
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            RecognitionRevealPolicy.geometryTrust(box, subject(box).copy(maskSize = 3), "image-a", 1200, 800, .96f),
        )
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            RecognitionRevealPolicy.geometryTrust(box, subject(box).copy(quality = FishSubjectQuality.WARNING), "image-a", 1200, 800, .96f),
        )
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            RecognitionRevealPolicy.geometryTrust(
                box,
                subject(box).copy(subjectBox = NormalizedSourceRect(.2f, .2f, .8f, .8f)),
                "image-a",
                1200,
                800,
                .96f,
            ),
        )
        assertEquals(
            RecognitionGeometryTrustState.UNTRUSTED,
            RecognitionRevealPolicy.geometryTrust(NormalizedFishBox(-.2f, .1f, .7f, .8f), null, "image-a", 1200, 800, .96f),
        )
        assertEquals(
            RecognitionGeometryTrustState.MISSING,
            RecognitionRevealPolicy.geometryTrust(null, null, "image-a", 1200, 800, null),
        )
    }

    @Test
    fun geometryTrustBindsMaskToSessionAttemptAndPhotoHash() {
        val box = NormalizedFishBox(.12f, .18f, .82f, .91f)
        val geometry = subject(box).copy(
            recognitionSessionId = "session-a",
            recognitionAttemptId = "attempt-a",
            photoSha256 = "photo-hash-a",
        )
        fun trust(candidate: RecognitionSubjectGeometrySnapshot) = RecognitionRevealPolicy.geometryTrust(
            detectorBox = box,
            subject = candidate,
            imageId = "image-a",
            sourceWidthPx = 1200,
            sourceHeightPx = 800,
            detectorConfidence = .96f,
            recognitionSessionId = "session-a",
            recognitionAttemptId = "attempt-a",
            photoSha256 = "photo-hash-a",
        )
        assertEquals(RecognitionGeometryTrustState.TRUSTED_MASK, trust(geometry))
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            trust(geometry.copy(recognitionAttemptId = "attempt-old")),
        )
        assertEquals(
            RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY,
            trust(geometry.copy(photoSha256 = "photo-hash-old")),
        )
    }

    @Test
    fun eligibilityIsHighOnlyAndRequiresTrustedGeometryAndPhotoAnchor() {
        val trusted = RecognitionGeometryTrustState.TRUSTED_MASK
        assertEquals(
            RecognitionRevealEligibility.FULL_REVEAL,
            RecognitionRevealPolicy.eligibility(true, true, true, trusted, true, true),
        )
        assertEquals(
            RecognitionRevealEligibility.PHOTO_ONLY,
            RecognitionRevealPolicy.eligibility(true, true, false, trusted, true, true),
        )
        assertEquals(
            RecognitionRevealEligibility.TITLE_FADE_ONLY,
            RecognitionRevealPolicy.eligibility(true, true, true, RecognitionGeometryTrustState.TRUSTED_BBOX_ONLY, true, true),
        )
        assertEquals(
            RecognitionRevealEligibility.STATIC_RESULT,
            RecognitionRevealPolicy.eligibility(false, true, true, trusted, true, true),
        )
    }

    @Test
    fun rr09RequiresReadyGeometryOnTheFirstResultFrame() {
        fun canStart(
            eligibility: RecognitionRevealEligibility = RecognitionRevealEligibility.FULL_REVEAL,
            photoGeometry: Boolean = true,
            hero: Boolean = true,
            title: Boolean = true,
            revealGeometry: Boolean = true,
            reduceMotion: Boolean = false,
        ) = RecognitionRevealPolicy.canStartOnFirstResultFrame(
            eligibility = eligibility,
            photoIdentityMatches = true,
            photoGeometryReady = photoGeometry,
            heroReady = hero,
            titleBoundsReady = title,
            revealGeometryReady = revealGeometry,
            reduceMotion = reduceMotion,
        )

        assertTrue(canStart())
        assertFalse(canStart(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY, revealGeometry = false))
        assertFalse(canStart(photoGeometry = false))
        assertFalse(canStart(hero = false))
        assertFalse(canStart(title = false))
        assertFalse(canStart(revealGeometry = false))
        assertFalse(canStart(reduceMotion = true))
        assertFalse(canStart(eligibility = RecognitionRevealEligibility.PHOTO_ONLY))
        assertFalse(canStart(eligibility = RecognitionRevealEligibility.STATIC_RESULT))
    }

    @Test
    fun attemptKeyAndPlaybackLedgerPreventReplayButAllowNextAttempt() {
        val ledger = RecognitionRevealPlaybackLedger(maxKeys = 2)
        val first = RecognitionRevealKey("session", "attempt-1")
        val second = RecognitionRevealKey("session", "attempt-2")
        assertTrue(ledger.claim(first))
        assertFalse(ledger.claim(first))
        assertTrue(ledger.hasPlayed(first))
        assertTrue(ledger.claim(second))
        assertFalse(RecognitionRevealSessionSnapshot("session", "attempt-old").matches(first))
        assertTrue(RecognitionRevealSessionSnapshot("session", "attempt-2").matches(second))
        assertTrue(ledger.claim(RecognitionRevealKey("session", "attempt-3")))
        assertFalse(ledger.hasPlayed(first)) // bounded process-lifetime history
    }

    @Test
    fun sourceCropTransformPreservesAspectWithoutStretching() {
        val crop = NormalizedSourceRect(.1f, .2f, .9f, .8f)
        val transform = com.yujian.ai.ui.identify.calculateRecognitionImageTransformForSourceRect(
            containerWidth = 800f,
            containerHeight = 300f,
            imageWidth = 1200,
            imageHeight = 800,
            sourceRect = crop,
        )
        assertEquals(1200, transform.sourceWidthPx)
        assertEquals(800, transform.sourceHeightPx)
        assertEquals(RecognitionContentScaleMode.CROP, transform.contentScaleMode)
        assertEquals(crop, transform.visibleSourceRect)
        assertEquals(transform.scale, transform.drawnWidth / transform.sourceWidthPx, .001f)
        assertEquals(transform.scale, transform.drawnHeight / transform.sourceHeightPx, .001f)
    }

    @Test
    fun latinNameComesOnlyFromExactCatalogIdAndHidesWhenMissing() {
        val catalog = listOf(
            FishGuideItem(id = "grass_carp", nameCn = "草鱼", scientificName = "Ctenopharyngodon idella"),
            FishGuideItem(id = "silver_carp", nameCn = "鲢鱼", scientificName = null),
        )
        assertEquals("Ctenopharyngodon idella", resolveRecognitionScientificName("grass_carp", catalog))
        assertEquals(null, resolveRecognitionScientificName("silver_carp", catalog))
        assertEquals(null, resolveRecognitionScientificName("missing", catalog))
    }

    private fun frame(
        stage: RecognitionSceneStage,
        progress: Float,
        handoff: RecognitionRevealHandoff,
        policy: RecognitionRevealMotionPolicy,
        stageEventComplete: Boolean = false,
    ): RecognitionRevealFrame {
        val frame = RecognitionSceneFrame(
            identity = RecognitionSceneIdentity("session-a", "attempt-a", "image-a", "hash", 1L),
            sceneElapsedMs = 60_000L,
            opticalReferenceElapsedMs = 60_000L,
            stage = stage,
            stageElapsedMs = (stage.minimumVisibleMs * progress).toLong(),
            stageProgress = progress,
            playbackMode = ScenePlaybackMode.AUTO,
            paused = false,
            reducedMotion = policy.reduceMotion,
            geometryVersion = 1L,
            stageEventComplete = stageEventComplete,
        )
        return RecognitionRevealClock.evaluate(frame, handoff, policy)
    }

    private fun handoff(
        eligibility: RecognitionRevealEligibility = RecognitionRevealEligibility.FULL_REVEAL,
    ) = RecognitionRevealHandoff(
        key = RecognitionRevealKey("session-a", "attempt-a"),
        imageId = "image-a",
        photoSourceIdentity = "image-a|source|1200x800",
        normalizedSourceWidth = 1200,
        normalizedSourceHeight = 800,
        sourceOrientationDegreesApplied = 0,
        trustedDetectorBBox = NormalizedSourceRect(.1f, .1f, .9f, .9f),
        detectorConfidence = .96f,
        subjectMaskOrContour = emptyList(),
        subjectRoiInSource = null,
        subjectAnchorNormalizedSource = null,
        contourSourceSpace = "test",
        geometryTrustState = RecognitionGeometryTrustState.TRUSTED_MASK,
        processingPhotoViewportBounds = RecognitionRevealWindowRect(0f, 0f, 1080f, 1920f),
        processingPhotoTransform = calculateRecognitionImageTransform(1080f, 1920f, 1200, 800),
        processingSubjectAnchor = null,
        resultIdentity = "species-a",
        eligibility = eligibility,
        handoffTimestampUptimeMs = 50L,
    )

    private fun subject(box: NormalizedFishBox) = RecognitionSubjectGeometrySnapshot(
        imageId = "image-a",
        sourceWidthPx = 1200,
        sourceHeightPx = 800,
        detectorBox = box,
        detectorConfidence = .96f,
        subjectBox = box.expand(.12f).let {
            NormalizedSourceRect(it.x1, it.y1, it.x2, it.y2)
        },
        contour = listOf(
            RecognitionContourSegment(.1f, .5f, .5f, .2f, .2f, .3f, .4f, .2f, beginsContour = true),
        ),
        quality = FishSubjectQuality.GOOD,
        maskSize = 64,
        expectedMaskSize = 64,
    )
}
