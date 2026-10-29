package com.yujian.ai.ui.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Geometry reported by the exact-source WebGL canvas in the reference browser. */
internal data class RecognitionPerimeterReferenceMetrics(
    val devicePixelRatio: Float,
    val cssWidthPx: Float,
    val cssHeightPx: Float,
    val backingWidthPx: Float,
    val backingHeightPx: Float,
) {
    val sourceDprCap: Float get() = min(devicePixelRatio, 2f)
    val sourceBackingScale: Float
        get() = (
            backingWidthPx / cssWidthPx + backingHeightPx / cssHeightPx
        ) * 0.5f

    init {
        require(devicePixelRatio.isFinite() && devicePixelRatio > 0f)
        require(cssWidthPx.isFinite() && cssWidthPx > 0f)
        require(cssHeightPx.isFinite() && cssHeightPx > 0f)
        require(backingWidthPx.isFinite() && backingWidthPx > 0f)
        require(backingHeightPx.isFinite() && backingHeightPx > 0f)
    }
}

/** One measured source-to-Android mapping; path CSS units and shader backing units share it. */
internal data class RecognitionPerimeterCoordinateScale(
    val shaderPxPerSourceUnit: Float,
    val pathPxPerCssUnit: Float,
    val sourceDprCap: Float,
    val isMeasured: Boolean,
)

internal data class RecognitionPerimeterPathPoint(
    val x: Float,
    val y: Float,
)

internal data class RecognitionPerimeterSparkle(
    val x: Float,
    val y: Float,
    val perimeterParam: Float,
    val active: Boolean,
)

/** Small CPU-only port of the source path sampler, kept separate from GLES lifecycle code. */
internal object RecognitionPerimeterShaderMath {
    const val SPREAD_DURATION_MS = 1_200L
    const val SPARKLE_COUNT = 16
    const val PATH_SUBDIVISIONS = 280
    const val MARGIN_RATIO = 0.015f

    fun linearProgress(elapsedMs: Long): Float =
        (elapsedMs.coerceAtLeast(0L).toFloat() / SPREAD_DURATION_MS).coerceIn(0f, 1f)

    fun sourceTimeSec(elapsedMs: Long, speed: Float = 1f): Float =
        elapsedMs.coerceAtLeast(0L) * 0.001f * speed

    fun coordinateScale(
        metrics: RecognitionPerimeterReferenceMetrics?,
        targetWidthPx: Float,
        targetHeightPx: Float,
    ): RecognitionPerimeterCoordinateScale {
        require(targetWidthPx.isFinite() && targetWidthPx > 0f)
        require(targetHeightPx.isFinite() && targetHeightPx > 0f)
        if (metrics == null) {
            // Candidate rendering remains usable, but this identity mapping is explicitly
            // unverified until the AI Studio browser DPR/CSS/backing-store tuple is recorded.
            return RecognitionPerimeterCoordinateScale(
                shaderPxPerSourceUnit = 1f,
                pathPxPerCssUnit = 1f,
                sourceDprCap = 1f,
                isMeasured = false,
            )
        }
        val scaleX = targetWidthPx / metrics.cssWidthPx
        val scaleY = targetHeightPx / metrics.cssHeightPx
        val relativeDifference = kotlin.math.abs(scaleX - scaleY) / maxOf(scaleX, scaleY)
        require(relativeDifference <= 0.01f) {
            "Source/Android photo bounds do not admit a uniform perimeter scale: " +
                "scaleX=$scaleX scaleY=$scaleY"
        }
        val pathScale = (scaleX + scaleY) * 0.5f
        val measuredBackingScaleX = metrics.backingWidthPx / metrics.cssWidthPx
        val measuredBackingScaleY = metrics.backingHeightPx / metrics.cssHeightPx
        val backingDifference = kotlin.math.abs(measuredBackingScaleX - measuredBackingScaleY) /
            maxOf(measuredBackingScaleX, measuredBackingScaleY)
        require(backingDifference <= 0.01f) {
            "Source WebGL backing store is not a uniform CSS scale: " +
                "scaleX=$measuredBackingScaleX scaleY=$measuredBackingScaleY"
        }
        val measuredBackingScale = (measuredBackingScaleX + measuredBackingScaleY) * 0.5f
        val dprDifference = kotlin.math.abs(measuredBackingScale - metrics.sourceDprCap) /
            metrics.sourceDprCap
        require(dprDifference <= 0.02f) {
            "Measured WebGL backing scale disagrees with min(devicePixelRatio, 2): " +
                "dprCap=${metrics.sourceDprCap} backingScale=$measuredBackingScale"
        }
        return RecognitionPerimeterCoordinateScale(
            shaderPxPerSourceUnit = pathScale / measuredBackingScale,
            pathPxPerCssUnit = pathScale,
            sourceDprCap = metrics.sourceDprCap,
            isMeasured = true,
        )
    }

