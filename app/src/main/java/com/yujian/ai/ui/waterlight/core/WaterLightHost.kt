package com.yujian.ai.ui.waterlight.core

/** Six scene placements sharing one frame but retaining independent render backends. */
enum class WaterLightHost { ORB, PERIMETER, PATH, CONTOUR, RESIDUE, HANDOFF }

data class WaterLightHostGeometry(
    val host: WaterLightHost,
    val version: Long,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
) {
    val valid: Boolean get() = rightPx > leftPx && bottomPx > topPx &&
        listOf(leftPx, topPx, rightPx, bottomPx).all(Float::isFinite)
}

/** A host submits its existing renderer only for geometry belonging to the active photo. */
class WaterLightHostBinding(
    val host: WaterLightHost,
    val material: WaterLightMaterialProfile,
    private val engine: UnifiedWaterLightEngine,
    private val expectedIdentity: RecognitionSceneIdentity,
) {
    fun rejectionReason(frame: WaterLightFrame, geometry: WaterLightHostGeometry): String? {
        val frameIdentity = RecognitionSceneIdentity(
            sessionId = frame.sessionId,
            attemptId = frame.attemptId,
            imageId = frame.imageId,
            photoSha256 = frame.photoSha256,
            photoContentVersion = frame.photoContentVersion,
        )
        val override = frame.sceneFrameOverride
        return when {
            geometry.host != host -> "HOST_MISMATCH"
            frameIdentity.sessionId != expectedIdentity.sessionId -> "SESSION_IDENTITY_MISMATCH"
            frameIdentity.attemptId != expectedIdentity.attemptId -> "ATTEMPT_IDENTITY_MISMATCH"
            frameIdentity.imageId != expectedIdentity.imageId -> "IMAGE_IDENTITY_MISMATCH"
            frameIdentity.photoSha256 != expectedIdentity.photoSha256 -> "PHOTO_SHA256_MISMATCH"
            frameIdentity.photoContentVersion != expectedIdentity.photoContentVersion -> "PHOTO_CONTENT_VERSION_MISMATCH"
            override != null && override.identity != frameIdentity -> "SCENE_FRAME_IDENTITY_MISMATCH"
            override != null && override.geometryVersion != frame.geometryVersion -> "SCENE_FRAME_GEOMETRY_MISMATCH"
            !frame.sceneFrame.isIdentityValid -> "INVALID_SCENE_IDENTITY"
            geometry.version != frame.geometryVersion -> "GEOMETRY_VERSION_MISMATCH"
            !geometry.valid -> "INVALID_ORB_BOUNDS"
            material.host != host -> "MATERIAL_HOST_MISMATCH"
            !engine.accepts(frame) -> "ENGINE_FRAME_MISMATCH"
            else -> null
        }
    }

    fun accepts(frame: WaterLightFrame, geometry: WaterLightHostGeometry): Boolean =
        rejectionReason(frame, geometry) == null

    /** Source-core fallback is allowed only after the stable page identity and host geometry pass. */
    fun canUseSourceCoreFallback(frame: WaterLightFrame, geometry: WaterLightHostGeometry): Boolean {
        val reason = rejectionReason(frame, geometry) ?: return false
        if (reason !in SAFE_SOURCE_CORE_FALLBACK_REASONS) return false
        val identity = RecognitionSceneIdentity(
            frame.sessionId, frame.attemptId, frame.imageId, frame.photoSha256, frame.photoContentVersion,
        )
        return expectedIdentity.sessionId.isNotBlank() && expectedIdentity.attemptId.isNotBlank() &&
            expectedIdentity.imageId.isNotBlank() && !expectedIdentity.photoSha256.isNullOrBlank() &&
            expectedIdentity.photoContentVersion >= 0L && identity == expectedIdentity &&
            frame.sceneFrame.isIdentityValid && frame.sceneFrame.identity == expectedIdentity &&
            frame.sceneFrame.geometryVersion == frame.geometryVersion && geometry.host == host &&
            geometry.version == frame.geometryVersion && geometry.valid
    }

    private companion object {
        val SAFE_SOURCE_CORE_FALLBACK_REASONS = setOf("MATERIAL_HOST_MISMATCH", "ENGINE_FRAME_MISMATCH")
    }
}
