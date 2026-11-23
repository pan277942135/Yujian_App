package com.yujian.ai.ai.subject

/** A dependency-free copy of the ONNX metadata needed to validate a SlimSAM Session. */
internal data class SlimSamTensorSignature(
    val elementType: String,
    val shape: List<Long>,
)

/** Validates the checked-in SlimSAM models against the shapes consumed by the production runner. */
internal object SlimSamTensorContract {
    private const val FLOAT = "FLOAT"
    private const val INT64 = "INT64"

    fun validateVision(
        inputs: Map<String, SlimSamTensorSignature>,
        outputs: Map<String, SlimSamTensorSignature>,
    ): String? = validateVision(inputs.keys, outputs.keys, inputs, outputs)

    fun validateDecoder(
        inputs: Map<String, SlimSamTensorSignature>,
        outputs: Map<String, SlimSamTensorSignature>,
    ): String? = validateDecoder(inputs.keys, outputs.keys, inputs, outputs)

    fun validateVision(
        inputNames: Set<String>,
        outputNames: Set<String>,
        inputs: Map<String, SlimSamTensorSignature>,
        outputs: Map<String, SlimSamTensorSignature>,
    ): String? {
        if (inputNames != setOf("pixel_values")) return "VISION_INPUT_NAMES_MISMATCH"
        if (outputNames != setOf("image_embeddings", "image_positional_embeddings")) {
            return "VISION_OUTPUT_NAMES_MISMATCH"
        }
        if (inputs.keys != inputNames || outputs.keys != outputNames) return "VISION_TENSOR_METADATA_MISSING"
        if (!hasType(inputs, "pixel_values", FLOAT)) return "VISION_INPUT_TYPE_MISMATCH"
        if (!matches(inputs.getValue("pixel_values").shape, listOf(batch(), fixed(3), fixed(1024), fixed(1024)))) {
            return "VISION_INPUT_SHAPE_MISMATCH"
        }
        for (name in outputNames) {
            val tensor = outputs.getValue(name)
            if (tensor.elementType != FLOAT) return "VISION_OUTPUT_TYPE_MISMATCH"
            if (!matches(tensor.shape, listOf(batch(), fixed(256), fixed(64), fixed(64)))) {
                return "VISION_OUTPUT_SHAPE_MISMATCH"
            }
        }
        return null
    }

    fun validateDecoder(
        inputNames: Set<String>,
        outputNames: Set<String>,
        inputs: Map<String, SlimSamTensorSignature>,
        outputs: Map<String, SlimSamTensorSignature>,
    ): String? {
        val expectedInputs = setOf("input_points", "input_labels", "image_embeddings", "image_positional_embeddings")
        val expectedOutputs = setOf("iou_scores", "pred_masks")
        if (inputNames != expectedInputs) return "DECODER_INPUT_NAMES_MISMATCH"
        if (outputNames != expectedOutputs) return "DECODER_OUTPUT_NAMES_MISMATCH"
        if (inputs.keys != inputNames || outputs.keys != outputNames) return "DECODER_TENSOR_METADATA_MISSING"
        if (!hasType(inputs, "input_points", FLOAT) || !hasType(inputs, "input_labels", INT64) ||
            !hasType(inputs, "image_embeddings", FLOAT) || !hasType(inputs, "image_positional_embeddings", FLOAT)
        ) return "DECODER_INPUT_TYPE_MISMATCH"
        if (!matches(inputs.getValue("input_points").shape, listOf(batch(), pointBatch(), pointCount(), fixed(2))) ||
            !matches(inputs.getValue("input_labels").shape, listOf(batch(), pointBatch(), pointCount())) ||
            !matches(inputs.getValue("image_embeddings").shape, listOf(batch(), fixed(256), fixed(64), fixed(64))) ||
            !matches(inputs.getValue("image_positional_embeddings").shape, listOf(batch(), fixed(256), fixed(64), fixed(64)))
        ) return "DECODER_INPUT_SHAPE_MISMATCH"
        if (!hasType(outputs, "iou_scores", FLOAT) || !hasType(outputs, "pred_masks", FLOAT)) {
            return "DECODER_OUTPUT_TYPE_MISMATCH"
        }
        if (!matches(outputs.getValue("iou_scores").shape, listOf(batch(), pointBatch(), fixed(3))) ||
            !matches(outputs.getValue("pred_masks").shape, listOf(batch(), pointBatch(), fixed(3), fixed(256), fixed(256)))
        ) return "DECODER_OUTPUT_SHAPE_MISMATCH"
        return null
    }

    private fun hasType(
        tensors: Map<String, SlimSamTensorSignature>,
        name: String,
        expected: String,
    ) = tensors[name]?.elementType == expected

    private data class Axis(
        val fixed: Long,
        val acceptsDynamic: Boolean = false,
    )
    private fun batch() = Axis(fixed = 1L, acceptsDynamic = true)
    private fun pointBatch() = Axis(fixed = 1L, acceptsDynamic = true)
    private fun pointCount() = Axis(fixed = 7L, acceptsDynamic = true)
    private fun fixed(value: Long) = Axis(fixed = value)

    private fun matches(actual: List<Long>, expected: List<Axis>): Boolean =
        actual.size == expected.size && actual.zip(expected).all { (value, axis) ->
            value == axis.fixed || (value == -1L && axis.acceptsDynamic)
        }
}

internal object SlimSamSessionReadiness {
    fun isReady(visionReady: Boolean, decoderReady: Boolean): Boolean = visionReady && decoderReady
}
