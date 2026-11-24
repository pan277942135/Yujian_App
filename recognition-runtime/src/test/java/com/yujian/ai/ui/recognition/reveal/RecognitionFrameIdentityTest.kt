package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFrameIdentityTest {
    private val identity = RecognitionSceneIdentity("session", "attempt", "photo", "a".repeat(64), 2L)
    private val receipt = RecognitionFramePresentedReceipt(
        identity = identity,
        snapshotId = "snapshot-1",
        snapshotContentHash = "b".repeat(64),
        renderer = "COMPOSE_TEXT_GLYPH",
        stage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
        checkpointId = "KF18",
        frameSequence = 9L,
        geometryVersion = 7L,
        drawPassSequence = 5L,
        presentFenceSignalTimeNanos = 10_000L,
        observedAtUptimeMs = 100L,
    )

    @Test
    fun oldAttemptWrongSnapshotAndMismatchedFrameSequenceAreRejected() {
        assertTrue(receipt.matches(
            expectedIdentity = identity,
            expectedSnapshotId = "snapshot-1",
            expectedSnapshotContentHash = "b".repeat(64),
            expectedRenderer = "COMPOSE_TEXT_GLYPH",
            expectedStage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            expectedCheckpointId = "KF18",
            expectedFrameSequence = 9L,
            expectedGeometryVersion = 7L,
            expectedDrawPassSequence = 5L,
        ))
        assertFalse(matches(identity.copy(attemptId = "old")))
        assertFalse(receipt.matches(
            expectedIdentity = identity,
            expectedSnapshotId = "wrong-snapshot",
            expectedSnapshotContentHash = "b".repeat(64),
            expectedRenderer = "COMPOSE_TEXT_GLYPH",
            expectedStage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            expectedCheckpointId = "KF18",
            expectedFrameSequence = 9L,
            expectedGeometryVersion = 7L,
            expectedDrawPassSequence = 5L,
        ))
        assertFalse(receipt.matches(
            expectedIdentity = identity,
            expectedSnapshotId = "snapshot-1",
            expectedSnapshotContentHash = "b".repeat(64),
            expectedRenderer = "COMPOSE_TEXT_GLYPH",
            expectedStage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
            expectedCheckpointId = "KF18",
            expectedFrameSequence = 10L,
            expectedGeometryVersion = 7L,
            expectedDrawPassSequence = 5L,
        ))
    }

    private fun matches(expectedIdentity: RecognitionSceneIdentity): Boolean = receipt.matches(
        expectedIdentity = expectedIdentity,
        expectedSnapshotId = "snapshot-1",
        expectedSnapshotContentHash = "b".repeat(64),
        expectedRenderer = "COMPOSE_TEXT_GLYPH",
        expectedStage = RecognitionSceneStage.SPECIES_NAME_REVEAL,
        expectedCheckpointId = "KF18",
        expectedFrameSequence = 9L,
        expectedGeometryVersion = 7L,
        expectedDrawPassSequence = 5L,
    )
}
