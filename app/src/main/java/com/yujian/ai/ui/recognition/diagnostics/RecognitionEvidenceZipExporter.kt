package com.yujian.ai.ui.recognition.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.DisplayMetrics
import androidx.core.content.FileProvider
import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.waterlight.renderer.OrbStage02RenderTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class RecognitionEvidenceZipExportResult(
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val integrityStatus: String,
    val sourceSha: String,
)

object RecognitionEvidenceZipExporter {
    suspend fun export(context: Context, recorder: RecognitionSessionEvidenceRecorder): RecognitionEvidenceZipExportResult =
        withContext(Dispatchers.IO) {
            check(BuildConfig.DEBUG) { "Evidence ZIP export is available only in Debug/QA builds" }
            check(recorder.flush()) { "Event queue did not flush" }
            val attemptDirectory = requireNotNull(recorder.activeAttemptDirectory()) { "No active recognition Attempt" }
            val summary = recorder.summaryJson()
            val captureIndex = recorder.captureIndexJson()
            val sessionFolder = File(attemptDirectory, "session").apply { mkdirs() }
            val framesFolder = File(attemptDirectory, "frames").apply { mkdirs() }
            val logsFolder = File(attemptDirectory, "logs").apply { mkdirs() }
            val reportsFolder = File(attemptDirectory, "reports").apply { mkdirs() }

            File(sessionFolder, "session_summary.json").writeText(summary.toString(2), Charsets.UTF_8)
            File(sessionFolder, "stage_timeline.json").writeText(stageTimelineJson(summary).toString(2), Charsets.UTF_8)
            File(framesFolder, "frame_index.json").writeText(captureIndex.toString(2), Charsets.UTF_8)
            File(logsFolder, "orb_render_trace.jsonl").writeText(OrbStage02RenderTrace.snapshotJsonLines(), Charsets.UTF_8)
            captureLogcat(Process.myPid(), File(logsFolder, "logcat.txt"))

            val successfulFrames = captureIndex.filesFrom(attemptDirectory).filter(File::isFile)
            RecognitionEvidenceContactSheet.create(
                successfulFrames,
                File(framesFolder, "screenshot_contact_sheet.jpg"),
            )
            val buildInfo = buildInfoJson(context)
            File(attemptDirectory, "build_info.json").writeText(buildInfo.toString(2), Charsets.UTF_8)
            val deviceInfo = deviceInfoJson(context)
            File(attemptDirectory, "device_info.json").writeText(deviceInfo.toString(2), Charsets.UTF_8)
            writeSummaryReports(context, attemptDirectory, summary, captureIndex, recorder.diagnostics())

            ensureExpectedFiles(logsFolder, reportsFolder)
            val filesBeforeManifest = listFiles(attemptDirectory).filter { it.name != "manifest.json" }
            val fileHashes = JSONObject()
            filesBeforeManifest.forEach { fileHashes[relativePath(attemptDirectory, it)] = sha256(file) }
            val integrityStatus = evidenceStatus(summary, captureIndex, recorder.diagnostics(), filesBeforeManifest)
            val identity = recorder.exportIdentity()
            val manifest = JSONObject()
                .put("schemaVersion", 1)
                .put("sourceSha", BuildConfig.SOURCE_REVISION)
                .put("apkSha256", buildInfo.optJSONObject("installedApks")?.optJSONObject("base")?.optString("sha256") ?: "NOT_RECORDED")
                .put("apkSizeBytes", buildInfo.optJSONObject("installedApks")?.optJSONObject("base")?.optLong("sizeBytes") ?: -1L)
                .put("installedApks", buildInfo.optJSONObject("installedApks") ?: JSONObject())
                .put("buildType", BuildConfig.BUILD_TYPE)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("deviceModel", Build.MODEL)
                .put("androidVersion", Build.VERSION.RELEASE)
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("screenResolution", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
                .put("density", context.resources.displayMetrics.density)
                .put("sessionIdHash", identity?.let { sha256(it.sessionId) } ?: "NOT_RECORDED")
                .put("attemptIdHash", identity?.let { sha256(it.attemptId) } ?: "NOT_RECORDED")
                .put("imageIdHash", identity?.let { sha256(it.imageId) } ?: "NOT_RECORDED")
                .put("photoSha256", identity?.photoSha256 ?: "NOT_RECORDED")
                .put("firstFrameTimestampMs", summary.optLong("firstFrameTimestampMs", -1L))
                .put("recognitionDurationMs", summary.optLong("recognitionDurationMs", -1L))
                .put("completedStageCount", summary.optInt("completedStageCount"))
                .put("screenshotRequestCount", summary.optInt("screenshotRequestCount"))
                .put("screenshotSuccessCount", summary.optInt("screenshotSuccessCount"))
                .put("screenshotFailureCount", summary.optInt("screenshotFailureCount"))
                .put("eventCount", summary.optLong("eventCount"))
                .put("droppedEventCount", summary.optLong("droppedCriticalEventCount") + summary.optLong("droppedSampleEventCount"))
                .put("evidenceIntegrityStatus", integrityStatus)
                .put("modelAssetSha256", buildInfo.optJSONObject("modelAssets") ?: JSONObject().put("status", "NOT_RECORDED"))
                .put("fileSha256", fileHashes)
            File(attemptDirectory, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)

            val diagnosticDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val sessionId = identity?.let { sha256(it.sessionId).take(12) } ?: "unknown"
            val archive = File(diagnosticDir, "YuJian_Recognition_Evidence_${sessionId}.zip")
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                listFiles(attemptDirectory).forEach { source ->
                    val relative = relativePath(attemptDirectory, source)
                    zip.putNextEntry(ZipEntry(relative))
                    FileInputStream(source).buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            verifyArchive(archive, fileHashes)
            val archiveHash = sha256(archive)
            File(diagnosticDir, archive.name + ".sha256").writeText("$archiveHash  ${archive.name}\n", Charsets.UTF_8)
            RecognitionEvidenceZipExportResult(
                file = archive,
                sha256 = archiveHash,
                sizeBytes = archive.length(),
                integrityStatus = integrityStatus,
                sourceSha = BuildConfig.SOURCE_REVISION,
            )
        }

    fun share(context: Context, result: RecognitionEvidenceZipExportResult) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", result.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "本次识鱼诊断包 · ${result.integrityStatus}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, result.file.name, uri)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "导出本次识鱼诊断包"))
    }

    private fun writeSummaryReports(
        context: Context,
        attemptDirectory: File,
        summary: JSONObject,
        captureIndex: JSONArray,
        queue: RecognitionEvidenceQueueDiagnostics,
    ) {
        val reports = File(attemptDirectory, "reports")
        val modelRuntime = lastJsonLine(File(attemptDirectory, "logs/model_inference.jsonl"))
        val maskRuntime = lastJsonLine(File(attemptDirectory, "logs/mask_runtime.jsonl"))
        val classifierContract = runCatching {
            context.assets.open("model_release_contract.json").use { input ->
                JSONObject(input.readBytes().toString(Charsets.UTF_8))
            }
        }.getOrNull()
        val modelSummary = JSONObject()
            .put("source", "runtime model_inference.jsonl, mask_runtime.jsonl, and packaged model_release_contract.json")
            .put("detectorStatus", recordedString(modelRuntime, "detectorStatus"))
            .put("detectorVersion", recordedString(modelRuntime, "detectorVersion"))
            .put("detectorSha256", recordedString(modelRuntime, "detectorSha256"))
            .put("detectorInputTensorShape", modelRuntime.optJSONArray("detectorInputTensorShape") ?: JSONArray().put("NOT_RECORDED"))
            .put("detectorOutputTensorShape", modelRuntime.optJSONArray("detectorOutputTensorShape") ?: JSONArray().put("NOT_RECORDED"))
            .put("orientationRetryPolicy", recordedString(modelRuntime, "orientationRetryPolicy"))
            .put("orientationAttempts", modelRuntime.optJSONArray("orientationAttempts") ?: JSONArray().put("NOT_RECORDED"))
            .put("orientationSelectedAttempt", recordedString(modelRuntime, "orientationSelectedAttempt"))
            .put("bbox", modelRuntime.optJSONArray("bbox") ?: JSONArray().put("NOT_RECORDED"))
            .put("crop", modelRuntime.optJSONArray("crop") ?: JSONArray().put("NOT_RECORDED"))
            .put("classifierStatus", recordedString(modelRuntime, "classifierStatus"))
            .put("classifierVersion", recordedString(modelRuntime, "classifierVersion"))
            .put("classifierSha256", recordedString(modelRuntime, "classifierSha256"))
            .put("classifierInputShape", classifierContract?.optJSONArray("input_shape") ?: JSONArray().put("NOT_RECORDED"))
            .put("classifierOutputShape", classifierContract?.optJSONArray("output_shape") ?: JSONArray().put("NOT_RECORDED"))
            .put("classifierInputBitmapSize", modelRuntime.optJSONArray("classifierInputSize") ?: JSONArray().put("NOT_RECORDED"))
            .put("top1", recordedString(modelRuntime, "top1"))
            .put("confidence", modelRuntime.opt("confidence") ?: "NOT_RECORDED")
            .put("slimSamStatus", recordedString(modelRuntime, "slimSamStatus"))
            .put("slimSamVersion", "NOT_RECORDED")
            .put("slimSamSha256", recordedString(modelRuntime, "slimSamModelSha256"))
            .put("maskStatus", recordedString(maskRuntime, "status"))
            .put("maskSha256", recordedString(maskRuntime, "maskSha256"))
            .put("maskVersion", maskRuntime.opt("maskVersion") ?: "NOT_RECORDED")
            .put("maskGeometryVersion", maskRuntime.opt("geometryVersion") ?: "NOT_RECORDED")
            .put("maskFallback", recordedString(maskRuntime, "fallback"))
            .put("maskFallbackReason", recordedString(maskRuntime, "fallbackReason"))
            .put("classifierPackagedModelId", recordedString(classifierContract, "model_id"))
            .put("classifierPackagedSha256", recordedString(classifierContract, "sha256"))
            .put("classifierPackagedInputShape", classifierContract?.optJSONArray("input_shape") ?: JSONArray().put("NOT_RECORDED"))
            .put("classifierPackagedOutputShape", classifierContract?.optJSONArray("output_shape") ?: JSONArray().put("NOT_RECORDED"))
        File(reports, "model_summary.json").writeText(modelSummary.toString(2), Charsets.UTF_8)
        File(reports, "r03_rotation_summary.json").writeText(
            JSONObject()
                .put("profile", "OrbCoreRotationProfileV1")
                .put("enabled", true)
                .put("periodMs", 7_000)
                .put("phaseSource", "WaterLightFrame.elapsedMs")
                .put("shaderPixelsMeasured", false)
                .put("pixelEvidenceStatus", "NOT_DEVICE_VERIFIED")
                .toString(2), Charsets.UTF_8,
        )
        File(reports, "r06_summary.json").writeText(
            JSONObject()
                .put("residentPathIds", JSONArray(listOf("G01", "G02", "A01", "A02", "C01")))
                .put("roles", "RESIDENT_AMBIENT")
                .put("renderTraceFile", "logs/orb_render_trace.jsonl")
                .put("pixelContributionMeasured", false)
                .put("pixelEvidenceStatus", "NOT_DEVICE_VERIFIED")
                .toString(2), Charsets.UTF_8,
        )
        File(reports, "resident_lifecycle_summary.json").writeText(
            JSONObject()
                .put("lifecycle", "OFF -> WAKE_IN -> ACTIVE -> DISSOLVE -> DISPOSED")
                .put("stageStatuses", summary.optJSONArray("stageStatuses") ?: JSONArray())
                .put("taskCompletionControlsAlpha", false)
                .toString(2), Charsets.UTF_8,
        )
        File(reports, "task_energy_summary.json").writeText(
            JSONObject()
                .put("sourceFile", "logs/waterlight_paths.jsonl")
                .put("mergeEvent", "TASK_ENERGY_MERGED")
                .put("pathSystem", "WaterLightPathLedger")
                .toString(2), Charsets.UTF_8,
        )
        File(reports, "capture_integrity.json").writeText(
            JSONObject()
                .put("screenshotRequestCount", summary.optInt("screenshotRequestCount"))
                .put("screenshotSuccessCount", summary.optInt("screenshotSuccessCount"))
                .put("screenshotFailureCount", summary.optInt("screenshotFailureCount"))
                .put("captureIndexCount", captureIndex.length())
                .put("droppedCriticalEventCount", queue.droppedCriticalEvents)
                .put("droppedSampleEventCount", queue.droppedSampleEvents)
                .put("writeFailures", queue.writeFailures)
                .put("incompleteReasons", JSONArray(queue.incompleteReasons))
                .toString(2), Charsets.UTF_8,
        )
    }

    private fun evidenceStatus(
        summary: JSONObject,
        captureIndex: JSONArray,
        queue: RecognitionEvidenceQueueDiagnostics,
        files: List<File>,
    ): String {
        val hasEvents = files.any { it.name == "recognition_events.jsonl" && it.length() > 0L }
        val hasStages = files.any { it.name == "stage_timeline.jsonl" && it.length() > 0L }
        val enteredStages = summary.optJSONArray("stageStatuses") ?: JSONArray()
        val successfulEntries = buildSet {
            for (i in 0 until captureIndex.length()) {
                val row = captureIndex.optJSONObject(i) ?: continue
                if (row.optString("captureType") == "STAGE_ENTRY" && row.optString("pixelCopyStatus") == "SUCCESS") {
                    add(row.optString("stage"))
                }
            }
        }
        var missingEntry = false
        for (i in 0 until enteredStages.length()) {
            val row = enteredStages.optJSONObject(i) ?: continue
            if (row.optString("status") in setOf("ENTERED", "COMPLETED", "BLOCKED") &&
                row.optString("stage") !in successfulEntries
            ) missingEntry = true
        }
        return if (
            !hasEvents || !hasStages || missingEntry || summary.optInt("screenshotFailureCount") > 0 ||
            queue.droppedCriticalEvents > 0 || queue.writeFailures > 0 || queue.incompleteReasons.isNotEmpty()
        ) "EVIDENCE_INCOMPLETE" else "COMPLETE"
    }

    private fun ensureExpectedFiles(logs: File, reports: File) {
        listOf(
            "recognition_events.jsonl", "orb_render_trace.jsonl", "model_inference.jsonl", "mask_runtime.jsonl",
            "waterlight_paths.jsonl", "result_handoff.jsonl", "stage_timeline.jsonl", "logcat.txt",
        ).forEach { name -> File(logs, name).takeIf { !it.exists() }?.writeText("", Charsets.UTF_8) }
        listOf(
            "model_summary.json", "r03_rotation_summary.json", "r06_summary.json",
            "resident_lifecycle_summary.json", "task_energy_summary.json", "capture_integrity.json",
        ).forEach { name -> File(reports, name).takeIf { !it.exists() }?.writeText("{}\n", Charsets.UTF_8) }
    }

    private fun stageTimelineJson(summary: JSONObject): JSONObject = JSONObject()
        .put("source", "RecognitionSceneAnimationController")
        .put("stages", summary.optJSONArray("stageStatuses") ?: JSONArray())

    private fun buildInfoJson(context: Context): JSONObject {
        val installed = JSONObject()
        val appInfo = context.applicationInfo
        val base = File(appInfo.sourceDir.orEmpty())
        installed.put(
            "base",
            JSONObject()
                .put("path", base.name.ifBlank { "NOT_RECORDED" })
                .put("sha256", if (base.isFile) sha256(base) else "NOT_RECORDED")
                .put("sizeBytes", if (base.isFile) base.length() else -1L),
        )
        val splitArray = JSONArray()
        appInfo.splitSourceDirs.orEmpty().forEach { path ->
            val split = File(path)
            splitArray.put(
                JSONObject()
                    .put("name", split.name)
                    .put("sha256", if (split.isFile) sha256(split) else "NOT_RECORDED")
                    .put("sizeBytes", if (split.isFile) split.length() else -1L),
            )
        }
        val assets = JSONArray()
        listAssets(context, "").filter { it.substringAfterLast('.', "") in MODEL_EXTENSIONS }.forEach { asset ->
            val digest = runCatching {
                context.assets.open(asset).use { input -> sha256(input) }
            }.getOrElse { "NOT_RECORDED" }
            assets.put(JSONObject().put("path", asset).put("sha256", digest))
        }
        return JSONObject()
            .put("sourceSha", BuildConfig.SOURCE_REVISION)
            .put("buildType", BuildConfig.BUILD_TYPE)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("buildTimestampUtc", BuildConfig.BUILD_TIMESTAMP_UTC)
            .put("installedApks", JSONObject().put("base", installed.optJSONObject("base")).put("splits", splitArray))
            .put("modelAssets", if (assets.length() == 0) JSONObject().put("status", "NOT_RECORDED") else JSONObject().put("assets", assets))
    }

    private fun deviceInfoJson(context: Context): JSONObject {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return JSONObject()
            .put("deviceModel", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("screenWidthPx", metrics.widthPixels)
            .put("screenHeightPx", metrics.heightPixels)
            .put("density", metrics.density)
            .put("densityDpi", metrics.densityDpi)
    }

    private fun captureLogcat(pid: Int, target: File) {
        runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid")
                .redirectErrorStream(true).start()
            val content = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            target.writeText(content, Charsets.UTF_8)
        }.onFailure { target.writeText("LOGCAT_NOT_RECORDED:${it.javaClass.simpleName}\n", Charsets.UTF_8) }
    }

    private fun lastJsonLine(file: File): JSONObject = runCatching {
        file.useLines { lines ->
            lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }.lastOrNull()
                ?: JSONObject()
        }
    }.getOrDefault(JSONObject())

    private fun recordedString(source: JSONObject?, key: String): String =
        source?.optString(key).orEmpty().takeIf { it.isNotBlank() && it != "NOT_RECORDED" && it != "null" }
            ?: "NOT_RECORDED"

    private fun listAssets(context: Context, path: String): List<String> {
        val entries = runCatching { context.assets.list(path).orEmpty() }.getOrDefault(emptyArray())
        if (entries.isEmpty()) return if (path.isNotBlank()) listOf(path) else emptyList()
        return entries.flatMap { name ->
            val child = if (path.isBlank()) name else "$path/$name"
            val nested = runCatching { context.assets.list(child).orEmpty() }.getOrDefault(emptyArray())
            if (nested.isEmpty()) listOf(child) else listAssets(context, child)
        }
    }

    private fun JSONArray.filesFrom(attemptDirectory: File): List<File> = buildList {
        for (index in 0 until length()) {
            val path = optJSONObject(index)?.optString("relativePath") ?: continue
            add(File(attemptDirectory, path))
        }
    }

    private fun listFiles(root: File): List<File> = root.walkTopDown().filter(File::isFile).toList()

    private fun relativePath(root: File, child: File): String = child.relativeTo(root).invariantSeparatorsPath

    private fun verifyArchive(archive: File, expected: JSONObject) {
        ZipFile(archive).use { zip ->
            val keys = expected.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val entry = zip.getEntry(path) ?: error("ZIP_MISSING_ENTRY:$path")
                val actual = zip.getInputStream(entry).use { sha256(it) }
                check(actual == expected.getString(path)) { "ZIP_HASH_MISMATCH:$path" }
            }
        }
    }

    private fun sha256(file: File): String = FileInputStream(file).use(::sha256)

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(input).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private val MODEL_EXTENSIONS = setOf("onnx", "tflite", "task", "model", "bin")
}
