package com.yujian.ai.ui.recognition.result

import com.yujian.ai.ui.adaptive.AdaptiveLayoutProfile
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.imagery.FishPhotoCropPlanner
import com.yujian.ai.ui.imagery.FishPhotoDisplayMode
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.roundToInt

data class RecognitionResultGeometry(
    val horizontalMarginDp: Int,
    val heroWidthDp: Int,
    val heroHeightDp: Int,
    val candidateWidthDp: Float,
    val compactHeightPolicy: Boolean,
)

enum class RecognitionResultVisualState {
    HIGH,
    MEDIUM,
    LOW,
    NO_FISH,
    IMAGE_QUALITY,
}

/** Ambient fill is opted into only for the high-confidence and no-fish acceptance surfaces. */
fun RecognitionResultVisualState.usesSourceDerivedHeroBackdrop(): Boolean =
    this == RecognitionResultVisualState.HIGH || this == RecognitionResultVisualState.NO_FISH


/** Pure adaptive resolver for the frozen Recognition Result geometry. */
object RecognitionResultGeometryResolver {
    const val MAX_CONTENT_WIDTH_DP = 560
    fun resolve(
        profile: AdaptiveLayoutProfile,
        state: RecognitionResultVisualState = RecognitionResultVisualState.HIGH,
    ): RecognitionResultGeometry = resolve(
        windowWidthDp = profile.safeWidthDp.roundToInt(),
        contentHeightDp = profile.safeHeightDp.roundToInt(),
        state = state,
    )

