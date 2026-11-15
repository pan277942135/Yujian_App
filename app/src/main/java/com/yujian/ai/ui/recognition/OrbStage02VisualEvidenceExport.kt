package com.yujian.ai.ui.recognition

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.OrbR06PathDiagnostic
import com.yujian.ai.ui.waterlight.renderer.OrbStage02LayerIsolation
import com.yujian.ai.ui.waterlight.renderer.OrbStage02MaxVisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbStage02RenderTrace
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max

/** Debug-only PixelCopy evidence of the actual Android window, with per-image frame identity. */
object OrbStage02VisualEvidenceExport {
    data class Metadata(
        val sessionId: String,
        val attemptId: String,
        val imageId: String,
        val stage: RecognitionSceneStage,
        val stageProgress: Float,
        val sceneElapsedMs: Long,
        val geometryVersion: Long,
        val visualProfile: OrbStage02VisualProfile,
        val renderIsolationMode: OrbRenderIsolationMode,
        val layerIsolation: OrbStage02LayerIsolation,
        val shaderRoute: String,
        val shaderStatus: String,
        val coreSizePx: Float,
        val reducedMotion: Boolean,
        val lowPerformance: Boolean,
        val interiorFlowStrength: Float,
        val interiorDisplacementD: Float,
        /** Runtime eligibility only. This does not claim measured GPU output. */
        val fluidRuntimeEligibilityInferred: Boolean,
        val r06BackPathIds: Set<String>,
        val r06BackTransmissionPathIds: Set<String>,
        val r06FrontPathIds: Set<String>,
        val r06PathDiagnostics: List<OrbR06PathDiagnostic>,
    )

    data class Capture(
        val fileName: String,
        val mode: OrbRenderIsolationMode,
        val layer: OrbStage02LayerIsolation,
        val excludedPathId: String? = null,
        val sampledSceneElapsedMs: Long? = null,
        val captureId: String = UUID.randomUUID().toString(),
    )

    private data class CapturedFile(
        val capture: Capture,
        val png: ByteArray,
        val bitmap: Bitmap,
        val metadata: JSONObject,
    )

    private data class CaptureFailure(val capture: Capture, val reason: String)

