package com.yujian.ai.ui.waterlight.renderer

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Rendering role is immutable: R06 paths are resident, task paths live in WaterLightPathLedger. */
enum class OrbFilamentRole { RESIDENT_AMBIENT, TASK }

enum class OrbResidentFilamentLifecycle { OFF, WAKE_IN, ACTIVE, DISSOLVE, DISPOSED }

data class OrbResidentFilamentState(
    val lifecycle: OrbResidentFilamentLifecycle,
    val residentAlpha: Float,
    val wakeInAlpha: Float,
    val dissolveProgress: Float,
) {
    val effectiveAlpha: Float
        get() = (residentAlpha * wakeInAlpha * (1f - dissolveProgress)).coerceIn(0f, 1f)
}

data class OrbResidentMotionParameters(
    val phase: Float,
    val periodMs: Long,
    val direction: Float,
    val deformationPhase: Float,
    val motionAmplitudeD: Float,
)

/** Deterministic scene-clock motion for the five frozen R06 ambient paths. */
object OrbResidentFilamentMotionV1 {
    val PATH_IDS: Set<String> = setOf("G01", "G02", "A01", "A02", "C01")

    fun role(pathId: String): OrbFilamentRole =
        if (pathId in PATH_IDS) OrbFilamentRole.RESIDENT_AMBIENT else OrbFilamentRole.TASK

    fun parameters(spec: OrbFilamentSpecV1): OrbResidentMotionParameters = OrbResidentMotionParameters(
        phase = spec.phase,
        periodMs = spec.periodMs.coerceAtLeast(1L),
        direction = when (spec.id) {
            "G01", "A01", "C01" -> 1f
            "G02", "A02" -> -1f
            else -> 1f
        },
        deformationPhase = spec.phase,
        motionAmplitudeD = spec.deformationAmplitudeD.coerceAtLeast(0f),
    )

    fun lifecycle(
        stage: RecognitionSceneStage,
        stageProgress: Float,
        orbToGlyphAccepted: Boolean,
    ): OrbResidentFilamentState = when (stage) {
        RecognitionSceneStage.PHOTO_STABLE -> state(OrbResidentFilamentLifecycle.OFF, 0f, 0f, 0f)
        RecognitionSceneStage.ORB_WAKE -> state(
            OrbResidentFilamentLifecycle.WAKE_IN,
            residentAlpha = 1f,
            wakeInAlpha = smoothstep(stageProgress),
            dissolveProgress = 0f,
        )
        RecognitionSceneStage.ORB_RELEASE,
        RecognitionSceneStage.PERIMETER_ARRIVAL,
        RecognitionSceneStage.PATH_TO_FISH,
        RecognitionSceneStage.CONTOUR_A,
        RecognitionSceneStage.CONTOUR_B,
        RecognitionSceneStage.CONTOUR_C,
        RecognitionSceneStage.FISH_GOLD_PEAK,
        RecognitionSceneStage.ORB_RETURN,
        RecognitionSceneStage.ORB_TO_NAME -> state(OrbResidentFilamentLifecycle.ACTIVE, 1f, 1f, 0f)
        RecognitionSceneStage.SPECIES_NAME_REVEAL -> if (orbToGlyphAccepted) {
            state(OrbResidentFilamentLifecycle.DISSOLVE, 1f, 1f, smoothstep(stageProgress))
        } else state(OrbResidentFilamentLifecycle.ACTIVE, 1f, 1f, 0f)
        RecognitionSceneStage.RESIDUE_SETTLE -> if (orbToGlyphAccepted) {
            state(OrbResidentFilamentLifecycle.DISPOSED, 0f, 1f, 1f)
        } else state(OrbResidentFilamentLifecycle.ACTIVE, 1f, 1f, 0f)
    }

