package com.yujian.ai.ui.waterlight.core

/** Stable IDs for non-model production gates. Values are part of Gate Policy V1. */
enum class RecognitionGateId {
    PHOTO_FIRST_FRAME,
    PROCESSING_GEOMETRY,
    ORB_ANCHOR,
    RELEASE_RENDER,
    PERIMETER_ARRIVAL_RENDER,
    REVEAL_DECISION,
    MASK_CONTOUR_PSTAR,
    PATH_BOUND,
    ORB_ONLY_RETURN_PATH,
    CONTOUR_RENDER,
    FISH_GOLD_RENDER,
    RETURN_RENDER,
    GLYPH_PATH,
    ORB_TO_GLYPH_RENDER,
    GLYPH_REVEAL_NAME_STABLE,
    RESULT_HOST_GEOMETRY,
    PHOTO_SETTLE,
    HANDOFF_ACK,
    SCENE_OWNER_TRANSFER,
    RESULT_INFO_ARRIVAL,
    RESULT_SCENE_STABLE,
    RESIDUE_SETTLE_RENDER,
    PIXELCOPY_CAPTURE,
}

enum class RecognitionGateFailureAction { FAIL_ATTEMPT, BLOCK_DOWNSTREAM, EVIDENCE_ONLY }
enum class RecognitionGateCategory { BLOCKING, OBSERVATIONAL, OPTIONAL }

enum class RecognitionGateRetryReason {
    TRANSIENT_RENDER_NOT_PRESENTED,
    TEMPORARY_GEOMETRY_UNAVAILABLE,
    INVALID_IDENTITY,
    INVALID_PHOTO_IDENTITY,
    INVALID_SNAPSHOT_HASH,
    INVALID_GEOMETRY_VERSION,
    EVENT_SEQUENCE_OUT_OF_ORDER,
    INVALID_FISH_PATH,
    UNTRUSTED_MASK,
    UPSTREAM_GATE_FAILED,
}

data class RecognitionGateDeadlineSpec(
    val id: RecognitionGateId,
    val timeoutMs: Long,
    val maxRetries: Int,
    val maxCumulativeBlockedMs: Long,
    val countsTowardAttemptBudget: Boolean,
    val startEvent: String,
    val successEvent: String,
    val failureCode: String,
    val producerId: String = "${id.name}_PRODUCER",
    val producerVersion: String = "1.0.0",
    val acceptedReceiptStatuses: Set<String> = setOf("ACCEPTED"),
    val action: RecognitionGateFailureAction = RecognitionGateFailureAction.BLOCK_DOWNSTREAM,
    val dependsOn: Set<RecognitionGateId> = emptySet(),
    /** Each set is an alternative parent group; one complete group must be satisfied. */
    val anyOfDependencies: List<Set<RecognitionGateId>> = emptyList(),
    val category: RecognitionGateCategory = RecognitionGateCategory.BLOCKING,
) {
    init {
        require(timeoutMs in 1L..RecognitionGateDeadlinePolicyV1.MAX_SINGLE_NON_MODEL_GATE_MS)
        require(maxRetries >= 0)
        require(maxCumulativeBlockedMs in 0L..timeoutMs)
        require(startEvent.isNotBlank() && successEvent.isNotBlank() && failureCode.isNotBlank())
        require(producerId.isNotBlank() && producerVersion.isNotBlank())
        require(acceptedReceiptStatuses.isNotEmpty() && acceptedReceiptStatuses.none(String::isBlank))
        require(countsTowardAttemptBudget || maxCumulativeBlockedMs == 0L)
        require(anyOfDependencies.none { it.isEmpty() })
        require(anyOfDependencies.flatten().none { it in dependsOn })
        require(category != RecognitionGateCategory.OBSERVATIONAL || action == RecognitionGateFailureAction.EVIDENCE_ONLY)
    }
}

/** Frozen numeric policy. Keep these values in sync with 12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md. */
object RecognitionGateDeadlinePolicyV1 {
    const val POLICY_ID = "YUJIAN_NON_MODEL_GATE_DEADLINE_POLICY_V1"
    const val VERSION = "1.0.0"
    const val ACTIVE_PRESENTATION_BUDGET_MS = 6_000L
    const val MODEL_ATTEMPT_DEADLINE_MS = 60_000L
    const val MAX_SINGLE_NON_MODEL_GATE_MS = 3_000L
    const val MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS = 10_000L
    const val PIXELCOPY_CAPTURE_TIMEOUT_MS = 1_500L

