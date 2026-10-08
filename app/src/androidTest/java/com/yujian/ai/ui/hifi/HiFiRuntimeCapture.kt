package com.yujian.ai.ui.hifi

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Screenshots made by the actual foreground app during instrumented Compose tests.
 * Never mark a static reference or a post-test emulator desktop as a runtime capture.
 */
object HiFiRuntimeCapture {
    fun save(activity: ComponentActivity, directory: String, name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val commit = InstrumentationRegistry.getArguments().getString("buildSha").orEmpty()
        check(Regex("^[a-f0-9]{40}$").matches(commit)) { "HIFI_BUILD_SHA_NOT_PROVIDED" }
        check(activity.packageName == "com.yujian.ai") { "HIFI_WRONG_PACKAGE" }
        check(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            "HIFI_ACTIVITY_NOT_RESUMED"
        }
        check(activity.hasWindowFocus()) { "HIFI_WINDOW_NOT_FOCUSED" }

        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("HIFI_SCREENSHOT_NULL")
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), directory)
        check(root.exists() || root.mkdirs())
        val file = File(root, name)
        try {
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
        } finally {
            bitmap.recycle()
        }
        check(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        check(activity.hasWindowFocus()) { "HIFI_FOCUS_LOST_DURING_CAPTURE" }

        val sha = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        val proof = JSONObject().apply {
            put("package", activity.packageName)
            put("build_sha", commit)
            put("foreground_verified", true)
            put("resumed_activity", activity.packageName + "/" + activity.javaClass.name)
            put("activity_window_focus", true)
            put("test_assertions_passed", true)
            put("capture_method", "instrumentation-ui-automation")
            put("screenshot_sha256", sha)
            put("capture_epoch_ms", System.currentTimeMillis())
        }
        File(root, "$name.provenance.json").writeText(proof.toString(2), Charsets.UTF_8)
        return file
    }
}
