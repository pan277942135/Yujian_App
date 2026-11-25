package com.yujian.ai.ui.waterlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class RecognitionPathBindingCommitCoordinatorTest {
    @Test fun onlyBothAcceptedEndpointsReachTheLinearizationCommit() {
        data class Row(val controller: Boolean, val store: Boolean, val expected: Boolean)
        listOf(
            Row(true, true, true),
            Row(true, false, false),
            Row(false, true, false),
            Row(false, false, false),
            Row(true, false, false), // Store PENDING
            Row(false, true, false), // Controller PENDING
        ).forEachIndexed { index, row ->
            val coordinator = RecognitionPathBindingCommitCoordinator()
            val commits = mutableListOf<String>()
            val receipt = coordinator.commit(
                identity = identity("attempt-$index"),
                nowUptimeMs = 100,
                controllerPrepared = row.controller,
                storePrepared = row.store,
                commitController = { commits += "controller"; row.controller },
                commitStore = { commits += "store"; row.store },
            )
            assertEquals(row.expected, receipt != null)
            assertEquals(row.expected, coordinator.committedReceipt("attempt-$index") != null)
            if (row.expected) assertEquals(listOf("controller", "store"), commits)
        }
    }

    @Test fun commitIsIdempotentWithoutRepeatingEndpointEffectsAndConflictCannotAbortPriorBinding() {
        val coordinator = RecognitionPathBindingCommitCoordinator()
        var controllerCommits = 0
        var storeCommits = 0
        var aborts = 0
        val first = coordinator.commit(
            identity("attempt-1"), 10, true, true,
            commitController = { controllerCommits++; true },
            commitStore = { storeCommits++; true },
        )
        val repeated = coordinator.commit(
            identity("attempt-1"), 20, true, true,
            commitController = { controllerCommits++; true },
            commitStore = { storeCommits++; true },
            abortStore = { aborts++ },
        )
        assertNotNull(first)
        assertEquals(first, repeated)
        assertEquals(1, controllerCommits)
        assertEquals(1, storeCommits)
        assertNull(coordinator.commit(
            identity("attempt-1").copy(pathSnapshotContentHash = "e".repeat(64)),
            30, true, true, { true }, { true }, abortStore = { aborts++ },
        ))
        assertEquals(0, aborts)
        assertEquals(first, coordinator.committedReceipt("attempt-1"))
    }

    @Test fun storeFailureCompensatesControllerAndAbortPreventsLateCommit() {
        val coordinator = RecognitionPathBindingCommitCoordinator()
        var compensated = false
        var aborted = false
        val result = coordinator.commit(
            identity("attempt-1"), 10, true, true,
            commitController = { true },
            commitStore = { false },
            compensateController = { compensated = true },
            abortStore = { aborted = true },
        )
        assertNull(result)
        assertTrue(compensated)
        assertTrue(aborted)
        assertNull(coordinator.committedReceipt("attempt-1"))
    }

    @Test fun stalePhotoOrAttemptAndDifferentPathIdentityCannotCommit() {
        val coordinator = RecognitionPathBindingCommitCoordinator()
        assertNull(coordinator.commit(identity("attempt-1").copy(photoIdentityHash = "bad"), 1, true, true, { true }, { true }))
        val first = coordinator.commit(identity("attempt-1"), 1, true, true, { true }, { true })
        assertNotNull(first)
        assertNull(coordinator.committedReceipt("attempt-2"))
        assertNull(coordinator.commit(
            identity("attempt-1").copy(photoId = "old-photo"), 2, true, true, { true }, { true },
        ))
    }

    @Test fun concurrentDuplicateCommitHasOneStableReceipt() {
        val coordinator = RecognitionPathBindingCommitCoordinator()
        val latch = CountDownLatch(1)
        val receipts = mutableListOf<RecognitionPathBindingCommitReceipt?>()
        val threads = (0 until 8).map { index ->
            Thread {
                latch.await()
                val receipt = coordinator.commit(
                    identity("attempt-1"), 100L + index, true, true, { true }, { true },
                )
                synchronized(receipts) { receipts += receipt }
            }.apply { start() }
        }
        latch.countDown()
        threads.forEach(Thread::join)
        assertEquals(8, receipts.size)
        assertEquals(1, receipts.filterNotNull().map { it.receiptId }.distinct().size)
        assertEquals(8, receipts.count { it != null })
    }

    @Test fun finalReceiptRequiresBothPrepareResults() {
        val coordinator = RecognitionPathBindingCommitCoordinator()
        val controllerCommitCalls = AtomicInteger()
        assertNull(coordinator.commit(
            identity("attempt-1"), 1, controllerPrepared = true, storePrepared = false,
            commitController = { controllerCommitCalls.incrementAndGet(); true },
            commitStore = { true },
        ))
        assertEquals(0, controllerCommitCalls.get())
    }

    private fun identity(attempt: String) = RecognitionPathBindingIdentity(
        recognitionSessionId = "session-1",
        attemptId = attempt,
        photoId = "photo-1",
        photoIdentityHash = "a".repeat(64),
        pathSnapshotContentHash = "b".repeat(64),
        fishPathId = "path-1",
        maskSha256 = "c".repeat(64),
        maskVersion = 3L,
        geometryVersion = 4L,
    )
}