    /** Exact source rounded-path segment order and 280-point cache sampling. */
    fun generatePerimeterPath(
        width: Float,
        height: Float,
        margin: Float = min(width, height) * MARGIN_RATIO,
        cornerRadius: Float = min(width, height) * 0.12f,
        subdivisions: Int = PATH_SUBDIVISIONS,
    ): List<RecognitionPerimeterPathPoint> {
        require(width > 0f && height > 0f && subdivisions > 0)
        val left = margin
        val right = width - margin
        val top = margin
        val bottom = height - margin
        val radius = min(cornerRadius, min((right - left) / 2f, (bottom - top) / 2f))
        val straightWidth = right - left - 2f * radius
        val straightHeight = bottom - top - 2f * radius
        val cornerArc = (PI.toFloat() / 2f) * radius
        val totalPerimeter = 2f * straightWidth + 2f * straightHeight + 4f * cornerArc
        val points = ArrayList<RecognitionPerimeterPathPoint>(subdivisions)

        repeat(subdivisions) { index ->
            val distance = (index / subdivisions.toFloat()) * totalPerimeter
            val firstLength = straightWidth / 2f
            val secondLength = firstLength + cornerArc
            val thirdLength = secondLength + straightHeight
            val fourthLength = thirdLength + cornerArc
            val fifthLength = fourthLength + straightWidth
            val sixthLength = fifthLength + cornerArc
            val seventhLength = sixthLength + straightHeight
            val eighthLength = seventhLength + cornerArc

            val point = when {
                distance < firstLength -> RecognitionPerimeterPathPoint(
                    left + radius + straightWidth / 2f + distance,
                    bottom,
                )
                distance < secondLength -> {
                    val angle = ((distance - firstLength) / cornerArc) * PI.toFloat() / 2f
                    RecognitionPerimeterPathPoint(
                        right - radius + sin(angle) * radius,
                        bottom - radius + cos(angle) * radius,
                    )
                }
                distance < thirdLength -> RecognitionPerimeterPathPoint(
                    right,
                    bottom - radius - (distance - secondLength),
                )
                distance < fourthLength -> {
                    val angle = ((distance - thirdLength) / cornerArc) * PI.toFloat() / 2f
                    RecognitionPerimeterPathPoint(
                        right - radius + cos(angle) * radius,
                        top + radius - sin(angle) * radius,
                    )
                }
                distance < fifthLength -> RecognitionPerimeterPathPoint(
                    right - radius - (distance - fourthLength),
                    top,
                )
                distance < sixthLength -> {
                    val angle = ((distance - fifthLength) / cornerArc) * PI.toFloat() / 2f
                    RecognitionPerimeterPathPoint(
                        left + radius - sin(angle) * radius,
                        top + radius - cos(angle) * radius,
                    )
                }
                distance < seventhLength -> RecognitionPerimeterPathPoint(
                    left,
                    top + radius + (distance - sixthLength),
                )
                distance < eighthLength -> {
                    val angle = ((distance - seventhLength) / cornerArc) * PI.toFloat() / 2f
                    RecognitionPerimeterPathPoint(
                        left + radius - sin(angle) * radius,
                        bottom - radius + cos(angle) * radius,
                    )
                }
                else -> RecognitionPerimeterPathPoint(
                    left + radius + (distance - eighthLength),
                    bottom,
                )
            }
            points += point
        }
        return points
    }

    fun sparkles(
        path: List<RecognitionPerimeterPathPoint>,
        timeSec: Float,
        activeCoverage: Float,
        pathScale: Float,
        photoOffsetX: Float,
        photoOffsetY: Float,
    ): List<RecognitionPerimeterSparkle> {
        require(path.isNotEmpty())
        val coverage = activeCoverage.coerceIn(0f, 1f)
        return List(SPARKLE_COUNT) { index ->
            val movementSpeed = 0.012f + (index % 3) * 0.007f
            val baseParam = index / SPARKLE_COUNT.toFloat()
            val pPos = (baseParam + timeSec * movementSpeed) % 1f
            val active = coverage > 0.05f &&
                (pPos <= 0.5f * coverage || pPos >= 1f - 0.5f * coverage)
            if (!active) {
                RecognitionPerimeterSparkle(-10_000f, -10_000f, pPos, active = false)
            } else {
                // JavaScript Math.round uses floor(x + 0.5), including exact ties.
                val pointIndex = kotlin.math.floor(pPos * path.size + 0.5f)
                    .toInt()
                    .coerceIn(0, path.lastIndex)
                val point = path[pointIndex]
                RecognitionPerimeterSparkle(
                    x = photoOffsetX + point.x * pathScale,
                    y = photoOffsetY + point.y * pathScale,
                    perimeterParam = pPos,
                    active = true,
                )
            }
        }
    }
}
