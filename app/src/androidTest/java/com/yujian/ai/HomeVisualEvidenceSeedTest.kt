package com.yujian.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.GuestCatchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Seeds deterministic local guest data for the main-branch visual evidence job. */
@RunWith(AndroidJUnit4::class)
class HomeVisualEvidenceSeedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedSingleGuestCatch() = seed(1)

    @Test
    fun seedMultipleGuestCatches() = seed(3)

    private fun seed(count: Int) {
        val preferences = context.getSharedPreferences(GUEST_ARCHIVE_PREFERENCES, Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "guest_catches").deleteRecursively()

        val source = File(context.cacheDir, "visual-seed.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("golden_yellow_catfish_224.jpg").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val repository = GuestCatchRepository(context)
        runBlocking {
            repeat(count) { index ->
                repository.saveCatch(
                    source,
                    CatchSaveDraft(
                        speciesId = "visual_species_$index",
                        speciesName = if (index == 0) "黄骨鱼" else "草鱼",
                        confidence = 0.92f,
                        modelVersion = "MODEL_M1_v0.6",
                    ),
                )
            }
        }

        // GuestCatchRepository uses apply() for the product save path.  The
        // evidence workflow immediately reinstalls and force-stops the app,
        // so synchronously re-committing the already-written archive makes
        // the seed durable before the launcher process reads it.
        val records = runBlocking { repository.listCatches() }
        assertEquals(count, records.size)
        val serializedRecords = preferences.getString(GUEST_RECORDS_KEY, null)
        assertTrue(serializedRecords != null)
        assertTrue(
            "Guest visual seed was not persisted",
            preferences.edit().putString(GUEST_RECORDS_KEY, serializedRecords).commit(),
        )
    }

    private companion object {
        const val GUEST_ARCHIVE_PREFERENCES = "yujian_guest_archive"
        const val GUEST_RECORDS_KEY = "records"
    }
}
