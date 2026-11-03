package com.yujian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.yujian.ai.session.UserSessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.CatchSaveMetadata
import com.yujian.ai.catches.GuestCatchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Seeds deterministic local guest data only; the shell runtime gate owns app launch/evidence. */
@RunWith(AndroidJUnit4::class)
class HomeVisualEvidenceSeedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedSingleGuestCatch() = seed(1)

    @Test
    fun seedMultipleGuestCatches() = seed(3)

    @Test
    fun seedGeminiReferenceGuestHome() {
        UserSessionManager(context).clear()
        val preferences = context.getSharedPreferences(GUEST_ARCHIVE_PREFERENCES, Context.MODE_PRIVATE)
        assertTrue("guest archive was cleared", preferences.edit().clear().commit())
        File(context.filesDir, "guest_catches").deleteRecursively()
        // Home applies user edits/media overlays after reading the guest archive.
        // Clear those test-app preferences too so a previous run cannot shadow
        // the deterministic fixture's location for the same stable catch IDs.
        val overlayPreferences = context.getSharedPreferences(CATCH_LOCAL_OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
        assertTrue("local catch overlays were cleared", overlayPreferences.edit().clear().commit())
        File(context.filesDir, "fish_record_memory").deleteRecursively()

        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val captureTime = Calendar.getInstance(zone).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestampFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = zone
        }
        val fixture = listOf(
            CatchFixture("nh01-catch-001", "species-grass-carp", "草鱼", 42.6, 1.28, "浙江 · 千岛湖", 0),
            CatchFixture("nh01-catch-002", "species-catfish", "长吻鮠（黄颡鱼）", 31.0, 0.84,
                "上海市青浦区岑卜村东岸临时钓位定位点地址编号321号", 10),
            CatchFixture("nh01-catch-003", "species-common-carp", "鲤鱼", 35.0, 1.12, "江苏 · 太湖", 25),
        )
        val source = File(context.cacheDir, "nh01-v4-guest-fixture.jpg")
        context.assets.open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val repository = GuestCatchRepository(context)
        runBlocking {
            fixture.forEach { item ->
                val timestamp = timestampFormatter.format(captureTime.timeInMillis - TimeUnit.MINUTES.toMillis(item.minutesBefore))
                repository.saveCatch(
                    source,
                    CatchSaveDraft(
                        speciesId = item.speciesId,
                        speciesName = item.speciesName,
                        confidence = 0.92f,
                        modelVersion = "nh01-v4-production-home-fixture",
                        metadata = CatchSaveMetadata(item.lengthCm, item.weightKg, item.location, null),
                        clientRecordId = item.clientRecordId,
                        capturedAt = timestamp,
                    ),
                )
            }
        }
        source.delete()

        val repositoryRecords = runBlocking { repository.listCatches() }
        assertEquals("the running app repository must expose all three seeded catches", 3, repositoryRecords.size)
        assertEquals("fixture catch IDs must be unique", 3, repositoryRecords.map { it.id }.toSet().size)
        assertEquals("fixture client IDs are deterministic", fixture.map { it.clientRecordId }.toSet(),
            repositoryRecords.map { it.clientRecordId }.toSet())
        val repositoryGrassCatch = repositoryRecords.single { it.speciesName == "草鱼" }
        assertEquals("repository preserves fixture length", 42.6, repositoryGrassCatch.lengthCm!!.toDouble(), 0.01)
        assertEquals("repository preserves fixture weight", 1.28, repositoryGrassCatch.weightKg!!.toDouble(), 0.01)
        assertEquals("repository preserves fixture location", "浙江 · 千岛湖", repositoryGrassCatch.location)
        assertTrue("all seeded image files must resolve locally", repositoryRecords.all { File(it.imageUrl).isFile })
        val ordered = repositoryRecords.sortedByDescending { it.capturedAt }
        assertEquals("草鱼 is the initial production card", "草鱼", ordered.first().speciesName)
        assertEquals("all fixture records share a single capture day", 1,
            repositoryRecords.map { it.capturedAt.take(10) }.toSet().size)
        val statistics = repository.statistics(repositoryRecords)
        assertEquals(3, statistics.totalCatches)
        assertEquals(3, statistics.speciesCount)
        assertEquals(1, statistics.recordDays)
        assertEquals("seed must leave the user in guest state", null, UserSessionManager(context).current())
    }

    @Test
    fun seedTwoAspectPortraitGuestCatches() {
        val preferences = context.getSharedPreferences(GUEST_ARCHIVE_PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        File(context.filesDir, "guest_catches").deleteRecursively()

        val fixtures = listOf(
            SeedCatch(
                filename = "normal-home-portrait-9x16.png",
                width = 360,
                height = 640,
                color = android.graphics.Color.rgb(46, 126, 184),
                speciesId = "visual_snakehead",
                speciesName = "黑鱼",
                capturedAt = "2026-10-04T21:50:00+08:00",
                lengthCm = 28.0,
                weightKg = 2.6,
                location = "江苏省苏州市吴中区太湖国家湿地公园东岸",
            ),
            SeedCatch(
                filename = "normal-home-portrait-4x5.png",
                width = 640,
                height = 800,
                color = android.graphics.Color.rgb(214, 53, 81),
                speciesId = "visual_mandarin_fish",
                speciesName = "鳜鱼",
                capturedAt = "2026-10-04T21:48:00+08:00",
                lengthCm = 32.0,
                weightKg = 3.0,
                location = "浙江省杭州市临安区青山湖国家森林公园东侧码头",
            ),
        )
        val repository = GuestCatchRepository(context)
        runBlocking {
            fixtures.forEach { fixture ->
                val source = createPortraitFixture(fixture)
                repository.saveCatch(
                    source,
                    CatchSaveDraft(
                        speciesId = fixture.speciesId,
                        speciesName = fixture.speciesName,
                        confidence = 0.92f,
                        modelVersion = "normal-home-data-parity-fixture",
                    ),
                )
                source.delete()
            }
        }

        val records = JSONArray(preferences.getString(GUEST_RECORDS_KEY, null))
        assertEquals(2, records.length())
        fixtures.forEachIndexed { index, fixture ->
            records.getJSONObject(index)
                .put("captured_at", fixture.capturedAt)
                .put("created_at", fixture.capturedAt)
                .put("length_cm", fixture.lengthCm)
                .put("weight_kg", fixture.weightKg)
                .put("location", fixture.location)
        }
        assertTrue(
            "Aspect-ratio guest seed was not persisted",
            preferences.edit().putString(GUEST_RECORDS_KEY, records.toString()).commit(),
        )
    }

    private fun seed(count: Int) {
        val preferences = context.getSharedPreferences(GUEST_ARCHIVE_PREFERENCES, Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "guest_catches").deleteRecursively()

        val source = File(context.cacheDir, "visual-seed.jpg")
        context.assets
            .open("home_normal/fish_record/sample_recent_catch.jpg").use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val repository = GuestCatchRepository(context)
        runBlocking {
            repeat(count) { index ->
                repository.saveCatch(
                    source,
                    CatchSaveDraft(
                        speciesId = "visual_species_$index",
                        speciesName = when (index) {
                            0 -> "草鱼"
                            1 -> "鲤鱼"
                            else -> "鲫鱼"
                        },
                        confidence = 0.92f,
                        modelVersion = "fixture-model",
                    ),
                )
            }
        }

        // GuestCatchRepository commits the archive before saveCatch returns.
        // This follow-up commit persists the visual-only metadata added below
        // before the launcher process reads the seed.
        val records = runBlocking { repository.listCatches() }
        assertEquals(count, records.size)
        val serializedRecords = preferences.getString(GUEST_RECORDS_KEY, null)
        assertTrue(serializedRecords != null)
        val visualRecords = JSONArray(serializedRecords)
        for (index in 0 until visualRecords.length()) {
            visualRecords.getJSONObject(index)
                .put("length_cm", 42.6 + index)
                .put("weight_kg", 1.28 + index * 0.12)
                .put("location", if (index == 0) "浙江 · 千岛湖" else "清晨湖畔")
        }
        assertTrue(
            "Guest visual seed was not persisted",
            preferences.edit().putString(GUEST_RECORDS_KEY, visualRecords.toString()).commit(),
        )
    }

    private fun createPortraitFixture(fixture: SeedCatch): File {
        val file = File(context.cacheDir, fixture.filename)
        val bitmap = Bitmap.createBitmap(fixture.width, fixture.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(fixture.color)
            val marker = Paint().apply { color = android.graphics.Color.rgb(255, 230, 66) }
            val markerWidth = fixture.width * 0.1f
            drawRect(0f, 0f, markerWidth, fixture.height.toFloat(), marker)
            drawRect(fixture.width - markerWidth, 0f, fixture.width.toFloat(), fixture.height.toFloat(), marker)
        }
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }

    private data class SeedCatch(
        val filename: String,
        val width: Int,
        val height: Int,
        val color: Int,
        val speciesId: String,
        val speciesName: String,
        val capturedAt: String,
        val lengthCm: Double,
        val weightKg: Double,
        val location: String,
    )

    private data class CatchFixture(
        val clientRecordId: String,
        val speciesId: String,
        val speciesName: String,
        val lengthCm: Double,
        val weightKg: Double,
        val location: String,
        val minutesBefore: Long,
    )

    private companion object {
        const val GUEST_ARCHIVE_PREFERENCES = "yujian_guest_archive"
        const val GUEST_RECORDS_KEY = "records"
        const val CATCH_LOCAL_OVERLAY_PREFERENCES = "fish_record_local_overlay_v1"
    }
}
