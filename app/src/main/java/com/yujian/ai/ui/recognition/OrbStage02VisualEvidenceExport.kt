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
import androidx.core.content.FileProvider
import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.designsystem.components.aiorb.OrbRenderIsolationMode
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.OrbStage02LayerIsolation
import com.yujian.ai.ui.waterlight.renderer.OrbStage02MaxVisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.OrbR06PathDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Debug-only PixelCopy export of the current Orb crop, sampled at one paused scene frame. */
object OrbStage02VisualEvidenceExport {
    data class Metadata(
        val sessionId: String,
        val attemptId: String,
        val imageId: String,
        val stage: RecognitionSceneStage,
        val stageProgress: Float,
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
        val actualFluidApplied: Boolean,
        val r06BackPathIds: Set<String>,
        val r06BackTransmissionPathIds: Set<String>,
        val r06FrontPathIds: Set<String>,
        val r06PathDiagnostics: List<OrbR06PathDiagnostic>,
    )

    data class Capture(val mode: OrbRenderIsolationMode, val layer: OrbStage02LayerIsolation)

    suspend fun share(
        context: Context,
        view: View,
        orbBoundsInView: Rect,
        metadata: Metadata,
        render: suspend (Capture) -> Unit,
    ) {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val outputs = listOf(
            "01_RAW_CORE.png" to Capture(OrbRenderIsolationMode.RAW_CORE, OrbStage02LayerIsolation.R03_R06),
            "02_R03_ONLY.png" to Capture(OrbRenderIsolationMode.NORMAL, OrbStage02LayerIsolation.R03_ONLY),
            "03_R06_ONLY.png" to Capture(OrbRenderIsolationMode.NORMAL, OrbStage02LayerIsolation.R06_ONLY),
            "04_R06_BACK_TRANSMISSION.png" to Capture(
                OrbRenderIsolationMode.NORMAL, OrbStage02LayerIsolation.R06_BACK_TRANSMISSION_ONLY,
            ),
            "05_FULL_COMPOSITE.png" to Capture(OrbRenderIsolationMode.NORMAL, OrbStage02LayerIsolation.R03_R06),
        )
        val zipFile = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "diagnostics/orb-visual-evidence").apply { mkdirs() }
            val output = File(directory, "orb-stage02-${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                withContext(Dispatchers.Main.immediate) {
                    outputs.forEach { (name, capture) ->
                        render(capture)
                        val bitmap = pixelCopy(view, orbBoundsInView)
                        val bytes = ByteArrayOutputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            bitmap.recycle()
                            stream.toByteArray()
                        }
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("orb_visual_diagnostic.json"))
                zip.write(metadataJson(metadata).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            output
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "导出Orb真实渲染证据"))
    }

    private suspend fun pixelCopy(view: View, bounds: Rect): Bitmap = suspendCancellableCoroutine { continuation ->
        val activity = view.context.findActivity()
        requireNotNull(activity) { "Orb pixel evidence requires an Activity window" }
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val sourceRect = Rect(
            location[0] + bounds.left,
            location[1] + bounds.top,
            location[0] + bounds.right,
            location[1] + bounds.bottom,
        )
        val target = Bitmap.createBitmap(sourceRect.width(), sourceRect.height(), Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, sourceRect, target, { result ->
            if (result == PixelCopy.SUCCESS) continuation.resume(target)
            else {
                target.recycle()
                continuation.resumeWithException(IllegalStateException("PixelCopy failed: $result"))
            }
        }, Handler(Looper.getMainLooper()))
        continuation.invokeOnCancellation { if (!target.isRecycled) target.recycle() }
    }

    private fun metadataJson(value: Metadata): String = JSONObject()
        .put("sourceCommitSha", BuildConfig.SOURCE_REVISION)
        .put("sessionId", value.sessionId)
        .put("attemptId", value.attemptId)
        .put("imageId", value.imageId)
        .put("stage", value.stage.name)
        .put("stageProgress", value.stageProgress.coerceIn(0f, 1f))
        .put("geometryVersion", value.geometryVersion)
        .put("visualProfile", value.visualProfile.name)
        .put("renderIsolationModeAtExport", value.renderIsolationMode.name)
        .put("layerIsolationAtExport", value.layerIsolation.name)
        .put("shaderRoute", value.shaderRoute)
        .put("shaderStatus", value.shaderStatus)
        .put("coreSizePx", value.coreSizePx)
        .put("reducedMotion", value.reducedMotion)
        .put("lowPerformance", value.lowPerformance)
        .put("r03", JSONObject()
            .put("maxDisplacementD", OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D)
            .put("interiorMixMax", OrbStage02MaxVisualProfile.FLUID_INTERIOR_MIX_MAX)
            .put("wakeFlowStrength", value.interiorFlowStrength)
            .put("wakeDisplacementD", value.interiorDisplacementD)
            .put("actualFluidApplied", value.actualFluidApplied)
            .put("outlineDisplacementD", OrbStage02MaxVisualProfile.FLUID_OUTLINE_DISPLACEMENT_D))
        .put("r06", JSONObject()
            .put("glassBackTransmissionMax", OrbStage02MaxVisualProfile.GLASS_BACK_TRANSMISSION_MAX)
            .put("glassRefractionMaxD", OrbStage02MaxVisualProfile.GLASS_BACK_REFRACTION_MAX_D)
            .put("glassTransmissionEnabled", value.visualProfile == OrbStage02VisualProfile.MAX)
            .put("backDrawnPathIds", JSONArray(value.r06BackPathIds.sorted()))
            .put("backGlassTransmittedPathIds", JSONArray(value.r06BackTransmissionPathIds.sorted()))
            .put("frontDrawnPathIds", JSONArray(value.r06FrontPathIds.sorted()))
            .put("visiblePathCount", value.r06BackPathIds.union(value.r06BackTransmissionPathIds)
                .union(value.r06FrontPathIds).size)
            .put("pathDiagnostics", JSONArray().apply {
                value.r06PathDiagnostics.sortedWith(compareBy<OrbR06PathDiagnostic>({ it.renderPass.name }, { it.front }, { it.pathId }))
                    .forEach { metric ->
                        put(JSONObject()
                            .put("pathId", metric.pathId)
                            .put("front", metric.front)
                            .put("renderPass", metric.renderPass.name)
                            .put("stage", metric.stage.name)
                            .put("stageProgress", metric.stageProgress.toDouble())
                            .put("geometryVersion", metric.geometryVersion)
                            .put("validSamples", metric.validSampleCount)
                            .put("bodyValidSegments", metric.bodyValidSegmentCount)
                            .put("bodyDrawnRuns", metric.bodyDrawnRunCount)
                            .put("bodyPaintAlpha", metric.bodyPaintAlpha)
                            .put("bodyStrokeWidthPx", metric.bodyStrokeWidthPx.toDouble())
                            .put("bodyOriginalAlpha", metric.bodyOriginalAlpha.toDouble())
                            .put("bodyFinalAlpha", metric.bodyFinalAlpha.toDouble())
                            .put("bodyAddedAlpha", metric.bodyAddedAlpha.toDouble()))
                    }
            }))
        .toString(2)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
