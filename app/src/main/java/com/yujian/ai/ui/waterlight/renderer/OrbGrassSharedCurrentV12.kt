package com.yujian.ai.ui.waterlight.renderer

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Pure, seekable shared-water-current projection used by the V1.2 mask warp. */
internal object OrbGrassSharedCurrentV12 {
    fun sharedLowFrequency(uvX: Float, uvY: Float, sceneElapsedMs: Long): Float {
        val seconds = sceneElapsedMs.coerceAtLeast(0L) / 1000f
        return sin(
            seconds * OrbFluidOpticalProfileV1.GRASS_SHARED_ANGULAR_FREQUENCY +
                uvX * OrbFluidOpticalProfileV1.GRASS_SHARED_X_SCALE +
                uvY * OrbFluidOpticalProfileV1.GRASS_SHARED_Y_SCALE,
        )
    }

    fun displacementWave(
        uvX: Float,
        uvY: Float,
        sceneElapsedMs: Long,
        periodMs: Long,
        phaseDegrees: Float,
    ): Float {
        val seconds = sceneElapsedMs.coerceAtLeast(0L) / 1000f
        val phase = phaseDegrees * (PI.toFloat() / 180f)
        val periodSeconds = periodMs.coerceAtLeast(1L) / 1000f
        val local = sin(2f * PI.toFloat() * seconds / periodSeconds + phase)
        val shared = sharedLowFrequency(uvX, uvY, sceneElapsedMs)
        val flutter = sin(
            seconds * OrbFluidOpticalProfileV1.GRASS_SHARED_ANGULAR_FREQUENCY *
                OrbFluidOpticalProfileV1.GRASS_SECONDARY_FREQUENCY_MULTIPLIER +
                uvY * OrbFluidOpticalProfileV1.GRASS_FLUTTER_Y_SCALE +
                phase * OrbFluidOpticalProfileV1.GRASS_FLUTTER_PHASE_SCALE,
        )
        return (
            local * OrbFluidOpticalProfileV1.GRASS_LOCAL_WEIGHT +
                shared * OrbFluidOpticalProfileV1.GRASS_SHARED_WEIGHT +
                flutter * OrbFluidOpticalProfileV1.GRASS_FLUTTER_WEIGHT
            ).coerceIn(-1f, 1f)
    }

    /** Matches the V1.1 arc-length envelope: roots remain fixed while tips retain their V1.1 bound. */
    fun bendEnvelope(arcFraction: Float): Float {
        val s = arcFraction.coerceIn(0f, 1f)
        val t = ((s - 0.10f) / (1f - 0.10f)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return smooth.pow(1.6f)
    }

    fun displacementD(leaf: OrbGrassMotionProfileV11.Leaf, arcFraction: Float, sceneElapsedMs: Long): Float =
        leaf.amplitudeD * bendEnvelope(arcFraction) * displacementWave(
            uvX = leaf.tipU,
            uvY = leaf.tipV,
            sceneElapsedMs = sceneElapsedMs,
            periodMs = leaf.periodMs,
            phaseDegrees = leaf.phaseDegrees,
        )
}
