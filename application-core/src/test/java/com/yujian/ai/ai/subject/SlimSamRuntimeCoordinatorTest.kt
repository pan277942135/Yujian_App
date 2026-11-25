package com.yujian.ai.ai.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
            recognitionSessionId = "session-1",
            attemptId = "attempt-1",
            photoId = "photo-1",
            sourceBytesSha256 = "0".repeat(64),
            canonicalPixelSha256 = "a".repeat(64),
            width = 1200,
            height = 800,
            sourceOrientation = 6,
            normalizationVersion = "photo-normalization-v1",
            photoContentVersion = 3,
            encoderModelSha256 = "b".repeat(64),
            preprocessVersion = "rgb1024-pad-v1",
            inputSchemaHash = "c".repeat(64),
            inputShape = listOf(1, 3, 1024, 1024),
        )
        val decodedAgain = SlimSamEmbeddingCacheKey.create(
            recognitionSessionId = "session-1",
            attemptId = "attempt-1",
            photoId = "photo-1",
            sourceBytesSha256 = "0".repeat(64),
            canonicalPixelSha256 = "a".repeat(64),
            width = 1200,
            height = 800,
            sourceOrientation = 6,
            normalizationVersion = "photo-normalization-v1",
            photoContentVersion = 3,
            encoderModelSha256 = "b".repeat(64),
            preprocessVersion = "rgb1024-pad-v1",
            inputSchemaHash = "c".repeat(64),
            inputShape = listOf(1, 3, 1024, 1024),
        )

        assertEquals(samePhoto, decodedAgain)
        assertFalse(samePhoto == decodedAgain.copy(canonicalPixelSha256 = "d".repeat(64)))
        assertFalse(samePhoto == decodedAgain.copy(width = 1199))
        assertFalse(samePhoto == decodedAgain.copy(encoderModelSha256 = "e".repeat(64)))
        assertFalse(samePhoto == decodedAgain.copy(preprocessVersion = "rgb1024-pad-v2"))
        assertFalse(samePhoto == decodedAgain.copy(inputSchemaHash = "f".repeat(64)))
        assertFalse(samePhoto == decodedAgain.copy(inputShape = listOf(1, 3, 512, 512)))
        assertFalse(samePhoto == decodedAgain.copy(attemptId = "another-attempt"))
        assertFalse(samePhoto == decodedAgain.copy(recognitionSessionId = "another-session"))
        assertFalse(samePhoto == decodedAgain.copy(photoId = "another-photo-id"))
        assertFalse(samePhoto == decodedAgain.copy(sourceBytesSha256 = "1".repeat(64)))
        assertFalse(samePhoto == decodedAgain.copy(sourceOrientation = 1))
        assertFalse(samePhoto == decodedAgain.copy(normalizationVersion = "photo-normalization-v2"))
        assertFalse(samePhoto == decodedAgain.copy(photoContentVersion = 4))
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

    @Test
    fun embeddingLeaseRetainsValueUntilDecoderReleasesIt() {
        val cache = SlimSamEmbeddingCache<String>()
        val oldPhoto = key("photo-old")
        val newPhoto = key("photo-new")
        cache.activate(oldPhoto)
        assertTrue(cache.putIfActive(oldPhoto, "old embedding"))
        val lease = cache.lease(oldPhoto, requireNotNull(cache.get(oldPhoto)))

        cache.activate(newPhoto)

        assertEquals("old embedding", lease.value)
        assertEquals(1, cache.activeLeases())
        lease.close()
        assertEquals(0, cache.activeLeases())
        assertNull(cache.get(oldPhoto))
    }

    @Test
    fun samePhotoRequestsShareOneInFlightEmbeddingComputation() = runBlocking {
        val singleFlight = SlimSamEmbeddingSingleFlight<String, String>()
        val computeCount = AtomicInteger()
        val computationStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()

        val producer = async(Dispatchers.Default) {
            singleFlight.run("photo-a") {
                computeCount.incrementAndGet()
                computationStarted.complete(Unit)
                allowCompletion.await()
                "embedding-a"
            }
        }
        computationStarted.await()
        val waiter = async(Dispatchers.Default) {
            singleFlight.run("photo-a") {
                computeCount.incrementAndGet()
                "duplicate-embedding"
            }
        }
        withTimeout(2_000) {
            while (singleFlight.waiterCount("photo-a") == 0) yield()
        }
        allowCompletion.complete(Unit)

        assertEquals("embedding-a", producer.await())
        assertEquals("embedding-a", waiter.await())
        assertEquals(1, computeCount.get())
    }

    @Test
    fun failedEmbeddingFlightIsRemovedSoAReplacementAttemptCanRetry() = runBlocking {
        val singleFlight = SlimSamEmbeddingSingleFlight<String, String>()

        val failure = runCatching {
            singleFlight.run("photo-a") { error("encoder failed") }
        }.exceptionOrNull()
        val retry = singleFlight.run("photo-a") { "embedding-retry" }

        assertTrue(failure is IllegalStateException)
        assertEquals("embedding-retry", retry)
    }

    private fun key(sha: String) = SlimSamEmbeddingCacheKey.create(
        canonicalPixelSha256 = sha,
        width = 1200,
        height = 800,
        encoderModelSha256 = "b".repeat(64),
        preprocessVersion = "rgb1024-pad-v1",
        inputSchemaHash = "c".repeat(64),
        inputShape = listOf(1, 3, 1024, 1024),
    )
}
