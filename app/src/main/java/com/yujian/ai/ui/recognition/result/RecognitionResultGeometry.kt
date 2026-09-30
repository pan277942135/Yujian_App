package com.yujian.ai.ui.recognition.result

import kotlin.math.roundToInt

data class RecognitionResultGeometry(
    val horizontalMarginDp: Int,
    val heroWidthDp: Int,
    val heroHeightDp: Int,
    val candidateWidthDp: Float,
    val compactHeightPolicy: Boolean,
)

/** Pure adaptive resolver for the frozen Recognition Result geometry. */
object RecognitionResultGeometryResolver {
    fun resolve(windowWidthDp: Int, contentHeightDp: Int): RecognitionResultGeometry {
        val width = windowWidthDp.coerceAtLeast(1)
        val margin = when {
            width <= 320 -> 16
            width <= 393 -> 20
            else -> 24
        }
        val contentWidth = (width - margin * 2).coerceAtLeast(1)
        val heroWidth = contentWidth
        var heroHeight = when (width) {
            320 -> 224
            360 -> 248
            393 -> 274
            411 -> 282
            else -> (heroWidth / 1.2903f).roundToInt().coerceAtMost(282)
        }
        if (contentHeightDp < 600) {
            heroHeight = (heroHeight * 0.88f).roundToInt().coerceAtLeast(208)
        }
        return RecognitionResultGeometry(
            horizontalMarginDp = margin,
            heroWidthDp = heroWidth,
            heroHeightDp = heroHeight,
            candidateWidthDp = ((contentWidth - 16f) / 3f).coerceIn(88f, 116f),
            compactHeightPolicy = contentHeightDp < 600,
        )
    }

    /** Accessibility text scaling can make the fixed candidate labels compete for width. */
    fun usesScrollableCandidateRow(fontScale: Float): Boolean = fontScale >= 1.3f
}

enum class RecognitionHeroMediaMode { SUBJECT_CROP_FILL, SUBJECT_SAFE_FIT, EVIDENCE_FIT }

data class NormalizedSourceRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class RecognitionHeroMediaPlan(
    val mode: RecognitionHeroMediaMode,
    val sourceRect: NormalizedSourceRect,
    val sourceClippedEdges: Set<SourceEdge>,
)

enum class SourceEdge { LEFT, TOP, RIGHT, BOTTOM }

object RecognitionResultInputValidation {
    fun length(raw: String): String? = decimal(raw, 0.1, 999.9, 1, "请输入有效的长度")

    fun weight(raw: String): String? = decimal(raw, 0.01, 999.99, 2, "请输入有效的重量")

    fun takeUnicodeCodePoints(value: String, limit: Int): String {
        val end = value.offsetByCodePoints(0, value.codePointCount(0, value.length).coerceAtMost(limit))
        return value.substring(0, end)
    }

    private fun decimal(raw: String, min: Double, max: Double, decimals: Int, message: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val parsed = value.toDoubleOrNull() ?: return message
        if (parsed !in min..max || value.substringAfter('.', "").length > decimals) return message
        return null
    }
}

/**
 * Plans a crop in the normalized, EXIF-oriented bitmap coordinate space.
 * SelectedImage is normalized before inference, so detector boxes use this same space.
 */
object RecognitionHeroMediaPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        bbox: NormalizedSourceRect?,
        evidenceFirst: Boolean,
    ): RecognitionHeroMediaPlan {
        val full = NormalizedSourceRect(0f, 0f, 1f, 1f)
        if (evidenceFirst) return RecognitionHeroMediaPlan(RecognitionHeroMediaMode.EVIDENCE_FIT, full, emptySet())
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidthDp <= 24f || viewportHeightDp <= 24f ||
            bbox == null || !bbox.isValid()
        ) return RecognitionHeroMediaPlan(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, full, emptySet())

        val clipped = buildSet {
            if (bbox.left <= 0.02f) add(SourceEdge.LEFT)
            if (bbox.top <= 0.02f) add(SourceEdge.TOP)
            if (1f - bbox.right <= 0.02f) add(SourceEdge.RIGHT)
            if (1f - bbox.bottom <= 0.02f) add(SourceEdge.BOTTOM)
        }
        // A source-clipped fish edge must never receive another crop.
        if (clipped.isNotEmpty()) return RecognitionHeroMediaPlan(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, full, clipped)

        val safe = NormalizedSourceRect(
            left = (bbox.left - bbox.width * 0.14f).coerceAtLeast(0f),
            top = (bbox.top - bbox.height * 0.18f).coerceAtLeast(0f),
            right = (bbox.right + bbox.width * 0.14f).coerceAtMost(1f),
            bottom = (bbox.bottom + bbox.height * 0.18f).coerceAtMost(1f),
        )
        val targetAspect = viewportWidthDp / viewportHeightDp
        val minCropWidthPx = safe.width * sourceWidth / (1f - 24f / viewportWidthDp)
        val minCropHeightPx = safe.height * sourceHeight / (1f - 24f / viewportHeightDp)
        var cropWidthPx = maxOf(minCropWidthPx, minCropHeightPx * targetAspect)
        var cropHeightPx = cropWidthPx / targetAspect
        if (cropWidthPx > sourceWidth || cropHeightPx > sourceHeight) {
            cropHeightPx = minOf(sourceHeight.toFloat(), sourceWidth / targetAspect)
            cropWidthPx = cropHeightPx * targetAspect
        }
        val cropWidth = cropWidthPx / sourceWidth
        val cropHeight = cropHeightPx / sourceHeight
        val centerX = (safe.left + safe.right) / 2f
        val centerY = (safe.top + safe.bottom) / 2f
        val left = (centerX - cropWidth / 2f).coerceIn(0f, 1f - cropWidth)
        val top = (centerY - cropHeight / 2f).coerceIn(0f, 1f - cropHeight)
        val crop = NormalizedSourceRect(left, top, left + cropWidth, top + cropHeight)
        val insetX = crop.width * 12f / viewportWidthDp
        val insetY = crop.height * 12f / viewportHeightDp
        val contained = safe.left >= crop.left + insetX - 0.0001f &&
            safe.right <= crop.right - insetX + 0.0001f &&
            safe.top >= crop.top + insetY - 0.0001f &&
            safe.bottom <= crop.bottom - insetY + 0.0001f
        return if (contained) {
            RecognitionHeroMediaPlan(RecognitionHeroMediaMode.SUBJECT_CROP_FILL, crop, clipped)
        } else {
            RecognitionHeroMediaPlan(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, full, clipped)
        }
    }

    private fun NormalizedSourceRect.isValid(): Boolean =
        left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && width > 0.001f && height > 0.001f
}
