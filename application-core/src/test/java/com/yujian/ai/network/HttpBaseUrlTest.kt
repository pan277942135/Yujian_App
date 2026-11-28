package com.yujian.ai.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpBaseUrlTest {
    @Test
    fun normalizesHttpAndHttpsRoots() {
        assertEquals("https://api.example.test", normalizeHttpBaseUrl(" https://api.example.test/// "))
        assertEquals("HTTP://api.example.test/v1", normalizeHttpBaseUrl("HTTP://api.example.test/v1/"))
    }

    @Test
    fun rejectsNonHttpSchemesAndBlankValues() {
        listOf("", "   ", "ftp://api.example.test", "file:///tmp/api", "httpx://api.example.test").forEach {
            assertNull("unexpected API base URL: $it", normalizeHttpBaseUrl(it))
        }
    }
}
