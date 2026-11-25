package com.yujian.ai.ai.subject

import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal enum class SlimSamNativeRunState {
    CREATED,
    RUNNING,
    TERMINATE_REQUESTED,
    TERMINATION_UNCONFIRMED,
    NATIVE_NOT_STARTED,
    NATIVE_RETURNED,
    CLEANUP_COMPLETED,
    CLEANUP_FAILED,
}

internal data class SlimSamNativeRunSnapshot(
    val runOptionsId: String,
    val state: SlimSamNativeRunState,
    val terminateRequested: Boolean,
    val nativeStarted: Boolean,
    val nativeReturned: Boolean,
    val terminationUnconfirmed: Boolean,
    val cleanupCompleted: Boolean,
    val failure: String?,
)

/** Tracks one synchronous ORT run while cancellation is delivered from a separate control thread. */
internal class SlimSamNativeRunController(
    private val runOptionsId: String,
    private val terminationExecutor: Executor,
    private val watchdogScheduler: ScheduledExecutorService,
    private val terminationWaitMs: Long = DEFAULT_TERMINATION_WAIT_MS,
    private val setTerminate: () -> Unit,
    private val onEvent: (event: String, detail: String?) -> Unit = { _, _ -> },
    private val onTerminationUnconfirmed: (reason: String) -> Unit = {},
) {
    private val lock = Any()
    private val terminateRequested = AtomicBoolean(false)
    private val nativeStarted = AtomicBoolean(false)
    private val nativeReturned = AtomicBoolean(false)
    private val nativeNotStarted = AtomicBoolean(false)
    private val terminationUnconfirmed = AtomicBoolean(false)
    private val cleanupCompleted = AtomicBoolean(false)
    private val cleanupSignal = CountDownLatch(1)
    @Volatile private var state = SlimSamNativeRunState.CREATED
    @Volatile private var failure: String? = null
    @Volatile private var watchdog: ScheduledFuture<*>? = null
    private var terminateCallFinished = true
    private var deferredResourceCleanup: (() -> Unit)? = null

    init {
        require(runOptionsId.isNotBlank())
        require(terminationWaitMs > 0)
    }

    /** Returns false after the first request or if the Native call already returned. */
    fun requestTermination(): Boolean {
        val accepted = synchronized(lock) {
            if (nativeReturned.get() || nativeNotStarted.get() || !terminateRequested.compareAndSet(false, true)) {
                false
            } else {
                terminateCallFinished = false
                state = SlimSamNativeRunState.TERMINATE_REQUESTED
                true
            }
        }
        if (!accepted) return false
        emitEvent("NATIVE_CANCEL_REQUESTED", null)
        emitEvent("NATIVE_TERMINATE_REQUESTED", null)

        try {
            watchdog = watchdogScheduler.schedule(
                {
                    if (!nativeReturned.get() && !nativeNotStarted.get()) {
                        markTerminationUnconfirmed("ORT Session.run did not return within ${terminationWaitMs}ms")
                    } else if (!terminationActionHasFinished()) {
                        markTerminationUnconfirmed("RunOptions.setTerminate did not return within ${terminationWaitMs}ms")
                    }
                },
                terminationWaitMs,
                TimeUnit.MILLISECONDS,
            )
            if ((nativeReturned.get() || nativeNotStarted.get()) && terminationActionHasFinished()) {
                watchdog?.cancel(false)
            }
        } catch (error: Throwable) {
            markTerminationUnconfirmed("termination watchdog unavailable: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        }

        try {
            terminationExecutor.execute {
                if (nativeReturned.get() || nativeNotStarted.get()) {
                    onTerminationCallFinished()
                    return@execute
                }
                try {
                    setTerminate()
                    emitEvent("NATIVE_TERMINATE_CALL_RETURNED", null)
                } catch (error: Throwable) {
                    markTerminationUnconfirmed("RunOptions.setTerminate failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
                } finally {
                    onTerminationCallFinished()
                }
            }
        } catch (error: Throwable) {
            markTerminationUnconfirmed("termination control thread unavailable: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
            onTerminationCallFinished()
        }
        return true
    }

    fun nativeStarted() {
        check(nativeStarted.compareAndSet(false, true)) { "Native inference started more than once for RunOptions $runOptionsId" }
        if (!terminateRequested.get()) state = SlimSamNativeRunState.RUNNING
        emitEvent("NATIVE_RUN_STARTED", null)
    }

    /** Called only after the synchronous Session.run call has returned or thrown. */
    fun nativeReturned() {
        if (!nativeReturned.compareAndSet(false, true)) return
        if (terminationActionHasFinished()) watchdog?.cancel(false)
        state = SlimSamNativeRunState.NATIVE_RETURNED
        emitEvent("NATIVE_RUN_RETURNED", null)
        runDeferredCleanupIfReady()
    }

    /** Settles a RunOptions object when cancellation wins before Session.run starts. */
    fun nativeNotStarted() {
        if (nativeStarted.get() || !nativeNotStarted.compareAndSet(false, true)) return
        if (terminationActionHasFinished()) watchdog?.cancel(false)
        state = SlimSamNativeRunState.NATIVE_NOT_STARTED
        emitEvent("NATIVE_RUN_NOT_STARTED", null)
        runDeferredCleanupIfReady()
    }

    /** Closes RunOptions only after both Session.run and the external terminate call are finished. */
    fun finishResourceCleanup(releaseResources: () -> Unit) {
        check(nativeReturned.get() || nativeNotStarted.get()) {
            "Native resources cannot be released before Session.run returns"
        }
        val runNow = synchronized(lock) {
            check(deferredResourceCleanup == null) { "Native run cleanup registered more than once" }
            if (terminateCallFinished) true else {
                deferredResourceCleanup = releaseResources
                false
            }
        }
        if (runNow) releaseResources()
    }

    /** Called after Ort.Result, tensors and RunOptions have been closed. */
    fun cleanupCompleted() {
        check(nativeReturned.get() || nativeNotStarted.get()) { "Native resources cannot be released before Session.run returns" }
        if (!cleanupCompleted.compareAndSet(false, true)) return
        watchdog?.cancel(false)
        state = SlimSamNativeRunState.CLEANUP_COMPLETED
        emitEvent("RESOURCE_CLEANUP_END", null)
        cleanupSignal.countDown()
    }

    fun cleanupFailed(reason: String) {
        if (cleanupCompleted.get()) return
        failure = reason
        state = SlimSamNativeRunState.CLEANUP_FAILED
        emitEvent("RESOURCE_CLEANUP_FAILED", reason)
        cleanupSignal.countDown()
    }

    fun awaitCleanup(timeoutMs: Long): Boolean = cleanupSignal.await(timeoutMs, TimeUnit.MILLISECONDS) && cleanupCompleted.get()

    fun snapshot() = SlimSamNativeRunSnapshot(
        runOptionsId = runOptionsId,
        state = state,
        terminateRequested = terminateRequested.get(),
        nativeStarted = nativeStarted.get(),
        nativeReturned = nativeReturned.get(),
        terminationUnconfirmed = terminationUnconfirmed.get(),
        cleanupCompleted = cleanupCompleted.get(),
        failure = failure,
    )

    private fun markTerminationUnconfirmed(reason: String) {
        val nativeSettledAndControlFinished =
            (nativeReturned.get() || nativeNotStarted.get()) && terminationActionHasFinished()
        if (nativeSettledAndControlFinished || !terminationUnconfirmed.compareAndSet(false, true)) return
        failure = reason
        state = SlimSamNativeRunState.TERMINATION_UNCONFIRMED
        emitEvent("NATIVE_TERMINATION_UNCONFIRMED", reason)
        runCatching { onTerminationUnconfirmed(reason) }
    }

    private fun emitEvent(event: String, detail: String?) {
        runCatching { onEvent(event, detail) }
    }

    private fun onTerminationCallFinished() {
        val cleanup = synchronized(lock) {
            terminateCallFinished = true
            takeDeferredCleanupLocked()
        }
        if (nativeReturned.get() || nativeNotStarted.get()) watchdog?.cancel(false)
        cleanup?.invoke()
    }

    private fun runDeferredCleanupIfReady() {
        val cleanup = synchronized(lock) { takeDeferredCleanupLocked() }
        cleanup?.invoke()
    }

    private fun takeDeferredCleanupLocked(): (() -> Unit)? {
        if (!terminateCallFinished || (!nativeReturned.get() && !nativeNotStarted.get())) return null
        return deferredResourceCleanup.also { deferredResourceCleanup = null }
    }

    private fun terminationActionHasFinished(): Boolean = synchronized(lock) { terminateCallFinished }

    companion object {
        const val DEFAULT_TERMINATION_WAIT_MS = 5_000L
    }
}

@OptIn(InternalCoroutinesApi::class)
internal fun installSlimSamCancellationHandler(
    job: Job,
    controller: SlimSamNativeRunController,
): DisposableHandle = job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
    if (cause != null) controller.requestTermination()
}

internal data class SlimSamNativeSessionSnapshot(
    val activeRunCount: Int,
    val acceptingRuns: Boolean,
    val poisoned: Boolean,
    val closeRequested: Boolean,
    val resourcesClosed: Boolean,
)

/** Defers closing ORT Sessions and their shared SessionOptions until every Native run lease is released. */
internal class SlimSamNativeSessionGate(
    private val closeResources: () -> Unit,
) {
    private val lock = Any()
    private var activeRunCount = 0
    private var acceptingRuns = true
    private var poisoned = false
    private var closeRequested = false
    private var resourcesClosed = false

    fun acquire(): Lease? = synchronized(lock) {
        if (!acceptingRuns || closeRequested || resourcesClosed || activeRunCount != 0) return@synchronized null
        activeRunCount += 1
        Lease()
    }

    fun requestClose() {
        val shouldClose = synchronized(lock) {
            acceptingRuns = false
            closeRequested = true
            takeCloseOwnershipLocked()
        }
        if (shouldClose) closeResources()
    }

    /** A run exceeding the bounded termination wait makes its Sessions permanently non-reusable. */
    fun poison() {
        val shouldClose = synchronized(lock) {
            acceptingRuns = false
            poisoned = true
            closeRequested = true
            takeCloseOwnershipLocked()
        }
        if (shouldClose) closeResources()
    }

    fun snapshot() = synchronized(lock) {
        SlimSamNativeSessionSnapshot(
            activeRunCount = activeRunCount,
            acceptingRuns = acceptingRuns && !closeRequested && !resourcesClosed,
            poisoned = poisoned,
            closeRequested = closeRequested,
            resourcesClosed = resourcesClosed,
        )
    }

    private fun release() {
        val shouldClose = synchronized(lock) {
            check(activeRunCount > 0) { "Native run lease released more than once" }
            activeRunCount -= 1
            takeCloseOwnershipLocked()
        }
        if (shouldClose) closeResources()
    }

    private fun takeCloseOwnershipLocked(): Boolean {
        if (!closeRequested || activeRunCount != 0 || resourcesClosed) return false
        resourcesClosed = true
        return true
    }

    inner class Lease internal constructor() : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) release()
        }
    }
}

internal object SlimSamNativeRunExecutors {
    private val daemonFactory = ThreadFactory { task ->
        Thread(task, "slimsam-native-control").apply { isDaemon = true }
    }

    val termination = Executors.newCachedThreadPool(daemonFactory)
    val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemonFactory)
    val eventLogging: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "slimsam-native-events").apply { isDaemon = true }
    }
}
