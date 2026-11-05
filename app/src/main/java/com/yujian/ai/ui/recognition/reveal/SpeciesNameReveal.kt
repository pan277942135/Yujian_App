package com.yujian.ai.ui.recognition.reveal

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Forms the actual classifier-provided Chinese glyph from deterministic water-gold ink samples.
 * The name is never replaced by a bitmap or a fixed species string.
 */
internal class SpeciesNameReveal {
    private data class InkPoint(val x: Float, val y: Float, val rank: Int)

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sweepEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f }
    private val glyphPathMeasure = PathMeasure()
    private val glyphSweepPath = Path()
    private var cachedGlyph: Path? = null
    private var inkPoints: List<InkPoint> = emptyList()

    fun draw(
        canvas: Canvas,
        glyph: Path,
        width: Float,
        height: Float,
        mode: RecognitionRevealTitleMode,
        formationProgress: Float,
        revealProgress: Float,
        sweepProgress: Float = revealProgress,
        substep: RecognitionOrbGlyphSubstep? = null,
        substepProgress: Float = revealProgress,
        glyphProgress: Float = revealProgress,
    ) {
        if (glyph.isEmpty || width <= 0f || height <= 0f || mode == RecognitionRevealTitleMode.HIDDEN ||
            mode == RecognitionRevealTitleMode.STATIC || mode == RecognitionRevealTitleMode.VISIBLE
        ) return
        if (cachedGlyph !== glyph) {
            cachedGlyph = glyph
            inkPoints = sampleGlyph(glyph)
        }
        when (mode) {
            RecognitionRevealTitleMode.FORMING -> when (substep) {
                RecognitionOrbGlyphSubstep.ORB_CORE_HOLD -> Unit
                RecognitionOrbGlyphSubstep.ORB_HORIZONTAL_STRETCH -> Unit
                RecognitionOrbGlyphSubstep.LIQUID_BRUSH_TRANSITION -> drawFormingInk(
                    canvas, glyph, width, height, glyphProgress.coerceIn(0f, 1f),
                )
                else -> drawFormingInk(canvas, glyph, width, height, formationProgress)
            }
            RecognitionRevealTitleMode.REVEALING -> drawReveal(
                canvas, glyph, width, height, revealProgress, sweepProgress,
                substep, substepProgress, glyphProgress,
            )
            RecognitionRevealTitleMode.HIDDEN,
            RecognitionRevealTitleMode.STATIC,
            RecognitionRevealTitleMode.VISIBLE -> Unit
        }
    }

    private fun drawFormingInk(canvas: Canvas, glyph: Path, width: Float, height: Float, progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f || inkPoints.isEmpty()) return
        // A connected water-gold wipe forms the real classifier Glyph while the
        // deterministic droplets fill its stroke area; the title never fades in as plain Text.
        drawSweptGlyph(canvas, glyph, width, height, (.08f + .86f * p).coerceIn(0f, 1f),
            alpha = (88 + 92 * p).toInt())
        drawGlyphPathSweep(canvas, glyph, p)
        val count = ceil(inkPoints.size * p).toInt().coerceIn(0, inkPoints.size)
        val save = canvas.save()
        try {
            canvas.clipPath(glyph)
            inkPoints.take(count).forEachIndexed { index, point ->
                val progressLocal = (index + 1f) / count.coerceAtLeast(1)
                val radius = (1.1f + (point.rank % 5) * .23f) * (0.7f + .3f * progressLocal)
                particlePaint.color = if (point.rank % 4 == 0) 0xFFF1D89D.toInt() else 0xFF5A9DA0.toInt()
                particlePaint.alpha = (130 + point.rank % 70).coerceIn(0, 200)
                canvas.drawCircle(point.x, point.y, radius, particlePaint)
            }
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun drawReveal(
        canvas: Canvas,
        glyph: Path,
        width: Float,
        height: Float,
        progress: Float,
        sweepProgress: Float,
        substep: RecognitionOrbGlyphSubstep?,
        substepProgress: Float,
        glyphProgress: Float,
    ) {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f && (substep == null || glyphProgress <= 0f)) return
        when (substep) {
            RecognitionOrbGlyphSubstep.STROKE_AGGREGATION -> {
                drawGlyphPathSweep(canvas, glyph, glyphProgress.coerceIn(0f, 1f), alpha = 135)
                drawAggregatingInk(canvas, glyph, glyphProgress, substepProgress)
                return
            }
            RecognitionOrbGlyphSubstep.GLYPH_EMERGENCE -> {
                val progress = glyphProgress.coerceIn(0f, 1f)
                drawSweptGlyph(
                    canvas, glyph, width, height, (.08f + .86f * progress).coerceIn(0f, 1f),
                    alpha = (120 + 125 * progress).toInt().coerceIn(0, 245),
                )
                drawGlyphPathSweep(canvas, glyph, progress, alpha = (145 * (1f - progress * .55f)).toInt())
                drawAggregatingInk(canvas, glyph, progress, 1f - progress)
                return
            }
            RecognitionOrbGlyphSubstep.SPECIES_NAME_FINAL -> {
                val settle = substepProgress.coerceIn(0f, 1f)
                drawSweptGlyph(canvas, glyph, width, height, 1f, alpha = (230 + 15 * settle).toInt())
                drawGlyphPathSweep(canvas, glyph, 1f, alpha = (76 * (1f - settle)).toInt())
                return
            }
            else -> Unit
        }
        val sweep = sweepProgress.coerceIn(p * .78f, 1f)
        drawSweptGlyph(canvas, glyph, width, height, sweep, alpha = (245 * p).toInt().coerceIn(0, 245))
        drawGlyphPathSweep(canvas, glyph, sweep, alpha = (155 * (1f - p * .45f)).toInt())
    }

    /** Ink beads aggregate on the actual classifier glyph path during 12.1 and 12.2. */
    private fun drawAggregatingInk(canvas: Canvas, glyph: Path, glyphProgress: Float, visibility: Float) {
        val count = ceil(inkPoints.size * glyphProgress.coerceIn(0f, 1f)).toInt().coerceIn(0, inkPoints.size)
        if (count <= 0 || visibility <= 0f) return
        val alpha = (155 * visibility.coerceIn(0f, 1f)).toInt().coerceIn(0, 180)
        val save = canvas.save()
        try {
            canvas.clipPath(glyph)
            inkPoints.take(count).forEach { point ->
                val radius = (1.0f + (point.rank % 5) * .22f) * visibility.coerceIn(0f, 1f)
                particlePaint.color = if (point.rank % 4 == 0) 0xFFF1D89D.toInt() else 0xFF5A9DA0.toInt()
                particlePaint.alpha = alpha
                canvas.drawCircle(point.x, point.y, radius, particlePaint)
            }
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /** Trace the actual classifier Glyph contours continuously, in their Path order. */
    private fun drawGlyphPathSweep(canvas: Canvas, glyph: Path, progress: Float, alpha: Int = 150) {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return
        glyphPathMeasure.setPath(glyph, false)
        var totalLength = 0f
        do {
            totalLength += glyphPathMeasure.length
        } while (glyphPathMeasure.nextContour())
        if (totalLength <= 0f) return

        glyphSweepPath.reset()
        glyphPathMeasure.setPath(glyph, false)
        var remaining = totalLength * p
        do {
            val contourLength = glyphPathMeasure.length
            val segmentLength = remaining.coerceIn(0f, contourLength)
            if (segmentLength > 0f) glyphPathMeasure.getSegment(0f, segmentLength, glyphSweepPath, true)
            remaining -= segmentLength
        } while (remaining > 0f && glyphPathMeasure.nextContour())

        val save = canvas.save()
        try {
            sweepEdgePaint.color = 0xFFE4C77E.toInt()
            sweepEdgePaint.alpha = alpha.coerceIn(0, 180)
            canvas.drawPath(glyphSweepPath, sweepEdgePaint)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun drawSweptGlyph(canvas: Canvas, glyph: Path, width: Float, height: Float, progress: Float, alpha: Int) {
        val save = canvas.save()
        try {
            canvas.clipRect(0f, 0f, max(1f, width * progress), height)
            sweepPaint.shader = LinearGradient(
                0f, height, width, 0f,
                intArrayOf(0xFF477F80.toInt(), 0xFF78B5AF.toInt(), 0xFFE4C77E.toInt(), 0xFF9A713B.toInt()),
                floatArrayOf(0f, .36f, .64f, 1f), Shader.TileMode.CLAMP,
            )
            sweepPaint.alpha = alpha.coerceIn(0, 245)
            canvas.drawPath(glyph, sweepPaint)
        } finally {
            sweepPaint.shader = null
            canvas.restoreToCount(save)
        }
    }

    private fun sampleGlyph(glyph: Path): List<InkPoint> {
        val bounds = RectF()
        glyph.computeBounds(bounds, true)
        if (bounds.width() <= 1f || bounds.height() <= 1f) return emptyList()
        val clip = Region(
            floor(bounds.left).toInt() - 2,
            floor(bounds.top).toInt() - 2,
            ceil(bounds.right).toInt() + 2,
            ceil(bounds.bottom).toInt() + 2,
        )
        val region = Region().apply { setPath(glyph, clip) }
        val step = maxOf(4, (minOf(bounds.width(), bounds.height()) / 9f).toInt())
        val samples = ArrayList<InkPoint>(96)
        var rank = 0
        var y = floor(bounds.top).toInt()
        while (y <= ceil(bounds.bottom).toInt()) {
            var x = floor(bounds.left).toInt()
            while (x <= ceil(bounds.right).toInt()) {
                // Stable hash staggering avoids a mechanical grid and is independent of frame time.
                val hash = ((x * 73856093) xor (y * 19349663)) and 0x7fffffff
                val px = x + (hash % step).toFloat() / step
                val py = y + ((hash / step) % step).toFloat() / step
                if (region.contains(px.toInt(), py.toInt()) && hash % 3 != 0) {
                    samples += InkPoint(px, py, hash % 97)
                }
                x += step
            }
            y += step
        }
        return samples.sortedBy(InkPoint::rank).take(120)
    }
}
