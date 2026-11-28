package com.yujian.ai.diagnostics

internal const val MAX_STARTUP_REPORT_CHARS = 96 * 1024

/** Reports contain diagnostics only; redact credentials if an exception embeds request text. */
internal fun sanitizeStartupReport(text: String): String = text
    .replace(Regex("(?i)(bearer\\s+)[a-z0-9._~+/-]+=*"), "$1[REDACTED]")
    .replace(Regex("(?i)((?:access_token|refresh_token|id_token|api_key|password|authorization)[\\s\\\"':=]+)[^\\s,}\\\"]+"), "$1[REDACTED]")
    .take(MAX_STARTUP_REPORT_CHARS)
