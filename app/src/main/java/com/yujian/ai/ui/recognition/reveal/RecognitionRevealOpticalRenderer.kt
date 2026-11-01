package com.yujian.ai.ui.recognition.reveal

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile

/** Draws the shared photo and optical reveal above Result content without owning input. */
@Composable
internal fun RecognitionRevealOpticalOverlay(
    modifier: Modifier,
    bitmap: Bitmap?,
    handoff: RecognitionRevealHandoff?,
    photoGeometry: RecognitionRevealPhotoGeometry?,
    geometry: RecognitionRevealGeometry?,
    frameState: State<RecognitionRevealFrame>,
    holdPhotoUntilHeroReady: Boolean,
    material: WaterLightMaterialProfile = WaterLightMaterialProfile.HANDOFF,
    energyEvent: EnergyEvent? = null,
) {
    val frame = frameState.value
    val sourceBitmap = bitmap ?: return
    if (handoff == null) return
    val activePhotoGeometry = photoGeometry ?: return
    val activeRevealGeometry = geometry
    if (!frame.drawOpticalLayer && !holdPhotoUntilHeroReady) return
    val drawsOptics = frame.drawOpticalLayer && activeRevealGeometry != null
    val canvasModifier = modifier.then(
        when {
            drawsOptics -> Modifier.testTag("recognition-reveal-optics")
            holdPhotoUntilHeroReady -> Modifier.testTag("recognition-reveal-photo-hold")
            else -> Modifier
        },
    ).then(
        if (activeRevealGeometry != null) Modifier.testTag("recognition-reveal-geometry-ready")
        else Modifier,
    )

    val orbReturnMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.orbReturnPathLocal?.let { PathMeasure(it.toPath(), false) }
    }
    val orbToGlyphMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.orbToGlyphPathLocal?.let { PathMeasure(it.toPath(), false) }
    }
    val glyphResidueMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.glyphResiduePathLocal?.let { PathMeasure(it.toPath(), false) }
    }
    val contourMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.let { PathMeasure(it.sourceContourPath, false) }
    }
    val transferTail = remember { android.graphics.Path() }
    val sharedPhotoClipPath = remember { android.graphics.Path() }
    val transferPosition = remember { FloatArray(2) }
    val transferTangent = remember { FloatArray(2) }
    val contourPosition = remember { FloatArray(2) }
    val mappedContourPosition = remember { FloatArray(2) }
    val transformValues = remember { FloatArray(9) }
    val contourBounds = remember(activeRevealGeometry) {
        RectF().also { bounds -> activeRevealGeometry?.sourceContourPath?.computeBounds(bounds, true) }
    }
    val imageMatrix = remember { Matrix() }
    val photoPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    }
    val haloPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val corePaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val particlePaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val dotPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val orbPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val orbRimPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }

    Canvas(canvasModifier) {
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val gold = material.primaryColor.toInt()
            val brightGold = material.secondaryColor.toInt()
            val drawSharedPhoto = frame.photoProgress < 1f || holdPhotoUntilHeroReady
            if (drawSharedPhoto) {
                val photoMatrix = activePhotoGeometry.photoMatrix(frame.photoProgress)
                val clip = activePhotoGeometry.photoClip(frame.photoProgress)
                val clipSave = canvas.save()
                sharedPhotoClipPath.reset()
                val progress = frame.photoProgress.coerceIn(0f, 1f)
                val cornerRadius = activePhotoGeometry.processingPhotoCornerRadiusPx +
                    (20.dp.toPx() - activePhotoGeometry.processingPhotoCornerRadiusPx) * progress
                sharedPhotoClipPath.addRoundRect(
                    RectF(clip.left, clip.top, clip.right, clip.bottom),
                    cornerRadius,
                    cornerRadius,
                    android.graphics.Path.Direction.CW,
                )
                canvas.clipPath(sharedPhotoClipPath)
                transformValues[0] = photoMatrix.scale
                transformValues[1] = 0f
                transformValues[2] = photoMatrix.translationX
                transformValues[3] = 0f
                transformValues[4] = photoMatrix.scale
                transformValues[5] = photoMatrix.translationY
                transformValues[6] = 0f
                transformValues[7] = 0f
                transformValues[8] = 1f
                imageMatrix.setValues(transformValues)
                canvas.save()
                canvas.concat(imageMatrix)
                canvas.drawBitmap(sourceBitmap, 0f, 0f, photoPaint)
                if (frame.fishHaloAlpha > 0f && activeRevealGeometry != null && contourMeasure != null) {
                    val inverseScale = 1f / photoMatrix.scale.coerceAtLeast(.1f)
                    val fishAxisPx = maxOf(contourBounds.width(), contourBounds.height()) * photoMatrix.scale
                    val coreWidthPx = (fishAxisPx * .008f).coerceIn(1f, 2.5f)
                    val softWidthPx = (fishAxisPx * .025f).coerceIn(4f, 10f)
                    val haloWidthPx = (fishAxisPx * .05f).coerceIn(12f, 28f)
                    haloPaint.color = android.graphics.Color.rgb(219, 163, 77)
                    haloPaint.alpha = (27 * frame.fishHaloAlpha).toInt()
                    haloPaint.strokeWidth = haloWidthPx * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, haloPaint)
                    haloPaint.color = android.graphics.Color.rgb(246, 199, 113)
                    haloPaint.alpha = (64 * frame.fishHaloAlpha).toInt()
                    haloPaint.strokeWidth = softWidthPx * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, haloPaint)
                    corePaint.color = android.graphics.Color.rgb(255, 227, 166)
                    corePaint.alpha = (148 * frame.fishHaloAlpha).toInt()
                    corePaint.strokeWidth = coreWidthPx * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, corePaint)
                    if (contourMeasure.length > 1f) {
                        for (index in 0 until 3) {
                            val fraction = ((index + 1f) / 4f + frame.transferProgress * .34f) % 1f
                            if (contourMeasure.getPosTan(contourMeasure.length * fraction, contourPosition, null)) {
                                mappedContourPosition[0] = contourPosition[0]
                                mappedContourPosition[1] = contourPosition[1]
                                imageMatrix.mapPoints(mappedContourPosition)
                                dotPaint.color = brightGold
                                dotPaint.alpha = (210 * frame.fishHaloAlpha).toInt()
                                canvas.drawCircle(
                                    mappedContourPosition[0], mappedContourPosition[1],
                                    3.5f * inverseScale, dotPaint,
                                )
                            }
                        }
                    }
                }
                canvas.restore()
                canvas.restoreToCount(clipSave)
            }

            val orbCenter = handoff.processingOrbCenterInWindow?.let { anchor ->
                val origin = activeRevealGeometry?.rootWindowOrigin ?: return@let null
                floatArrayOf(anchor.x - origin.x, anchor.y - origin.y)
            }
            if (activeRevealGeometry != null && orbCenter != null && frame.elapsedMs < RecognitionRevealClock.ORB_RETURN_END_MS) {
                val formation = frame.titleFormationProgress.coerceIn(0f, 1f)
                val returning = frame.elapsedMs in RecognitionRevealClock.PHOTO_END_MS until RecognitionRevealClock.ORB_RETURN_END_MS
                val orbAlpha = if (returning) 1f else (1f - formation * .12f)
                drawTransientOrb(
                    canvas, orbCenter[0], orbCenter[1], 23f,
                    alpha = orbAlpha,
                    energy = if (energyEvent?.feedback == EnergyFeedback.RETURN) energyEvent.level else 0f,
                    orbPaint = orbPaint,
                    rimPaint = orbRimPaint,
                )
            }

            // V4: fish energy starts returning while the photo continuity morph runs during 0–200 ms.
            if (activeRevealGeometry != null && orbReturnMeasure != null &&
                frame.elapsedMs in 0 until RecognitionRevealClock.PHOTO_END_MS
            ) {
                val progress = frame.photoProgress.coerceIn(0f, 1f)
                val headDistance = orbReturnMeasure.length * progress
                if (headDistance > 0f) {
                    transferTail.reset()
                    orbReturnMeasure.getSegment(
                        (headDistance - orbReturnMeasure.length * .38f).coerceAtLeast(0f),
                        headDistance,
                        transferTail,
                        true,
                    )
                    haloPaint.color = gold
                    haloPaint.alpha = (46 * frame.afterglowAlpha).toInt()
                    haloPaint.strokeWidth = 22f
                    canvas.drawPath(transferTail, haloPaint)
                    haloPaint.color = brightGold
                    haloPaint.alpha = (146 * frame.afterglowAlpha).toInt()
                    haloPaint.strokeWidth = 7.5f
                    canvas.drawPath(transferTail, haloPaint)
                    corePaint.color = 0xFFFFE5AA.toInt()
                    corePaint.alpha = (210 * frame.afterglowAlpha * material.opacity).toInt().coerceIn(0, 255)
                    corePaint.strokeWidth = 2.4f
                    canvas.drawPath(transferTail, corePaint)
                }
                if (orbReturnMeasure.getPosTan(headDistance, transferPosition, null)) {
                    dotPaint.color = 0xFFFFE5AA.toInt()
                    dotPaint.alpha = (220 * frame.afterglowAlpha).toInt()
                    canvas.drawCircle(transferPosition[0], transferPosition[1], 5.2f, dotPaint)
                    dotPaint.color = gold
                    dotPaint.alpha = (68 * frame.afterglowAlpha).toInt()
                    canvas.drawCircle(transferPosition[0], transferPosition[1], 14f, dotPaint)
                }
                for (index in 0 until frame.visibleParticleCount) {
                    val lag = (index + 1f) / (frame.visibleParticleCount + 1f) * .28f
                    val distance = (progress - lag).coerceAtLeast(0f) * orbReturnMeasure.length
                    if (orbReturnMeasure.getPosTan(distance, transferPosition, null)) {
                        particlePaint.color = if (index % 3 == 0) 0xFFFFE5AA.toInt() else brightGold
                        particlePaint.alpha = (132 * frame.afterglowAlpha).toInt()
                        canvas.drawCircle(transferPosition[0], transferPosition[1], if (index % 4 == 0) 2.8f else 1.7f, particlePaint)
                    }
                }
            }

            // V4: the recovered Orb stretches along its measured path and becomes real glyph ink.
            if (activeRevealGeometry != null && orbToGlyphMeasure != null &&
                frame.elapsedMs in RecognitionRevealClock.ORB_RETURN_END_MS until RecognitionRevealClock.ORB_TO_NAME_END_MS
            ) {
                val p = frame.titleFormationProgress.coerceIn(0f, 1f)
                val distance = orbToGlyphMeasure.length * p
                transferTail.reset()
                if (distance > 0f) {
                    orbToGlyphMeasure.getSegment(
                        (distance - orbToGlyphMeasure.length * .48f).coerceAtLeast(0f), distance, transferTail, true,
                    )
                    haloPaint.color = 0xFF4F9CA1.toInt()
                    haloPaint.alpha = (48 * (1f - p)).toInt()
                    haloPaint.strokeWidth = 18f * (1f + .4f * p)
                    canvas.drawPath(transferTail, haloPaint)
                    corePaint.color = 0xFFFFE3A6.toInt()
                    corePaint.alpha = (188 * (1f - p * .62f)).toInt()
                    corePaint.strokeWidth = 3.4f * (1f + .9f * p)
                    canvas.drawPath(transferTail, corePaint)
                }
                if (orbToGlyphMeasure.getPosTan(distance, transferPosition, transferTangent)) {
                    val radius = 22f
                    val width = radius * 2f * (1f + 2.2f * p)
                    val height = radius * 2f * (1f - .56f * p).coerceAtLeast(.38f)
                    val angle = kotlin.math.atan2(transferTangent[1].toDouble(), transferTangent[0].toDouble())
                    val save = canvas.save()
                    canvas.rotate(Math.toDegrees(angle).toFloat(), transferPosition[0], transferPosition[1])
                    orbPaint.shader = android.graphics.RadialGradient(
                        transferPosition[0] - width * .18f, transferPosition[1] - height * .18f,
                        width * .72f,
                        intArrayOf(0xFFFFF7DE.toInt(), 0xFF72BAC0.toInt(), 0x8836828E.toInt(), android.graphics.Color.TRANSPARENT),
                        null, android.graphics.Shader.TileMode.CLAMP,
                    )
                    orbPaint.alpha = (220 * (1f - p * .45f)).toInt()
                    canvas.drawOval(
                        RectF(transferPosition[0] - width / 2f, transferPosition[1] - height / 2f,
                            transferPosition[0] + width / 2f, transferPosition[1] + height / 2f), orbPaint,
                    )
                    orbPaint.shader = null
                    canvas.restoreToCount(save)
                }
            }

            // Localized residue settles around the real title bounds and reaches zero at R5.
            if (activeRevealGeometry != null && frame.elapsedMs in RecognitionRevealClock.SPECIES_NAME_REVEAL_END_MS until RecognitionRevealClock.TOTAL_DURATION_MS) {
                val bounds = activeRevealGeometry.titleBoundsLocal
                val alpha = (96 * frame.afterglowAlpha * material.opacity).toInt().coerceIn(0, 96)
                if (glyphResidueMeasure != null) {
                    val settledProgress = (1f - frame.afterglowAlpha).coerceIn(0f, 1f)
                    val distance = glyphResidueMeasure.length * settledProgress
                    if (distance > 0f) {
                        transferTail.reset()
                        glyphResidueMeasure.getSegment(0f, distance, transferTail, true)
                        corePaint.color = 0xFFD9BB7B.toInt()
                        corePaint.alpha = alpha
                        corePaint.strokeWidth = 1.6f
                        canvas.drawPath(transferTail, corePaint)
                    }
                }
                for (index in 0 until 8) {
                    val x = bounds.left + bounds.width * (((index * 7) % 11) + 1) / 12f
                    val y = bounds.top + bounds.height * (.18f + ((index * 3) % 6) * .11f)
                    particlePaint.color = if (index % 3 == 0) 0xFFE8C77F.toInt() else 0xFF8EC9C7.toInt()
                    particlePaint.alpha = alpha
                    canvas.drawCircle(x, y, if (index % 2 == 0) 1.7f else 1.1f, particlePaint)
                }
            }

        }
    }
}

