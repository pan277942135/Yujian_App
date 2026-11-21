package com.yujian.ai.ui.waterlight.core

/** Stable identity for the trusted Mask and nearest-contour contact used by the fish route. */
data class WaterLightPathMaskIdentity(
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
    val contactPointNormalizedSource: WaterLightPathPoint,
) {
    fun isValid(): Boolean = maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
        maskVersion >= 0L && geometryVersion > 0L &&
        contactPointNormalizedSource.isValid() &&
        contactPointNormalizedSource.x in 0f..1f &&
        contactPointNormalizedSource.y in 0f..1f
}

data class WaterLightPathRoute(
    val id: String,
    val segments: List<WaterLightPathGeometry>,
    val maskSha256: String? = null,
    val maskVersion: Long? = null,
    val geometryVersion: Long,
    val contactPointNormalizedSource: WaterLightPathPoint? = null,
) {
    fun isValid(): Boolean = id.isNotBlank() && geometryVersion > 0L && segments.isNotEmpty() &&
        segments.all(WaterLightPathGeometry::isValid) &&
        (maskSha256 == null || (
            maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
                maskVersion != null && maskVersion >= 0L &&
                contactPointNormalizedSource?.isValid() == true &&
                contactPointNormalizedSource.x in 0f..1f &&
                contactPointNormalizedSource.y in 0f..1f
            ))
}

/** Immutable Processing-authored fish route identity carried into Result without regeneration. */
data class RecognitionFishPathSnapshot(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoContentVersion: Long,
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
    val pStarNormalizedSource: WaterLightPathPoint,
    val fishPathId: String,
    val fishPathGeometry: WaterLightPathGeometry,
    /** Exact photo-perimeter arrival path; its endpoint is the Task split and Fish Path start. */
    val perimeterBranch: WaterLightPathGeometry,
    val orbReleasePath: WaterLightPathGeometry,
    val returnPathId: String,
    val returnPathSegments: List<WaterLightPathGeometry>,
    val sourceCoordinateSpace: String,
    val creationTimestampMonotonicMs: Long,
    val creationEventSequence: Long,
) {
    fun isValid(): Boolean = sessionId.isNotBlank() && attemptId.isNotBlank() && imageId.isNotBlank() &&
        photoSha256.isNotBlank() && photoContentVersion >= 0L &&
        maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) && maskVersion >= 0L && geometryVersion > 0L &&
        pStarNormalizedSource.isValid() && pStarNormalizedSource.x in 0f..1f && pStarNormalizedSource.y in 0f..1f &&
        fishPathId.isNotBlank() && returnPathId == fishPathId &&
        sourceCoordinateSpace == fishPathGeometry.coordinateSpace &&
        fishPathGeometry.isValid() && perimeterBranch.isValid() && orbReleasePath.isValid() &&
        returnPathSegments.size == 3 && returnPathSegments.all(WaterLightPathGeometry::isValid) &&
        returnPathSegments.all { it.coordinateSpace == sourceCoordinateSpace } &&
        returnPathSegments.zipWithNext().all { (a, b) -> a.end == b.start } &&
        returnPathSegments.first().start == fishPathGeometry.end &&
        fishPathGeometry.start == perimeterBranch.end &&
        returnPathSegments[1].start == perimeterBranch.end &&
        returnPathSegments[1].end == perimeterBranch.start &&
        returnPathSegments[2].start == orbReleasePath.end &&
        returnPathSegments.last().end == orbReleasePath.start &&
        creationTimestampMonotonicMs >= 0L && creationEventSequence >= 0L

    fun matches(frame: WaterLightFrame): Boolean = isValid() &&
        sessionId == frame.sessionId && attemptId == frame.attemptId && imageId == frame.imageId &&
        photoSha256 == frame.photoSha256 && photoContentVersion == frame.photoContentVersion
}

private data class WaterLightPathIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoContentVersion: Long,
    val geometryVersion: Long,
) {
    fun sameAttempt(other: WaterLightPathIdentity): Boolean =
        sessionId == other.sessionId && attemptId == other.attemptId && imageId == other.imageId &&
            photoSha256 == other.photoSha256 && photoContentVersion == other.photoContentVersion
}

private data class WaterLightPathMaskBinding(
    val maskIdentity: WaterLightPathMaskIdentity,
    val geometry: WaterLightPathGeometry,
    val creationTimestampMonotonicMs: Long,
    val creationEventSequence: Long,
)

