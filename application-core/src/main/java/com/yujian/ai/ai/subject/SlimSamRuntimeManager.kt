package com.yujian.ai.ai.subject

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionModelIdentity
import com.yujian.ai.model.SelectedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class SlimSamRuntimeState {
    NOT_STARTED,
    ASSETS_VERIFYING,
    ORT_INITIALIZING,
    VISION_LOADING,
    DECODER_LOADING,
    READY,
    DEGRADED,
    FAILED,
}

data class SlimSamRuntimeSnapshot(
    val state: SlimSamRuntimeState = SlimSamRuntimeState.NOT_STARTED,
    val attempt: Int = 0,
    val stage: String = "NOT_STARTED",
    val visionSessionReady: Boolean = false,
    val decoderSessionReady: Boolean = false,
    val visionModelSha256: String? = null,
    val decoderModelSha256: String? = null,
    val visionTensorMetadata: String? = null,
    val decoderTensorMetadata: String? = null,
    val intraOpThreads: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val exceptionClass: String? = null,
    val rootCause: String? = null,
    val tensorMetadata: String? = null,
    val updatedUptimeNs: Long = 0L,
)

data class SlimSamRuntimeEvent(
    val recognitionSessionId: String = "",
    val recognitionAttemptId: String = "",
    val imageId: String = "",
    val photoSha256: String = "",
    val modelVersion: String = SlimSamSegmenter.MODEL_VERSION,
    val modelSha256: String? = null,
    val ortVersion: String = SlimSamSegmenter.ORT_VERSION,
    val runOptionsId: String? = null,
    val activeNativeRunCount: Int? = null,
    val nodeName: String? = null,
    val executionBoundary: String? = null,
    val knownTensorInputBytes: Long? = null,
    val androidNativeHeapAllocatedBytes: Long? = null,
    val androidNativeHeapSizeBytes: Long? = null,
    val androidNativeHeapFreeBytes: Long? = null,
    val androidProcessPssKb: Long? = null,
    val androidProcessRssKb: Long? = null,
    val androidProcessVmHwmKb: Long? = null,
    val androidProcessRssAnonKb: Long? = null,
    val androidProcessVmSwapKb: Long? = null,
    val androidSystemMemAvailableKb: Long? = null,
    val androidSystemSwapFreeKb: Long? = null,
    val androidMemoryPsiSome: String? = null,
    val androidMemoryPsiFull: String? = null,
    val androidJavaHeapUsedBytes: Long? = null,
    val androidJavaHeapMaxBytes: Long? = null,
    val ortArenaReservedBytes: Long? = null,
    val memorySampleUptimeNs: Long? = null,
    val stageName: String,
    val eventName: String,
    val startUptimeNs: Long,
    val endUptimeNs: Long,
    val durationMs: Double,
    val threadName: String,
    val result: String,
    val errorCode: String = "",
    val errorMessage: String? = null,
    val exceptionClass: String? = null,
    val rootCause: String? = null,
    val tensorMetadata: String? = null,
)

/**
 * Application-process owner for the two SlimSAM ONNX Sessions and the single active photo embedding.
 * Construction is cheap; all model I/O and ONNX initialization starts only from [startPreload].
 */
