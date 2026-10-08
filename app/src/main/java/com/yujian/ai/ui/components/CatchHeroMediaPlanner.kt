package com.yujian.ai.ui.components

import kotlin.math.ceil
import kotlin.math.floor

enum class CatchHeroVariant { HOME, DETAIL_A }

enum class CatchHeroMediaMode {
    SUBJECT_CROP,
    SAFE_FIT_AMBIENT,
    EDGE_PROTECTED_FIT,
    EVIDENCE_FIT,
}

enum class CatchHeroBackgroundPolicy { SAME_SOURCE_AMBIENT, NONE }

enum class LetterboxConfidence { NONE, VERIFIED, UNCERTAIN }

data class NormalizedRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    fun isFiniteAndOrdered(): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            right > left && bottom > top

    fun isWithinUnitBounds(): Boolean =
        left >= 0.0 && top >= 0.0 && right <= 1.0 && bottom <= 1.0

    companion object {
        val FULL = NormalizedRect(0.0, 0.0, 1.0, 1.0)
    }
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class CatchHeroMediaRequest(
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val variant: CatchHeroVariant,
    /** Visible content rectangle after EXIF orientation and verified bar handling. */
    val orientedSourceRect: NormalizedRect = NormalizedRect.FULL,
    /** Coordinates are normalized to the oriented source image, not the device screen. */
    val trustedFishRect: NormalizedRect? = null,
    val trustedFishBox: Boolean = false,
    val fishTouchesSourceEdge: Boolean = false,
    val letterboxConfidence: LetterboxConfidence = LetterboxConfidence.NONE,
    val density: Float = 1f,
)

data class CatchHeroMediaPlan(
    val mode: CatchHeroMediaMode,
    val effectiveSourceRect: NormalizedRect,
    val sourceCropRectPx: PixelRect?,
    val safeSubjectRect: NormalizedRect?,
    val foregroundFit: Boolean,
    val backgroundPolicy: CatchHeroBackgroundPolicy,
    val reason: String,
    val layoutTransform: String,
)

/** Pure shared presentation policy for the HOME and DETAIL A-side Hero images. */
object CatchHeroMediaPlanner {
    private const val EDGE_GUARD = 0.02
    private const val HORIZONTAL_SUBJECT_PAD = 0.14
    private const val VERTICAL_SUBJECT_PAD = 0.18
    private const val MIN_VIEWPORT_INSET_DP = 12.0
    private const val MAX_SOURCE_ASPECT = 2.5
    private const val MIN_SOURCE_ASPECT = 1.0 / 2.5