    private fun gate(
        id: RecognitionGateId,
        timeout: Long,
        retries: Int,
        start: String,
        success: String,
        failure: String,
        producerId: String = "${id.name}_PRODUCER",
        producerVersion: String = "1.0.0",
        acceptedStatuses: Set<String> = setOf("ACCEPTED"),
        counts: Boolean = true,
        dependsOn: Set<RecognitionGateId> = emptySet(),
        anyOf: List<Set<RecognitionGateId>> = emptyList(),
        category: RecognitionGateCategory = if (counts) RecognitionGateCategory.BLOCKING else RecognitionGateCategory.OBSERVATIONAL,
    ) = RecognitionGateDeadlineSpec(
        id, timeout, retries, if (counts) timeout else 0L, counts,
        start, success, failure, producerId, producerVersion, acceptedStatuses,
        if (category == RecognitionGateCategory.OBSERVATIONAL) RecognitionGateFailureAction.EVIDENCE_ONLY
        else RecognitionGateFailureAction.BLOCK_DOWNSTREAM,
        dependsOn,
        anyOf,
        category,
    )

    val specs: Map<RecognitionGateId, RecognitionGateDeadlineSpec> = listOf(
        gate(RecognitionGateId.PHOTO_FIRST_FRAME, 3_000, 2, "PHOTO_STABLE_FIRST_FRAME_REQUEST", "PHOTO_FRAME_COMMITTED", "PHOTO_FIRST_FRAME_TIMEOUT", producerId = "PHOTO_FRAME_COMPOSITOR"),
        gate(RecognitionGateId.PROCESSING_GEOMETRY, 2_000, 1, "NORMALIZED_PHOTO_GEOMETRY_REQUEST", "PROCESSING_GEOMETRY_READY", "PROCESSING_GEOMETRY_TIMEOUT", dependsOn = setOf(RecognitionGateId.PHOTO_FIRST_FRAME), producerId = "PHOTO_TRANSFORM_PRODUCER"),
        gate(RecognitionGateId.ORB_ANCHOR, 1_500, 1, "ORB_WAKE_ENTERED", "ORB_ANCHOR_READY", "ORB_ANCHOR_TIMEOUT", dependsOn = setOf(RecognitionGateId.PROCESSING_GEOMETRY)),
        gate(RecognitionGateId.RELEASE_RENDER, 1_500, 2, "ORB_RELEASE_ENTERED", "ORB_RELEASE_RENDERED", "RELEASE_RENDER_TIMEOUT", dependsOn = setOf(RecognitionGateId.ORB_ANCHOR), producerId = "STAGE045_GLES_RENDERER"),
        gate(RecognitionGateId.PERIMETER_ARRIVAL_RENDER, 1_500, 2, "PERIMETER_ARRIVAL_ENTERED", "PERIMETER_ARRIVAL_RENDERED", "PERIMETER_ARRIVAL_RENDER_TIMEOUT", dependsOn = setOf(RecognitionGateId.RELEASE_RENDER), producerId = "STAGE045_GLES_RENDERER"),
        gate(RecognitionGateId.REVEAL_DECISION, 1_000, 0, "ALL_REQUIRED_MODELS_TERMINAL", "REVEAL_BRANCH_DECIDED", "REVEAL_DECISION_TIMEOUT"),
        gate(RecognitionGateId.MASK_CONTOUR_PSTAR, 2_000, 0, "MASK_CALLBACK_TERMINAL", "MASK_CONTOUR_PSTAR_READY", "MASK_CONTOUR_PSTAR_TIMEOUT", acceptedStatuses = setOf("ACCEPTED", "DEGRADED_ACCEPTED")),
        gate(RecognitionGateId.PATH_BOUND, 2_000, 1, "FISHPATH_DUAL_END_BIND_REQUESTED", "FISHPATH_BOUND_BY_CONTROLLER_AND_STORE", "PATH_BOUND_TIMEOUT", dependsOn = setOf(RecognitionGateId.MASK_CONTOUR_PSTAR, RecognitionGateId.REVEAL_DECISION)),
        gate(RecognitionGateId.ORB_ONLY_RETURN_PATH, 1_500, 1, "ORB_ONLY_BRANCH_CONFIRMED", "ORB_ONLY_RETURN_PATH_READY", "ORB_ONLY_RETURN_PATH_TIMEOUT", dependsOn = setOf(RecognitionGateId.REVEAL_DECISION), category = RecognitionGateCategory.OPTIONAL),
        gate(RecognitionGateId.CONTOUR_RENDER, 1_500, 2, "KF10_KF11_KF12_RENDER_REQUESTED", "CONTOUR_RENDERED", "CONTOUR_RENDER_TIMEOUT", dependsOn = setOf(RecognitionGateId.MASK_CONTOUR_PSTAR)),
        gate(RecognitionGateId.FISH_GOLD_RENDER, 1_500, 2, "KF13_ENTERED", "FISH_GOLD_RENDERED", "FISH_GOLD_RENDER_TIMEOUT", dependsOn = setOf(RecognitionGateId.PATH_BOUND)),
        gate(RecognitionGateId.RETURN_RENDER, 2_000, 2, "KF14_ORB_RETURN_ENTERED", "RETURN_RENDERED", "RETURN_RENDER_TIMEOUT", anyOf = listOf(setOf(RecognitionGateId.PATH_BOUND, RecognitionGateId.ORB_ONLY_RETURN_PATH))),
        gate(RecognitionGateId.GLYPH_PATH, 2_500, 1, "VALID_SPECIES_RESULT_GLYPHPATH_REQUESTED", "GLYPH_PATH_READY", "GLYPH_PATH_TIMEOUT"),
        gate(RecognitionGateId.ORB_TO_GLYPH_RENDER, 2_000, 2, "KF15_ENTERED", "ORB_TO_GLYPH_RENDERED", "ORB_TO_GLYPH_RENDER_TIMEOUT", dependsOn = setOf(RecognitionGateId.RETURN_RENDER, RecognitionGateId.GLYPH_PATH)),
        gate(RecognitionGateId.GLYPH_REVEAL_NAME_STABLE, 2_000, 2, "KF18_ENTERED", "GLYPH_AND_NAME_STABLE", "GLYPH_REVEAL_NAME_STABLE_TIMEOUT", dependsOn = setOf(RecognitionGateId.ORB_TO_GLYPH_RENDER)),
        gate(RecognitionGateId.RESULT_HOST_GEOMETRY, 3_000, 2, "RESULT_HOST_PREMOUNT_GEOMETRY_REQUESTED", "RESULT_HOST_GEOMETRY_READY", "RESULT_GEOMETRY_TIMEOUT"),
        gate(RecognitionGateId.PHOTO_SETTLE, 2_000, 1, "STAGE13_2_ENTERED", "PHOTO_SETTLED", "PHOTO_SETTLE_TIMEOUT"),
        gate(RecognitionGateId.HANDOFF_ACK, 1_000, 0, "HANDOFF_ENVELOPE_EMITTED", "HANDOFF_CONSUMER_ACKNOWLEDGED", "HANDOFF_ACK_TIMEOUT"),
        gate(RecognitionGateId.SCENE_OWNER_TRANSFER, 1_000, 0, "CONSUMER_ACK_SCENE_OWNER_TRANSFER_REQUESTED", "SCENE_OWNER_TRANSFERRED", "SCENE_OWNER_TRANSFER_TIMEOUT", dependsOn = setOf(RecognitionGateId.HANDOFF_ACK)),
        gate(RecognitionGateId.RESULT_INFO_ARRIVAL, 1_500, 1, "STAGE13_4_ENTERED", "RESULT_INFO_FRAME_PRESENTED", "RESULT_INFO_ARRIVAL_TIMEOUT", dependsOn = setOf(RecognitionGateId.SCENE_OWNER_TRANSFER)),
        gate(RecognitionGateId.RESULT_SCENE_STABLE, 2_000, 1, "STAGE13_5_ENTERED", "RESULT_SCENE_STABLE_RECEIPT_ACCEPTED", "RESULT_SCENE_STABLE_TIMEOUT", dependsOn = setOf(RecognitionGateId.RESULT_INFO_ARRIVAL)),
        gate(RecognitionGateId.RESIDUE_SETTLE_RENDER, 1_500, 2, "STAGE13_RESIDUE_STABILIZATION_REQUESTED", "RESIDUE_RENDER_STABLE", "RESIDUE_SETTLE_RENDER_TIMEOUT"),
        gate(RecognitionGateId.PIXELCOPY_CAPTURE, PIXELCOPY_CAPTURE_TIMEOUT_MS, 0, "PIXELCOPY_REQUEST_SUBMITTED", "PIXELCOPY_CAPTURED", "PIXELCOPY_CAPTURE_TIMEOUT", counts = false),
    ).associateBy { it.id }