private fun drawTransientOrb(
    canvas: android.graphics.Canvas,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float,
    energy: Float,
    orbPaint: AndroidPaint,
    rimPaint: AndroidPaint,
) {
    val received = energy.coerceIn(0f, 1f)
    val r = radius * (1f + .04f * received)
    orbPaint.shader = android.graphics.RadialGradient(
        centerX - r * .28f,
        centerY - r * .34f,
        r * 1.28f,
        intArrayOf(
            0xFFF4FFF2.toInt(),
            0xFF9BD4CB.toInt(),
            0xAA3B8FA2.toInt(),
            0x44334859,
        ),
        null,
        android.graphics.Shader.TileMode.CLAMP,
    )
    orbPaint.alpha = (215f * alpha.coerceIn(0f, 1f) * (1f + .16f * received)).toInt().coerceIn(0, 255)
    canvas.drawCircle(centerX, centerY, r, orbPaint)
    orbPaint.shader = null
    rimPaint.style = AndroidPaint.Style.STROKE
    rimPaint.strokeWidth = 1.25f
    rimPaint.color = 0xFFE7D29B.toInt()
    rimPaint.alpha = (125f * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 150)
    val filament = android.graphics.Path().apply {
        moveTo(centerX - r * 1.05f, centerY + r * .10f)
        cubicTo(centerX - r * .45f, centerY - r * .92f, centerX + r * .10f, centerY - r * .72f, centerX + r * .72f, centerY - r * .22f)
        moveTo(centerX - r * .22f, centerY + r * .96f)
        cubicTo(centerX + r * .75f, centerY + r * .43f, centerX + r * .80f, centerY + r * .02f, centerX + r * 1.02f, centerY - r * .16f)
    }
    canvas.drawPath(filament, rimPaint)
    rimPaint.color = 0xFFE9F9F0.toInt()
    rimPaint.alpha = (85f * alpha.coerceIn(0f, 1f) + 70f * received).toInt().coerceIn(0, 180)
    rimPaint.strokeWidth = .8f
    canvas.drawCircle(centerX - r * .32f, centerY - r * .38f, r * .10f, rimPaint)
}
