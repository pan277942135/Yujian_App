package com.yujian.ai.diagnostics

import org.junit.Assert.*
import org.junit.Test

class StartupCrashReportTest {
    @Test fun reportKeepsCrashFramesButRedactsCredentialValues() {
        val report = sanitizeStartupReport("java.lang.IllegalStateException: launch\n at com.yujian.ai.MainActivity.onCreate(MainActivity.kt:30)\nAuthorization: Bearer secret.jwt.value\naccess_token=abc123 refresh_token=refresh987 password=private")
        assertTrue(report.contains("MainActivity.kt:30"))
        assertTrue(report.contains("IllegalStateException"))
        for (secret in listOf("secret.jwt.value", "abc123", "refresh987", "private")) assertFalse(report.contains(secret))
        assertTrue(report.contains("[REDACTED]"))
    }
    @Test fun aHugeExceptionCannotCreateAnUnboundedRecoveryReport() {
        val report = sanitizeStartupReport("x".repeat(MAX_STARTUP_REPORT_CHARS * 2))
        assertEquals(MAX_STARTUP_REPORT_CHARS, report.length)
    }
}
