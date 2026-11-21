package com.yujian.ai.ui.imagery

import com.yujian.ai.ai.NormalizedFishBox
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Describes whether detector coordinates already use the displayed bitmap's axes. */
enum class FishBoxCoordinateSpace {
    DISPLAY_ORIENTED,
    RAW_EXIF_SOURCE,
}

/** EXIF orientation values in the standard 1–8 order. */
enum class PhotoExifOrientation(val value: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8),
    ;

    companion object {
        fun fromValue(value: Int): PhotoExifOrientation = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

data class FishPhotoBoundingBox(
    val box: NormalizedFishBox,
    val confidence: Float,
    val coordinateSpace: FishBoxCoordinateSpace = FishBoxCoordinateSpace.DISPLAY_ORIENTED,
    val exifOrientation: PhotoExifOrientation = PhotoExifOrientation.NORMAL,
    val version: String = "detector-bbox-v1",
)

data class PhotoCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val aspectRatio: Float get() = width / height

    fun contains(other: PhotoCropRect, tolerance: Float = 0.001f): Boolean =
        left <= other.left + tolerance && top <= other.top + tolerance &&
            right >= other.right - tolerance && bottom >= other.bottom - tolerance
}

enum class FishPhotoDisplayMode {
    SUBJECT_CROP,
    FULL_FRAME_WITH_SOURCE_BACKDROP,
}

enum class FishPhotoFallbackReason {
    NONE,
    CROPPING_DISABLED,
    MISSING_BBOX,
    LOW_CONFIDENCE,
    AMBIGUOUS_SUBJECT,
    INVALID_BBOX,
    UNREASONABLE_BBOX,
    ASPECT_CANNOT_CONTAIN_SUBJECT,
}

data class FishPhotoCropPlan(
    val mode: FishPhotoDisplayMode,
    val cropRect: PhotoCropRect?,
    /** The requested safety margin level, in percentage points, when cropping succeeds. */
    val marginLevelPercent: Int?,
    val attemptedMarginLevels: List<Int>,
    val fallbackReason: FishPhotoFallbackReason,
    val orientedBoundingBox: PhotoCropRect?,
) {
    val retainsWholeBoundingBox: Boolean
        get() = cropRect != null && orientedBoundingBox != null && cropRect.contains(orientedBoundingBox)
}

/**
 * Pure, display-only crop geometry. It never changes the bitmap used by the detector/classifier.
 * The 0.65 threshold belongs only to presentation safety, not to recognition quality gates.
 */
object FishPhotoCropPlanner {
    const val DISPLAY_CROP_CONFIDENCE_THRESHOLD = 0.65f
    const val ALGORITHM_VERSION = "fish-photo-bbox-safe-crop-v1"
    const val BBOX_COORDINATE_VERSION = "normalized-display-or-exif-v1"

    private const val INITIAL_HORIZONTAL_MARGIN_RATIO = 0.10f
    private const val INITIAL_VERTICAL_MARGIN_RATIO = 0.12f
    private const val MIN_INITIAL_MARGIN_PX = 4f
    private const val MIN_NON_ZERO_MARGIN_PX = 1f
    private const val MIN_BOX_SIDE_PX = 2f
    private const val MIN_BOX_AREA_RATIO = 0.00001f
    private const val MAX_BOX_AREA_RATIO = 0.98f
    private const val MAX_BOX_ASPECT_RATIO = 50f
    private val marginLevels = listOf(12, 8, 4, 0)

    fun mapBoxToDisplayedCoordinates(
        box: NormalizedFishBox,
        orientation: PhotoExifOrientation,
    ): NormalizedFishBox {
        if (orientation == PhotoExifOrientation.NORMAL) return box
        val corners = listOf(
            box.x1 to box.y1,
            box.x2 to box.y1,
            box.x1 to box.y2,
            box.x2 to box.y2,
        ).map { (x, y) -> mapPoint(x, y, orientation) }
        return NormalizedFishBox(
            x1 = corners.minOf { it.first },
            y1 = corners.minOf { it.second },
            x2 = corners.maxOf { it.first },
            y2 = corners.maxOf { it.second },
        )
    }

