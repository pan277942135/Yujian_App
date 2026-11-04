package com.yujian.ai.ui.recognition.reveal

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PathMeasure
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbCore
import com.yujian.ai.ui.designsystem.components.aiorb.AIOrbVisualState
import com.yujian.ai.ui.recognition.RecognitionContourMaterialCandidate
import com.yujian.ai.ui.recognition.RecognitionContourPathCache
import com.yujian.ai.ui.recognition.RecognitionPerimeterOpticalProfileV1
import com.yujian.ai.ui.recognition.RecognitionPerimeterShaderMath
import com.yujian.ai.ui.recognition.recognitionPerimeterOpacity
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
    val contourIdentity = remember(
        handoff.key,
        handoff.imageId,
        handoff.photoSourceIdentity,
        handoff.maskVersion,
        handoff.subjectMaskOrContour.hashCode(),
        waterLightFrame?.geometryVersion,
    ) {
        listOf(handoff.key, handoff.imageId, handoff.photoSourceIdentity,
            handoff.maskVersion, handoff.subjectMaskOrContour.hashCode(), waterLightFrame?.geometryVersion)
    }
    val contourMeasure = remember(activeRevealGeometry, contourIdentity) {
        activeRevealGeometry?.let { PathMeasure(it.sourceContourPath, false) }
    }
    val contourComposePath = remember(activeRevealGeometry, contourIdentity) {
        activeRevealGeometry?.sourceContourPath?.asComposePath()
    }
    val contourPathCache = remember(contourComposePath, contourIdentity) {
        contourComposePath?.let(::RecognitionContourPathCache)
    }
    val transferTail = remember { android.graphics.Path() }
    val sharedPhotoClipPath = remember { android.graphics.Path() }
    val perimeterPath = remember { android.graphics.Path() }
    val perimeterRect = remember { RectF() }
    val perimeterPathMeasure = remember { PathMeasure() }
    val perimeterPosition = remember { FloatArray(2) }
    val perimeterTangent = remember { FloatArray(2) }
    val bottomFlourishPath = remember { android.graphics.Path() }
    val transferPosition = remember { FloatArray(2) }
    val contourPosition = remember { FloatArray(2) }
    val mappedContourPosition = remember { FloatArray(2) }
    val transformValues = remember { FloatArray(9) }
    val contourBounds = remember(activeRevealGeometry, contourIdentity) {
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
    val contourUnderPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val contourGradientPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val contourHighlightPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val contourGoldPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val perimeterHaloPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val perimeterBloomPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val perimeterCorePaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val perimeterAccentPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
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
            val perimeterClip = activePhotoGeometry.photoClip(frame.photoProgress)
            if (drawSharedPhoto) {
                val photoMatrix = activePhotoGeometry.photoMatrix(frame.photoProgress)
                val clip = perimeterClip
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

                    val coreWidthPx = 2.2f.dp.toPx()
                    contourUnderPaint.color = android.graphics.Color.argb(
                        (255f * RecognitionContourMaterialCandidate.UNDER_STROKE_OPACITY * frame.fishHaloAlpha).toInt().coerceIn(0, 255),
                        6, 59, 70,
                    )
                    contourUnderPaint.strokeWidth = 3.8f.dp.toPx() * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, contourUnderPaint)

                    val contourColors = RecognitionContourMaterialCandidate.CORE_GRADIENT_COLORS.map { color ->
                        android.graphics.Color.argb(
                            (255f * .90f * frame.fishHaloAlpha).toInt().coerceIn(0, 255),
                            android.graphics.Color.red(color.toInt()),
                            android.graphics.Color.green(color.toInt()),
                            android.graphics.Color.blue(color.toInt()),
                        )
                    }.toIntArray()
                    contourGradientPaint.shader = LinearGradient(
                        contourBounds.left, contourBounds.top,
                        contourBounds.right.coerceAtLeast(contourBounds.left + 1f),
                        contourBounds.bottom.coerceAtLeast(contourBounds.top + 1f),
                        contourColors,
                        RecognitionContourMaterialCandidate.CORE_GRADIENT_OFFSETS.toFloatArray(),
                        Shader.TileMode.CLAMP,
                    )
                    contourGradientPaint.strokeWidth = coreWidthPx * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, contourGradientPaint)
                    contourGradientPaint.shader = null

                    contourHighlightPaint.color = android.graphics.Color.argb(
                        (255f * .60f * frame.fishHaloAlpha).toInt().coerceIn(0, 255),
                        221, 251, 255,
                    )
                    contourHighlightPaint.strokeWidth = coreWidthPx * .46f * inverseScale
                    canvas.drawPath(activeRevealGeometry.sourceContourPath, contourHighlightPaint)

                    val sharedReferenceMs = waterLightFrame?.sceneFrame?.opticalReferenceElapsedMs ?: 0L
                    val glintPhase = (sharedReferenceMs % RecognitionContourMaterialCandidate.GOLD_GLINT_PERIOD_MS)
                        .toFloat() / RecognitionContourMaterialCandidate.GOLD_GLINT_PERIOD_MS
                    val glint = if (contourComposePath != null && contourPathCache != null) {
                        contourPathCache.glint(contourComposePath, glintPhase,
                            RecognitionContourMaterialCandidate.GOLD_GLINT_ARC_RATIO).asAndroidPath()
                    } else null
                    if (glint != null && !glint.isEmpty) {
                        val goldGlintColors = intArrayOf(
                            android.graphics.Color.argb((255f * .10f * frame.fishHaloAlpha).toInt(), 255, 209, 102),
                            android.graphics.Color.argb((255f * .90f * frame.fishHaloAlpha).toInt(), 255, 228, 148),
                            android.graphics.Color.argb((255f * frame.fishHaloAlpha).toInt(), 255, 244, 208),
                            android.graphics.Color.argb((255f * .90f * frame.fishHaloAlpha).toInt(), 255, 209, 102),
                            android.graphics.Color.argb((255f * .10f * frame.fishHaloAlpha).toInt(), 229, 166, 45),
                        )
                        contourGoldPaint.shader = LinearGradient(
                            contourBounds.left, contourBounds.top,
                            contourBounds.right.coerceAtLeast(contourBounds.left + 1f),
                            contourBounds.bottom.coerceAtLeast(contourBounds.top + 1f),
                            goldGlintColors, floatArrayOf(0f, .3f, .5f, .7f, 1f), Shader.TileMode.CLAMP,
                        )
                        contourGoldPaint.strokeWidth = maxOf(1.4f.dp.toPx(), coreWidthPx * .95f) * inverseScale
                        canvas.drawPath(glint, contourGoldPaint)
                        contourGoldPaint.shader = null
                    }
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
            if (waterLightFrame != null) drawSharedPhotoPerimeter(
                canvas = canvas,
                clip = perimeterClip,
                density = density.density,
                frame = waterLightFrame,
                materialOpacity = material.opacity,
                path = perimeterPath,
                rect = perimeterRect,
                pathMeasure = perimeterPathMeasure,
                position = perimeterPosition,
                tangent = perimeterTangent,
                flourishPath = bottomFlourishPath,
                haloPaint = perimeterHaloPaint,
                bloomPaint = perimeterBloomPaint,
                corePaint = perimeterCorePaint,
                accentPaint = perimeterAccentPaint,
            )

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

