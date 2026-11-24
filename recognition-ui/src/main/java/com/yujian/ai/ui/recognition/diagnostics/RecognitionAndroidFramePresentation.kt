package com.yujian.ai.ui.recognition.diagnostics

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceControl
import android.view.View
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class RecognitionPlatformFrameSignal(
    val frameSequence: Long,
    val frameCommittedAtUptimeMs: Long,
    val presentFenceSignalTimeNanos: Long,
    val observedPresentedAtUptimeMs: Long,
)

/**
 * Observes the Android root surface transaction. A frame is marked PRESENTED only after its
 * SurfaceFlinger present fence signals; draw callbacks and Choreographer are never promoted to it.
 */
object RecognitionAndroidFramePresentation {
    private val frameSequence = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun requestNextFrame(
        view: View,
        timeoutMs: Long = 1_500L,
        onFrameCommitted: (frameSequence: Long, committedAtUptimeMs: Long) -> Unit,
        onFramePresented: (RecognitionPlatformFrameSignal) -> Unit,
        onPresentationUnavailable: (frameSequence: Long, reason: String) -> Unit,
    ) {
        require(timeoutMs > 0L)
        val sequence = frameSequence.incrementAndGet()
        if (Build.VERSION.SDK_INT >= 35) {
            Api35.request(view, sequence, timeoutMs, onFrameCommitted, onFramePresented, onPresentationUnavailable)
        } else if (Build.VERSION.SDK_INT >= 29) {
            val observer = view.viewTreeObserver
            if (!observer.isAlive || !view.isAttachedToWindow) {
                onPresentationUnavailable(sequence, "ROOT_VIEW_NOT_ATTACHED")
                return
            }
            observer.registerFrameCommitCallback {
                val committedAt = SystemClock.uptimeMillis()
                onFrameCommitted(sequence, committedAt)
                onPresentationUnavailable(sequence, "SURFACEFLINGER_PRESENT_FENCE_UNAVAILABLE_API_${Build.VERSION.SDK_INT}")
            }
        } else {
            onPresentationUnavailable(sequence, "PLATFORM_FRAME_COMMIT_UNAVAILABLE_API_${Build.VERSION.SDK_INT}")
        }
    }

    private object Api35 {
        fun request(
            view: View,
            sequence: Long,
            timeoutMs: Long,
            onFrameCommitted: (Long, Long) -> Unit,
            onFramePresented: (RecognitionPlatformFrameSignal) -> Unit,
            onFailure: (Long, String) -> Unit,
        ) {
            val rootSurface = view.rootSurfaceControl
            if (rootSurface == null || !view.isAttachedToWindow) {
                onFailure(sequence, "ROOT_SURFACE_CONTROL_UNAVAILABLE")
                return
            }
            val transaction = SurfaceControl.Transaction()
            val transactionCompleted = AtomicBoolean(false)
            val fenceCompleted = AtomicBoolean(false)
            val commitTimeout = Runnable {
                if (transactionCompleted.compareAndSet(false, true)) {
                    runCatching { transaction.close() }
                    onFailure(sequence, "SURFACE_CONTROL_COMMIT_TIMEOUT")
                }
            }
            mainHandler.postDelayed(commitTimeout, timeoutMs)
            transaction.addTransactionCompletedListener(view.context.mainExecutor) { stats ->
                if (!transactionCompleted.compareAndSet(false, true)) {
                    runCatching { transaction.close() }
                    return@addTransactionCompletedListener
                }
                mainHandler.removeCallbacks(commitTimeout)
                val committedAt = SystemClock.uptimeMillis()
                runCatching { onFrameCommitted(sequence, committedAt) }
                val fence = runCatching { stats.presentFence }.getOrNull()
                runCatching { transaction.close() }
                if (fence == null || !fence.isValid) {
                    runCatching { fence?.close() }
                    onFailure(sequence, "SURFACEFLINGER_PRESENT_FENCE_INVALID")
                    return@addTransactionCompletedListener
                }
                Thread({
                    try {
                        val signaled = fence.await(Duration.ofMillis(timeoutMs))
                        val signalTime = fence.signalTime
                        if (signaled && fence.isValid &&
                            signalTime != android.hardware.SyncFence.SIGNAL_TIME_INVALID &&
                            signalTime != android.hardware.SyncFence.SIGNAL_TIME_PENDING &&
                            fenceCompleted.compareAndSet(false, true)
                        ) {
                            val observation = RecognitionPlatformFrameSignal(
                                frameSequence = sequence,
                                frameCommittedAtUptimeMs = committedAt,
                                presentFenceSignalTimeNanos = signalTime,
                                observedPresentedAtUptimeMs = SystemClock.uptimeMillis(),
                            )
                            view.post { onFramePresented(observation) }
                        } else if (fenceCompleted.compareAndSet(false, true)) {
                            view.post { onFailure(sequence, "SURFACEFLINGER_PRESENT_FENCE_TIMEOUT") }
                        }
                    } catch (error: Throwable) {
                        if (fenceCompleted.compareAndSet(false, true)) {
                            view.post { onFailure(sequence, "SURFACEFLINGER_PRESENT_FENCE_ERROR:${error.javaClass.simpleName}") }
                        }
                    } finally {
                        runCatching { fence.close() }
                    }
                }, "recognition-present-fence-$sequence").apply { isDaemon = true }.start()
            }
            if (!rootSurface.applyTransactionOnDraw(transaction)) {
                if (transactionCompleted.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(commitTimeout)
                    runCatching { transaction.close() }
                    onFailure(sequence, "ROOT_SURFACE_REJECTED_TRANSACTION")
                }
            }
        }
    }
}
