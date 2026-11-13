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
) {
    fun rejectionReason(frame: WaterLightFrame, geometry: WaterLightHostGeometry): String? = when {
        geometry.host != host -> "HOST_MISMATCH"
        material.host != host -> "MATERIAL_HOST_MISMATCH"
        geometry.version != frame.geometryVersion -> "GEOMETRY_VERSION_MISMATCH"
        !geometry.valid -> "INVALID_ORB_BOUNDS"
        !engine.accepts(frame) -> "SESSION_FRAME_MISMATCH"
        else -> null
    }

    fun accepts(frame: WaterLightFrame, geometry: WaterLightHostGeometry): Boolean =
        rejectionReason(frame, geometry) == null
}
