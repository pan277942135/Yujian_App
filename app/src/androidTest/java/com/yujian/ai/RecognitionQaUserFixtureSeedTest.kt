package com.yujian.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.catches.GuestCatchRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Explicit, opt-in seed endpoints for the pinned local guest QA user.
 *
 * Run ONE method by instrumentation -e class and -e qa_seed_enabled true;
 * when invoked by the normal all-tests suite, these methods are skipped.
 * Never grants/revokes Android permissions, touches auth or creates remote users.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionQaUserFixtureSeedTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val target: Context get() = instrument.targetContext
    private val fixture: JSONObject by lazy {
        instrument.context.assets.open("qa/recognition/v1/test_users.json").bufferedReader().use {
            JSONObject(it.readText())
        }
    }

    @Test fun seedFirstUseGuest() = seed("FIRST_USE")
    @Test fun seedGuidePendingGuest() = seed("GUIDE_PENDING")
    @Test fun seedReturningGuest() = seed("RETURNING")
    @Test fun seedPermissionDeniedGuest() = seed("PERMISSION_DENIED")

    private fun seed(stateId: String) {
        assumeTrue("Explicit QA fixture use only", 
            InstrumentationRegistry.getArguments().getString("qa_seed_enabled") == "true")
        check(target.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "Refusing QA data mutation for non-debuggable app"
        }
        check(fixture.getString("schema_version") == "1.0")
        check(fixture.getJSONObject("identity").getString("user_id") == "QA_RECOGNITION_001")
        check(fixture.getJSONObject("identity").getString("type") == "GUEST_LOCAL_FIXTURE")
        check(!fixture.getJSONObject("identity").getBoolean("is_real_backend_account"))
        val storage = fixture.getJSONObject("storage_contract")
        check(storage.getString("guest_archive_preferences") == "yujian_guest_archive")
        check(storage.getString("guest_records_key") == "records")
        check(storage.getString("guest_image_directory") == "guest_catches")
        check(storage.getString("guide_preferences") == "yujian_capture_guide_v1")
        check(storage.getString("guide_completed_key") == "capture_guide_v1_completed")

        val states = fixture.getJSONArray("states")
        val state = (0 until states.length()).map { states.getJSONObject(it) }
            .single { it.getString("id") == stateId }
        assertEquals("EMPTY_HOME", state.getString("expected_initial_page"))
        val guest = target.getSharedPreferences("yujian_guest_archive", Context.MODE_PRIVATE)
        val guide = target.getSharedPreferences("yujian_capture_guide_v1", Context.MODE_PRIVATE)
        val archive = File(target.filesDir, "guest_catches")

        check(guest.edit().clear().commit()) { "Guest records reset failed" }
        check(!archive.exists() || archive.deleteRecursively()) { "Guest images reset failed" }
        check(guide.edit().clear()
            .putBoolean("capture_guide_v1_completed", state.getBoolean("guide_completed"))
            .commit()) { "Guide completion seed failed" }

        assertTrue("Guest records after seed must be empty",
            runBlocking { GuestCatchRepository(target).listCatches() }.isEmpty())
        assertEquals("[]", guest.getString("records", "[]"))
        assertEquals(state.getBoolean("guide_completed"),
            guide.getBoolean("capture_guide_v1_completed", !state.getBoolean("guide_completed")))
        assertTrue("Guest images must remain empty",
            archive.listFiles().isNullOrEmpty())

        val raw = instrument.context.assets.open("qa/recognition/v1/test_users.json").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        val receipt = JSONObject()
            .put("qa_user_id", "QA_RECOGNITION_001")
            .put("dataset_version", fixture.getString("dataset_version"))
            .put("manifest_sha256", hash)
            .put("state_id", stateId)
            .put("guest_fish_records", 0)
            .put("guide_completed", state.getBoolean("guide_completed"))
            .put("camera_permission", "HOST_CONTROLLED_NOT_MUTATED")
            .put("runtime_acceptance", "NOT_RUN")
        File(target.cacheDir, "recognition-qa-user-seed.json")
            .writeText(receipt.toString(2), Charsets.UTF_8)
    }
}
