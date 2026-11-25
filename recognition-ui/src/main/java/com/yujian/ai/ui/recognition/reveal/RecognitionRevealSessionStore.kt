package com.yujian.ai.ui.recognition.reveal

import androidx.lifecycle.ViewModel
import com.yujian.ai.ui.recognition.result.resultSpeciesDisplayName
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.RecognitionAttemptDeadline
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.recognition.domain.PhotoIdentity
import com.yujian.ai.recognition.domain.ClassifierResult as FrozenClassifierResult
import com.yujian.ai.recognition.domain.ContourOutcome as FrozenContourOutcome
import com.yujian.ai.recognition.domain.DetectorDetection as FrozenDetectorDetection
import com.yujian.ai.recognition.domain.DetectorResult as FrozenDetectorResult
import com.yujian.ai.recognition.domain.FishPathSnapshotRef as FrozenFishPathSnapshotRef
import com.yujian.ai.recognition.domain.MaskOutcome as FrozenMaskOutcome
import com.yujian.ai.recognition.domain.ModelBundleIdentity as FrozenModelBundleIdentity
import com.yujian.ai.recognition.domain.ModelIdentity as FrozenModelIdentity
import com.yujian.ai.recognition.domain.PStarGeometry as FrozenPStarGeometry
import com.yujian.ai.recognition.domain.RecognitionOutcome as FrozenRecognitionOutcome
import com.yujian.ai.recognition.domain.RecognitionResultSnapshot as FrozenRecognitionResultSnapshot
import com.yujian.ai.recognition.domain.RecognitionSnapshotCommitter
import com.yujian.ai.recognition.domain.SnapshotCommitStatus
import com.yujian.ai.recognition.domain.SpeciesResult as FrozenSpeciesResult
import com.yujian.ai.recognition.domain.isSha256
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.RecognitionSnapshot
import com.yujian.ai.ui.waterlight.core.RecognitionOrbOnlyReturnPathSnapshot
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionPathBindingCommitReceipt
import com.yujian.ai.ui.waterlight.core.RecognitionSceneOwnerTransferReceipt
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import java.security.MessageDigest

data class RecognitionRevealSessionSnapshot(
    val recognitionSessionId: String,
    val recognitionAttemptId: String? = null,
    val image: SelectedImage? = null,
    val productionResult: ProductionRecognitionResult? = null,
    val prediction: RecognitionPrediction? = null,
    val subjectResult: FishSubjectResult = FishSubjectResult(SubjectStatus.IDLE),
    val inferenceAsset: InferenceAsset? = null,
    val subjectGeometry: RecognitionSubjectGeometrySnapshot? = null,
    val processingPhoto: RecognitionProcessingPhotoSnapshot? = null,
    val fishPathSnapshot: RecognitionSnapshot? = null,
    /** Path callback retained until Subject and measured Photo identities arrive. */
    val pendingFishPathSnapshot: RecognitionSnapshot? = null,
    val fishPathSnapshotBindingState: RecognitionFishPathSnapshotBindingState =
        RecognitionFishPathSnapshotBindingState.NOT_RECEIVED,
    val fishPathBindingCommitReceiptId: String? = null,
    val sceneOwner: RecognitionSceneOwner = RecognitionSceneOwner.RECOGNITION,
    val sceneOwnerTransferReceiptId: String? = null,
    val fishPathSnapshotBindingDiagnostic: Map<String, String> = emptyMap(),
    val fishPathSnapshotPathId: String? = null,
    val fishPathSnapshotProducerSha256: String? = null,
    val fishPathSnapshotContentSha256: String? = null,
    val fishPathSnapshotReceivedAtUptimeMs: Long? = null,
    val fishPathSnapshotPendingReason: String? = null,
    val orbOnlyReturnPathSnapshot: RecognitionOrbOnlyReturnPathSnapshot? = null,
    val handoffFreezeAtUptimeMs: Long? = null,
    val handoffFreezePathBound: Boolean? = null,
    val pStarPathTerminalUnavailable: Boolean = false,
    val pStarPathFailureReason: String? = null,
    val attemptStartedUptimeMs: Long = 0L,
    val handoff: RecognitionRevealHandoff? = null,
    val highConfidenceResult: Boolean = false,
    val revealDecision: RecognitionRevealDecisionState = RecognitionRevealDecisionState.WAITING_BOTH,
    val resultHandoffState: RecognitionResultHandoffState = RecognitionResultHandoffState(),
    val committedResultSnapshot: FrozenRecognitionResultSnapshot? = null,
    val commitCandidateSnapshotId: String? = null,
) {
    val pStarPathState: RecognitionPStarPathState
        get() = RecognitionRevealDecisionGate.pStarState(
            hasBoundPath = fishPathSnapshot?.isValid() == true,
            terminalUnavailable = pStarPathTerminalUnavailable,
        )
}

fun RecognitionRevealSessionSnapshot.matches(key: RecognitionRevealKey): Boolean =
    recognitionSessionId == key.recognitionSessionId && recognitionAttemptId == key.recognitionAttemptId

internal enum class RecognitionFishPathSnapshotBindOutcome {
    WAITING_FOR_INPUTS,
    ACCEPTED,
    IDENTITY_MISMATCH,
    MASK_MISMATCH,
    GEOMETRY_MISMATCH,
}

enum class RecognitionFishPathSnapshotBindingState {
    NOT_RECEIVED,
    RECEIVED_PENDING,
    PREPARED,
    BOUND,
    REJECTED_IDENTITY,
    REJECTED_MASK,
    REJECTED_GEOMETRY,
    REJECTED_FROZEN_HANDOFF,
}

enum class RecognitionSceneOwner { RECOGNITION, RESULT }

/** Pure identity join used by SessionStore when Path, Photo, and Subject callbacks race. */
internal object RecognitionFishPathSnapshotBinding {
    const val P_STAR_VIEWPORT_TOLERANCE_PX = 1f

    fun evaluate(
        path: RecognitionSnapshot?,
        subjectStatus: SubjectStatus,
        subject: RecognitionSubjectGeometrySnapshot?,
        photo: RecognitionProcessingPhotoSnapshot?,
        sessionId: String,
        attemptId: String,
        imageId: String,
        photoSha256: String,
        photoContentVersion: Long,
        photoIdentity: PhotoIdentity? = null,
    ): RecognitionFishPathSnapshotBindOutcome {
        if (path == null || subject == null || photo == null || subjectStatus != SubjectStatus.READY) {
            return RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS
        }
        val identityMatches = path.sessionId == sessionId && path.attemptId == attemptId &&
            path.imageId == imageId && path.photoSha256 == photoSha256 &&
            path.photoContentVersion == photoContentVersion && path.photoIdentity == photoIdentity &&
            photo.imageId == imageId && photo.photoIdentity == photoIdentity &&
            subject.imageId == imageId && subject.photoSha256 == photoSha256 &&
            subject.photoIdentity == photoIdentity &&
            subject.recognitionSessionId == sessionId && subject.recognitionAttemptId == attemptId
        if (!identityMatches) return RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH

        // A Path callback can race the Processing Photo geometry callback. Empty/default photo
        // fields are not contradictory Mask evidence: keep the immutable Path pending until the
        // same photo reports its Mask identity. Compare complete producer/subject identities
        // immediately so genuine Mask disagreements remain fail-closed.
        if (subject.maskSha256.isBlank() || subject.maskVersion < 0L) {
            return RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS
        }
        if (path.maskSha256 != subject.maskSha256 || path.maskVersion != subject.maskVersion) {
            return RecognitionFishPathSnapshotBindOutcome.MASK_MISMATCH
        }
        if (photo.contactPointMaskSha256.isBlank() || photo.contactPointMaskVersion == -1L) {
            return RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS
        }
        if (photo.contactPointMaskVersion < 0L) {
            return RecognitionFishPathSnapshotBindOutcome.MASK_MISMATCH
        }
        if (path.maskSha256 != photo.contactPointMaskSha256 ||
            path.maskVersion != photo.contactPointMaskVersion
        ) return RecognitionFishPathSnapshotBindOutcome.MASK_MISMATCH

        val photoContactPoint = photo.contactPointNormalizedSource
            ?: return RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS
        if (photo.contactPointGeometryVersion == 0L || hasMissingTransformInputs(photo.sourceToViewport)) {
            return RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS
        }
        if (photo.contactPointGeometryVersion < 0L || hasMalformedTransform(photo.sourceToViewport) ||
            !photoContactPoint.x.isFinite() || !photoContactPoint.y.isFinite() ||
            photoContactPoint.x !in 0f..1f || photoContactPoint.y !in 0f..1f
        ) {
            return RecognitionFishPathSnapshotBindOutcome.GEOMETRY_MISMATCH
        }

        val photoContact = WaterLightPathPoint(photoContactPoint.x, photoContactPoint.y)
        val expectedContactInViewport = photo.sourceToViewport.mapNormalized(
            photoContactPoint.x,
            photoContactPoint.y,
        ).let { WaterLightPathPoint(it.x, it.y) }
        val endpointMatches = path.fishPathGeometry.end.isWithinViewportToleranceOf(
            expectedContactInViewport,
            P_STAR_VIEWPORT_TOLERANCE_PX,
        )
        val geometryMatches = path.isValid() && path.geometryVersion > 0L &&
            path.geometryVersion == photo.contactPointGeometryVersion &&
            photo.sourceToViewport.sourceWidthPx == subject.sourceWidthPx &&
            photo.sourceToViewport.sourceHeightPx == subject.sourceHeightPx &&
            path.pStarNormalizedSource == photoContact && endpointMatches
        return if (geometryMatches) RecognitionFishPathSnapshotBindOutcome.ACCEPTED
        else RecognitionFishPathSnapshotBindOutcome.GEOMETRY_MISMATCH
    }

