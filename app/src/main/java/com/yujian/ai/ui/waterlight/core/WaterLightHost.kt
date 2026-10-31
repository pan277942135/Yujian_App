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
    fun accepts(frame: WaterLightFrame, geometry: WaterLightHostGeometry): Boolean =
        geometry.host == host && geometry.version == frame.geometryVersion &&
            geometry.valid && engine.accepts(frame)
}
