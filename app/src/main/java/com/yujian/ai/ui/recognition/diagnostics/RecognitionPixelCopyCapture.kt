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
    val geometryVersionAtCompletion: Long? = null,
    val eventSequenceAtCompletion: Long? = null,
    val pathIdAtCompletion: String? = null,
)

internal fun interface RecognitionPixelCopyRequestBackend {
    fun request(window: Window, bitmap: Bitmap, handler: Handler, callback: (Int) -> Unit)
}

/** Captures the real Android Window buffer. The caller compares stage identity after completion. */
object RecognitionPixelCopyCapture {
    const val DEFAULT_TIMEOUT_MS = 2_000L

    fun request(
        window: Window?,
        frame: RecognitionSceneFrame,
        currentFrame: () -> RecognitionSceneFrame?,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: (RecognitionPixelCopyResult) -> Unit,
    ) = requestWithBackend(window, frame, currentFrame, timeoutMs, ANDROID_BACKEND, callback)

    /** Backend injection exists for Android instrumentation fault tests; production always uses PixelCopy. */
    internal fun requestWithBackend(
        window: Window?,
        frame: RecognitionSceneFrame,
        currentFrame: () -> RecognitionSceneFrame?,
        timeoutMs: Long,
        backend: RecognitionPixelCopyRequestBackend,
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
        if (!isWindowReadyForPixelCopy(decor)) {
            callback(RecognitionPixelCopyResult(null, "WINDOW_UNAVAILABLE", currentFrame()?.stage?.name.orEmpty(), false, android.os.SystemClock.uptimeMillis()))
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val copyStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun pathId(latest: RecognitionSceneFrame?): String = latest?.energyEvent?.pathId ?: "NOT_CREATED"
        fun sameIdentityFor(latest: RecognitionSceneFrame?): Boolean =
            RecognitionEvidenceCapturePolicy.sameSceneFrameIdentity(
                requestedIdentity = frame.identity,
                completedIdentity = latest?.identity,
                requestedGeometryVersion = frame.geometryVersion,
                completedGeometryVersion = latest?.geometryVersion,
                requestedEventSequence = frame.eventSequence,
                completedEventSequence = latest?.eventSequence,
                requestedPathId = frame.energyEvent?.pathId ?: "NOT_CREATED",
                completedPathId = pathId(latest),
            )
        val timeoutRunnable = Runnable {
            if (!completed.compareAndSet(false, true)) return@Runnable
            val latest = currentFrame()
            val completionTime = android.os.SystemClock.uptimeMillis()
            // Do not recycle bitmap here because the OS PixelCopy thread may still write into it
            // until the real callback runs (which recycles it cleanly when completed is already true).
            callback(
                RecognitionPixelCopyResult(
                    bitmap = null,
                    status = "PIXELCOPY_TIMEOUT",
                    stageAtCompletion = latest?.stage?.name ?: "NOT_RECORDED",
                    sameSceneIdentity = sameIdentityFor(latest),
                    completionTimestampMs = maxOf(requestTime, completionTime),
                    geometryVersionAtCompletion = latest?.geometryVersion,
                    eventSequenceAtCompletion = latest?.eventSequence,
                    pathIdAtCompletion = pathId(latest),
                ),
            )
        }
        handler.postDelayed(timeoutRunnable, timeoutMs.coerceAtLeast(1L))

        fun dispatchPixelCopy() {
            if (!copyStarted.compareAndSet(false, true) || completed.get()) return
            if (!isWindowReadyForPixelCopy(decor)) {
                handler.removeCallbacks(timeoutRunnable)
                if (completed.compareAndSet(false, true)) {
                    callback(
                        RecognitionPixelCopyResult(
                            bitmap = null,
                            status = "WINDOW_UNAVAILABLE",
                            stageAtCompletion = currentFrame()?.stage?.name.orEmpty(),
                            sameSceneIdentity = false,
                            completionTimestampMs = android.os.SystemClock.uptimeMillis(),
                        ),
                    )
                }
                return
            }
            val bitmap = runCatching {
                Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            if (bitmap == null) {
                handler.removeCallbacks(timeoutRunnable)
                if (completed.compareAndSet(false, true)) {
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
                return
            }
            runCatching {
                backend.request(window, bitmap, handler) { copyResult ->
                    handler.removeCallbacks(timeoutRunnable)
                    if (!completed.compareAndSet(false, true)) {
                        runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                        return@request
                    }
                    val latest = currentFrame()
                    val completionTime = android.os.SystemClock.uptimeMillis()
                    val sameIdentity = sameIdentityFor(latest)
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
                        runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                        null
                    }
                    callback(
                        RecognitionPixelCopyResult(
                            bitmap = resolved,
                            status = status,
                            stageAtCompletion = latest?.stage?.name ?: "NOT_RECORDED",
                            sameSceneIdentity = sameIdentity,
                            completionTimestampMs = maxOf(requestTime, completionTime),
                            geometryVersionAtCompletion = latest?.geometryVersion,
                            eventSequenceAtCompletion = latest?.eventSequence,
                            pathIdAtCompletion = pathId(latest),
                        ),
                    )
                }
            }.onFailure {
                handler.removeCallbacks(timeoutRunnable)
                if (completed.compareAndSet(false, true)) {
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
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

        // Never call PixelCopy.request synchronously inside Compose drawWithContent:
        // defer until after RenderThread commits the current frame so we don't stall on an unsignaled buffer fence.
        val observer = decor.viewTreeObserver
        val registeredCommit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && observer.isAlive) {
            runCatching {
                observer.registerFrameCommitCallback {
                    handler.post { dispatchPixelCopy() }
                }
                true
            }.getOrDefault(false)
        } else {
            false
        }
        if (registeredCommit) {
            handler.postDelayed({ dispatchPixelCopy() }, POST_COMMIT_FALLBACK_DELAY_MS)
        } else {
            decor.post { dispatchPixelCopy() }
        }
    }

    private fun isWindowReadyForPixelCopy(decor: android.view.View): Boolean =
        decor.isAttachedToWindow &&
            decor.visibility == android.view.View.VISIBLE &&
            decor.isHardwareAccelerated &&
            decor.hasWindowFocus() &&
            decor.width > 0 &&
            decor.height > 0

    private val ANDROID_BACKEND = RecognitionPixelCopyRequestBackend { window, bitmap, handler, callback ->
        PixelCopy.request(window, bitmap, { result -> callback(result) }, handler)
    }

    private const val POST_COMMIT_FALLBACK_DELAY_MS = 48L
}
