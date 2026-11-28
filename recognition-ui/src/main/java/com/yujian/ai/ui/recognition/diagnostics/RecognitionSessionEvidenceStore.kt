package com.yujian.ai.ui.recognition.diagnostics

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Durable private attempt storage. Current evidence is never kept only in cache. */
class RecognitionSessionEvidenceStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "recognition_evidence")

    fun begin(identity: RecognitionEvidenceIdentity): AttemptStore {
        val sessionKey = sha256(identity.sessionId).take(20)
        val attemptKey = sha256(identity.attemptId).take(20)
        val directory = File(File(root, sessionKey), attemptKey)
        listOf("session", "frames/stage_entry", "frames/motion_samples", "logs", "reports")
            .forEach { File(directory, it).mkdirs() }
        val identityJson = JSONObject()
            .put("schemaVersion", 1)
            .put("sessionIdHash", sha256(identity.sessionId))
            .put("attemptIdHash", sha256(identity.attemptId))
            .put("imageIdHash", sha256(identity.imageId))
            .put("photoSha256", identity.photoSha256)
        File(directory, "session/session_identity.json").writeText(identityJson.toString(2), Charsets.UTF_8)
        return AttemptStore(identity, directory)
    }

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
}
