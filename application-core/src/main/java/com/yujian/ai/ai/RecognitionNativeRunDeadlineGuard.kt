package com.yujian.ai.ai

import ai.onnxruntime.OrtSession
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Cancellation of one synchronous native invocation when its shared Attempt deadline expires. */
internal class RecognitionNativeRunDeadlineGuard(
    private val deadline: RecognitionAttemptDeadline,
    private val nowUptimeMs: () -> Long,
    private val terminateNativeRun: () -> Unit,
    private val schedule: (delayMs: Long, task: () -> Unit) -> RecognitionScheduledTask,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val terminationRequested = AtomicBoolean(false)
    private val terminationFailure = AtomicReference<Throwable?>(null)
    @Volatile private var scheduledTask: RecognitionScheduledTask? = null

    init {
        scheduleNextCheck()
    }

    private fun scheduleNextCheck() {
        if (closed.get() || terminationRequested.get()) return
        val remainingMs = deadline.remainingMs(nowUptimeMs())
        if (remainingMs == 0L) {
            terminateOnce()
            return
        }
        scheduledTask = schedule(remainingMs) {
            if (closed.get()) return@schedule
            if (deadline.isExpired(nowUptimeMs())) terminateOnce() else scheduleNextCheck()
        }
    }

    private fun terminateOnce() {
        if (terminationRequested.compareAndSet(false, true)) {
            runCatching(terminateNativeRun).onFailure(terminationFailure::set)
        }
    }

    fun hasExpired(): Boolean = terminationRequested.get()

    fun throwIfExpired() {
        if (!closed.get() && !terminationRequested.get() && deadline.isExpired(nowUptimeMs())) terminateOnce()
        if (terminationRequested.get()) {
            val failure = RecognitionAttemptNativeTimeoutException(
                "Recognition Attempt native inference exceeded its shared deadline",
                terminationFailure.get(),
            )
            throw failure
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) scheduledTask?.cancel()
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "recognition-native-deadline").apply { isDaemon = true }
        }

        fun create(
            deadline: RecognitionAttemptDeadline,
            terminateNativeRun: () -> Unit,
        ): RecognitionNativeRunDeadlineGuard = RecognitionNativeRunDeadlineGuard(
            deadline = deadline,
            nowUptimeMs = android.os.SystemClock::uptimeMillis,
            terminateNativeRun = terminateNativeRun,
            schedule = { delayMs, task ->
                val future: ScheduledFuture<*> = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS)
                RecognitionScheduledTask { future.cancel(false) }
            },
        )
    }
}

internal fun interface RecognitionScheduledTask {
    fun cancel()
}

internal class RecognitionAttemptNativeTimeoutException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Each ONNX RunOptions instance belongs to exactly one native run and closes after it returns. */
internal object RecognitionOrtNativeRun {
    fun <T : AutoCloseable> run(
        deadline: RecognitionAttemptDeadline?,
        invoke: (OrtSession.RunOptions) -> T,
    ): T {
        val options = OrtSession.RunOptions()
        val guard = deadline?.let {
            RecognitionNativeRunDeadlineGuard.create(it) { options.setTerminate(true) }
        }
        try {
            guard?.throwIfExpired()
            val result = try {
                invoke(options)
            } catch (error: Throwable) {
                if (guard?.hasExpired() == true) {
                    throw RecognitionAttemptNativeTimeoutException(
                        "Recognition Attempt native inference was terminated at its deadline",
                        error,
                    )
                }
                throw error
            }
            try {
                guard?.throwIfExpired()
            } catch (timeout: RecognitionAttemptNativeTimeoutException) {
                runCatching { result.close() }
                throw timeout
            }
            return result
        } finally {
            guard?.close()
            options.close()
        }
    }
}