    fun plan(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        targetAspectRatio: Float,
        boundingBox: FishPhotoBoundingBox? = null,
        primaryAmbiguous: Boolean = false,
        allowSubjectCrop: Boolean = true,
        displayConfidenceThreshold: Float = DISPLAY_CROP_CONFIDENCE_THRESHOLD,
    ): FishPhotoCropPlan {
        require(sourceWidthPx > 0 && sourceHeightPx > 0)
        require(targetAspectRatio.isFinite() && targetAspectRatio > 0f)
        require(displayConfidenceThreshold.isFinite() && displayConfidenceThreshold in 0f..1f)

        if (!allowSubjectCrop) return fallback(FishPhotoFallbackReason.CROPPING_DISABLED)
        if (boundingBox == null) return fallback(FishPhotoFallbackReason.MISSING_BBOX)
        if (primaryAmbiguous) return fallback(FishPhotoFallbackReason.AMBIGUOUS_SUBJECT)
        if (!boundingBox.confidence.isFinite() || boundingBox.confidence !in 0f..1f ||
            boundingBox.confidence < displayConfidenceThreshold
        ) {
            return fallback(FishPhotoFallbackReason.LOW_CONFIDENCE)
        }

        val raw = boundingBox.box
        if (!raw.x1.isFinite() || !raw.y1.isFinite() || !raw.x2.isFinite() || !raw.y2.isFinite() ||
            raw.x1 < 0f || raw.y1 < 0f || raw.x2 > 1f || raw.y2 > 1f || raw.x1 >= raw.x2 || raw.y1 >= raw.y2
        ) return fallback(FishPhotoFallbackReason.INVALID_BBOX)

        val displayedBox = when (boundingBox.coordinateSpace) {
            FishBoxCoordinateSpace.DISPLAY_ORIENTED -> raw
            FishBoxCoordinateSpace.RAW_EXIF_SOURCE -> mapBoxToDisplayedCoordinates(raw, boundingBox.exifOrientation)
        }
        val bbox = PhotoCropRect(
            left = displayedBox.x1 * sourceWidthPx,
            top = displayedBox.y1 * sourceHeightPx,
            right = displayedBox.x2 * sourceWidthPx,
            bottom = displayedBox.y2 * sourceHeightPx,
        )
        val bboxWidthRatio = bbox.width / sourceWidthPx
        val bboxHeightRatio = bbox.height / sourceHeightPx
        val boxAspect = bbox.width / bbox.height
        val areaRatio = bboxWidthRatio * bboxHeightRatio
        if (bbox.width < MIN_BOX_SIDE_PX || bbox.height < MIN_BOX_SIDE_PX ||
            areaRatio < MIN_BOX_AREA_RATIO || areaRatio > MAX_BOX_AREA_RATIO ||
            boxAspect > MAX_BOX_ASPECT_RATIO || boxAspect < 1f / MAX_BOX_ASPECT_RATIO
        ) return fallback(FishPhotoFallbackReason.UNREASONABLE_BBOX, bbox)

        val attempted = mutableListOf<Int>()

        for (level in marginLevels) {
            attempted += level
            val marginX = marginForLevel(bbox.width, INITIAL_HORIZONTAL_MARGIN_RATIO, level)
            val marginY = marginForLevel(bbox.height, INITIAL_VERTICAL_MARGIN_RATIO, level)
            val (safeLeft, safeRight) = expandAxisWithOppositeSideTransfer(
                min = bbox.left,
                max = bbox.right,
                bound = sourceWidthPx.toFloat(),
                margin = marginX,
            )
            val (safeTop, safeBottom) = expandAxisWithOppositeSideTransfer(
                min = bbox.top,
                max = bbox.bottom,
                bound = sourceHeightPx.toFloat(),
                margin = marginY,
            )

            val safeWidth = safeRight - safeLeft
            val safeHeight = safeBottom - safeTop
            val cropWidth = max(safeWidth, targetAspectRatio * safeHeight)
            val cropHeight = cropWidth / targetAspectRatio
            if (cropWidth > sourceWidthPx + 0.001f || cropHeight > sourceHeightPx + 0.001f) continue

            val proposedLeft = (safeLeft + safeRight - cropWidth) / 2f
            val proposedTop = (safeTop + safeBottom - cropHeight) / 2f
            val cropLeft = proposedLeft.coerceIn(0f, sourceWidthPx - cropWidth)
            val cropTop = proposedTop.coerceIn(0f, sourceHeightPx - cropHeight)
            val crop = PhotoCropRect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
            val safeRect = PhotoCropRect(safeLeft, safeTop, safeRight, safeBottom)
            if (!crop.contains(bbox) || !crop.contains(safeRect)) continue

            return FishPhotoCropPlan(
                mode = FishPhotoDisplayMode.SUBJECT_CROP,
                cropRect = crop,
                marginLevelPercent = level,
                attemptedMarginLevels = attempted.toList(),
                fallbackReason = FishPhotoFallbackReason.NONE,
                orientedBoundingBox = bbox,
            )
        }

        return FishPhotoCropPlan(
            mode = FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP,
            cropRect = null,
            marginLevelPercent = null,
            attemptedMarginLevels = attempted.toList(),
            fallbackReason = FishPhotoFallbackReason.ASPECT_CANNOT_CONTAIN_SUBJECT,
            orientedBoundingBox = bbox,
        )
    }

