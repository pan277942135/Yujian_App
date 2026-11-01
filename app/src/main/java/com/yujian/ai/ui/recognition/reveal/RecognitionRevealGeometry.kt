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
) {
    val processingPhotoClipLocal: RecognitionRevealWindowRect
        get() = processingPhotoClip.localTo(rootWindowOrigin)
    val heroPhotoClipLocal: RecognitionRevealWindowRect
        get() = heroPhotoClip.localTo(rootWindowOrigin)
    val titleBoundsLocal: RecognitionRevealWindowRect
        get() = titleBounds.localTo(rootWindowOrigin)
    val goldTransferPathLocal: RecognitionRevealCubicPath
        get() = goldTransferPath.localTo(rootWindowOrigin)

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
            ?: return rejected("PHOTO_GEOMETRY_INVALID")
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
        val path = transferCurve(targetAnchorWindow, targetTitle)
            ?: return rejected("TRANSFER_PATH_DOES_NOT_REACH_TITLE")
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
            ),
            failureReason = null,
        )
    }

    private fun rejected(reason: String) = RecognitionRevealGeometryResolution(
        geometry = null,
        failureReason = reason,
    )

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
