package com.yujian.ai.ui.identify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRectAreaTest {
    @Test
    fun acceptsOnlyFiniteRectanglesWithPositiveArea() {
        assertTrue(DisplayRect(0f, 0f, 1f, 1f).hasValidArea())
        assertFalse(DisplayRect(Float.NaN, 0f, 1f, 1f).hasValidArea())
        assertFalse(DisplayRect(0f, 0f, Float.POSITIVE_INFINITY, 1f).hasValidArea())
        assertFalse(DisplayRect(0f, 0f, 0f, 1f).hasValidArea())
        assertFalse(DisplayRect(0f, 1f, 1f, 0f).hasValidArea())
    }
}
