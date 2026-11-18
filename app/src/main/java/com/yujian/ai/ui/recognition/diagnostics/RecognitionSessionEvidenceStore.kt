package com.yujian.ai.ui.recognition.diagnostics

import android.content.Context
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Durable private attempt storage. Current evidence is never kept only in cache. */
class RecognitionSessionEvidenceStore(context: Context) {
    private val root = File(context.applicationContext.noBackupFilesDir, "recognition_evidence")

    fun installCrashRecovery() = RecognitionEvidenceCrashHandler.install()

    fun begin(identity: RecognitionEvidenceIdentity): AttemptStore {
        val sessionKey = sha256(identity.sessionId).take(20)
        val attemptKey = sha256(identity.attemptId).take(20)
        val directory = File(File(root, sessionKey), attemptKey)
        listOf("session", "frames/stage_entry", "frames/motion_samples", "logs", "reports")
            .forEach { File(directory, it).mkdirs() }
        val nowWall = System.currentTimeMillis()
        val nowUptime = SystemClock.uptimeMillis()
        val schema = RecognitionEvidenceSchema
        val sessionJson = JSONObject()
            .put("evidenceSchemaVersion", schema.EVIDENCE_SCHEMA_VERSION)
            .put("runtimeContractVersion", schema.RUNTIME_CONTRACT_VERSION)
            .put("diagnosticBuildVersion", schema.DIAGNOSTIC_BUILD_VERSION)
            .put("sessionIdHash", sha256(identity.sessionId))
            .put("createdAtEpochMs", nowWall)
            .put("sourceSha", androidBuildSourceSha())
        val attemptJson = JSONObject()
            .put("evidenceSchemaVersion", schema.EVIDENCE_SCHEMA_VERSION)
            .put("runtimeContractVersion", schema.RUNTIME_CONTRACT_VERSION)
            .put("diagnosticBuildVersion", schema.DIAGNOSTIC_BUILD_VERSION)
            .put("sessionIdHash", sha256(identity.sessionId))
            .put("attemptIdHash", sha256(identity.attemptId))
            .put("imageIdHash", sha256(identity.imageId))
            .put("photoSha256", identity.photoSha256)
            .put("photoDimensions", JSONObject()
                .put("width", identity.photoWidthPx.takeIf { it > 0 } ?: JSONObject.NULL)
                .put("height", identity.photoHeightPx.takeIf { it > 0 } ?: JSONObject.NULL))
            .put("orientationDegrees", identity.orientationDegrees)
            .put("startedAtEpochMs", nowWall)
            .put("startedAtUptimeMs", nowUptime)
            .put("processInstanceId", RecognitionEvidenceProcessInstance.id)
            .put("terminalState", RecognitionAttemptTerminalState.RUNNING.name)
            .put("endedAtEpochMs", JSONObject.NULL)
            .put("resultEvidenceStatus", "NOT_REACHED")
            .put("recovered", false)
        val sessionFile = File(directory, "session/session.json")
        if (!sessionFile.isFile) atomicWrite(sessionFile, sessionJson.toString(2))
        atomicWrite(
            File(directory, "session/session_identity.json"),
            JSONObject()
                .put("schemaVersion", schema.EVIDENCE_SCHEMA_VERSION)
                .put("sessionIdHash", sha256(identity.sessionId))
                .put("attemptIdHash", sha256(identity.attemptId))
                .put("imageIdHash", sha256(identity.imageId))
                .put("photoSha256", identity.photoSha256)
                .put("photoWidthPx", identity.photoWidthPx.takeIf { it > 0 } ?: JSONObject.NULL)
                .put("photoHeightPx", identity.photoHeightPx.takeIf { it > 0 } ?: JSONObject.NULL)
                .put("orientationDegrees", identity.orientationDegrees)
                .toString(2),
        )
        atomicWrite(File(directory, "attempt.json"), attemptJson.toString(2))
        RecognitionEvidenceCrashHandler.activate(directory)
        return AttemptStore(identity, directory)
    }

