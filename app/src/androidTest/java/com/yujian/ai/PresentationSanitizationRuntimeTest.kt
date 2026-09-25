package com.yujian.ai

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

        clickText("全部")
        waitForText("我的鱼获")
        assertRuntimeClean("My Catches with null location")
        assertVisible("草鱼")
        assertVisible("09月25日")
        assertVisible("1条鱼获")
        assertAbsent("首次null")
        screenshot("02_my_catches_null_clean.png")

        clickText("草鱼")
        waitForText("鱼获详情")
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
        device.executeShellCommand("am force-stop $APP_PACKAGE")
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

    private fun clickText(text: String) {
        waitForText(text)
        val objectUnderTest = device.findObject(By.text(text))
        assertTrue("Expected clickable text: $text", objectUnderTest != null)
        objectUnderTest!!.click()
    }

    private fun waitForText(text: String) {
        assertTrue("Timed out waiting for: $text", device.wait(Until.hasObject(By.text(text)), SETTLE_MILLIS))
    }

    private fun assertVisible(text: String) {
        assertTrue("Expected visible runtime text: $text", device.findObject(By.text(text)) != null)
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
        val file = File(context.getExternalFilesDir(null), "evidence/ui_rework_v1/data_sanitization/$name")
        file.parentFile?.mkdirs()
        assertTrue("Failed to capture runtime screenshot: $name", device.takeScreenshot(file))
    }

    private companion object {
        const val APP_PACKAGE = "com.yujian.ai.uiv2"
        const val SETTLE_MILLIS = 10_000L
        val DIRTY_VALUES = listOf("null", "undefined", "n/a", "1970", ".onnx", ".tflite")
    }
}
