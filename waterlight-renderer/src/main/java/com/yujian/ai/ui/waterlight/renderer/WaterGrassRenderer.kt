package com.yujian.ai.ui.waterlight.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import kotlin.math.sin

/**
 * Small, reusable aquatic-grass rendering host. It consumes the same WaterLightFrame as
 * Orb and the photo hosts; it never owns a timer or derives identity from view lifecycle.
 */
class WaterGrassRenderer {
    private val stemPath = Path()
    private val leafPath = Path()
    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bladeBounds = RectF()

    /** Draw five tapered stems with roots and leaves in a caller-selected safe region. */
    fun draw(
        canvas: Canvas,
        frame: WaterLightFrame,
        bounds: RectF,
        seed: Long,
        material: WaterLightMaterialProfile,
        opacity: Float,
        energyEvent: EnergyEvent? = null,
        avoidBounds: List<RectF> = emptyList(),
    ) {
        if (!frame.sceneFrame.isIdentityValid || bounds.width() < 2f || bounds.height() < 2f) return
        val safeOpacity = (opacity * material.opacity).coerceIn(0f, 1f)
        if (safeOpacity <= .001f) return
        val event = energyEvent?.takeIf { it.matches(frame) }
        val pulse = when (event?.feedback) {
            EnergyFeedback.RELEASE, EnergyFeedback.ARRIVE -> event.level.coerceIn(0f, 1f)
            EnergyFeedback.ABSORB, EnergyFeedback.RETURN -> .7f * event.level.coerceIn(0f, 1f)
            else -> 0f
        }
        val motion = if (frame.reducedMotion) 0f else {
            frame.opticalReferenceElapsedMs / 1_000f * material.speed * material.motionScale
        }
        val width = bounds.width()
        val height = bounds.height()
        val scale = minOf(width, height)
        val baseAlpha = (safeOpacity * (.38f + .10f * pulse)).coerceIn(0f, .22f)
        val green = material.highlightColor.toInt()
        val teal = material.secondaryColor.toInt()
        val gold = material.primaryColor.toInt()
        val positions = floatArrayOf(.06f, .27f, .50f, .73f, .94f)
        val heights = floatArrayOf(.48f, .74f, .58f, .78f, .43f)
        val phases = floatArrayOf(.2f, 1.4f, 2.5f, 3.7f, 5.1f)
        val save = canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)
        for (index in positions.indices) {
            val rootX = positions[index] * width
            val rootY = height
            val bladeHeight = heights[index] * height
            val phase = phases[index] + ((seed xor (seed ushr 17)) and 0xFFFF).toFloat() / 65_535f
            val sway = if (frame.reducedMotion) 0f else
                sin(motion * .55f + phase) * width * (.012f + .004f * pulse)
            bladeBounds.set(
                bounds.left + rootX - bladeHeight * .23f + minOf(0f, sway),
                bounds.top + rootY - bladeHeight * 1.08f,
                bounds.left + rootX + bladeHeight * .23f + maxOf(0f, sway),
                bounds.top + rootY,
            )
            if (avoidBounds.any { avoid -> RectF.intersects(bladeBounds, avoid) }) continue

            val tipX = rootX + sway
            val tipY = rootY - bladeHeight
            stemPath.reset()
            stemPath.moveTo(rootX, rootY)
            stemPath.cubicTo(rootX - sway * .15f, rootY - bladeHeight * .34f,
                rootX + sway * .72f, rootY - bladeHeight * .68f, tipX, tipY)
            stemPaint.color = withAlpha(if (index % 2 == 0) green else teal, baseAlpha * 255f)
            stemPaint.strokeWidth = (scale * .014f).coerceIn(1f, 3f)
            canvas.drawPath(stemPath, stemPaint)

            for (leafIndex in 0 until 3) {
                val along = .31f + leafIndex * .20f
                val stemX = rootX + sway * along * .74f
                val stemY = rootY - bladeHeight * along
                val side = if ((leafIndex + index) % 2 == 0) -1f else 1f
                val leafLength = bladeHeight * (.19f - leafIndex * .018f)
                val lift = leafLength * .72f
                leafPath.reset()
                leafPath.moveTo(stemX, stemY)
                leafPath.cubicTo(stemX + side * leafLength * .32f, stemY - lift * .72f,
                    stemX + side * leafLength * .85f, stemY - lift * .88f,
                    stemX + side * leafLength, stemY - lift)
                leafPath.cubicTo(stemX + side * leafLength * .62f, stemY - lift * .08f,
                    stemX + side * leafLength * .22f, stemY + lift * .02f,
                    stemX, stemY)
                leafPath.close()
                leafPaint.color = withAlpha(if (leafIndex == 2) teal else green,
                    baseAlpha * (if (leafIndex == 0) 190f else 150f))
                canvas.drawPath(leafPath, leafPaint)

                if (leafIndex == 2) {
                    leafPath.reset()
                    leafPath.moveTo(stemX, stemY)
                    leafPath.lineTo(stemX + side * leafLength * .7f, stemY - lift * .55f)
                    glintPaint.color = withAlpha(gold, baseAlpha * (34f + 18f * pulse))
                    glintPaint.strokeWidth = (scale * .004f).coerceAtLeast(.65f)
                    canvas.drawPath(leafPath, glintPaint)
                }
            }
            rootPaint.color = withAlpha(teal, baseAlpha * 145f)
            canvas.drawCircle(rootX, rootY - scale * .012f, (scale * .012f).coerceIn(1f, 3.5f), rootPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or (alpha.toInt().coerceIn(0, 255) shl 24)
}

/** Compose adapter for hosts whose local bounds match the Canvas bounds. */
@Composable
fun WaterGrassField(
    frame: WaterLightFrame,
    modifier: Modifier = Modifier,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.PATH,
    opacity: Float = .2f,
    seed: Long = frame.sessionId.hashCode().toLong(),
    drawBounds: Rect? = null,
    avoidBounds: Rect? = null,
    energyEvent: EnergyEvent? = null,
) {
    val renderer = remember { WaterGrassRenderer() }
    ComposeCanvas(modifier) {
        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            val bounds = drawBounds?.let { RectF(it.left, it.top, it.right, it.bottom) }
                ?: RectF(0f, 0f, size.width, size.height)
            val avoid = avoidBounds?.let { listOf(RectF(it.left, it.top, it.right, it.bottom)) }.orEmpty()
            renderer.draw(native, frame, bounds, seed, material, opacity, energyEvent, avoid)
        }
    }
}
