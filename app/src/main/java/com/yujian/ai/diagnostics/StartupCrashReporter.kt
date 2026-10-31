package com.yujian.ai.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import com.yujian.ai.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Installed before Compose; fatal errors still reach Android's original handler. */
object StartupCrashReporter {
    private val installed = AtomicBoolean(false)
    private const val TAG = "YuJianStartup"
    private fun reportFile(context: Context) = File(context.filesDir, "startup_diagnostics/last_crash.txt")
    private fun pendingFile(context: Context) = File(context.filesDir, "startup_diagnostics/pending")
    private fun prefs(context: Context) = context.getSharedPreferences("startup_diagnostics", Context.MODE_PRIVATE)

    fun install(application: Application) {
        if (!BuildConfig.DEBUG || !installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                runCatching {
                    val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                    persist(application, "source=uncaught_exception\nthread=${thread.name}\n" + stack)
                }.onFailure { Log.e(TAG, "Unable to persist crash", it) }
            } finally {
                previous?.uncaughtException(thread, error)
                // A fatal process must never continue if a custom previous handler returns.
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun hasPendingReport(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        if (pendingFile(context).isFile && reportFile(context).isFile) return true
        // Can recover native/Java exits from the previously installed APK on Android 11+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching {
            val acknowledged = prefs(context).getLong("acknowledged_exit_ms", 0L)
            val manager = context.getSystemService(ActivityManager::class.java) ?: return@runCatching
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull { it.timestamp > acknowledged && it.reason in setOf(
                    android.app.ApplicationExitInfo.REASON_CRASH,
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                    android.app.ApplicationExitInfo.REASON_ANR,
                ) }
            if (exit != null) {
                val trace = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readTextBounded() } }.getOrNull()
                persist(context, "source=android_exit_history\nexit_timestamp=${exit.timestamp}\n" +
                    "reason=${exit.reason} status=${exit.status} description=${exit.description}\n" +
                    (trace ?: "TRACE_NOT_AVAILABLE: the system retained exit metadata only.\n"))
            }
        }.onFailure { Log.w(TAG, "Historical exit trace unavailable", it) }
        return pendingFile(context).isFile && reportFile(context).isFile
    }

    private fun java.io.Reader.readTextBounded(): String {
        val buffer = CharArray(4096)
        val result = StringBuilder()
        while (result.length < MAX_STARTUP_REPORT_CHARS) {
            val count = read(buffer, 0, minOf(buffer.size, MAX_STARTUP_REPORT_CHARS - result.length))
            if (count < 0) break
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun persist(context: Context, details: String) {
        val file = reportFile(context)
        file.parentFile?.mkdirs()
        val metrics = context.resources.displayMetrics
        val report = sanitizeStartupReport(
            "YuJian startup crash diagnostics\n" +
                "captured_at_ms=${System.currentTimeMillis()}\n" +
                "package=${context.packageName}\nversion=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "source_revision=${BuildConfig.SOURCE_REVISION}\n" +
                "device=${Build.MANUFACTURER} ${Build.MODEL}\nandroid=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}\n" +
                "abis=${Build.SUPPORTED_ABIS.joinToString()}\n" +
                "screen=${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.density} font_scale=${context.resources.configuration.fontScale}\n" +
                "max_heap_bytes=${Runtime.getRuntime().maxMemory()}\n\n$details",
        )
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(report.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) {
            atomic.failWrite(output)
            throw failure
        }
        pendingFile(context).writeText(System.currentTimeMillis().toString())
    }

    fun report(context: Context): String = runCatching {
        reportFile(context).reader().use { it.readTextBounded() }
    }.getOrDefault("没有可用的崩溃栈，请重试打开后再次导出。")

    fun exportFile(context: Context): File {
        val target = File(context.cacheDir, "diagnostics/startup_crash.txt")
        target.parentFile?.mkdirs()
        target.writeText(report(context))
        return target
    }

    fun acknowledge(context: Context) {
        // Keep the report for export, but do not force recovery after an explicit retry.
        prefs(context).edit().putLong("acknowledged_exit_ms", System.currentTimeMillis()).commit()
        pendingFile(context).delete()
    }
}
