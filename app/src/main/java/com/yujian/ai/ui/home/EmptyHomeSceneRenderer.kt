package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import com.yujian.ai.ui.components.AssetImage
import kotlin.math.roundToInt

private const val FALLBACK_BACKGROUND =
    "empty_home_runtime_v2/static/scene_base.webp"

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
        x = 214f + cloudOffsetPx(time),
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
            alpha = 0.18f * sunBeamEnvelope(time),
            transform = transform,
            paint = paint,
            destination = destination,
        )
        drawSunParticles(
            time = time,
            envelope = sunBeamEnvelope(time),
            particles = particles,
            transform = transform,
            particleBitmap = assets.particle,
            paint = paint,
            destination = destination,
        )
    }

    drawReferenceBitmap(
        bitmap = assets.rod,
        x = 0f,
        y = 950f,
        width = assets.rod.width.toFloat(),
        height = assets.rod.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )
    drawReferenceBitmap(
        bitmap = assets.line,
        x = 400f,
        y = 950f,
        width = assets.line.width.toFloat(),
        height = assets.line.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    val bobberOffset = if (motionState.running && !motionState.reduceMotion) {
        bobberOffsetPx(time)
    } else {
        0f
    }
    drawReferenceBitmap(
        bitmap = assets.ripple,
        x = EMPTY_HOME_V2_RIPPLE_X,
        y = EMPTY_HOME_V2_RIPPLE_Y + bobberOffset,
        width = assets.ripple.width.toFloat(),
        height = assets.ripple.height.toFloat(),
        alpha = if (motionState.running && !motionState.reduceMotion) {
            rippleAlpha(time)
        } else {
            0.30f
        },
        scale = if (motionState.running && !motionState.reduceMotion) {
            rippleScale(time)
        } else {
            1f
        },
        pivotX = EMPTY_HOME_V2_WATER_CONTACT_X,
        pivotY = EMPTY_HOME_V2_WATER_CONTACT_Y + bobberOffset,
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
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(bitmap, null, destination, paint)
    }
}
