package com.yujian.ai.ui.recognition.reveal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
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
    fun resultDetailsStayHiddenUntilTheAcceptedInfoArrivalMilestone() {
        val beforeInfo = setOf(
            ResultHandoffSubstage.NAME_STABLE,
            ResultHandoffSubstage.PHOTO_SETTLE,
            ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
        )
        assertFalse(RecognitionResultHandoffContract.mayExposeResultInfo(true, emptySet()))
        assertFalse(RecognitionResultHandoffContract.mayExposeResultInfo(true, beforeInfo))
        assertTrue(
            RecognitionResultHandoffContract.mayExposeResultInfo(
                true, beforeInfo + ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            ),
        )
        assertTrue(RecognitionResultHandoffContract.mayExposeResultInfo(false, emptySet()))
    }

}
