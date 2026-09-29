package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import kotlin.math.roundToInt

private const val FALLBACK_BACKGROUND =
    "empty_home_runtime_v2/static/scene_base.webp"

// The frozen mask is authored with translucent alpha. Normalize only this
// mask before applying the contract alpha so the contact edge remains legible
// without changing the 0.30 -> 0 ripple contract.
private val RippleAlphaNormalizationFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1.7f, 0f,
        ),
    ),
)

@Composable
internal fun EmptyHomeSceneRenderer(
    modifier: Modifier,
    motionState: HomeMotionState,
    runtimeAssets: EmptyHomeRuntimeAssets?,
) {
    Box(modifier.clipToBounds()) {
        AssetImage(
            FALLBACK_BACKGROUND,
            Modifier.fillMaxSize(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
        Canvas(Modifier.fillMaxSize()) {
            // A light blue-gray veil lowers saturation and foreground noise
            // while keeping the frozen lake and CTA structure intact.
            drawRect(color = YuJianColors.MistBlueGray.copy(alpha = 0.045f))
        }
        if (runtimeAssets != null) {
            val particles = remember { createSunParticleSpecs() }
            val paint = remember {
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            }
            val destination = remember { RectF() }
            Canvas(Modifier.fillMaxSize()) {
                drawRuntimeOverlays(
                    assets = runtimeAssets,
                    motionState = motionState,
                    particles = particles,
                    paint = paint,
                    destination = destination,
                )
            }
        }
    }
}

private fun DrawScope.drawRuntimeOverlays(
    assets: EmptyHomeRuntimeAssets,
    motionState: HomeMotionState,
    particles: List<SunParticleSpec>,
    paint: Paint,
    destination: RectF,
) {
    val transform = calculateReferenceSceneTransform(size.width, size.height)
    val time = if (motionState.running) motionState.sceneTimeSeconds else 0f

    drawReferenceBitmap(
        bitmap = assets.cloud,
        x = 214f + cloudOffsetPx(time + CLOUD_PHASE_OFFSET_SECONDS),
        y = 92f,
        width = assets.cloud.width.toFloat(),
        height = assets.cloud.height.toFloat(),
        alpha = 0.45f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    if (motionState.running && !motionState.reduceMotion) {
        drawReferenceBitmap(
            bitmap = assets.sunBeam,
            x = 654f,
            y = 474f,
            width = assets.sunBeam.width.toFloat(),
            height = assets.sunBeam.height.toFloat(),
            alpha = 0.18f * sunBeamEnvelope(time + SUN_BEAM_PHASE_OFFSET_SECONDS),
            transform = transform,
            paint = paint,
            destination = destination,
        )
        drawSunParticles(
            time = time,
            envelope = sunBeamEnvelope(time + SUN_BEAM_PHASE_OFFSET_SECONDS),
            particles = particles,
            transform = transform,
            particleBitmap = assets.particle,
            paint = paint,
            destination = destination,
        )
    }

    drawReferenceBitmap(
        bitmap = assets.rod,
        x = EMPTY_HOME_V2_ROD_X,
        y = EMPTY_HOME_V2_ROD_Y,
        width = assets.rod.width.toFloat(),
        height = assets.rod.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )
    drawFrozenFishingLine(transform)

    // Local atmosphere only: no glass surface, hard vignette, or CTA plate.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                YuJianColors.MistBlueGray.copy(alpha = 0.055f),
            ),
            startY = size.height * 0.72f,
            endY = size.height,
        ),
    )

    val bobberMotionActive = emptyHomeMotionActive(
        running = motionState.running,
        reduceMotion = motionState.reduceMotion,
    )
    val bobberOffset = if (bobberMotionActive) {
        bobberOffsetPx(time + BOBBER_PHASE_OFFSET_SECONDS)
    } else {
        0f
    }
    drawReferenceBitmap(
        bitmap = assets.ripple,
        x = EMPTY_HOME_V2_RIPPLE_X,
        // The ripple belongs to the water plane; only the bobber receives Y
        // motion, so the contact point never rides up and down with it.
        y = EMPTY_HOME_V2_RIPPLE_Y,
        width = assets.ripple.width.toFloat(),
        height = assets.ripple.height.toFloat(),
        // Reduce Motion freezes the contact state rather than removing it:
        // the lake still reads as a correctly seated bobber at rest.
        alpha = if (bobberMotionActive) rippleAlpha(time + RIPPLE_PHASE_OFFSET_SECONDS) else 0.30f,
        scale = if (bobberMotionActive) rippleScale(time + RIPPLE_PHASE_OFFSET_SECONDS) else 1f,
        pivotX = EMPTY_HOME_V2_WATER_CONTACT_X,
        pivotY = EMPTY_HOME_V2_WATER_CONTACT_Y,
        colorFilter = RippleAlphaNormalizationFilter,
        transform = transform,
        paint = paint,
        destination = destination,
    )
    drawReferenceBitmap(
        bitmap = assets.bobber,
        x = EMPTY_HOME_V2_BOBBER_X,
        y = EMPTY_HOME_V2_BOBBER_Y + bobberOffset,
        width = assets.bobber.width.toFloat(),
        height = assets.bobber.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )
}

