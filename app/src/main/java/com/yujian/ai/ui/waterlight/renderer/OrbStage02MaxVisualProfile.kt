package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max

/** Selects the frozen pre-V3 rendering or the high-visibility Stage02 candidate. */
enum class OrbStage02VisualProfile {
    BASELINE,
    MAX;

    companion object {
        fun currentBuildDefault(): OrbStage02VisualProfile =
            if (com.yujian.ai.BuildConfig.DEBUG) MAX else BASELINE
    }
}

/** Stage02 MAX candidate values. These are runtime parameters; source assets remain frozen. */
internal object OrbStage02MaxVisualProfile {
    const val BASELINE_SHADER_OVERLAY_ALPHA = 0.25f
    const val FLUID_DISPLACEMENT_MAX_D = 0.018f
    const val FLUID_INTERIOR_MIX_MAX = 0.92f
    const val FLUID_WAKE_START_MS = 80f
    const val FLUID_WAKE_FULL_MS = 280f
    const val FLUID_WAKE_PHASE_CYCLES = 0.65f
    const val FLUID_EDGE_PROTECTION_START_D = 0.34f
    const val FLUID_EDGE_PROTECTION_END_D = 0.47f
    const val FLUID_OUTLINE_DISPLACEMENT_D = 0f
    const val GLASS_BACK_TRANSMISSION_MAX = 0.80f
    const val GLASS_BACK_REFRACTION_MAX_D = 0.006f
    const val GLASS_BACK_SOFTEN_MAX_DP = 0.65f
    const val R06_FRONT_BODY_ALPHA_TARGET = 0.45f
    const val R06_BACK_BODY_ALPHA_TARGET = 0.38f
    const val R06_BODY_STROKE_GAIN_MAX = 1.35f
    const val STAGE03_TRANSITION_MS = 80f
}

/** Stateless shared Stage02 field used by the fluid shader and the R06 glass pass. */
internal object OrbInteriorOpticalField {
    data class Frame(
        val wakeStrength: Float,
        val maxDisplacementD: Float,
        val refractionStrength: Float,
        val interiorMix: Float,
        val semanticPhase: Float,
        val enabled: Boolean,
        val baselineOpticalMix: Float = 0f,
    )

    data class Vector(val x: Float, val y: Float)

    fun maxFluidWakeEnvelope(stage: RecognitionSceneStage, stageProgress: Float): Float {
        val p = stageProgress.coerceIn(0f, 1f)
        return when (stage) {
            RecognitionSceneStage.ORB_WAKE -> {
                val localMs = p * RecognitionSceneStage.ORB_WAKE.minimumVisibleMs
                smoothstep((localMs - OrbStage02MaxVisualProfile.FLUID_WAKE_START_MS) /
                    (OrbStage02MaxVisualProfile.FLUID_WAKE_FULL_MS - OrbStage02MaxVisualProfile.FLUID_WAKE_START_MS))
            }
            RecognitionSceneStage.ORB_RELEASE -> {
                val localMs = p * RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs
                1f - smoothstep(localMs / OrbStage02MaxVisualProfile.STAGE03_TRANSITION_MS)
            }
            else -> 0f
        }
    }

    fun maxFluidShaderActive(stage: RecognitionSceneStage, stageProgress: Float): Boolean = when (stage) {
        RecognitionSceneStage.ORB_WAKE -> true
        RecognitionSceneStage.ORB_RELEASE ->
            stageProgress.coerceIn(0f, 1f) <=
                OrbStage02MaxVisualProfile.STAGE03_TRANSITION_MS /
                    RecognitionSceneStage.ORB_RELEASE.minimumVisibleMs.toFloat()
        else -> false
    }

    fun shaderOverlayAlpha(
        profile: OrbStage02VisualProfile,
        stage: RecognitionSceneStage,
        stageProgress: Float,
        baselineOptics: Float,
    ): Float = if (profile == OrbStage02VisualProfile.MAX &&
        (stage == RecognitionSceneStage.ORB_WAKE || stage == RecognitionSceneStage.ORB_RELEASE)
    ) {
        OrbStage02MaxVisualProfile.BASELINE_SHADER_OVERLAY_ALPHA +
            (1f - OrbStage02MaxVisualProfile.BASELINE_SHADER_OVERLAY_ALPHA) * maxFluidWakeEnvelope(stage, stageProgress)
    } else baselineOptics.coerceIn(0f, 1f) * OrbStage02MaxVisualProfile.BASELINE_SHADER_OVERLAY_ALPHA

