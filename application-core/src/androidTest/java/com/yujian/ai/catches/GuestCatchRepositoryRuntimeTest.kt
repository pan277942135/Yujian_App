package com.yujian.ai.catches

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class GuestCatchRepositoryRuntimeTest {
    @Test
    fun repeatedGuestSaveWithOneClientRecordIdReturnsOneDurableFishRecord() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clientRecordId = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "guest-idempotency-$clientRecordId.jpg").apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }
        val draft = CatchSaveDraft(
            speciesId = "grass_carp",
            speciesName = "草鱼",
            confidence = 0.91f,
            modelVersion = "test-model",
            metadata = CatchSaveMetadata(32.0, 3.6, "测试水域", "游客记录幂等验证"),
            clientRecordId = clientRecordId,
            capturedAt = "2026-10-15T08:00:00+08:00",
        )
        val preferences = context.getSharedPreferences("yujian_guest_archive", Context.MODE_PRIVATE)
        try {
            removeClientRecord(preferences, clientRecordId)
            val first = GuestCatchRepository(context).saveCatch(source, draft)
            assertTrue(File(first.imageUrl).isFile)
            val second = GuestCatchRepository(context).saveCatch(source, draft)
            assertEquals(first.id, second.id)

            val reloaded = GuestCatchRepository(context).listCatches().filter {
                it.clientRecordId == clientRecordId
            }
            assertEquals(1, reloaded.size)
            assertEquals(first.id, reloaded.single().id)
            assertTrue(File(reloaded.single().imageUrl).isFile)
        } finally {
            removeClientRecord(preferences, clientRecordId)
            source.delete()
            File(context.filesDir, "guest_catches/${guestCatchIdForClientRecord(clientRecordId)}.jpg").delete()
        }
    }

    private fun removeClientRecord(preferences: android.content.SharedPreferences, clientRecordId: String) {
        val current = runCatching {
            JSONArray(preferences.getString("records", "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val kept = JSONArray()
        for (index in 0 until current.length()) {
            val record = runCatching { current.getJSONObject(index) }.getOrNull()
            if (record == null || record.optString("client_record_id") != clientRecordId) {
                kept.put(record ?: current.opt(index))
            }
        }
        preferences.edit().putString("records", kept.toString()).commit()
    }
}
