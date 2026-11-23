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
    val photoSha256: String,
    val width: Int,
    val height: Int,
    val modelVersion: String,
    val preprocessingVersion: String,
) {
    companion object {
        fun create(
            photoSha256: String,
            width: Int,
            height: Int,
            modelVersion: String,
            preprocessingVersion: String,
        ) = SlimSamEmbeddingCacheKey(
            photoSha256 = photoSha256,
            width = width,
            height = height,
            modelVersion = modelVersion,
            preprocessingVersion = preprocessingVersion,
        )
    }
}

/** Keeps one active embedding and rejects a late write from a replaced photo/attempt. */
internal class SlimSamEmbeddingCache<T> {
    private var activeKey: SlimSamEmbeddingCacheKey? = null
    private var cachedKey: SlimSamEmbeddingCacheKey? = null
    private var cachedValue: T? = null

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
}