    fun plan(request: CatchHeroMediaRequest): CatchHeroMediaPlan {
        val fullRect = request.orientedSourceRect.takeIf { it.isFiniteAndOrdered() && it.isWithinUnitBounds() }
            ?: NormalizedRect.FULL
        if (request.sourceWidthPx <= 0 || request.sourceHeightPx <= 0 ||
            request.viewportWidthPx <= 0 || request.viewportHeightPx <= 0 ||
            !request.density.isFinite() || request.density <= 0f ||
            !request.orientedSourceRect.isFiniteAndOrdered() || !request.orientedSourceRect.isWithinUnitBounds()
        ) {
            return fit(CatchHeroMediaMode.EVIDENCE_FIT, fullRect, "invalid_geometry")
        }

        if (request.letterboxConfidence == LetterboxConfidence.UNCERTAIN) {
            return fit(CatchHeroMediaMode.EVIDENCE_FIT, fullRect, "letterbox_uncertain")
        }

        val sourceAspect = request.sourceWidthPx.toDouble() / request.sourceHeightPx
        if (sourceAspect > MAX_SOURCE_ASPECT || sourceAspect < MIN_SOURCE_ASPECT) {
            return fit(CatchHeroMediaMode.EVIDENCE_FIT, fullRect, "extreme_source_aspect")
        }

        val fish = request.trustedFishRect
        if (!request.trustedFishBox || fish == null || !fish.isFiniteAndOrdered() || !fish.isWithinUnitBounds()) {
            return fit(CatchHeroMediaMode.EVIDENCE_FIT, fullRect, "trusted_fish_box_missing_or_invalid")
        }

        val edgeContact = request.fishTouchesSourceEdge ||
            fish.left <= EDGE_GUARD || fish.top <= EDGE_GUARD ||
            fish.right >= 1.0 - EDGE_GUARD || fish.bottom >= 1.0 - EDGE_GUARD
        if (edgeContact) {
            return fit(CatchHeroMediaMode.EDGE_PROTECTED_FIT, fullRect, "fish_touches_source_edge")
        }

        val safeSubject = NormalizedRect(
            left = (fish.left - fish.width * HORIZONTAL_SUBJECT_PAD).coerceAtLeast(0.0),
            top = (fish.top - fish.height * VERTICAL_SUBJECT_PAD).coerceAtLeast(0.0),
            right = (fish.right + fish.width * HORIZONTAL_SUBJECT_PAD).coerceAtMost(1.0),
            bottom = (fish.bottom + fish.height * VERTICAL_SUBJECT_PAD).coerceAtMost(1.0),
        )
        val sourceWidth = request.sourceWidthPx.toDouble()
        val sourceHeight = request.sourceHeightPx.toDouble()
        val viewportAspect = request.viewportWidthPx.toDouble() / request.viewportHeightPx
        val cropWidth = if (sourceAspect > viewportAspect) sourceHeight * viewportAspect else sourceWidth
        val cropHeight = if (sourceAspect > viewportAspect) sourceHeight else sourceWidth / viewportAspect

        if (safeSubject.width * sourceWidth > cropWidth || safeSubject.height * sourceHeight > cropHeight) {
            return fit(CatchHeroMediaMode.SAFE_FIT_AMBIENT, fullRect, "safe_subject_exceeds_candidate_crop", safeSubject)
        }

        val safeCenterX = safeSubject.centerX * sourceWidth
        val safeCenterY = safeSubject.centerY * sourceHeight
        val left = (safeCenterX - cropWidth / 2.0).coerceIn(0.0, sourceWidth - cropWidth)
        val top = (safeCenterY - cropHeight / 2.0).coerceIn(0.0, sourceHeight - cropHeight)
        val scale = request.viewportWidthPx.toDouble() / cropWidth
        val insetSourcePx = MIN_VIEWPORT_INSET_DP * request.density / scale
        val safeLeft = safeSubject.left * sourceWidth
        val safeTop = safeSubject.top * sourceHeight
        val safeRight = safeSubject.right * sourceWidth
        val safeBottom = safeSubject.bottom * sourceHeight

        if (safeLeft < left + insetSourcePx || safeTop < top + insetSourcePx ||
            safeRight > left + cropWidth - insetSourcePx || safeBottom > top + cropHeight - insetSourcePx
        ) {
            return fit(CatchHeroMediaMode.SAFE_FIT_AMBIENT, fullRect, "minimum_viewport_inset_not_met", safeSubject)
        }

        val crop = PixelRect(
            left = floor(left).toInt().coerceAtLeast(0),
            top = floor(top).toInt().coerceAtLeast(0),
            right = ceil(left + cropWidth).toInt().coerceAtMost(request.sourceWidthPx),
            bottom = ceil(top + cropHeight).toInt().coerceAtMost(request.sourceHeightPx),
        )
        if (crop.width <= 0 || crop.height <= 0) {
            return fit(CatchHeroMediaMode.EVIDENCE_FIT, fullRect, "candidate_crop_invalid", safeSubject)
        }

        val cropRect = NormalizedRect(
            crop.left / sourceWidth,
            crop.top / sourceHeight,
            crop.right / sourceWidth,
            crop.bottom / sourceHeight,
        )
        return CatchHeroMediaPlan(
            mode = CatchHeroMediaMode.SUBJECT_CROP,
            effectiveSourceRect = fullRect,
            sourceCropRectPx = crop,
            safeSubjectRect = safeSubject,
            foregroundFit = false,
            backgroundPolicy = CatchHeroBackgroundPolicy.NONE,
            reason = "trusted_subject_and_safety_padding_fit_${request.variant.name.lowercase()}",
            layoutTransform = "crop($crop)",
        )
    }

    private fun fit(
        mode: CatchHeroMediaMode,
        sourceRect: NormalizedRect,
        reason: String,
        safeSubjectRect: NormalizedRect? = null,
    ) = CatchHeroMediaPlan(
        mode = mode,
        effectiveSourceRect = sourceRect,
        sourceCropRectPx = null,
        safeSubjectRect = safeSubjectRect,
        foregroundFit = true,
        backgroundPolicy = CatchHeroBackgroundPolicy.SAME_SOURCE_AMBIENT,
        reason = reason,
        layoutTransform = "fit_full_visible_source",
    )
}

/** EXIF orientation values with normalized coordinate transforms shared by the loader and tests. */
enum class ExifOrientation(val exifValue: Int) {
    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90_CW(6),
    TRANSVERSE(7),
    ROTATE_270_CW(8),
    UNKNOWN(0);

    fun orientedSize(width: Int, height: Int): Pair<Int, Int> = when (this) {
        TRANSPOSE, ROTATE_90_CW, TRANSVERSE, ROTATE_270_CW -> height to width
        else -> width to height
    }

    fun mapRect(rect: NormalizedRect): NormalizedRect {
        val points = listOf(
            mapPoint(rect.left, rect.top),
            mapPoint(rect.right, rect.top),
            mapPoint(rect.left, rect.bottom),
            mapPoint(rect.right, rect.bottom),
        )
        return NormalizedRect(
            left = points.minOf { it.first },
            top = points.minOf { it.second },
            right = points.maxOf { it.first },
            bottom = points.maxOf { it.second },
        )
    }

    private fun mapPoint(x: Double, y: Double): Pair<Double, Double> = when (this) {
        NORMAL, UNKNOWN -> x to y
        FLIP_HORIZONTAL -> (1.0 - x) to y
        ROTATE_180 -> (1.0 - x) to (1.0 - y)
        FLIP_VERTICAL -> x to (1.0 - y)
        TRANSPOSE -> y to x
        ROTATE_90_CW -> (1.0 - y) to x
        TRANSVERSE -> (1.0 - y) to (1.0 - x)
        ROTATE_270_CW -> y to (1.0 - x)
    }

    companion object {
        fun fromExif(value: Int): ExifOrientation = entries.firstOrNull { it.exifValue == value } ?: UNKNOWN
    }
}
