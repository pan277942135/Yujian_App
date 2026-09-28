package com.yujian.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.GuestCatchRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production Normal Home evidence using persisted guest FishRecords and the real launcher route. */
@RunWith(AndroidJUnit4::class)
class NormalHomeRuntimeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Test
    fun productionNormalHome_rendersFrozenHierarchyAcrossAspectRatios() {
        seed(1)
        setSize("1080x1920")
        launchAndWait()
        saveScreenshot("01_single_16_9.png")
        setSize("1080x2340")
        waitForRecompose()
        saveScreenshot("02_single_19_5_9.png")

        seed(3)
        setSize("1080x2400")
        launchAndWait()
        saveScreenshot("03_multiple_20_9.png")
        setSize("1080x2520")
        waitForRecompose()
        saveScreenshot("04_multiple_21_9.png")

        // Leave a real multi-record Normal Home alive for shell motion evidence.
        setSize("1080x1920")
        launchAndWait()
    }

    private fun seed(count: Int) {
        val preferences = context.getSharedPreferences(GUEST_ARCHIVE_PREFERENCES, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        File(context.filesDir, "guest_catches").deleteRecursively()
        val source = File(context.cacheDir, "normal-home-runtime-seed.jpg")
        instrumentation.context.assets.open("golden_yellow_catfish_224.jpg").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val species = listOf("黄骨鱼", "草鱼", "鲤鱼")
        val repository = GuestCatchRepository(context)
        runBlocking {
            repeat(count) { index ->
                repository.saveCatch(
                    source,
                    CatchSaveDraft(
                        speciesId = "normal_home_species_$index",
                        speciesName = species[index % species.size],
                        confidence = 0.92f - index * 0.03f,
                        modelVersion = "MODEL_M1_v0.6",
                    ),
                )
            }
        }
        val records = runBlocking { repository.listCatches() }
        assertEquals(count, records.size)
        val serialized = preferences.getString(GUEST_RECORDS_KEY, null)
        assertTrue(serialized != null)
        assertTrue(preferences.edit().putString(GUEST_RECORDS_KEY, serialized).commit())
    }

    private fun launchAndWait() {
        // Never force-stop the target package from inside instrumentation: on API 28
        // that can kill the test runner itself. CLEAR_TASK recreates the product
        // Activity and therefore re-reads the persisted guest archive safely.
        device.pressHome()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: error("Unable to resolve YuJian launcher activity")
        context.startActivity(launchIntent)
        assertTrue(
            "Normal Home did not become visible",
            device.wait(Until.hasObject(By.text("最近鱼获")), NORMAL_HOME_SETTLE_MILLIS),
        )
        waitForRecompose()
    }

    private fun setSize(size: String) {
        device.executeShellCommand("wm size $size")
        Thread.sleep(500)
    }

    private fun waitForRecompose() {
        device.waitForIdle()
        Thread.sleep(900)
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val root = File(context.getExternalFilesDir(null), "normal_home_v1")
        check(root.exists() || root.mkdirs())
        FileOutputStream(File(root, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    private companion object {
        const val APP_PACKAGE = "com.yujian.ai.uiv2"
        const val GUEST_ARCHIVE_PREFERENCES = "yujian_guest_archive"
        const val GUEST_RECORDS_KEY = "records"
        const val NORMAL_HOME_SETTLE_MILLIS = 10_000L
    }
}
