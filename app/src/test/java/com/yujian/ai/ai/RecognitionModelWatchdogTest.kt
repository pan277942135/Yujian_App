package com.yujian.ai.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionModelWatchdogTest {
    @Test
    fun defaultPerModelLimitIsSixtySeconds() {
        assertEquals(60_000L, RecognitionModelWatchdog.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun successRecordsIdentityAndElapsedTime() = runBlocking {
        var now = 100L
        val records = mutableListOf<String>()
        val identity = RecognitionModelIdentity("session-1", "attempt-2", "image-3", "sha-4")

        val value = RecognitionModelWatchdog.execute(
            modelName = "CLASSIFIER",
            identity = identity,
            nowMs = { now },
            log = { records.add(it) },
        ) {
            now = 145L
            "species"
        }

        assertEquals("species", value)
        assertTrue(records.first().contains("MODEL_START model=CLASSIFIER"))
        assertTrue(records.first().contains("session=session-1 attempt=attempt-2 image=image-3 sha256=sha-4"))
        assertTrue(records.last().contains("duration_ms=45 outcome=READY"))
    }

    @Test
    fun timedOutModelHasExplicitTerminalEvent() = runBlocking {
        val records = mutableListOf<String>()
        var timedOut: RecognitionModelTimeoutException? = null
        try {
            RecognitionModelWatchdog.execute(
                modelName = "SLIMSAM",
                identity = null,
                timeoutMs = 10L,
                nowMs = { 20L },
                log = { records.add(it) },
            ) {
                delay(100L)
                "unreachable"
            }
        } catch (error: RecognitionModelTimeoutException) {
            timedOut = error
        }

        assertEquals("SLIMSAM", timedOut?.modelName)
        assertEquals(10L, timedOut?.timeoutMs ?: -1L)
        assertTrue(records.last().contains("outcome=TIMEOUT"))
    }
}
