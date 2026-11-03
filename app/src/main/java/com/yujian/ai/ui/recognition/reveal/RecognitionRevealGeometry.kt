package com.yujian.ai.ui.recognition.reveal

import android.graphics.Path
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.identify.calculateRecognitionImageTransformForSourceRect
import kotlin.math.abs

private fun RecognitionRevealWindowRect.localTo(origin: DisplayPoint) = copy(
    left = left - origin.x,
    top = top - origin.y,
    right = right - origin.x,
    bottom = bottom - origin.y,
)

private fun RecognitionRevealCubicPath.localTo(origin: DisplayPoint) = copy(
    start = start.copy(x = start.x - origin.x, y = start.y - origin.y),
    control1 = control1.copy(x = control1.x - origin.x, y = control1.y - origin.y),
    control2 = control2.copy(x = control2.x - origin.x, y = control2.y - origin.y),
    end = end.copy(x = end.x - origin.x, y = end.y - origin.y),
)

data class RecognitionRevealSourceMatrix(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
) {
    fun map(sourceX: Float, sourceY: Float): DisplayPoint =
        DisplayPoint(translationX + sourceX * scale, translationY + sourceY * scale)
}

data class RecognitionRevealHeroSnapshot(
    val boundsInWindow: RecognitionRevealWindowRect,
    /** Crop selected by FishPhotoCropPlanner; null means the full-source FIT fallback. */
    val cropRect: NormalizedSourceRect?,
    val renderReady: Boolean,
)

data class RecognitionRevealCubicPath(
    val start: DisplayPoint,
    val control1: DisplayPoint,
    val control2: DisplayPoint,
    val end: DisplayPoint,
) {
    fun toPath(): Path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
    }
}

data class RecognitionRevealPhotoGeometry(
    val rootWindowOrigin: DisplayPoint,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val processingStartMatrix: RecognitionRevealSourceMatrix,
    val heroEndMatrix: RecognitionRevealSourceMatrix,
    val processingPhotoClip: RecognitionRevealWindowRect,
    val processingPhotoCornerRadiusPx: Float,
    val heroPhotoClip: RecognitionRevealWindowRect,
    val heroSourceTransform: RecognitionImageTransform,
) {
    fun photoMatrix(progress: Float): RecognitionRevealSourceMatrix {
        val p = progress.coerceIn(0f, 1f)
        return RecognitionRevealSourceMatrix(
            scale = lerp(processingStartMatrix.scale, heroEndMatrix.scale, p),
            translationX = lerp(processingStartMatrix.translationX, heroEndMatrix.translationX, p),
            translationY = lerp(processingStartMatrix.translationY, heroEndMatrix.translationY, p),
        )
    }

    fun photoClip(progress: Float): RecognitionRevealWindowRect =
        lerp(processingPhotoClip.localTo(rootWindowOrigin), heroPhotoClip.localTo(rootWindowOrigin), progress)

    private fun lerp(a: Float, b: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return a + (b - a) * p
    }

    private fun lerp(
        a: RecognitionRevealWindowRect,
        b: RecognitionRevealWindowRect,
        progress: Float,
    ) = RecognitionRevealWindowRect(
        left = lerp(a.left, b.left, progress),
        top = lerp(a.top, b.top, progress),
        right = lerp(a.right, b.right, progress),
        bottom = lerp(a.bottom, b.bottom, progress),
    )
}