    suspend fun share(
        context: Context,
        view: View,
        orbBoundsInView: Rect,
        metadata: Metadata,
        render: suspend (Capture) -> Unit,
    ) {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        require(metadata.stage == RecognitionSceneStage.ORB_WAKE) { "Evidence export is Stage02-only" }
        require(metadata.sessionId.isNotBlank() && metadata.attemptId.isNotBlank() && metadata.imageId.isNotBlank()) {
            "Evidence export requires the current host identity"
        }
        val outputs = buildList {
            add(Capture("composite/RAW_CORE.png", OrbRenderIsolationMode.RAW_CORE,
                OrbStage02LayerIsolation.R03_R06, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            add(Capture("composite/R03_ONLY.png", OrbRenderIsolationMode.NORMAL,
                OrbStage02LayerIsolation.R03_ONLY, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            add(Capture("composite/R06_ONLY.png", OrbRenderIsolationMode.NORMAL,
                OrbStage02LayerIsolation.R06_ONLY, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            add(Capture("composite/BACK_TRANSMISSION.png", OrbRenderIsolationMode.NORMAL,
                OrbStage02LayerIsolation.R06_BACK_TRANSMISSION_ONLY, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            add(Capture("composite/FULL_COMPOSITE.png", OrbRenderIsolationMode.NORMAL,
                OrbStage02LayerIsolation.R03_R06, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            listOf(0L, 500L, 1_500L).forEachIndexed { index, offset ->
                add(Capture(
                    fileName = "r03/R03_T${listOf("0000", "0500", "1500")[index]}.png",
                    mode = OrbRenderIsolationMode.NORMAL,
                    layer = OrbStage02LayerIsolation.R03_ONLY,
                    sampledSceneElapsedMs = metadata.sceneElapsedMs + offset,
                ))
            }
            add(Capture("r06/R06_FULL.png", OrbRenderIsolationMode.NORMAL,
                OrbStage02LayerIsolation.R06_ONLY, sampledSceneElapsedMs = metadata.sceneElapsedMs))
            listOf("G01", "G02", "A01", "A02", "C01").forEach { pathId ->
                add(Capture(
                    fileName = "r06/R06_WITHOUT_$pathId.png",
                    mode = OrbRenderIsolationMode.NORMAL,
                    layer = OrbStage02LayerIsolation.R06_ONLY,
                    excludedPathId = pathId,
                    sampledSceneElapsedMs = metadata.sceneElapsedMs,
                ))
            }
        }

        val captured = mutableListOf<CapturedFile>()
        val failures = mutableListOf<CaptureFailure>()
        try {
        withContext(Dispatchers.Main.immediate) {
            outputs.forEach { capture ->
                try {
                    val marker = OrbStage02RenderTrace.registerCaptureMarker(capture.captureId)
                    render(capture)
                    try {
                        withTimeout(CAPTURE_TIMEOUT_MS) { marker.await() }
                    } catch (timeout: TimeoutCancellationException) {
                        if (!currentCoroutineContext().isActive) throw timeout
                        throw IllegalStateException("CAPTURE_FRAME_UNVERIFIED: Orb draw marker timed out", timeout)
                    }
                    val expectsShaderLayer = capture.mode == OrbRenderIsolationMode.NORMAL &&
                        capture.layer in setOf(OrbStage02LayerIsolation.R03_ONLY, OrbStage02LayerIsolation.R03_R06)
                    val submittedState = OrbStage02RenderTrace.captureState(capture.captureId)
                        ?: throw IllegalStateException("CAPTURE_STATE_MISSING: ${capture.captureId}")
                    validateCaptureState(metadata, capture, submittedState)
                    if (expectsShaderLayer && submittedState.optBoolean("shaderOverlayEnabled")) {
                        try {
                            withTimeout(CAPTURE_TIMEOUT_MS) {
                                while (!OrbStage02RenderTrace.hasCaptureEvent(capture.captureId, "ORB_R03_RENDER")) {
                                    delay(16L)
                                }
                            }
                        } catch (timeout: TimeoutCancellationException) {
                            if (!currentCoroutineContext().isActive) throw timeout
                            throw IllegalStateException("RENDER_LAYER_NOT_UPDATED: ${capture.captureId}", timeout)
                        }
                    }
                    awaitCommittedWindowFrame(view)
                    val committedState = OrbStage02RenderTrace.captureState(capture.captureId)
                        ?: throw IllegalStateException("CAPTURE_STATE_MISSING: ${capture.captureId}")
                    validateCaptureState(metadata, capture, committedState)
                    val capturePathDiagnostics = OrbStage02RenderTrace.capturePathDiagnostics(capture.captureId)
                    val captureWindowRect = pixelCopyWindowRect(view, orbBoundsInView)
                    val drawWidth = committedState.optDouble("captureWidthPx")
                    val drawHeight = committedState.optDouble("captureHeightPx")
                    if (abs(drawWidth - captureWindowRect.width()) > 2.0 ||
                        abs(drawHeight - captureWindowRect.height()) > 2.0
                    ) {
                        throw IllegalStateException(
                            "CAPTURE_BOUNDS_MISMATCH: draw=${drawWidth}x$drawHeight " +
                                "pixelCopy=${captureWindowRect.width()}x${captureWindowRect.height()}",
                        )
                    }
                    val bitmap = try {
                        withTimeout(CAPTURE_TIMEOUT_MS) { pixelCopy(view, captureWindowRect) }
                    } catch (timeout: TimeoutCancellationException) {
                        if (!currentCoroutineContext().isActive) throw timeout
                        throw IllegalStateException("PIXEL_COPY_FAILED: PixelCopy timed out", timeout)
                    }
                    val png = ByteArrayOutputStream().use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG compression failed" }
                        stream.toByteArray()
                    }
                    val entryMetadata = captureMetadata(
                        metadata, capture, committedState, capturePathDiagnostics,
                        captureWindowRect, sha256(png), "PIXEL_COPY_SUCCESS",
                    )
                    captured += CapturedFile(capture, png, bitmap, entryMetadata)
                    OrbStage02RenderTrace.record(
                        eventType = "ORB_CAPTURE",
                        dataSource = "PIXEL_COPY_RESULT",
                        captureId = capture.captureId,
                        fields = mapOf("status" to "SUCCESS", "fileName" to capture.fileName,
                            "pngSha256" to sha256(png), "pixelWidth" to bitmap.width, "pixelHeight" to bitmap.height),
                    )
                } catch (failure: Throwable) {
                    if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
                    val reason = failure.message ?: failure::class.qualifiedName.orEmpty()
                    failures += CaptureFailure(capture, reason)
                    OrbStage02RenderTrace.record(
                        eventType = when {
                            reason.contains("CAPTURE_FRAME_UNVERIFIED") -> "CAPTURE_FRAME_UNVERIFIED"
                            reason.contains("RENDER_LAYER_NOT_UPDATED") -> "RENDER_LAYER_NOT_UPDATED"
                            reason.contains("CAPTURE_FRAME_MISMATCH") -> "CAPTURE_FRAME_MISMATCH"
                            else -> "PIXEL_COPY_FAILED"
                        },
                        dataSource = "PIXEL_COPY_RESULT",
                        captureId = capture.captureId,
                        fields = mapOf("status" to "FAILED", "fileName" to capture.fileName,
                            "exceptionType" to (failure::class.qualifiedName ?: failure.javaClass.name), "reason" to reason),
                    )
                } finally {
                    OrbStage02RenderTrace.clearCaptureState(capture.captureId)
                }
            }
        }

        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "diagnostics/orb-visual-evidence").apply { mkdirs() }
            val output = File(directory, "orb-stage02-${System.currentTimeMillis()}.zip")
            val metrics = try {
                analyzeCapturedPixels(captured)
            } finally {
                captured.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            }
            val r03PixelPairsMeasured = metrics.optJSONObject("r03")?.let { r03Metrics ->
                listOf("pair0", "pair1").all { pairId ->
                    val pair = r03Metrics.optJSONObject(pairId) ?: return@all false
                    pair.optJSONObject("alignment")?.optBoolean("aligned") == true &&
                        pair.optJSONObject("innerRoi")?.optString("status") == "MEASURED"
                }
            } == true
            val r06AllPathPixelsMeasured = metrics.optJSONObject("r06PerPathContributions")?.let { r06Metrics ->
                listOf("G01", "G02", "A01", "A02", "C01").all { pathId ->
                    val contribution = r06Metrics.optJSONObject(pathId) ?: return@all false
                    contribution.optJSONObject("alignment")?.optBoolean("aligned") == true &&
                        contribution.optString("status") == "MEASURED"
                }
            } == true
            metrics.optJSONObject("r03")?.let { r03Metrics ->
                r03Metrics.keys().forEach { pair ->
                    val entry = r03Metrics.optJSONObject(pair) ?: return@forEach
                    val inner = entry.optJSONObject("innerRoi") ?: JSONObject()
                    OrbStage02RenderTrace.record(
                        eventType = "ORB_R03_PIXEL",
                        dataSource = "PIXEL_MEASUREMENT",
                        captureId = entry.optString("fromCaptureId"),
                        fields = mapOf(
                            "capturePair" to "${entry.optString("from")}|${entry.optString("to")}",
                            "otherCaptureId" to entry.optString("toCaptureId"),
                            "changedPixelRatio" to inner.optDouble("changedPixelRatio", 0.0),
                            "meanAbsoluteRgbDifference" to inner.optDouble("meanAbsoluteRgbDifference", 0.0),
                            "normalizedLumaDifference" to inner.optDouble("normalizedLumaDifference", 0.0),
                            "globalLuminanceDelta" to inner.optDouble("globalLuminanceDelta", 0.0),
                        ),
                    )
                }
            }
            metrics.optJSONObject("r06PerPathContributions")?.let { r06Metrics ->
                r06Metrics.keys().forEach { pathId ->
                    val entry = r06Metrics.optJSONObject(pathId) ?: return@forEach
                    OrbStage02RenderTrace.record(
                        eventType = "ORB_R06_PIXEL",
                        dataSource = "PIXEL_MEASUREMENT",
                        captureId = entry.optString("fullCaptureId"),
                        fields = mapOf(
                            "pathId" to pathId,
                            "excludedCaptureId" to entry.optString("withoutCaptureId"),
                            "changedPixelCount" to entry.optInt("changedPixelCount"),
                            "changedPixelRatio" to entry.optDouble("changedPixelRatio", 0.0),
                            "meanAbsoluteRgbDifference" to entry.optDouble("meanAbsoluteRgbDifference", 0.0),
                            "maximumChannelDifference" to entry.optInt("maximumChannelDifference"),
                            "largestConnectedComponentExtentPx" to entry.optInt("largestConnectedComponentExtentPx"),
                        ),
                    )
                }
            }
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                val manifestFiles = JSONArray()
                fun putEntry(path: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                    if (path != "manifest.json") manifestFiles.put(JSONObject()
                        .put("path", path).put("sizeBytes", bytes.size).put("sha256", sha256(bytes)))
                }
                fun putText(path: String, text: String) = putEntry(path, text.toByteArray(Charsets.UTF_8))
                captured.forEach { item ->
                    putEntry(item.capture.fileName, item.png)
                    val metadataName = "capture_metadata/${item.capture.fileName.substringAfterLast('/').substringBeforeLast('.')}.json"
                    putText(metadataName, item.metadata.toString(2))
                }
                val diagnostic = diagnosticJson(metadata, captured)
                putText("orb_visual_diagnostic.json", diagnostic.toString(2))
                putText("device_info.json", deviceInfo(context, metadata).toString(2))
                putText("build_info.json", JSONObject()
                    .put("sourceSha", BuildConfig.SOURCE_REVISION)
                    .put("buildType", if (BuildConfig.DEBUG) "debug" else "release")
                    .put("visualProfile", metadata.visualProfile.name)
                    .put("apkVersion", BuildConfig.VERSION_NAME)
                    .put("buildTimestampUtc", BuildConfig.BUILD_TIMESTAMP_UTC).toString(2))
                putText("logs/orb_render_trace.jsonl", OrbStage02RenderTrace.snapshotJsonLines())
                putText("logs/orb_visual_diagnostic.json", diagnostic.toString(2))
                putText("logs/capture_metadata.json", JSONArray().apply {
                    captured.forEach { put(JSONObject(it.metadata.toString())) }
                }.toString(2))
                putText("logs/errors.json", JSONArray(failures.map { JSONObject()
                    .put("captureId", it.capture.captureId).put("fileName", it.capture.fileName).put("reason", it.reason) }.toTypedArray()).toString(2))
                putText("r03/r03_metrics.json", metrics.optJSONObject("r03")?.toString(2).orEmpty())
                putText("r06/r06_metrics.json", metrics.optJSONObject("r06PerPathContributions")?.toString(2).orEmpty())
                putText("report/device_qa_summary.json", JSONObject()
                    .put("pixelCopySuccessCount", captured.size)
                    .put("pixelCopyFailureCount", failures.size)
                    .put("missingEvidence", JSONArray(failures.map { it.capture.fileName }))
                    .put("productionStage02TraceFrameCount", OrbStage02RenderTrace.productionStage02FrameCount())
                    .put("productionStage02TraceStatus", if (OrbStage02RenderTrace.productionStage02FrameCount() > 0) {
                        "AVAILABLE"
                    } else "PRODUCTION_STAGE02_TRACE_MISSING")
                    .put("humanVisualDiscernibility", "NOT_ASSESSED")
                    .put("r03PixelMeasurement", if (r03PixelPairsMeasured) {
                        "PIXELCOPY_PAIR_MEASURED; FLUID_INTERPRETATION_REQUIRES_ROUTE_AND_FIXED_PROGRESS_QUALIFICATION"
                    } else "NOT_MEASURED_REQUIRED_R03_PAIR_OR_ALIGNMENT_MISSING")
                    .put("r03FluidRouteQualified", metrics.optBoolean("r03RouteQualified"))
                    .put("r03FluidRouteQualification", metrics.optString("r03RouteQualification"))
                    .put("r06PerPathPixelContribution", if (r06AllPathPixelsMeasured) {
                        "PIXELCOPY_MEASURED_FOR_ALL_FIVE_PATHS; HUMAN_VISIBILITY_NOT_ASSESSED"
                    } else "NOT_MEASURED_REQUIRED_PATH_ABLATION_OR_ALIGNMENT_MISSING")
                    .put("r03FixedProgressQualified", metadata.stageProgress >= .999f)
                    .put("r03SampleClockSemantics", "T0_T500_T1500_ARE_PROJECTED_SHARED_OPTICAL_SAMPLES_WHILE_STAGE_FRAME_IS_PINNED")
                    .toString(2))
                val manifest = JSONObject()
                    .put("schemaVersion", 1)
                    .put("sourceSha", BuildConfig.SOURCE_REVISION)
                    .put("traceId", OrbStage02RenderTrace.activeTraceId())
                    .put("stage", metadata.stage.name)
                    .put("stageProgress", metadata.stageProgress.toDouble())
                    .put("geometryVersion", metadata.geometryVersion)
                    .put("captureCount", captured.size)
                    .put("failureCount", failures.size)
                    .put("droppedTraceEvents", OrbStage02RenderTrace.droppedEventCount())
                    .put("files", manifestFiles)
                    .put("failures", JSONArray(failures.map { JSONObject()
                        .put("captureId", it.capture.captureId).put("fileName", it.capture.fileName).put("reason", it.reason) }))
                putText("manifest.json", manifest.toString(2))
            }
            output
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "导出Orb完整渲染证据"))
        } finally {
            captured.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    @RequiresApi(29)
    private suspend fun awaitCommittedWindowFrame(view: View) {
        if (Build.VERSION.SDK_INT < 29) {
            throw IllegalStateException("CAPTURE_FRAME_UNVERIFIED: Window frame commit callback requires API 29+")
        }
        try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val observer = view.viewTreeObserver
                    if (!observer.isAlive) {
                        continuation.resumeWithException(IllegalStateException("CAPTURE_FRAME_UNVERIFIED: dead ViewTreeObserver"))
                        return@suspendCancellableCoroutine
                    }
                    try {
                        observer.registerFrameCommitCallback {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        view.postInvalidateOnAnimation()
                    } catch (failure: Throwable) {
                        continuation.resumeWithException(IllegalStateException(
                            "CAPTURE_FRAME_UNVERIFIED: ${failure::class.qualifiedName}", failure,
                        ))
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw timeout
            throw IllegalStateException("CAPTURE_FRAME_UNVERIFIED: window frame commit timed out", timeout)
        }
    }

    private fun validateCaptureState(
        metadata: Metadata,
        capture: Capture,
        state: JSONObject,
    ) {
        fun mismatch(field: String, expected: Any?, actual: Any?): Nothing =
            throw IllegalStateException("CAPTURE_FRAME_MISMATCH: $field expected=$expected actual=$actual")

        if (state.optString("sourceCommitSha") != BuildConfig.SOURCE_REVISION) {
            mismatch("sourceCommitSha", BuildConfig.SOURCE_REVISION, state.optString("sourceCommitSha"))
        }
        listOf(
            "sessionIdHash" to metadata.sessionId,
            "attemptIdHash" to metadata.attemptId,
            "imageIdHash" to metadata.imageId,
        ).forEach { (field, identity) ->
            val expected = anonymize(identity)
            if (state.optString(field) != expected) mismatch(field, expected, state.optString(field))
        }
        if (!state.optBoolean("markerObserved")) mismatch("markerObserved", true, false)
        if (state.optString("captureId") != capture.captureId) mismatch("captureId", capture.captureId, state.optString("captureId"))
        if (state.optString("frameTraceId").isBlank()) mismatch("frameTraceId", "nonblank", state.optString("frameTraceId"))
        if (state.optString("stage") != metadata.stage.name) mismatch("stage", metadata.stage.name, state.optString("stage"))
        if (kotlin.math.abs(state.optDouble("stageProgress", -1.0) - metadata.stageProgress.toDouble()) > 0.0001) {
            mismatch("stageProgress", metadata.stageProgress, state.optDouble("stageProgress", -1.0))
        }
        if (state.optLong("geometryVersion", Long.MIN_VALUE) != metadata.geometryVersion) {
            mismatch("geometryVersion", metadata.geometryVersion, state.optLong("geometryVersion", Long.MIN_VALUE))
        }
        if (state.optString("visualProfile") != metadata.visualProfile.name) {
            mismatch("visualProfile", metadata.visualProfile.name, state.optString("visualProfile"))
        }
        if (state.optString("renderIsolationMode") != capture.mode.name) {
            mismatch("renderIsolationMode", capture.mode.name, state.optString("renderIsolationMode"))
        }
        if (state.optString("layerIsolation") != capture.layer.name) {
            mismatch("layerIsolation", capture.layer.name, state.optString("layerIsolation"))
        }
        val expectedExcludedPath = capture.excludedPathId.orEmpty()
        if (state.optString("excludedPathId") != expectedExcludedPath) {
            mismatch("excludedPathId", expectedExcludedPath, state.optString("excludedPathId"))
        }
        val expectedOpticalMs = capture.sampledSceneElapsedMs ?: metadata.sceneElapsedMs
        if (state.optLong("sampledOpticalElapsedMs", Long.MIN_VALUE) != expectedOpticalMs) {
            mismatch("sampledOpticalElapsedMs", expectedOpticalMs, state.optLong("sampledOpticalElapsedMs", Long.MIN_VALUE))
        }
        if (state.optLong("frameSceneElapsedMs", Long.MIN_VALUE) != metadata.sceneElapsedMs) {
            mismatch("frameSceneElapsedMs", metadata.sceneElapsedMs, state.optLong("frameSceneElapsedMs", Long.MIN_VALUE))
        }
        if (state.optDouble("captureWidthPx", 0.0) <= 0.0 || state.optDouble("captureHeightPx", 0.0) <= 0.0) {
            mismatch("captureSize", "positive", "${state.optDouble("captureWidthPx") }x${state.optDouble("captureHeightPx")}")
        }
    }

    private fun pixelCopyWindowRect(view: View, boundsInView: Rect): Rect {
        val activity = view.context.findActivity()
            ?: throw IllegalStateException("CAPTURE_BOUNDS_UNVERIFIED: no Activity window")
        if (boundsInView.left < 0 || boundsInView.top < 0 ||
            boundsInView.right > view.width || boundsInView.bottom > view.height ||
            boundsInView.width() <= 0 || boundsInView.height() <= 0
        ) {
            throw IllegalStateException(
                "CAPTURE_BOUNDS_UNVERIFIED: Orb bounds=${boundsInView.flattenToString()} view=${view.width}x${view.height}",
            )
        }
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val sourceRect = Rect(
            location[0] + boundsInView.left,
            location[1] + boundsInView.top,
            location[0] + boundsInView.right,
            location[1] + boundsInView.bottom,
        )
        val decor = activity.window.decorView
        if (sourceRect.left < 0 || sourceRect.top < 0 ||
            sourceRect.right > decor.width || sourceRect.bottom > decor.height
        ) {
            throw IllegalStateException(
                "CAPTURE_BOUNDS_UNVERIFIED: windowRect=${sourceRect.flattenToString()} " +
                    "window=${decor.width}x${decor.height}",
            )
        }
        return sourceRect
    }

    private suspend fun pixelCopy(view: View, sourceRect: Rect): Bitmap = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            continuation.resumeWithException(IllegalStateException("PixelCopy unavailable"))
            return@suspendCancellableCoroutine
        }
        val activity = view.context.findActivity()
        if (sourceRect.width() <= 0 || sourceRect.height() <= 0 || activity == null) {
            continuation.resumeWithException(IllegalStateException("Invalid Orb capture bounds or Activity"))
            return@suspendCancellableCoroutine
        }
        val target = Bitmap.createBitmap(sourceRect.width(), sourceRect.height(), Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(activity.window, sourceRect, target, { result ->
                if (!continuation.isActive) {
                    if (!target.isRecycled) target.recycle()
                } else if (result == PixelCopy.SUCCESS) continuation.resume(target)
                else {
                    target.recycle()
                    continuation.resumeWithException(IllegalStateException("PixelCopy failed: $result"))
                }
            }, Handler(Looper.getMainLooper()))
        }.onFailure { failure ->
            if (!target.isRecycled) target.recycle()
            continuation.resumeWithException(failure)
        }
        continuation.invokeOnCancellation { if (!target.isRecycled) target.recycle() }
    }

    private fun captureMetadata(
        value: Metadata,
        capture: Capture,
        submittedState: JSONObject,
        capturePathDiagnostics: JSONArray,
        captureWindowRect: Rect,
        pngSha256: String,
        status: String,
    ) = JSONObject()
        .put("schemaVersion", 1)
        .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
        .put("traceId", OrbStage02RenderTrace.activeTraceId())
        .put("frameTraceId", submittedState.optString("frameTraceId"))
        .put("captureId", capture.captureId)
        .put("markerObserved", submittedState.optBoolean("markerObserved"))
        .put("submittedRenderState", JSONObject(submittedState.toString()))
        .put("pixelCopySourceRectWindow", JSONObject()
            .put("left", captureWindowRect.left)
            .put("top", captureWindowRect.top)
            .put("right", captureWindowRect.right)
            .put("bottom", captureWindowRect.bottom))
        .put("pixelCopySourceRectCoordinateSpace", "ANDROID_WINDOW")
        .put("stage", value.stage.name)
        .put("stageProgress", value.stageProgress.coerceIn(0f, 1f).toDouble())
        .put("playbackMode", submittedState.optString("playbackMode"))
        .put("playbackPaused", submittedState.optBoolean("playbackPaused"))
        .put("sceneElapsedMs", submittedState.optLong("frameSceneElapsedMs", value.sceneElapsedMs))
        .put("opticalSampleMs", submittedState.optLong(
            "sampledOpticalElapsedMs", capture.sampledSceneElapsedMs ?: value.sceneElapsedMs,
        ))
        .put("geometryVersion", value.geometryVersion)
        .put("sessionIdHash", submittedState.optString("sessionIdHash"))
        .put("attemptIdHash", submittedState.optString("attemptIdHash"))
        .put("imageIdHash", submittedState.optString("imageIdHash"))
        .put("reducedMotion", value.reducedMotion)
        .put("lowPerformance", value.lowPerformance)
        .put("renderMode", capture.mode.name)
        .put("layerIsolation", capture.layer.name)
        .put("excludedPathId", capture.excludedPathId)
        .put("opticalSampleMode", if (capture.sampledSceneElapsedMs == value.sceneElapsedMs) {
            "CURRENT_UNIFIED_SCENE_CLOCK_SAMPLE"
        } else {
            "FIXED_STAGE_PROGRESS_PROJECTED_SHARED_OPTICAL_SAMPLE_NOT_SCENE_CLOCK_ADVANCE"
        })
        .put("r06PathDiagnosticsAtCapture", capturePathDiagnostics)
        .put("r06PathDiagnosticsDataSource", "CURRENT_CAPTURE_CANVAS_DRAW_METRICS_NOT_PIXEL_READBACK")
        .put("shaderRoute", submittedState.optString("shaderRoute"))
        .put("shaderStatus", submittedState.optString("shaderStatus"))
        .put("shaderOverlayEnabled", submittedState.optBoolean("shaderOverlayEnabled"))
        .put("shaderStateDataSource", "CAPTURE_FRAME_DRAW_SUBMISSION")
        .put("visualProfile", value.visualProfile.name)
        .put("pngSha256", pngSha256)
        .put("captureStatus", status)
        .put("evidenceLevel", "PIXEL_COPY_CAPTURE; NOT_HUMAN_VISUAL_VERDICT")

    private fun diagnosticJson(value: Metadata, capturedFiles: List<CapturedFile>): JSONObject = JSONObject()
        .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
        .put("traceId", OrbStage02RenderTrace.activeTraceId())
        .put("sessionIdHash", anonymize(value.sessionId))
        .put("attemptIdHash", anonymize(value.attemptId))
        .put("imageIdHash", anonymize(value.imageId))
        .put("stage", value.stage.name)
        .put("stageProgress", value.stageProgress.coerceIn(0f, 1f).toDouble())
        .put("sceneElapsedMs", value.sceneElapsedMs)
        .put("geometryVersion", value.geometryVersion)
        .put("visualProfile", value.visualProfile.name)
        .put("renderIsolationModeAtExport", value.renderIsolationMode.name)
        .put("layerIsolationAtExport", value.layerIsolation.name)
        .put("captureRecords", JSONArray().apply {
            capturedFiles.forEach { put(JSONObject(it.metadata.toString())) }
        })
        .put("shaderRoute", value.shaderRoute)
        .put("shaderStatus", value.shaderStatus)
        .put("shaderStatusDataSource", "RUNTIME_STATE")
        .put("coreSizePx", value.coreSizePx)
        .put("reducedMotion", value.reducedMotion)
        .put("lowPerformance", value.lowPerformance)
        .put("fluidRuntimeEligibilityInferred", value.fluidRuntimeEligibilityInferred)
        .put("fluidRuntimeEligibilityDataSource", "RUNTIME_STATE_INFERRED")
        .put("r03", JSONObject()
            .put("maxDisplacementD", OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D)
            .put("interiorMixMax", OrbStage02MaxVisualProfile.FLUID_INTERIOR_MIX_MAX)
            .put("wakeFlowStrength", value.interiorFlowStrength)
            .put("wakeDisplacementD", value.interiorDisplacementD)
            .put("metricStatus", "PIXEL_METRICS_IN_R03_METRICS_JSON"))
        .put("r06", JSONObject()
            .put("glassBackTransmissionMax", OrbStage02MaxVisualProfile.GLASS_BACK_TRANSMISSION_MAX)
            .put("glassRefractionMaxD", OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D)
            .put("glassTransmissionEnabled", value.visualProfile == OrbStage02VisualProfile.MAX)
            .put("backDrawnPathIdsRuntime", JSONArray(value.r06BackPathIds.sorted()))
            .put("backGlassPathIdsRuntime", JSONArray(value.r06BackTransmissionPathIds.sorted()))
            .put("frontDrawnPathIdsRuntime", JSONArray(value.r06FrontPathIds.sorted()))
            .put("pathCountDataSource", "CANVAS_DRAW_SUBMISSION_NOT_PIXEL_VISIBILITY")
            .put("pathDiagnosticsDataSource", "INITIAL_SCREEN_STATE_SNAPSHOT; SEE_CAPTURE_RECORDS_FOR_CAPTURE_BOUND_METRICS")
            .put("pathDiagnostics", JSONArray().apply {
                value.r06PathDiagnostics.sortedWith(compareBy<OrbR06PathDiagnostic>({ it.renderPass.name }, { it.front }, { it.pathId }))
                    .forEach { metric ->
                        put(JSONObject()
                            .put("pathId", metric.pathId)
                            .put("front", metric.front)
                            .put("renderPass", metric.renderPass.name)
                            .put("stageProgress", metric.stageProgress.toDouble())
                            .put("geometryVersion", metric.geometryVersion)
                            .put("validSamples", metric.validSampleCount)
                            .put("bodyValidSegments", metric.bodyValidSegmentCount)
                            .put("bodyDrawnRuns", metric.bodyDrawnRunCount)
                            .put("bodyPaintAlpha", metric.bodyPaintAlpha)
                            .put("bodyStrokeWidthPx", metric.bodyStrokeWidthPx.toDouble())
                            .put("bodyOriginalAlpha", metric.bodyOriginalAlpha.toDouble())
                            .put("bodyFinalAlphaBeforePixelMask", metric.bodyFinalAlpha.toDouble())
                            .put("bodyAddedAlpha", metric.bodyAddedAlpha.toDouble())
                            .put("transmissionFactor", metric.transmissionFactor.toDouble())
                            .put("edgeFade", metric.edgeFade.toDouble())
                            .put("pathPhaseU", metric.pathPhaseU.toDouble())
                            .put("effectiveTrailStartU", metric.effectiveTrailStartU.toDouble())
                            .put("effectiveTrailEndU", metric.effectiveTrailEndU.toDouble())
                            .put("bodyTailAlpha", metric.bodyTailAlpha.toDouble())
                            .put("headPaintAlpha", metric.headPaintAlpha))
                    }
            }))

    private fun deviceInfo(context: Context, value: Metadata) = JSONObject()
        .put("deviceModel", Build.MODEL)
        .put("androidVersion", Build.VERSION.RELEASE)
        .put("apiLevel", Build.VERSION.SDK_INT)
        .put("screenDensity", context.resources.displayMetrics.density)
        .put("reducedMotion", value.reducedMotion)
        .put("lowPerformance", value.lowPerformance)

    private fun analyzeCapturedPixels(files: List<CapturedFile>): JSONObject {
        val byName = files.associateBy { it.capture.fileName }
        val r03 = JSONObject()
        val r03Frames = listOf("r03/R03_T0000.png", "r03/R03_T0500.png", "r03/R03_T1500.png")
            .mapNotNull(byName::get)
        val routeQualified = r03Frames.size == 3 && r03Frames.all { frame ->
            frame.metadata.optString("visualProfile") == OrbStage02VisualProfile.MAX.name &&
                frame.metadata.optString("stage") == RecognitionSceneStage.ORB_WAKE.name &&
                frame.metadata.optDouble("stageProgress", -1.0) >= .999 &&
                frame.metadata.optString("renderMode") == OrbRenderIsolationMode.NORMAL.name &&
                frame.metadata.optString("layerIsolation") == OrbStage02LayerIsolation.R03_ONLY.name &&
                frame.metadata.optBoolean("shaderOverlayEnabled") &&
                frame.metadata.optString("shaderRoute") == "FLUID_GRASS_V1_2_CANDIDATE" &&
                frame.metadata.optString("shaderStatus") == "READY" &&
                !frame.metadata.optBoolean("reducedMotion")
        }
        r03Frames.zipWithNext().forEachIndexed { index, (first, second) ->
            val alignment = capturePairAlignment(first, second, requireSameOpticalSample = false)
            val inner = if (alignment.optBoolean("aligned")) compare(first.bitmap, second.bitmap, innerOnly = true)
                else JSONObject().put("status", "REJECTED_ALIGNMENT")
            val outer = if (alignment.optBoolean("aligned")) compare(first.bitmap, second.bitmap, innerOnly = false)
                else JSONObject().put("status", "REJECTED_ALIGNMENT")
            r03.put("pair$index", JSONObject()
                .put("from", first.capture.fileName).put("to", second.capture.fileName)
                .put("fromCaptureId", first.capture.captureId).put("toCaptureId", second.capture.captureId)
                .put("alignment", alignment)
                .put("innerRoi", inner).put("outerRoi", outer)
                .put("edgeRing", if (alignment.optBoolean("aligned")) compare(first.bitmap, second.bitmap,
                    innerOnly = false, edgeOnly = true) else JSONObject().put("status", "REJECTED_ALIGNMENT"))
                .put("localTextureMotion", if (alignment.optBoolean("aligned")) {
                    estimateLocalTextureMotion(first.bitmap, second.bitmap)
                } else JSONObject().put("status", "NOT_MEASURED_ALIGNMENT_REJECTED"))
                .put("fluidRouteQualified", routeQualified)
                .put("fluidRouteQualification", if (routeQualified) "MAX_CANDIDATE_READY_REDUCED_MOTION_OFF" else "RUNTIME_ROUTE_NOT_QUALIFIED")
                .put("dataSource", "PIXEL_MEASUREMENT"))
        }
        val layerComparisons = JSONObject()
        val raw = byName["composite/RAW_CORE.png"]
        if (raw != null) {
            listOf(
                "R03_ONLY" to "composite/R03_ONLY.png",
                "R06_ONLY" to "composite/R06_ONLY.png",
                "BACK_TRANSMISSION_ONLY" to "composite/BACK_TRANSMISSION.png",
                "FULL_COMPOSITE" to "composite/FULL_COMPOSITE.png",
            ).forEach { (label, path) ->
                val other = byName[path] ?: return@forEach
                val alignment = capturePairAlignment(raw, other, requireSameOpticalSample = true)
                layerComparisons.put(label, JSONObject()
                    .put("from", raw.capture.fileName)
                    .put("to", other.capture.fileName)
                    .put("fromCaptureId", raw.capture.captureId)
                    .put("toCaptureId", other.capture.captureId)
                    .put("alignment", alignment)
                    .put("innerRoi", if (alignment.optBoolean("aligned")) {
                        compare(raw.bitmap, other.bitmap, innerOnly = true)
                    } else JSONObject().put("status", "REJECTED_ALIGNMENT"))
                    .put("fullOrb", if (alignment.optBoolean("aligned")) {
                        compare(raw.bitmap, other.bitmap, innerOnly = false)
                    } else JSONObject().put("status", "REJECTED_ALIGNMENT"))
                    .put("dataSource", "PIXELCOPY_CAPTURE_DIFFERENCE_NOT_HUMAN_VISIBILITY"))
            }
        }
        val r06 = JSONObject()
        val full = byName["r06/R06_FULL.png"]
        if (full != null) listOf("G01", "G02", "A01", "A02", "C01").forEach { pathId ->
            val without = byName["r06/R06_WITHOUT_$pathId.png"] ?: return@forEach
            val alignment = capturePairAlignment(full, without, requireSameOpticalSample = true)
            val pixelComparison = if (alignment.optBoolean("aligned")) {
                compare(full.bitmap, without.bitmap, innerOnly = false)
            } else JSONObject().put("status", "REJECTED_ALIGNMENT")
            r06.put(pathId, pixelComparison
                .put("fullCaptureId", full.capture.captureId)
                .put("withoutCaptureId", without.capture.captureId)
                .put("alignment", alignment)
                .put("comparison", "R06_FULL minus R06_WITHOUT_$pathId")
                .put("contributionState", if (alignment.optBoolean("aligned") &&
                    pixelComparison.optString("status") == "MEASURED"
                ) "PIXEL_CONTRIBUTION_MEASURED_NOT_HUMAN_VISIBILITY" else "NOT_MEASURED_ALIGNMENT_REJECTED"))
        }
        return JSONObject()
            .put("r03", r03)
            .put("r03RouteQualified", routeQualified)
            .put("r03RouteQualification", if (routeQualified) "MAX_CANDIDATE_READY_REDUCED_MOTION_OFF" else "RUNTIME_ROUTE_NOT_QUALIFIED")
            .put("layerContributions", layerComparisons)
            .put("r06PerPathContributions", r06)
    }

    private fun capturePairAlignment(
        first: CapturedFile,
        second: CapturedFile,
        requireSameOpticalSample: Boolean,
    ): JSONObject {
        val a = first.metadata
        val b = second.metadata
        val reasons = mutableListOf<String>()
        if (a.optString("sourceCommitSha") != b.optString("sourceCommitSha")) reasons += "SOURCE_SHA"
        if (a.optString("traceId") != b.optString("traceId")) reasons += "TRACE_ID"
        if (a.optString("stage") != b.optString("stage")) reasons += "STAGE"
        if (abs(a.optDouble("stageProgress") - b.optDouble("stageProgress")) > .0001) reasons += "STAGE_PROGRESS"
        if (a.optLong("sceneElapsedMs", Long.MIN_VALUE) != b.optLong("sceneElapsedMs", Long.MAX_VALUE)) reasons += "SCENE_FRAME_TIME"
        if (a.optLong("geometryVersion", Long.MIN_VALUE) != b.optLong("geometryVersion", Long.MAX_VALUE)) reasons += "GEOMETRY"
        if (a.optString("visualProfile") != b.optString("visualProfile")) reasons += "VISUAL_PROFILE"
        if (a.optString("pixelCopySourceRectWindow") != b.optString("pixelCopySourceRectWindow")) reasons += "CROP_RECT"
        val stateA = a.optJSONObject("submittedRenderState") ?: JSONObject()
        val stateB = b.optJSONObject("submittedRenderState") ?: JSONObject()
        listOf("sessionIdHash", "attemptIdHash", "imageIdHash").forEach { field ->
            if (stateA.optString(field) != stateB.optString(field)) reasons += field.uppercase()
        }
        if (requireSameOpticalSample && a.optLong("opticalSampleMs", Long.MIN_VALUE) != b.optLong("opticalSampleMs", Long.MAX_VALUE)) {
            reasons += "OPTICAL_SAMPLE"
        }
        if (first.bitmap.width != second.bitmap.width || first.bitmap.height != second.bitmap.height) reasons += "DIMENSIONS"
        return JSONObject()
            .put("aligned", reasons.isEmpty())
            .put("rejectedReasons", JSONArray(reasons))
            .put("opticalSamplesEqual", a.optLong("opticalSampleMs", Long.MIN_VALUE) == b.optLong("opticalSampleMs", Long.MAX_VALUE))
            .put("sameWindowCrop", a.optString("pixelCopySourceRectWindow") == b.optString("pixelCopySourceRectWindow"))
    }

    private fun compare(a: Bitmap, b: Bitmap, innerOnly: Boolean, edgeOnly: Boolean = false): JSONObject {
        if (a.width != b.width || a.height != b.height) return JSONObject().put("status", "DIMENSION_MISMATCH")
        val left = if (innerOnly) a.width / 4 else 0
        val right = if (innerOnly) a.width * 3 / 4 else a.width
        val top = if (innerOnly) a.height / 4 else 0
        val bottom = if (innerOnly) a.height * 3 / 4 else a.height
        var changed = 0
        var absolute = 0L
        var maximum = 0
        var lumA = 0.0
        var lumB = 0.0
        var count = 0
        var minX = right
        var minY = bottom
        var maxX = left
        var maxY = top
        val roiWidth = right - left
        val roiHeight = bottom - top
        val changedMask = BooleanArray(roiWidth * roiHeight)
        val centerLeft = a.width / 4
        val centerRight = a.width * 3 / 4
        val centerTop = a.height / 4
        val centerBottom = a.height * 3 / 4
        for (y in top until bottom) for (x in left until right) {
            if (edgeOnly && x in centerLeft until centerRight && y in centerTop until centerBottom) continue
            val ca = a.getPixel(x, y)
            val cb = b.getPixel(x, y)
            val d = maxOf(abs((ca ushr 16 and 255) - (cb ushr 16 and 255)),
                abs((ca ushr 8 and 255) - (cb ushr 8 and 255)), abs((ca and 255) - (cb and 255)))
            if (d > 2) {
                changed++
                changedMask[(y - top) * roiWidth + (x - left)] = true
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
            maximum = max(maximum, d)
            absolute += abs((ca ushr 16 and 255) - (cb ushr 16 and 255)).toLong()
            absolute += abs((ca ushr 8 and 255) - (cb ushr 8 and 255)).toLong()
            absolute += abs((ca and 255) - (cb and 255)).toLong()
            lumA += luminance(ca); lumB += luminance(cb); count++
        }
        val pixelCount = count
        var largestComponentPixels = 0
        var largestComponentExtent = 0
        val visited = BooleanArray(changedMask.size)
        val queue = IntArray(changedMask.size.coerceAtLeast(1))
        for (index in changedMask.indices) {
            if (!changedMask[index] || visited[index]) continue
            var head = 0
            var tail = 0
            queue[tail++] = index
            visited[index] = true
            var componentCount = 0
            var cMinX = index % roiWidth
            var cMaxX = cMinX
            var cMinY = index / roiWidth
            var cMaxY = cMinY
            while (head < tail) {
                val current = queue[head++]
                val cx = current % roiWidth
                val cy = current / roiWidth
                componentCount++
                cMinX = minOf(cMinX, cx); cMaxX = maxOf(cMaxX, cx)
                cMinY = minOf(cMinY, cy); cMaxY = maxOf(cMaxY, cy)
                for (ny in max(0, cy - 1)..minOf(roiHeight - 1, cy + 1)) {
                    for (nx in max(0, cx - 1)..minOf(roiWidth - 1, cx + 1)) {
                        val neighbor = ny * roiWidth + nx
                        if (changedMask[neighbor] && !visited[neighbor]) {
                            visited[neighbor] = true
                            queue[tail++] = neighbor
                        }
                    }
                }
            }
            largestComponentPixels = max(largestComponentPixels, componentCount)
            largestComponentExtent = max(largestComponentExtent, max(cMaxX - cMinX + 1, cMaxY - cMinY + 1))
        }
        val meanA = if (count == 0) 0.0 else lumA / count
        val meanB = if (count == 0) 0.0 else lumB / count
        val meanScale = if (meanB > .001) meanA / meanB else 1.0
        var normalizedAbsolute = 0.0
        if (meanA > .001) for (y in top until bottom) for (x in left until right) {
            if (edgeOnly && x in centerLeft until centerRight && y in centerTop until centerBottom) continue
            normalizedAbsolute += abs(luminance(a.getPixel(x, y)) - luminance(b.getPixel(x, y)) * meanScale)
        }
        return JSONObject()
            .put("status", "MEASURED")
            .put("roi", when {
                innerOnly -> "INNER_50_PERCENT"
                edgeOnly -> "OUTER_RING_EXCLUDING_CENTER_50_PERCENT"
                else -> "FULL_ORB_CROP"
            })
            .put("changedPixelCount", changed)
            .put("changedPixelRatio", if (pixelCount == 0) 0.0 else changed.toDouble() / pixelCount)
            .put("meanAbsoluteRgbDifference", if (count == 0) 0.0 else absolute.toDouble() / (count * 3.0))
            .put("normalizedLumaDifference", if (count == 0) 0.0 else normalizedAbsolute / count)
            .put("meanLuminanceA", meanA).put("meanLuminanceB", meanB)
            .put("globalLuminanceDelta", meanB - meanA)
            .put("maximumChannelDifference", maximum)
            .put("largestConnectedComponentPixels", largestComponentPixels)
            .put("largestConnectedComponentExtentPx", largestComponentExtent)
            .put("changedBounds", if (changed == 0) JSONObject.NULL else JSONObject()
                .put("left", minX).put("top", minY).put("right", maxX).put("bottom", maxY))
    }

    /** Estimates local texture displacement after removing per-patch luminance offsets. */
    private fun estimateLocalTextureMotion(a: Bitmap, b: Bitmap): JSONObject {
        if (a.width != b.width || a.height != b.height) return JSONObject().put("status", "DIMENSION_MISMATCH")
        val roiLeft = a.width / 4
        val roiRight = a.width * 3 / 4
        val roiTop = a.height / 4
        val roiBottom = a.height * 3 / 4
        val patchRadius = 3
        val patchSize = patchRadius * 2 + 1
        val patchPixels = patchSize * patchSize
        val searchRadius = 6
        val stride = 10
        val offsetsX = ArrayList<Int>()
        val offsetsY = ArrayList<Int>()
        var zeroCorrelationSum = 0.0
        var bestCorrelationSum = 0.0
        var improvementSum = 0.0
        var candidateBlocks = 0
        var acceptedBlocks = 0

        fun patchSums(bitmap: Bitmap, cx: Int, cy: Int): Pair<Double, Double> {
            var sum = 0.0
            var sumSquares = 0.0
            for (dy in -patchRadius..patchRadius) for (dx in -patchRadius..patchRadius) {
                val value = luminance(bitmap.getPixel(cx + dx, cy + dy))
                sum += value
                sumSquares += value * value
            }
            return sum to sumSquares
        }

        fun correlation(
            reference: Bitmap,
            candidate: Bitmap,
            ax: Int,
            ay: Int,
            bx: Int,
            by: Int,
            referenceSums: Pair<Double, Double>,
            candidateSums: Pair<Double, Double>,
        ): Double {
            val meanA = referenceSums.first / patchPixels
            val meanB = candidateSums.first / patchPixels
            val varianceA = (referenceSums.second - referenceSums.first * meanA).coerceAtLeast(0.0)
            val varianceB = (candidateSums.second - candidateSums.first * meanB).coerceAtLeast(0.0)
            val denominator = kotlin.math.sqrt(varianceA * varianceB)
            if (denominator <= 1e-6) return -1.0
            var cross = 0.0
            for (dy in -patchRadius..patchRadius) for (dx in -patchRadius..patchRadius) {
                cross += luminance(reference.getPixel(ax + dx, ay + dy)) *
                    luminance(candidate.getPixel(bx + dx, by + dy))
            }
            return ((cross - referenceSums.first * candidateSums.first / patchPixels) / denominator)
                .coerceIn(-1.0, 1.0)
        }

        val minCenterX = roiLeft + patchRadius + searchRadius
        val maxCenterX = roiRight - patchRadius - searchRadius
        val minCenterY = roiTop + patchRadius + searchRadius
        val maxCenterY = roiBottom - patchRadius - searchRadius
        for (y in minCenterY..maxCenterY step stride) for (x in minCenterX..maxCenterX step stride) {
            candidateBlocks++
            val referenceSums = patchSums(a, x, y)
            val referenceVariance = referenceSums.second - referenceSums.first * referenceSums.first / patchPixels
            if (referenceVariance < 80.0) continue
            var bestScore = -1.0
            var bestDx = 0
            var bestDy = 0
            var zeroScore = -1.0
            for (dy in -searchRadius..searchRadius) for (dx in -searchRadius..searchRadius) {
                val candidateSums = patchSums(b, x + dx, y + dy)
                val score = correlation(a, b, x, y, x + dx, y + dy, referenceSums, candidateSums)
                if (dx == 0 && dy == 0) zeroScore = score
                if (score > bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
            val improvement = bestScore - zeroScore
            if (bestScore < .15 || improvement < .02) continue
            acceptedBlocks++
            offsetsX += bestDx
            offsetsY += bestDy
            zeroCorrelationSum += zeroScore
            bestCorrelationSum += bestScore
            improvementSum += improvement
        }

        if (acceptedBlocks == 0) return JSONObject()
            .put("status", "NO_TEXTURE_MATCH")
            .put("method", "LOCAL_NORMALIZED_CROSS_CORRELATION")
            .put("candidateBlocks", candidateBlocks)
            .put("acceptedBlocks", 0)
            .put("validBlockRatio", 0.0)

        val medianDx = offsetsX.sorted()[acceptedBlocks / 2]
        val medianDy = offsetsY.sorted()[acceptedBlocks / 2]
        val magnitudes = offsetsX.indices.map { index -> kotlin.math.hypot(offsetsX[index].toDouble(), offsetsY[index].toDouble()) }
        val meanMagnitude = magnitudes.average()
        val vectorMeanX = offsetsX.average()
        val vectorMeanY = offsetsY.average()
        val directionCoherence = if (meanMagnitude <= 1e-6) 0.0 else
            (kotlin.math.hypot(vectorMeanX, vectorMeanY) / meanMagnitude).coerceIn(0.0, 1.0)
        return JSONObject()
            .put("status", "MEASURED_ESTIMATE")
            .put("method", "LOCAL_NORMALIZED_CROSS_CORRELATION")
            .put("interpretation", "LOCAL_TEXTURE_MATCH_OFFSETS_NOT_GPU_FLOW_READBACK_OR_HUMAN_VISIBILITY")
            .put("candidateBlocks", candidateBlocks)
            .put("acceptedBlocks", acceptedBlocks)
            .put("validBlockRatio", if (candidateBlocks == 0) 0.0 else acceptedBlocks.toDouble() / candidateBlocks)
            .put("medianMatchOffsetPx", JSONObject().put("x", medianDx).put("y", medianDy))
            .put("meanMatchOffsetMagnitudePx", meanMagnitude)
            .put("directionCoherence", directionCoherence)
            .put("meanZeroShiftCorrelation", zeroCorrelationSum / acceptedBlocks)
            .put("meanBestShiftCorrelation", bestCorrelationSum / acceptedBlocks)
            .put("meanCorrelationImprovement", improvementSum / acceptedBlocks)
    }

    private fun luminance(color: Int): Double =
        .2126 * (color ushr 16 and 255) + .7152 * (color ushr 8 and 255) + .0722 * (color and 255)

    private fun anonymize(value: String): String = sha256(value.toByteArray(Charsets.UTF_8)).take(20)

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value).joinToString("") { "%02x".format(it) }

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val CAPTURE_TIMEOUT_MS = 3_000L
}