private fun DrawScope.drawFrozenFishingLine(
    transform: ReferenceSceneTransform,
) {
    fun sx(value: Float): Float = transform.offsetX + value * transform.scale
    fun sy(value: Float): Float = transform.offsetY + value * transform.scale

    val linePath = Path().apply {
        moveTo(sx(EMPTY_HOME_V2_ROD_TIP_X), sy(EMPTY_HOME_V2_ROD_TIP_Y))
        cubicTo(
            sx(EMPTY_HOME_V2_LINE_C1_X),
            sy(EMPTY_HOME_V2_LINE_C1_Y),
            sx(EMPTY_HOME_V2_LINE_C2_X),
            sy(EMPTY_HOME_V2_LINE_C2_Y),
            sx(EMPTY_HOME_V2_LINE_END_X),
            sy(EMPTY_HOME_V2_LINE_END_Y),
        )
    }

    drawPath(
        path = linePath,
        color = YuJianColors.DeepInk.copy(alpha = 0.12f),
        style = Stroke(width = 2.25f * transform.scale, cap = StrokeCap.Round),
    )
    drawPath(
        path = linePath,
        color = Color.White.copy(alpha = 0.64f),
        style = Stroke(width = 1.15f * transform.scale, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawSunParticles(
    time: Float,
    envelope: Float,
    particles: List<SunParticleSpec>,
    transform: ReferenceSceneTransform,
    particleBitmap: Bitmap,
    paint: Paint,
    destination: RectF,
) {
    particles.forEach { particle ->
        val progress = (time / 4.8f + particle.phase) % 1f
        val x = particle.x + particle.driftX * progress
        val y = particle.y + particle.travelY * progress
        drawReferenceBitmap(
            bitmap = particleBitmap,
            x = x - particle.radius,
            y = y - particle.radius,
            width = particle.radius * 2f,
            height = particle.radius * 2f,
            alpha = particle.alpha * envelope,
            transform = transform,
            paint = paint,
            destination = destination,
        )
    }
}

private fun DrawScope.drawReferenceBitmap(
    bitmap: Bitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    alpha: Float,
    transform: ReferenceSceneTransform,
    paint: Paint,
    destination: RectF,
    scale: Float = 1f,
    pivotX: Float = x + width / 2f,
    pivotY: Float = y + height / 2f,
    colorFilter: ColorMatrixColorFilter? = null,
) {
    if (alpha <= 0f) return
    val scaledWidth = width * scale
    val scaledHeight = height * scale
    val left = pivotX + (x - pivotX) * scale
    val top = pivotY + (y - pivotY) * scale
    destination.set(
        transform.offsetX + left * transform.scale,
        transform.offsetY + top * transform.scale,
        transform.offsetX + (left + scaledWidth) * transform.scale,
        transform.offsetY + (top + scaledHeight) * transform.scale,
    )
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    paint.colorFilter = colorFilter
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(bitmap, null, destination, paint)
    }
    paint.colorFilter = null
}
