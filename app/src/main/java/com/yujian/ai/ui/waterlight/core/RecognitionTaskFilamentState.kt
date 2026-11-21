package com.yujian.ai.ui.waterlight.core

enum class RecognitionTaskFilamentPhase {
    IDLE,
    RELEASE,
    PHOTO_CONTACT,
    FISH_SEARCH,
    MASK_CONTACT,
    GOLD_PEAK,
    RETURN,
    MERGED,
}

/** Attempt-scoped task-path state. It never owns or mutates the five resident Orb paths. */
data class RecognitionTaskFilamentState(
    val phase: RecognitionTaskFilamentPhase,
    val taskId: String,
    val taskPathId: String,
    val pStarPathId: String?,
    val sessionId: String,
    val attemptId: String,
    val imageId: String,
    val maskSha256: String?,
    val maskVersion: Long?,
    val geometryVersion: Long,
    val pathGeometryVersion: Long,
    val eventSequence: Long,
    val releaseProgress: Float,
    val contactProgress: Float,
    val returnProgress: Float,
    val taskAlpha: Float,
) {
    fun matches(frame: WaterLightFrame): Boolean =
        sessionId == frame.sessionId && attemptId == frame.attemptId && imageId == frame.imageId &&
            frame.photoSha256?.isNotBlank() == true && geometryVersion == frame.geometryVersion &&
            taskPathId.isNotBlank() && taskPathId !in RESIDENT_PATH_IDS

    companion object {
        private val RESIDENT_PATH_IDS = setOf("G01", "G02", "A01", "A02", "C01")
        private val TASK_FEEDBACKS = setOf(
            EnergyFeedback.RELEASE,
            EnergyFeedback.ARRIVE,
            EnergyFeedback.ABSORB,
            EnergyFeedback.RETURN,
        )

        fun from(
            frame: WaterLightFrame,
            event: EnergyEvent?,
        ): RecognitionTaskFilamentState? {
            val current = event ?: return null
            if (!current.matches(frame) || current.stage != frame.sceneFrame.stage ||
                current.feedback !in TASK_FEEDBACKS || current.pathId.isNullOrBlank() ||
                current.pathId in RESIDENT_PATH_IDS || !current.level.isFinite() ||
                current.level !in 0f..1f
            ) return null
            return fromEvent(current, frame.sceneFrame.taskEnergyMerged)
        }

        fun fromEvent(
            event: EnergyEvent,
            taskEnergyMerged: Boolean = false,
        ): RecognitionTaskFilamentState? {
            val pathId = event.pathId?.takeIf { it.isNotBlank() && it !in RESIDENT_PATH_IDS } ?: return null
            if (event.feedback !in TASK_FEEDBACKS || !event.level.isFinite() || event.level !in 0f..1f ||
                event.sessionId.isBlank() || event.attemptId.isBlank() || event.imageId.isBlank() ||
                event.photoSha256.isBlank() || event.geometryVersion <= 0L || event.eventSequence < 0L
            ) return null

            val phase = if (taskEnergyMerged) RecognitionTaskFilamentPhase.MERGED else when (event.stage) {
                RecognitionSceneStage.ORB_RELEASE -> RecognitionTaskFilamentPhase.RELEASE
                RecognitionSceneStage.PERIMETER_ARRIVAL -> RecognitionTaskFilamentPhase.PHOTO_CONTACT
                RecognitionSceneStage.PATH_TO_FISH -> RecognitionTaskFilamentPhase.FISH_SEARCH
                RecognitionSceneStage.CONTOUR_A,
                RecognitionSceneStage.CONTOUR_B,
                RecognitionSceneStage.CONTOUR_C -> RecognitionTaskFilamentPhase.MASK_CONTACT
                RecognitionSceneStage.FISH_GOLD_PEAK -> RecognitionTaskFilamentPhase.GOLD_PEAK
                RecognitionSceneStage.ORB_RETURN -> RecognitionTaskFilamentPhase.RETURN
                else -> return null
            }
            val trustedPStarIdentity = event.pathMaskSha256?.matches(Regex("(?i)^[a-f0-9]{64}$")) == true &&
                event.pathMaskVersion != null && event.pathMaskVersion >= 0L &&
                event.pathGeometryVersion != null && event.pathGeometryVersion != 0L &&
                event.pathContactPointNormalizedSource?.let {
                    it.isValid() && it.x in 0f..1f && it.y in 0f..1f
                } == true
            val taskAlpha = if (phase == RecognitionTaskFilamentPhase.MERGED) 0f else event.level.coerceIn(0f, 1f)
            return RecognitionTaskFilamentState(
                phase = phase,
                taskId = "task:${event.sessionId}:${event.attemptId}:$pathId",
                taskPathId = pathId,
                pStarPathId = pathId.takeIf { trustedPStarIdentity },
                sessionId = event.sessionId,
                attemptId = event.attemptId,
                imageId = event.imageId,
                maskSha256 = event.pathMaskSha256,
                maskVersion = event.pathMaskVersion,
                geometryVersion = event.geometryVersion,
                pathGeometryVersion = event.pathGeometryVersion ?: event.geometryVersion,
                eventSequence = event.eventSequence,
                releaseProgress = if (phase == RecognitionTaskFilamentPhase.RELEASE) taskAlpha else 0f,
                contactProgress = if (phase in setOf(
                        RecognitionTaskFilamentPhase.PHOTO_CONTACT,
                        RecognitionTaskFilamentPhase.FISH_SEARCH,
                        RecognitionTaskFilamentPhase.MASK_CONTACT,
                        RecognitionTaskFilamentPhase.GOLD_PEAK,
                    )) taskAlpha else 0f,
                returnProgress = if (phase == RecognitionTaskFilamentPhase.RETURN) taskAlpha else 0f,
                taskAlpha = taskAlpha,
            )
        }
    }
}