/** Stage-04 perimeter entry route. It is photo-bound and deliberately carries no Mask/P* identity. */
data class WaterLightPerimeterArrivalSnapshot(
    val pathId: String,
    val geometry: WaterLightPathGeometry,
    val geometryVersion: Long,
) {
    fun isValid(): Boolean = pathId.isNotBlank() && geometryVersion > 0L && geometry.isValid()
}

/**
 * One attempt-scoped ledger. The release route and trusted-mask route are recorded once,
 * then reused for all later stage events. RETURN is assembled by reversing these exact
 * segments; it never reconstructs a visually similar path from current layout coordinates.
 */
class WaterLightPathLedger {
    private var identity: WaterLightPathIdentity? = null
    private var orbToPerimeter: WaterLightPathGeometry? = null
    private var perimeterArrival: WaterLightPerimeterArrivalSnapshot? = null
    private var maskBinding: WaterLightPathMaskBinding? = null
    private var perimeterBranch: WaterLightPathGeometry? = null

    fun bind(frame: WaterLightFrame): Boolean {
        val next = frame.identityOrNull() ?: return false
        val current = identity
        if (current == null || !current.sameAttempt(next)) {
            identity = next
            orbToPerimeter = null
            perimeterArrival = null
            maskBinding = null
            perimeterBranch = null
        } else if (current.geometryVersion != next.geometryVersion) {
            // Keep the authored Stage-03 endpoint for Stage-04, but rebuild routes whose
            // geometry and P* are bound to the photo's current measured transform.
            identity = next
            perimeterArrival = null
            maskBinding = null
            perimeterBranch = null
        }
        return true
    }

    fun recordRelease(frame: WaterLightFrame, geometry: WaterLightPathGeometry): Boolean {
        if (!bind(frame) || !geometry.isValid()) return false
        if (orbToPerimeter == null) orbToPerimeter = geometry
        return orbToPerimeter == geometry
    }

    /**
     * Record the one authoritative Stage-04 route from the Stage-03 release endpoint into the
     * current photo. This route is independent of SlimSAM, Mask trust, and P*.
     */
    fun recordPerimeterArrivalPath(
        frame: WaterLightFrame,
        geometry: WaterLightPathGeometry,
    ): Boolean {
        if (!bind(frame) || !geometry.isValid()) return false
        val release = orbToPerimeter ?: return false
        if (geometry.start != release.end || geometry.end == geometry.start) return false
        val pathId = pathId(frame)?.plus(":arrival") ?: return false
        val snapshot = WaterLightPerimeterArrivalSnapshot(pathId, geometry, frame.geometryVersion)
        if (!snapshot.isValid()) return false
        val existing = perimeterArrival
        if (existing != null) return existing == snapshot
        perimeterArrival = snapshot
        return true
    }

    fun perimeterArrivalPath(frame: WaterLightFrame): WaterLightPerimeterArrivalSnapshot? {
        if (!bind(frame)) return null
        return perimeterArrival?.takeIf { it.geometryVersion == frame.geometryVersion }
    }

    fun recordFishPath(
        frame: WaterLightFrame,
        geometry: WaterLightPathGeometry,
        trustedMask: Boolean,
        maskSha256: String,
        maskVersion: Long,
        contactPointNormalizedSource: WaterLightPathPoint,
    ): Boolean {
        if (!trustedMask || !bind(frame) || !geometry.isValid()) return false
        val maskIdentity = WaterLightPathMaskIdentity(
            maskSha256 = maskSha256,
            maskVersion = maskVersion,
            geometryVersion = frame.geometryVersion,
            contactPointNormalizedSource = contactPointNormalizedSource,
        )
        if (!maskIdentity.isValid()) return false
        val arrival = perimeterArrival?.takeIf { it.geometryVersion == frame.geometryVersion } ?: return false
        // The Task split lives at the end of the measured photo-perimeter arrival. Fish search
        // begins at that exact point, so no straight cross-photo bridge is inserted.
        if (geometry.start != arrival.geometry.end) return false
        val existing = maskBinding
        if (existing != null) {
            val sameMaskGeometry = existing.maskIdentity.maskSha256 == maskIdentity.maskSha256 &&
                existing.maskIdentity.maskVersion == maskIdentity.maskVersion &&
                existing.maskIdentity.geometryVersion == maskIdentity.geometryVersion
            if (sameMaskGeometry) {
                // P* is frozen at the first trusted Stage-05 contact. Later recompositions
                // may propose a different nearest point while the Orb settles, but may not
                // move the active path anchor within this same Mask/geometry identity.
                return existing.maskIdentity.contactPointNormalizedSource == contactPointNormalizedSource &&
                    existing.geometry == geometry
            }
            // A new Mask SHA/version or geometry invalidates only the fish segment.
            // The release route remains valid for the same photo attempt.
            maskBinding = null
            perimeterBranch = null
        }
        val current = maskBinding
        if (current == null) {
            val branch = arrival.geometry
            if (branch.start != (orbToPerimeter ?: return false).end) return false
            perimeterBranch = branch
            val sceneFrame = frame.sceneFrame
            maskBinding = WaterLightPathMaskBinding(
                maskIdentity = maskIdentity,
                geometry = geometry,
                creationTimestampMonotonicMs = sceneFrame.monotonicTimestampMs,
                creationEventSequence = sceneFrame.eventSequence,
            )
            return true
        }
        return current.maskIdentity == maskIdentity && current.geometry == geometry
    }

