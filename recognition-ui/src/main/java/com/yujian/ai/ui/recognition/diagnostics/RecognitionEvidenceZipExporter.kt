package com.yujian.ai.ui.recognition.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.net.Uri
import androidx.core.content.FileProvider
import com.yujian.ai.recognition.ui.BuildConfig
import com.yujian.ai.ui.waterlight.renderer.OrbStage02RenderTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class RecognitionEvidenceZipExportResult(
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val integrityStatus: String,
    val sourceSha: String,
    val degradedReasons: List<String> = emptyList(),
)

data class RecognitionEvidenceShareOutcome(
    val chooserLaunched: Boolean,
    val shareUri: String? = null,
    val errorReason: String? = null,
)

internal data class RecognitionEvidencePackagedArchive(
    val archive: File,
    val sha256: String,
    val sizeBytes: Long,
    val fileHashes: Map<String, String>,
)

internal data class LogcatCaptureResult(
    val status: String,
    val truncated: Boolean,
    val degradedReason: String? = null,
)

object RecognitionEvidenceZipExporter {
    suspend fun export(
        context: Context,
        recorder: RecognitionSessionEvidenceRecorder,
        flushTimeoutMs: Long = RecognitionSessionEvidenceRecorder.DEFAULT_FLUSH_TIMEOUT_MS,
        stage: String = "UNKNOWN",
    ): RecognitionEvidenceZipExportResult = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG) { "Evidence ZIP export is available only in Debug/QA builds" }
        val startedAt = SystemClock.elapsedRealtime()
        Log.i("RecognitionEvidenceExport", "EXPORT_SNAPSHOT_START stage=$stage atMs=$startedAt")
        val diagnosticDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        cleanupStaleTempArtifacts(diagnosticDir)
        val stagingDir = File(diagnosticDir, ".staging_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val snapshot = requireNotNull(recorder.createImmutableExportSnapshot(stagingDir, flushTimeoutMs)) {
                "No active recognition Attempt"
            }
            Log.i("RecognitionEvidenceExport", "EXPORT_SNAPSHOT_COMPLETE stage=$stage attempt=${snapshot.identity?.attemptId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt} degraded=${snapshot.degradedReasons}")
            val degradedReasons = snapshot.degradedReasons.toMutableList()
            val summary = JSONObject(snapshot.summary.toString())
            val captureIndex = JSONArray(snapshot.captureIndex.toString())
            val sessionFolder = File(stagingDir, "session").apply { mkdirs() }
            val framesFolder = File(stagingDir, "frames").apply { mkdirs() }
            val logsFolder = File(stagingDir, "logs").apply { mkdirs() }
            val reportsFolder = File(stagingDir, "reports").apply { mkdirs() }

            File(sessionFolder, "session_summary.json").writeText(summary.toString(2), Charsets.UTF_8)
            File(sessionFolder, "stage_timeline.json").writeText(stageTimelineJson(summary).toString(2), Charsets.UTF_8)
            File(framesFolder, "frame_index.json").writeText(captureIndex.toString(2), Charsets.UTF_8)

            val rawOrbTrace = OrbStage02RenderTrace.snapshotJsonLines().toByteArray(Charsets.UTF_8)
            val orbTraceBytes = if (rawOrbTrace.size <= MAX_ORB_TRACE_BYTES) {
                rawOrbTrace
            } else {
                degradedReasons += "ORB_RENDER_TRACE_TRUNCATED"
                tailAlignedUtf8Bytes(rawOrbTrace, MAX_ORB_TRACE_BYTES)
            }
            File(logsFolder, "orb_render_trace.jsonl").writeBytes(orbTraceBytes)

            val logcatOutcome = captureLogcat(Process.myPid(), File(logsFolder, "logcat.txt"))
            logcatOutcome.degradedReason?.let { degradedReasons += it }

            val successfulFrames = captureIndex.filesFrom(stagingDir).filter(File::isFile)
            if (!File(stagingDir, "mask/original_mask.png").isFile ||
                !File(stagingDir, "mask/alpha_statistics.json").isFile) {
                degradedReasons += "MASK_PIXEL_EVIDENCE_MISSING"
            }
            runCatching {
                RecognitionEvidenceContactSheet.create(
                    successfulFrames,
                    File(framesFolder, "screenshot_contact_sheet.jpg"),
                )
            }.onFailure { failure ->
                degradedReasons += "CONTACT_SHEET_FAILED:${failure.javaClass.simpleName}"
            }

            val buildInfo = buildInfoJson(context)
            File(stagingDir, "build_info.json").writeText(buildInfo.toString(2), Charsets.UTF_8)
            val deviceInfo = deviceInfoJson(context)
            File(stagingDir, "device_info.json").writeText(deviceInfo.toString(2), Charsets.UTF_8)
            val uniqueDegraded = degradedReasons.distinct()
            writeSummaryReports(
                context = context,
                attemptDirectory = stagingDir,
                summary = summary,
                captureIndex = captureIndex,
                queue = snapshot.queueDiagnostics,
                degradedReasons = uniqueDegraded,
            )
            val resultEvidenceStatus = RecognitionEvidenceResultStatusPolicy.fromStatus(
                summary.optString("resultEvidenceStatus", "RESULT_EVIDENCE_NOT_AVAILABLE"),
            )
            File(reportsFolder, "result_evidence_status.json").writeText(
                resultEvidenceStatus.toJsonString(),
                Charsets.UTF_8,
            )

            ensureExpectedFiles(logsFolder, reportsFolder)
            val filesBeforeManifest = listFiles(stagingDir).filter { it.name != "manifest.json" }
            val integrityStatus = if (uniqueDegraded.isNotEmpty() ||
                evidenceStatus(summary, captureIndex, snapshot.queueDiagnostics, filesBeforeManifest) != "COMPLETE"
            ) "DEGRADED" else "COMPLETE"
            val identity = snapshot.identity
            val sessionId = identity?.let { sha256(it.sessionId).take(12) } ?: "unknown"
            val archive = File(diagnosticDir, "YuJian_Recognition_Evidence_${sessionId}.zip")

            Log.i("RecognitionEvidenceExport", "EXPORT_PACKAGE_START stage=$stage attempt=${identity?.attemptId} atMs=${SystemClock.elapsedRealtime()}")
            val packaged = packageSnapshotAndVerify(stagingDir, archive) { fileHashesMap ->
                val fileHashes = JSONObject().apply {
                    fileHashesMap.forEach { (path, hash) -> put(path, hash) }
                }
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
                    .put("degradedReasons", JSONArray(uniqueDegraded))
                    .put("modelAssetSha256", buildInfo.optJSONObject("modelAssets") ?: JSONObject().put("status", "NOT_RECORDED"))
                    .put("fileSha256", fileHashes)
                manifest.toString(2).toByteArray(Charsets.UTF_8)
            }
            Log.i("RecognitionEvidenceExport", "EXPORT_PACKAGE_COMPLETE stage=$stage attempt=${identity?.attemptId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt} sizeBytes=${packaged.sizeBytes} sha256=${packaged.sha256}")
            RecognitionEvidenceZipExportResult(
                file = packaged.archive,
                sha256 = packaged.sha256,
                sizeBytes = packaged.sizeBytes,
                integrityStatus = integrityStatus,
                sourceSha = BuildConfig.SOURCE_REVISION,
                degradedReasons = uniqueDegraded,
            )
        } catch (failure: Throwable) {
            Log.e("RecognitionEvidenceExport", "EXPORT_FAILED stage=$stage elapsedMs=${SystemClock.elapsedRealtime() - startedAt} errorType=${failure.javaClass.simpleName} error=${failure.message}", failure)
            throw failure
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    suspend fun saveToUri(context: Context, result: RecognitionEvidenceZipExportResult, uri: Uri): Long =
        withContext(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i("RecognitionEvidenceExport", "EXPORT_SAVE_START atMs=$startedAt uri=$uri sizeBytes=${result.sizeBytes}")
            try {
                val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                    "ContentResolver returned no output stream"
                }
                val copied = stream.use { output -> result.file.inputStream().use { input -> copyArchive(input, output) } }
                check(copied == result.sizeBytes) { "ZIP_SAVE_SIZE_MISMATCH expected=${result.sizeBytes} actual=$copied" }
                Log.i("RecognitionEvidenceExport", "EXPORT_SAVE_COMPLETE elapsedMs=${SystemClock.elapsedRealtime() - startedAt} sizeBytes=$copied sha256=${result.sha256}")
                copied
            } catch (failure: Throwable) {
                Log.e("RecognitionEvidenceExport", "EXPORT_FAILED phase=SAVE elapsedMs=${SystemClock.elapsedRealtime() - startedAt} errorType=${failure.javaClass.simpleName} error=${failure.message}", failure)
                throw failure
            }
        }

    internal fun copyArchive(input: InputStream, output: java.io.OutputStream): Long {
        var bytes = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            bytes += count
        }
        output.flush()
        return bytes
    }

    internal fun resolveShareUri(context: Context, file: File): android.net.Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    internal fun createShareIntent(
        context: Context,
        result: RecognitionEvidenceZipExportResult,
        uri: android.net.Uri = resolveShareUri(context, result.file),
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "本次识鱼诊断包 · ${result.integrityStatus}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, result.file.name, uri)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(send, "导出本次识鱼诊断包").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun share(context: Context, result: RecognitionEvidenceZipExportResult): RecognitionEvidenceShareOutcome {
        val uri = runCatching { resolveShareUri(context, result.file) }.getOrElse { failure ->
            return RecognitionEvidenceShareOutcome(
                chooserLaunched = false,
                shareUri = null,
                errorReason = "FILEPROVIDER_URI_FAILED:${failure.javaClass.simpleName}",
            )
        }
        val chooser = createShareIntent(context, result, uri)
        return runCatching {
            context.startActivity(chooser)
            RecognitionEvidenceShareOutcome(chooserLaunched = true, shareUri = uri.toString())
        }.getOrElse { failure ->
            RecognitionEvidenceShareOutcome(
                chooserLaunched = false,
                shareUri = uri.toString(),
                errorReason = "SHARE_ACTIVITY_FAILED:${failure.javaClass.simpleName}",
            )
        }
    }

    /**
     * Packages an already-frozen [snapshotDir] into [outputArchive], computing hashes and
     * verifying all entries + manifest against a temp archive before atomic replacement.
     */
    internal fun packageSnapshotAndVerify(
        snapshotDir: File,
        outputArchive: File,
        writeManifest: (Map<String, String>) -> ByteArray,
    ): RecognitionEvidencePackagedArchive {
        val filesBeforeManifest = listFiles(snapshotDir)
            .filter { it.name != "manifest.json" }
            .sortedBy { relativePath(snapshotDir, it) }
        val fileHashes = linkedMapOf<String, String>()
        filesBeforeManifest.forEach { file ->
            fileHashes[relativePath(snapshotDir, file)] = sha256(file)
        }
        val manifestBytes = writeManifest(fileHashes)
        File(snapshotDir, "manifest.json").writeBytes(manifestBytes)

        val archiveDir = outputArchive.parentFile?.apply { mkdirs() }
        val tempArchive = File(archiveDir, "${outputArchive.name}.tmp.${UUID.randomUUID()}")
        try {
            ZipOutputStream(tempArchive.outputStream().buffered()).use { zip ->
                listFiles(snapshotDir)
                    .sortedBy { relativePath(snapshotDir, it) }
                    .forEach { source ->
                        val relative = relativePath(snapshotDir, source)
                        zip.putNextEntry(ZipEntry(relative))
                        FileInputStream(source).buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            RecognitionEvidenceArchiveVerifier.verify(tempArchive, fileHashes, manifestBytes)
            val archiveHash = sha256(tempArchive)
            if (outputArchive.exists()) outputArchive.delete()
            if (!tempArchive.renameTo(outputArchive)) {
                tempArchive.copyTo(outputArchive, overwrite = true)
                tempArchive.delete()
            }
            File(archiveDir, outputArchive.name + ".sha256")
                .writeText("$archiveHash  ${outputArchive.name}\n", Charsets.UTF_8)
            return RecognitionEvidencePackagedArchive(
                archive = outputArchive,
                sha256 = archiveHash,
                sizeBytes = outputArchive.length(),
                fileHashes = fileHashes,
            )
        } finally {
            if (tempArchive.exists()) tempArchive.delete()
        }
    }

    private fun writeSummaryReports(
        context: Context,
        attemptDirectory: File,
        summary: JSONObject,
        captureIndex: JSONArray,
        queue: RecognitionEvidenceQueueDiagnostics,
        degradedReasons: List<String> = emptyList(),
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
                .put("incompleteReasons", JSONArray((queue.incompleteReasons + degradedReasons).distinct()))
                .put("degradedReasons", JSONArray(degradedReasons))
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
        val stageSnapshots = buildList {
            for (i in 0 until enteredStages.length()) {
                val row = enteredStages.optJSONObject(i) ?: continue
                add(RecognitionEvidenceStageSnapshot(row.optString("stage"), row.optString("status")))
            }
        }
        val captures = buildList {
            for (i in 0 until captureIndex.length()) {
                val row = captureIndex.optJSONObject(i) ?: continue
                add(
                    RecognitionEvidenceCaptureSnapshot(
                        stage = row.optString("stage"),
                        captureType = row.optString("captureType"),
                        pixelCopyStatus = row.optString("pixelCopyStatus"),
                    ),
                )
            }
        }
        val failures = RecognitionEvidenceIntegrityPolicy.incompleteReasons(
            hasEventLog = hasEvents,
            hasStageLog = hasStages,
            nonEmptyFileNames = files.filter { it.length() > 0L }.map(File::getName).toSet(),
            stageStatuses = stageSnapshots,
            captures = captures,
            screenshotFailureCount = summary.optInt("screenshotFailureCount"),
            droppedCriticalEventCount = queue.droppedCriticalEvents,
            droppedSampleEventCount = queue.droppedSampleEvents,
            writeFailures = queue.writeFailures,
            queueIncompleteReasons = queue.incompleteReasons,
        )
        return if (failures.isEmpty()) "COMPLETE" else "EVIDENCE_INCOMPLETE"
    }

    private fun ensureExpectedFiles(logs: File, reports: File) {
        listOf(
            "recognition_events.jsonl", "orb_render_trace.jsonl", "model_inference.jsonl", "mask_runtime.jsonl",
            "waterlight_paths.jsonl", "result_handoff.jsonl", "stage_timeline.jsonl", "logcat.txt",
        ).forEach { name -> File(logs, name).takeIf { !it.exists() }?.writeText("", Charsets.UTF_8) }
        listOf(
            "model_summary.json", "r03_rotation_summary.json", "r06_summary.json",
            "resident_lifecycle_summary.json", "task_energy_summary.json", "capture_integrity.json",
            "result_evidence_status.json",
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

    private fun captureLogcat(pid: Int, target: File): LogcatCaptureResult = captureBoundedCommandOutput(
        command = listOf("logcat", "-d", "-v", "threadtime", "-t", MAX_LOGCAT_LINES.toString(), "--pid=$pid"),
        target = target,
        timeoutMs = LOGCAT_TIMEOUT_MS,
        maxBytes = MAX_LOGCAT_BYTES,
    )

    internal fun captureBoundedCommandOutput(
        command: List<String>,
        target: File,
        timeoutMs: Long = LOGCAT_TIMEOUT_MS,
        maxBytes: Int = MAX_LOGCAT_BYTES,
    ): LogcatCaptureResult {
        val clampedTimeoutMs = timeoutMs.coerceIn(1L, MAX_LOGCAT_TIMEOUT_MS)
        val clampedMaxBytes = maxBytes.coerceIn(1_024, MAX_LOGCAT_BYTES_HARD_LIMIT)
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val captured = ByteArrayOutputStream()
            var truncated = false
            val readerThread = Thread({
                runCatching {
                    process.inputStream.use { stream ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            val remaining = clampedMaxBytes - captured.size()
                            if (remaining > 0) {
                                val toWrite = minOf(read, remaining)
                                captured.write(buffer, 0, toWrite)
                                if (toWrite < read) truncated = true
                            } else {
                                truncated = true
                            }
                        }
                    }
                }
            }, "yujian-logcat-reader").apply {
                isDaemon = true
                start()
            }
            // Timed Process.waitFor and destroyForcibly require API 26. Poll exitValue
            // within the same bounded capture budget for the app's API 24 minimum.
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(clampedTimeoutMs)
            var finished = false
            while (System.nanoTime() < deadlineNanos) {
                finished = runCatching { process.exitValue(); true }.getOrDefault(false)
                if (finished) break
                Thread.sleep(25L)
            }
            if (!finished) finished = runCatching { process.exitValue(); true }.getOrDefault(false)
            if (!finished) {
                process.destroy()
                readerThread.join(200L)
                val prefix = captured.toByteArray()
                val suffix = "\nLOGCAT_TIMEOUT:${clampedTimeoutMs}ms\n".toByteArray(Charsets.UTF_8)
                target.writeBytes(prefix + suffix)
                LogcatCaptureResult(status = "TIMEOUT", truncated = truncated, degradedReason = "LOGCAT_TIMEOUT")
            } else {
                readerThread.join(500L)
                val bytes = captured.toByteArray()
                val exit = runCatching { process.exitValue() }.getOrDefault(-1)
                if (exit != 0 && bytes.isEmpty()) {
                    target.writeText("LOGCAT_NOT_RECORDED:EXIT_$exit\n", Charsets.UTF_8)
                    LogcatCaptureResult(status = "FAILED", truncated = false, degradedReason = "LOGCAT_EXIT_$exit")
                } else if (truncated) {
                    val suffix = "\nLOGCAT_TRUNCATED:${clampedMaxBytes}B\n".toByteArray(Charsets.UTF_8)
                    target.writeBytes(bytes + suffix)
                    LogcatCaptureResult(status = "TRUNCATED", truncated = true, degradedReason = "LOGCAT_SIZE_CAP_REACHED")
                } else {
                    target.writeBytes(bytes)
                    LogcatCaptureResult(status = "SUCCESS", truncated = false, degradedReason = null)
                }
            }
        }.getOrElse { failure ->
            target.writeText("LOGCAT_NOT_RECORDED:${failure.javaClass.simpleName}\n", Charsets.UTF_8)
            LogcatCaptureResult(
                status = "FAILED",
                truncated = false,
                degradedReason = "LOGCAT_FAILED:${failure.javaClass.simpleName}",
            )
        }
    }

    private fun cleanupStaleTempArtifacts(diagnosticDir: File) {
        val cutoffMs = System.currentTimeMillis() - STALE_TEMP_CLEANUP_AGE_MS
        diagnosticDir.listFiles()?.forEach { child ->
            if ((child.name.startsWith(".staging_") || child.name.contains(".tmp.")) &&
                child.lastModified() in 1L until cutoffMs
            ) {
                child.deleteRecursively()
            }
        }
    }

    private fun tailAlignedUtf8Bytes(raw: ByteArray, maxBytes: Int): ByteArray {
        if (raw.size <= maxBytes) return raw
        var start = raw.size - maxBytes
        while (start < raw.size && raw[start] != '\n'.code.toByte()) {
            start++
        }
        if (start < raw.size && raw[start] == '\n'.code.toByte()) start++
        return if (start >= raw.size) raw.copyOfRange(raw.size - maxBytes, raw.size)
        else raw.copyOfRange(start, raw.size)
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

    private fun sha256(file: File): String = FileInputStream(file).use(::sha256)

    private fun sha256(input: InputStream): String {
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
    internal const val LOGCAT_TIMEOUT_MS = 2_500L
    internal const val MAX_LOGCAT_TIMEOUT_MS = 5_000L
    internal const val MAX_LOGCAT_LINES = 2_000
    internal const val MAX_LOGCAT_BYTES = 512 * 1024
    internal const val MAX_LOGCAT_BYTES_HARD_LIMIT = 2 * 1024 * 1024
    internal const val MAX_ORB_TRACE_BYTES = 4 * 1024 * 1024
    private const val STALE_TEMP_CLEANUP_AGE_MS = 10 * 60 * 1000L
}
