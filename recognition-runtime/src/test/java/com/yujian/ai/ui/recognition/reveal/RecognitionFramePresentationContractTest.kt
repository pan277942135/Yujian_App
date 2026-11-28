package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFramePresentationContractTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "photo", "a".repeat(64), 2L)
    private val snapshotId = "snapshot-1"
    private val snapshotHash = "b".repeat(64)

    @Test
    fun drawSubmissionAloneCannotSatisfyGlyphPresentedReadiness() {
        val descriptor = descriptor()
        val draw = drawReceipt(descriptor)
        val readiness = RecognitionGlyphReadiness(
            speciesResultReady = true,
            glyphPathReady = true,
            glyphRevealRendered = true,
            descriptor = descriptor,
            renderReceipt = draw,
        )

        assertFalse(RecognitionGlyphReadinessContract.isRendered(identity, "species", "鱼名", readiness))
        assertFalse(RecognitionGlyphReadinessContract.isNameStableClaim(identity, "species", "鱼名", readiness))
    }

    @Test
    fun presentFenceReceiptForExactSnapshotAndDrawSatisfiesRenderedContract() {
        val descriptor = descriptor()
        val draw = drawReceipt(descriptor)
        val readiness = RecognitionGlyphReadiness(
            speciesResultReady = true,
            glyphPathReady = true,
            glyphRevealRendered = true,
            nameStable = true,
            descriptor = descriptor,
            renderReceipt = draw,
            framePresentedReceipt = frameReceipt(draw, descriptor),
        )

        assertTrue(RecognitionGlyphReadinessContract.isRendered(identity, "species", "鱼名", readiness))
        assertTrue(RecognitionGlyphReadinessContract.isNameStableClaim(identity, "species", "鱼名", readiness))
    }

    @Test
    fun failedOrPendingPresentFenceCannotProduceAValidReceipt() {
        val descriptor = descriptor()
        val draw = drawReceipt(descriptor)
        val receipt = frameReceipt(draw, descriptor)

        assertFalse(receipt.copy(presentFenceSignalTimeNanos = 0L).isValid())
        assertFalse(receipt.copy(observedAtUptimeMs = 0L).isValid())
    }

    private fun descriptor() = RecognitionGlyphPathDescriptor(
        identity = identity,
        speciesKey = "species",
        speciesName = "鱼名",
        pathId = "glyph-path",
        geometryVersion = 7L,
        expectedCharacterCount = 2,
        builtCharacterCount = 2,
        contourCount = 8,
        totalPathLengthPx = 90f,
        bounds = RecognitionGlyphBounds(0f, 0f, 50f, 20f),
    )

    private fun drawReceipt(descriptor: RecognitionGlyphPathDescriptor) = RecognitionGlyphDrawReceipt(
        identity = identity,
        speciesKey = descriptor.speciesKey,
        speciesName = descriptor.speciesName,
        pathId = descriptor.pathId,
        geometryVersion = descriptor.geometryVersion,
        renderedProgress = 1f,
        fillPathDrawn = true,
        contourPathDrawn = true,
        drawPassSequence = 5L,
        snapshotId = snapshotId,
        snapshotContentHash = snapshotHash,
    )

    private fun frameReceipt(draw: RecognitionGlyphDrawReceipt, descriptor: RecognitionGlyphPathDescriptor) =
        RecognitionFramePresentedReceipt(
            identity = identity,
            snapshotId = snapshotId,
            snapshotContentHash = snapshotHash,
            renderer = "COMPOSE_TEXT_GLYPH",
            stage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            checkpointId = "KF18",
            frameSequence = 9L,
            geometryVersion = descriptor.geometryVersion,
            drawPassSequence = draw.drawPassSequence,
            presentFenceSignalTimeNanos = 10_000L,
            observedAtUptimeMs = 100L,
        )
}
