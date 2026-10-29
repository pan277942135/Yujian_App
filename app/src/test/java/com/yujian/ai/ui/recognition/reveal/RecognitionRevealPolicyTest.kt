package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRevealPolicyTest {
    @Test
    fun timelineHasFrozenBoundariesAndOneMonotonicProgression() {
        val handoff = handoff()
        val policy = RecognitionRevealMotionPolicy()

        assertEquals(RecognitionRevealPhase.PHOTO_CONTINUITY, frame(0, handoff, policy).phase)
        assertEquals(0f, frame(0, handoff, policy).photoProgress, 0f)
        assertEquals(RecognitionRevealPhase.PHOTO_CONTINUITY, frame(120, handoff, policy).phase)
        assertTrue(frame(120, handoff, policy).photoProgress in 0f..1f)
        assertEquals(0.5f, frame(100, handoff, policy).photoProgress, 0.0001f)
        assertEquals(RecognitionRevealPhase.GOLD_TRANSFER, frame(200, handoff, policy).phase)
        assertEquals(RecognitionRevealClock.FULL_PARTICLE_LIMIT, frame(200, handoff, policy).visibleParticleCount)
        assertEquals(RecognitionRevealPhase.GOLD_TRANSFER, frame(379, handoff, policy).phase)
        assertEquals(0, frame(380, handoff, policy).visibleParticleCount)
        assertEquals(RecognitionRevealPhase.NAME_FORMATION, frame(380, handoff, policy).phase)
        assertEquals(RecognitionRevealTitleMode.FORMING, frame(549, handoff, policy).titleMode)
        assertEquals(RecognitionRevealPhase.NAME_REVEAL, frame(550, handoff, policy).phase)
        assertEquals(RecognitionRevealTitleMode.REVEALING, frame(610, handoff, policy).titleMode)
        assertEquals(RecognitionRevealPhase.AFTERGLOW, frame(680, handoff, policy).phase)
        assertEquals(RecognitionRevealTitleMode.VISIBLE, frame(680, handoff, policy).titleMode)
        assertEquals(RecognitionRevealPhase.COMPLETE, frame(850, handoff, policy).phase)
        assertEquals(0, frame(850, handoff, policy).visibleParticleCount)
        assertFalse(frame(850, handoff, policy).drawOpticalLayer)
    }

    @Test
    fun titleOnlyAndNonHighFallbacksNeverEmitGoldParticles() {
        val titleOnly = handoff(RecognitionRevealEligibility.TITLE_FADE_ONLY)
        val titleFrame = frame(120, titleOnly, RecognitionRevealMotionPolicy())
        assertTrue(titleFrame.fallback)
        assertEquals(0, titleFrame.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.HIDDEN, titleFrame.titleMode)
        assertEquals(RecognitionRevealTitleMode.FORMING, frame(400, titleOnly, RecognitionRevealMotionPolicy()).titleMode)
        assertEquals(RecognitionRevealTitleMode.REVEALING, frame(600, titleOnly, RecognitionRevealMotionPolicy()).titleMode)

        val nonHigh = handoff(RecognitionRevealEligibility.PHOTO_ONLY)
        val nonHighFrame = frame(290, nonHigh, RecognitionRevealMotionPolicy())
        assertEquals(1f, nonHighFrame.photoProgress, 0f)
        assertEquals(0f, nonHighFrame.transferProgress, 0f)
        assertEquals(0, nonHighFrame.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.STATIC, nonHighFrame.titleMode)
    }

    @Test
    fun reduceMotionIsImmediateAndLowPerformanceRespectsParticleBudget() {
        val full = handoff()
        val reduced = frame(290, full, RecognitionRevealMotionPolicy(reduceMotion = true))
        assertFalse(reduced.drawOpticalLayer)
        assertEquals(0, reduced.visibleParticleCount)
        assertEquals(RecognitionRevealTitleMode.VISIBLE, reduced.titleMode)
        assertTrue(reduced.ctaEnabled)

        val low = frame(290, full, RecognitionRevealMotionPolicy(lowPerformance = true))
        val regular = frame(290, full, RecognitionRevealMotionPolicy())
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
        timeMs: Long,
        handoff: RecognitionRevealHandoff,
        policy: RecognitionRevealMotionPolicy,
    ) = RecognitionRevealClock.evaluate(timeMs, handoff, policy)

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
        subjectBox = NormalizedSourceRect(.1f, .1f, .9f, .9f),
        contour = listOf(
            RecognitionContourSegment(.1f, .5f, .5f, .2f, .2f, .3f, .4f, .2f, beginsContour = true),
        ),
        quality = FishSubjectQuality.GOOD,
        maskSize = 64,
        expectedMaskSize = 64,
    )
}