    /** Marks all prior attempts without a terminal marker and keeps their raw logs exportable. */
    fun recoverUnfinishedAttempts(): List<RecoveredAttemptStore> {
        if (!root.isDirectory) return emptyList()
        val now = System.currentTimeMillis()
        val recovered = mutableListOf<RecoveredAttemptStore>()
        root.listFiles()?.filter(File::isDirectory).orEmpty().forEach { sessionDirectory ->
            sessionDirectory.listFiles()?.filter(File::isDirectory).orEmpty().forEach attemptLoop@{ attemptDirectory ->
                val attemptFile = File(attemptDirectory, "attempt.json")
                val identityFile = File(attemptDirectory, "session/session_identity.json")
                val metadata = runCatching { JSONObject(attemptFile.readText(Charsets.UTF_8)) }.getOrNull()
                val identity = runCatching { JSONObject(identityFile.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
                if (metadata == null && !identityFile.isFile) return@attemptLoop

                val state = metadata?.optString("terminalState").orEmpty()
                var effectiveMetadata = metadata ?: JSONObject()
                    .put("evidenceSchemaVersion", RecognitionEvidenceSchema.EVIDENCE_SCHEMA_VERSION)
                    .put("runtimeContractVersion", RecognitionEvidenceSchema.RUNTIME_CONTRACT_VERSION)
                    .put("diagnosticBuildVersion", RecognitionEvidenceSchema.DIAGNOSTIC_BUILD_VERSION)
                    .put("sessionIdHash", identity.optString("sessionIdHash", sessionDirectory.name))
                    .put("attemptIdHash", identity.optString("attemptIdHash", attemptDirectory.name))
                    .put("imageIdHash", identity.optString("imageIdHash", "NOT_RECORDED"))
                    .put("photoSha256", identity.optString("photoSha256", "NOT_RECORDED"))
                    .put("startedAtEpochMs", attemptDirectory.lastModified())
                    .put("startedAtUptimeMs", JSONObject.NULL)
                    .put("processInstanceId", "NOT_RECORDED")
                    .put("resultEvidenceStatus", "NOT_REACHED")
                var recoveredThisLaunch = false
                val crashExists = File(attemptDirectory, "crash_report.json").isFile
                val terminal = RecognitionEvidenceRecoveryPolicy.classify(
                    previousTerminalState = state,
                    crashSidecarPresent = crashExists,
                    previousProcessInstanceId = effectiveMetadata.optString("processInstanceId").takeIf(String::isNotBlank),
                    currentProcessInstanceId = RecognitionEvidenceProcessInstance.id,
                )
                val shouldRecover = state == RecognitionAttemptTerminalState.RUNNING.name || state.isBlank() ||
                    (terminal == RecognitionAttemptTerminalState.CRASHED && !effectiveMetadata.optBoolean("recovered", false))
                if (shouldRecover && terminal != null) {
                    effectiveMetadata = effectiveMetadata
                        .put("terminalState", terminal.name)
                        .put("endedAtEpochMs", now)
                        .put("recovered", true)
                    val recovery = JSONObject()
                        .put("recoveryStatus", "RECOVERED")
                        .put("terminalState", terminal.name)
                        .put("detectedAtEpochMs", now)
                        .put("sessionIdHash", effectiveMetadata.optString("sessionIdHash", "NOT_RECORDED"))
                        .put("attemptIdHash", effectiveMetadata.optString("attemptIdHash", "NOT_RECORDED"))
                        .put("photoSha256", effectiveMetadata.optString("photoSha256", "NOT_RECORDED"))
                        .put("resultEvidenceStatus", "NOT_REACHED")
                        .put("reason", RecognitionEvidenceRecoveryPolicy.reasonFor(terminal))
                    atomicWrite(File(attemptDirectory, "recovery_report.json"), recovery.toString(2))
                    atomicWrite(attemptFile, effectiveMetadata.toString(2))
                    recoveredThisLaunch = true
                }
                if (effectiveMetadata.optBoolean("recovered", recoveredThisLaunch)) {
                    recovered += RecoveredAttemptStore(
                        directory = attemptDirectory,
                        sessionIdHash = effectiveMetadata.optString("sessionIdHash", identity.optString("sessionIdHash")),
                        attemptIdHash = effectiveMetadata.optString("attemptIdHash", identity.optString("attemptIdHash")),
                        photoSha256 = effectiveMetadata.optString("photoSha256", identity.optString("photoSha256")),
                        terminalState = effectiveMetadata.optString("terminalState", RecognitionAttemptTerminalState.UNKNOWN_TERMINATION.name),
                        startedAtEpochMs = effectiveMetadata.optLong("startedAtEpochMs", attemptDirectory.lastModified()),
                    )
                }
            }
        }
        return recovered.sortedByDescending { it.startedAtEpochMs }
    }

    fun finish(
        attempt: AttemptStore,
        terminalState: RecognitionAttemptTerminalState,
        reason: String? = null,
        resultEvidenceAvailable: Boolean = false,
    ) {
        require(terminalState.isTerminal) { "Attempt terminal state must be final" }
        val file = File(attempt.directory, "attempt.json")
        val metadata = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
        if (metadata.optString("terminalState") != RecognitionAttemptTerminalState.RUNNING.name) return
        atomicWrite(
            file,
            metadata
                .put("terminalState", terminalState.name)
                .put("terminalReason", reason ?: JSONObject.NULL)
                .put("endedAtEpochMs", System.currentTimeMillis())
                .put("endedAtUptimeMs", SystemClock.uptimeMillis())
                .put(
                    "resultEvidenceStatus",
                    RecognitionEvidenceRecoveryPolicy.resultEvidenceStatus(terminalState, resultEvidenceAvailable),
                )
                .put(
                    "resultEvidenceMissingReason",
                    if (terminalState == RecognitionAttemptTerminalState.RESULT_STABLE && !resultEvidenceAvailable) {
                        "RESULT_STABLE_PIXELCOPY_NOT_CONFIRMED"
                    } else JSONObject.NULL,
                )
                .toString(2),
        )
        RecognitionEvidenceCrashHandler.deactivate(attempt.directory)
    }

    fun markResultEvidenceAvailable(attempt: AttemptStore) {
        val file = File(attempt.directory, "attempt.json")
        val metadata = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return
        if (metadata.optString("terminalState") != RecognitionAttemptTerminalState.RESULT_STABLE.name) return
        atomicWrite(
            file,
            metadata
                .put("resultEvidenceStatus", "AVAILABLE")
                .put("resultEvidenceMissingReason", JSONObject.NULL)
                .toString(2),
        )
    }

    data class RecoveredAttemptStore(
        val directory: File,
        val sessionIdHash: String,
        val attemptIdHash: String,
        val photoSha256: String,
        val terminalState: String,
        val startedAtEpochMs: Long,
    )

    data class AttemptStore(
        val identity: RecognitionEvidenceIdentity,
        val directory: File,
    ) {
        fun file(relativePath: String): File {
            val target = File(directory, relativePath)
            require(target.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                "Evidence path escapes attempt directory"
            }
            return target
        }

        fun write(relativePath: String, content: String) {
            file(relativePath).apply {
                parentFile?.mkdirs()
                writeText(content, Charsets.UTF_8)
            }
        }

        fun append(relativePath: String, content: String) {
            file(relativePath).apply {
                parentFile?.mkdirs()
                appendText(content, Charsets.UTF_8)
            }
        }
    }

    private fun atomicWrite(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun androidBuildSourceSha(): String = runCatching {
        Class.forName("com.yujian.ai.BuildConfig").getField("SOURCE_REVISION").get(null) as String
    }.getOrDefault("NOT_RECORDED")
}

/** Synchronous crash sidecar writer; never relies on the asynchronous evidence queue. */
private object RecognitionEvidenceCrashHandler {
    private val installed = AtomicBoolean(false)
    private val activeAttemptDirectory = AtomicReference<File?>(null)

    fun activate(directory: File) { activeAttemptDirectory.set(directory) }

    fun deactivate(directory: File) {
        activeAttemptDirectory.compareAndSet(directory, null)
    }

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            val directory = activeAttemptDirectory.get()
            if (directory != null) runCatching { writeCrashSidecar(directory, thread, failure) }
            if (previous != null) previous.uncaughtException(thread, failure)
            else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun writeCrashSidecar(directory: File, thread: Thread, failure: Throwable) {
        val stack = StringWriter().also { writer -> failure.printStackTrace(PrintWriter(writer)) }
            .toString().take(MAX_CRASH_STACK_CHARS)
        val attemptFile = File(directory, "attempt.json")
        val metadata = runCatching { JSONObject(attemptFile.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
        val sessionIdentity = runCatching {
            JSONObject(File(directory, "session/session_identity.json").readText(Charsets.UTF_8))
        }.getOrDefault(JSONObject())
        val lastSummary = runCatching {
            JSONObject(File(directory, "session/session_summary.json").readText(Charsets.UTF_8))
        }.getOrDefault(JSONObject())
        val report = JSONObject()
            .put("crashStatus", "CRASHED")
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("capturedAtUptimeMs", SystemClock.uptimeMillis())
            .put("threadName", thread.name)
            .put("exceptionClass", failure.javaClass.name)
            .put("message", failure.message ?: JSONObject.NULL)
            .put("sessionIdHash", metadata.optString("sessionIdHash", sessionIdentity.optString("sessionIdHash", "NOT_RECORDED")))
            .put("attemptIdHash", metadata.optString("attemptIdHash", sessionIdentity.optString("attemptIdHash", "NOT_RECORDED")))
            .put("photoSha256", metadata.optString("photoSha256", sessionIdentity.optString("photoSha256", "NOT_RECORDED")))
            .put("lastKnownSceneClockMs", lastSummary.optLong("recognitionDurationMs", -1L))
            .put("lastKnownStageStatuses", lastSummary.optJSONArray("stageStatuses") ?: org.json.JSONArray())
            .put("lastKnownWaitingReason", lastSummary.opt("waitingReason") ?: JSONObject.NULL)
            .put("stackTrace", stack)
        val crashFile = File(directory, "crash_report.json")
        crashFile.writeText(report.toString(2), Charsets.UTF_8)
        val terminalMetadata = metadata
            .put("terminalState", RecognitionAttemptTerminalState.CRASHED.name)
            .put("terminalReason", failure.javaClass.name)
            .put("endedAtEpochMs", System.currentTimeMillis())
            .put("endedAtUptimeMs", SystemClock.uptimeMillis())
            .put("resultEvidenceStatus", "NOT_REACHED")
        val temporary = File(directory, "attempt.json.tmp")
        temporary.writeText(terminalMetadata.toString(2), Charsets.UTF_8)
        if (!temporary.renameTo(attemptFile)) {
            temporary.copyTo(attemptFile, overwrite = true)
            temporary.delete()
        }
    }

    private const val MAX_CRASH_STACK_CHARS = 64 * 1024
}

private object RecognitionEvidenceProcessInstance {
    val id: String by lazy { "${Process.myPid()}-${java.util.UUID.randomUUID()}" }
}
