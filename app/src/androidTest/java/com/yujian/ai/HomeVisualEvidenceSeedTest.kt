package com.yujian.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.GuestCatchRepository
import kotlinx.coroutines.runBlocking
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
        context.getSharedPreferences("yujian_guest_archive", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "guest_catches").deleteRecursively()

        val source = File(context.cacheDir, "visual-seed.jpg")
        context.assets.open("golden_yellow_catfish_224.jpg").use { input ->
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
    }
}
