package com.yujian.ai

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
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
    private val device: UiDevice
        get() = UiDevice.getInstance(instrumentation)

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
        clickVisibleTarget(sourceText, destinationText, By.text(sourceText))
        waitForText(destinationText)
    }

    private fun clickVisibleDescription(description: String, destinationText: String) {
        clickVisibleTarget(description, destinationText, By.desc(description))
        waitForText(destinationText)
    }

    private fun clickVisibleTarget(sourceLabel: String, destinationText: String, selector: BySelector) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                // A stale node can be reported after navigation starts. Check the destination first
                // so a successful click is never repeated.
                if (device.findObject(By.text(destinationText)) != null) return
                val target = device.findObject(selector)
                if (target != null && !target.visibleBounds.isEmpty) {
                    target.click()
                    return
                }
            } catch (_: StaleObjectException) {
                // Reacquire the node on the next attempt.
            }
            SystemClock.sleep(100L)
        }
        throw AssertionError("Timed out clicking visible target: $sourceLabel")
    }

    private fun waitForText(text: String) {
        try {
            waitForVisibleTextBounds(text)
        } catch (_: AssertionError) {
            val hierarchy = evidenceFile("diagnostics_hierarchy.xml")
            device.dumpWindowHierarchy(hierarchy)
            device.takeScreenshot(evidenceFile("diagnostics_screen.png"))
            throw AssertionError("Timed out waiting for visible text: $text; diagnostics saved to $hierarchy")
        }
    }

    private fun evidenceFile(name: String): File =
        File(context.getExternalFilesDir(null), "evidence/ui_rework_v1/data_sanitization/$name")
            .apply { parentFile?.mkdirs() }

    private fun waitForVisibleTextBounds(text: String): Rect {
        val selector = By.text(text)
        val candidate = device.wait(Until.findObject(selector), SETTLE_MILLIS)
        val visibleBounds = candidate?.visibleBounds?.let(::Rect)?.takeUnless { it.isEmpty }
        if (visibleBounds != null) return visibleBounds
        throw AssertionError("Timed out waiting for visible text: $text")
    }

    private fun assertVisible(text: String) {
        if (device.findObject(By.text(text)) != null) return
        val hierarchy = evidenceFile("diagnostics_hierarchy.xml")
        device.dumpWindowHierarchy(hierarchy)
        device.takeScreenshot(evidenceFile("diagnostics_screen.png"))
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

    private fun runtimeSemantics(): String {
        val output = ByteArrayOutputStream()
        device.dumpWindowHierarchy(output)
        return output.toString(Charsets.UTF_8.name()).lowercase(Locale.ROOT)
    }

    private fun screenshot(name: String) {
        val file = evidenceFile(name)
        assertTrue("Failed to capture runtime screenshot: $name", device.takeScreenshot(file))
    }

    private companion object {
        const val APP_PACKAGE = "com.yujian.ai.uiv2"
        const val SETTLE_MILLIS = 10_000L
        val DIRTY_VALUES = listOf("null", "undefined", "n/a", "1970", ".onnx", ".tflite")
    }
}
