package com.yujian.ai.ui.waterlight.core

/** Visual energy is sampled from the existing recognition clock and current photo identity. */
enum class EnergyAnchor { ORB_CORE, ORB_RIBBON, PHOTO_PERIMETER, PHOTO_PATH, FISH_CONTOUR, REVEAL_TEXT, RESIDUE }
enum class EnergyFeedback { RELEASE, ARRIVE, ABSORB, RETURN, TRANSLATE, REVEAL, SETTLE }

data class WaterLightPathPoint(val x: Float, val y: Float) {
    fun isValid(): Boolean = x.isFinite() && y.isFinite()
}

/** Actual viewport-space curve endpoints derived from the current photo transform and trusted Mask. */
data class WaterLightPathGeometry(
    val start: WaterLightPathPoint,
    val control1: WaterLightPathPoint,
    val control2: WaterLightPathPoint,
    val end: WaterLightPathPoint,
    val coordinateSpace: String = "RECOGNITION_VIEWPORT_PX",
) {
    fun isValid(): Boolean = coordinateSpace == "RECOGNITION_VIEWPORT_PX" &&
        listOf(start, control1, control2, end).all(WaterLightPathPoint::isValid)
}

data class EnergyEvent(
    val sessionId: String,
    val photoSha256: String,
    val source: EnergyAnchor,
    val target: EnergyAnchor,
    val feedback: EnergyFeedback,
    val level: Float,
    val referenceMs: Long,
    val geometryVersion: Long,
    val attemptId: String = "",
    val imageId: String = "",
    val photoContentVersion: Long = 0L,
    val pathId: String? = null,
    val pathGeometry: WaterLightPathGeometry? = null,
    /** Ordered cubic segments; RETURN stores the outward route in reverse order and direction. */
    val pathSegments: List<WaterLightPathGeometry> = emptyList(),
    val eventSequence: Long = 0L,
    val monotonicTimestampMs: Long = 0L,
    val sourceHost: String? = null,
    val targetHost: String? = null,
    val renderStatus: String = "PENDING",
    val stage: RecognitionSceneStage = RecognitionSceneStage.PHOTO_STABLE,
) {
    fun matches(frame: WaterLightFrame): Boolean =
        sessionId == frame.sessionId && attemptId == frame.attemptId && imageId == frame.imageId &&
            photoSha256 == frame.photoSha256 && photoContentVersion == frame.photoContentVersion &&
            geometryVersion == frame.geometryVersion
}

object WaterLightEnergy {
    /** No fish-target event can be emitted until a real mask is trusted for this frame. */
    fun at(
        frame: WaterLightFrame,
        trustedMask: Boolean,
        successfulRevealAllowed: Boolean = false,
        pathGeometry: WaterLightPathGeometry? = null,
        orbOnlyRevealAllowed: Boolean = false,
        pathSegments: List<WaterLightPathGeometry> = emptyList(),
        pathIdOverride: String? = null,
    ): EnergyEvent? {
        val photo = frame.photoSha256?.takeIf(String::isNotBlank) ?: return null
        val scene = frame.sceneFrame
        if (!scene.isIdentityValid) return null
        val (from, to, feedback, level) = when {
            scene.stage == RecognitionSceneStage.ORB_RELEASE -> Quad(EnergyAnchor.ORB_RIBBON,
                EnergyAnchor.PHOTO_PERIMETER, EnergyFeedback.RELEASE, scene.stageProgress)
            scene.stage == RecognitionSceneStage.PERIMETER_ARRIVAL -> Quad(EnergyAnchor.PHOTO_PERIMETER,
                EnergyAnchor.PHOTO_PATH, EnergyFeedback.ARRIVE, scene.stageProgress)
            trustedMask && scene.stage == RecognitionSceneStage.PATH_TO_FISH -> Quad(EnergyAnchor.PHOTO_PATH,
                EnergyAnchor.FISH_CONTOUR, EnergyFeedback.ABSORB, scene.stageProgress)
            trustedMask && successfulRevealAllowed && scene.stage == RecognitionSceneStage.ORB_RETURN -> Quad(EnergyAnchor.FISH_CONTOUR,
                EnergyAnchor.ORB_CORE, EnergyFeedback.RETURN, scene.stageProgress)
            !trustedMask && orbOnlyRevealAllowed && successfulRevealAllowed &&
                scene.stage == RecognitionSceneStage.ORB_RETURN -> Quad(EnergyAnchor.PHOTO_PERIMETER,
                EnergyAnchor.ORB_CORE, EnergyFeedback.RETURN, scene.stageProgress)
            (trustedMask || orbOnlyRevealAllowed) && successfulRevealAllowed &&
                scene.stage == RecognitionSceneStage.ORB_TO_NAME -> Quad(EnergyAnchor.ORB_CORE,
                EnergyAnchor.REVEAL_TEXT, EnergyFeedback.TRANSLATE, scene.stageProgress)
            (trustedMask || orbOnlyRevealAllowed) && successfulRevealAllowed &&
                scene.stage == RecognitionSceneStage.SPECIES_NAME_REVEAL -> Quad(
                EnergyAnchor.REVEAL_TEXT, EnergyAnchor.REVEAL_TEXT, EnergyFeedback.REVEAL, scene.stageProgress)
            (trustedMask || orbOnlyRevealAllowed) && successfulRevealAllowed &&
                scene.stage == RecognitionSceneStage.RESIDUE_SETTLE -> Quad(
                EnergyAnchor.REVEAL_TEXT, EnergyAnchor.RESIDUE, EnergyFeedback.SETTLE, scene.stageProgress)
            else -> return null
        }
        return EnergyEvent(frame.sessionId, photo, from, to, feedback, level,
            scene.opticalReferenceElapsedMs, frame.geometryVersion, frame.attemptId, frame.imageId, frame.photoContentVersion,
            pathId = pathIdOverride ?: when (feedback) {
                EnergyFeedback.RELEASE -> "orb-to-photo"
                EnergyFeedback.ARRIVE, EnergyFeedback.ABSORB -> "photo-to-fish"
                EnergyFeedback.RETURN -> if (from == EnergyAnchor.PHOTO_PERIMETER) {
                    "photo-perimeter-to-original-orb"
                } else {
                    "fish-to-original-orb"
                }
                EnergyFeedback.TRANSLATE -> "orb-to-species-glyph"
                EnergyFeedback.REVEAL -> "species-glyph-ink"
                EnergyFeedback.SETTLE -> "glyph-to-residue"
            },
            pathGeometry = pathGeometry?.takeIf(WaterLightPathGeometry::isValid),
            pathSegments = pathSegments.filter(WaterLightPathGeometry::isValid),
            eventSequence = scene.eventSequence,
            monotonicTimestampMs = scene.monotonicTimestampMs,
            sourceHost = from.name,
            targetHost = to.name,
            renderStatus = if (pathGeometry?.isValid() == true || pathSegments.any(WaterLightPathGeometry::isValid)) "READY" else "PATH_PENDING",
            stage = scene.stage,
        )
    }

    private data class Quad(
        val from: EnergyAnchor, val to: EnergyAnchor, val feedback: EnergyFeedback, val level: Float,
    )
}
