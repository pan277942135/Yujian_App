package com.yujian.ai.ui.recognition.reveal

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

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
) {
    val frame = frameState.value
    val sourceBitmap = bitmap ?: return
    if (handoff == null) return
    val activePhotoGeometry = photoGeometry ?: return
    val activeRevealGeometry = geometry
    if (!frame.drawOpticalLayer && !holdPhotoUntilHeroReady) return

    val transferMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.let { PathMeasure(it.goldTransferPathLocal.toPath(), false) }
    }
    val contourMeasure = remember(activeRevealGeometry) {
        activeRevealGeometry?.let { PathMeasure(it.sourceContourPath, false) }
    }
    val transferTail = remember { android.graphics.Path() }
    val sharedPhotoClipPath = remember { android.graphics.Path() }
    val transferPosition = remember { FloatArray(2) }
    val contourPosition = remember { FloatArray(2) }
    val mappedContourPosition = remember { FloatArray(2) }
    val transformValues = remember { FloatArray(9) }
    val formationParticleFractions = remember { floatArrayOf(.09f, .28f, .47f, .67f, .88f) }
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
    val sweepPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }

    Canvas(modifier.testTag("recognition-reveal-optics")) {
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val gold = android.graphics.Color.rgb(220, 176, 91)
            val brightGold = android.graphics.Color.rgb(255, 227, 156)
            val drawSharedPhoto = frame.photoProgress < 1f || holdPhotoUntilHeroReady
            if (drawSharedPhoto) {
                val photoMatrix = activePhotoGeometry.photoMatrix(frame.photoProgress)
                val clip = activePhotoGeometry.photoClip(frame.photoProgress)
                val clipSave = canvas.save()
                sharedPhotoClipPath.reset()
                val cornerRadius = 20.dp.toPx() * frame.photoProgress.coerceIn(0f, 1f)
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
                    haloPaint.color = gold
                    haloPaint.alpha = (90 * frame.fishHaloAlpha).toInt()
                    haloPaint.strokeWidth = 28f * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, haloPaint)
                    haloPaint.alpha = (210 * frame.fishHaloAlpha).toInt()
                    haloPaint.strokeWidth = 13f * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, haloPaint)
                    corePaint.color = brightGold
                    corePaint.alpha = (230 * frame.fishHaloAlpha).toInt()
                    corePaint.strokeWidth = 4f * inverseScale
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

            if (activeRevealGeometry != null && transferMeasure != null &&
                (frame.transferProgress > 0f || frame.phase == RecognitionRevealPhase.GOLD_TRANSFER)
            ) {
                val progress = frame.transferProgress.coerceIn(0f, 1f)
                val headDistance = transferMeasure.length * progress
                val transferAlpha = (255 * frame.afterglowAlpha).toInt().coerceIn(0, 255)
                if (headDistance > 0f) {
                    transferTail.reset()
                    transferMeasure.getSegment(
                        (headDistance - transferMeasure.length * .32f).coerceAtLeast(0f),
                        headDistance,
                        transferTail,
                        true,
                    )
                    haloPaint.color = gold
                    haloPaint.alpha = (62 * frame.afterglowAlpha).toInt()
                    haloPaint.strokeWidth = 24f
                    canvas.drawPath(transferTail, haloPaint)
                    haloPaint.alpha = (165 * frame.afterglowAlpha).toInt()
                    haloPaint.strokeWidth = 10f
                    canvas.drawPath(transferTail, haloPaint)
                    corePaint.color = brightGold
                    corePaint.alpha = transferAlpha
                    corePaint.strokeWidth = 3.2f
                    canvas.drawPath(transferTail, corePaint)
                }
                if (transferMeasure.getPosTan(headDistance, transferPosition, null)) {
                    dotPaint.color = brightGold
                    dotPaint.alpha = transferAlpha
                    canvas.drawCircle(transferPosition[0], transferPosition[1], 7.5f, dotPaint)
                    dotPaint.color = gold
                    dotPaint.alpha = (95 * frame.afterglowAlpha).toInt()
                    canvas.drawCircle(transferPosition[0], transferPosition[1], 17f, dotPaint)
                }
                val count = frame.visibleParticleCount
                for (index in 0 until count) {
                    val lag = (index + 1f) / (count + 1f) * .27f
                    val distance = (progress - lag).coerceAtLeast(0f) * transferMeasure.length
                    if (transferMeasure.getPosTan(distance, transferPosition, null)) {
                        particlePaint.color = if (index % 3 == 0) brightGold else gold
                        particlePaint.alpha = (150 * frame.afterglowAlpha * (1f - index * .025f)).toInt()
                        val radius = if (index % 4 == 0) 3.0f else 1.8f
                        canvas.drawCircle(transferPosition[0], transferPosition[1], radius, particlePaint)
                    }
                }
            }

            val title = activeRevealGeometry?.titleBoundsLocal
            if (title != null && frame.phase == RecognitionRevealPhase.NAME_FORMATION && frame.titleFormationProgress > 0f) {
                for (index in formationParticleFractions.indices) {
                    val x = title.left + title.width * formationParticleFractions[index]
                    val y = title.top + title.height * (if (index % 2 == 0) .27f else .68f)
                    particlePaint.color = brightGold
                    particlePaint.alpha = (140 * frame.titleFormationProgress).toInt()
                    canvas.drawCircle(x, y, if (index % 2 == 0) 2.2f else 1.5f, particlePaint)
                }
            }
            if (title != null && frame.titleSweepProgress > 0f) {
                val x = title.left + title.width * frame.titleSweepProgress
                sweepPaint.color = brightGold
                sweepPaint.alpha = (46 * (1f - frame.titleSweepProgress)).toInt()
                sweepPaint.strokeWidth = title.height * .78f
                sweepPaint.strokeCap = AndroidPaint.Cap.ROUND
                canvas.drawLine(x, title.top + title.height * .1f, x, title.bottom - title.height * .1f, sweepPaint)
            }
        }
    }
}
