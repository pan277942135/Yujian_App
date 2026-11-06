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
    fun rejectionReason(frame: WaterLightFrame, geometry: WaterLightHostGeometry): String? {
        when {
            geometry.host != host -> return "GEOMETRY_HOST_MISMATCH(expected=$host actual=${geometry.host})"
            material.host != host -> return "MATERIAL_HOST_MISMATCH(expected=$host actual=${material.host})"
            geometry.version != frame.geometryVersion ->
                return "FRAME_GEOMETRY_VERSION_MISMATCH(frame=${frame.geometryVersion} geometry=${geometry.version})"
            !geometry.valid -> return "INVALID_GEOMETRY(rect=${geometry.leftPx},${geometry.topPx},${geometry.rightPx},${geometry.bottomPx})"
        }
        return engine.rejectionReason(frame)?.let { "FRAME_IDENTITY_$it" }
    }

    fun accepts(frame: WaterLightFrame, geometry: WaterLightHostGeometry): Boolean =
        rejectionReason(frame, geometry) == null
}
