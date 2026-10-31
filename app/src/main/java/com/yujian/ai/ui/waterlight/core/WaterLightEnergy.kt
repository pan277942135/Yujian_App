package com.yujian.ai.ui.waterlight.core

/** Visual energy is sampled from the existing recognition clock and current photo identity. */
enum class EnergyAnchor { ORB_CORE, ORB_RIBBON, PHOTO_PERIMETER, PHOTO_PATH, FISH_CONTOUR, REVEAL_TEXT }
enum class EnergyFeedback { RELEASE, ARRIVE, ABSORB, RETURN, SETTLE }

data class EnergyEvent(
    val sessionId: String,
    val photoSha256: String,
    val source: EnergyAnchor,
    val target: EnergyAnchor,
    val feedback: EnergyFeedback,
    val level: Float,
    val referenceMs: Long,
    val geometryVersion: Long,
)

object WaterLightEnergy {
    /** No fish-target event can be emitted until a real mask is trusted for this frame. */
    fun at(frame: WaterLightFrame, trustedMask: Boolean): EnergyEvent? {
        val photo = frame.photoSha256?.takeIf(String::isNotBlank) ?: return null
        if (frame.sessionId.isBlank()) return null
        val t = frame.opticalReferenceElapsedMs
        val (from, to, feedback, level) = when {
            t < 350L -> return null
            t < 950L -> Quad(EnergyAnchor.ORB_RIBBON, EnergyAnchor.PHOTO_PERIMETER,
                EnergyFeedback.RELEASE, ((t - 350L) / 600f).coerceIn(0f, 1f))
            t < 1200L -> Quad(EnergyAnchor.PHOTO_PERIMETER, EnergyAnchor.PHOTO_PATH,
                EnergyFeedback.ARRIVE, ((t - 950L) / 250f).coerceIn(0f, 1f))
            trustedMask && t < 2800L -> Quad(EnergyAnchor.PHOTO_PATH, EnergyAnchor.FISH_CONTOUR,
                EnergyFeedback.ABSORB, ((t - 1200L) / 350f).coerceIn(0f, 1f))
            else -> return null
        }
        return EnergyEvent(frame.sessionId, photo, from, to, feedback, level,
            t, frame.geometryVersion)
    }

    private data class Quad(
        val from: EnergyAnchor, val to: EnergyAnchor, val feedback: EnergyFeedback, val level: Float,
    )
}
