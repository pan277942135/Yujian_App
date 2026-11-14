package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.sin

/** Pure projections from the shared scene frame and its semantic stage-local progress. */
internal object OrbWakeBreathReleaseV11 {
    private const val S2_TRANSITION_LOCAL_MS = 450f
    private const val RELEASE_RETURN_PROGRESS = 34f / 60f
    private const val R06_WAKE_ACTIVE_NORMALIZATION = .45f
    const val R06_ALPHA_GAIN = 1.28f
    const val R06_ALPHA_CAP = .82f
    const val R06_MOVING_HIGHLIGHT_GAIN = 1.15f
    const val R06_RELEASE_HEAD_BRIGHTNESS_GAIN = 1.18f
    const val R06_MAX_EXTENSION_DP = 14f

    data class Wake(val optics: Float, val waterCore: Float, val grassLight: Float)
    enum class LocalRelease { AMBIENT, OUTWARD, RETURN }

    /** Smoothstep cubic-bezier equivalent with zero endpoint velocity. */
    fun smoothstep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** A deterministic readability envelope tied only to the shared stage-local progress. */
    internal fun r06WakeReadabilityEnvelope(
        stage: RecognitionSceneStage,
        stageProgress: Float,
    ): Float {
        val p = stageProgress.coerceIn(0f, 1f)
        return when (stage) {
            RecognitionSceneStage.ORB_WAKE -> {
                val localMs = p * RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
                smoothstep((localMs - 80f) / 200f)
            }
            RecognitionSceneStage.ORB_RELEASE -> {
                val localMs = p * RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs
                1f - smoothstep(localMs / 80f)
            }
            else -> 0f
        }
    }

    /** Adds a bounded amount to the two frozen body materials while preserving natural fades. */
    internal fun r06WakeBodyAlpha(
        stage: RecognitionSceneStage,
        stageProgress: Float,
        originalLayerAlpha: Float,
        layerId: String,
        front: Boolean,
        active: Float,
        endFade: Float,
        tailAlpha: Float,
        profileOpacity: Float,
        crossOpacity: Float,
    ): Float {
        if (layerId != "translucent_body" && layerId != "fine_filament") return originalLayerAlpha
        val envelope = r06WakeReadabilityEnvelope(stage, stageProgress)
        if (envelope <= 0f || active <= 0f || endFade <= 0f) return originalLayerAlpha

        val readabilityCap = if (front) .12f else .08f
        val activeWeight = (active / R06_WAKE_ACTIVE_NORMALIZATION).coerceIn(0f, 1f)
        val naturalShapeWeight = sqrt(
            (tailAlpha.coerceIn(0f, 1f) *
                profileOpacity.coerceIn(0f, 1f) *
                crossOpacity.coerceIn(0f, 1f)).coerceIn(0f, 1f),
        )
        val readabilityContribution = readabilityCap * envelope * activeWeight *
            endFade.coerceIn(0f, 1f) * naturalShapeWeight
        return maxOf(originalLayerAlpha, readabilityContribution)
            .coerceIn(0f, R06_ALPHA_CAP)
    }

    /** Frozen ease-out cubic-bezier(0.16, 1.0, 0.30, 1.0), solved for x. */
    fun wakeEase(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        // Preserve the frozen keyframe boundaries exactly; bisection would leave
        // a tiny positive residue for x=0 and break the 80/220 ms start frames.
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        repeat(18) {
            val t = (lo + hi) * .5f
            val bx = cubic(t, .16f, .30f)
            if (bx < x) lo = t else hi = t
        }
        val t = (lo + hi) * .5f
        return cubic(t, 1f, 1f)
    }

    private fun cubic(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
    }

    fun wake(stage: RecognitionSceneStage, progress: Float): Wake {
        val p = progress.coerceIn(0f, 1f)
        if (stage == RecognitionSceneStage.PHOTO_STABLE) return Wake(0f, 0f, 0f)
        if (stage != RecognitionSceneStage.ORB_WAKE) return Wake(1f, 1f, 1f)
        val localMs = p * RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
        val optics = if (localMs < 80f) 0f else wakeEase(
            (localMs - 80f) / (RecognitionSceneStage.ORB_WAKE.minimumVisibleMs - 80f),
        )
        val water = if (localMs < 220f) 0f else wakeEase(
            (localMs - 220f) / (RecognitionSceneStage.ORB_WAKE.minimumVisibleMs - 220f),
        )
        return Wake(optics, water, water)
    }

    fun s2Progress(stage: RecognitionSceneStage, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val localSceneMs = when (stage) {
            RecognitionSceneStage.PHOTO_STABLE -> 0f
            RecognitionSceneStage.ORB_WAKE -> p * RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
            RecognitionSceneStage.ORB_RELEASE -> RecognitionSceneStage.ORB_WAKE.minimumVisibleMs +
                p * RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs
            else -> S2_TRANSITION_LOCAL_MS
        }
        return smoothstep(localSceneMs / S2_TRANSITION_LOCAL_MS)
    }

    fun breathScale(
        sceneElapsedMs: Long,
        stage: RecognitionSceneStage,
        stageProgress: Float,
        reducedMotion: Boolean,
    ): Float {
        if (reducedMotion) return 1f
        val p = s2Progress(stage, stageProgress)
        val amplitude = .008f + (.018f - .008f) * p
        val periodMs = 6000f + (4800f - 6000f) * p
        val phase = 2f * PI.toFloat() * sceneElapsedMs.coerceAtLeast(0L) / periodMs
        return 1f + amplitude * sin(phase)
    }

    fun localRelease(stage: RecognitionSceneStage, progress: Float): LocalRelease = when {
        stage != RecognitionSceneStage.ORB_RELEASE -> LocalRelease.AMBIENT
        progress.coerceIn(0f, 1f) < RELEASE_RETURN_PROGRESS -> LocalRelease.OUTWARD
        else -> LocalRelease.RETURN
    }

    fun releaseOffset(stage: RecognitionSceneStage, progress: Float, peakArcFraction: Float): Float {
        if (stage != RecognitionSceneStage.ORB_RELEASE) return 0f
        val p = progress.coerceIn(0f, 1f)
        return if (p < RELEASE_RETURN_PROGRESS) peakArcFraction * smoothstep(p / RELEASE_RETURN_PROGRESS)
        else peakArcFraction * (1f - smoothstep((p - RELEASE_RETURN_PROGRESS) / (1f - RELEASE_RETURN_PROGRESS)))
    }

    fun returnDirectionProgress(stage: RecognitionSceneStage, progress: Float): Float {
        if (stage != RecognitionSceneStage.ORB_RELEASE) return 0f
        return smoothstep((progress.coerceIn(0f, 1f) - RELEASE_RETURN_PROGRESS) /
            (1f - RELEASE_RETURN_PROGRESS))
    }
}