class SlimSamRuntimeManager(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val segmenterFactory: ((Context, (SlimSamRuntimeEvent) -> Unit) -> SlimSamSegmenter)? = null,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val operationMutex = Mutex()
    private val activeOperations = AtomicInteger(0)
    private val preloadLock = Any()
    private var preloadJob: Job? = null
    @Volatile private var segmenter: SlimSamSegmenter? = null
    @Volatile private var applicationCreateStartedUptimeNs: Long? = null
    private val appFirstFrameRecorded = AtomicBoolean(false)
    private val events = CopyOnWriteArrayList<SlimSamRuntimeEvent>()
    private val eventLock = Any()
    private val mutableState = MutableStateFlow(SlimSamRuntimeSnapshot())
    val state: StateFlow<SlimSamRuntimeSnapshot> = mutableState.asStateFlow()

    private val initializationGate = SlimSamInitializationGate { attempt -> initialize(attempt) }

    /** Safe to call repeatedly from Application startup and an early recognition request. */
    fun startPreload(applicationCreateStartUptimeNs: Long? = null) {
        applicationCreateStartUptimeNs?.let { started ->
            this.applicationCreateStartedUptimeNs = started
            val ended = SystemClock.elapsedRealtimeNanos()
            record("APP_STARTUP", "APP_CREATE_START", started, started, "STARTED")
            record("APP_STARTUP", "APP_CREATE_END", started, ended, "PRELOAD_SCHEDULED")
        }
        synchronized(preloadLock) {
            if (mutableState.value.state == SlimSamRuntimeState.READY || preloadJob?.isActive == true) return
            if (mutableState.value.state == SlimSamRuntimeState.FAILED) return
            preloadJob = scope.launch(dispatcher) {
                try {
                    initializationGate.ensureReady()
                    if (mutableState.value.state != SlimSamRuntimeState.READY) {
                        updateState(SlimSamRuntimeState.READY, "RUNTIME_READY")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val attempts = initializationGate.attemptCount
                    val failure = error.slimSamFailureDetails()
                    updateState(
                        state = SlimSamRuntimeState.FAILED,
                        stage = failure.stage,
                        attempt = attempts,
                        errorCode = failure.errorCode,
                        errorMessage = error.message ?: error::class.java.simpleName,
                        exceptionClass = failure.exceptionClass,
                        rootCause = failure.rootCause,
                        tensorMetadata = failure.tensorMetadata,
                    )
                    record(
                        stage = failure.stage,
                        event = "SLIMSAM_RUNTIME_FAILED",
                        startNs = mutableState.value.updatedUptimeNs,
                        endNs = SystemClock.elapsedRealtimeNanos(),
                        result = "FAILED",
                        errorCode = failure.errorCode,
                        errorMessage = error.message ?: error::class.java.simpleName,
                        exceptionClass = failure.exceptionClass,
                        rootCause = failure.rootCause,
                        tensorMetadata = failure.tensorMetadata,
                    )
                }
            }
        }
    }

    /** Called by MainActivity's first pre-draw; records app-start-to-first-frame on a monotonic clock. */
    fun recordAppFirstFrame() {
        if (!appFirstFrameRecorded.compareAndSet(false, true)) return
        val start = applicationCreateStartedUptimeNs ?: return
        val end = SystemClock.elapsedRealtimeNanos()
        record("APP_STARTUP", "APP_FIRST_FRAME", start, end, "DRAWN")
    }

    suspend fun awaitReady() {
        startPreload()
        val job = synchronized(preloadLock) { preloadJob }
        job?.join()
        val snapshot = mutableState.value
        if (snapshot.state != SlimSamRuntimeState.READY) {
            throw SlimSamModelException(
                snapshot.errorCode ?: "SLIMSAM_RUNTIME_NOT_READY",
                snapshot.errorMessage ?: "SlimSAM ONNX Sessions are not ready (${snapshot.state})",
                snapshot.stage,
            )
        }
    }

    suspend fun prepare(selected: SelectedImage, identity: RecognitionModelIdentity? = null) {
        awaitReady()
        activeOperations.incrementAndGet()
        val waitStartedNs = SystemClock.elapsedRealtimeNanos()
        var mutexAcquired = false
        recordPhoto(identity, "EMBEDDING_TASK_WAIT", "EMBEDDING_TASK_WAIT_START", waitStartedNs, waitStartedNs, "STARTED")
        recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_WAIT_START", waitStartedNs, waitStartedNs, "WAITING")
        try {
            operationMutex.withLock {
                mutexAcquired = true
                val acquiredNs = SystemClock.elapsedRealtimeNanos()
                recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_ACQUIRED", waitStartedNs, acquiredNs, "ACQUIRED")
                recordPhoto(identity, "EMBEDDING_TASK_WAIT", "EMBEDDING_TASK_WAIT_END", waitStartedNs, acquiredNs, "READY_TO_RUN")
                recordPhoto(identity, "PHOTO_SELECTED", "PHOTO_SELECTED", acquiredNs, acquiredNs, "STABLE")
                withContext(Dispatchers.Default) {
                    requireNotNull(segmenter).prepareImageEmbedding(
                        source = selected.bitmap,
                        photoSha256 = selected.photoSha256,
                        identity = identity,
                    )
                }
            }
        } finally {
            val releasedNs = SystemClock.elapsedRealtimeNanos()
            recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_RELEASED", releasedNs, releasedNs,
                if (mutexAcquired) "RELEASED" else "CANCELLED_BEFORE_ACQUIRE")
            activeOperations.decrementAndGet()
        }
    }

    suspend fun generate(
        selected: SelectedImage,
        detectorBbox: NormalizedFishBox,
        identity: RecognitionModelIdentity? = null,
        detectorConfidence: Float? = null,
    ): FishSubjectResult {
        awaitReady()
        activeOperations.incrementAndGet()
        val bboxWaitStartedNs = SystemClock.elapsedRealtimeNanos()
        var mutexAcquired = false
        recordPhoto(identity, "BBOX_WAIT", "BBOX_WAIT_START", bboxWaitStartedNs, bboxWaitStartedNs, "WAITING_FOR_EMBEDDING_SLOT")
        recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_WAIT_START", bboxWaitStartedNs, bboxWaitStartedNs, "WAITING")
        try {
            return operationMutex.withLock {
                mutexAcquired = true
                val bboxReadyNs = SystemClock.elapsedRealtimeNanos()
                recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_ACQUIRED", bboxWaitStartedNs, bboxReadyNs, "ACQUIRED")
                recordPhoto(identity, "BBOX_WAIT", "BBOX_WAIT_END", bboxWaitStartedNs, bboxReadyNs, "EMBEDDING_SLOT_READY")
                withContext(Dispatchers.Default) {
                    requireNotNull(segmenter).segment(
                        source = selected.bitmap,
                        detectorBbox = detectorBbox,
                        sessionId = identity?.sessionId.orEmpty(),
                        attemptId = identity?.attemptId.orEmpty(),
                        imageId = selected.imageId,
                        photoSha256 = selected.photoSha256,
                        identity = identity,
                        detectorConfidence = detectorConfidence,
                    )
                }
            }
        } finally {
            val releasedNs = SystemClock.elapsedRealtimeNanos()
            recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_RELEASED", releasedNs, releasedNs,
                if (mutexAcquired) "RELEASED" else "CANCELLED_BEFORE_ACQUIRE")
            activeOperations.decrementAndGet()
        }
    }

    suspend fun generate(
        source: android.graphics.Bitmap,
        detectorBbox: NormalizedFishBox,
        photoSha256: String = "",
        identity: RecognitionModelIdentity? = null,
    ): FishSubjectResult {
        awaitReady()
        activeOperations.incrementAndGet()
        var mutexAcquired = false
        try {
            return operationMutex.withLock {
                mutexAcquired = true
                val acquiredNs = SystemClock.elapsedRealtimeNanos()
                recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_ACQUIRED", acquiredNs, acquiredNs, "ACQUIRED")
                withContext(Dispatchers.Default) {
                    requireNotNull(segmenter).segment(
                        source = source,
                        detectorBbox = detectorBbox,
                        photoSha256 = photoSha256,
                        identity = identity,
                    )
                }
            }
        } finally {
            val releasedNs = SystemClock.elapsedRealtimeNanos()
            recordPhoto(identity, "RUNTIME_MUTEX", "MUTEX_RELEASED", releasedNs, releasedNs,
                if (mutexAcquired) "RELEASED" else "CANCELLED_BEFORE_ACQUIRE")
            activeOperations.decrementAndGet()
        }
    }

    fun metricsSnapshot(): SlimSamRuntimeMetrics {
        val stats = segmenter?.runtimeStats()
        return SlimSamRuntimeMetrics(
            visionSessionCount = if (stats?.visionSessionReady == true) 1 else 0,
            decoderSessionCount = if (stats?.decoderSessionReady == true) 1 else 0,
            activeOperationCount = activeOperations.get(),
            embeddingBufferBytes = stats?.embeddingBufferBytes ?: 0L,
            cachedPhotoCount = stats?.cachedPhotoCount ?: 0,
            intraOpThreads = stats?.intraOpThreads,
            activeNativeRunCount = stats?.activeNativeRunCount ?: 0,
            nativeSessionPoisoned = stats?.nativeSessionPoisoned ?: false,
        )
    }

    fun recordPhotoStage(
        identity: RecognitionModelIdentity,
        stage: String,
        event: String,
        startUptimeNs: Long,
        endUptimeNs: Long,
        result: String = "PASS",
        errorCode: String = "",
    ) {
        recordPhoto(identity, stage, event, startUptimeNs, endUptimeNs, result, errorCode)
    }

    /** Drops only the inactive photo embedding; process Sessions remain resident. */
    fun onTrimMemory(level: Int) {
        if (level != android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL &&
            level != android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) return
        scope.launch(dispatcher) {
            operationMutex.withLock { segmenter?.clearEmbedding("MEMORY_PRESSURE") }
        }
    }

    fun snapshotEvents(): List<SlimSamRuntimeEvent> = events.toList()

    suspend fun closeForProcessTermination() {
        val job = synchronized(preloadLock) { preloadJob }
        job?.cancel()
        job?.join()
        operationMutex.withLock {
            segmenter?.close()
            segmenter = null
        }
        scope.cancel()
    }

    private suspend fun initialize(attempt: Int) {
        var currentStage = "MODEL_VERIFY"
        updateState(SlimSamRuntimeState.ASSETS_VERIFYING, currentStage, attempt)
        try {
            val worker = segmenter ?: createSegmenter().also { segmenter = it }
            timedStage("MODEL_VERIFY", attempt) { worker.verifyModelAssets() }

            currentStage = "ORT_INITIALIZATION"
            updateState(SlimSamRuntimeState.ORT_INITIALIZING, "ORT_INITIALIZATION", attempt)
            timedStage("ORT_INITIALIZATION", attempt) { worker.initializeOrtEnvironment() }

            currentStage = "VISION_SESSION_CREATE"
            updateState(SlimSamRuntimeState.VISION_LOADING, "VISION_SESSION_CREATE", attempt)
            timedStage("VISION_SESSION_CREATE", attempt) { worker.preloadVisionSession() }

            currentStage = "DECODER_SESSION_CREATE"
            updateState(SlimSamRuntimeState.DECODER_LOADING, "DECODER_SESSION_CREATE", attempt)
            timedStage("DECODER_SESSION_CREATE", attempt) { worker.preloadDecoderSession() }

            val stats = worker.runtimeStats()
            check(SlimSamSessionReadiness.isReady(stats.visionSessionReady, stats.decoderSessionReady)) {
                "Session metadata validation completed without both validated Sessions READY"
            }
            updateState(
                state = SlimSamRuntimeState.READY,
                stage = "RUNTIME_READY",
                attempt = attempt,
                visionSessionReady = stats.visionSessionReady,
                decoderSessionReady = stats.decoderSessionReady,
                visionModelSha256 = stats.visionModelSha256,
                decoderModelSha256 = stats.decoderModelSha256,
                visionTensorMetadata = stats.visionTensorMetadata,
                decoderTensorMetadata = stats.decoderTensorMetadata,
                intraOpThreads = stats.intraOpThreads,
            )
            record(
                "RUNTIME_INITIALIZATION",
                "SLIMSAM_RUNTIME_READY",
                mutableState.value.updatedUptimeNs,
                SystemClock.elapsedRealtimeNanos(),
                "READY",
                tensorMetadata = "vision=${stats.visionTensorMetadata.orEmpty()}; decoder=${stats.decoderTensorMetadata.orEmpty()}",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failure = error.slimSamFailureDetails()
            val errorStage = failure.stage.takeUnless { it == "UNKNOWN" } ?: currentStage
            updateState(
                state = SlimSamRuntimeState.DEGRADED,
                stage = errorStage,
                attempt = attempt,
                visionSessionReady = segmenter?.runtimeStats()?.visionSessionReady == true,
                decoderSessionReady = segmenter?.runtimeStats()?.decoderSessionReady == true,
                errorCode = failure.errorCode,
                errorMessage = error.message ?: error::class.java.simpleName,
                exceptionClass = failure.exceptionClass,
                rootCause = failure.rootCause,
                tensorMetadata = failure.tensorMetadata,
            )
            record(
                stage = errorStage,
                event = "SLIMSAM_INITIALIZATION_ATTEMPT_FAILED",
                startNs = mutableState.value.updatedUptimeNs,
                endNs = SystemClock.elapsedRealtimeNanos(),
                result = "FAILED",
                errorCode = failure.errorCode,
                errorMessage = error.message ?: error::class.java.simpleName,
                exceptionClass = failure.exceptionClass,
                rootCause = failure.rootCause,
                tensorMetadata = failure.tensorMetadata,
            )
            throw error
        }
    }

    private suspend fun timedStage(stage: String, attempt: Int, block: suspend () -> Unit) {
        val start = SystemClock.elapsedRealtimeNanos()
        record(stage, "${stage}_START", start, start, "STARTED")
        try {
            operationMutex.withLock { block() }
            val end = SystemClock.elapsedRealtimeNanos()
            record(stage, "${stage}_END", start, end, "PASS")
        } catch (error: Throwable) {
            val end = SystemClock.elapsedRealtimeNanos()
            val failure = error.slimSamFailureDetails()
            record(
                stage,
                "${stage}_FAIL",
                start,
                end,
                "FAILED",
                errorCode = failure.errorCode,
                errorMessage = error.message ?: error::class.java.simpleName,
                exceptionClass = failure.exceptionClass,
                rootCause = failure.rootCause,
                tensorMetadata = failure.tensorMetadata,
            )
            throw error
        }
        updateState(mutableState.value.state, stage, attempt)
    }

    private fun createSegmenter() = (segmenterFactory ?: { app, sink -> SlimSamSegmenter(app, eventSink = sink) })
        .invoke(appContext, ::recordEvent)

    private fun updateState(
        state: SlimSamRuntimeState,
        stage: String,
        attempt: Int = mutableState.value.attempt,
        visionSessionReady: Boolean = mutableState.value.visionSessionReady,
        decoderSessionReady: Boolean = mutableState.value.decoderSessionReady,
        visionModelSha256: String? = mutableState.value.visionModelSha256,
        decoderModelSha256: String? = mutableState.value.decoderModelSha256,
        visionTensorMetadata: String? = mutableState.value.visionTensorMetadata,
        decoderTensorMetadata: String? = mutableState.value.decoderTensorMetadata,
        intraOpThreads: Int? = mutableState.value.intraOpThreads,
        errorCode: String? = null,
        errorMessage: String? = null,
        exceptionClass: String? = null,
        rootCause: String? = null,
        tensorMetadata: String? = null,
    ) {
        mutableState.value = SlimSamRuntimeSnapshot(
            state = state,
            attempt = attempt,
            stage = stage,
            visionSessionReady = visionSessionReady,
            decoderSessionReady = decoderSessionReady,
            visionModelSha256 = visionModelSha256,
            decoderModelSha256 = decoderModelSha256,
            visionTensorMetadata = visionTensorMetadata,
            decoderTensorMetadata = decoderTensorMetadata,
            intraOpThreads = intraOpThreads,
            errorCode = errorCode,
            errorMessage = errorMessage,
            exceptionClass = exceptionClass,
            rootCause = rootCause,
            tensorMetadata = tensorMetadata,
            updatedUptimeNs = SystemClock.elapsedRealtimeNanos(),
        )
    }

    private fun recordEvent(event: SlimSamRuntimeEvent) {
        synchronized(eventLock) {
            events += event
            while (events.size > MAX_RETAINED_EVENTS) events.removeAt(0)
        }
        Log.i("SlimSamTiming", event.toLogLine())
    }

    private fun recordPhoto(
        identity: RecognitionModelIdentity?,
        stage: String,
        event: String,
        startNs: Long,
        endNs: Long,
        result: String,
        errorCode: String = "",
    ) = recordEvent(
        SlimSamRuntimeEvent(
            recognitionSessionId = identity?.sessionId.orEmpty(),
            recognitionAttemptId = identity?.attemptId.orEmpty(),
            imageId = identity?.imageId.orEmpty(),
            photoSha256 = identity?.photoSha256.orEmpty(),
            stageName = stage,
            eventName = event,
            startUptimeNs = startNs,
            endUptimeNs = endNs,
            durationMs = (endNs - startNs).coerceAtLeast(0L) / 1_000_000.0,
            threadName = Thread.currentThread().name,
            result = result,
            errorCode = errorCode,
        ),
    )

    private fun record(
        stage: String,
        event: String,
        startNs: Long,
        endNs: Long,
        result: String,
        errorCode: String = "",
        errorMessage: String? = null,
        exceptionClass: String? = null,
        rootCause: String? = null,
        tensorMetadata: String? = null,
    ) = recordEvent(
        SlimSamRuntimeEvent(
            stageName = stage,
            eventName = event,
            startUptimeNs = startNs,
            endUptimeNs = endNs,
            durationMs = ((endNs - startNs).coerceAtLeast(0L)) / 1_000_000.0,
            threadName = Thread.currentThread().name,
            result = result,
            errorCode = errorCode,
            errorMessage = errorMessage,
            exceptionClass = exceptionClass,
            rootCause = rootCause,
            tensorMetadata = tensorMetadata,
        ),
    )

    private fun SlimSamRuntimeEvent.toLogLine(): String = buildString {
        append("event=$eventName stage=$stageName model=$modelVersion")
        append(" modelSha256=$modelSha256 ortVersion=$ortVersion runOptionsId=$runOptionsId")
        append(" recognitionSessionId=$recognitionSessionId recognitionAttemptId=$recognitionAttemptId")
        append(" imageId=$imageId photoSha256=$photoSha256")
        append(" startUptimeNs=$startUptimeNs endUptimeNs=$endUptimeNs durationMs=$durationMs")
        append(" activeNativeRunCount=$activeNativeRunCount")
        append(" nativeHeapAllocatedBytes=$androidNativeHeapAllocatedBytes nativeHeapSizeBytes=$androidNativeHeapSizeBytes")
        append(" nativeHeapFreeBytes=$androidNativeHeapFreeBytes processPssKb=$androidProcessPssKb processRssKb=$androidProcessRssKb")
        append(" processVmHwmKb=$androidProcessVmHwmKb processRssAnonKb=$androidProcessRssAnonKb processVmSwapKb=$androidProcessVmSwapKb")
        append(" systemMemAvailableKb=$androidSystemMemAvailableKb systemSwapFreeKb=$androidSystemSwapFreeKb")
        append(" javaHeapUsedBytes=$androidJavaHeapUsedBytes javaHeapMaxBytes=$androidJavaHeapMaxBytes")
        append(" memoryPsiSome=$androidMemoryPsiSome memoryPsiFull=$androidMemoryPsiFull")
        append(" ortArenaReservedBytes=$ortArenaReservedBytes nodeName=$nodeName executionBoundary=$executionBoundary knownTensorInputBytes=$knownTensorInputBytes")
        append(" memorySampleUptimeNs=$memorySampleUptimeNs")
        append(" threadName=$threadName result=$result errorCode=$errorCode")
        errorMessage?.let { append(" errorMessage=${it.take(512)}") }
        exceptionClass?.let { append(" exceptionClass=$it") }
        rootCause?.let { append(" rootCause=${it.take(1024)}") }
        tensorMetadata?.let { append(" tensorMetadata=${it.take(1024)}") }
    }

    private companion object {
        const val MAX_RETAINED_EVENTS = 4096
    }
}

internal data class SlimSamRuntimeStats(
    val visionSessionReady: Boolean,
    val decoderSessionReady: Boolean,
    val visionModelSha256: String?,
    val decoderModelSha256: String?,
    val visionTensorMetadata: String?,
    val decoderTensorMetadata: String?,
    val intraOpThreads: Int,
    val embeddingBufferBytes: Long,
    val cachedPhotoCount: Int,
    val activeNativeRunCount: Int,
    val nativeSessionPoisoned: Boolean,
)

data class SlimSamRuntimeMetrics(
    val visionSessionCount: Int,
    val decoderSessionCount: Int,
    val activeOperationCount: Int,
    val embeddingBufferBytes: Long,
    val cachedPhotoCount: Int,
    val intraOpThreads: Int?,
    val activeNativeRunCount: Int,
    val nativeSessionPoisoned: Boolean,
)

class SlimSamModelException(
    val errorCode: String,
    message: String,
    val stage: String,
    cause: Throwable? = null,
    val tensorMetadata: String? = null,
) : IllegalStateException(message, cause)