    fun diagnostic(
        path: RecognitionSnapshot?,
        subject: RecognitionSubjectGeometrySnapshot?,
        photo: RecognitionProcessingPhotoSnapshot?,
        sessionId: String,
        attemptId: String,
        imageId: String,
        photoSha256: String,
        photoContentVersion: Long,
        photoIdentity: PhotoIdentity? = null,
        state: RecognitionFishPathSnapshotBindingState,
        subjectStatus: SubjectStatus,
        handoffFrozen: Boolean,
    ): Map<String, String> {
        val expectedPStar = photo?.contactPointNormalizedSource?.let { WaterLightPathPoint(it.x, it.y) }
        val expectedViewport = photo?.contactPointNormalizedSource?.let {
            photo.sourceToViewport.mapNormalized(it.x, it.y)
        }?.let { WaterLightPathPoint(it.x, it.y) }
        val pendingInputs = buildList {
            if (path == null) add("PATH")
            if (subject == null) add("SUBJECT_GEOMETRY")
            if (subjectStatus != SubjectStatus.READY) add("SUBJECT_READY")
            if (subject != null && (subject.maskSha256.isBlank() || subject.maskVersion < 0L)) {
                add("SUBJECT_MASK_IDENTITY")
            }
            if (photo == null) add("PROCESSING_PHOTO")
            if (photo != null && photo.contactPointMaskSha256.isBlank()) add("PHOTO_MASK_SHA256")
            if (photo != null && photo.contactPointMaskVersion < 0L) add("PHOTO_MASK_VERSION")
            if (photo != null && photo.contactPointNormalizedSource == null) add("PHOTO_PSTAR_SOURCE")
            if (photo != null && photo.contactPointGeometryVersion == 0L) add("PHOTO_GEOMETRY_VERSION")
            if (photo != null && hasMissingTransformInputs(photo.sourceToViewport)) add("PHOTO_TRANSFORM_DIMENSIONS")
            if (photo != null && hasMalformedTransform(photo.sourceToViewport)) add("PHOTO_TRANSFORM_INVALID")
        }.joinToString(",")
        fun identityDigest(session: String, attempt: String, image: String, sha: String, version: Long): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$session|$attempt|$image|$sha|$version".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
        return linkedMapOf(
            "state" to state.name,
            "expectedIdentityHash" to identityDigest(
                sessionId, attemptId, imageId,
                "$photoSha256|${photoIdentity?.sourceBytesSha256.orEmpty()}|${photoIdentity?.canonicalPixelSha256.orEmpty()}",
                photoContentVersion,
            ),
            "actualIdentityHash" to path?.let {
                identityDigest(
                    it.sessionId, it.attemptId, it.imageId,
                    "${it.photoSha256}|${it.photoIdentity?.sourceBytesSha256.orEmpty()}|${it.photoIdentity?.canonicalPixelSha256.orEmpty()}",
                    it.photoContentVersion,
                )
            }.orEmpty(),
            "expectedMaskSha256" to subject?.maskSha256?.take(16).orEmpty(),
            "actualMaskSha256" to path?.maskSha256?.take(16).orEmpty(),
            "expectedMaskVersion" to subject?.maskVersion?.toString().orEmpty(),
            "actualMaskVersion" to path?.maskVersion?.toString().orEmpty(),
            "processingGeometryVersion" to photo?.contactPointGeometryVersion?.toString().orEmpty(),
            "photoMaskSha256" to photo?.contactPointMaskSha256?.take(16).orEmpty(),
            "photoMaskVersion" to photo?.contactPointMaskVersion?.toString().orEmpty(),
            "photoPStarSource" to photo?.contactPointNormalizedSource?.let { "${it.x},${it.y}" }.orEmpty(),
            "pathGeometryVersion" to path?.geometryVersion?.toString().orEmpty(),
            "pStarSourceExpected" to expectedPStar?.let { "${it.x},${it.y}" }.orEmpty(),
            "pStarSourceActual" to path?.pStarNormalizedSource?.let { "${it.x},${it.y}" }.orEmpty(),
            "pStarViewportExpected" to expectedViewport?.let { "${it.x},${it.y}" }.orEmpty(),
            "fishPathEndpoint" to path?.fishPathGeometry?.end?.let { "${it.x},${it.y}" }.orEmpty(),
            "pathId" to path?.fishPathId.orEmpty(),
            "pendingInputs" to pendingInputs,
            "handoffFrozen" to handoffFrozen.toString(),
        )
    }

    fun hasMissingTransformInputs(transform: RecognitionImageTransform): Boolean =
        transform.sourceWidthPx == 0 || transform.sourceHeightPx == 0 ||
            transform.viewportWidthPx == 0f || transform.viewportHeightPx == 0f ||
            transform.scale == 0f || transform.drawnWidth == 0f || transform.drawnHeight == 0f ||
            (transform.visibleSourceRect.width == 0f && transform.visibleSourceRect.height == 0f)

    private fun hasMalformedTransform(transform: RecognitionImageTransform): Boolean {
        val visible = transform.visibleSourceRect
        return transform.sourceWidthPx < 0 || transform.sourceHeightPx < 0 ||
            !transform.viewportWidthPx.isFinite() || transform.viewportWidthPx < 0f ||
            !transform.viewportHeightPx.isFinite() || transform.viewportHeightPx < 0f ||
            !transform.scale.isFinite() || transform.scale < 0f ||
            !transform.drawnWidth.isFinite() || transform.drawnWidth < 0f ||
            !transform.drawnHeight.isFinite() || transform.drawnHeight < 0f ||
            !transform.translationX.isFinite() || !transform.translationY.isFinite() ||
            !visible.left.isFinite() || !visible.top.isFinite() ||
            !visible.right.isFinite() || !visible.bottom.isFinite() ||
            visible.left !in 0f..1f || visible.top !in 0f..1f ||
            visible.right !in 0f..1f || visible.bottom !in 0f..1f ||
            visible.right <= visible.left || visible.bottom <= visible.top
    }

    private fun WaterLightPathPoint.isWithinViewportToleranceOf(
        expected: WaterLightPathPoint?,
        tolerancePx: Float,
    ): Boolean {
        if (expected == null || !isValid() || !expected.isValid()) return false
        return kotlin.math.hypot((x - expected.x).toDouble(), (y - expected.y).toDouble()) <= tolerancePx
    }
}

/** Holds the active recognition payload in memory across Activity recreation. */
class RecognitionRevealSessionStore : ViewModel() {
    var snapshot by mutableStateOf<RecognitionRevealSessionSnapshot?>(null)
        private set

    private val playedKeys = RecognitionRevealPlaybackLedger()
    private val snapshotCommitter = RecognitionSnapshotCommitter()

    @Synchronized
    fun ensureSession(sessionId: String): RecognitionRevealSessionSnapshot {
        val current = snapshot?.takeIf { it.recognitionSessionId == sessionId }
        if (current != null) return current
        return RecognitionRevealSessionSnapshot(recognitionSessionId = sessionId).also { snapshot = it }
    }

    @Synchronized
    fun beginAttempt(
        sessionId: String,
        attemptId: String,
        image: SelectedImage,
        createdTimestampUptimeMs: Long = 0L,
    ) {
        snapshotCommitter.activateAttempt(attemptId)
        snapshot = RecognitionRevealSessionSnapshot(
            recognitionSessionId = sessionId,
            recognitionAttemptId = attemptId,
            image = image,
            attemptStartedUptimeMs = (createdTimestampUptimeMs.takeIf { it > 0L } ?: SystemClock.uptimeMillis()).coerceAtLeast(0L),
            resultHandoffState = RecognitionResultHandoffState(
                navigationTrace = listOf(
                    RecognitionResultNavigationTraceEntry(
                        status = RecognitionHandoffTransactionStatus.CREATED,
                        monotonicTimestampMs = createdTimestampUptimeMs.coerceAtLeast(0L),
                        source = "RecognitionRevealSessionStore.beginAttempt",
                        eventSequence = 0L,
                    ),
                ),
            ),
        )
    }

    @Synchronized
    fun updateSubject(
        key: RecognitionRevealKey,
        result: FishSubjectResult,
        geometry: RecognitionSubjectGeometrySnapshot?,
    ) {
        val current = snapshot ?: return
        if (!current.matches(key)) return
        if ((current.handoff != null || current.committedResultSnapshot != null) &&
            (current.subjectGeometry != geometry || current.subjectResult != result)
        ) return
        if (current.subjectResult == result && current.subjectGeometry == geometry) return
        var candidate = current.copy(subjectResult = result, subjectGeometry = geometry)
        candidate = acceptPendingFishPathIfReady(candidate)
        val updatedHandoff = rebuildHandoff(candidate)
        snapshot = candidate.copy(
            handoff = updatedHandoff,
            revealDecision = decisionFor(candidate),
        )
    }

    @Synchronized
    fun updateProcessingPhoto(key: RecognitionRevealKey, photo: RecognitionProcessingPhotoSnapshot) {
        val current = snapshot ?: return
        if (!current.matches(key) || current.image?.imageId != photo.imageId) return
        if ((current.handoff != null || current.committedResultSnapshot != null) && current.processingPhoto != photo) return
        if (current.processingPhoto == photo) return
        var candidate = current.copy(processingPhoto = photo)
        candidate = acceptPendingFishPathIfReady(candidate)
        snapshot = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
    }

    /** Save only the exact P* route exported by the active Processing PathLedger. */
    @Synchronized
    fun updateFishPathSnapshot(
        key: RecognitionRevealKey,
        pathSnapshot: RecognitionSnapshot,
    ): Boolean = receiveFishPathSnapshot(key, pathSnapshot, prepareOnly = false)

    /** Stage one endpoint without making a path visible to downstream consumers. */
    @Synchronized
    fun prepareFishPathSnapshot(
        key: RecognitionRevealKey,
        pathSnapshot: RecognitionSnapshot,
    ): Boolean = receiveFishPathSnapshot(key, pathSnapshot, prepareOnly = true)