data class RecognitionRevealGeometry(
    val rootWindowOrigin: DisplayPoint,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val processingStartMatrix: RecognitionRevealSourceMatrix,
    val heroEndMatrix: RecognitionRevealSourceMatrix,
    val processingPhotoClip: RecognitionRevealWindowRect,
    val heroPhotoClip: RecognitionRevealWindowRect,
    val sourceContourPath: Path,
    val goldTransferPath: RecognitionRevealCubicPath,
    val titleBounds: RecognitionRevealWindowRect,
    val heroSourceTransform: RecognitionImageTransform,
    val orbReturnPath: RecognitionRevealCubicPath? = null,
    val orbToGlyphPath: RecognitionRevealCubicPath? = null,
    val glyphResiduePath: RecognitionRevealCubicPath? = null,
) {
    val processingPhotoClipLocal: RecognitionRevealWindowRect
        get() = processingPhotoClip.localTo(rootWindowOrigin)
    val heroPhotoClipLocal: RecognitionRevealWindowRect
        get() = heroPhotoClip.localTo(rootWindowOrigin)
    val titleBoundsLocal: RecognitionRevealWindowRect
        get() = titleBounds.localTo(rootWindowOrigin)
    val goldTransferPathLocal: RecognitionRevealCubicPath
        get() = goldTransferPath.localTo(rootWindowOrigin)
    val orbReturnPathLocal: RecognitionRevealCubicPath?
        get() = orbReturnPath?.localTo(rootWindowOrigin)
    val orbToGlyphPathLocal: RecognitionRevealCubicPath?
        get() = orbToGlyphPath?.localTo(rootWindowOrigin)
    val glyphResiduePathLocal: RecognitionRevealCubicPath?
        get() = glyphResiduePath?.localTo(rootWindowOrigin)

    fun photoMatrix(progress: Float): RecognitionRevealSourceMatrix {
        val p = progress.coerceIn(0f, 1f)
        return RecognitionRevealSourceMatrix(
            scale = lerp(processingStartMatrix.scale, heroEndMatrix.scale, p),
            translationX = lerp(processingStartMatrix.translationX, heroEndMatrix.translationX, p),
            translationY = lerp(processingStartMatrix.translationY, heroEndMatrix.translationY, p),
        )
    }

    fun photoClip(progress: Float): RecognitionRevealWindowRect =
        lerp(processingPhotoClip.localTo(rootWindowOrigin), heroPhotoClip.localTo(rootWindowOrigin), progress)

    private fun lerp(a: Float, b: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return a + (b - a) * p
    }

    private fun lerp(
        a: RecognitionRevealWindowRect,
        b: RecognitionRevealWindowRect,
        progress: Float,
    ) = RecognitionRevealWindowRect(
        left = lerp(a.left, b.left, progress),
        top = lerp(a.top, b.top, progress),
        right = lerp(a.right, b.right, progress),
        bottom = lerp(a.bottom, b.bottom, progress),
    )
}

/** Result of resolving the live Result layout into source-space reveal geometry. */
data class RecognitionRevealGeometryResolution(
    val geometry: RecognitionRevealGeometry?,
    val failureReason: String?,
)

object RecognitionRevealGeometryResolver {
    fun resolvePhoto(
        handoff: RecognitionRevealHandoff,
        hero: RecognitionRevealHeroSnapshot,
        rootBoundsInWindow: RecognitionRevealWindowRect,
    ): RecognitionRevealPhotoGeometry? {
        val processingBounds = handoff.processingPhotoViewportBounds ?: return null
        val processingTransform = handoff.processingPhotoTransform ?: return null
        if (handoff.imageId.isBlank() || handoff.normalizedSourceWidth <= 0 || handoff.normalizedSourceHeight <= 0) return null
        if (!processingBounds.isValid() || !hero.boundsInWindow.isValid() || !rootBoundsInWindow.isValid()) return null
        if (processingTransform.sourceWidthPx != handoff.normalizedSourceWidth ||
            processingTransform.sourceHeightPx != handoff.normalizedSourceHeight
        ) return null
        val heroTransform = runCatching {
            hero.cropRect?.let { crop ->
                calculateRecognitionImageTransformForSourceRect(
                    containerWidth = hero.boundsInWindow.width,
                    containerHeight = hero.boundsInWindow.height,
                    imageWidth = handoff.normalizedSourceWidth,
                    imageHeight = handoff.normalizedSourceHeight,
                    sourceRect = crop,
                )
            } ?: calculateRecognitionImageTransform(
                containerWidth = hero.boundsInWindow.width,
                containerHeight = hero.boundsInWindow.height,
                imageWidth = handoff.normalizedSourceWidth,
                imageHeight = handoff.normalizedSourceHeight,
                contentScaleMode = RecognitionContentScaleMode.FIT,
            )
        }.getOrNull() ?: return null
        return RecognitionRevealPhotoGeometry(
            rootWindowOrigin = DisplayPoint(rootBoundsInWindow.left, rootBoundsInWindow.top),
            sourceWidth = handoff.normalizedSourceWidth,
            sourceHeight = handoff.normalizedSourceHeight,
            processingStartMatrix = sourceMatrix(processingTransform, processingBounds, rootBoundsInWindow),
            heroEndMatrix = sourceMatrix(heroTransform, hero.boundsInWindow, rootBoundsInWindow),
            processingPhotoClip = handoff.processingPhotoClipBoundsInWindow
                ?.takeIf(RecognitionRevealWindowRect::isValid)
                ?: processingBounds,
            processingPhotoCornerRadiusPx = handoff.processingPhotoCornerRadiusPx
                .takeIf { it.isFinite() && it >= 0f } ?: 0f,
            heroPhotoClip = hero.boundsInWindow,
            heroSourceTransform = heroTransform,
        )
    }