    fun releasePath(frame: WaterLightFrame): WaterLightPathGeometry? {
        if (!bind(frame)) return null
        return orbToPerimeter
    }

    fun fishPath(
        frame: WaterLightFrame,
        maskSha256: String,
        maskVersion: Long,
        contactPointNormalizedSource: WaterLightPathPoint,
    ): WaterLightPathGeometry? {
        if (!bind(frame)) return null
        val current = maskBinding ?: return null
        return current.geometry.takeIf {
            current.maskIdentity == expectedMaskIdentity(frame, maskSha256, maskVersion, contactPointNormalizedSource)
        }
    }

    fun contactPointNormalizedSource(
        frame: WaterLightFrame,
        maskSha256: String,
        maskVersion: Long,
    ): WaterLightPathPoint? {
        if (!bind(frame)) return null
        val current = maskBinding ?: return null
        return current.maskIdentity.contactPointNormalizedSource.takeIf {
            current.maskIdentity.maskSha256 == maskSha256 &&
                current.maskIdentity.maskVersion == maskVersion &&
                current.maskIdentity.geometryVersion == frame.geometryVersion
        }
    }

    fun returnRoute(
        frame: WaterLightFrame,
        trustedMask: Boolean,
        maskSha256: String? = null,
        maskVersion: Long? = null,
        contactPointNormalizedSource: WaterLightPathPoint? = null,
    ): WaterLightPathRoute? {
        if (!bind(frame)) return null
        val release = orbToPerimeter ?: return null
        val segments = buildList {
            if (trustedMask) {
                val expected = expectedMaskIdentity(
                    frame,
                    maskSha256 ?: return null,
                    maskVersion ?: return null,
                    contactPointNormalizedSource ?: return null,
                ) ?: return null
                val fish = maskBinding?.takeIf { it.maskIdentity == expected }?.geometry ?: return null
                val branch = perimeterBranch ?: return null
                add(fish.reversed())
                add(branch.reversed())
            }
            add(release.reversed())
        }
        val pathId = pathId(
            frame = frame,
            maskSha256 = if (trustedMask) maskSha256 else null,
            maskVersion = if (trustedMask) maskVersion else null,
            contactPointNormalizedSource = if (trustedMask) contactPointNormalizedSource else null,
        ) ?: return null
        return WaterLightPathRoute(
            id = pathId,
            segments = segments,
            maskSha256 = if (trustedMask) maskSha256 else null,
            maskVersion = if (trustedMask) maskVersion else null,
            geometryVersion = frame.geometryVersion,
            contactPointNormalizedSource = if (trustedMask) contactPointNormalizedSource else null,
        ).takeIf(WaterLightPathRoute::isValid)
    }