    @Synchronized
    private fun receiveFishPathSnapshot(
        key: RecognitionRevealKey,
        pathSnapshot: RecognitionSnapshot,
        prepareOnly: Boolean,
    ): Boolean {
        val current = snapshot ?: return false
        val image = current.image ?: return false
        if (!current.matches(key)) return false

        val contentVersion = image.photoIdentity?.photoContentVersion ?: 0L
        val digestMismatch = pathSnapshot.snapshotSha256.isNotBlank() &&
            pathSnapshot.snapshotSha256 != pathSnapshot.calculateContentSha256()
        val rejection = when {
            pathSnapshot.sessionId != key.recognitionSessionId ||
                pathSnapshot.attemptId != key.recognitionAttemptId ||
                pathSnapshot.imageId != image.imageId || pathSnapshot.photoSha256 != image.photoSha256 ||
                pathSnapshot.photoContentVersion != contentVersion ||
                pathSnapshot.photoIdentity != image.photoIdentity ->
                RecognitionFishPathSnapshotBindingState.REJECTED_IDENTITY
            digestMismatch || !pathSnapshot.isValid() || pathSnapshot.geometryVersion <= 0L ->
                RecognitionFishPathSnapshotBindingState.REJECTED_GEOMETRY
            else -> null
        }
        if (rejection != null) {
            snapshot = current.withPathBindingState(
                state = rejection,
                path = pathSnapshot,
                reason = if (digestMismatch) "PATH_SNAPSHOT_SHA256_MISMATCH" else rejection.name,
                handoffFrozen = current.handoff != null,
            )
            return false
        }
        val existing = current.fishPathSnapshot
        if (existing == pathSnapshot) {
            snapshot = current.withPathBindingState(
                state = RecognitionFishPathSnapshotBindingState.BOUND,
                path = pathSnapshot,
                reason = null,
                handoffFrozen = current.handoff != null,
            )
            return true
        }
        if (existing != null || (current.pendingFishPathSnapshot != null && current.pendingFishPathSnapshot != pathSnapshot)) {
            snapshot = current.withPathBindingState(
                state = if (current.handoff != null) {
                    RecognitionFishPathSnapshotBindingState.REJECTED_FROZEN_HANDOFF
                } else RecognitionFishPathSnapshotBindingState.REJECTED_IDENTITY,
                path = pathSnapshot,
                reason = if (current.handoff != null) "HANDOFF_FROZEN" else "PATH_SNAPSHOT_ALREADY_RECEIVED",
                handoffFrozen = current.handoff != null,
            )
            return false
        }
        // A completed handoff freezes the decision and all path identity. A late callback
        // cannot replace an Orb-only terminal route or mutate a FULL_REVEAL transaction.
        if (current.handoff != null) {
            snapshot = current.withPathBindingState(
                state = RecognitionFishPathSnapshotBindingState.REJECTED_FROZEN_HANDOFF,
                path = pathSnapshot,
                reason = "HANDOFF_FROZEN",
                handoffFrozen = true,
            )
            return false
        }
        val staged = current.copy(
            pendingFishPathSnapshot = pathSnapshot,
            fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
            fishPathSnapshotPathId = pathSnapshot.fishPathId,
            fishPathSnapshotProducerSha256 = pathSnapshot.snapshotSha256.takeIf(String::isNotBlank),
            fishPathSnapshotContentSha256 = pathSnapshot.calculateContentSha256(),
            fishPathSnapshotReceivedAtUptimeMs = current.fishPathSnapshotReceivedAtUptimeMs
                ?: SystemClock.uptimeMillis(),
            fishPathSnapshotPendingReason = pendingBindingReason(
                current.subjectGeometry,
                current.processingPhoto,
                current.subjectResult.status,
            ),
            fishPathSnapshotBindingDiagnostic = RecognitionFishPathSnapshotBinding.diagnostic(
                path = pathSnapshot,
                subject = current.subjectGeometry,
                photo = current.processingPhoto,
                sessionId = current.recognitionSessionId,
                attemptId = current.recognitionAttemptId.orEmpty(),
                imageId = image.imageId,
                photoSha256 = image.photoSha256,
                photoContentVersion = contentVersion,
                photoIdentity = image.photoIdentity,
                state = RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                subjectStatus = current.subjectResult.status,
                handoffFrozen = false,
            ),
            pStarPathFailureReason = null,
        )
        val prepared = prepareOnly && RecognitionFishPathSnapshotBinding.evaluate(
            path = pathSnapshot,
            subjectStatus = staged.subjectResult.status,
            subject = staged.subjectGeometry,
            photo = staged.processingPhoto,
            sessionId = staged.recognitionSessionId,
            attemptId = staged.recognitionAttemptId.orEmpty(),
            imageId = image.imageId,
            photoSha256 = image.photoSha256,
            photoContentVersion = contentVersion,
            photoIdentity = image.photoIdentity,
        ) == RecognitionFishPathSnapshotBindOutcome.ACCEPTED
        val candidate = if (prepared) staged.copy(
            fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.PREPARED,
            fishPathSnapshotPendingReason = null,
            fishPathSnapshotBindingDiagnostic = RecognitionFishPathSnapshotBinding.diagnostic(
                path = pathSnapshot,
                subject = staged.subjectGeometry,
                photo = staged.processingPhoto,
                sessionId = staged.recognitionSessionId,
                attemptId = staged.recognitionAttemptId.orEmpty(),
                imageId = image.imageId,
                photoSha256 = image.photoSha256,
                photoContentVersion = contentVersion,
                photoIdentity = image.photoIdentity,
                state = RecognitionFishPathSnapshotBindingState.PREPARED,
                subjectStatus = staged.subjectResult.status,
                handoffFrozen = false,
            ),
        ) else if (prepareOnly) acceptPendingFishPathIfReady(staged) else acceptPendingFishPathIfReady(staged)
        snapshot = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
        return if (prepareOnly) {
            prepared && candidate.pendingFishPathSnapshot == pathSnapshot &&
                candidate.fishPathSnapshotBindingState == RecognitionFishPathSnapshotBindingState.PREPARED
        } else candidate.pendingFishPathSnapshot == pathSnapshot || candidate.fishPathSnapshot == pathSnapshot
    }

    /** Finalize the prepared path only when the shared coordinator presents its immutable receipt. */
    @Synchronized
    fun commitPreparedFishPathSnapshot(
        key: RecognitionRevealKey,
        receipt: RecognitionPathBindingCommitReceipt,
    ): Boolean {
        if (!receipt.isValid()) return false
        val current = snapshot ?: return false
        val path = current.pendingFishPathSnapshot ?: current.fishPathSnapshot ?: return false
        val image = current.image ?: return false
        val identity = receipt.identity
        if (!current.matches(key) || current.fishPathSnapshotBindingState !in setOf(
                RecognitionFishPathSnapshotBindingState.PREPARED,
                RecognitionFishPathSnapshotBindingState.BOUND,
            ) || identity.recognitionSessionId != key.recognitionSessionId ||
            identity.attemptId != key.recognitionAttemptId || identity.photoId != image.imageId ||
            identity.photoIdentityHash != image.photoSha256 ||
            identity.pathSnapshotContentHash != path.calculateContentSha256() ||
            identity.fishPathId != path.fishPathId || identity.maskSha256 != path.maskSha256 ||
            identity.maskVersion != path.maskVersion || identity.geometryVersion != path.geometryVersion
        ) return false
        if (current.fishPathBindingCommitReceiptId != null) {
            return current.fishPathBindingCommitReceiptId == receipt.receiptId && current.fishPathSnapshot == path
        }
        val promoted = acceptPendingFishPathIfReady(
            current.copy(fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING),
        )
        if (promoted.fishPathSnapshotBindingState != RecognitionFishPathSnapshotBindingState.BOUND ||
            promoted.fishPathSnapshot != path
        ) return false
        snapshot = promoted.copy(
            fishPathBindingCommitReceiptId = receipt.receiptId,
            handoff = rebuildHandoff(promoted),
            revealDecision = decisionFor(promoted),
        )
        return true
    }

    @Synchronized
    fun abortPreparedFishPathSnapshot(key: RecognitionRevealKey, receiptId: String? = null): Boolean {
        val current = snapshot ?: return false
        if (!current.matches(key) || current.fishPathSnapshotBindingState !in setOf(
                RecognitionFishPathSnapshotBindingState.PREPARED,
                RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                RecognitionFishPathSnapshotBindingState.BOUND,
            ) || (receiptId != null && current.fishPathBindingCommitReceiptId != null &&
                current.fishPathBindingCommitReceiptId != receiptId)
        ) return false
        val identity = current.image ?: return false
        snapshot = current.copy(
            fishPathSnapshot = null,
            pendingFishPathSnapshot = null,
            fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.NOT_RECEIVED,
            fishPathBindingCommitReceiptId = null,
            fishPathSnapshotPendingReason = null,
            fishPathSnapshotBindingDiagnostic = RecognitionFishPathSnapshotBinding.diagnostic(
                path = current.pendingFishPathSnapshot ?: current.fishPathSnapshot,
                subject = current.subjectGeometry,
                photo = current.processingPhoto,
                sessionId = current.recognitionSessionId,
                attemptId = current.recognitionAttemptId.orEmpty(),
                imageId = identity.imageId,
                photoSha256 = identity.photoSha256,
                photoContentVersion = identity.photoIdentity?.photoContentVersion ?: 0L,
                photoIdentity = identity.photoIdentity,
                state = RecognitionFishPathSnapshotBindingState.NOT_RECEIVED,
                subjectStatus = current.subjectResult.status,
                handoffFrozen = false,
            ),
        )
        return true
    }

    /** Retains the real Processing-ledger perimeter route before an Orb-only handoff freezes. */
    @Synchronized
    fun updateOrbOnlyReturnPathSnapshot(
        key: RecognitionRevealKey,
        route: RecognitionOrbOnlyReturnPathSnapshot,
    ): Boolean {
        val current = snapshot ?: return false
        val image = current.image ?: return false
        if (!current.matches(key) || !route.isValid() ||
            route.sessionId != key.recognitionSessionId || route.attemptId != key.recognitionAttemptId ||
            route.imageId != image.imageId || route.photoSha256 != image.photoSha256 ||
            route.photoContentVersion != (image.photoIdentity?.photoContentVersion ?: 0L) ||
            route.photoIdentity != image.photoIdentity
        ) return false
        if (current.orbOnlyReturnPathSnapshot == route) return true
        if (current.handoff != null) return false
        snapshot = current.copy(
            orbOnlyReturnPathSnapshot = route,
            revealDecision = decisionFor(current),
        )
        return true
    }