    fun resolve(
        handoff: RecognitionRevealHandoff,
        hero: RecognitionRevealHeroSnapshot,
        titleBoundsInWindow: RecognitionRevealWindowRect,
        rootBoundsInWindow: RecognitionRevealWindowRect,
    ): RecognitionRevealGeometry? = resolveDetailed(
        handoff = handoff,
        hero = hero,
        titleBoundsInWindow = titleBoundsInWindow,
        rootBoundsInWindow = rootBoundsInWindow,
    ).geometry

    fun resolveDetailed(
        handoff: RecognitionRevealHandoff,
        hero: RecognitionRevealHeroSnapshot,
        titleBoundsInWindow: RecognitionRevealWindowRect,
        rootBoundsInWindow: RecognitionRevealWindowRect,
    ): RecognitionRevealGeometryResolution {
        val photoGeometry = resolvePhoto(handoff, hero, rootBoundsInWindow)
            ?: return rejected("INVALID_PHOTO_GEOMETRY")
        if (handoff.eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY) {
            return resolveDegradedOrbToGlyph(handoff, photoGeometry, titleBoundsInWindow, rootBoundsInWindow)
        }
        val subjectBox = handoff.subjectRoiInSource ?: return rejected("SUBJECT_BOX_MISSING")
        val anchor = handoff.subjectAnchorNormalizedSource ?: return rejected("SUBJECT_ANCHOR_MISSING")
        val contour = handoff.subjectMaskOrContour
        if (handoff.key.recognitionSessionId.isBlank() || handoff.imageId.isBlank()) return rejected("IDENTITY_INVALID")
        if (handoff.eligibility != RecognitionRevealEligibility.FULL_REVEAL) return rejected("NOT_FULL_REVEAL")
        if (handoff.geometryTrustState != RecognitionGeometryTrustState.TRUSTED_MASK) return rejected("GEOMETRY_UNTRUSTED")
        if (handoff.normalizedSourceWidth <= 0 || handoff.normalizedSourceHeight <= 0 || contour.isEmpty()) {
            return rejected("MASK_EMPTY_OR_SOURCE_SIZE_INVALID")
        }
        if (!titleBoundsInWindow.isValid() || !rootBoundsInWindow.isValid()) return rejected("LAYOUT_BOUNDS_INVALID")
        if (titleBoundsInWindow.top <= hero.boundsInWindow.bottom) return rejected("TITLE_OVERLAPS_HERO")
        if (titleBoundsInWindow.bottom > rootBoundsInWindow.bottom + 1f) return rejected("TITLE_OUTSIDE_ROOT")
        if (!isValidSourceBox(subjectBox) || !isValidSourcePoint(anchor)) return rejected("SUBJECT_SOURCE_GEOMETRY_INVALID")
        if (contour.any { segment ->
                listOf(
                    segment.startX, segment.startY, segment.endX, segment.endY,
                    segment.control1X, segment.control1Y, segment.control2X, segment.control2Y,
                ).any { !it.isFinite() || it !in 0f..1f }
            }
        ) return rejected("CONTOUR_COORDINATES_INVALID")

        val processingMatrix = photoGeometry.processingStartMatrix
        val sourceAnchorX = anchor.x * handoff.normalizedSourceWidth
        val sourceAnchorY = anchor.y * handoff.normalizedSourceHeight
        val mappedStartAnchor = processingMatrix.map(sourceAnchorX, sourceAnchorY)
        val reportedAnchor = handoff.processingSubjectAnchor ?: return rejected("PROCESSING_ANCHOR_MISSING")
        if (abs(mappedStartAnchor.x + rootBoundsInWindow.left - reportedAnchor.x) > 2f ||
            abs(mappedStartAnchor.y + rootBoundsInWindow.top - reportedAnchor.y) > 2f
        ) return rejected("PROCESSING_ANCHOR_TRANSFORM_MISMATCH")

        val targetLocalTransform = photoGeometry.heroSourceTransform
        val targetMatrix = photoGeometry.heroEndMatrix
        val targetAnchorLocal = targetMatrix.map(sourceAnchorX, sourceAnchorY)
        val targetAnchorWindow = DisplayPoint(
            targetAnchorLocal.x + rootBoundsInWindow.left,
            targetAnchorLocal.y + rootBoundsInWindow.top,
        )
        val targetTitle = DisplayPoint(
            x = titleBoundsInWindow.left + titleBoundsInWindow.width * .5f,
            y = titleBoundsInWindow.top + titleBoundsInWindow.height * .32f,
        )
        // Keep the V3 path as inert compatibility geometry; V4 renderers use the two Orb paths below.
        val path = transferCurve(targetAnchorWindow, targetTitle) ?: opticalCurve(targetAnchorWindow, targetTitle)
            ?: return rejected("TRANSFER_PATH_DOES_NOT_REACH_TITLE")
        val originalOrb = handoff.processingOrbCenterInWindow
            ?.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: return rejected("ORIGINAL_ORB_ANCHOR_MISSING")
        val orbReturnPath = opticalCurve(targetAnchorWindow, originalOrb)
            ?: return rejected("FISH_TO_ORB_PATH_INVALID")
        val orbToGlyphPath = opticalCurve(originalOrb, targetTitle)
            ?: return rejected("ORB_TO_GLYPH_PATH_INVALID")
        val residueAnchor = DisplayPoint(
            titleBoundsInWindow.right - titleBoundsInWindow.width * .08f,
            titleBoundsInWindow.bottom - titleBoundsInWindow.height * .12f,
        )
        val glyphResiduePath = opticalCurve(targetTitle, residueAnchor)
            ?: return rejected("GLYPH_RESIDUE_PATH_INVALID")
        return RecognitionRevealGeometryResolution(
            geometry = RecognitionRevealGeometry(
                rootWindowOrigin = DisplayPoint(rootBoundsInWindow.left, rootBoundsInWindow.top),
                sourceWidth = photoGeometry.sourceWidth,
                sourceHeight = photoGeometry.sourceHeight,
                processingStartMatrix = photoGeometry.processingStartMatrix,
                heroEndMatrix = photoGeometry.heroEndMatrix,
                processingPhotoClip = photoGeometry.processingPhotoClip,
                heroPhotoClip = photoGeometry.heroPhotoClip,
                sourceContourPath = sourceContourPath(contour,
                    if (handoff.contourSourceSpace == "CANONICAL_PHOTO_NORMALIZED")
                        NormalizedSourceRect(0f, 0f, 1f, 1f) else subjectBox,
                    handoff.normalizedSourceWidth, handoff.normalizedSourceHeight),
                goldTransferPath = path,
                titleBounds = titleBoundsInWindow,
                heroSourceTransform = targetLocalTransform,
                orbReturnPath = orbReturnPath,
                orbToGlyphPath = orbToGlyphPath,
                glyphResiduePath = glyphResiduePath,
            ),
            failureReason = null,
        )
    }