    /** Rotates each frozen irregular curve around the Core while keeping its radial profile. */
    fun transformLut(
        spec: OrbFilamentSpecV1,
        lut: OrbFilamentPathMathV1.ArcLengthLut,
        sceneElapsedMs: Long,
        reducedMotion: Boolean,
    ) {
        val p = parameters(spec)
        val seconds = if (reducedMotion) 0.0 else sceneElapsedMs.coerceAtLeast(0L) / 1000.0
        val cycles = if (reducedMotion) 0.0 else seconds * 1000.0 / p.periodMs + p.phase
        val rotationPhase = (2.0 * PI * cycles).toFloat()
        for (index in lut.x.indices) {
            val x = lut.x[index]
            val y = lut.y[index]
            val radius = sqrt(x * x + y * y)
            val normalizedRadius = (radius / spec.maxOuterRadiusD.coerceAtLeast(.001f)).coerceIn(0f, 1f)
            // The core turns slightly faster than the edge, producing liquid shear rather than a ring.
            val radialShear = 1f - .22f * normalizedRadius * normalizedRadius
            val angle = rotationPhase * p.direction * radialShear
            val c = if (reducedMotion) 1f else cos(angle)
            val s = if (reducedMotion) 0f else sin(angle)
            val deformation = if (reducedMotion) 0f else {
                sin((2.0 * PI * (seconds * 1000.0 / spec.deformationPeriodMs + p.deformationPhase + index.toFloat() / lut.x.size)).toFloat()) *
                    p.motionAmplitudeD * .22f * normalizedRadius
            }
            val rotatedRadius = (radius + deformation).coerceAtLeast(0f)
            val theta = kotlin.math.atan2(y, x)
            lut.x[index] = rotatedRadius * cos(theta + angle)
            lut.y[index] = rotatedRadius * sin(theta + angle)
            if (index > 0) {
                val dx = lut.x[index] - lut.x[index - 1]
                val dy = lut.y[index] - lut.y[index - 1]
                lut.cumulativeD[index] = lut.cumulativeD[index - 1] + sqrt(dx * dx + dy * dy)
            } else lut.cumulativeD[index] = 0f
        }
    }

    private fun state(
        lifecycle: OrbResidentFilamentLifecycle,
        residentAlpha: Float,
        wakeInAlpha: Float,
        dissolveProgress: Float,
    ) = OrbResidentFilamentState(
        lifecycle,
        residentAlpha.coerceIn(0f, 1f),
        wakeInAlpha.coerceIn(0f, 1f),
        dissolveProgress.coerceIn(0f, 1f),
    )

    private fun smoothstep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

data class OrbCoreRotationSample(
    val phaseRadians: Float,
    val angularVelocityRadiansPerSecond: Float,
    val radiusNormalized: Float,
    val maxFlowDisplacementD: Float,
)

/** One production profile for R03; rotation phase is always projected from WaterLightFrame time. */
object OrbCoreRotationProfileV1 {
    const val ENABLED = true
    const val DIRECTION = 1f
    const val PERIOD_MS = 7_000L
    val CENTER_ANGULAR_VELOCITY_RAD_S = (2.0 * PI / (PERIOD_MS / 1000.0)).toFloat()
    val EDGE_ANGULAR_VELOCITY_RAD_S = CENTER_ANGULAR_VELOCITY_RAD_S * .72f
    const val RADIAL_FALLOFF = 1.6f
    const val ROTATION_STRENGTH = 1f
    const val MAX_ROTATION_DISPLACEMENT_D = OrbStage02MaxVisualProfile.FLUID_DISPLACEMENT_MAX_D
}

object OrbCoreRotationFieldV1 {
    const val ENABLED = OrbCoreRotationProfileV1.ENABLED
    const val DIRECTION = OrbCoreRotationProfileV1.DIRECTION
    const val PERIOD_MS = OrbCoreRotationProfileV1.PERIOD_MS
    val CENTER_ANGULAR_VELOCITY_RAD_S = OrbCoreRotationProfileV1.CENTER_ANGULAR_VELOCITY_RAD_S
    val EDGE_ANGULAR_VELOCITY_RAD_S = OrbCoreRotationProfileV1.EDGE_ANGULAR_VELOCITY_RAD_S
    const val RADIAL_FALLOFF = OrbCoreRotationProfileV1.RADIAL_FALLOFF
    const val ROTATION_STRENGTH = OrbCoreRotationProfileV1.ROTATION_STRENGTH
    const val MAX_ROTATION_DISPLACEMENT_D = OrbCoreRotationProfileV1.MAX_ROTATION_DISPLACEMENT_D

