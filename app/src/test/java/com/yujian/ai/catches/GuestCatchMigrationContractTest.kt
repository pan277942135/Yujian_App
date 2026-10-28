package com.yujian.ai.catches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestCatchMigrationContractTest {
    private val guest = RemoteCatch(
        id = "guest_record_01",
        imageUrl = "/tmp/guest_record_01.jpg",
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = 0.92f,
        modelVersion = "MODEL_M1_v0.5",
        capturedAt = "2026-10-09T08:15:30+08:00",
        createdAt = "2026-10-09T08:15:30+08:00",
        lengthCm = 32f,
        weightKg = 3.6f,
        location = "上海市青浦区",
        story = "清晨湖边的一次收获",
    )

    private val server = guest.copy(
        id = "server-catch-01",
        imageUrl = "/api/v1/catches/server-catch-01/media",
        clientRecordId = guest.id,
        capturedAt = "2026-10-09T00:15:30Z",
    )

    @Test
    fun migrationGateAcceptsExactReadBackWithEquivalentTimezone() {
        assertTrue(guestMigrationMatches(guest, server))
    }

    @Test
    fun migrationDraftUsesTheFinalViewAndStableSourceRecordId() {
        val edited = guest.copy(
            lengthCm = 33f,
            location = "上海市青浦区（东岸）",
            story = "编辑后的回忆",
        )

        val draft = guestMigrationDraft(guest.id, edited)

        assertEquals(guest.id, draft.clientRecordId)
        assertEquals(33.0, draft.metadata?.lengthCm)
        assertEquals("上海市青浦区（东岸）", draft.metadata?.location)
        assertEquals("编辑后的回忆", draft.metadata?.story)
        assertEquals(guest.capturedAt, draft.capturedAt)
    }

    @Test
    fun migrationGateRejectsWrongMappingOrAnyLostUserValue() {
        assertFalse(guestMigrationMatches(guest, server.copy(clientRecordId = "another-record")))
        assertFalse(guestMigrationMatches(guest, server.copy(lengthCm = null)))
        assertFalse(guestMigrationMatches(guest, server.copy(weightKg = 3.5f)))
        assertFalse(guestMigrationMatches(guest, server.copy(location = null)))
        assertFalse(guestMigrationMatches(guest, server.copy(story = null)))
        assertFalse(guestMigrationMatches(guest, server.copy(imageUrl = "")))
        assertFalse(guestMigrationMatches(guest, server.copy(imageUrl = "/api/v1/catches/another/media")))
    }
}