    fun frame(
        stage: RecognitionSceneStage,
        stageProgress: Float,
        reducedMotion: Boolean,
        profile: OrbStage02VisualProfile,
    ): Frame {
        if (profile != OrbStage02VisualProfile.MAX) return Frame(0f, 0f, 0f, 0f, 0f, false)
        val wake = maxFluidWakeEnvelope(stage, stageProgress)
        val displacement = if (reducedMotion) 0f else OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D * wake
        val phase = if (reducedMotion) 0f else (2f * PI.toFloat() *
            OrbStage02MaxVisualProfile.FLUID_WAKE_PHASE_CYCLES * wake)
        return Frame(
            wakeStrength = wake,
            maxDisplacementD = displacement,
            refractionStrength = wake,
            interiorMix = OrbStage02MaxVisualProfile.FLUID_INTERIOR_MIX_MAX * wake,
            semanticPhase = phase,
            enabled = wake > 0f,
            baselineOpticalMix = if (stage == RecognitionSceneStage.ORB_RELEASE) 1f - wake else 0f,
        )
    }

    /** Same normalized wake-flow vector is mirrored in the AGSL fluid program. */
    fun flowDirectionAt(u: Float, v: Float, semanticPhase: Float): Vector {
        return Vector(flowXAt(u, v, semanticPhase), flowYAt(u, v, semanticPhase))
    }

    fun flowXAt(u: Float, v: Float, semanticPhase: Float): Float {
        val x = sin(v * 5.2f + semanticPhase)
        val y = cos(u * 4.2f - semanticPhase)
        return x / sqrt(x * x + y * y).coerceAtLeast(1e-5f)
    }

    fun flowYAt(u: Float, v: Float, semanticPhase: Float): Float {
        val x = sin(v * 5.2f + semanticPhase)
        val y = cos(u * 4.2f - semanticPhase)
        return y / sqrt(x * x + y * y).coerceAtLeast(1e-5f)
    }

    /** Kotlin mirror of the AGSL liquidFlow + semantic Wake Flow blend for R06/caustics. */
    fun sharedFlowDirectionAt(
        u: Float,
        v: Float,
        semanticPhase: Float,
        sceneElapsedMs: Long,
    ): Vector = FloatArray(2).let { scratch ->
        writeSharedFlowDirectionAt(u, v, semanticPhase, sceneElapsedMs, scratch, 0)
        Vector(scratch[0], scratch[1])
    }

    fun writeSharedFlowDirectionAt(
        u: Float,
        v: Float,
        semanticPhase: Float,
        sceneElapsedMs: Long,
        output: FloatArray,
        offset: Int,
    ) {
        val profile = OrbFluidOpticalProfileV1
        val px = (u - .5f) * 6f * profile.FLOW_SCALE_A
        val py = (v - .5f) * 4.6f * profile.FLOW_SCALE_A
        val seconds = sceneElapsedMs.coerceAtLeast(0L) / 1000f
        val tA = seconds * profile.FLOW_TIME_RATE_A
        val tB = seconds * profile.FLOW_TIME_RATE_B
        val warpX = (sin(py * .73f + tA) + .35f * sin((px + py) * .41f - tB)) * profile.FLOW_DOMAIN_WARP
        val warpY = (cos(px * .67f - tB) + .30f * cos((py - px) * .38f + tA)) * profile.FLOW_DOMAIN_WARP
        val qx = px + warpX
        val qy = py + warpY
        val fieldAx = sin(qy * 1.17f + tA) + .25f * sin((qx + qy) * .63f - tB)
        val fieldAy = cos(qx * .91f - tB) + .25f * cos((qy - qx) * .58f + tA)
        val qBx = qx * (profile.FLOW_SCALE_B / max(profile.FLOW_SCALE_A, .001f)) + 1.31f
        val qBy = qy * (profile.FLOW_SCALE_B / max(profile.FLOW_SCALE_A, .001f)) - .82f
        val fieldBx = sin(qBy * .79f + tB) + .18f * cos(qBx * .54f - tA)
        val fieldBy = cos(qBx * .72f - tA) + .18f * sin(qBy * .49f + tB)
        val ambientX = fieldAx * .62f + fieldBx * .38f
        val ambientY = fieldAy * .62f + fieldBy * .38f
        val ambientLength = sqrt(ambientX * ambientX + ambientY * ambientY)
        val boundedAmbientX = if (ambientLength > 1f) ambientX / ambientLength else ambientX
        val boundedAmbientY = if (ambientLength > 1f) ambientY / ambientLength else ambientY
        val sharedX = boundedAmbientX * .35f + flowXAt(u, v, semanticPhase) * .65f
        val sharedY = boundedAmbientY * .35f + flowYAt(u, v, semanticPhase) * .65f
        val sharedLength = sqrt(sharedX * sharedX + sharedY * sharedY)
        val scale = if (sharedLength > 1f) 1f / sharedLength else 1f
        output[offset] = sharedX * scale
        output[offset + 1] = sharedY * scale
    }

    fun interiorWeight(radiusD: Float): Float {
        val start = OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_START_D
        val end = OrbStage02MaxVisualProfile.FLUID_EDGE_PROTECTION_END_D
        return 1f - smoothstep((radiusD - start) / (end - start))
    }

    fun smoothstep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

/** Optional Debug isolation of the Stage02 optical components. */
enum class OrbStage02LayerIsolation { R03_ONLY, R06_ONLY, R03_R06, R06_BACK_TRANSMISSION_ONLY }