    fun pixelsFor(box: NormalizedFishBox, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        val left = floor(box.x1 * width).toInt().coerceIn(0, width - 1)
        val top = floor(box.y1 * height).toInt().coerceIn(0, height - 1)
        val right = ceil(box.x2 * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil(box.y2 * height).toInt().coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }

    internal fun expandAxisWithOppositeSideTransfer(
        min: Float,
        max: Float,
        bound: Float,
        margin: Float,
    ): Pair<Float, Float> {
        var low = min - margin
        var high = max + margin
        if (low < 0f) {
            high += -low
            low = 0f
        }
        if (high > bound) {
            low -= high - bound
            high = bound
        }
        low = low.coerceAtLeast(0f)
        high = high.coerceAtMost(bound)
        return low to high
    }

    private fun marginForLevel(bboxSidePx: Float, initialRatio: Float, level: Int): Float = when (level) {
        0 -> 0f
        12 -> max(MIN_INITIAL_MARGIN_PX, bboxSidePx * initialRatio)
        else -> max(MIN_NON_ZERO_MARGIN_PX, bboxSidePx * level / 100f)
    }

    private fun mapPoint(x: Float, y: Float, orientation: PhotoExifOrientation): Pair<Float, Float> = when (orientation) {
        PhotoExifOrientation.NORMAL -> x to y
        PhotoExifOrientation.FLIP_HORIZONTAL -> (1f - x) to y
        PhotoExifOrientation.ROTATE_180 -> (1f - x) to (1f - y)
        PhotoExifOrientation.FLIP_VERTICAL -> x to (1f - y)
        PhotoExifOrientation.TRANSPOSE -> y to x
        PhotoExifOrientation.ROTATE_90 -> (1f - y) to x
        PhotoExifOrientation.TRANSVERSE -> (1f - y) to (1f - x)
        PhotoExifOrientation.ROTATE_270 -> y to (1f - x)
    }

    private fun fallback(reason: FishPhotoFallbackReason, bbox: PhotoCropRect? = null) = FishPhotoCropPlan(
        mode = FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP,
        cropRect = null,
        marginLevelPercent = null,
        attemptedMarginLevels = emptyList(),
        fallbackReason = reason,
        orientedBoundingBox = bbox,
    )
}
