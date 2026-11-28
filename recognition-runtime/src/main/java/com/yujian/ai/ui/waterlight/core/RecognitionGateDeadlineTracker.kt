package com.yujian.ai.ui.waterlight.core

/** Identity shared by Gate requests, callbacks and receipts. Nullable fields are pinned on first use. */
data class RecognitionGateIdentity(
    val attemptId: String,
    val photoIdentityHash: String? = null,
    val snapshotId: String? = null,
    val snapshotContentHash: String? = null,
    val geometryVersion: Long? = null,
    val recognitionSessionId: String? = null,
    val photoId: String? = null,
)

/** Immutable producer-authored success evidence accepted by the Gate control plane. */
data class RecognitionGateReceipt(
    val receiptId: String,
    val receiptType: String,
    val gateId: RecognitionGateId,
    val producerId: String,
    val producerVersion: String,
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val photoIdentityHash: String,
    val snapshotId: String? = null,
    val snapshotContentHash: String? = null,
    val sourceEventId: String,
    val eventSequence: Long,
    val stageId: String,
    val kfId: String? = null,
    val geometryVersion: Long? = null,
    val contentHash: String? = null,
    val monotonicTimestampMs: Long,
    val resultStatus: String,
) {
    fun identity(): RecognitionGateIdentity = RecognitionGateIdentity(
        attemptId = attemptId,
        photoIdentityHash = photoIdentityHash,
        snapshotId = snapshotId,
        snapshotContentHash = snapshotContentHash,
        geometryVersion = geometryVersion,
        recognitionSessionId = recognitionSessionId,
        photoId = photoId,
    )
}

/** Data emitted only after a real production producer has validated its output. */
data class RecognitionGateProducerEvent(
    val gateId: RecognitionGateId,
    val producerId: String,
    val producerVersion: String,
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val photoIdentityHash: String,
    val snapshotId: String? = null,
    val snapshotContentHash: String? = null,
    val sourceEventId: String,
    val stageId: String,
    val kfId: String? = null,
    val geometryVersion: Long? = null,
    val contentHash: String? = null,
    val monotonicTimestampMs: Long,
    val resultStatus: String = "ACCEPTED",
)

data class RecognitionGateEvent(
    val name: String,
    val eventSequence: Long,
    val identity: RecognitionGateIdentity,
)

enum class RecognitionGateRunStatus { WAITING, RETRYING, SATISFIED, FAILED, EXPIRED, CANCELLED, BLOCKED }

enum class RecognitionGateParentState {
    PARENT_NOT_REGISTERED, PARENT_NOT_ARMED, PARENT_WAITING, PARENT_RETRYING,
    PARENT_FAILED, PARENT_EXPIRED, PARENT_CANCELLED, PARENT_SATISFIED, PARENT_BLOCKED,
}

data class RecognitionGateRunSnapshot(
    val gateId: RecognitionGateId,
    val status: RecognitionGateRunStatus,
    val identity: RecognitionGateIdentity,
    val startedAtUptimeMs: Long,
    val deadlineUptimeMs: Long,
    val retriesUsed: Int,
    val lastEventSequence: Long,
    val blocksAutoPlayback: Boolean = true,
    val failureCode: String? = null,
    val blockedBy: Set<RecognitionGateId> = emptySet(),
    val parentStates: Map<RecognitionGateId, RecognitionGateParentState> = emptyMap(),
)

data class RecognitionFirstFailureRecord(
    val attemptId: String,
    val gateId: RecognitionGateId?,
    val failureCategory: String,
    val failureCode: String,
    val eventSequence: Long,
    val timestampUptimeMs: Long,
    val expected: String,
    val actual: String,
    val blockedDownstream: Set<RecognitionGateId>,
)

data class RecognitionGateDeadlineSnapshot(
    val attemptId: String,
    val gates: Map<RecognitionGateId, RecognitionGateRunSnapshot>,
    val blockedWaitTotalMs: Long,
    val nextDeadlineUptimeMs: Long?,
    val firstFailure: RecognitionFirstFailureRecord?,
    val autoPlayback: Boolean,
    val modelRunning: Boolean,
)