    /** Real-name fallback geometry: keeps the original Orb-to-glyph path and contains no fish contour. */
    private fun resolveDegradedOrbToGlyph(
        handoff: RecognitionRevealHandoff,
        photo: RecognitionRevealPhotoGeometry,
        title: RecognitionRevealWindowRect,
        root: RecognitionRevealWindowRect,
    ): RecognitionRevealGeometryResolution {
        if (handoff.key.recognitionSessionId.isBlank() || handoff.imageId.isBlank()) return rejected("IDENTITY_INVALID")
        if (!title.isValid() || !root.isValid() || title.top <= photo.heroPhotoClip.bottom ||
            title.bottom > root.bottom + 1f) return rejected("LAYOUT_BOUNDS_INVALID")
        val originalOrb = handoff.processingOrbCenterInWindow?.takeIf { point ->
            point.x.isFinite() && point.y.isFinite() && point.x in root.left..root.right && point.y in root.top..root.bottom
        } ?: return rejected("ORIGINAL_ORB_ANCHOR_MISSING")
        // When SlimSAM has no trusted contour, the perimeter is the only valid
        // energy source. Return from its measured photo edge; never substitute a
        // detector box or a fabricated fish anchor for the missing Mask.
        val perimeterStart = nearestPerimeterPoint(photo.processingPhotoClip, originalOrb)
            ?: return rejected("PHOTO_PERIMETER_ANCHOR_MISSING")
        val orbReturn = opticalCurve(perimeterStart, originalOrb)
            ?: return rejected("PERIMETER_TO_ORB_PATH_INVALID")
        val glyphAnchor = DisplayPoint(title.left + title.width * .5f, title.top + title.height * .38f)
        val orbToGlyph = opticalCurve(originalOrb, glyphAnchor) ?: return rejected("ORB_TO_GLYPH_PATH_INVALID")
        val residueAnchor = DisplayPoint(title.right - title.width * .08f, title.bottom - title.height * .12f)
        val residue = opticalCurve(glyphAnchor, residueAnchor) ?: return rejected("GLYPH_RESIDUE_PATH_INVALID")
        return RecognitionRevealGeometryResolution(
            geometry = RecognitionRevealGeometry(
                rootWindowOrigin = photo.rootWindowOrigin,
                sourceWidth = photo.sourceWidth,
                sourceHeight = photo.sourceHeight,
                processingStartMatrix = photo.processingStartMatrix,
                heroEndMatrix = photo.heroEndMatrix,
                processingPhotoClip = photo.processingPhotoClip,
                heroPhotoClip = photo.heroPhotoClip,
                sourceContourPath = Path(),
                goldTransferPath = orbReturn,
                titleBounds = title,
                heroSourceTransform = photo.heroSourceTransform,
                orbReturnPath = orbReturn,
                orbToGlyphPath = orbToGlyph,
                glyphResiduePath = residue,
            ),
            failureReason = null,
        )
    }

