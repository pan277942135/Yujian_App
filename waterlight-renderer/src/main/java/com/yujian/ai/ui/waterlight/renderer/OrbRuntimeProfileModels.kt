package com.yujian.ai.ui.waterlight.renderer

/** Immutable, generated projection of the AI Orb R02-R06 design contracts. */
data class OrbPointV1(val x: Float, val y: Float)
data class OrbCubicV1(val c1: OrbPointV1, val c2: OrbPointV1, val end: OrbPointV1)
data class OrbCurvePointV1(val u: Float, val value: Float)

data class OrbDropletSpecV1(
    val id: String,
    val u: Float,
    val v: Float,
    val diameterDpAt80: Float,
    val bodyAlpha: Float,
    val rimAlpha: Float,
    val periodMs: Long,
    val phase: Float,
)

data class OrbMaterialLayerV1(
    val id: String,
    val widthMultiplier: Float,
    val alphaMax: Float,
    val blurSigmaDp: Float?,
    val color: Long,
    val offsetNormalFraction: Float?,
    val lengthFraction: Float?,
    val travelsWithHead: Boolean,
)

data class OrbFilamentMaterialV1(
    val id: String,
    val baseColor: Long,
    val highlightColor: Long,
    val refractionColor: Long,
    val widthMultiplier: Float,
    val layers: List<OrbMaterialLayerV1>,
    val crossSection: List<OrbCurvePointV1>,
    val widthProfile: List<OrbCurvePointV1>,
    val opacityProfile: List<OrbCurvePointV1>,
    val headLuminance: Float,
    val bodyLuminance: Float,
    val tailLuminance: Float,
    val specularShape: String,
    val specularHeadRadiusDpMin: Float,
    val specularHeadRadiusDpMax: Float,
    val specularHeadAlpha: Float,
    val specularBandLengthFraction: Float,
    val featherSigmaDp: Float,
    val featherAlphaMax: Float,
    val featherColor: Long,
    val trailLengthFraction: Float,
    val deformationAmplitudeD: Float,
)

data class OrbFilamentSpecV1(
    val id: String,
    val material: String,
    val front: Boolean,
    val start: OrbPointV1,
    val segments: List<OrbCubicV1>,
    val pathLengthD: Float,
    val maxRadiusD: Float,
    val maxOuterRadiusD: Float,
    val periodMs: Long,
    val phase: Float,
    val widthMinDp: Float,
    val widthMaxDp: Float,
    val alphaMin: Float,
    val alphaMax: Float,
    val normalAmplitudeD: Float,
    val deformationPeriodMs: Long,
    val deformationAmplitudeD: Float,
)

data class OrbCausticLayerV1(
    val id: String,
    val periodMs: Long,
    val frequency: Float,
    val alpha: Float,
    val advectionXDps: Float,
    val advectionYDps: Float,
)