/**
 * Attempt-scoped state machine for the frozen non-model Gate policy. It only reports deadlines and
 * failures and exposes authorization state to the scene owner. It never changes a SceneClock.
 */
class RecognitionGateDeadlineTracker(
    attemptId: String,
    private val policy: RecognitionGateDeadlinePolicyV1 = RecognitionGateDeadlinePolicyV1,
) {
    private data class GateRun(
        val spec: RecognitionGateDeadlineSpec,
        val identity: RecognitionGateIdentity,
        val startedAt: Long,
        val deadline: Long,
        var status: RecognitionGateRunStatus = RecognitionGateRunStatus.WAITING,
        var retries: Int = 0,
        var lastSequence: Long = 0,
        val blocksAutoPlayback: Boolean = true,
        var failureCode: String? = null,
        var blockedBy: Set<RecognitionGateId> = emptySet(),
        var parentStates: Map<RecognitionGateId, RecognitionGateParentState> = emptyMap(),
    )

    private val runs = linkedMapOf<RecognitionGateId, GateRun>()
    private val acceptedReceipts = linkedMapOf<String, RecognitionGateReceipt>()
    private var activeAttemptId = attemptId.also { require(it.isNotBlank()) }
    private var autoPlayback = true
    private var modelRunning = false
    private var latestSequence = 0L
    private var blockedWaitTotalMs = 0L
    private var blockedUnionStartedAt: Long? = null
    private var firstFailure: RecognitionFirstFailureRecord? = null

    @Synchronized
    fun armOnce(
        gateId: RecognitionGateId,
        nowUptimeMs: Long,
        identity: RecognitionGateIdentity = RecognitionGateIdentity(activeAttemptId),
        startEvent: RecognitionGateEvent = nextEvent(policy.spec(gateId).startEvent, identity),
        blocksAutoPlayback: Boolean = true,
    ): RecognitionGateRunSnapshot? {
        accountBlockedUnion(nowUptimeMs)
        if (!acceptIdentity(identity, gateId, startEvent.eventSequence, nowUptimeMs)) return null
        val existing = runs[gateId]
        if (existing != null) {
            if (existing.identity != identity || startEvent.name != existing.spec.startEvent) {
                failGlobal(gateId, "GATE_IDENTITY_MISMATCH", "GATE_IDENTITY_MISMATCH", startEvent.eventSequence, nowUptimeMs,
                    "same Attempt and original Gate start", "identity or start event changed")
                return snapshotOf(gateId, existing)
            }
            // Recomposition, duplicate delivery or a retry callback must never move the deadline.
            return snapshotOf(gateId, existing)
        }
        val spec = policy.spec(gateId)
        if (startEvent.name != spec.startEvent) {
            failGlobal(gateId, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", startEvent.eventSequence, nowUptimeMs,
                spec.startEvent, startEvent.name)
            return null
        }
        val allParents = (spec.dependsOn + spec.anyOfDependencies.flatten()).toSet()
        val parentStates = allParents.associateWith(::parentState)
        val requiredParents = spec.dependsOn
        val requiredUnsatisfied = requiredParents.filterTo(linkedSetOf()) {
            parentStates[it] != RecognitionGateParentState.PARENT_SATISFIED
        }
        val alternativesSatisfied = spec.anyOfDependencies.all { alternatives ->
            alternatives.any { parentStates[it] == RecognitionGateParentState.PARENT_SATISFIED }
        }
        if (requiredUnsatisfied.isNotEmpty() || !alternativesSatisfied) {
            val terminalParents = allParents.filterTo(linkedSetOf()) {
                parentStates[it] in setOf(
                    RecognitionGateParentState.PARENT_FAILED,
                    RecognitionGateParentState.PARENT_EXPIRED,
                    RecognitionGateParentState.PARENT_CANCELLED,
                    RecognitionGateParentState.PARENT_BLOCKED,
                )
            }
            val unsatisfied = if (requiredUnsatisfied.isNotEmpty()) requiredUnsatisfied else allParents.filterTo(linkedSetOf()) {
                parentStates[it] != RecognitionGateParentState.PARENT_SATISFIED
            }
            val code = if (terminalParents.isNotEmpty()) "BLOCKED_BY_UPSTREAM_FAILURE" else "GATE_PARENT_NOT_SATISFIED"
            val run = GateRun(
                spec = spec,
                identity = identity,
                startedAt = nowUptimeMs,
                deadline = nowUptimeMs,
                status = RecognitionGateRunStatus.BLOCKED,
                lastSequence = startEvent.eventSequence,
                failureCode = code,
                blockedBy = if (terminalParents.isNotEmpty()) terminalParents else unsatisfied,
                parentStates = parentStates,
            )
            runs[gateId] = run
            latestSequence = maxOf(latestSequence, startEvent.eventSequence)
            firstFailure = firstFailure ?: RecognitionFirstFailureRecord(
                activeAttemptId,
                gateId,
                if (terminalParents.isNotEmpty()) "BLOCKED_BY_UPSTREAM_FAILURE" else "GATE_PARENT_NOT_SATISFIED",
                code,
                startEvent.eventSequence,
                nowUptimeMs,
                "required parent receipts satisfied",
                "parent states=$parentStates",
                downstreamOf(gateId),
            )
            blockDescendants(gateId)
            return snapshotOf(gateId, run)
        }
        val run = GateRun(
            spec, identity, nowUptimeMs, safeDeadline(nowUptimeMs, spec.timeoutMs),
            lastSequence = startEvent.eventSequence,
            blocksAutoPlayback = blocksAutoPlayback && spec.countsTowardAttemptBudget,
            parentStates = parentStates,
        )
        runs[gateId] = run
        latestSequence = maxOf(latestSequence, startEvent.eventSequence)
        accountBlockedUnion(nowUptimeMs)
        return snapshotOf(gateId, run)
    }

    @Synchronized
    fun markSatisfied(gateId: RecognitionGateId, event: RecognitionGateEvent, nowUptimeMs: Long): Boolean {
        accountBlockedUnion(nowUptimeMs)
        val run = runs[gateId] ?: return orderFailure(gateId, event, nowUptimeMs, "armed Gate", "satisfaction before arm")
        if (!validateReceipt(run, event, run.spec.successEvent, nowUptimeMs)) return false
        if (run.status == RecognitionGateRunStatus.SATISFIED) return true
        if (run.status !in ACTIVE_STATUSES) return false
        run.status = RecognitionGateRunStatus.SATISFIED
        run.lastSequence = event.eventSequence
        latestSequence = maxOf(latestSequence, event.eventSequence)
        accountBlockedUnion(nowUptimeMs)
        return true
    }

    @Synchronized
    fun acceptReceipt(receipt: RecognitionGateReceipt, nowUptimeMs: Long): Boolean {
        val priorReceipt = acceptedReceipts[receipt.receiptId]
        if (priorReceipt != null) {
            if (priorReceipt == receipt) return runs[receipt.gateId]?.status == RecognitionGateRunStatus.SATISFIED
            failGlobal(
                receipt.gateId,
                "GATE_RECEIPT_ID_REUSED",
                "GATE_RECEIPT_ID_REUSED",
                receipt.eventSequence,
                nowUptimeMs,
                "same immutable receipt payload",
                "receipt ID reused with different payload",
            )
            return false
        }
        val run = runs[receipt.gateId] ?: return orderFailure(
            receipt.gateId,
            RecognitionGateEvent(receipt.receiptType, receipt.eventSequence, receipt.identity()),
            nowUptimeMs,
            "armed Gate",
            "receipt before arm",
        )
        val spec = run.spec
        val event = RecognitionGateEvent(spec.successEvent, receipt.eventSequence, receipt.identity())
        if (receipt.receiptId.isBlank() || receipt.receiptType != "PRODUCTION_SUCCESS" ||
            receipt.producerId != spec.producerId || receipt.producerVersion != spec.producerVersion ||
            receipt.recognitionSessionId.isBlank() || receipt.photoId.isBlank() ||
            receipt.sourceEventId.isBlank() || receipt.stageId.isBlank() ||
            receipt.resultStatus !in spec.acceptedReceiptStatuses ||
            receipt.monotonicTimestampMs < run.startedAt || receipt.monotonicTimestampMs > run.deadline ||
            receipt.monotonicTimestampMs > nowUptimeMs || receipt.attemptId != activeAttemptId ||
            !samePinnedIdentity(run.identity, receipt.identity())
        ) {
            failRun(receipt.gateId, run, "GATE_RECEIPT_INVALID", "GATE_RECEIPT_INVALID",
                receipt.eventSequence, nowUptimeMs, "current producer receipt before original deadline", receipt.toString())
            return false
        }
        val accepted = markSatisfied(receipt.gateId, event, nowUptimeMs)
        if (accepted) acceptedReceipts[receipt.receiptId] = receipt
        return accepted
    }

    /** Retry preserves the first request's deadline and the Attempt's accumulated wait budget. */
    @Synchronized
    fun markRetrying(
        gateId: RecognitionGateId,
        reason: RecognitionGateRetryReason,
        event: RecognitionGateEvent,
        nowUptimeMs: Long,
    ): Boolean {
        accountBlockedUnion(nowUptimeMs)
        val run = runs[gateId] ?: return orderFailure(gateId, event, nowUptimeMs, "armed Gate", "retry before arm")
        val retryEvent = "${run.spec.startEvent}:RETRY"
        if (!validateReceipt(run, event, retryEvent, nowUptimeMs)) return false
        if (run.status !in ACTIVE_STATUSES) return false
        if (!policy.mayRetry(reason)) {
            val (category, code) = when (reason) {
                RecognitionGateRetryReason.INVALID_IDENTITY,
                RecognitionGateRetryReason.INVALID_PHOTO_IDENTITY,
                RecognitionGateRetryReason.INVALID_SNAPSHOT_HASH,
                RecognitionGateRetryReason.INVALID_GEOMETRY_VERSION -> "GATE_IDENTITY_MISMATCH" to "GATE_IDENTITY_MISMATCH"
                RecognitionGateRetryReason.EVENT_SEQUENCE_OUT_OF_ORDER -> "GATE_EVENT_ORDER_INVALID" to "GATE_EVENT_ORDER_INVALID"
                RecognitionGateRetryReason.INVALID_FISH_PATH -> "NON_MODEL_GATE_FAILURE" to "FISHPATH_GEOMETRY_INVALID"
                RecognitionGateRetryReason.UNTRUSTED_MASK -> "NON_MODEL_GATE_FAILURE" to "MASK_UNTRUSTED"
                RecognitionGateRetryReason.UPSTREAM_GATE_FAILED -> "BLOCKED_BY_UPSTREAM_FAILURE" to "BLOCKED_BY_UPSTREAM_FAILURE"
                RecognitionGateRetryReason.TRANSIENT_RENDER_NOT_PRESENTED,
                RecognitionGateRetryReason.TEMPORARY_GEOMETRY_UNAVAILABLE -> error("retryable reason handled above")
            }
            failRun(gateId, run, category, code, event.eventSequence, nowUptimeMs,
                "retryable transient render or geometry issue", reason.name)
            return false
        }
        if (run.retries >= run.spec.maxRetries) {
            failRun(gateId, run, "GATE_RETRY_EXHAUSTED", run.spec.failureCode, event.eventSequence, nowUptimeMs,
                "maxRetries=${run.spec.maxRetries}", "retry ${run.retries + 1}")
            return false
        }
        run.retries += 1
        run.status = RecognitionGateRunStatus.RETRYING
        run.lastSequence = event.eventSequence
        latestSequence = maxOf(latestSequence, event.eventSequence)
        accountBlockedUnion(nowUptimeMs)
        return true
    }

    @Synchronized
    fun markFailed(
        gateId: RecognitionGateId,
        failureCode: String,
        event: RecognitionGateEvent,
        nowUptimeMs: Long,
        failureCategory: String = "NON_MODEL_GATE_FAILURE",
    ): Boolean {
        accountBlockedUnion(nowUptimeMs)
        val run = runs[gateId] ?: return orderFailure(gateId, event, nowUptimeMs, "armed Gate", "failure before arm")
        if (!validateReceipt(run, event, "failure event", nowUptimeMs, allowEventName = true)) return false
        if (run.status !in ACTIVE_STATUSES) return false
        failRun(gateId, run, failureCategory, failureCode, event.eventSequence, nowUptimeMs,
            run.spec.successEvent, event.name)
        accountBlockedUnion(nowUptimeMs)
        return true
    }

    /** Cancelling a started Gate is terminal and blocks required downstream work. */
    @Synchronized
    fun markCancelled(gateId: RecognitionGateId, nowUptimeMs: Long, failureCode: String = "GATE_CANCELLED"): Boolean {
        accountBlockedUnion(nowUptimeMs)
        val run = runs[gateId] ?: return false
        if (run.status !in ACTIVE_STATUSES) return false
        run.status = RecognitionGateRunStatus.CANCELLED
        run.failureCode = failureCode
        val sequence = nextSequence()
        run.lastSequence = sequence
        latestSequence = sequence
        firstFailure = firstFailure ?: RecognitionFirstFailureRecord(
            activeAttemptId, gateId, "GATE_CANCELLED", failureCode, sequence, nowUptimeMs,
            "active Gate", "cancelled", downstreamOf(gateId),
        )
        blockDescendants(gateId)
        accountBlockedUnion(nowUptimeMs)
        return true
    }

    @Synchronized
    fun evaluateTimeouts(nowUptimeMs: Long): List<RecognitionGateId> {
        accountBlockedUnion(nowUptimeMs)
        if (!budgetExceeded && blockedWaitTotalMs >= RecognitionGateDeadlinePolicyV1.MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS) {
            budgetExceeded = true
            failGlobal(null, "NON_MODEL_GATE_WAIT_BUDGET_EXCEEDED", "NON_MODEL_GATE_WAIT_BUDGET_EXCEEDED",
                nextSequence(), nowUptimeMs, "<${RecognitionGateDeadlinePolicyV1.MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS}ms",
                "${blockedWaitTotalMs}ms")
            runs.values.filter { it.status in ACTIVE_STATUSES }.forEach { run ->
                run.status = RecognitionGateRunStatus.BLOCKED
                run.failureCode = "BLOCKED_BY_UPSTREAM_FAILURE"
                run.blockedBy = runs.values.filter { it.status == RecognitionGateRunStatus.FAILED }.map { it.spec.id }.toSet()
            }
        }
        val timedOut = runs.values.filter { it.status in ACTIVE_STATUSES && nowUptimeMs >= it.deadline }
            .sortedBy { it.deadline }
        timedOut.forEach { run ->
            failRun(run.spec.id, run, "NON_MODEL_GATE_TIMEOUT", run.spec.failureCode,
                nextSequence(), nowUptimeMs, "deadline=${run.deadline}", "now=$nowUptimeMs",
                terminalStatus = RecognitionGateRunStatus.EXPIRED)
        }
        accountBlockedUnion(nowUptimeMs)
        return timedOut.map { it.spec.id }
    }

    @Synchronized
    fun nextDeadlineUptimeMs(): Long? {
        val nearestGate = runs.values.asSequence()
            .filter { it.status in ACTIVE_STATUSES }
            .minOfOrNull { it.deadline }
        val budgetDeadline = blockedUnionStartedAt?.takeUnless { budgetExceeded }?.let { started ->
            safeAdd(
                started,
                (RecognitionGateDeadlinePolicyV1.MAX_NON_MODEL_BLOCKED_WAIT_PER_ATTEMPT_MS - blockedWaitTotalMs)
                    .coerceAtLeast(0L),
            )
        }
        return listOfNotNull(nearestGate, budgetDeadline).minOrNull()
    }

    @Synchronized
    fun remainingTimeMs(gateId: RecognitionGateId, nowUptimeMs: Long): Long? = runs[gateId]
        ?.takeIf { it.status in ACTIVE_STATUSES }
        ?.let { (it.deadline - nowUptimeMs).coerceAtLeast(0L) }

    @Synchronized
    fun isSatisfied(gateId: RecognitionGateId): Boolean =
        runs[gateId]?.status == RecognitionGateRunStatus.SATISFIED

    @Synchronized
    fun authorizeAdvance(requiredGates: Set<RecognitionGateId>): Boolean =
        requiredGates.all { runs[it]?.status == RecognitionGateRunStatus.SATISFIED }

    /** AUTO and model terminal state are independent inputs; toggling them never changes Gate deadlines. */
    @Synchronized
    fun setRuntimeContext(nowUptimeMs: Long, autoPlayback: Boolean = this.autoPlayback, modelRunning: Boolean = this.modelRunning) {
        accountBlockedUnion(nowUptimeMs)
        this.autoPlayback = autoPlayback
        this.modelRunning = modelRunning
        accountBlockedUnion(nowUptimeMs)
    }

    @Synchronized
    fun snapshot(nowUptimeMs: Long): RecognitionGateDeadlineSnapshot {
        accountBlockedUnion(nowUptimeMs)
        return RecognitionGateDeadlineSnapshot(
            activeAttemptId,
            runs.mapValues { (_, run) -> snapshotOf(run.spec.id, run) }.toMap(),
            blockedWaitTotalMs,
            nextDeadlineUptimeMs(),
            firstFailure,
            autoPlayback,
            modelRunning,
        )
    }

    /** Only a different Attempt may reset deadlines, retries, failure and cumulative budget. */
    @Synchronized
    fun resetForNewAttempt(newAttemptId: String, nowUptimeMs: Long): Boolean {
        require(newAttemptId.isNotBlank())
        if (newAttemptId == activeAttemptId) return false
        accountBlockedUnion(nowUptimeMs)
        activeAttemptId = newAttemptId
        runs.clear()
        acceptedReceipts.clear()
        latestSequence = 0L
        blockedWaitTotalMs = 0L
        blockedUnionStartedAt = null
        firstFailure = null
        budgetExceeded = false
        return true
    }

    private var budgetExceeded = false

    private fun acceptIdentity(identity: RecognitionGateIdentity, gateId: RecognitionGateId, sequence: Long, now: Long): Boolean {
        if (identity.attemptId != activeAttemptId) {
            failGlobal(gateId, "GATE_IDENTITY_MISMATCH", "GATE_IDENTITY_MISMATCH", sequence, now,
                activeAttemptId, identity.attemptId)
            return false
        }
        if (sequence <= 0L || sequence < latestSequence) {
            failGlobal(gateId, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", sequence, now,
                "eventSequence >= $latestSequence", sequence.toString())
            return false
        }
        return true
    }

    private fun parentState(parent: RecognitionGateId): RecognitionGateParentState {
        if (policy.specs[parent] == null) return RecognitionGateParentState.PARENT_NOT_REGISTERED
        val run = runs[parent] ?: return RecognitionGateParentState.PARENT_NOT_ARMED
        return when (run.status) {
            RecognitionGateRunStatus.WAITING -> RecognitionGateParentState.PARENT_WAITING
            RecognitionGateRunStatus.RETRYING -> RecognitionGateParentState.PARENT_RETRYING
            RecognitionGateRunStatus.SATISFIED -> RecognitionGateParentState.PARENT_SATISFIED
            RecognitionGateRunStatus.FAILED -> RecognitionGateParentState.PARENT_FAILED
            RecognitionGateRunStatus.EXPIRED -> RecognitionGateParentState.PARENT_EXPIRED
            RecognitionGateRunStatus.CANCELLED -> RecognitionGateParentState.PARENT_CANCELLED
            RecognitionGateRunStatus.BLOCKED -> RecognitionGateParentState.PARENT_BLOCKED
        }
    }

    private fun validateReceipt(
        run: GateRun,
        event: RecognitionGateEvent,
        expectedName: String,
        now: Long,
        allowEventName: Boolean = false,
    ): Boolean {
        if (event.identity.attemptId != activeAttemptId || !samePinnedIdentity(run.identity, event.identity)) {
            failRun(run.spec.id, run, "GATE_IDENTITY_MISMATCH", "GATE_IDENTITY_MISMATCH", event.eventSequence,
                now, run.identity.toString(), event.identity.toString())
            return false
        }
        if (event.eventSequence <= run.lastSequence || event.eventSequence < latestSequence) {
            failRun(run.spec.id, run, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", event.eventSequence,
                now, "> ${maxOf(run.lastSequence, latestSequence)}", event.eventSequence.toString())
            return false
        }
        if (!allowEventName && event.name != expectedName) {
            failRun(run.spec.id, run, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", event.eventSequence,
                now, expectedName, event.name)
            return false
        }
        return true
    }

    private fun samePinnedIdentity(expected: RecognitionGateIdentity, actual: RecognitionGateIdentity): Boolean =
        expected.attemptId == actual.attemptId &&
            (expected.photoIdentityHash == null || expected.photoIdentityHash == actual.photoIdentityHash) &&
            (expected.snapshotId == null || expected.snapshotId == actual.snapshotId) &&
            (expected.snapshotContentHash == null || expected.snapshotContentHash == actual.snapshotContentHash) &&
            (expected.geometryVersion == null || expected.geometryVersion == actual.geometryVersion) &&
            (expected.recognitionSessionId == null || expected.recognitionSessionId == actual.recognitionSessionId) &&
            (expected.photoId == null || expected.photoId == actual.photoId)

    private fun orderFailure(gateId: RecognitionGateId, event: RecognitionGateEvent, now: Long, expected: String, actual: String): Boolean {
        failGlobal(gateId, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", event.eventSequence, now, expected, actual)
        return false
    }

    private fun failRun(
        gateId: RecognitionGateId,
        run: GateRun,
        category: String,
        code: String,
        sequence: Long,
        now: Long,
        expected: String,
        actual: String,
        terminalStatus: RecognitionGateRunStatus = RecognitionGateRunStatus.FAILED,
    ) {
        if (run.status !in ACTIVE_STATUSES) return
        run.status = terminalStatus
        run.failureCode = code
        run.lastSequence = sequence
        latestSequence = maxOf(latestSequence, sequence)
        val descendants = downstreamOf(gateId)
        if (run.spec.action != RecognitionGateFailureAction.EVIDENCE_ONLY) {
            firstFailure = firstFailure ?: RecognitionFirstFailureRecord(
                activeAttemptId, gateId, category, code, sequence, now, expected, actual, descendants,
            )
            blockDescendants(gateId)
        }
    }

    private fun failGlobal(
        gateId: RecognitionGateId?,
        category: String,
        code: String,
        sequence: Long,
        now: Long,
        expected: String,
        actual: String,
    ) {
        latestSequence = maxOf(latestSequence, sequence)
        firstFailure = firstFailure ?: RecognitionFirstFailureRecord(
            activeAttemptId, gateId, category, code, sequence, now, expected, actual,
            gateId?.let(::downstreamOf) ?: emptySet(),
        )
        if (gateId != null) runs[gateId]?.let { run ->
            run.status = RecognitionGateRunStatus.FAILED
            run.failureCode = code
            blockDescendants(gateId)
        }
    }

    private fun blockDescendants(failedId: RecognitionGateId) {
        // A child with an any-of parent group is blocked only after every alternative has
        // reached a terminal failure. Keep earlier failures in the closure so the second
        // failing alternative can close the branch.
        val failedParents = runs.values
            .filter { it.status in TERMINAL_FAILURE_STATUSES }
            .mapTo(mutableSetOf()) { it.spec.id }
            .apply { add(failedId) }
        var changed: Boolean
        do {
            changed = false
            runs.values.forEach { candidate ->
                val requiredFailed = candidate.spec.dependsOn.any { it in failedParents }
                val alternativesAllFailed = candidate.spec.anyOfDependencies.any { alternatives ->
                    alternatives.all { it in failedParents }
                }
                if (candidate.status in ACTIVE_STATUSES && (requiredFailed || alternativesAllFailed)) {
                    candidate.status = RecognitionGateRunStatus.BLOCKED
                    candidate.failureCode = "BLOCKED_BY_UPSTREAM_FAILURE"
                    candidate.blockedBy = (candidate.spec.dependsOn.intersect(failedParents) +
                        candidate.spec.anyOfDependencies.filter { group -> group.all { it in failedParents } }.flatten())
                    if (failedParents.add(candidate.spec.id)) changed = true
                }
            }
        } while (changed)
    }

    private fun downstreamOf(gateId: RecognitionGateId): Set<RecognitionGateId> {
        val failedParents = runs.values
            .filter { it.status in TERMINAL_FAILURE_STATUSES }
            .mapTo(mutableSetOf()) { it.spec.id }
            .apply { add(gateId) }
        val result = linkedSetOf<RecognitionGateId>()
        var changed: Boolean
        do {
            changed = false
            policy.specs.values.forEach { spec ->
                if (spec.id in failedParents || runs[spec.id]?.status == RecognitionGateRunStatus.SATISFIED) return@forEach
                val requiredFailed = spec.dependsOn.any { it in failedParents }
                val alternativesAllFailed = spec.anyOfDependencies.any { group -> group.all { it in failedParents } }
                if (requiredFailed || alternativesAllFailed) {
                    result += spec.id
                    if (failedParents.add(spec.id)) changed = true
                }
            }
        } while (changed)
        return result
    }

    private fun accountBlockedUnion(now: Long) {
        if (now < 0L) return
        val shouldCount = autoPlayback && !modelRunning && runs.values.any {
            it.spec.countsTowardAttemptBudget && it.blocksAutoPlayback && it.status in ACTIVE_STATUSES
        }
        val started = blockedUnionStartedAt
        if (started != null) {
            if (now >= started) blockedWaitTotalMs = safeAdd(blockedWaitTotalMs, now - started)
            blockedUnionStartedAt = null
        }
        if (shouldCount) blockedUnionStartedAt = now
    }

    private fun snapshotOf(id: RecognitionGateId, run: GateRun) = RecognitionGateRunSnapshot(
        id, run.status, run.identity, run.startedAt, run.deadline, run.retries,
        run.lastSequence, run.blocksAutoPlayback, run.failureCode, run.blockedBy.toSet(), run.parentStates.toMap(),
    )

    private fun nextEvent(name: String, identity: RecognitionGateIdentity) = RecognitionGateEvent(name, nextSequence(), identity)
    private fun nextSequence(): Long = maxOf(latestSequence + 1L, 1L)
    private fun safeDeadline(start: Long, duration: Long): Long = safeAdd(start, duration)
    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private companion object {
        val ACTIVE_STATUSES = setOf(RecognitionGateRunStatus.WAITING, RecognitionGateRunStatus.RETRYING)
        val TERMINAL_FAILURE_STATUSES = setOf(
            RecognitionGateRunStatus.FAILED,
            RecognitionGateRunStatus.EXPIRED,
            RecognitionGateRunStatus.CANCELLED,
            RecognitionGateRunStatus.BLOCKED,
        )
    }
}
