package com.yujian.ai.ai

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class FishInputStatus(val wireName: String) {
    READY("ready"),
    NO_FISH("no_fish"),
    UNCERTAIN("uncertain"),
    MULTIPLE_FISH("multiple_fish"),
    INCOMPLETE_FISH("incomplete_fish"),
    FISH_TOO_SMALL("fish_too_small"),
}

/**
 * Android UX quality level layered on top of the frozen detector contract.
 *
 * GOOD and WARNING are both classifier-eligible. INVALID is the only level that
 * blocks MODEL_M1_v0.6. This keeps common field photos usable when a tail touches
 * the frame or the fish is lightly occluded, while still blocking ambiguous input.
 */
enum class FishQualityLevel(val wireName: String) {
    GOOD("good"),
    WARNING("warning"),
    INVALID("invalid"),
}

data class FishInputAssessment(
    val status: FishInputStatus,
    val primary: FishDetection?,
    val cropBox: NormalizedFishBox?,
    val strongDetections: List<FishDetection>,
    val weakDetections: List<FishDetection>,
    val reason: String,
    val qualityLevel: FishQualityLevel = FishQualityLevel.GOOD,
) {
    val qualityReason: String get() = reason
    val bboxAreaRatio: Float? get() = primary?.areaRatio
    val isClassifierEligible: Boolean get() = qualityLevel != FishQualityLevel.INVALID && cropBox != null
}

object FishDetectionQualityGate {
    const val CONTRACT_VERSION = "RECOGNITION_PIPELINE_v1"
    const val QUALITY_GATE_VERSION = "QUALITY_GATE_v1.1"
    const val STRONG_CONFIDENCE = 0.35f
    const val WEAK_CONFIDENCE = 0.20f
    const val NMS_IOU = 0.45f
    const val MIN_PRIMARY_AREA_RATIO = 0.08f
    const val INCOMPLETE_EDGE_MARGIN_RATIO = 0.015f
    const val CROP_EXPAND_RATIO = 0.15f

    internal fun rankScore(detection: FishDetection): Float =
        detection.confidence.coerceAtLeast(0f) * sqrt(detection.areaRatio.coerceAtLeast(0f))

    fun assess(detections: List<FishDetection>): FishInputAssessment {
        val fish = detections
            .filter { it.className.equals("fish", ignoreCase = true) && it.box.areaRatio > 0f }
            .map { it.copy(box = it.box.normalized()) }

        val strong = fish
            .filter { it.confidence >= STRONG_CONFIDENCE }
            .sortedByDescending(::rankScore)
        val weak = fish
            .filter { it.confidence >= WEAK_CONFIDENCE && it.confidence < STRONG_CONFIDENCE }
            .sortedByDescending(::rankScore)

        if (strong.isEmpty()) {
            return if (weak.isNotEmpty()) {
                FishInputAssessment(
                    FishInputStatus.UNCERTAIN,
                    weak.first(),
                    weak.first().box.expand(CROP_EXPAND_RATIO),
                    strong,
                    weak,
                    "weak_fish_detection_only",
                    FishQualityLevel.WARNING,
                )
            } else {
                FishInputAssessment(
                    FishInputStatus.NO_FISH,
                    null,
                    null,
                    strong,
                    weak,
                    "no_fish_detection_above_weak_threshold",
                    FishQualityLevel.INVALID,
                )
            }
        }

        val primary = strong.first()
        if (strong.size >= 2) {
            // Multiple strong candidates are detector ambiguity, not proof that
            // the source image is unusable. Keep the highest-ranked fish as the
            // classifier crop so a clear handheld photo can still resolve to a
            // normal low-confidence result instead of IMAGE_QUALITY.
            return FishInputAssessment(
                FishInputStatus.MULTIPLE_FISH,
                primary,
                primary.box.expand(CROP_EXPAND_RATIO),
                strong,
                weak,
                "multiple_strong_fish_candidates_primary_selected",
                FishQualityLevel.WARNING,
            )
        }

        if (primary.box.touchesEdge(INCOMPLETE_EDGE_MARGIN_RATIO)) {
            return FishInputAssessment(
                FishInputStatus.INCOMPLETE_FISH,
                primary,
                primary.box.expand(CROP_EXPAND_RATIO),
                strong,
                weak,
                "primary_fish_bbox_touches_image_edge",
                FishQualityLevel.WARNING,
            )
        }

        if (primary.areaRatio < MIN_PRIMARY_AREA_RATIO) {
            return FishInputAssessment(
                FishInputStatus.FISH_TOO_SMALL,
                primary,
                null,
                strong,
                weak,
                "primary_fish_area_below_minimum",
                FishQualityLevel.INVALID,
            )
        }

        return FishInputAssessment(
            FishInputStatus.READY,
            primary,
            primary.box.expand(CROP_EXPAND_RATIO),
            strong,
            weak,
            "single_complete_fish_ready_for_classifier",
            FishQualityLevel.GOOD,
        )
    }

    /** Exact parity contract with backend: floor left/top, ceil right/bottom. */
    fun cropBoxPixels(
        box: NormalizedFishBox,
        width: Int,
        height: Int,
    ): IntArray {
        require(width > 0 && height > 0)
        val b = box.normalized()
        val left = floor(b.x1 * width).toInt().coerceIn(0, width - 1)
        val top = floor(b.y1 * height).toInt().coerceIn(0, height - 1)
        val right = ceil(b.x2 * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil(b.y2 * height).toInt().coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }
}
