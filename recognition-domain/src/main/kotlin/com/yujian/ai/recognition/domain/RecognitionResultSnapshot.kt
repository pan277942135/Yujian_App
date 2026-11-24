package com.yujian.ai.recognition.domain

import java.security.MessageDigest
import java.util.Collections

enum class RecognitionOutcome { FULL_REVEAL, ORB_ONLY, CLASSIFICATION_FAILED }

data class ModelIdentity(val id: String, val version: String, val sha256: String) {
    fun isValid(): Boolean = id.isNotBlank() && version.isNotBlank() && sha256.isSha256()
}

data class ModelBundleIdentity(
    val detector: ModelIdentity,
    val classifier: ModelIdentity,
    val visionEncoder: ModelIdentity?,
    val maskDecoder: ModelIdentity?,
) {
    fun isValid(): Boolean = detector.isValid() && classifier.isValid() &&
        (visionEncoder == null || visionEncoder.isValid()) && (maskDecoder == null || maskDecoder.isValid())
}

data class DetectorDetection(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val confidence: Float) {
    fun isValid(): Boolean = listOf(x1, y1, x2, y2, confidence).all(Float::isFinite) &&
        x1 in 0f..1f && y1 in 0f..1f && x2 in 0f..1f && y2 in 0f..1f &&
        x2 > x1 && y2 > y1 && confidence in 0f..1f
}

data class DetectorResult(
    val terminalStatus: String,
    val detections: List<DetectorDetection>,
) {
    fun isValid(): Boolean = terminalStatus.isNotBlank() && detections.all(DetectorDetection::isValid)
}

data class SpeciesResult(val key: String, val name: String, val confidence: Float) {
    fun isValid(): Boolean = key.isNotBlank() && name.isNotBlank() && confidence.isFinite() && confidence in 0f..1f
}

data class ClassifierResult(
    val terminalStatus: String,
    val modelRunId: String,
    val species: SpeciesResult?,
    val candidates: List<SpeciesResult>,
) {
    fun isValid(): Boolean = terminalStatus.isNotBlank() && modelRunId.isNotBlank() &&
        candidates.all(SpeciesResult::isValid) && (species == null || species.isValid()) &&
        (terminalStatus != "READY" || species != null)
}

data class MaskOutcome(
    val terminalStatus: String,
    val trusted: Boolean,
    val maskSha256: String?,
    val failureCode: String?,
) {
    fun isValid(): Boolean = terminalStatus.isNotBlank() &&
        (maskSha256 == null || maskSha256.isSha256()) && (!trusted || maskSha256?.isSha256() == true) &&
        (!trusted || terminalStatus == "TRUSTED")
}

data class ContourOutcome(val terminalStatus: String, val closed: Boolean, val geometrySha256: String?) {
    fun isValid(): Boolean = terminalStatus.isNotBlank() &&
        (geometrySha256 == null || geometrySha256.isSha256()) &&
        (!closed || geometrySha256?.isSha256() == true)
}

data class PStarGeometry(
    val sourceX: Float,
    val sourceY: Float,
    val viewportX: Float,
    val viewportY: Float,
    val geometryVersion: Long,
) {
    fun isValid(): Boolean = listOf(sourceX, sourceY, viewportX, viewportY).all(Float::isFinite) &&
        sourceX in 0f..1f && sourceY in 0f..1f && geometryVersion > 0L
}

data class FishPathSnapshotRef(
    val pathId: String,
    val contentSha256: String,
    val geometryVersion: Long,
    val photoIdentity: PhotoIdentity,
) {
    fun isValid(): Boolean = pathId.isNotBlank() && contentSha256.isSha256() && geometryVersion > 0L &&
        photoIdentity.isValid()
}

