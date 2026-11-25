package com.yujian.ai.ai.subject

import android.os.Debug
import java.io.File

/** Android process counters. ORT arena reservation and internal tensor liveness are not exposed by ORT Java. */
internal data class SlimSamMemorySnapshot(
    val nativeHeapAllocatedBytes: Long?,
    val nativeHeapSizeBytes: Long?,
    val nativeHeapFreeBytes: Long?,
    val processPssKb: Long?,
    val processRssKb: Long?,
) {
    companion object {
        fun capture(): SlimSamMemorySnapshot = SlimSamMemorySnapshot(
            nativeHeapAllocatedBytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrNull(),
            nativeHeapSizeBytes = runCatching { Debug.getNativeHeapSize() }.getOrNull(),
            nativeHeapFreeBytes = runCatching { Debug.getNativeHeapFreeSize() }.getOrNull(),
            processPssKb = runCatching { Debug.getPss().toLong() }.getOrNull(),
            processRssKb = readVmRssKb(),
        )

        private fun readVmRssKb(): Long? = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(1)
                    ?.toLong()
            }
        }.getOrNull()
    }
}
