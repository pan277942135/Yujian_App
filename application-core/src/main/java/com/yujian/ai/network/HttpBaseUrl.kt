package com.yujian.ai.network

/** Returns a normalized API root only when it uses an HTTP(S) URL scheme. */
internal fun normalizeHttpBaseUrl(value: String): String? {
    val normalized = value.trim().trimEnd('/')
    return normalized.takeIf {
        it.isNotBlank() &&
            (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true))
    }
}
