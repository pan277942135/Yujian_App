package com.yujian.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Deterministic Production Route gate for presentation data hygiene. */
@RunWith(AndroidJUnit4::class)
class PresentationSanitizationRuntimeTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext

    @Test
    fun productionRoutes_hideDirtyValues_andPreserveValidLocation() {
        seedCatch(location = "null")
        launchFresh()

        assertHome("null location")
        screenshot("01_normal_home_null_clean.png")

        clickVisibleDescription(description = "全部鱼获", destinationText = "我的鱼获")
        assertRuntimeClean("My Catches with null location")
        assertVisible("草鱼")
        assertVisible(currentDayOfMonthLabel())
        assertVisible(currentMonthLabel())
        assertVisible("1 条鱼获 · 1 种鱼")
        assertAbsent("首次null")
        screenshot("02_my_catches_null_clean.png")

        clickVisibleText(sourceText = "草鱼", destinationText = "鱼获详情")
        assertRuntimeClean("FishRecordDetail with null location")
        assertVisible("草鱼")
        assertAbsent("2026-09-25T")
        assertAbsent("1970")
        screenshot("03_fish_record_detail_null_clean.png")

        seedCatch(location = "千岛湖")
        launchFresh()
        assertHome("valid location")
        assertVisible("千岛湖")
        assertRuntimeClean("Normal Home with valid location")
        screenshot("04_valid_location_regression.png")
    }

    private fun seedCatch(location: String) {
        val timestamp = fixtureTimestamp()
        val record = JSONObject()
            .put("id", "qa-catch-a")
            .put("image_url", "")
            .put("species_id", "grass_carp")
            .put("species_name", "草鱼")
            .put("confidence", 0.92)
            .put("model_version", "qa-fixture")
            .put("captured_at", timestamp)
            .put("created_at", timestamp)
            .put("length_cm", JSONObject.NULL)
            .put("weight_kg", JSONObject.NULL)
            .put("location", location)
            .put("bside_status", "NONE")
        val preferences = context.getSharedPreferences("yujian_guest_archive", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putString("records", JSONArray().put(record).toString()).commit())
        context.getSharedPreferences("yujian_user_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun currentDayOfMonthLabel(): String =
        "%02d".format(Locale.US, Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    private fun currentMonthLabel(): String =
        SimpleDateFormat("MMM", Locale.ENGLISH).format(Calendar.getInstance().time).uppercase(Locale.ROOT)

    private fun fixtureTimestamp(): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 7)
            set(Calendar.SECOND, 19)
            set(Calendar.MILLISECOND, 0)
        }
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(calendar.time)
    }

    private fun launchFresh() {
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: error("Unable to resolve YuJian launcher activity")
        context.startActivity(intent)
        waitForText("最近鱼获")
    }

    private fun assertHome(label: String) {
        assertVisible("草鱼")
        assertVisible("今天 20:07")
        assertRuntimeClean("Normal Home: $label")
    }

    private fun clickVisibleText(sourceText: String, destinationText: String) {
        waitForVisibleTextBounds(sourceText)
        clickVisibleTarget(sourceText, destinationText) { findNodeByText(sourceText) }
        waitForText(destinationText)
    }

    private fun clickVisibleDescription(description: String, destinationText: String) {
        clickVisibleTarget(description, destinationText) { findNodeByDescription(description) }
        waitForText(destinationText)
    }

    private fun clickVisibleTarget(
        sourceLabel: String,
        destinationText: String,
        findSource: () -> AccessibilityNodeInfo?,
    ) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            // Poll the active accessibility tree directly. UiDevice.findObject and dumpWindowHierarchy
            // wait for global idle, which never occurs on Home while its ambient animation is running.
            findNodeByText(destinationText)?.let { destination ->
                destination.recycle()
                return
            }
            val target = findSource()
            if (target != null) {
                val bounds = Rect().also(target::getBoundsInScreen)
                val isVisible = !bounds.isEmpty && target.isVisibleToUser
                if (isVisible) {
                    val clicked = performAccessibleClick(target, bounds)
                    target.recycle()
                    if (clicked) return
                } else {
                    target.recycle()
                }
            }
            SystemClock.sleep(100L)
        }
        throw AssertionError("Timed out clicking visible target: $sourceLabel")
    }

    private fun waitForText(text: String) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNodeByText(text)
            if (node != null) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val visible = node.isVisibleToUser && !bounds.isEmpty
                node.recycle()
                if (visible) return
            }
            SystemClock.sleep(100L)
        }
        val hierarchy = evidenceFile("diagnostics_accessibility.txt")
        writeAccessibilityHierarchy(hierarchy)
        screenshot("diagnostics_screen.png")
        throw AssertionError("Timed out waiting for visible text: $text; diagnostics saved to $hierarchy")
    }

    private fun evidenceFile(name: String): File =
        File(context.getExternalFilesDir(null), "evidence/ui_rework_v1/data_sanitization/$name")
            .apply { parentFile?.mkdirs() }

    private fun waitForVisibleTextBounds(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + SETTLE_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val candidate = findNodeByText(text)
            if (candidate != null) {
                val visibleBounds = Rect().also(candidate::getBoundsInScreen)
                val visible = candidate.isVisibleToUser && !visibleBounds.isEmpty
                candidate.recycle()
                if (visible) return visibleBounds
            }
            SystemClock.sleep(100L)
        }
        throw AssertionError("Timed out waiting for visible text: $text")
    }

    private fun assertVisible(text: String) {
        val node = findNodeByText(text)
        if (node != null) {
            node.recycle()
            return
        }
        val hierarchy = evidenceFile("diagnostics_accessibility.txt")
        writeAccessibilityHierarchy(hierarchy)
        screenshot("diagnostics_screen.png")
        assertTrue("Expected visible runtime text: $text; diagnostics saved to $hierarchy", false)
    }

    private fun assertAbsent(text: String) {
        assertFalse("Unexpected dirty runtime text: $text", runtimeSemantics().contains(text.lowercase(Locale.ROOT)))
    }

    private fun assertRuntimeClean(label: String) {
        val semantics = runtimeSemantics()
        DIRTY_VALUES.forEach { dirty ->
            assertFalse("$label exposed dirty value: $dirty", semantics.contains(dirty))
        }
    }

    private fun runtimeSemantics(): String = accessibilitySnapshot()
        .lowercase(Locale.ROOT)

    private fun writeAccessibilityHierarchy(destination: File) {
        destination.writeText(accessibilitySnapshot())
    }

    private fun accessibilitySnapshot(): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow
            ?: throw AssertionError("No active accessibility root for the current runtime page")
        val output = StringBuilder()
        @Suppress("DEPRECATION")
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            try {
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if (text.isNotBlank() || description.isNotBlank()) {
                    val bounds = Rect().also(node::getBoundsInScreen)
                    output.append("  ".repeat(depth))
                        .append("text=").append(text)
                        .append(" description=").append(description)
                        .append(" bounds=").append(bounds)
                        .append('\n')
                }
                repeat(node.childCount) { index ->
                    node.getChild(index)?.let { child -> visit(child, depth + 1) }
                }
            } finally {
                node.recycle()
            }
        }
        visit(root, 0)
        return output.toString()
    }

    private fun findNodeByText(text: String): AccessibilityNodeInfo? =
        findActiveNode { it.text?.toString() == text }

    private fun findNodeByDescription(description: String): AccessibilityNodeInfo? =
        findActiveNode { it.contentDescription?.toString() == description }

    private fun findActiveNode(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val bounds = Rect().also(node::getBoundsInScreen)
            if (matches(node) && node.isVisibleToUser && !bounds.isEmpty) {
                return AccessibilityNodeInfo.obtain(node)
            }
            repeat(node.childCount) { index ->
                val child = node.getChild(index) ?: return@repeat
                val found = try {
                    visit(child)
                } finally {
                    child.recycle()
                }
                if (found != null) return found
            }
            return null
        }
        return try {
            visit(root)
        } finally {
            root.recycle()
        }
    }

    private fun performAccessibleClick(target: AccessibilityNodeInfo, bounds: Rect): Boolean {
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(target)
        repeat(MAX_CLICK_ANCESTOR_DEPTH) {
            val current = candidate ?: return@repeat
            if (current.isClickable && current.isEnabled) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                current.recycle()
                candidate = null
                if (clicked) return true
            } else {
                val parent = current.parent
                current.recycle()
                candidate = parent
            }
        }
        candidate?.recycle()
        return tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0)
        return try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
            true
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun screenshot(name: String) {
        val file = evidenceFile(name)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("Failed to capture runtime screenshot: $name", bitmap != null)
        FileOutputStream(file).use { output ->
            assertTrue("Failed to encode runtime screenshot: $name", requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        requireNotNull(bitmap).recycle()
    }

    private companion object {
        const val APP_PACKAGE = "com.yujian.ai.uiv2"
        const val SETTLE_MILLIS = 10_000L
        const val MAX_CLICK_ANCESTOR_DEPTH = 8
        val DIRTY_VALUES = listOf("null", "undefined", "n/a", "1970", ".onnx", ".tflite")
    }
}