/** Pure immutable recognition payload. It contains no Android, Compose, raster, or evidence state. */
class RecognitionResultSnapshot private constructor(
    val snapshotId: String,
    val recognitionSessionId: String,
    val attemptId: String,
    val photoIdentity: PhotoIdentity,
    val modelBundleIdentity: ModelBundleIdentity,
    val detectorResult: DetectorResult,
    val classifierResult: ClassifierResult,
    val maskOutcome: MaskOutcome,
    val contourOutcome: ContourOutcome,
    val pStarGeometry: PStarGeometry?,
    val fishPathSnapshotRef: FishPathSnapshotRef?,
    val recognitionOutcome: RecognitionOutcome,
    val schemaVersion: Int,
    val contentHash: String,
    val committedAtMonotonicNanos: Long,
) {
    fun isValid(): Boolean {
        if (snapshotId.isBlank() || recognitionSessionId.isBlank() || attemptId.isBlank() ||
            !photoIdentity.isValid() || !modelBundleIdentity.isValid() || !detectorResult.isValid() ||
            !classifierResult.isValid() || !maskOutcome.isValid() || !contourOutcome.isValid() ||
            schemaVersion != CURRENT_SCHEMA_VERSION || committedAtMonotonicNanos <= 0L ||
            !contentHash.isSha256() || contentHash != calculateContentHash()
        ) return false
        val pathMatchesPhoto = fishPathSnapshotRef?.let {
            it.isValid() && it.photoIdentity == photoIdentity
        } ?: true
        if (!pathMatchesPhoto) return false
        return when (recognitionOutcome) {
            RecognitionOutcome.FULL_REVEAL -> classifierResult.terminalStatus == "READY" &&
                maskOutcome.trusted && contourOutcome.closed && pStarGeometry?.isValid() == true &&
                fishPathSnapshotRef?.isValid() == true
            RecognitionOutcome.ORB_ONLY -> classifierResult.terminalStatus == "READY" &&
                !maskOutcome.trusted && maskOutcome.terminalStatus !in setOf("PENDING", "IDLE", "RUNNING") &&
                !contourOutcome.closed && pStarGeometry == null && fishPathSnapshotRef == null
            RecognitionOutcome.CLASSIFICATION_FAILED -> classifierResult.terminalStatus != "READY" &&
                classifierResult.species == null
        }
    }

    fun calculateContentHash(): String = sha256(canonicalContent())

    private fun canonicalContent(): String = listOf(
        "schema=$schemaVersion",
        "session=$recognitionSessionId",
        "attempt=$attemptId",
        "photo=${photoIdentity.photoId}|${photoIdentity.sourceBytesSha256}|${photoIdentity.canonicalPixelSha256}|" +
            "${photoIdentity.width}|${photoIdentity.height}|${photoIdentity.sourceOrientation}|" +
            "${photoIdentity.normalizationVersion}|${photoIdentity.photoContentVersion}",
        "models=${modelBundleIdentity.canonical()}",
        "detector=${detectorResult.terminalStatus}|${detectorResult.detections.joinToString(";") { it.canonical() }}",
        "classifier=${classifierResult.terminalStatus}|${classifierResult.modelRunId}|" +
            "${classifierResult.species?.canonical().orEmpty()}|${classifierResult.candidates.joinToString(";") { it.canonical() }}",
        "mask=${maskOutcome.terminalStatus}|${maskOutcome.trusted}|${maskOutcome.maskSha256.orEmpty()}|${maskOutcome.failureCode.orEmpty()}",
        "contour=${contourOutcome.terminalStatus}|${contourOutcome.closed}|${contourOutcome.geometrySha256.orEmpty()}",
        "pstar=${pStarGeometry?.canonical().orEmpty()}",
        "path=${fishPathSnapshotRef?.let { "${it.pathId}|${it.contentSha256}|${it.geometryVersion}" }.orEmpty()}",
        "outcome=${recognitionOutcome.name}",
    ).joinToString(separator = "\n") { "${it.length}:$it" }

    private fun ModelBundleIdentity.canonical() = listOfNotNull(
        detector.canonical(), classifier.canonical(), visionEncoder?.canonical(), maskDecoder?.canonical(),
    ).joinToString("|")

    private fun ModelIdentity.canonical() = "$id|$version|$sha256"
    private fun SpeciesResult.canonical() = "$key|$name|${confidence.toBits()}"
    private fun DetectorDetection.canonical() =
        "${x1.toBits()}|${y1.toBits()}|${x2.toBits()}|${y2.toBits()}|${confidence.toBits()}"
    private fun PStarGeometry.canonical() =
        "${sourceX.toBits()}|${sourceY.toBits()}|${viewportX.toBits()}|${viewportY.toBits()}|$geometryVersion"

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun create(
            snapshotId: String,
            recognitionSessionId: String,
            attemptId: String,
            photoIdentity: PhotoIdentity,
            modelBundleIdentity: ModelBundleIdentity,
            detectorResult: DetectorResult,
            classifierResult: ClassifierResult,
            maskOutcome: MaskOutcome,
            contourOutcome: ContourOutcome,
            pStarGeometry: PStarGeometry?,
            fishPathSnapshotRef: FishPathSnapshotRef?,
            recognitionOutcome: RecognitionOutcome,
            committedAtMonotonicNanos: Long,
        ): RecognitionResultSnapshot {
            val frozenDetector = detectorResult.copy(detections = immutable(detectorResult.detections))
            val frozenClassifier = classifierResult.copy(candidates = immutable(classifierResult.candidates))
            val provisional = RecognitionResultSnapshot(
                snapshotId, recognitionSessionId, attemptId, photoIdentity, modelBundleIdentity,
                frozenDetector, frozenClassifier, maskOutcome, contourOutcome, pStarGeometry,
                fishPathSnapshotRef, recognitionOutcome, CURRENT_SCHEMA_VERSION, "", committedAtMonotonicNanos,
            )
            return RecognitionResultSnapshot(
                snapshotId, recognitionSessionId, attemptId, photoIdentity, modelBundleIdentity,
                frozenDetector, frozenClassifier, maskOutcome, contourOutcome, pStarGeometry,
                fishPathSnapshotRef, recognitionOutcome, CURRENT_SCHEMA_VERSION,
                provisional.calculateContentHash(), committedAtMonotonicNanos,
            )
        }

        private fun <T> immutable(items: List<T>): List<T> =
            Collections.unmodifiableList(ArrayList(items))

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

enum class SnapshotCommitStatus { COMMITTED, ALREADY_COMMITTED, CONFLICT, STALE_ATTEMPT, INVALID }

data class SnapshotCommitResult(val status: SnapshotCommitStatus, val snapshot: RecognitionResultSnapshot?)

/** Attempt-scoped commit-once owner. Late callbacks from superseded attempts cannot replace data. */
class RecognitionSnapshotCommitter {
    private var activeAttemptId: String? = null
    private val committed = linkedMapOf<String, RecognitionResultSnapshot>()

    @Synchronized
    fun activateAttempt(attemptId: String) {
        require(attemptId.isNotBlank())
        activeAttemptId = attemptId
    }

    @Synchronized
    fun commitOnce(attemptId: String, candidate: RecognitionResultSnapshot): SnapshotCommitResult {
        if (activeAttemptId != attemptId || candidate.attemptId != attemptId) {
            return SnapshotCommitResult(SnapshotCommitStatus.STALE_ATTEMPT, null)
        }
        if (!candidate.isValid()) return SnapshotCommitResult(SnapshotCommitStatus.INVALID, null)
        val existing = committed[attemptId]
        if (existing != null) {
            return if (existing.contentHash == candidate.contentHash) {
                SnapshotCommitResult(SnapshotCommitStatus.ALREADY_COMMITTED, existing)
            } else SnapshotCommitResult(SnapshotCommitStatus.CONFLICT, existing)
        }
        committed[attemptId] = candidate
        return SnapshotCommitResult(SnapshotCommitStatus.COMMITTED, candidate)
    }

    @Synchronized
    fun snapshot(attemptId: String): RecognitionResultSnapshot? = committed[attemptId]
}
