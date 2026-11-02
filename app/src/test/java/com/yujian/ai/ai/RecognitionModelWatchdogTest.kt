package com.yujian.ai.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionModelWatchdogTest {
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
    fun modelRemainsPendingUntilTheRealCallCompletes() = runBlocking {
        val records = mutableListOf<String>()
        val realModelResult = CompletableDeferred<String>()
        val task = async {
            RecognitionModelWatchdog.execute(
                modelName = "SLIMSAM",
                identity = null,
                nowMs = { 20L },
                log = { records.add(it) },
            ) { realModelResult.await() }
        }

        yield()
        assertTrue(task.isActive)
        assertEquals(1, records.size)
        assertTrue(records.single().contains("MODEL_START model=SLIMSAM"))

        realModelResult.complete("trusted-mask")
        assertEquals("trusted-mask", task.await())
        assertTrue(records.last().contains("outcome=READY"))
    }
}
