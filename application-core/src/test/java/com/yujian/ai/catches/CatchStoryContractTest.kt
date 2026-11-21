package com.yujian.ai.catches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatchStoryContractTest {
    @Test
    fun savedStoryFromCatchResponseRemainsUserVisibleText() {
        assertEquals("第一条黑鱼。", catchStoryFromWire(" 第一条黑鱼。 "))
    }

    @Test
    fun blankAndJsonSentinelStoryValuesBecomeMissing() {
        listOf(null, "", "   ", "null", " NULL ", "undefined", " Undefined ").forEach {
            assertNull(catchStoryFromWire(it))
        }
    }
}
