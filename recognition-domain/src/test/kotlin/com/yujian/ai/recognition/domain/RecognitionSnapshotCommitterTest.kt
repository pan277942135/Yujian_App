package com.yujian.ai.recognition.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionSnapshotCommitterTest {
    @Test
    fun repeatedCommitReturnsTheFirstImmutableSnapshot() {
        val committer = RecognitionSnapshotCommitter().apply { activateAttempt("attempt-1") }
        val first = snapshot(snapshotId = "snapshot-first", committedAt = 10L)
        val duplicate = snapshot(snapshotId = "snapshot-duplicate", committedAt = 20L)

        assertEquals(SnapshotCommitStatus.COMMITTED, committer.commitOnce("attempt-1", first).status)
        val repeated = committer.commitOnce("attempt-1", duplicate)
        assertEquals(SnapshotCommitStatus.ALREADY_COMMITTED, repeated.status)
        assertEquals("snapshot-first", repeated.snapshot?.snapshotId)
        assertEquals(first.contentHash, repeated.snapshot?.contentHash)
        assertNotNull(committer.snapshot("attempt-1"))
    }

    @Test
    fun changedPayloadConflictsAndOldAttemptCannotCommit() {
        val committer = RecognitionSnapshotCommitter().apply { activateAttempt("attempt-1") }
        val first = snapshot(snapshotId = "snapshot-1", committedAt = 10L)
        assertEquals(SnapshotCommitStatus.COMMITTED, committer.commitOnce("attempt-1", first).status)

        val changed = snapshot(snapshotId = "snapshot-2", committedAt = 11L, confidence = .73f)
        assertEquals(SnapshotCommitStatus.CONFLICT, committer.commitOnce("attempt-1", changed).status)

        committer.activateAttempt("attempt-2")
        assertEquals(SnapshotCommitStatus.STALE_ATTEMPT, committer.commitOnce("attempt-1", first).status)
        assertNull(committer.snapshot("attempt-2"))
    }

    @Test
    fun listsAreFrozenAndOutcomeBranchesAreValidated() {
        val mutableDetections = mutableListOf(DetectorDetection(.1f, .2f, .8f, .9f, .95f))
        val frozen = snapshot("snapshot-1", 10L, detections = mutableDetections)
        mutableDetections.clear()
        assertEquals(1, frozen.detectorResult.detections.size)
        assertTrue(runCatching {
            (frozen.detectorResult.detections as MutableList).clear()
        }.isFailure)

        assertTrue(snapshot("full", 10L).isValid())
        assertTrue(snapshot("orb", 10L, outcome = RecognitionOutcome.ORB_ONLY).isValid())
        assertTrue(snapshot("failed", 10L, outcome = RecognitionOutcome.CLASSIFICATION_FAILED).isValid())
        assertTrue(!snapshot("invalid-orb", 10L, outcome = RecognitionOutcome.ORB_ONLY, trusted = true).isValid())
    }

    private fun snapshot(
        snapshotId: String,
        committedAt: Long,
        confidence: Float = .95f,
        outcome: RecognitionOutcome = RecognitionOutcome.FULL_REVEAL,
        trusted: Boolean = outcome == RecognitionOutcome.FULL_REVEAL,
        detections: List<DetectorDetection> = listOf(DetectorDetection(.1f, .2f, .8f, .9f, .95f)),
    ): RecognitionResultSnapshot {
        val photo = PhotoIdentity(
            "photo-1", "a".repeat(64), "b".repeat(64), 100, 80, 1,
            PhotoIdentity.NORMALIZATION_VERSION_V1, 1L,
        )
        val classifierFailed = outcome == RecognitionOutcome.CLASSIFICATION_FAILED
        val path = if (outcome == RecognitionOutcome.FULL_REVEAL) {
            FishPathSnapshotRef("fish-path-1", "c".repeat(64), 7L, photo)
        } else null
        return RecognitionResultSnapshot.create(
            snapshotId = snapshotId,
            recognitionSessionId = "session-1",
            attemptId = "attempt-1",
            photoIdentity = photo,
            modelBundleIdentity = ModelBundleIdentity(
                ModelIdentity("detector", "v1", "d".repeat(64)),
                ModelIdentity("classifier", "v1", "e".repeat(64)),
                ModelIdentity("vision", "v1", "f".repeat(64)),
                ModelIdentity("decoder", "v1", "0".repeat(64)),
            ),
            detectorResult = DetectorResult("READY", detections),
            classifierResult = ClassifierResult(
                terminalStatus = if (classifierFailed) "FAILED" else "READY",
                modelRunId = "classifier-run-1",
                species = if (classifierFailed) null else SpeciesResult("grass_carp", "草鱼", confidence),
                candidates = if (classifierFailed) emptyList() else listOf(
                    SpeciesResult("grass_carp", "草鱼", confidence),
                ),
            ),
            maskOutcome = MaskOutcome(
                terminalStatus = if (trusted) "TRUSTED" else "FAILED",
                trusted = trusted,
                maskSha256 = if (trusted) "1".repeat(64) else null,
                failureCode = if (trusted) null else "MASK_UNTRUSTED",
            ),
            contourOutcome = ContourOutcome(
                terminalStatus = if (trusted) "CLOSED" else "UNAVAILABLE",
                closed = trusted,
                geometrySha256 = if (trusted) "2".repeat(64) else null,
            ),
            pStarGeometry = if (trusted) PStarGeometry(.5f, .5f, 240f, 320f, 7L) else null,
            fishPathSnapshotRef = path,
            recognitionOutcome = outcome,
            committedAtMonotonicNanos = committedAt,
        )
    }
}
