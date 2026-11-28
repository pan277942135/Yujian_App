package com.yujian.ai.ai.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SlimSamMemoryTelemetryTest {
    @Test
    fun parsesProcessAndSystemMemoryCountersWithoutConflatingUnits() {
        val counters = parseSlimSamMemoryValues(
            listOf(
                "VmHWM: 2048000 kB",
                "VmRSS: 1800000 kB",
                "RssAnon: 1500000 kB",
                "VmSwap: 120000 kB",
                "MemAvailable: 900000 kB",
                "SwapFree: 400000 kB",
                "Name: com.yujian.ai.uiv2",
                "VmRSS: invalid kB",
                "not-a-counter",
            ),
        )

        assertEquals(2_048_000L, counters["VmHWM"])
        assertEquals(1_800_000L, counters["VmRSS"])
        assertEquals(1_500_000L, counters["RssAnon"])
        assertEquals(120_000L, counters["VmSwap"])
        assertEquals(900_000L, counters["MemAvailable"])
        assertEquals(400_000L, counters["SwapFree"])
        assertFalse("Process name is not a numeric memory counter", counters.containsKey("Name"))
    }
}