    /** Exports the exact ledger paths and identities used by Processing for a FULL_REVEAL handoff. */
    fun exportRecognitionFishPathSnapshot(
        frame: WaterLightFrame,
        maskSha256: String,
        maskVersion: Long,
        contactPointNormalizedSource: WaterLightPathPoint,
    ): RecognitionFishPathSnapshot? {
        if (!bind(frame)) return null
        val base = identity ?: return null
        val binding = maskBinding ?: return null
        if (binding.maskIdentity.maskSha256 != maskSha256 ||
            binding.maskIdentity.maskVersion != maskVersion ||
            binding.maskIdentity.contactPointNormalizedSource != contactPointNormalizedSource
        ) return null
        val fish = fishPath(frame, maskSha256, maskVersion, contactPointNormalizedSource) ?: return null
        val branch = perimeterBranch ?: return null
        val release = releasePath(frame) ?: return null
        val route = returnRoute(frame, trustedMask = true, maskSha256, maskVersion, contactPointNormalizedSource)
            ?.takeIf(WaterLightPathRoute::isValid) ?: return null
        val pathId = pathId(frame, maskSha256, maskVersion, contactPointNormalizedSource) ?: return null
        val snapshot = RecognitionFishPathSnapshot(
            sessionId = base.sessionId,
            attemptId = base.attemptId,
            imageId = base.imageId,
            photoSha256 = base.photoSha256,
            photoContentVersion = base.photoContentVersion,
            maskSha256 = binding.maskIdentity.maskSha256,
            maskVersion = binding.maskIdentity.maskVersion,
            geometryVersion = binding.maskIdentity.geometryVersion,
            pStarNormalizedSource = binding.maskIdentity.contactPointNormalizedSource,
            fishPathId = pathId,
            fishPathGeometry = fish,
            perimeterBranch = branch,
            orbReleasePath = release,
            returnPathId = route.id,
            returnPathSegments = route.segments.toList(),
            sourceCoordinateSpace = fish.coordinateSpace,
            creationTimestampMonotonicMs = binding.creationTimestampMonotonicMs,
            creationEventSequence = binding.creationEventSequence,
        )
        return snapshot.takeIf(RecognitionFishPathSnapshot::isValid)
    }

    fun pathId(
        frame: WaterLightFrame,
        maskSha256: String? = null,
        maskVersion: Long? = null,
        contactPointNormalizedSource: WaterLightPathPoint? = null,
    ): String? {
        if (!bind(frame)) return null
        val base = identity ?: return null
        val maskId = if (maskSha256 == null && maskVersion == null && contactPointNormalizedSource == null) {
            null
        } else {
            expectedMaskIdentity(
                frame,
                maskSha256 ?: return null,
                maskVersion ?: return null,
                contactPointNormalizedSource ?: return null,
            ) ?: return null
        }
        if (maskId != null && maskBinding?.maskIdentity != maskId) return null
        val prefix = base.sessionId + ":" + base.attemptId + ":" + base.imageId + ":" +
            base.photoContentVersion + ":" + base.geometryVersion
        return if (maskId == null) {
            "waterlight:" + prefix
        } else {
            val point = maskId.contactPointNormalizedSource
            "waterlight:" + prefix + ":mask:" + maskId.maskSha256.take(16) + ":" +
                maskId.maskVersion + ":" +
                String.format(java.util.Locale.US, "%.6f", point.x) + ":" +
                String.format(java.util.Locale.US, "%.6f", point.y)
        }
    }

    fun clear() {
        identity = null
        orbToPerimeter = null
        perimeterArrival = null
        maskBinding = null
        perimeterBranch = null
    }

    private fun expectedMaskIdentity(
        frame: WaterLightFrame,
        maskSha256: String,
        maskVersion: Long,
        contactPointNormalizedSource: WaterLightPathPoint,
    ): WaterLightPathMaskIdentity? {
        val expected = WaterLightPathMaskIdentity(
            maskSha256 = maskSha256,
            maskVersion = maskVersion,
            geometryVersion = frame.geometryVersion,
            contactPointNormalizedSource = contactPointNormalizedSource,
        )
        return expected.takeIf(WaterLightPathMaskIdentity::isValid)
    }

    private fun WaterLightFrame.identityOrNull(): WaterLightPathIdentity? {
        if (sessionId.isBlank() || attemptId.isBlank() || imageId.isBlank() ||
            photoSha256.isNullOrBlank() || photoContentVersion < 0L || geometryVersion <= 0L
        ) return null
        return WaterLightPathIdentity(
            sessionId = sessionId,
            attemptId = attemptId,
            imageId = imageId,
            photoSha256 = photoSha256,
            photoContentVersion = photoContentVersion,
            geometryVersion = geometryVersion,
        )
    }

    private fun bridge(
        start: WaterLightPathPoint,
        end: WaterLightPathPoint,
    ): WaterLightPathGeometry? {
        if (!start.isValid() || !end.isValid()) return null
        val dx = end.x - start.x
        val dy = end.y - start.y
        return WaterLightPathGeometry(
            start = start,
            control1 = WaterLightPathPoint(start.x + dx * .35f, start.y + dy * .35f),
            control2 = WaterLightPathPoint(start.x + dx * .65f, start.y + dy * .65f),
            end = end,
        ).takeIf(WaterLightPathGeometry::isValid)
    }

    private fun WaterLightPathGeometry.reversed() = WaterLightPathGeometry(
        start = end,
        control1 = control2,
        control2 = control1,
        end = start,
        coordinateSpace = coordinateSpace,
    )
}
