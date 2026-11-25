package com.yujian.ai.ui.waterlight.core

import java.security.MessageDigest

data class RecognitionPathBindingIdentity(
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val photoIdentityHash: String,
    val pathSnapshotContentHash: String,
    val fishPathId: String,
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
) {
    fun isValid(): Boolean = recognitionSessionId.isNotBlank() && attemptId.isNotBlank() &&
        photoId.isNotBlank() && photoIdentityHash.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
        pathSnapshotContentHash.matches(Regex("(?i)^[a-f0-9]{64}$")) && fishPathId.isNotBlank() &&
        maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) && maskVersion >= 0L && geometryVersion > 0L
}

/** Immutable linearization receipt issued only after both endpoints commit the same path value. */
data class RecognitionPathBindingCommitReceipt(
    val receiptId: String,
    val identity: RecognitionPathBindingIdentity,
    val committedAtUptimeMs: Long,
    val commitAuthority: String = "RecognitionPathBindingCommitCoordinator",
) {
    fun isValid(): Boolean = receiptId.isNotBlank() && identity.isValid() &&
        committedAtUptimeMs >= 0L && commitAuthority == "RecognitionPathBindingCommitCoordinator"
}

data class RecognitionSceneOwnerTransferReceipt(
    val receiptId: String,
    val recognitionSessionId: String,
    val attemptId: String,
    val photoId: String,
    val photoIdentityHash: String,
    val snapshotId: String,
    val snapshotContentHash: String,
    val handoffAckEventSequence: Long,
    val transferredAtUptimeMs: Long,
    val newOwner: String = "RESULT",
) {
    fun isValid(): Boolean = receiptId.isNotBlank() && recognitionSessionId.isNotBlank() &&
        attemptId.isNotBlank() && photoId.isNotBlank() &&
        photoIdentityHash.matches(Regex("(?i)^[a-f0-9]{64}$")) && snapshotId.isNotBlank() &&
        snapshotContentHash.matches(Regex("(?i)^[a-f0-9]{64}$")) && handoffAckEventSequence > 0L &&
        transferredAtUptimeMs >= 0L && newOwner == "RESULT"
}

/**
 * The single linearization authority for the Controller/SessionStore path binding. Endpoint
 * preparation is provisional; callers may publish the returned receipt only after both commits.
 */
class RecognitionPathBindingCommitCoordinator {
    private val committedByAttempt = linkedMapOf<String, RecognitionPathBindingCommitReceipt>()

    @Synchronized
    fun commit(
        identity: RecognitionPathBindingIdentity,
        nowUptimeMs: Long,
        controllerPrepared: Boolean,
        storePrepared: Boolean,
        commitController: (RecognitionPathBindingCommitReceipt) -> Boolean,
        commitStore: (RecognitionPathBindingCommitReceipt) -> Boolean,
        compensateController: (RecognitionPathBindingCommitReceipt) -> Unit = {},
        abortStore: (RecognitionPathBindingCommitReceipt?) -> Unit = {},
    ): RecognitionPathBindingCommitReceipt? {
        if (!identity.isValid() || nowUptimeMs < 0L || !controllerPrepared || !storePrepared) {
            abortStore(null)
            return null
        }
        val prior = committedByAttempt[identity.attemptId]
        if (prior != null) {
            // A conflicting late proposal must not abort or otherwise mutate an already
            // committed binding. Exact duplicates return the original receipt without
            // re-running endpoint callbacks, making retries side-effect free.
            return prior.takeIf { it.identity == identity }
        }
        val receipt = RecognitionPathBindingCommitReceipt(
            receiptId = "path-bind:${identity.attemptId}:${sha256(identity.pathSnapshotContentHash)}",
            identity = identity,
            committedAtUptimeMs = nowUptimeMs,
        )
        if (!receipt.isValid()) {
            abortStore(receipt)
            return null
        }
        if (!commitController(receipt)) {
            abortStore(receipt)
            return null
        }
        if (!commitStore(receipt)) {
            compensateController(receipt)
            abortStore(receipt)
            return null
        }
        committedByAttempt[identity.attemptId] = receipt
        return receipt
    }

    @Synchronized
    fun committedReceipt(attemptId: String): RecognitionPathBindingCommitReceipt? =
        committedByAttempt[attemptId]

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
