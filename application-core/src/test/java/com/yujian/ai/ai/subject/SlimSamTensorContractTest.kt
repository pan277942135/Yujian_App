package com.yujian.ai.ai.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlimSamTensorContractTest {
    @Test
    fun visionAcceptsStaticAndDynamicBatchOneContracts() {
        assertNull(SlimSamTensorContract.validateVision(visionInputs(longArrayOf(1, 3, 1024, 1024)), visionOutputs()))
        assertNull(SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, 3, 1024, 1024)), visionOutputs()))
    }

    @Test
    fun visionRejectsWrongChannelRankAndResolutionWithSpecificErrors() {
        assertEquals(
            "VISION_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, 4, 1024, 1024)), visionOutputs()),
        )
        assertEquals(
            "VISION_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, 3, 1024)), visionOutputs()),
        )
        assertEquals(
            "VISION_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, 3, 512, 512)), visionOutputs()),
        )
    }

    @Test
    fun dynamicMetadataIsAcceptedOnlyOnAxesDeclaredDynamicByTheModelContract() {
        assertEquals(
            "VISION_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, -1, 1024, 1024)), visionOutputs()),
        )
        assertEquals(
            "VISION_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateVision(visionInputs(longArrayOf(-1, 3, -1, 1024)), visionOutputs()),
        )

        val decoderWithDynamicPointCount = decoderInputs().toMutableMap().apply {
            put("input_points", tensor("FLOAT", longArrayOf(-1, -1, -1, 2)))
            put("input_labels", tensor("INT64", longArrayOf(-1, -1, -1)))
        }
        assertNull(SlimSamTensorContract.validateDecoder(decoderWithDynamicPointCount, decoderOutputs()))
        assertEquals(
            "DECODER_INPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateDecoder(
                decoderWithDynamicPointCount.toMutableMap().apply {
                    put("input_points", tensor("FLOAT", longArrayOf(-1, -1, -1, -1)))
                },
                decoderOutputs(),
            ),
        )
    }

    @Test
    fun decoderRequiresActualTensorNamesDtypesRanksAndFixedDimensions() {
        assertNull(SlimSamTensorContract.validateDecoder(decoderInputs(), decoderOutputs()))
        assertEquals(
            "DECODER_INPUT_TYPE_MISMATCH",
            SlimSamTensorContract.validateDecoder(
                decoderInputs().toMutableMap().apply { put("input_labels", tensor("FLOAT", longArrayOf(-1, -1, -1))) },
                decoderOutputs(),
            ),
        )
        assertEquals(
            "DECODER_OUTPUT_SHAPE_MISMATCH",
            SlimSamTensorContract.validateDecoder(
                decoderInputs(),
                decoderOutputs().toMutableMap().apply { put("pred_masks", tensor("FLOAT", longArrayOf(-1, -1, 3, 128, 128))) },
            ),
        )
        assertEquals(
            "DECODER_INPUT_NAMES_MISMATCH",
            SlimSamTensorContract.validateDecoder(decoderInputs() - "image_embeddings", decoderOutputs()),
        )
    }

    @Test
    fun runtimeIsReadyOnlyWhenBothValidatedSessionsAreReady() {
        assertTrue(SlimSamSessionReadiness.isReady(visionReady = true, decoderReady = true))
        assertEquals(false, SlimSamSessionReadiness.isReady(visionReady = false, decoderReady = true))
        assertEquals(false, SlimSamSessionReadiness.isReady(visionReady = true, decoderReady = false))
    }

    private fun visionInputs(shape: LongArray) = mapOf("pixel_values" to tensor("FLOAT", shape))
    private fun visionOutputs() = mapOf(
        "image_embeddings" to tensor("FLOAT", longArrayOf(-1, 256, 64, 64)),
        "image_positional_embeddings" to tensor("FLOAT", longArrayOf(-1, 256, 64, 64)),
    )
    private fun decoderInputs() = mapOf(
        "input_points" to tensor("FLOAT", longArrayOf(-1, -1, -1, 2)),
        "input_labels" to tensor("INT64", longArrayOf(-1, -1, -1)),
        "image_embeddings" to tensor("FLOAT", longArrayOf(-1, 256, 64, 64)),
        "image_positional_embeddings" to tensor("FLOAT", longArrayOf(-1, 256, 64, 64)),
    )
    private fun decoderOutputs() = mapOf(
        "iou_scores" to tensor("FLOAT", longArrayOf(-1, -1, 3)),
        "pred_masks" to tensor("FLOAT", longArrayOf(-1, -1, 3, 256, 256)),
    )
    private fun tensor(type: String, shape: LongArray) = SlimSamTensorSignature(type, shape.toList())
}
