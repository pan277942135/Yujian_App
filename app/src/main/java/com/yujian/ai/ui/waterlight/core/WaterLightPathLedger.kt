package com.yujian.ai.ui.waterlight.core

/** Stable identity for the trusted Mask and nearest-contour contact used by the fish route. */
data class WaterLightPathMaskIdentity(
    val maskSha256: String,
    val maskVersion: Long,
    val geometryVersion: Long,
    val contactPointNormalizedSource: WaterLightPathPoint,
) {
    fun isValid(): Boolean = maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
        maskVersion >= 0L && geometryVersion >= 0L &&
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
    fun isValid(): Boolean = id.isNotBlank() && segments.isNotEmpty() &&
        segments.all(WaterLightPathGeometry::isValid) &&
        (maskSha256 == null || (
            maskSha256.matches(Regex("(?i)^[a-f0-9]{64}$")) &&
                maskVersion != null && maskVersion >= 0L &&
                contactPointNormalizedSource?.isValid() == true &&
                contactPointNormalizedSource.x in 0f..1f &&
                contactPointNormalizedSource.y in 0f..1f
            ))
}

private data class WaterLightPathIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoContentVersion: Long,
    val geometryVersion: Long,
)

private data class WaterLightPathMaskBinding(
    val maskIdentity: WaterLightPathMaskIdentity,
    val geometry: WaterLightPathGeometry,
)

/**
 * One attempt-scoped ledger. The release route and trusted-mask route are recorded once,
 * then reused for all later stage events. RETURN is assembled by reversing these exact
 * segments; it never reconstructs a visually similar path from current layout coordinates.
 */
class WaterLightPathLedger {
    private var identity: WaterLightPathIdentity? = null
    private var orbToPerimeter: WaterLightPathGeometry? = null
    private var maskBinding: WaterLightPathMaskBinding? = null
    private var perimeterBridge: WaterLightPathGeometry? = null

    fun bind(frame: WaterLightFrame): Boolean {
        val next = frame.identityOrNull() ?: return false
        if (identity != next) {
            identity = next
            orbToPerimeter = null
            maskBinding = null
            perimeterBridge = null
        }
        return true
    }

    fun recordRelease(frame: WaterLightFrame, geometry: WaterLightPathGeometry): Boolean {
        if (!bind(frame) || !geometry.isValid()) return false
        if (orbToPerimeter == null) orbToPerimeter = geometry
        return orbToPerimeter == geometry
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
        val existing = maskBinding
        if (existing != null && existing.maskIdentity != maskIdentity) {
            // A new Mask SHA/version/P* invalidates only the fish segment. The release
            // route remains valid for the same photo attempt and geometry version.
            maskBinding = null
            perimeterBridge = null
        }
        val current = maskBinding
        if (current == null) {
            val release = orbToPerimeter ?: return false
            val bridge = bridge(release.end, geometry.start) ?: return false
            perimeterBridge = bridge
            maskBinding = WaterLightPathMaskBinding(maskIdentity, geometry)
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
                val bridge = perimeterBridge ?: return null
                add(fish.reversed())
                add(bridge.reversed())
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
        maskBinding = null
        perimeterBridge = null
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
            photoSha256.isNullOrBlank() || photoContentVersion < 0L || geometryVersion < 0L
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