    private fun rejected(reason: String) = RecognitionRevealGeometryResolution(
        geometry = null,
        failureReason = reason,
    )

    private fun nearestPerimeterPoint(
        photoBoundsInWindow: RecognitionRevealWindowRect,
        targetInWindow: DisplayPoint,
    ): DisplayPoint? {
        if (!photoBoundsInWindow.isValid() || !targetInWindow.x.isFinite() || !targetInWindow.y.isFinite()) return null
        val bounds = photoBoundsInWindow
        val isOutside = targetInWindow.x !in bounds.left..bounds.right ||
            targetInWindow.y !in bounds.top..bounds.bottom
        if (isOutside) {
            return DisplayPoint(
                targetInWindow.x.coerceIn(bounds.left, bounds.right),
                targetInWindow.y.coerceIn(bounds.top, bounds.bottom),
            )
        }
        val candidates = listOf(
            DisplayPoint(bounds.left, targetInWindow.y) to (targetInWindow.x - bounds.left),
            DisplayPoint(bounds.right, targetInWindow.y) to (bounds.right - targetInWindow.x),
            DisplayPoint(targetInWindow.x, bounds.top) to (targetInWindow.y - bounds.top),
            DisplayPoint(targetInWindow.x, bounds.bottom) to (bounds.bottom - targetInWindow.y),
        )
        return candidates.minByOrNull { it.second }?.first
    }

