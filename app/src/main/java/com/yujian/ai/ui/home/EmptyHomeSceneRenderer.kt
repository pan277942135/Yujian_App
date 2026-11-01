package com.yujian.ai.ui.home

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.yujian.ai.ui.components.AssetImage
import kotlin.math.roundToInt

private const val FALLBACK_BACKGROUND =
    "empty_home_runtime_v2/static/scene_base.webp"

@Composable
internal fun EmptyHomeSceneRenderer(
    modifier: Modifier,
    motionState: HomeMotionState,
    runtimeAssets: EmptyHomeRuntimeAssets?,
    sceneTransform: ReferenceSceneTransform,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var lastHapticBurstId by remember(motionState) { mutableLongStateOf(Long.MIN_VALUE) }
    val currentBurstId = remember(motionState) {
        derivedStateOf {
            if (!motionState.running || motionState.reduceMotion) {
                null
            } else {
                calculatePeriodicBobberRippleBurst(
                    motionState.sceneTimeSeconds + RIPPLE_PHASE_OFFSET_SECONDS,
                )?.eventId
            }
        }
    }

    LaunchedEffect(
        currentBurstId.value,
        motionState.running,
        motionState.reduceMotion,
        runtimeAssets != null,
    ) {
        val burstId = currentBurstId.value ?: return@LaunchedEffect
        if (runtimeAssets != null && motionState.running && !motionState.reduceMotion &&
            burstId != lastHapticBurstId
        ) {
            bobberNibbleHaptic(context, view)
            lastHapticBurstId = burstId
        }
    }

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
                    transform = sceneTransform,
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
    transform: ReferenceSceneTransform,
) {
    val time = if (motionState.running) motionState.sceneTimeSeconds else 0f

    drawReferenceBitmap(
        bitmap = assets.sceneBase,
        x = 0f,
        y = 0f,
        width = REFERENCE_SCENE_WIDTH,
        height = REFERENCE_SCENE_HEIGHT,
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    // Remove the former full-page and bottom color veils. V2 does not authorize
    // an additional grade over the frozen Sunrise Hero surface.

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

    val bobberMotionActive = emptyHomeMotionActive(
        running = motionState.running,
        reduceMotion = motionState.reduceMotion,
    )
    val bobberOffset = if (bobberMotionActive) {
        bobberOffsetPx(time + BOBBER_PHASE_OFFSET_SECONDS)
    } else {
        0f
    }
    val bobberSplit = calculateBobberWaterSplit(
        bitmapHeight = assets.bobber.height,
        bobberTopY = EMPTY_HOME_V2_BOBBER_Y + bobberOffset,
        waterContactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
    )

    // Keep V2.2's narrow optical continuation separate from the bobber body.
    // This layer is independently faint/faded and never renders a second float.
    drawReferenceBitmap(
        bitmap = assets.bobberReflection,
        x = EMPTY_HOME_V2_REFLECTION_X,
        y = EMPTY_HOME_V2_REFLECTION_Y,
        width = assets.bobberReflection.width.toFloat(),
        height = assets.bobberReflection.height.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
    )

    val rippleBurst = if (bobberMotionActive) {
        calculatePeriodicBobberRippleBurst(time + RIPPLE_PHASE_OFFSET_SECONDS)
    } else {
        null
    }
    drawPeriodicBobberRipples(
        burstElapsedSeconds = rippleBurst?.elapsedSeconds,
        contactX = EMPTY_HOME_V2_WATER_CONTACT_X,
        contactY = EMPTY_HOME_V2_WATER_CONTACT_Y,
        transform = transform,
    )
    drawReferenceBitmap(
        bitmap = assets.bobber,
        x = EMPTY_HOME_V2_BOBBER_X,
        y = EMPTY_HOME_V2_BOBBER_Y + bobberOffset,
        width = assets.bobber.width.toFloat(),
        height = bobberSplit.splitY.toFloat(),
        alpha = 1f,
        transform = transform,
        paint = paint,
        destination = destination,
        source = Rect(0, 0, assets.bobber.width, bobberSplit.splitY),
    )
}

private fun DrawScope.drawPeriodicBobberRipples(
    burstElapsedSeconds: Float?,
    contactX: Float,
    contactY: Float,
    transform: ReferenceSceneTransform,
) {
    val centerX = transform.offsetX + contactX * transform.scale
    val centerY = transform.offsetY + contactY * transform.scale
    val scale = transform.scale
    val perspectiveRatio = 3.2f

    // One stable contact ring remains visible when Reduce Motion is enabled.
    val contactRx = 14f * scale
    val contactRy = contactRx / perspectiveRatio
    drawOval(
        color = Color(0xFFC0ECF5).copy(alpha = 0.32f),
        topLeft = Offset(centerX - contactRx * 1.25f, centerY - contactRy * 1.25f),
        size = Size(contactRx * 2.5f, contactRy * 2.5f),
        style = Stroke(width = 2.2f * scale),
    )
    drawOval(
        color = Color(0xFFEDF9FC).copy(alpha = 0.70f),
        topLeft = Offset(centerX - contactRx, centerY - contactRy),
        size = Size(contactRx * 2f, contactRy * 2f),
        style = Stroke(width = 1.35f * scale),
    )

    val elapsed = burstElapsedSeconds ?: return
    drawRippleWave(
        elapsedSeconds = elapsed,
        delaySeconds = 0f,
        totalDurationSeconds = 3.6f,
        startRadius = 16f,
        radiusGrowth = 270f,
        alphaFrom = 0.86f,
        strokeFrom = 2.3f,
        strokeTo = 1.3f,
        shadowAlphaRatio = 0.28f,
        centerX = centerX,
        centerY = centerY,
        scale = scale,
        perspectiveRatio = perspectiveRatio,
        highlight = Color(0xFFE2F8FD),
        sunGleam = true,
    )
    drawRippleWave(
        elapsedSeconds = elapsed,
        delaySeconds = 0.42f,
        totalDurationSeconds = 3.6f,
        startRadius = 14f,
        radiusGrowth = 190f,
        alphaFrom = 0.72f,
        strokeFrom = 1.95f,
        strokeTo = 1.1f,
        shadowAlphaRatio = 0.22f,
        centerX = centerX,
        centerY = centerY,
        scale = scale,
        perspectiveRatio = perspectiveRatio,
        highlight = Color(0xFFDAF4FC),
        sunGleam = true,
    )
    drawRippleWave(
        elapsedSeconds = elapsed,
        delaySeconds = 0.90f,
        totalDurationSeconds = 3.6f,
        startRadius = 12f,
        radiusGrowth = 120f,
        alphaFrom = 0.56f,
        strokeFrom = 1.6f,
        strokeTo = 0.95f,
        shadowAlphaRatio = 0f,
        centerX = centerX,
        centerY = centerY,
        scale = scale,
        perspectiveRatio = perspectiveRatio,
        highlight = Color(0xFFD2F1FA),
        sunGleam = false,
    )
}

private fun DrawScope.drawRippleWave(
    elapsedSeconds: Float,
    delaySeconds: Float,
    totalDurationSeconds: Float,
    startRadius: Float,
    radiusGrowth: Float,
    alphaFrom: Float,
    strokeFrom: Float,
    strokeTo: Float,
    shadowAlphaRatio: Float,
    centerX: Float,
    centerY: Float,
    scale: Float,
    perspectiveRatio: Float,
    highlight: Color,
    sunGleam: Boolean,
) {
    if (elapsedSeconds < delaySeconds) return
    val waveDuration = totalDurationSeconds - delaySeconds
    val progress = ((elapsedSeconds - delaySeconds) / waveDuration).coerceIn(0f, 1f)
    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val radiusX = (startRadius + radiusGrowth * eased) * scale
    val radiusY = radiusX / perspectiveRatio
    val alpha = (alphaFrom * (1f - progress) * (1f - 0.22f * progress)).coerceIn(0f, 1f)
    val strokeWidth = ((strokeFrom + (strokeTo - strokeFrom) * progress) * scale).coerceAtLeast(0.85f)

    if (alpha <= 0.015f) return
    if (shadowAlphaRatio > 0f) {
        drawOval(
            color = Color(0xFF0F3946).copy(alpha = (alpha * shadowAlphaRatio).coerceIn(0f, 1f)),
            topLeft = Offset(centerX - radiusX, centerY - radiusY + 0.8f * scale),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = strokeWidth * 1.3f),
        )
    }
    drawOval(
        color = highlight.copy(alpha = alpha),
        topLeft = Offset(centerX - radiusX, centerY - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = Stroke(width = strokeWidth),
    )
    if (sunGleam) {
        drawArc(
            color = Color(0xFFFFF7DC).copy(alpha = (alpha * 0.78f).coerceIn(0f, 1f)),
            startAngle = -45f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(centerX - radiusX, centerY - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = strokeWidth * 1.2f, cap = StrokeCap.Round),
        )
    }
}

internal data class BobberWaterSplit(
    val splitY: Int,
    val underwaterHeight: Int,
    val underwaterAlpha: Float,
)

internal fun calculateBobberWaterSplit(
    bitmapHeight: Int,
    bobberTopY: Float,
    waterContactY: Float,
): BobberWaterSplit {
    require(bitmapHeight > 0)
    val splitY = (waterContactY - bobberTopY).roundToInt().coerceIn(0, bitmapHeight)
    // The reflection is a separate low-alpha layer; the body is clipped at the
    // fixed water plane and no submerged copy is composited.
    return BobberWaterSplit(splitY = splitY, underwaterHeight = 0, underwaterAlpha = 0f)
}

private fun DrawScope.drawFrozenFishingLine(
    transform: ReferenceSceneTransform,
) {
    fun sx(value: Float): Float = transform.offsetX + value * transform.scale
    fun sy(value: Float): Float = transform.offsetY + value * transform.scale

    val linePath = Path().apply {
        moveTo(sx(EMPTY_HOME_V2_LINE_START_X), sy(EMPTY_HOME_V2_LINE_START_Y))
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
        color = Color(0xFFD5E1E3).copy(alpha = 0.78f),
        style = Stroke(width = 1.35f * transform.scale, cap = StrokeCap.Round),
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
    source: Rect? = null,
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
        canvas.nativeCanvas.drawBitmap(bitmap, source, destination, paint)
    }
    paint.colorFilter = null
}
