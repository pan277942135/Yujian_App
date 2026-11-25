package com.yujian.ai.ai.subject

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

/** Serializes process startup and first-use requests around one bounded Session initialization. */
internal class SlimSamInitializationGate(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val initialize: suspend (attempt: Int) -> Unit,
) {
    private val mutex = Mutex()
    @Volatile private var ready = false
    @Volatile private var attempts = 0
    @Volatile private var failure: Throwable? = null

    val isReady: Boolean get() = ready
    val attemptCount: Int get() = attempts

    init {
        require(maxAttempts > 0)
    }

    suspend fun ensureReady() {
        if (ready) return
        mutex.withLock {
            if (ready) return
            while (attempts < maxAttempts && !ready) {
                attempts += 1
                try {
                    initialize(attempts)
                    ready = true
                    failure = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failure = error
                }
            }
            if (!ready) {
                throw SlimSamInitializationFailedException(attempts, failure)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 2
    }
}

internal class SlimSamInitializationFailedException(
    val attempts: Int,
    cause: Throwable?,
) : IllegalStateException("SlimSAM Runtime initialization failed after $attempts attempt(s)", cause)

internal data class SlimSamFailureDetails(
    val errorCode: String,
    val stage: String,
    val exceptionClass: String,
    val rootCause: String,
    val tensorMetadata: String? = null,
)

/** Keeps the model-stage failure visible when the bounded initialization gate wraps it. */
internal fun Throwable.slimSamFailureDetails(): SlimSamFailureDetails {
    val chain = generateSequence(this as Throwable?) { it.cause }.take(16).toList()
    val modelFailure = chain.filterIsInstance<SlimSamModelException>().firstOrNull()
    val root = chain.lastOrNull() ?: this
    val rootDescription = buildString {
        append(root.javaClass.name)
        root.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it.take(1024)) }
    }
    return SlimSamFailureDetails(
        errorCode = modelFailure?.errorCode
            ?: root.javaClass.simpleName.uppercase().ifBlank { "SLIMSAM_FAILURE" },
        stage = modelFailure?.stage ?: "UNKNOWN",
        exceptionClass = modelFailure?.javaClass?.name ?: javaClass.name,
        rootCause = rootDescription,
        tensorMetadata = modelFailure?.tensorMetadata,
    )
}

/** Stable single-photo cache identity. Bitmap object identity is intentionally not part of the key. */
internal data class SlimSamEmbeddingCacheKey(
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val canonicalPixelSha256: String,
    val width: Int,
    val height: Int,
    val encoderModelSha256: String,
    val preprocessVersion: String,
    val inputSchemaHash: String,
    val inputShape: List<Int>,
) {
    companion object {
        fun create(
            recognitionSessionId: String = "UNSCOPED_PREWARM",
            attemptId: String = "UNSCOPED_PREWARM",
            photoId: String = "UNSCOPED_PREWARM",
            canonicalPixelSha256: String,
            width: Int,
            height: Int,
            encoderModelSha256: String,
            preprocessVersion: String,
            inputSchemaHash: String,
            inputShape: List<Int>,
        ) = SlimSamEmbeddingCacheKey(
            recognitionSessionId = recognitionSessionId,
            attemptId = attemptId,
            photoId = photoId,
            canonicalPixelSha256 = canonicalPixelSha256,
            width = width,
            height = height,
            encoderModelSha256 = encoderModelSha256,
            preprocessVersion = preprocessVersion,
            inputSchemaHash = inputSchemaHash,
            inputShape = java.util.Collections.unmodifiableList(ArrayList(inputShape)),
        )
    }
}

/** Keeps one active embedding and rejects a late write from a replaced photo/attempt. */
internal class SlimSamEmbeddingCache<T> {
    class Lease<T> internal constructor(val value: T, private val releaseAction: () -> Unit) : AutoCloseable {
        private val released = java.util.concurrent.atomic.AtomicBoolean(false)
        override fun close() {
            if (released.compareAndSet(false, true)) releaseAction()
        }
    }

    private var activeKey: SlimSamEmbeddingCacheKey? = null
    private var cachedKey: SlimSamEmbeddingCacheKey? = null
    private var cachedValue: T? = null
    private var activeLeaseCount: Int = 0

    @Synchronized
    fun activate(key: SlimSamEmbeddingCacheKey) {
        if (activeKey == key) return
        activeKey = key
        cachedKey = null
        cachedValue = null
    }

    @Synchronized
    fun get(key: SlimSamEmbeddingCacheKey): T? =
        cachedValue?.takeIf { activeKey == key && cachedKey == key }

    @Synchronized
    fun lease(key: SlimSamEmbeddingCacheKey, value: T): Lease<T> {
        check(activeKey == key && cachedKey == key && cachedValue == value) {
            "Embedding lease identity is no longer active"
        }
        activeLeaseCount += 1
        return Lease(value) {
            synchronized(this) { activeLeaseCount = (activeLeaseCount - 1).coerceAtLeast(0) }
        }
    }

    @Synchronized
    fun putIfActive(key: SlimSamEmbeddingCacheKey, value: T): Boolean {
        if (activeKey != key) return false
        cachedKey = key
        cachedValue = value
        return true
    }

    @Synchronized
    fun clear() {
        activeKey = null
        cachedKey = null
        cachedValue = null
    }

    @Synchronized
    fun hasValue(): Boolean = cachedValue != null

    @Synchronized
    fun activeLeases(): Int = activeLeaseCount
}
