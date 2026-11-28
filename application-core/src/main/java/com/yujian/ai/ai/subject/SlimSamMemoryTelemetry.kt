package com.yujian.ai.ai.subject

import android.os.Debug
import java.io.File

internal fun parseSlimSamMemoryValues(lines: Iterable<String>): Map<String, Long> = lines.mapNotNull { line ->
    val (key, value) = line.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
    val number = value.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        ?: return@mapNotNull null
    key.trim() to number
}.toMap()

/** Android process counters. ORT arena reservation and internal tensor liveness are not exposed by ORT Java. */
internal data class SlimSamMemorySnapshot(
    val nativeHeapAllocatedBytes: Long?,
    val nativeHeapSizeBytes: Long?,
    val nativeHeapFreeBytes: Long?,
    val processPssKb: Long?,
    val processRssKb: Long?,
    val processVmHwmKb: Long? = null,
    val processRssAnonKb: Long? = null,
    val processVmSwapKb: Long? = null,
    val systemMemAvailableKb: Long? = null,
    val systemSwapFreeKb: Long? = null,
    val memoryPsiSome: String? = null,
    val memoryPsiFull: String? = null,
    val javaHeapUsedBytes: Long? = null,
    val javaHeapMaxBytes: Long? = null,
) {
    companion object {
        fun capture(): SlimSamMemorySnapshot {
            val status = readKeyValues(File("/proc/self/status"))
            val memInfo = readKeyValues(File("/proc/meminfo"))
            val pressure = runCatching { File("/proc/pressure/memory").readLines() }.getOrDefault(emptyList())
                .associateBy { it.substringBefore(' ') }
            val javaRuntime = Runtime.getRuntime()
            return SlimSamMemorySnapshot(
                nativeHeapAllocatedBytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrNull(),
                nativeHeapSizeBytes = runCatching { Debug.getNativeHeapSize() }.getOrNull(),
                nativeHeapFreeBytes = runCatching { Debug.getNativeHeapFreeSize() }.getOrNull(),
                processPssKb = runCatching { Debug.getPss().toLong() }.getOrNull(),
                processRssKb = status["VmRSS"],
                processVmHwmKb = status["VmHWM"],
                processRssAnonKb = status["RssAnon"],
                processVmSwapKb = status["VmSwap"],
                systemMemAvailableKb = memInfo["MemAvailable"],
                systemSwapFreeKb = memInfo["SwapFree"],
                memoryPsiSome = pressure["some"]?.replace(Regex("\\s+"), "_"),
                memoryPsiFull = pressure["full"]?.replace(Regex("\\s+"), "_"),
                javaHeapUsedBytes = runCatching { javaRuntime.totalMemory() - javaRuntime.freeMemory() }.getOrNull(),
                javaHeapMaxBytes = runCatching { javaRuntime.maxMemory() }.getOrNull(),
            )
        }

        private fun readKeyValues(file: File): Map<String, Long> = runCatching {
            parseSlimSamMemoryValues(file.readLines())
        }.getOrNull()
            ?: emptyMap()
    }
}