/** Lightweight Result continuation of the same perimeter identity and Scene Frame. */
private fun drawSharedPhotoPerimeter(
    canvas: android.graphics.Canvas,
    clip: com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect,
    density: Float,
    frame: WaterLightFrame,
    materialOpacity: Float,
    path: android.graphics.Path,
    rect: RectF,
    pathMeasure: PathMeasure,
    position: FloatArray,
    tangent: FloatArray,
    flourishPath: android.graphics.Path,
    haloPaint: AndroidPaint,
    bloomPaint: AndroidPaint,
    corePaint: AndroidPaint,
    accentPaint: AndroidPaint,
) {
    val profile = RecognitionPerimeterOpticalProfileV1.EFFECT_02
    val opacity = recognitionPerimeterOpacity(true, frame.reducedMotion, frame) * materialOpacity
    if (opacity <= .001f || clip.width <= 1f || clip.height <= 1f) return
    val shortSide = minOf(clip.width, clip.height)
    val margin = shortSide * profile.marginRatio
    val cornerRadius = shortSide * profile.cornerRadiusRatio
    rect.set(clip.left + margin, clip.top + margin, clip.right - margin, clip.bottom - margin)
    path.reset()
    path.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)

    haloPaint.color = profile.goldColor.toInt()
    haloPaint.alpha = (255f * .12f * opacity).toInt().coerceIn(0, 255)
    haloPaint.strokeWidth = 12f * density
    canvas.drawPath(path, haloPaint)
    bloomPaint.color = profile.cyanColor.toInt()
    bloomPaint.alpha = (255f * .24f * opacity).toInt().coerceIn(0, 255)
    bloomPaint.strokeWidth = 5f * density
    canvas.drawPath(path, bloomPaint)
    corePaint.color = profile.greenColor.toInt()
    corePaint.alpha = (255f * .64f * opacity).toInt().coerceIn(0, 255)
    corePaint.strokeWidth = 1.25f * density
    canvas.drawPath(path, corePaint)

    pathMeasure.setPath(path, false)
    val totalLength = pathMeasure.length
    if (totalLength > 0f) {
        val scene = frame.sceneFrame
        val expansion = if (frame.reducedMotion) 1f else
            RecognitionPerimeterShaderMath.expansionProgress(scene, profile)
        val time = if (frame.reducedMotion) 0f else RecognitionPerimeterShaderMath.sourceTimeSec(scene, profile.speed)
        accentPaint.style = AndroidPaint.Style.FILL
        for (index in 0 until RecognitionPerimeterShaderMath.SPARKLE_COUNT) {
            val speed = .012f + (index % 3) * .007f
            val p = (index / RecognitionPerimeterShaderMath.SPARKLE_COUNT.toFloat() + time * speed) % 1f
            val active = p <= .5f * expansion || p >= 1f - .5f * expansion
            if (!active || !pathMeasure.getPosTan(totalLength * p, position, tangent)) continue
            val pulse = .65f + .35f * kotlin.math.sin(time * 3.4f + index * 1.7f)
            val goldNode = p > .45f
            val nodeSize = (profile.sparkleBaseSizeCssPx +
                (index % 4) * profile.sparkleSizeStepCssPx) * density
            accentPaint.color = (if (goldNode) profile.goldColor else profile.cyanColor).toInt()
            accentPaint.alpha = (255f * profile.sparkleHaloOpacity * pulse * opacity)
                .toInt().coerceIn(0, 255)
            canvas.drawCircle(position[0], position[1], nodeSize * profile.sparkleHaloScale * pulse, accentPaint)
            accentPaint.color = (if (goldNode) 0xFFFEF08AL else 0xFFE0F2FEL).toInt()
            accentPaint.alpha = (255f * profile.sparkleCoreOpacity * pulse * opacity)
                .toInt().coerceIn(0, 255)
            canvas.drawCircle(position[0], position[1], nodeSize * profile.sparkleCoreScale * pulse, accentPaint)
        }

        if (expansion > .15f) {
            val centerX = rect.centerX()
            val centerY = rect.bottom
            val pulse = .75f + .25f * kotlin.math.sin(time * 2.4f)
            accentPaint.color = android.graphics.Color.WHITE
            accentPaint.alpha = (255f * .92f * opacity).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX, centerY, 3f * density * pulse, accentPaint)
            flourishPath.reset()
            flourishPath.moveTo(centerX - profile.flourishHalfWidthCssPx * density, centerY)
            flourishPath.quadTo(centerX - profile.flourishHalfWidthCssPx * .5f * density,
                centerY - 2f * profile.flourishPeakHeightCssPx * density,
                centerX - 6f * density, centerY)
            flourishPath.moveTo(centerX + 6f * density, centerY)
            flourishPath.quadTo(centerX + profile.flourishHalfWidthCssPx * .5f * density,
                centerY + 2f * profile.flourishPeakHeightCssPx * density,
                centerX + profile.flourishHalfWidthCssPx * density, centerY)
            corePaint.color = profile.goldColor.toInt()
            corePaint.alpha = (255f * .55f * profile.intensity * opacity).toInt().coerceIn(0, 255)
            corePaint.strokeWidth = 1.3f * density
            canvas.drawPath(flourishPath, corePaint)
            accentPaint.color = android.graphics.Color.WHITE
            accentPaint.alpha = (255f * .88f * opacity).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX - profile.flourishHalfWidthCssPx * density, centerY, 1.4f * density, accentPaint)
            canvas.drawCircle(centerX + profile.flourishHalfWidthCssPx * density, centerY, 1.4f * density, accentPaint)
        }
    }
}