    init {
        require(specs.keys == RecognitionGateId.entries.toSet()) { "Gate registry must define every known Gate exactly once" }
        validateDependencyGraph(specs)
    }

    fun spec(id: RecognitionGateId): RecognitionGateDeadlineSpec = requireNotNull(specs[id])

    fun specOrNull(id: String): RecognitionGateDeadlineSpec? =
        RecognitionGateId.entries.firstOrNull { it.name == id }?.let(specs::get)

    internal fun validateDependencyGraph(candidate: Map<RecognitionGateId, RecognitionGateDeadlineSpec>) {
        val visiting = mutableSetOf<RecognitionGateId>()
        val visited = mutableSetOf<RecognitionGateId>()
        fun visit(id: RecognitionGateId) {
            if (id in visited) return
            require(visiting.add(id)) { "Gate dependency cycle at ${id.name}" }
            val spec = requireNotNull(candidate[id]) { "Unregistered Gate dependency ${id.name}" }
            (spec.dependsOn + spec.anyOfDependencies.flatten()).forEach(::visit)
            visiting.remove(id)
            visited += id
        }
        candidate.keys.forEach(::visit)
    }

    fun mayRetry(reason: RecognitionGateRetryReason): Boolean = when (reason) {
        RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
        RecognitionGateRetryReason.TEMPORARY_GEOMETRY_UNAVAILABLE -> true
        else -> false
    }
}
