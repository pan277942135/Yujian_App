package com.yujian.ai.ui.waterlight.core

/** Visual energy is sampled from the existing recognition clock and current photo identity. */
enum class EnergyAnchor { ORB_CORE, ORB_RIBBON, PHOTO_PERIMETER, PHOTO_PATH, FISH_CONTOUR, REVEAL_TEXT, RESIDUE }
enum class EnergyFeedback { RELEASE, ARRIVE, ABSORB, RETURN, TRANSLATE, REVEAL, SETTLE }

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
)

object WaterLightEnergy {
    /** No fish-target event can be emitted until a real mask is trusted for this frame. */
    fun at(frame: WaterLightFrame, trustedMask: Boolean): EnergyEvent? {
        val photo = frame.photoSha256?.takeIf(String::isNotBlank) ?: return null
        val scene = frame.sceneFrame
        if (!scene.isIdentityValid) return null
        val t = scene.opticalReferenceElapsedMs
        val (from, to, feedback, level) = when {
            t < 350L -> return null
            t < 950L -> Quad(EnergyAnchor.ORB_RIBBON, EnergyAnchor.PHOTO_PERIMETER,
                EnergyFeedback.RELEASE, ((t - 350L) / 600f).coerceIn(0f, 1f))
            t < 1_075L -> Quad(EnergyAnchor.PHOTO_PERIMETER, EnergyAnchor.PHOTO_PATH,
                EnergyFeedback.ARRIVE, ((t - 950L) / 125f).coerceIn(0f, 1f))
            trustedMask && t < 1_550L -> Quad(EnergyAnchor.PHOTO_PATH, EnergyAnchor.FISH_CONTOUR,
                EnergyFeedback.ABSORB, ((t - 1_075L) / 475f).coerceIn(0f, 1f))
            trustedMask && t in 2_800L until 3_180L -> Quad(EnergyAnchor.FISH_CONTOUR, EnergyAnchor.ORB_CORE,
                EnergyFeedback.RETURN, ((t - 2_800L) / 380f).coerceIn(0f, 1f))
            trustedMask && t in 3_180L until 3_350L -> Quad(EnergyAnchor.ORB_CORE, EnergyAnchor.REVEAL_TEXT,
                EnergyFeedback.TRANSLATE, ((t - 3_180L) / 170f).coerceIn(0f, 1f))
            trustedMask && t in 3_350L until 3_480L -> Quad(EnergyAnchor.REVEAL_TEXT, EnergyAnchor.REVEAL_TEXT,
                EnergyFeedback.REVEAL, ((t - 3_350L) / 130f).coerceIn(0f, 1f))
            trustedMask && t <= 3_650L -> Quad(EnergyAnchor.REVEAL_TEXT, EnergyAnchor.RESIDUE,
                EnergyFeedback.SETTLE, ((t - 3_480L) / 170f).coerceIn(0f, 1f))
            else -> return null
        }
        return EnergyEvent(frame.sessionId, photo, from, to, feedback, level,
            t, frame.geometryVersion, frame.attemptId, frame.imageId, frame.photoContentVersion,
            pathId = when (feedback) {
                EnergyFeedback.RELEASE -> "orb-to-photo"
                EnergyFeedback.ARRIVE, EnergyFeedback.ABSORB -> "photo-to-fish"
                EnergyFeedback.RETURN -> "fish-to-original-orb"
                EnergyFeedback.TRANSLATE, EnergyFeedback.REVEAL -> "orb-to-species-glyph"
                EnergyFeedback.SETTLE -> "glyph-to-residue"
            })
    }

    private data class Quad(
        val from: EnergyAnchor, val to: EnergyAnchor, val feedback: EnergyFeedback, val level: Float,
    )
}
