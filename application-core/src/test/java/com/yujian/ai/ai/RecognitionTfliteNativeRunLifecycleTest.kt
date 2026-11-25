package com.yujian.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionTfliteNativeRunLifecycleTest {
    @Test
    fun timeoutQuarantinesButDoesNotClaimNativeReturnOrReleaseBuffers() {
        var now = 10L
        val events = mutableListOf<RecognitionTfliteNativeEvent>()
        val lifecycle = RecognitionTfliteNativeRunLifecycle("model-session", { now }, events::add)
        val token = lifecycle.begin("infer-1", "attempt-1", RecognitionAttemptDeadline(10L, 20L))
        assertTrue(lifecycle.startNative(token, 12L, 16L))

        now = 30L
        assertTrue(lifecycle.onDeadline(token))
        assertTrue(lifecycle.isQuarantined())
        assertTrue(lifecycle.hasActiveRun())
        assertFalse(events.any { it.state == RecognitionTfliteNativeState.NATIVE_CALL_RETURNED })
        assertFalse(events.any { it.state == RecognitionTfliteNativeState.RESOURCE_RELEASED })
        assertFalse(lifecycle.requestClose())

        assertFalse(lifecycle.nativeReturned(token))
        assertTrue(lifecycle.finish(token))
        lifecycle.confirmSessionClosed()
        assertEquals(
            listOf(
                RecognitionTfliteNativeState.CREATED,
                RecognitionTfliteNativeState.RUNNING,
                RecognitionTfliteNativeState.NATIVE_CALL_STUCK,
                RecognitionTfliteNativeState.SESSION_QUARANTINED,
                RecognitionTfliteNativeState.RESOURCE_QUARANTINED,
                RecognitionTfliteNativeState.NATIVE_CALL_RETURNED,
                RecognitionTfliteNativeState.RESOURCE_RELEASED,
                RecognitionTfliteNativeState.SESSION_CLOSED,
            ),
            events.map { it.state },
        )
        assertEquals(12L, events.last().inputBufferBytes)
        assertEquals(16L, events.last().outputBufferBytes)
    }

    @Test
    fun closeDefersWhileNativeCallIsActiveAndNextRunIsRejectedAfterQuarantine() {
        val events = mutableListOf<RecognitionTfliteNativeEvent>()
        val lifecycle = RecognitionTfliteNativeRunLifecycle("session", { 4L }, events::add)
        val token = lifecycle.begin("infer", "attempt", null)
        assertTrue(lifecycle.startNative(token, 4L, 8L))
        assertFalse(lifecycle.requestClose())
        assertTrue(lifecycle.nativeReturned(token))
        assertTrue(lifecycle.finish(token))
        lifecycle.confirmSessionClosed()
        assertThrows(IllegalStateException::class.java) {
            lifecycle.begin("infer-2", "attempt-2", null)
        }
        assertTrue(events.any { it.state == RecognitionTfliteNativeState.SESSION_CLOSED })
    }

    @Test
    fun timeoutBeforeNativeSubmissionDoesNotClaimNativeCallStuck() {
        var now = 100L
        val events = mutableListOf<RecognitionTfliteNativeEvent>()
        val lifecycle = RecognitionTfliteNativeRunLifecycle("session", { now }, events::add)
        val token = lifecycle.begin("infer", "attempt", RecognitionAttemptDeadline(100L, 10L))
        now = 110L
        assertTrue(lifecycle.onDeadline(token))
        assertFalse(lifecycle.startNative(token, 4L, 8L))
        assertFalse(lifecycle.isQuarantined())
        assertFalse(events.any { it.state == RecognitionTfliteNativeState.NATIVE_CALL_STUCK })
        assertFalse(lifecycle.finish(token))
        assertTrue(events.any { it.state == RecognitionTfliteNativeState.RESOURCE_RELEASED })
    }
}
