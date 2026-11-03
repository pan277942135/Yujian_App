package com.yujian.ai.ui.recognition.reveal

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.waterlight.core.EnergyEvent
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.WaterGrassRenderer
import kotlin.math.roundToInt

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
    waterLightFrame: WaterLightFrame? = null,
) {
    val frame = frameState.value
    val sourceBitmap = bitmap ?: return
    if (handoff == null) return
    val activePhotoGeometry = photoGeometry ?: return
    val activeRevealGeometry = geometry
    if (!frame.drawOpticalLayer && !holdPhotoUntilHeroReady) return
    val density = LocalDensity.current
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
    val waterGrassRenderer = remember { WaterGrassRenderer() }

    Box(canvasModifier) {
    Canvas(Modifier.fillMaxSize()) {
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

            // V4: fish energy starts returning while the photo continuity morph runs during 0–200 ms.
            if (activeRevealGeometry != null && orbReturnMeasure != null &&
                frame.stage == RecognitionSceneStage.ORB_RETURN
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
                frame.stage == RecognitionSceneStage.ORB_TO_NAME
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
            }

            // Localized residue settles around the real title bounds and reaches zero at R5.
            if (activeRevealGeometry != null && frame.stage == RecognitionSceneStage.RESIDUE_SETTLE && !frame.isComplete) {
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

            // Water grass is a restrained residue around the measured real glyph bounds.
            // It uses the shared scene frame and fades with the RESIDUE_SETTLE stage.
            val residueFrame = waterLightFrame
            val glyphBounds = activeRevealGeometry?.titleBoundsLocal
            if (residueFrame != null && glyphBounds != null &&
                frame.stage == RecognitionSceneStage.RESIDUE_SETTLE && !frame.isComplete
            ) {
                val sideWidth = glyphBounds.width * .18f
                val fieldHeight = glyphBounds.height * .52f
                val top = glyphBounds.bottom - fieldHeight * .62f
                val leftField = RectF(glyphBounds.left, top, glyphBounds.left + sideWidth, glyphBounds.bottom)
                val rightField = RectF(glyphBounds.right - sideWidth, top, glyphBounds.right, glyphBounds.bottom)
                val grassAlpha = .45f * frame.afterglowAlpha
                val grassSeed = residueFrame.sessionId.hashCode().toLong() xor residueFrame.imageId.hashCode().toLong()
                waterGrassRenderer.draw(canvas, residueFrame, leftField, grassSeed, material, grassAlpha, energyEvent)
                waterGrassRenderer.draw(canvas, residueFrame, rightField, grassSeed xor 0x5A17L, material, grassAlpha, energyEvent)
            }

        }
    }
    val orbCenter = handoff.processingOrbCenterInWindow?.let { anchor ->
        activeRevealGeometry?.rootWindowOrigin?.let { origin ->
            (anchor.x - origin.x) to (anchor.y - origin.y)
        }
    }
    val orbDiameterPx = handoff.processingOrbCoreDiameterPx
    if (frame.drawOpticalLayer && activeRevealGeometry != null && orbCenter != null && orbDiameterPx > 1f &&
        frame.stage in setOf(RecognitionSceneStage.ORB_RETURN, RecognitionSceneStage.ORB_TO_NAME)
    ) {
        val coreSize = with(density) { orbDiameterPx.toDp() }
        val outerSizePx = with(density) { (coreSize + 28.dp).toPx() }
        val morph = if (frame.stage == RecognitionSceneStage.ORB_TO_NAME) frame.titleFormationProgress.coerceIn(0f, 1f) else 0f
        AIOrbCore(
            state = AIOrbVisualState.ORB_ACTIVE,
            modifier = Modifier.offset {
                IntOffset(
                    (orbCenter.first - outerSizePx / 2f).roundToInt(),
                    (orbCenter.second - outerSizePx / 2f).roundToInt(),
                )
            }.graphicsLayer {
                // This remains the same measured Processing Orb; it stretches and
                // releases its material toward the Glyph instead of being replaced by an oval.
                transformOrigin = TransformOrigin.Center
                scaleX = 1f + .62f * morph
                scaleY = 1f - .32f * morph
                alpha = 1f - morph
            },
            coreSize = coreSize,
            entranceElapsedMs = 900L,
            elapsedMs = waterLightFrame?.elapsedMs ?: frame.elapsedMs,
            completion = 0f,
            reduceMotion = waterLightFrame?.reducedMotion == true,
            lowPerformance = false,
            fromCamera = false,
            waterLightFrame = waterLightFrame,
            energyEvent = energyEvent,
            waterLightMaterial = WaterLightMaterialProfile.ORB_LEGACY,
        )
    }
    }
}
