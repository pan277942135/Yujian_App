package com.yujian.ai.ui.recognition.diagnostics

import android.os.SystemClock
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded asynchronous append-only writer. Critical and sampling traffic have separate budgets. */
class RecognitionEvidenceEventSink internal constructor(private val attemptDirectory: File) {
    private data class Queued(val sequence: Long, val timestampMs: Long, val event: RecognitionEvidenceEvent)

    private val lock = Any()
    private val criticalQueue = ArrayDeque<Queued>()
    private val sampleQueue = ArrayDeque<Queued>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yujian-recognition-evidence").apply { isDaemon = true }
    }
    private var draining = false
    private var nextSequence = 0L
    private var eventCount = 0L
    private var droppedCritical = 0L
    private var droppedSamples = 0L
    private var writeFailures = 0L
    private val incompleteReasons = linkedSetOf<String>()

    fun record(event: RecognitionEvidenceEvent): Boolean {
        val accepted = synchronized(lock) {
            val row = Queued(++nextSequence, SystemClock.uptimeMillis(), event)
            val queue = if (event.priority == RecognitionEvidencePriority.CRITICAL) criticalQueue else sampleQueue
            val capacity = if (event.priority == RecognitionEvidencePriority.CRITICAL) MAX_CRITICAL else MAX_SAMPLES
            val enqueued = if (queue.size >= capacity) {
                if (event.priority == RecognitionEvidencePriority.CRITICAL) {
                    droppedCritical++
                    incompleteReasons += "CRITICAL_EVENT_QUEUE_OVERFLOW"
                } else {
                    droppedSamples++
                    incompleteReasons += "SAMPLE_EVENT_QUEUE_OVERFLOW"
                }
                false
            } else {
                queue.addLast(row)
                true
            }
            if (!draining && (criticalQueue.isNotEmpty() || sampleQueue.isNotEmpty())) {
                draining = true
                executor.execute(::drain)
            }
            enqueued
        }
        return accepted
    }

    fun markIncomplete(reason: String) {
        synchronized(lock) { incompleteReasons += reason }
    }

    fun diagnostics(): RecognitionEvidenceQueueDiagnostics = synchronized(lock) {
        RecognitionEvidenceQueueDiagnostics(
            droppedCriticalEvents = droppedCritical,
            droppedSampleEvents = droppedSamples,
            writeFailures = writeFailures,
            incompleteReasons = incompleteReasons.toList(),
            eventCount = eventCount,
        )
    }

    fun flush(timeoutMs: Long = 10_000L): Boolean = runCatching {
        executor.submit { drain() }.get(timeoutMs, TimeUnit.MILLISECONDS)
        true
    }.getOrElse {
        markIncomplete("EVENT_FLUSH_FAILED:${it.javaClass.simpleName}")
        false
    }

    fun dispose() {
        executor.shutdown()
    }

    private fun drain() {
        while (true) {
            val queued = synchronized(lock) {
                when {
                    criticalQueue.isNotEmpty() -> criticalQueue.removeFirst()
                    sampleQueue.isNotEmpty() -> sampleQueue.removeFirst()
                    else -> {
                        draining = false
                        null
                    }
                }
            } ?: return
            val target = File(File(attemptDirectory, "logs"), queued.event.fileName)
            try {
                target.parentFile?.mkdirs()
                target.appendText(queued.event.json(queued.sequence, queued.timestampMs) + "\n", Charsets.UTF_8)
                synchronized(lock) { eventCount++ }
            } catch (failure: Throwable) {
                synchronized(lock) {
                    writeFailures++
                    incompleteReasons += "EVENT_WRITE_FAILED:${failure.javaClass.simpleName}"
                }
            }
        }
    }

    private companion object {
        const val MAX_CRITICAL = 2_048
        const val MAX_SAMPLES = 2_048
    }
}
