package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionNativeRunDeadlineGuardTest {
    @Test
    fun sharedAttemptDeadlineTerminatesRunAndRejectsLateResult() {
        var now = 1_000L
        var terminateCount = 0
        lateinit var scheduled: () -> Unit
        var scheduledDelay = -1L
        val guard = RecognitionNativeRunDeadlineGuard(
            deadline = RecognitionAttemptDeadline(1_000L, 60_000L),
            nowUptimeMs = { now },
            terminateNativeRun = { terminateCount++ },
            schedule = { delay, task ->
                scheduledDelay = delay
                scheduled = task
                RecognitionScheduledTask { }
            },
        )

        assertEquals(60_000L, scheduledDelay)
        assertFalse(guard.hasExpired())
        now = 61_000L
        scheduled()

        assertEquals(1, terminateCount)
        assertTrue(guard.hasExpired())
        assertThrows(RecognitionAttemptNativeTimeoutException::class.java) { guard.throwIfExpired() }
        guard.close()
    }

    @Test
    fun schedulerEarlyWakeRearmsAgainstOriginalDeadline() {
        var now = 10L
        val delays = mutableListOf<Long>()
        val tasks = mutableListOf<() -> Unit>()
        var terminateCount = 0
        val guard = RecognitionNativeRunDeadlineGuard(
            deadline = RecognitionAttemptDeadline(10L, 8L),
            nowUptimeMs = { now },
            terminateNativeRun = { terminateCount++ },
            schedule = { delay, task ->
                delays += delay
                tasks += task
                RecognitionScheduledTask { }
            },
        )

        now = 15L
        tasks[0]()
        assertEquals(listOf(8L, 3L), delays)
        assertEquals(0, terminateCount)
        now = 18L
        tasks[1]()
        assertEquals(1, terminateCount)
        assertThrows(RecognitionAttemptNativeTimeoutException::class.java) { guard.throwIfExpired() }
        guard.close()
    }

    @Test
    fun closeCancelsWatchdogAndDoesNotTerminateSuccessfulNativeRun() {
        var now = 100L
        var terminateCount = 0
        var cancelCount = 0
        lateinit var scheduled: () -> Unit
        val guard = RecognitionNativeRunDeadlineGuard(
            deadline = RecognitionAttemptDeadline(100L, 1_000L),
            nowUptimeMs = { now },
            terminateNativeRun = { terminateCount++ },
            schedule = { _, task ->
                scheduled = task
                RecognitionScheduledTask { cancelCount++ }
            },
        )

        guard.throwIfExpired()
        guard.close()
        now = 2_000L
        scheduled()

        assertEquals(1, cancelCount)
        assertEquals(0, terminateCount)
        assertFalse(guard.hasExpired())
    }

    @Test
    fun deadlineAlreadyExpiredTerminatesBeforeNativeSubmission() {
        var terminateCount = 0
        var schedulerCalls = 0
        val guard = RecognitionNativeRunDeadlineGuard(
            deadline = RecognitionAttemptDeadline(5L, 10L),
            nowUptimeMs = { 15L },
            terminateNativeRun = { terminateCount++ },
            schedule = { _, _ ->
                schedulerCalls++
                RecognitionScheduledTask { }
            },
        )

        assertEquals(1, terminateCount)
        assertEquals(0, schedulerCalls)
        assertThrows(RecognitionAttemptNativeTimeoutException::class.java) { guard.throwIfExpired() }
        guard.close()
    }
}
