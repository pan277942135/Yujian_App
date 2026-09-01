package com.yujian.ai

import com.yujian.ai.contracts.CandidateBbox
import com.yujian.ai.contracts.FeedbackContract
import com.yujian.ai.contracts.InferenceRecord
import com.yujian.ai.feedback.FeedbackDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceContractTest {
    @Test
    fun candidate_bbox_is_normalized_xywh_and_never_ground_truth() {
        val bbox = CandidateBbox(.1f, .2f, .3f, .4f)
        val json = bbox.toJson()
        assertEquals(.1, json.getDouble(0), .00001)
        assertEquals(.4, json.getDouble(3), .00001)
        assertFalse("candidate contract must not claim ground truth", json.toString().contains("ground_truth"))
    }

    @Test
    fun confirmed_feedback_is_not_a_hard_case() {
        val feedback = FeedbackContract.fromDraft(
            FeedbackDraft("APP_1", "confirmed", "MODEL_M1_v0.2", "grass_carp", .82f, null),
        )
        assertTrue(feedback.toJson().getBoolean("is_error").not())
        assertTrue(feedback.toJson().getBoolean("hard_case").not())
    }

    @Test
    fun corrected_feedback_is_a_hard_case_candidate() {
        val feedback = FeedbackContract.fromDraft(
            FeedbackDraft("APP_2", "corrected", "MODEL_M1_v0.2", "grass_carp", .82f, "common_carp"),
        )
        assertTrue(feedback.isError)
        assertTrue(feedback.hardCase)
        assertEquals("common_carp", feedback.toJson().getString("user_label"))
    }

    @Test
    fun record_contract_has_stable_version_and_nested_sections() {
        val record = InferenceRecord(
            imageId = "yj_img_123",
            timestamp = "2026-09-01T00:00:00.000Z",
            source = "camera",
            sourceImagePath = "/data/user/0/com.yujian.ai.uiv2/files/yujian/inference/2026/09/01/yj_img_123.jpg",
            detection = null,
            crop = null,
            classification = null,
            pipelineStatus = "no_fish",
            qualityLevel = "invalid",
            qualityReason = "no_fish_detection_above_weak_threshold",
        )
        val json = record.toJson()
        assertEquals("INFERENCE_RECORD_V2", json.getString("contract_version"))
        assertTrue(json.has("detection"))
        assertTrue(json.has("crop"))
        assertTrue(json.has("classification"))
        assertTrue(json.has("feedback"))
    }
}
