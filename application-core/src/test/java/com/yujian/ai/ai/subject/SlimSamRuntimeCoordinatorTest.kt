package com.yujian.ai.ai.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SlimSamRuntimeCoordinatorTest {
    @Test
    fun concurrentStartupAndRecognitionRequestsCreateSessionsOnlyOnce() = runBlocking {
        val initializationCount = AtomicInteger()
        val gate = SlimSamInitializationGate(maxAttempts = 2) {
            initializationCount.incrementAndGet()
            delay(25)
        }

        (1..32).map {
            async(Dispatchers.Default) { gate.ensureReady() }
        }.awaitAll()

        assertEquals(1, initializationCount.get())
        assertEquals(1, gate.attemptCount)
        assertTrue(gate.isReady)
    }

    @Test
    fun failedInitializationRetriesOnlyWithinConfiguredLimit() = runBlocking {
        val attempts = AtomicInteger()
        val gate = SlimSamInitializationGate(maxAttempts = 2) {
            if (attempts.incrementAndGet() == 1) error("temporary ORT startup failure")
        }

        gate.ensureReady()

        assertEquals(2, attempts.get())
        assertEquals(2, gate.attemptCount)
        assertTrue(gate.isReady)
    }

    @Test
    fun permanentlyFailedInitializationStopsAtConfiguredLimit() = runBlocking {
        val attempts = AtomicInteger()
        val gate = SlimSamInitializationGate(maxAttempts = 3) {
            attempts.incrementAndGet()
            error("persistent ORT startup failure")
        }

        val failure = runCatching { gate.ensureReady() }.exceptionOrNull()

        assertTrue(failure is SlimSamInitializationFailedException)
        assertEquals(3, attempts.get())
        assertEquals(3, gate.attemptCount)
        assertFalse(gate.isReady)
    }

    @Test
    fun wrappedRuntimeFailureRetainsModelStageRootCauseAndActualTensorMetadata() {
        val metadata = "inputs=[pixel_values] FLOAT [-1, 4, 1024, 1024]"
        val modelFailure = SlimSamModelException(
            errorCode = "VISION_INPUT_SHAPE_MISMATCH",
            message = "SlimSAM Vision Session metadata rejected: $metadata",
            stage = "VISION_SESSION_VALIDATE",
            cause = IllegalArgumentException("expected channel count 3"),
            tensorMetadata = metadata,
        )
        val wrapped = SlimSamInitializationFailedException(attempts = 2, cause = modelFailure)

        val details = wrapped.slimSamFailureDetails()

        assertEquals("VISION_INPUT_SHAPE_MISMATCH", details.errorCode)
        assertEquals("VISION_SESSION_VALIDATE", details.stage)
        assertEquals(SlimSamModelException::class.java.name, details.exceptionClass)
        assertTrue(details.rootCause.contains("expected channel count 3"))
        assertEquals(metadata, details.tensorMetadata)
    }

    @Test
    fun cancellationDoesNotConsumeInitializationRetryBudget() = runBlocking {
        val attempts = AtomicInteger()
        val gate = SlimSamInitializationGate(maxAttempts = 3) {
            if (attempts.incrementAndGet() == 1) throw CancellationException("caller cancelled")
        }

        val cancelled = runCatching { gate.ensureReady() }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
        assertEquals(1, gate.attemptCount)
        assertFalse(gate.isReady)

        gate.ensureReady()
        assertEquals(2, attempts.get())
        assertTrue(gate.isReady)
    }

    @Test
    fun embeddingCacheIdentityUsesPhotoContentGeometryAndModelContracts() {
        val samePhoto = SlimSamEmbeddingCacheKey.create(
            photoSha256 = "photo-a",
            width = 1200,
            height = 800,
            modelVersion = "vision+decoder-v1",
            preprocessingVersion = "rgb1024-pad-v1",
        )
        val decodedAgain = SlimSamEmbeddingCacheKey.create(
            photoSha256 = "photo-a",
            width = 1200,
            height = 800,
            modelVersion = "vision+decoder-v1",
            preprocessingVersion = "rgb1024-pad-v1",
        )

        assertEquals(samePhoto, decodedAgain)
        assertFalse(samePhoto == decodedAgain.copy(photoSha256 = "photo-b"))
        assertFalse(samePhoto == decodedAgain.copy(width = 1199))
        assertFalse(samePhoto == decodedAgain.copy(modelVersion = "vision+decoder-v2"))
        assertFalse(samePhoto == decodedAgain.copy(preprocessingVersion = "rgb1024-pad-v2"))
    }

    @Test
    fun cancelledOldPhotoCannotReplaceTheActivePhotosEmbedding() {
        val oldPhoto = key("photo-old")
        val newPhoto = key("photo-new")
        val cache = SlimSamEmbeddingCache<String>()

        cache.activate(newPhoto)
        assertFalse(cache.putIfActive(oldPhoto, "old embedding"))
        assertNull(cache.get(newPhoto))
        assertTrue(cache.putIfActive(newPhoto, "new embedding"))
        assertEquals("new embedding", cache.get(newPhoto))
    }

    @Test
    fun replacingActivePhotoReleasesThePreviousEmbeddingReference() {
        val cache = SlimSamEmbeddingCache<String>()
        val oldPhoto = key("photo-old")
        val newPhoto = key("photo-new")
        cache.activate(oldPhoto)
        assertTrue(cache.putIfActive(oldPhoto, "old embedding"))

        cache.activate(newPhoto)

        assertNull(cache.get(oldPhoto))
        assertNull(cache.get(newPhoto))
    }

    private fun key(sha: String) = SlimSamEmbeddingCacheKey.create(
        photoSha256 = sha,
        width = 1200,
        height = 800,
        modelVersion = "vision+decoder-v1",
        preprocessingVersion = "rgb1024-pad-v1",
    )
}