    fun resolve(
        windowWidthDp: Int,
        contentHeightDp: Int,
        state: RecognitionResultVisualState = RecognitionResultVisualState.HIGH,
    ): RecognitionResultGeometry {
        val width = windowWidthDp.coerceAtMost(MAX_CONTENT_WIDTH_DP).coerceAtLeast(1)
        val margin = when {
            width <= 320 -> 16
            width <= 393 -> 19
            else -> 24
        }
        val contentWidth = (width - margin * 2).coerceAtLeast(1)
        val heroWidth = contentWidth
        val heroHeight = when (state) {
            RecognitionResultVisualState.HIGH -> (heroWidth * 210f / 322f).roundToInt()
            RecognitionResultVisualState.MEDIUM,
            RecognitionResultVisualState.LOW -> (heroWidth / 1.94f).roundToInt()
            RecognitionResultVisualState.NO_FISH -> (heroWidth * 245f / 322f).roundToInt()
            RecognitionResultVisualState.IMAGE_QUALITY -> (heroWidth / 1.62f).roundToInt()
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

    /** Keep ordinary Result pages free of an always-on scroll affordance. */
    fun usesScrollableResultPage(compactHeightPolicy: Boolean, accessibilityFontScale: Boolean): Boolean =
        compactHeightPolicy || accessibilityFontScale
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
) {
    /** Safe-fit and evidence-fit presentations use a same-source ambient fill behind the full photo. */
    val requiresSourceBackdrop: Boolean
        get() = mode != RecognitionHeroMediaMode.SUBJECT_CROP_FILL
}

enum class RecognitionSubjectVisibilityPolicy {
    FULL_SOURCE_SAFE,
    SUBJECT_SAFE_FIT,
}

/**
 * Frozen Processing media geometry. The primary transform is the only geometry
 * authority for the photo, bbox, halo, and contour. The background is decorative.
 */
data class RecognitionProcessingMediaPlan(
    val primarySourceRect: NormalizedSourceRect,
    val primaryContentScaleMode: RecognitionContentScaleMode,
    val primaryTransform: RecognitionImageTransform,
    val decorativeBackgroundTransform: RecognitionImageTransform?,
    val subjectVisibilityPolicy: RecognitionSubjectVisibilityPolicy,
)

enum class SourceEdge { LEFT, TOP, RIGHT, BOTTOM }

object RecognitionResultInputValidation {
    fun length(raw: String): String? = decimal(raw, 0.1, 999.9, 1, "请输入 0.1–999.9 cm 的长度")

    fun weight(raw: String): String? = decimal(raw, 0.01, 999.99, 2, "请输入 0.01–999.99 kg 的重量")

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

/** Existing metadata opens as editable numeric text with the caret at its logical end. */
fun resultNumericEditorInitialValue(rawValue: String): TextFieldValue {
    val text = rawValue
        .filter { it.isDigit() || it == '.' || it == ',' }
        .replace(',', '.')
        .take(8)
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

/** Result identity contains only the species name, without leading broken glyphs or placeholders. */
fun resultSpeciesDisplayName(value: String): String =
    value.trimStart().dropWhile { !it.isLetterOrDigit() }.trim()

/**
 * Plans a crop in the normalized, EXIF-oriented bitmap coordinate space.
 * SelectedImage is normalized before inference, so detector boxes use this same space.
 */
object RecognitionMediaPlanner {
    fun planProcessing(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        bbox: NormalizedFishBox? = null,
    ): RecognitionProcessingMediaPlan {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(viewportWidthPx > 0f && viewportHeightPx > 0f)

        // Processing has no safe opportunity to wait for a detector box before
        // its first frame. Full-source FIT therefore stays frozen for every
        // phase; a later bbox never causes a photo pan, zoom, or crop jump.
        val primary = calculateRecognitionImageTransform(
            containerWidth = viewportWidthPx,
            containerHeight = viewportHeightPx,
            imageWidth = sourceWidth,
            imageHeight = sourceHeight,
            contentScaleMode = RecognitionContentScaleMode.FIT,
        )
        val needsDecorativeFill =
            primary.drawnWidth < viewportWidthPx - 0.5f ||
                primary.drawnHeight < viewportHeightPx - 0.5f
        val decorative = if (needsDecorativeFill) {
            calculateRecognitionImageTransform(
                containerWidth = viewportWidthPx,
                containerHeight = viewportHeightPx,
                imageWidth = sourceWidth,
                imageHeight = sourceHeight,
                contentScaleMode = RecognitionContentScaleMode.CROP,
            )
        } else {
            null
        }
        return RecognitionProcessingMediaPlan(
            primarySourceRect = NormalizedSourceRect(0f, 0f, 1f, 1f),
            primaryContentScaleMode = RecognitionContentScaleMode.FIT,
            primaryTransform = primary,
            decorativeBackgroundTransform = decorative,
            subjectVisibilityPolicy = if (bbox != null && bbox.width > 0.001f && bbox.height > 0.001f) {
                RecognitionSubjectVisibilityPolicy.SUBJECT_SAFE_FIT
            } else {
                RecognitionSubjectVisibilityPolicy.FULL_SOURCE_SAFE
            },
        )
    }

    fun planResult(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        bbox: NormalizedSourceRect?,
        evidenceFirst: Boolean,
        bboxConfidence: Float? = null,
    ): RecognitionHeroMediaPlan {
        val full = NormalizedSourceRect(0f, 0f, 1f, 1f)
        if (evidenceFirst) return RecognitionHeroMediaPlan(RecognitionHeroMediaMode.EVIDENCE_FIT, full, emptySet())
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidthDp <= 0f || viewportHeightDp <= 0f ||
            bbox == null || bbox.left < 0f || bbox.top < 0f || bbox.right > 1f || bbox.bottom > 1f ||
            bbox.width <= 0.001f || bbox.height <= 0.001f
        ) return RecognitionHeroMediaPlan(RecognitionHeroMediaMode.SUBJECT_SAFE_FIT, full, emptySet())

        val clipped = buildSet {
            if (bbox.left <= 0.02f) add(SourceEdge.LEFT)
            if (bbox.top <= 0.02f) add(SourceEdge.TOP)
            if (1f - bbox.right <= 0.02f) add(SourceEdge.RIGHT)
            if (1f - bbox.bottom <= 0.02f) add(SourceEdge.BOTTOM)
        }
        val confidence = bboxConfidence ?: return RecognitionHeroMediaPlan(
            RecognitionHeroMediaMode.SUBJECT_SAFE_FIT,
            full,
            clipped,
        )
        val plan = FishPhotoCropPlanner.plan(
            sourceWidthPx = sourceWidth,
            sourceHeightPx = sourceHeight,
            targetAspectRatio = viewportWidthDp / viewportHeightDp,
            boundingBox = FishPhotoBoundingBox(
                box = NormalizedFishBox(bbox.left, bbox.top, bbox.right, bbox.bottom),
                confidence = confidence,
                version = "legacy-result-adapter-v1",
            ),
        )
        val crop = plan.cropRect ?: return RecognitionHeroMediaPlan(
            RecognitionHeroMediaMode.SUBJECT_SAFE_FIT,
            full,
            clipped,
        )
        return RecognitionHeroMediaPlan(
            mode = if (plan.mode == FishPhotoDisplayMode.SUBJECT_CROP) {
                RecognitionHeroMediaMode.SUBJECT_CROP_FILL
            } else {
                RecognitionHeroMediaMode.SUBJECT_SAFE_FIT
            },
            sourceRect = NormalizedSourceRect(
                crop.left / sourceWidth,
                crop.top / sourceHeight,
                crop.right / sourceWidth,
                crop.bottom / sourceHeight,
            ),
            sourceClippedEdges = clipped,
        )
    }
}

/** Backwards-compatible result entry point; the shared planner owns the policy. */
object RecognitionHeroMediaPlanner {
    fun plan(
        sourceWidth: Int,
        sourceHeight: Int,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        bbox: NormalizedSourceRect?,
        evidenceFirst: Boolean,
        bboxConfidence: Float? = null,
    ): RecognitionHeroMediaPlan = RecognitionMediaPlanner.planResult(
        sourceWidth,
        sourceHeight,
        viewportWidthDp,
        viewportHeightDp,
        bbox,
        evidenceFirst,
        bboxConfidence,
    )
}