    private fun sourceMatrix(
        transform: RecognitionImageTransform,
        viewport: RecognitionRevealWindowRect,
        root: RecognitionRevealWindowRect,
    ) = RecognitionRevealSourceMatrix(
        scale = transform.scale,
        translationX = viewport.left - root.left + transform.translationX,
        translationY = viewport.top - root.top + transform.translationY,
    )

    private fun sourceContourPath(
        contour: List<com.yujian.ai.ui.recognition.RecognitionContourSegment>,
        roi: NormalizedSourceRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Path = Path().apply {
        contour.forEach { segment ->
            val startX = (roi.left + segment.startX * roi.width) * sourceWidth
            val startY = (roi.top + segment.startY * roi.height) * sourceHeight
            val endX = (roi.left + segment.endX * roi.width) * sourceWidth
            val endY = (roi.top + segment.endY * roi.height) * sourceHeight
            val control1X = (roi.left + segment.control1X * roi.width) * sourceWidth
            val control1Y = (roi.top + segment.control1Y * roi.height) * sourceHeight
            val control2X = (roi.left + segment.control2X * roi.width) * sourceWidth
            val control2Y = (roi.top + segment.control2Y * roi.height) * sourceHeight
            if (segment.beginsContour) moveTo(startX, startY) else lineTo(startX, startY)
            cubicTo(control1X, control1Y, control2X, control2Y, endX, endY)
            if (segment.closesContour) close()
        }
    }

    private fun transferCurve(start: DisplayPoint, end: DisplayPoint): RecognitionRevealCubicPath? {
        val dy = end.y - start.y
        if (!dy.isFinite() || dy <= 1f) return null
        val dx = end.x - start.x
        return RecognitionRevealCubicPath(
            start = start,
            control1 = DisplayPoint(start.x + dx * .12f, start.y + dy * .56f),
            control2 = DisplayPoint(end.x - dx * .08f, end.y - dy * .34f),
            end = end,
        )
    }

    private fun opticalCurve(start: DisplayPoint, end: DisplayPoint): RecognitionRevealCubicPath? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = kotlin.math.hypot(dx, dy)
        if (!length.isFinite() || length < 1f) return null
        val bend = (length * .12f).coerceAtMost(40f)
        val sign = if (((dx.toInt() xor dy.toInt()) and 1) == 0) 1f else -1f
        val nx = -dy / length * bend * sign
        val ny = dx / length * bend * sign
        return RecognitionRevealCubicPath(
            start = start,
            control1 = DisplayPoint(start.x + dx * .28f + nx, start.y + dy * .28f + ny),
            control2 = DisplayPoint(start.x + dx * .72f + nx, start.y + dy * .72f + ny),
            end = end,
        )
    }

    private fun isValidSourceBox(rect: NormalizedSourceRect) =
        listOf(rect.left, rect.top, rect.right, rect.bottom).all(Float::isFinite) &&
            rect.left >= 0f && rect.top >= 0f && rect.right <= 1f && rect.bottom <= 1f &&
            rect.left < rect.right && rect.top < rect.bottom

    private fun isValidSourcePoint(point: DisplayPoint) =
        point.x.isFinite() && point.y.isFinite() && point.x in 0f..1f && point.y in 0f..1f

    private fun lerp(a: Float, b: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return a + (b - a) * p
    }

    private fun lerp(
        a: RecognitionRevealWindowRect,
        b: RecognitionRevealWindowRect,
        progress: Float,
    ) = RecognitionRevealWindowRect(
        left = lerp(a.left, b.left, progress),
        top = lerp(a.top, b.top, progress),
        right = lerp(a.right, b.right, progress),
        bottom = lerp(a.bottom, b.bottom, progress),
    )
}
