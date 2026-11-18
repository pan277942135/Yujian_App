package com.yujian.ai.ui.recognition.diagnostics

import android.os.SystemClock
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded asynchronous append-only writer with one globally ordered evidence sequence. */
class RecognitionEvidenceEventSink internal constructor(
    private val attemptDirectory: File,
    private val clockMs: () -> Long = { SystemClock.uptimeMillis() },
) {
    private data class Queued(
        val sequence: Long,
        val fileName: String,
        val priority: RecognitionEvidencePriority,
        val encodedLine: (sequence: Long, timestampMs: Long) -> String,
        val timestampMs: Long,
    )

    internal data class LogSnapshotResult(
        val diagnostics: RecognitionEvidenceQueueDiagnostics,
        val copiedFiles: List<String>,
        val truncatedFiles: List<String>,
        val sizeCapReached: Boolean,
    )

    private val lock = Any()
    private val logFileLock = Any()
    private val queue = ArrayDeque<Queued>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yujian-recognition-evidence").apply { isDaemon = true }
    }
    private var draining = false
    private var disposed = false
    private var nextSequence = 0L
    private var eventCount = 0L
    private var droppedCritical = 0L
    private var droppedSamples = 0L
    private var writeFailures = 0L
    private val incompleteReasons = linkedSetOf<String>()

    fun record(event: RecognitionEvidenceEvent): Boolean = enqueue(
        fileName = event.fileName,
        priority = event.priority,
        encodedLine = { seq, ts -> event.json(seq, ts) },
    )

    internal fun recordRawLine(
        fileName: String,
        encodedJsonLine: String,
        priority: RecognitionEvidencePriority = RecognitionEvidencePriority.CRITICAL,
    ): Boolean = enqueue(
        fileName = fileName,
        priority = priority,
        encodedLine = { _, _ -> encodedJsonLine },
    )

    private fun enqueue(
        fileName: String,
        priority: RecognitionEvidencePriority,
        encodedLine: (sequence: Long, timestampMs: Long) -> String,
    ): Boolean {
        val accepted = synchronized(lock) {
            if (disposed) return false
            val row = Queued(++nextSequence, fileName, priority, encodedLine, clockMs())
            var enqueued = true
            if (queue.size >= MAX_TOTAL_QUEUE) {
                if (priority == RecognitionEvidencePriority.CRITICAL) {
                    val oldestSample = queue.firstOrNull { it.priority == RecognitionEvidencePriority.SAMPLE }
                    if (oldestSample != null) {
                        queue.remove(oldestSample)
                        droppedSamples++
                        incompleteReasons += "SAMPLE_EVENT_EVICTED_FOR_CRITICAL"
                    } else {
                        droppedCritical++
                        incompleteReasons += "CRITICAL_EVENT_QUEUE_OVERFLOW"
                        enqueued = false
                    }
                } else {
                    droppedSamples++
                    incompleteReasons += "SAMPLE_EVENT_QUEUE_OVERFLOW"
                    enqueued = false
                }
            }
            if (enqueued) queue.addLast(row)
            if (!draining && queue.isNotEmpty()) {
                draining = true
                runCatching { executor.execute(::drain) }.onFailure {
                    draining = false
                    writeFailures++
                    incompleteReasons += "EVENT_EXECUTOR_REJECTED:${it.javaClass.simpleName}"
                }
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

    fun flush(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        val clampedTimeoutMs = timeoutMs.coerceIn(1L, MAX_FLUSH_TIMEOUT_MS)
        val disposedNow = synchronized(lock) { disposed }
        if (disposedNow) return true
        return runCatching {
            executor.submit { drain() }.get(clampedTimeoutMs, TimeUnit.MILLISECONDS)
            true
        }.getOrElse { failure ->
            val reason = if (failure is java.util.concurrent.TimeoutException) {
                "EVENT_FLUSH_TIMEOUT"
            } else {
                "EVENT_FLUSH_FAILED:${failure.javaClass.simpleName}"
            }
            markIncomplete(reason)
            false
        }
    }

    /**
     * Establishes a write-locked immutable snapshot of current log files into [targetLogsDir].
     * UI threads calling [record] remain unblocked in memory while existing log files are copied atomically.
     */
    internal fun snapshotLogsTo(
        targetLogsDir: File,
        flushTimeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS,
        maxTotalBytes: Long = MAX_SNAPSHOT_LOG_BYTES,
    ): LogSnapshotResult {
        flush(flushTimeoutMs)
        targetLogsDir.mkdirs()
        return synchronized(logFileLock) {
            val sourceLogsDir = File(attemptDirectory, "logs")
            val copied = mutableListOf<String>()
            val truncated = mutableListOf<String>()
            var remainingBudget = maxTotalBytes.coerceAtLeast(1L)
            var capReached = false
            val files = sourceLogsDir.listFiles()?.filter(File::isFile)?.sortedBy(File::getName).orEmpty()
            for (source in files) {
                val dest = File(targetLogsDir, source.name)
                val length = source.length()
                if (remainingBudget <= 0L) {
                    capReached = true
                    truncated += source.name
                    dest.writeText("", Charsets.UTF_8)
                    copied += source.name
                    continue
                }
                if (length <= remainingBudget && length <= MAX_SINGLE_LOG_FILE_BYTES) {
                    source.copyTo(dest, overwrite = true)
                    remainingBudget -= length
                } else {
                    val allowed = minOf(remainingBudget, MAX_SINGLE_LOG_FILE_BYTES)
                    copyTailBounded(source, dest, allowed)
                    remainingBudget = (remainingBudget - dest.length()).coerceAtLeast(0L)
                    truncated += source.name
                    capReached = true
                }
                copied += source.name
            }
            if (capReached) {
                markIncomplete("LOG_SNAPSHOT_SIZE_CAP_REACHED")
            }
            LogSnapshotResult(
                diagnostics = diagnostics(),
                copiedFiles = copied,
                truncatedFiles = truncated,
                sizeCapReached = capReached,
            )
        }
    }

    fun dispose() {
        synchronized(lock) { disposed = true }
        executor.shutdown()
    }

    private fun drain() {
        while (true) {
            val queued = synchronized(lock) {
                when {
                    queue.isNotEmpty() -> queue.removeFirst()
                    else -> {
                        draining = false
                        null
                    }
                }
            } ?: return
            val target = File(File(attemptDirectory, "logs"), queued.fileName)
            try {
                val line = queued.encodedLine(queued.sequence, queued.timestampMs) + "\n"
                synchronized(logFileLock) {
                    target.parentFile?.mkdirs()
                    if (target.isFile && target.length() >= MAX_SINGLE_LOG_FILE_BYTES) {
                        var rotation = 1
                        var rotated = File(target.parentFile, "${queued.fileName}.part_${rotation.toString().padStart(4, '0')}")
                        while (rotated.exists()) {
                            rotation++
                            rotated = File(
                                target.parentFile,
                                "${queued.fileName}.part_${rotation.toString().padStart(4, '0')}",
                            )
                        }
                        check(target.renameTo(rotated)) { "LOG_ROTATION_FAILED:${queued.fileName}" }
                    }
                    target.appendText(line, Charsets.UTF_8)
                }
                synchronized(lock) { eventCount++ }
            } catch (failure: Throwable) {
                synchronized(lock) {
                    writeFailures++
                    incompleteReasons += "EVENT_WRITE_FAILED:${failure.javaClass.simpleName}"
                }
            }
        }
    }

    private fun copyTailBounded(source: File, dest: File, maxBytes: Long) {
        if (maxBytes <= 0L) {
            dest.writeText("", Charsets.UTF_8)
            return
        }
        val length = source.length()
        if (length <= maxBytes) {
            source.copyTo(dest, overwrite = true)
            return
        }
        java.io.RandomAccessFile(source, "r").use { raf ->
            val skip = length - maxBytes
            raf.seek(skip)
            // Align to the next newline so truncated JSONL keeps valid lines.
            if (skip > 0L) {
                while (raf.filePointer < length) {
                    if (raf.read() == '\n'.code) break
                }
            }
            dest.outputStream().buffered().use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = raf.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    internal companion object {
        const val MAX_TOTAL_QUEUE = 4_096
        const val DEFAULT_FLUSH_TIMEOUT_MS = 2_000L
        const val MAX_FLUSH_TIMEOUT_MS = 5_000L
        const val MAX_SINGLE_LOG_FILE_BYTES = 2L * 1024L * 1024L
        const val MAX_SNAPSHOT_LOG_BYTES = 8L * 1024L * 1024L
    }
}