    /** Called with the monotonic Attempt clock; only the real 60s deadline may end P* waiting. */
    @Synchronized
    fun expirePendingPathBinding(key: RecognitionRevealKey, nowUptimeMs: Long): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        val elapsed = nowUptimeMs - current.attemptStartedUptimeMs
        if (elapsed < RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS || current.fishPathSnapshot != null) return false
        if (current.pStarPathTerminalUnavailable) return true
        val candidate = current.copy(
            pStarPathTerminalUnavailable = true,
            pStarPathFailureReason = "ATTEMPT_DEADLINE_EXCEEDED",
        )
        snapshot = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
        return true
    }

    private fun acceptPendingFishPathIfReady(
        candidate: RecognitionRevealSessionSnapshot,
    ): RecognitionRevealSessionSnapshot {
        if (candidate.fishPathSnapshotBindingState == RecognitionFishPathSnapshotBindingState.PREPARED) return candidate
        val path = candidate.pendingFishPathSnapshot ?: return candidate
        val image = candidate.image ?: return candidate
        val contentVersion = image.photoIdentity?.photoContentVersion ?: 0L
        fun diagnostic(state: RecognitionFishPathSnapshotBindingState, reason: String? = null) =
            RecognitionFishPathSnapshotBinding.diagnostic(
                path = path,
                subject = candidate.subjectGeometry,
                photo = candidate.processingPhoto,
                sessionId = candidate.recognitionSessionId,
                attemptId = candidate.recognitionAttemptId.orEmpty(),
                imageId = image.imageId,
                photoSha256 = image.photoSha256,
                photoContentVersion = contentVersion,
                photoIdentity = image.photoIdentity,
                state = state,
                subjectStatus = candidate.subjectResult.status,
                handoffFrozen = candidate.handoff != null,
            ) + mapOf(
                "producerSnapshotSha256" to path.snapshotSha256,
                "snapshotContentSha256" to path.calculateContentSha256(),
                "snapshotPathId" to path.fishPathId,
                "snapshotReceivedAtUptimeMs" to candidate.fishPathSnapshotReceivedAtUptimeMs.toString(),
                "pendingReason" to reason.orEmpty(),
            )
        return when (RecognitionFishPathSnapshotBinding.evaluate(
            path = path,
            subjectStatus = candidate.subjectResult.status,
            subject = candidate.subjectGeometry,
            photo = candidate.processingPhoto,
            sessionId = candidate.recognitionSessionId,
            attemptId = candidate.recognitionAttemptId.orEmpty(),
            imageId = image.imageId,
            photoSha256 = image.photoSha256,
            photoContentVersion = contentVersion,
            photoIdentity = image.photoIdentity,
        )) {
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS -> candidate.copy(
                fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                fishPathSnapshotPendingReason = pendingBindingReason(
                    candidate.subjectGeometry,
                    candidate.processingPhoto,
                    candidate.subjectResult.status,
                ),
                fishPathSnapshotBindingDiagnostic = diagnostic(
                    RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
                    pendingBindingReason(candidate.subjectGeometry, candidate.processingPhoto, candidate.subjectResult.status),
                ),
            )
            RecognitionFishPathSnapshotBindOutcome.ACCEPTED -> candidate.copy(
                fishPathSnapshot = path,
                pendingFishPathSnapshot = null,
                fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.BOUND,
                fishPathSnapshotPendingReason = null,
                fishPathSnapshotBindingDiagnostic = diagnostic(RecognitionFishPathSnapshotBindingState.BOUND),
                pStarPathFailureReason = null,
            )
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH -> candidate.copy(
                pendingFishPathSnapshot = null,
                fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.REJECTED_IDENTITY,
                fishPathSnapshotPendingReason = "PATH_IDENTITY_MISMATCH",
                fishPathSnapshotBindingDiagnostic = diagnostic(
                    RecognitionFishPathSnapshotBindingState.REJECTED_IDENTITY,
                    "PATH_IDENTITY_MISMATCH",
                ),
                pStarPathFailureReason = "PATH_IDENTITY_MISMATCH",
            )
            RecognitionFishPathSnapshotBindOutcome.MASK_MISMATCH -> candidate.copy(
                pendingFishPathSnapshot = null,
                fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.REJECTED_MASK,
                fishPathSnapshotPendingReason = "PATH_MASK_MISMATCH",
                fishPathSnapshotBindingDiagnostic = diagnostic(
                    RecognitionFishPathSnapshotBindingState.REJECTED_MASK,
                    "PATH_MASK_MISMATCH",
                ),
                pStarPathFailureReason = "PATH_MASK_MISMATCH",
            )
            RecognitionFishPathSnapshotBindOutcome.GEOMETRY_MISMATCH -> candidate.copy(
                pendingFishPathSnapshot = null,
                fishPathSnapshotBindingState = RecognitionFishPathSnapshotBindingState.REJECTED_GEOMETRY,
                fishPathSnapshotPendingReason = "PATH_GEOMETRY_MISMATCH",
                fishPathSnapshotBindingDiagnostic = diagnostic(
                    RecognitionFishPathSnapshotBindingState.REJECTED_GEOMETRY,
                    "PATH_GEOMETRY_MISMATCH",
                ),
                pStarPathFailureReason = "PATH_GEOMETRY_MISMATCH",
            )
        }
    }

    private fun RecognitionRevealSessionSnapshot.withPathBindingState(
        state: RecognitionFishPathSnapshotBindingState,
        path: RecognitionSnapshot,
        reason: String?,
        handoffFrozen: Boolean,
    ): RecognitionRevealSessionSnapshot {
        val activeImage = this.image
        val receivedAt = fishPathSnapshotReceivedAtUptimeMs ?: SystemClock.uptimeMillis()
        val pendingReason = reason ?: if (state == RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING) {
            pendingBindingReason(subjectGeometry, processingPhoto, subjectResult.status)
        } else null
        return copy(
            fishPathSnapshotBindingState = state,
            fishPathSnapshotPathId = path.fishPathId,
            fishPathSnapshotProducerSha256 = path.snapshotSha256.takeIf(String::isNotBlank),
            fishPathSnapshotContentSha256 = path.calculateContentSha256(),
            fishPathSnapshotReceivedAtUptimeMs = receivedAt,
            fishPathSnapshotPendingReason = pendingReason,
            fishPathSnapshotBindingDiagnostic = RecognitionFishPathSnapshotBinding.diagnostic(
                path = path,
                subject = subjectGeometry,
                photo = processingPhoto,
                sessionId = recognitionSessionId,
                attemptId = recognitionAttemptId.orEmpty(),
                imageId = activeImage?.imageId.orEmpty(),
                photoSha256 = activeImage?.photoSha256.orEmpty(),
                photoContentVersion = activeImage?.photoIdentity?.photoContentVersion ?: 0L,
                photoIdentity = activeImage?.photoIdentity,
                state = state,
                subjectStatus = subjectResult.status,
                handoffFrozen = handoffFrozen,
            ) + mapOf(
                "reason" to reason.orEmpty(),
                "producerSnapshotSha256" to path.snapshotSha256,
                "snapshotContentSha256" to path.calculateContentSha256(),
                "snapshotPathId" to path.fishPathId,
                "snapshotReceivedAtUptimeMs" to receivedAt.toString(),
                "pendingReason" to pendingReason.orEmpty(),
            ),
        )
    }

    private fun pendingBindingReason(
        subject: RecognitionSubjectGeometrySnapshot?,
        photo: RecognitionProcessingPhotoSnapshot?,
        subjectStatus: SubjectStatus,
    ): String = when {
        subject == null -> "SUBJECT_GEOMETRY_NOT_RECEIVED"
        subjectStatus != SubjectStatus.READY -> "SUBJECT_NOT_READY"
        subject.maskSha256.isBlank() || subject.maskVersion < 0L -> "SUBJECT_MASK_IDENTITY_NOT_READY"
        photo == null -> "PROCESSING_PHOTO_NOT_RECEIVED"
        photo.contactPointMaskSha256.isBlank() -> "PROCESSING_PHOTO_MASK_SHA256_NOT_READY"
        photo.contactPointMaskVersion < 0L -> "PROCESSING_PHOTO_MASK_VERSION_NOT_READY"
        photo.contactPointGeometryVersion == 0L -> "PROCESSING_PHOTO_GEOMETRY_VERSION_NOT_READY"
        photo.contactPointNormalizedSource == null -> "PROCESSING_PHOTO_PSTAR_NOT_READY"
        RecognitionFishPathSnapshotBinding.hasMissingTransformInputs(photo.sourceToViewport) ->
            "PROCESSING_PHOTO_TRANSFORM_NOT_READY"
        else -> "BINDING_INPUTS_RECEIVED_AWAITING_RETRY"
    }

    fun fishPathSnapshot(key: RecognitionRevealKey): RecognitionSnapshot? =
        snapshot?.takeIf { it.matches(key) }?.fishPathSnapshot

    /** Retain the real Classifier result immediately while the independent SlimSAM task continues. */
    @Synchronized
    fun recordClassifierResult(
        key: RecognitionRevealKey,
        result: ProductionRecognitionResult,
        inferenceAsset: InferenceAsset?,
        highConfidenceResult: Boolean,
    ): RecognitionRevealDecisionState? {
        val current = snapshot ?: return null
        val image = current.image ?: return null
        if (!current.matches(key) || image.imageId.isBlank() || image.photoSha256.isBlank()) return null
        if (current.handoff != null) {
            if (current.prediction?.top1?.speciesKey != result.prediction?.top1?.speciesKey ||
                current.highConfidenceResult != highConfidenceResult
            ) return null
            snapshot = current.copy(inferenceAsset = inferenceAsset ?: current.inferenceAsset)
            return current.revealDecision
        }
        val candidate = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            highConfidenceResult = highConfidenceResult,
        )
        val updated = candidate.copy(
            handoff = rebuildHandoff(candidate),
            revealDecision = decisionFor(candidate),
        )
        snapshot = updated
        return updated.revealDecision
    }

    fun revealDecisionFor(key: RecognitionRevealKey): RecognitionRevealDecisionState? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return null
        return current.handoff?.revealDecision ?: current.revealDecision
    }

    @Synchronized
    fun completeAttempt(
        key: RecognitionRevealKey,
        result: ProductionRecognitionResult,
        highConfidenceResult: Boolean,
        inferenceAsset: InferenceAsset?,
        handoffTimestampUptimeMs: Long,
    ): RecognitionRevealHandoff? {
        var current = snapshot ?: return null
        if (!current.matches(key)) return null
        if (highConfidenceResult && !current.pStarPathTerminalUnavailable && current.fishPathSnapshot == null &&
            handoffTimestampUptimeMs - current.attemptStartedUptimeMs >= RecognitionAttemptDeadline.DEFAULT_TIMEOUT_MS
        ) {
            expirePendingPathBinding(key, handoffTimestampUptimeMs)
            current = snapshot?.takeIf { it.matches(key) } ?: return null
        }
        val image = current.image ?: return null
        if (!result.ready) {
            val failedCandidate = current.copy(
                productionResult = result,
                prediction = result.prediction,
                inferenceAsset = inferenceAsset ?: current.inferenceAsset,
                highConfidenceResult = false,
                commitCandidateSnapshotId = current.commitCandidateSnapshotId
                    ?: "yj_snapshot_${java.util.UUID.randomUUID()}",
            )
            val frozenFailure = createAndCommitSnapshot(
                failedCandidate,
                result,
                handoff = null,
                outcome = FrozenRecognitionOutcome.CLASSIFICATION_FAILED,
                snapshotId = requireNotNull(failedCandidate.commitCandidateSnapshotId),
            )
            snapshot = failedCandidate.copy(committedResultSnapshot = frozenFailure)
            return null
        }
        val existingHandoff = current.handoff?.takeIf {
            it.key == key && it.imageId == image.imageId &&
                it.resultIdentity == result.prediction?.top1?.speciesKey &&
                RecognitionRevealDecisionGate.isNavigable(it.revealDecision ?: RecognitionRevealDecisionState.WAITING_BOTH) &&
                (it.revealDecision != RecognitionRevealDecisionState.FISH_FOCUS_READY || current.fishPathSnapshot != null) &&
                (it.revealDecision != RecognitionRevealDecisionState.ORB_ONLY_READY ||
                    it.orbOnlyReturnPathSnapshot?.isValid() == true)
        }
        if (existingHandoff != null) {
            if (current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.CREATED ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.HANDOFF_REJECTED ||
                current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.NAVIGATION_FAILED
            ) {
                val state = current.resultHandoffState
                val trace = if (state.transactionStatus == RecognitionHandoffTransactionStatus.CREATED) {
                    state.navigationTrace + transition(
                        RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                        handoffTimestampUptimeMs,
                        "RecognitionRevealSessionStore.completeAttempt",
                        state.lastEventSequence,
                    )
                } else state.navigationTrace
                snapshot = current.copy(
                    resultHandoffState = state.copy(
                    transactionStatus = if (existingHandoff.eligibility == RecognitionRevealEligibility.STATIC_RESULT) {
                        RecognitionHandoffTransactionStatus.NON_ANIMATED_RESULT_READY
                    } else RecognitionHandoffTransactionStatus.HANDOFF_READY,
                        navigationTrace = trace + transition(
                            if (existingHandoff.eligibility == RecognitionRevealEligibility.STATIC_RESULT) {
                                RecognitionHandoffTransactionStatus.NON_ANIMATED_RESULT_READY
                            } else RecognitionHandoffTransactionStatus.HANDOFF_READY,
                            handoffTimestampUptimeMs,
                            "RecognitionRevealSessionStore.completeAttempt",
                            state.lastEventSequence,
                        ),
                    ),
                )
            }
            return existingHandoff
        }
        val processingTrace = current.resultHandoffState.navigationTrace + transition(
            RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
            handoffTimestampUptimeMs,
            "RecognitionRevealSessionStore.completeAttempt",
            current.resultHandoffState.lastEventSequence,
        )
        val candidate = current.copy(
            productionResult = result,
            prediction = result.prediction,
            inferenceAsset = inferenceAsset,
            highConfidenceResult = highConfidenceResult,
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                navigationTrace = processingTrace,
            ),
        )
        val decision = current.revealDecision
        if (!RecognitionRevealDecisionGate.isNavigable(decision)) {
            // Keep the immutable Classifier result and decision. Subject/Photo/Path callbacks
            // can still complete this join; a missing asynchronous callback is not terminal.
            snapshot = candidate.copy(
                resultHandoffState = candidate.resultHandoffState.copy(
                    transactionStatus = RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                    navigationTrace = processingTrace,
                ),
            )
            return null
        }
        if (decision == RecognitionRevealDecisionState.FISH_FOCUS_READY && current.fishPathSnapshot == null) {
            snapshot = candidate.copy(resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ))
            return null
        }
        val orbOnlyPath = current.orbOnlyReturnPathSnapshot?.takeIf { route ->
            route.isValid() && route.sessionId == key.recognitionSessionId &&
                route.attemptId == key.recognitionAttemptId && route.imageId == image.imageId &&
                route.photoSha256 == image.photoSha256 &&
                route.photoContentVersion == (image.photoIdentity?.photoContentVersion ?: 0L) &&
                route.photoIdentity == image.photoIdentity
        }
        if (decision == RecognitionRevealDecisionState.ORB_ONLY_READY && orbOnlyPath == null) {
            snapshot = candidate.copy(
                resultHandoffState = candidate.resultHandoffState.copy(
                    transactionStatus = RecognitionHandoffTransactionStatus.PROCESSING_COMPLETE,
                ),
            )
            return null
        }
        val snapshotId = candidate.commitCandidateSnapshotId ?: "yj_snapshot_${java.util.UUID.randomUUID()}"
        val handoff = RecognitionRevealHandoffFactory.create(
            recognitionSessionId = key.recognitionSessionId,
            recognitionAttemptId = key.recognitionAttemptId,
            image = image,
            result = result,
            subject = current.subjectGeometry,
            processingPhoto = current.processingPhoto,
            highConfidenceResult = highConfidenceResult,
            handoffTimestampUptimeMs = handoffTimestampUptimeMs,
            subjectStatus = current.subjectResult.status,
            fishPathSnapshot = current.fishPathSnapshot,
            orbOnlyReturnPathSnapshot = orbOnlyPath,
            pStarPathTerminalUnavailable = current.pStarPathState == RecognitionPStarPathState.PSTAR_TERMINAL_UNAVAILABLE,
            frozenRevealDecision = decision,
            recognitionSnapshotId = snapshotId,
        )
        if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL && handoff.fishPathSnapshot == null) {
            snapshot = candidate.copy(resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ))
            return null
        }
        val readyStatus = if (handoff.eligibility == RecognitionRevealEligibility.STATIC_RESULT) {
            RecognitionHandoffTransactionStatus.NON_ANIMATED_RESULT_READY
        } else RecognitionHandoffTransactionStatus.HANDOFF_READY
        val frozen = createAndCommitSnapshot(
            candidate.copy(commitCandidateSnapshotId = snapshotId),
            result,
            handoff,
            if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL) {
                FrozenRecognitionOutcome.FULL_REVEAL
            } else FrozenRecognitionOutcome.ORB_ONLY,
            snapshotId,
        )
        if (frozen == null) {
            snapshot = candidate.copy(resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                navigationTrace = processingTrace + transition(
                    RecognitionHandoffTransactionStatus.HANDOFF_REJECTED,
                    handoffTimestampUptimeMs,
                    "RecognitionSnapshotCommitter.commitOnce",
                    current.resultHandoffState.lastEventSequence,
                ),
            ))
            return null
        }
        snapshot = candidate.copy(
            handoff = handoff,
            revealDecision = handoff.revealDecision ?: decision,
            handoffFreezeAtUptimeMs = handoffTimestampUptimeMs,
            handoffFreezePathBound = current.fishPathSnapshot != null,
            committedResultSnapshot = frozen,
            commitCandidateSnapshotId = snapshotId,
            resultHandoffState = candidate.resultHandoffState.copy(
                transactionStatus = readyStatus,
                navigationTrace = processingTrace + transition(
                    readyStatus,
                    handoffTimestampUptimeMs,
                    "RecognitionRevealSessionStore.completeAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return handoff
    }

    private fun createAndCommitSnapshot(
        source: RecognitionRevealSessionSnapshot,
        result: ProductionRecognitionResult,
        handoff: RecognitionRevealHandoff?,
        outcome: FrozenRecognitionOutcome,
        snapshotId: String,
    ): FrozenRecognitionResultSnapshot? {
        val image = source.image ?: return null
        val photoIdentity = image.photoIdentity?.takeIf(PhotoIdentity::isValid) ?: return null
        if (handoff != null && handoff.recognitionSnapshotId != snapshotId) return null
        val prediction = result.prediction
        val contour = source.subjectGeometry?.contour.orEmpty()
        val contourSha = contour.takeIf { it.isNotEmpty() }?.let { segments ->
            val canonical = segments.joinToString("|") { segment ->
                listOf(
                    segment.startX, segment.startY, segment.endX, segment.endY,
                    segment.control1X, segment.control1Y, segment.control2X, segment.control2Y,
                    segment.beginsContour, segment.closesContour,
                ).joinToString(",")
            }
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
        val trustedMask = handoff?.geometryTrustState == RecognitionGeometryTrustState.TRUSTED_MASK &&
            source.subjectResult.maskSha256.isSha256()
        val path = handoff?.fishPathSnapshot?.takeIf { outcome == FrozenRecognitionOutcome.FULL_REVEAL }
        val sourcePStar = handoff?.subjectAnchorNormalizedSource
        val viewportPStar = handoff?.processingSubjectAnchor
        val pStar = if (outcome == FrozenRecognitionOutcome.FULL_REVEAL && sourcePStar != null &&
            viewportPStar != null && handoff.contactPointGeometryVersion > 0L
        ) FrozenPStarGeometry(
            sourceX = sourcePStar.x,
            sourceY = sourcePStar.y,
            viewportX = viewportPStar.x,
            viewportY = viewportPStar.y,
            geometryVersion = handoff.contactPointGeometryVersion,
        ) else null
        val pathRef = path?.let { snapshot ->
            FrozenFishPathSnapshotRef(
                pathId = snapshot.fishPathId,
                contentSha256 = snapshot.snapshotSha256.ifBlank { snapshot.calculateContentSha256() },
                geometryVersion = snapshot.geometryVersion,
                photoIdentity = snapshot.photoIdentity ?: return null,
            )
        }
        val detectorRun = result.detectorRun
        val detectorSha = detectorRun.onnxSha256.takeIf(String::isSha256) ?: return null
        val classifierSha = prediction?.modelSha256?.takeIf(String::isSha256)
            ?: FishRecognitionEngine.FROZEN_CLASSIFIER_SHA256
        val geometryModels = source.subjectResult.geometry?.modelSha256.orEmpty()
        val encoderSha = Regex("vision:([a-fA-F0-9]{64})").find(geometryModels)?.groupValues?.get(1)
            ?: com.yujian.ai.ai.subject.SlimSamSegmenter.VISION_ENCODER_SHA256
        val decoderSha = Regex("decoder:([a-fA-F0-9]{64})").find(geometryModels)?.groupValues?.get(1)
            ?: com.yujian.ai.ai.subject.SlimSamSegmenter.DECODER_MODEL_SHA256
        val candidate = FrozenRecognitionResultSnapshot.create(
            snapshotId = snapshotId,
            recognitionSessionId = source.recognitionSessionId,
            attemptId = source.recognitionAttemptId ?: return null,
            photoIdentity = photoIdentity,
            modelBundleIdentity = FrozenModelBundleIdentity(
                detector = FrozenModelIdentity("DET_FISH", detectorRun.modelVersion, detectorSha),
                classifier = FrozenModelIdentity(
                    FishRecognitionEngine.MODEL_FILE,
                    prediction?.modelVersion ?: FishRecognitionEngine.PRODUCTION_RELEASE_TAG,
                    classifierSha,
                ),
                visionEncoder = FrozenModelIdentity(
                    com.yujian.ai.ai.subject.SlimSamSegmenter.VISION_ENCODER_MODEL,
                    com.yujian.ai.ai.subject.SlimSamSegmenter.MODEL_VERSION,
                    encoderSha,
                ),
                maskDecoder = FrozenModelIdentity(
                    com.yujian.ai.ai.subject.SlimSamSegmenter.MASK_DECODER_MODEL,
                    com.yujian.ai.ai.subject.SlimSamSegmenter.MODEL_VERSION,
                    decoderSha,
                ),
            ),
            detectorResult = FrozenDetectorResult(
                terminalStatus = result.assessment.status.name,
                detections = detectorRun.detections.map { detection ->
                    val box = detection.box.normalized()
                    FrozenDetectorDetection(box.x1, box.y1, box.x2, box.y2, detection.confidence)
                },
            ),
            classifierResult = FrozenClassifierResult(
                terminalStatus = if (prediction == null) result.failureCode?.name ?: "FAILED" else "READY",
                modelRunId = "classifier:${source.recognitionAttemptId}",
                species = prediction?.top1?.let {
                    FrozenSpeciesResult(it.speciesKey, it.speciesName, it.confidence)
                },
                candidates = prediction?.candidates.orEmpty().map {
                    FrozenSpeciesResult(it.speciesKey, it.speciesName, it.confidence)
                },
            ),
            maskOutcome = FrozenMaskOutcome(
                terminalStatus = if (trustedMask) "TRUSTED" else source.subjectResult.status.name,
                trusted = trustedMask,
                maskSha256 = source.subjectResult.maskSha256.takeIf(String::isSha256),
                failureCode = source.subjectResult.errorCode,
            ),
            contourOutcome = FrozenContourOutcome(
                terminalStatus = if (contour.lastOrNull()?.closesContour == true && trustedMask) "CLOSED" else "UNAVAILABLE",
                closed = trustedMask && contour.lastOrNull()?.closesContour == true,
                geometrySha256 = contourSha,
            ),
            pStarGeometry = pStar,
            fishPathSnapshotRef = pathRef,
            recognitionOutcome = outcome,
            committedAtMonotonicNanos = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L),
        )
        val commit = snapshotCommitter.commitOnce(candidate.attemptId, candidate)
        return when (commit.status) {
            SnapshotCommitStatus.COMMITTED,
            SnapshotCommitStatus.ALREADY_COMMITTED -> commit.snapshot
            SnapshotCommitStatus.CONFLICT,
            SnapshotCommitStatus.STALE_ATTEMPT,
            SnapshotCommitStatus.INVALID -> null
        }
    }

    /** Idempotent Processing → navigation transaction. Rejected handoffs remain retryable. */
    fun resultNavigationRejectionReason(key: RecognitionRevealKey): String? {
        val current = snapshot ?: return "NO_SNAPSHOT"
        if (!current.matches(key)) return "ATTEMPT_IDENTITY_MISMATCH"
        if (current.handoff?.key != key) return "HANDOFF_NOT_READY"
        return when (current.resultHandoffState.transactionStatus) {
            RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED -> "NAVIGATION_ALREADY_REQUESTED"
            RecognitionHandoffTransactionStatus.RESULT_ATTACHED -> "RESULT_ALREADY_ATTACHED"
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED -> "HANDOFF_ALREADY_COMPLETED"
            else -> null
        }
    }

    @Synchronized
    fun requestResultNavigation(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val rejection = resultNavigationRejectionReason(key)
        if (rejection != null) {
            android.util.Log.w("RecognitionRevealHandoff", "RESULT_HANDOFF_REJECTED attempt=${key.recognitionAttemptId} reason=$rejection")
            return false
        }
        val current = requireNotNull(snapshot)
        snapshot = current.copy(
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                navigationTrace = current.resultHandoffState.navigationTrace + transition(
                    RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                    monotonicTimestampMs,
                    "YujianApp.deliverRecognitionAttempt",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return true
    }

    fun markNavigationFailed(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        if (current.resultHandoffState.transactionStatus !in setOf(
                RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
                RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
            )
        ) return false
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            transactionStatus = RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
            navigationTrace = current.resultHandoffState.navigationTrace + transition(
                RecognitionHandoffTransactionStatus.NAVIGATION_FAILED,
                monotonicTimestampMs,
                "YujianApp.deliverRecognitionAttempt",
                current.resultHandoffState.lastEventSequence,
            ),
        ))
        return true
    }

    fun markResultAttached(
        key: RecognitionRevealKey,
        monotonicTimestampMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val current = snapshot?.takeIf { it.matches(key) } ?: return false
        if (current.handoff?.key != key || current.image?.imageId != current.handoff.imageId) return false
        if (current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.RESULT_ATTACHED ||
            current.resultHandoffState.transactionStatus == RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED
        ) return true
        if (current.resultHandoffState.transactionStatus != RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED) return false
        snapshot = current.copy(
            resultHandoffState = current.resultHandoffState.copy(
                transactionStatus = RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                navigationTrace = current.resultHandoffState.navigationTrace + transition(
                    RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                    monotonicTimestampMs,
                    "YujianApp.resultDestination",
                    current.resultHandoffState.lastEventSequence,
                ),
            ),
        )
        return true
    }

    fun navigationAccepted(key: RecognitionRevealKey): Boolean {
        val status = snapshot?.takeIf { it.matches(key) }?.resultHandoffState?.transactionStatus ?: return false
        return status in setOf(
            RecognitionHandoffTransactionStatus.NAVIGATION_REQUESTED,
            RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
        )
    }

    fun canAcceptResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent): String? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return "STALE_ATTEMPT"
        val image = current.image ?: return "IMAGE_MISSING"
        val handoff = current.handoff ?: return "HANDOFF_MISSING"
        val prediction = current.prediction ?: return "CLASSIFIER_RESULT_MISSING"
        if (event.identity.sessionId != key.recognitionSessionId || event.identity.attemptId != key.recognitionAttemptId ||
            event.identity.imageId != image.imageId || event.identity.photoSha256 != image.photoSha256 ||
            event.identity.photoContentVersion != (image.photoIdentity?.photoContentVersion ?: 0L) ||
            event.identity.photoIdentity != image.photoIdentity
        ) return "IDENTITY_MISMATCH"
        if (handoff.imageId != image.imageId || event.resultIdentity != prediction.top1.speciesKey) return "RESULT_IDENTITY_MISMATCH"
        if (current.resultHandoffState.transactionStatus !in setOf(
                RecognitionHandoffTransactionStatus.RESULT_ATTACHED,
                RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
            )
        ) return "RESULT_ROUTE_NOT_ATTACHED"
        if (!RecognitionResultHandoffContract.isNextEventSequence(
                event.eventSequence,
                current.resultHandoffState.lastEventSequence,
            )
        ) return "EVENT_SEQUENCE_NOT_CONTIGUOUS"
        if (event.geometryVersion == 0L) return "RESULT_GEOMETRY_INVALID"
        if (!RecognitionResultHandoffContract.hasStrictlyLaterTimestamp(
                event.monotonicTimestampMs,
                current.resultHandoffState.lastEventTimestampMs,
            ) ||
            event.eventProducer != RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER ||
            event.eventConsumer != RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER ||
            event.renderStatus != "READY" || !event.progress.isFinite() || event.progress < .999f ||
            event.stage.ordinal < com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.ORB_RETURN.ordinal
        ) return "EVENT_NOT_COMPLETE"
        if (!RecognitionResultHandoffContract.isNext(event.substage, current.resultHandoffState.completedSubstages)) {
            return "RESULT_SUBSTAGE_OUT_OF_ORDER"
        }
        val glyphReadiness = event.glyphReadiness ?: return "GLYPH_READINESS_MISSING"
        val expectedGlyphName = resultSpeciesDisplayName(prediction.top1.speciesName)
        if (event.substage == ResultHandoffSubstage.NAME_STABLE) {
            if (!RecognitionGlyphReadinessContract.isNameStableClaim(
                    event.identity, prediction.top1.speciesKey, expectedGlyphName, glyphReadiness,
                )
            ) return "GLYPH_NOT_ACTUALLY_RENDERED"
        } else {
            val acceptedGlyph = current.resultHandoffState.glyphReadiness
            if (!acceptedGlyph.nameStable || glyphReadiness != acceptedGlyph) {
                return "GLYPH_PATH_IDENTITY_CHANGED"
            }
        }
        if (event.substage == ResultHandoffSubstage.NAME_STABLE) {
            if (event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.SPECIES_NAME_REVEAL) {
                return "NAME_STABILITY_STAGE_MISMATCH"
            }
        } else if (event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.RESIDUE_SETTLE) {
            return "RESULT_HANDOFF_STAGE_MISMATCH"
        }
        if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL) {
            val path = current.fishPathSnapshot ?: return "PROCESSING_PATH_SNAPSHOT_MISSING"
            if (event.maskSha256 != path.maskSha256 || event.maskVersion != path.maskVersion ||
                event.pathId != path.returnPathId || event.pathGeometryVersion != path.geometryVersion ||
                event.pStarNormalizedSource != path.pStarNormalizedSource || event.pathSegments != path.returnPathSegments
            ) {
                return "PROCESSING_PATH_IDENTITY_MISMATCH"
            }
        }
        if (event.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE) {
            val required = setOf(
                ResultHandoffSubstage.NAME_STABLE,
                ResultHandoffSubstage.PHOTO_SETTLE,
                ResultHandoffSubstage.RESULT_SCENE_HANDOFF,
                ResultHandoffSubstage.RESULT_INFO_ARRIVAL,
            )
            if (!current.resultHandoffState.completedSubstages.containsAll(required) ||
                event.stage != com.yujian.ai.ui.waterlight.core.RecognitionSceneStage.RESIDUE_SETTLE
            ) return "RESULT_STABLE_PREREQUISITES_MISSING"
            if (!RecognitionResultStableRenderContract.isComplete(
                    RecognitionSceneIdentity(
                        key.recognitionSessionId,
                        key.recognitionAttemptId,
                        image.imageId,
                        image.photoSha256,
                        image.photoIdentity?.photoContentVersion ?: 0L,
                        image.photoIdentity,
                    ),
                    event.resultIdentity,
                    event.geometryVersion,
                    event.resultStableRenderReceipt,
                )
            ) return "RESULT_STABLE_RENDER_RECEIPT_INVALID"
            if (current.resultHandoffState.playbackStatus != RevealPlaybackStatus.IN_PROGRESS) return "PLAYBACK_NOT_IN_PROGRESS"
        }
        if (event.substage == ResultHandoffSubstage.RESULT_INFO_ARRIVAL &&
            !RecognitionResultInfoRenderContract.isComplete(
                RecognitionSceneIdentity(
                    key.recognitionSessionId,
                    key.recognitionAttemptId,
                    image.imageId,
                    image.photoSha256,
                    image.photoIdentity?.photoContentVersion ?: 0L,
                    image.photoIdentity,
                ),
                event.resultIdentity,
                event.geometryVersion,
                event.resultInfoRenderReceipt,
            )
        ) return "RESULT_INFO_RENDER_RECEIPT_INVALID"
        return null
    }

    fun recordResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent): RecognitionResultHandoffReceipt {
        val rejection = canAcceptResultHandoffEvent(key, event)
        if (rejection != null) return RecognitionResultHandoffReceipt(false, rejection, snapshot?.resultHandoffState?.resultSceneStable == true)
        val current = snapshot ?: return RecognitionResultHandoffReceipt(false, "SESSION_MISSING")
        val nextSubstages = current.resultHandoffState.completedSubstages + event.substage
        val stable = event.substage == ResultHandoffSubstage.RESULT_SCENE_STABLE
        val transaction = if (stable) RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED
            else RecognitionHandoffTransactionStatus.RESULT_ATTACHED
        val navigationTrace = if (stable) current.resultHandoffState.navigationTrace + transition(
            RecognitionHandoffTransactionStatus.HANDOFF_COMPLETED,
            event.monotonicTimestampMs,
            "RecognitionRevealSessionStore.recordResultHandoffEvent",
            event.eventSequence,
        ) else current.resultHandoffState.navigationTrace
        val nextState = current.resultHandoffState.copy(
            transactionStatus = transaction,
            lastEventSequence = event.eventSequence,
            lastEventTimestampMs = event.monotonicTimestampMs,
            acceptedEvents = current.resultHandoffState.acceptedEvents + event,
            eventTrace = current.resultHandoffState.eventTrace + RecognitionResultHandoffTraceEntry(event, accepted = true),
            navigationTrace = navigationTrace,
            completedSubstages = nextSubstages,
            resultSceneStable = stable,
            glyphReadiness = event.glyphReadiness?.copy(nameStable = true) ?: current.resultHandoffState.glyphReadiness,
            resultStableTimestampMs = event.monotonicTimestampMs.takeIf { stable },
            playbackStatus = if (stable) RevealPlaybackStatus.COMPLETED else current.resultHandoffState.playbackStatus,
            animationGeometryReleased = stable && current.resultHandoffState.animationGeometryReleased,
        )
        snapshot = current.copy(resultHandoffState = nextState)
        if (stable) playedKeys.complete(key, resultSceneStable = true)
        return RecognitionResultHandoffReceipt(true, resultSceneStable = stable)
    }

    /** Store endpoint for the explicit Consumer-ACK to Result-owner transfer. */
    @Synchronized
    fun transferSceneOwner(key: RecognitionRevealKey, receipt: RecognitionSceneOwnerTransferReceipt): Boolean {
        if (!receipt.isValid()) return false
        val current = snapshot ?: return false
        val image = current.image ?: return false
        val result = current.committedResultSnapshot ?: return false
        if (!current.matches(key) || current.recognitionSessionId != receipt.recognitionSessionId ||
            current.recognitionAttemptId != receipt.attemptId || image.imageId != receipt.photoId ||
            image.photoIdentity?.canonicalPixelSha256 != receipt.photoIdentityHash ||
            result.snapshotId != receipt.snapshotId || result.contentHash != receipt.snapshotContentHash ||
            !current.resultHandoffState.has(ResultHandoffSubstage.RESULT_SCENE_HANDOFF)
        ) return false
        val existing = current.sceneOwnerTransferReceiptId
        if (existing != null) return existing == receipt.receiptId && current.sceneOwner == RecognitionSceneOwner.RESULT
        snapshot = current.copy(
            sceneOwner = RecognitionSceneOwner.RESULT,
            sceneOwnerTransferReceiptId = receipt.receiptId,
        )
        return true
    }

    @Synchronized
    fun abortSceneOwnerTransfer(key: RecognitionRevealKey, receipt: RecognitionSceneOwnerTransferReceipt): Boolean {
        val current = snapshot ?: return false
        if (!current.matches(key) || current.sceneOwnerTransferReceiptId != receipt.receiptId ||
            current.sceneOwner != RecognitionSceneOwner.RESULT
        ) return false
        snapshot = current.copy(
            sceneOwner = RecognitionSceneOwner.RECOGNITION,
            sceneOwnerTransferReceiptId = null,
        )
        return true
    }

    fun recordRejectedResultHandoffEvent(key: RecognitionRevealKey, event: RecognitionResultHandoffEvent, reason: String) {
        val current = snapshot?.takeIf { it.matches(key) } ?: return
        if (event.identity.sessionId != key.recognitionSessionId || event.identity.attemptId != key.recognitionAttemptId) return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            eventTrace = current.resultHandoffState.eventTrace + RecognitionResultHandoffTraceEntry(
                event = event,
                accepted = false,
                rejectionReason = reason,
            ),
        ))
    }

    fun resultHandoffTraceJson(key: RecognitionRevealKey): String? {
        val state = snapshot?.takeIf { it.matches(key) }?.resultHandoffState ?: return null
        val entries = org.json.JSONArray()
        state.eventTrace.forEach { trace ->
            val event = trace.event
            val identity = event.identity
            val row = org.json.JSONObject()
                .put("sessionId", identity.sessionId)
                .put("attemptId", identity.attemptId)
                .put("imageId", identity.imageId)
                .put("photoSha", identity.photoSha256)
                .put("photoContentVersion", identity.photoContentVersion)
                .put("maskSha", event.maskSha256 ?: org.json.JSONObject.NULL)
                .put("maskVersion", event.maskVersion ?: org.json.JSONObject.NULL)
                .put("geometryVersion", event.geometryVersion)
                .put("pathId", event.pathId ?: org.json.JSONObject.NULL)
                .put("pathGeometryVersion", event.pathGeometryVersion ?: org.json.JSONObject.NULL)
                .put("pStarNormalizedSource", event.pStarNormalizedSource?.let {
                    org.json.JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())
                } ?: org.json.JSONObject.NULL)
                .put("pathSegments", org.json.JSONArray().apply {
                    event.pathSegments.forEach { segment -> put(org.json.JSONObject()
                        .put("start", org.json.JSONObject().put("x", segment.start.x.toDouble()).put("y", segment.start.y.toDouble()))
                        .put("control1", org.json.JSONObject().put("x", segment.control1.x.toDouble()).put("y", segment.control1.y.toDouble()))
                        .put("control2", org.json.JSONObject().put("x", segment.control2.x.toDouble()).put("y", segment.control2.y.toDouble()))
                        .put("end", org.json.JSONObject().put("x", segment.end.x.toDouble()).put("y", segment.end.y.toDouble()))
                        .put("coordinateSpace", segment.coordinateSpace)) }
                })
                .put("stage", event.stage.name)
                .put("substage", event.substage.name)
                .put("contractId", event.substage.contractId)
                .put("eventSequence", event.eventSequence)
                .put("timestamp", event.monotonicTimestampMs)
                .put("eventProducer", event.eventProducer)
                .put("eventConsumer", event.eventConsumer)
                .put("source", event.source)
                .put("target", event.target)
                .put("feedback", event.feedback)
                .put("progress", event.progress.toDouble())
                .put("renderStatus", event.renderStatus)
                .put("resultIdentity", event.resultIdentity)
                .put("glyphSpeciesKey", event.glyphReadiness?.descriptor?.speciesKey ?: org.json.JSONObject.NULL)
                .put("glyphSpeciesName", event.glyphReadiness?.descriptor?.speciesName ?: org.json.JSONObject.NULL)
                .put("glyphPathId", event.glyphReadiness?.descriptor?.pathId ?: org.json.JSONObject.NULL)
                .put("glyphPathGeometryVersion", event.glyphReadiness?.descriptor?.geometryVersion ?: org.json.JSONObject.NULL)
                .put("glyphPathReady", event.glyphReadiness?.glyphPathReady == true)
                .put("glyphRevealRendered", event.glyphReadiness?.glyphRevealRendered == true)
                .put("glyphNameStable", event.glyphReadiness?.nameStable == true)
                .put("glyphDrawProgress", event.glyphReadiness?.renderReceipt?.renderedProgress?.toDouble() ?: 0.0)
                .put("glyphDrawPassSequence", event.glyphReadiness?.renderReceipt?.drawPassSequence ?: 0L)
                .put("accepted", trace.accepted)
                .put("rejectionReason", trace.rejectionReason ?: org.json.JSONObject.NULL)
            entries.put(row)
        }
        return org.json.JSONObject()
            .put("sessionId", key.recognitionSessionId)
            .put("attemptId", key.recognitionAttemptId)
            .put("transactionStatus", state.transactionStatus.name)
            .put("playbackStatus", state.playbackStatus.name)
            .put("lastEventSequence", state.lastEventSequence)
            .put("lastEventTimestampMs", state.lastEventTimestampMs)
            .put("resultStableTimestampMs", state.resultStableTimestampMs ?: org.json.JSONObject.NULL)
            .put("geometryReleasedTimestampMs", state.geometryReleasedTimestampMs ?: org.json.JSONObject.NULL)
            .put("navigation", org.json.JSONArray().apply {
                state.navigationTrace.forEach { item -> put(org.json.JSONObject()
                    .put("status", item.status.name)
                    .put("monotonicTimestampMs", item.monotonicTimestampMs)
                    .put("source", item.source)
                    .put("eventSequence", item.eventSequence)) }
            })
            .put("events", entries)
            .put("resultSceneStable", state.resultSceneStable)
            .toString()
    }

    fun resultSnapshot(key: RecognitionRevealKey): RecognitionResultSnapshot? {
        val current = snapshot?.takeIf { it.matches(key) } ?: return null
        val image = current.image ?: return null
        val committedPayload = current.committedResultSnapshot ?: return null
        val result = current.productionResult?.takeIf { it.ready } ?: return null
        val prediction = current.prediction ?: return null
        val handoff = current.handoff?.takeIf { it.key == key } ?: return null
        if (prediction.top1.speciesKey != handoff.resultIdentity || prediction.top1.speciesName.isBlank() ||
            handoff.captureTimestampEpochMs != image.captureTimestampEpochMs ||
            handoff.captureTimestampSource != image.captureTimestampSource
        ) return null
        return RecognitionResultSnapshot(
            key = key,
            image = image,
            productionResult = result,
            prediction = prediction,
            subjectResult = current.subjectResult,
            inferenceAsset = current.inferenceAsset,
            subjectGeometry = current.subjectGeometry,
            handoff = handoff,
            fishPathSnapshot = current.fishPathSnapshot,
            captureTimestampEpochMs = image.captureTimestampEpochMs,
            captureTimestampSource = image.captureTimestampSource,
            glyphSpeciesKey = prediction.top1.speciesKey,
            glyphSpeciesName = prediction.top1.speciesName,
            revealEligibility = handoff.eligibility,
            handoffState = current.resultHandoffState,
            resultDataReady = true,
            resultSceneStable = current.resultHandoffState.resultSceneStable,
            committedPayload = committedPayload,
        )
    }

    fun hasPlayed(key: RecognitionRevealKey): Boolean = playedKeys.hasPlayed(key)

    fun claimPlayback(key: RecognitionRevealKey): Boolean {
        val claimed = playedKeys.claim(key)
        if (claimed) updatePlaybackStatus(key, RevealPlaybackStatus.CLAIMED)
        return claimed
    }

    fun startPlayback(key: RecognitionRevealKey): Boolean = playedKeys.start(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.IN_PROGRESS)
    }

    fun resumePlayback(key: RecognitionRevealKey): Boolean = playedKeys.resume(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.IN_PROGRESS)
    }

    fun updatePlaybackProgress(key: RecognitionRevealKey, stage: com.yujian.ai.ui.waterlight.core.RecognitionSceneStage, progress: Float): Boolean {
        val updated = playedKeys.updateProgress(key, stage, progress)
        if (updated) {
            val current = snapshot?.takeIf { it.matches(key) } ?: return false
            snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
                playbackStatus = RevealPlaybackStatus.IN_PROGRESS,
                playbackProgressStage = stage,
                playbackProgress = progress,
            ))
        }
        return updated
    }

    fun interruptPlayback(key: RecognitionRevealKey): Boolean = playedKeys.interrupt(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.INTERRUPTED)
    }

    fun failPlayback(key: RecognitionRevealKey): Boolean = playedKeys.fail(key).also {
        if (it) updatePlaybackStatus(key, RevealPlaybackStatus.FAILED)
    }

    fun releaseAnimationGeometry(key: RecognitionRevealKey, monotonicTimestampMs: Long = 0L) {
        val current = snapshot ?: return
        if (!current.matches(key)) return
        if (!current.resultHandoffState.resultSceneStable) return
        if (current.resultHandoffState.animationGeometryReleased) return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(
            animationGeometryReleased = true,
            geometryReleasedTimestampMs = monotonicTimestampMs.coerceAtLeast(0L),
        ))
    }

    private fun updatePlaybackStatus(key: RecognitionRevealKey, status: RevealPlaybackStatus) {
        val current = snapshot?.takeIf { it.matches(key) } ?: return
        snapshot = current.copy(resultHandoffState = current.resultHandoffState.copy(playbackStatus = status))
    }

    private fun transition(
        status: RecognitionHandoffTransactionStatus,
        timestampMs: Long,
        source: String,
        eventSequence: Long,
    ) = RecognitionResultNavigationTraceEntry(
        status = status,
        monotonicTimestampMs = timestampMs.coerceAtLeast(0L),
        source = source,
        eventSequence = eventSequence,
    )

    private fun rebuildHandoff(
        current: RecognitionRevealSessionSnapshot,
        subject: RecognitionSubjectGeometrySnapshot? = current.subjectGeometry,
        processingPhoto: RecognitionProcessingPhotoSnapshot? = current.processingPhoto,
    ): RecognitionRevealHandoff? {
        val handoff = current.handoff ?: return null
        val image = current.image ?: return handoff
        val production = current.productionResult ?: return handoff
        return RecognitionRevealHandoffFactory.create(
            recognitionSessionId = handoff.key.recognitionSessionId,
            recognitionAttemptId = handoff.key.recognitionAttemptId,
            image = image,
            result = production,
            subject = subject,
            processingPhoto = processingPhoto,
            highConfidenceResult = current.highConfidenceResult,
            handoffTimestampUptimeMs = handoff.handoffTimestampUptimeMs,
            subjectStatus = current.subjectResult.status,
            fishPathSnapshot = current.fishPathSnapshot,
                orbOnlyReturnPathSnapshot = current.orbOnlyReturnPathSnapshot,
            pStarPathTerminalUnavailable = current.pStarPathTerminalUnavailable,
            frozenRevealDecision = handoff.revealDecision,
            recognitionSnapshotId = handoff.recognitionSnapshotId,
        )
    }

    private fun decisionFor(
        current: RecognitionRevealSessionSnapshot,
    ): RecognitionRevealDecisionState {
        val result = current.productionResult
        val image = current.image
        val classifier = when {
            result == null -> RecognitionClassifierTaskState.WAITING
            result.ready -> RecognitionClassifierTaskState.READY
            else -> RecognitionClassifierTaskState.FAILED
        }
        val primary = result?.assessment?.primary
        val trust = if (image != null && primary != null) RecognitionRevealPolicy.geometryTrust(
            detectorBox = primary.box,
            subject = current.subjectGeometry,
            imageId = image.imageId,
            sourceWidthPx = image.bitmap.width,
            sourceHeightPx = image.bitmap.height,
            detectorConfidence = primary.confidence,
            recognitionSessionId = current.recognitionSessionId,
            recognitionAttemptId = current.recognitionAttemptId,
            photoSha256 = image.photoSha256,
        ) else RecognitionGeometryTrustState.MISSING
        val contact = current.processingPhoto?.contactPointNormalizedSource
        val contactMaskMatches = contact != null && current.subjectGeometry?.let { subject ->
            current.processingPhoto.contactPointMaskSha256 == subject.maskSha256 &&
                current.processingPhoto.contactPointMaskVersion == subject.maskVersion &&
                current.processingPhoto.contactPointGeometryVersion > 0L
        } == true
        val contourClosed = current.subjectGeometry?.contour?.lastOrNull()?.closesContour == true
        val registeredPath = current.fishPathSnapshot?.takeIf { path ->
            path.isValid() && path.sessionId == current.recognitionSessionId &&
                path.attemptId == current.recognitionAttemptId && path.imageId == image?.imageId &&
                path.photoSha256 == image?.photoSha256 &&
                path.maskSha256 == current.subjectGeometry?.maskSha256 &&
                path.maskVersion == current.subjectGeometry?.maskVersion &&
                path.geometryVersion > 0L && contactMaskMatches &&
                path.pStarNormalizedSource == contact?.let { WaterLightPathPoint(it.x, it.y) }
        }
        val pStarPathReady = registeredPath != null
        val effectiveSubjectStatus = RecognitionRevealDecisionGate.effectiveSubjectStatus(
            current.subjectResult.status,
            hasAuthoritativeGeometry = current.subjectGeometry != null,
        )
        return RecognitionRevealDecisionGate.decide(
            classifier = classifier,
            subject = effectiveSubjectStatus,
            maskTrusted = trust == RecognitionGeometryTrustState.TRUSTED_MASK,
            contourValid = current.subjectGeometry?.contour?.isNotEmpty() == true,
            identityCurrent = image != null && !current.recognitionSessionId.isBlank() &&
                !current.recognitionAttemptId.isNullOrBlank() && image.imageId.isNotBlank() &&
                image.photoSha256.isNotBlank(),
            contourClosed = contourClosed,
            pStarPathReady = pStarPathReady,
            pStarPathTerminalUnavailable = current.pStarPathTerminalUnavailable,
            highConfidenceResult = current.highConfidenceResult,
        )
    }

    /** Releases bitmap and inference references when the user leaves this recognition attempt. */
    fun clearAttempt(preserveSession: Boolean) {
        val current = snapshot
        snapshot = if (preserveSession && current != null) {
            RecognitionRevealSessionSnapshot(recognitionSessionId = current.recognitionSessionId)
        } else {
            null
        }
    }

}
