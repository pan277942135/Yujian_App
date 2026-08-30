package com.yujian.ai.ai

import android.graphics.Bitmap
import android.util.Log
import java.util.Locale

/**
 * Debug trace helper for Android/Python inference parity checks.
 * Enabled for development builds only.
 */
object InferenceTrace {
    private const val TAG = "YujianInference"

    fun bitmap(label: String, bitmap: Bitmap) {
        Log.i(TAG, """
            $label
            width=${bitmap.width}
            height=${bitmap.height}
            config=${bitmap.config}
        """.trimIndent())
    }

    fun tensorHead(values: FloatArray, count: Int = 32) {
        val text = values.take(count).joinToString(",") {
            String.format(Locale.US, "%.8f", it)
        }
        Log.i(TAG, "tensor_head=$text")
    }

    fun model(sha: String, size: Int) {
        Log.i(TAG, "model_sha=$sha model_bytes=$size")
    }
}
