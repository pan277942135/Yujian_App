package com.yujian.ai.ui.recognition

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.FileProvider
import com.yujian.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Debug-only, local export. Includes no image bytes and never uploads anything. */
object RecognitionDiagnosticExport {
    suspend fun share(context: Context) {
        if (!BuildConfig.DEBUG) return
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val raw = File(directory, "recognition-raw.tmp")
            val report = File(directory, "recognition-${System.currentTimeMillis()}.txt")
            var status = "OK"
            try {
                val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}")
                    .redirectErrorStream(true).redirectOutput(raw).start()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    status = "LOGCAT_TIMEOUT"
                } else if (process.exitValue() != 0) status = "LOGCAT_EXIT_${process.exitValue()}"
            } catch (error: Exception) {
                status = "LOGCAT_ERROR_${error.javaClass.simpleName}"
            }
            val markers = listOf("Recognition", "SLIMSAM", "MASK_GATE", "GLES_", "PHOTO_", "RR09_", "VISUAL_STAGE", "COMPOSITOR_MODE", "GOLD_")
            val lines = if (raw.exists()) raw.useLines { seq ->
                seq.filter { line -> markers.any { line.contains(it, ignoreCase = true) } }.toList().takeLast(3000)
            } else emptyList()
            report.bufferedWriter().use { out ->
                out.appendLine("YuJian recognition diagnostic; status=$status; pid=${Process.myPid()}; source=${BuildConfig.VERSION_NAME}")
                out.appendLine("No photo bytes are included. Logcat is limited to this app process and recognition markers.")
                if (lines.isEmpty()) out.appendLine("NO_MATCHING_LOGS: capture may have started in another process or the log buffer rotated.")
                lines.forEach { out.appendLine(it) }
            }
            raw.delete()
            report
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "导出识别诊断日志"))
    }
}
