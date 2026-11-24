package com.yujian.ai.ui.waterlight.core

/** Identity shared by Gate requests, callbacks and receipts. Nullable fields are pinned on first use. */
data class RecognitionGateIdentity(
    val attemptId: String,
    val photoIdentityHash: String? = null,
    val snapshotId: String? = null,
    val snapshotContentHash: String? = null,
    val geometryVersion: Long? = null,
)

data class RecognitionGateEvent(
    val name: String,
    val eventSequence: Long,
    val identity: RecognitionGateIdentity,
)

enum class RecognitionGateRunStatus { WAITING, RETRYING, SATISFIED, FAILED, BLOCKED }

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
 * failures; it never advances a RecognitionSceneAnimationController or changes a SceneClock.
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
    )

    private val runs = linkedMapOf<RecognitionGateId, GateRun>()
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
        val failedParents = spec.dependsOn.filter { parent ->
            runs[parent]?.status in setOf(RecognitionGateRunStatus.FAILED, RecognitionGateRunStatus.BLOCKED)
        }.toSet()
        if (failedParents.isNotEmpty()) {
            val run = GateRun(spec, identity, nowUptimeMs, nowUptimeMs, RecognitionGateRunStatus.BLOCKED,
                failureCode = "BLOCKED_BY_UPSTREAM_FAILURE", blockedBy = failedParents, lastSequence = startEvent.eventSequence)
            runs[gateId] = run
            latestSequence = maxOf(latestSequence, startEvent.eventSequence)
            return snapshotOf(gateId, run)
        }
        val pendingParents = spec.dependsOn.filter { parent ->
            runs[parent]?.let { it.status !in setOf(RecognitionGateRunStatus.SATISFIED) } == true
        }.toSet()
        if (pendingParents.isNotEmpty()) {
            failGlobal(gateId, "GATE_EVENT_ORDER_INVALID", "GATE_EVENT_ORDER_INVALID", startEvent.eventSequence, nowUptimeMs,
                "dependencies satisfied before ${gateId.name}", "pending dependencies=$pendingParents")
            return null
        }
        val run = GateRun(
            spec, identity, nowUptimeMs, safeDeadline(nowUptimeMs, spec.timeoutMs),
            lastSequence = startEvent.eventSequence,
            blocksAutoPlayback = blocksAutoPlayback && spec.countsTowardAttemptBudget,
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
                nextSequence(), nowUptimeMs, "deadline=${run.deadline}", "now=$nowUptimeMs")
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
            (expected.geometryVersion == null || expected.geometryVersion == actual.geometryVersion)

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
    ) {
        if (run.status !in ACTIVE_STATUSES) return
        run.status = RecognitionGateRunStatus.FAILED
        run.failureCode = code
        run.lastSequence = sequence
        latestSequence = maxOf(latestSequence, sequence)
        val descendants = downstreamOf(gateId)
        firstFailure = firstFailure ?: RecognitionFirstFailureRecord(
            activeAttemptId, gateId, category, code, sequence, now, expected, actual, descendants,
        )
        blockDescendants(gateId)
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
        val failedParents = mutableSetOf(failedId)
        var changed: Boolean
        do {
            changed = false
            runs.values.forEach { candidate ->
                if (candidate.status in ACTIVE_STATUSES && candidate.spec.dependsOn.any { it in failedParents }) {
                    candidate.status = RecognitionGateRunStatus.BLOCKED
                    candidate.failureCode = "BLOCKED_BY_UPSTREAM_FAILURE"
                    candidate.blockedBy = candidate.spec.dependsOn.intersect(failedParents)
                    if (failedParents.add(candidate.spec.id)) changed = true
                }
            }
        } while (changed)
    }

    private fun downstreamOf(gateId: RecognitionGateId): Set<RecognitionGateId> {
        val result = linkedSetOf<RecognitionGateId>()
        var frontier = setOf(gateId)
        while (frontier.isNotEmpty()) {
            frontier = policy.specs.values.filter { spec -> spec.id !in result && spec.dependsOn.any(frontier::contains) }
                .map { it.id }.toSet()
            result += frontier
        }
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
        run.lastSequence, run.blocksAutoPlayback, run.failureCode, run.blockedBy.toSet(),
    )

    private fun nextEvent(name: String, identity: RecognitionGateIdentity) = RecognitionGateEvent(name, nextSequence(), identity)
    private fun nextSequence(): Long = maxOf(latestSequence + 1L, 1L)
    private fun safeDeadline(start: Long, duration: Long): Long = safeAdd(start, duration)
    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private companion object {
        val ACTIVE_STATUSES = setOf(RecognitionGateRunStatus.WAITING, RecognitionGateRunStatus.RETRYING)
    }
}
