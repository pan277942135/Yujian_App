package com.yujian.ai.ui.waterlight.core

/**
 * Retains the actual outward optical route for one recognition/photo/geometry identity.
 * The return route is constructed only by reversing those measured cubic segments.
 */
data class WaterLightPathIdentity(
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val photoSha256: String,
    val photoContentVersion: Long,
    val geometryVersion: Long,
)

data class WaterLightPathRoute(
    val id: String,
    val segments: List<WaterLightPathGeometry>,
) {
    fun isValid(): Boolean = id.isNotBlank() && segments.isNotEmpty() &&
        segments.all(WaterLightPathGeometry::isValid)
}

class WaterLightPathLedger {
    private var identity: WaterLightPathIdentity? = null
    private var orbToPerimeter: WaterLightPathGeometry? = null
    private var perimeterToDetector: WaterLightPathGeometry? = null
    private var perimeterToFish: WaterLightPathGeometry? = null

    fun bind(frame: WaterLightFrame): Boolean {
        val photo = frame.photoSha256?.takeIf(String::isNotBlank) ?: return false
        if (!frame.sceneFrame.isIdentityValid) return false
        val next = WaterLightPathIdentity(
            sessionId = frame.sessionId,
            attemptId = frame.attemptId,
            imageId = frame.imageId,
            photoSha256 = photo,
            photoContentVersion = frame.photoContentVersion,
            geometryVersion = frame.geometryVersion,
        )
        if (identity != next) {
            identity = next
            orbToPerimeter = null
            perimeterToDetector = null
            perimeterToFish = null
        }
        return true
    }

    fun recordRelease(frame: WaterLightFrame, geometry: WaterLightPathGeometry): Boolean {
        if (!bind(frame) || !geometry.isValid()) return false
        if (orbToPerimeter == null) orbToPerimeter = geometry
        return true
    }

    fun recordFishPath(
        frame: WaterLightFrame,
        geometry: WaterLightPathGeometry,
        trustedMask: Boolean,
    ): Boolean {
        if (!trustedMask || !bind(frame) || !geometry.isValid()) return false
        val detectorAnchor = perimeterToDetector?.end ?: return false
        if (distance(detectorAnchor, geometry.start) > MAX_JOIN_GAP_PX) return false
        if (perimeterToFish == null) perimeterToFish = geometry
        return true
    }

    /** Stores the measured photo-edge → real Detector BBox path used while SlimSAM is pending. */
    fun recordDetectorPath(frame: WaterLightFrame, geometry: WaterLightPathGeometry): Boolean {
        if (!bind(frame) || !geometry.isValid()) return false
        val perimeterAnchor = orbToPerimeter?.end ?: return false
        if (distance(perimeterAnchor, geometry.start) > MAX_JOIN_GAP_PX) return false
        if (perimeterToDetector == null) perimeterToDetector = geometry
        return true
    }

    fun releaseEndpoint(frame: WaterLightFrame): WaterLightPathPoint? =
        if (bind(frame)) orbToPerimeter?.end else null

    /** Fish → perimeter → the exact original Orb release anchor. */
    fun returnRoute(frame: WaterLightFrame, trustedMask: Boolean): WaterLightPathRoute? {
        if (!bind(frame)) return null
        val release = orbToPerimeter?.reversed() ?: return null
        val fish = perimeterToFish?.takeIf { trustedMask }?.reversed()
        val detector = perimeterToDetector?.takeIf { trustedMask }?.reversed()
        val segments = listOfNotNull(fish, detector, release)
        return WaterLightPathRoute(routeId(requireNotNull(identity)), segments)
            .takeIf(WaterLightPathRoute::isValid)
    }

    fun pathId(frame: WaterLightFrame): String? {
        if (!bind(frame)) return null
        return identity?.let(::routeId)
    }

    fun hasReleasePath(frame: WaterLightFrame): Boolean = bind(frame) && orbToPerimeter != null

    fun clear() {
        identity = null
        orbToPerimeter = null
        perimeterToDetector = null
        perimeterToFish = null
    }

    private fun routeId(value: WaterLightPathIdentity): String =
        value.sessionId + ":" + value.attemptId + ":" + value.imageId + ":" +
            value.photoSha256.take(12) + ":orb-waterlight-route"

    private fun distance(a: WaterLightPathPoint, b: WaterLightPathPoint): Float =
        kotlin.math.hypot(a.x - b.x, a.y - b.y)

    companion object {
        private const val MAX_JOIN_GAP_PX = 2f
    }

    private fun WaterLightPathGeometry.reversed(): WaterLightPathGeometry = copy(
        start = end,
        control1 = control2,
        control2 = control1,
        end = start,
    )
}
