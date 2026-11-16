package com.yujian.ai.ui.recognition.diagnostics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame

/** Resolves the actual host Window even when Compose supplies a wrapped Context. */
fun recognitionEvidenceWindow(context: Context): Window? {
    var current: Context = context
    val visited = mutableSetOf<Context>()
    while (visited.add(current)) {
        if (current is Activity) return current.window
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

data class RecognitionPixelCopyResult(
    val bitmap: Bitmap?,
    val status: String,
    val stageAtCompletion: String,
    val sameSceneIdentity: Boolean,
    val completionTimestampMs: Long,
)

/** Captures the real Android Window buffer. The caller compares stage identity after completion. */
object RecognitionPixelCopyCapture {
    fun request(
        window: Window?,
        frame: RecognitionSceneFrame,
        currentFrame: () -> RecognitionSceneFrame?,
        callback: (RecognitionPixelCopyResult) -> Unit,
    ) {
        val requestTime = android.os.SystemClock.uptimeMillis()
        if (window == null) {
            callback(RecognitionPixelCopyResult(null, "WINDOW_UNAVAILABLE", currentFrame()?.stage?.name.orEmpty(), false, android.os.SystemClock.uptimeMillis()))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            callback(RecognitionPixelCopyResult(null, "PIXELCOPY_ERROR", currentFrame()?.stage?.name.orEmpty(), false, android.os.SystemClock.uptimeMillis()))
            return
        }
        val decor = window.decorView
        val width = decor.width
        val height = decor.height
        if (width <= 0 || height <= 0) {
            callback(RecognitionPixelCopyResult(null, "WINDOW_UNAVAILABLE", currentFrame()?.stage?.name.orEmpty(), false, android.os.SystemClock.uptimeMillis()))
            return
        }
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (bitmap == null) {
            callback(RecognitionPixelCopyResult(null, "REQUEST_FAILED", currentFrame()?.stage?.name.orEmpty(), false, android.os.SystemClock.uptimeMillis()))
            return
        }
        runCatching {
            PixelCopy.request(window, bitmap, { copyResult ->
                val latest = currentFrame()
                val completionTime = android.os.SystemClock.uptimeMillis()
                val sameIdentity = latest?.identity == frame.identity
                val status = when (copyResult) {
                    PixelCopy.SUCCESS -> "SUCCESS"
                    PixelCopy.ERROR_SOURCE_NO_DATA,
                    PixelCopy.ERROR_SOURCE_INVALID,
                    PixelCopy.ERROR_DESTINATION_INVALID,
                    PixelCopy.ERROR_TIMEOUT,
                    PixelCopy.ERROR_UNKNOWN -> "PIXELCOPY_ERROR"
                    else -> "PIXELCOPY_ERROR_$copyResult"
                }
                val resolved = if (status == "SUCCESS") bitmap else {
                    bitmap.recycle()
                    null
                }
                callback(
                    RecognitionPixelCopyResult(
                        bitmap = resolved,
                        status = status,
                        stageAtCompletion = latest?.stage?.name ?: "NOT_RECORDED",
                        sameSceneIdentity = sameIdentity,
                        completionTimestampMs = maxOf(requestTime, completionTime),
                    ),
                )
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            bitmap.recycle()
            callback(
                RecognitionPixelCopyResult(
                    bitmap = null,
                    status = "REQUEST_FAILED",
                    stageAtCompletion = currentFrame()?.stage?.name.orEmpty(),
                    sameSceneIdentity = false,
                    completionTimestampMs = android.os.SystemClock.uptimeMillis(),
                ),
            )
        }
    }
}
