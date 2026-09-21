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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import com.yujian.ai.ui.components.AssetImage
import kotlin.math.roundToInt

private const val FALLBACK_BACKGROUND =
    "home_empty_v1_3/assets/background/home_empty_bg_no_bobber.webp"
private const val GOLD = 0xFFFDCF88

@Composable
internal fun EmptyHomeSceneRenderer(
    modifier: Modifier,
    motionState: HomeMotionState,
    runtimeAssets: EmptyHomeRuntimeAssets?,
) {
    Box(modifier.clipToBounds()) {
        if (runtimeAssets == null) {
            AssetImage(
                FALLBACK_BACKGROUND,
                Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        } else {
            val particles = remember { createSunParticleSpecs() }
            val paint = remember {
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            }
            val destination = remember { RectF() }
            Canvas(Modifier.fillMaxSize()) {
                drawRuntimeScene(
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

private fun DrawScope.drawRuntimeScene(
    assets: EmptyHomeRuntimeAssets,
    motionState: HomeMotionState,
    particles: List<SunParticleSpec>,
    paint: Paint,
    destination: RectF,
) {
    val transform = calculateReferenceSceneTransform(size.width, size.height)
    val time = if (motionState.running) motionState.sceneTimeSeconds else 0f

    drawReferenceBitmap(
        bitmap = assets.staticScene,
        x = 0f,
        y = 0f,
        width = REFERENCE_SCENE_WIDTH,
        height = REFERENCE_SCENE_HEIGHT,
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    // The renderer owns dynamic overlays only. Static scene pixels are decoded once.
    drawReferenceBitmap(
        bitmap = assets.cloud,
        x = cloudOffsetPx(time),
        y = 5f,
        width = assets.cloud.width.toFloat(),
        height = assets.cloud.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    val glowScale = if (motionState.reduceMotion) 1f else sunGlowScale(time)
    val glowAlpha = if (motionState.reduceMotion) 1f else sunGlowAlpha(time)
    drawReferenceBitmap(
        bitmap = assets.sunGlow,
        x = 845f - assets.sunGlow.width / 2f,
        y = 680f - assets.sunGlow.height / 2f,
        width = assets.sunGlow.width.toFloat(),
        height = assets.sunGlow.height.toFloat(),
        alpha = glowAlpha,
        scale = glowScale,
        pivotX = 845f,
        pivotY = 680f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    if (motionState.running && !motionState.reduceMotion) {
        val beamEnvelope = sunBeamEnvelope(time)
        drawReferenceBitmap(
            bitmap = assets.sunBeamMask,
            x = 710f,
            y = 680f,
            width = assets.sunBeamMask.width.toFloat(),
            height = assets.sunBeamMask.height.toFloat(),
            alpha = 0.16f * beamEnvelope,
            transform = transform,
            paint = paint,
            destination = destination,
        )
        drawReferenceBitmap(
            bitmap = assets.sunParticleMask,
            x = 735f,
            y = 700f,
            width = assets.sunParticleMask.width.toFloat(),
            height = assets.sunParticleMask.height.toFloat(),
            alpha = 0.78f * beamEnvelope,
            transform = transform,
            paint = paint,
            destination = destination,
        )
        drawSunParticles(
            time = time,
            envelope = beamEnvelope,
            particles = particles,
            transform = transform,
        )
    }

    val bobberOffset = if (motionState.running && !motionState.reduceMotion) {
        bobberOffsetPx(time)
    } else {
        0f
    }
    drawReferenceBitmap(
        bitmap = assets.ripple,
        x = 690f,
        y = 1158f - assets.ripple.height / 2f + bobberOffset,
        width = assets.ripple.width.toFloat(),
        height = assets.ripple.height.toFloat(),
        alpha = if (motionState.running && !motionState.reduceMotion) {
            rippleAlpha(time)
        } else {
            1f
        },
        scale = if (motionState.running && !motionState.reduceMotion) {
            rippleScale(time)
        } else {
            1f
        },
        pivotX = 750f,
        pivotY = 1158f,
        transform = transform,
        paint = paint,
        destination = destination,
    )
    drawReferenceBitmap(
        bitmap = assets.bobber,
        x = 743f,
        y = 1112f + bobberOffset,
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
) {
    particles.forEach { particle ->
        val progress = (time / 4.8f + particle.phase) % 1f
        val x = particle.x + particle.driftX * progress
        val y = particle.y + particle.travelY * progress
        drawCircle(
            color = Color(GOLD).copy(alpha = particle.alpha * envelope),
            radius = particle.radius * transform.scale,
            center = Offset(
                x = transform.offsetX + x * transform.scale,
                y = transform.offsetY + y * transform.scale,
            ),
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
