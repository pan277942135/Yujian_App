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
    private var perimeterBridge: WaterLightPathGeometry? = null
    private var perimeterToFish: WaterLightPathGeometry? = null
    private var fishMaskIdentity: String? = null

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
            perimeterBridge = null
            perimeterToFish = null
            fishMaskIdentity = null
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
        maskIdentity: String,
    ): Boolean {
        if (!trustedMask || maskIdentity.isBlank() || !bind(frame) || !geometry.isValid()) return false
        if (fishMaskIdentity != maskIdentity) {
            perimeterToFish = null
            perimeterBridge = null
            fishMaskIdentity = maskIdentity
        }
        if (perimeterToFish == null) {
            perimeterToFish = geometry
            val releaseEdge = orbToPerimeter?.end
            val fishEdge = geometry.start
            if (releaseEdge != null &&
                kotlin.math.hypot(releaseEdge.x - fishEdge.x, releaseEdge.y - fishEdge.y) >= 1f
            ) {
                perimeterBridge = WaterLightPathGeometryFactory.between(
                    releaseEdge,
                    fishEdge,
                    seed = requireNotNull(identity).hashCode(),
                    bendFraction = .08f,
                )
            }
        }
        return true
    }

    /** Fish → perimeter → the exact original Orb release anchor. */
    fun returnRoute(frame: WaterLightFrame, trustedMask: Boolean, maskIdentity: String? = null): WaterLightPathRoute? {
        if (!bind(frame)) return null
        val release = orbToPerimeter?.reversed() ?: return null
        val fish = perimeterToFish?.takeIf {
            trustedMask && !maskIdentity.isNullOrBlank() && maskIdentity == fishMaskIdentity
        }?.reversed()
        val bridge = perimeterBridge?.takeIf {
            trustedMask && !maskIdentity.isNullOrBlank() && maskIdentity == fishMaskIdentity
        }?.reversed()
        val segments = listOfNotNull(fish, bridge, release)
        return WaterLightPathRoute(routeId(requireNotNull(identity), fishMaskIdentity?.takeIf { fish != null }), segments)
            .takeIf(WaterLightPathRoute::isValid)
    }

    fun pathId(frame: WaterLightFrame, maskIdentity: String? = null): String? {
        if (!bind(frame)) return null
        return identity?.let { routeId(it, maskIdentity?.takeIf { candidate -> candidate == fishMaskIdentity }) }
    }

    fun hasReleasePath(frame: WaterLightFrame): Boolean = bind(frame) && orbToPerimeter != null

    fun clear() {
        identity = null
        orbToPerimeter = null
        perimeterBridge = null
        perimeterToFish = null
        fishMaskIdentity = null
    }

    private fun routeId(value: WaterLightPathIdentity, maskIdentity: String? = null): String =
        value.sessionId + ":" + value.attemptId + ":" + value.imageId + ":" +
            value.photoSha256.take(12) + ":orb-waterlight-route" +
            maskIdentity?.let { ":mask-${it.hashCode().toString(16)}" }.orEmpty()

    private fun WaterLightPathGeometry.reversed(): WaterLightPathGeometry = copy(
        start = end,
        control1 = control2,
        control2 = control1,
        end = start,
    )
}