    fun sample(sceneElapsedMs: Long, radiusNormalized: Float, reducedMotion: Boolean): OrbCoreRotationSample {
        val r = radiusNormalized.coerceIn(0f, 1f)
        val radialProfile = r.pow(RADIAL_FALLOFF)
        val velocity = CENTER_ANGULAR_VELOCITY_RAD_S +
            (EDGE_ANGULAR_VELOCITY_RAD_S - CENTER_ANGULAR_VELOCITY_RAD_S) * radialProfile
        val edgeFade = 1f - OrbInteriorOpticalField.smoothstep((r - .78f) / (1f - .78f))
        val effectiveVelocity = velocity * edgeFade
        val phase = if (!ENABLED || reducedMotion) 0f else {
            (sceneElapsedMs.coerceAtLeast(0L) / 1000f * effectiveVelocity * DIRECTION * ROTATION_STRENGTH) % (2f * PI.toFloat())
        }
        return OrbCoreRotationSample(
            phaseRadians = phase,
            angularVelocityRadiansPerSecond = if (reducedMotion || !ENABLED) 0f else effectiveVelocity * DIRECTION,
            radiusNormalized = r,
            maxFlowDisplacementD = MAX_ROTATION_DISPLACEMENT_D,
        )
    }

    /** Rotational inverse sample preserves radius, so it creates angular motion without edge expansion. */
    fun inverseSample(u: Float, v: Float, sceneElapsedMs: Long, reducedMotion: Boolean): Pair<Float, Float> {
        val dx = u - .5f
        val dy = v - .5f
        val radius = sqrt(dx * dx + dy * dy)
        if (radius <= 1e-6f || reducedMotion || !ENABLED) return u to v
        val normalized = (radius / .5f).coerceIn(0f, 1f)
        val radialWeight = 1f - OrbInteriorOpticalField.smoothstep(normalized.pow(RADIAL_FALLOFF))
        val center = CENTER_ANGULAR_VELOCITY_RAD_S
        val edge = EDGE_ANGULAR_VELOCITY_RAD_S
        val omega = edge + (center - edge) * radialWeight
        val edgeFade = 1f - OrbInteriorOpticalField.smoothstep((normalized - .78f) / (1f - .78f))
        val angle = -(sceneElapsedMs.coerceAtLeast(0L) / 1000f) * omega * edgeFade * DIRECTION * ROTATION_STRENGTH
        val c = cos(angle)
        val s = sin(angle)
        return (.5f + dx * c - dy * s) to (.5f + dx * s + dy * c)
    }

    private fun Float.pow(power: Float): Float = kotlin.math.exp(kotlin.math.ln(coerceAtLeast(1e-8f)) * power)
}

/** Rear paths may transmit whenever the independent resident system overlaps a visible Core. */
object OrbResidentTransmissionFieldV1 {
    val REAR_PATH_IDS: Set<String> = setOf("G01", "A01")

    fun enabled(state: OrbResidentFilamentState, coreVisible: Boolean): Boolean =
        coreVisible && state.effectiveAlpha > 0f &&
            state.lifecycle in setOf(OrbResidentFilamentLifecycle.WAKE_IN, OrbResidentFilamentLifecycle.ACTIVE, OrbResidentFilamentLifecycle.DISSOLVE)

    fun sampleFactor(pathId: String, rawCoreAlpha: Float, edgeFade: Float, state: OrbResidentFilamentState): Float {
        if (pathId !in REAR_PATH_IDS) return 0f
        return (rawCoreAlpha.coerceIn(0f, 1f) * edgeFade.coerceIn(0f, 1f) * state.effectiveAlpha).coerceIn(0f, 1f)
    }
}
