package com.yujian.ai.catches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestCatchIdempotencyTest {
    @Test
    fun oneClientRecordIdMapsToTheSameSafeGuestRecordId() {
        val first = guestCatchIdForClientRecord("d2d911ab-1f39-4de8-a5b5-2cd85f5659e8")
        val retry = guestCatchIdForClientRecord("d2d911ab-1f39-4de8-a5b5-2cd85f5659e8")

        assertEquals(first, retry)
        assertTrue(first.matches(Regex("guest_[0-9a-f]{32}")))
    }

    @Test
    fun differentSaveIntentsReceiveDifferentGuestRecordIds() {
        assertNotEquals(
            guestCatchIdForClientRecord("client-record-a"),
            guestCatchIdForClientRecord("client-record-b"),
        )
    }
}
