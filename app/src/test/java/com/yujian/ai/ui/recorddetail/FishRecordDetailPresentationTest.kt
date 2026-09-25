package com.yujian.ai.ui.recorddetail

import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishRecordDetailPresentationTest {
    private val record = RemoteCatch(
        id = "catch-42",
        imageUrl = "https://example.test/catch.jpg",
        speciesId = "grass_carp",
        speciesName = "草鱼",
        confidence = .96f,
        modelVersion = "test",
        capturedAt = "2026-09-25T08:30:00Z",
        createdAt = "2026-09-25T08:30:00Z",
        lengthCm = 42.6f,
        weightKg = 1.28f,
        location = "浙江·千岛湖",
        bsideStatus = BsideStatus.NONE,
    )

    @Test
    fun loading_is_selected_for_requested_catch_id() {
        assertEquals(
            FishRecordDetailUiState.Loading,
            FishRecordDetailPresentation.resolve("catch-42", emptyList(), loading = true, error = null),
        )
    }

    @Test
    fun matching_record_is_success() {
        val state = FishRecordDetailPresentation.resolve("catch-42", listOf(record), loading = false, error = null)
        assertEquals(FishRecordDetailUiState.Success(record), state)
    }

    @Test
    fun missing_record_is_empty_and_request_error_is_error() {
        assertEquals(
            FishRecordDetailUiState.Empty,
            FishRecordDetailPresentation.resolve("missing", listOf(record), loading = false, error = null),
        )
        assertEquals(
            FishRecordDetailUiState.Error("网络不可用"),
            FishRecordDetailPresentation.resolve("missing", emptyList(), loading = false, error = "网络不可用"),
        )
    }

    @Test
    fun fields_render_measurement_combinations_without_placeholders() {
        assertEquals("42.6 cm · 1.28 kg", FishRecordDetailPresentation.measurement(record))
        assertEquals("42.6 cm", FishRecordDetailPresentation.measurement(record.copy(weightKg = null)))
        assertEquals("1.28 kg", FishRecordDetailPresentation.measurement(record.copy(lengthCm = null)))
        assertEquals(null, FishRecordDetailPresentation.measurement(record.copy(lengthCm = null, weightKg = null)))
    }

    @Test
    fun media_uses_only_real_record_image() {
        assertEquals(listOf("https://example.test/catch.jpg"), FishRecordDetailPresentation.mediaUrls(record))
        assertTrue(FishRecordDetailPresentation.mediaUrls(record.copy(imageUrl = "")).isEmpty())
    }

    @Test
    fun sentinelLocationIsMissingAndTimestampIsHumanReadable() {
        val missingLocation = record.copy(
            location = "null",
            capturedAt = "invalid",
            createdAt = "2026-09-25T20:07:19+08:00",
        )

        assertEquals(null, FishRecordDetailPresentation.location(missingLocation))
        assertEquals("今天 20:07", FishRecordDetailPresentation.capturedAt(missingLocation))
        assertFalse(FishRecordDetailPresentation.capturedAt(missingLocation)!!.contains("2026-09-25T"))
    }

    @Test
    fun invalidBothTimestampsNeverBecome1970() {
        val missing = record.copy(capturedAt = "invalid", createdAt = "undefined")

        assertEquals(null, FishRecordDetailPresentation.capturedAt(missing))
        assertFalse(FishRecordDetailPresentation.capturedAt(missing).orEmpty().contains("1970"))
    }

    @Test
    fun navigation_contract_targets_catch_id_route() {
        assertEquals("catch/{catchId}", FishRecordDetailRoute)
    }
}
