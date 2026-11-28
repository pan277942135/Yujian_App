package com.yujian.ai.ai.subject

import kotlin.math.floor
import kotlin.math.min

/** Immutable decoder result retaining all three original model candidates. */
data class SlimSamDecoderOutput(
    val iouScores: FloatArray,
    val predMasksLogits: FloatArray,
    val decoderDurationMs: Long,
) {
    init {
        require(iouScores.size == CANDIDATE_COUNT)
        require(predMasksLogits.size == CANDIDATE_COUNT * MASK_PLANE_ELEMENTS)
    }

    companion object {
        const val CANDIDATE_COUNT = 3
        const val MASK_LOGIT_SIZE = 256
        const val MASK_PLANE_ELEMENTS = MASK_LOGIT_SIZE * MASK_LOGIT_SIZE
    }
}

data class SlimSamCandidateDiagnostic(
    val index: Int,
    val iou: Float,
    val pixelCount: Int,
    val coverage: Float,
    val majorSpan: Float,
    val rankScore: Float,
    val geometryTrusted: Boolean,
    val contourRawPointCount: Int,
    val contourTerminationReason: BoundaryTerminationReason,
    val trusted: Boolean,
)

data class SlimSamMaskPostProcessResult(
    val roi: SlimSamMaskQualityGate.PixelRect?,
    val candidates: List<SlimSamCandidateDiagnostic>,
    val selectedCandidateIndex: Int?,
    /** LCC binary mask local to [roi]; present only for a fully trusted candidate. */
    val selectedMask: ByteArray?,
    val selectedIou: Float?,
    val contourRawPointCount: Int,
    val contourTerminationReason: BoundaryTerminationReason,
    val failureCode: String?,
) {
    val trusted: Boolean get() = selectedMask != null && selectedCandidateIndex != null && failureCode == null
}

/** Pixel-center postprocess parity port of the pinned reference SlimSAM mask pipeline. */
object SlimSamMaskPostProcessor {
    private const val MODEL_MASK_SIZE = SlimSamDecoderOutput.MASK_LOGIT_SIZE
    private const val PAD_SIZE = 1024

    fun postProcess(
        decoderOutput: SlimSamDecoderOutput,
        resizedWidth: Int,
        resizedHeight: Int,
        photoWidth: Int,
        photoHeight: Int,
        detection: SlimSamMaskQualityGate.DetectionGeometry?,
    ): SlimSamMaskPostProcessResult {
        if (detection == null || !detection.isBboxTrustworthy) {
            return SlimSamMaskPostProcessResult(
                roi = detection?.expandedRoi,
                candidates = emptyList(),
                selectedCandidateIndex = null,
                selectedMask = null,
                selectedIou = null,
                contourRawPointCount = 0,
                contourTerminationReason = BoundaryTerminationReason.INVALID_INPUT,
                failureCode = if (detection == null) "DETECTOR_GEOMETRY_INVALID" else "DETECTOR_BBOX_UNTRUSTED",
            )
        }
        val roi = detection.expandedRoi
        if (!roi.isValidWithin(photoWidth, photoHeight) || resizedWidth !in 1..PAD_SIZE || resizedHeight !in 1..PAD_SIZE) {
            return SlimSamMaskPostProcessResult(
                roi, emptyList(), null, null, null, 0,
                BoundaryTerminationReason.INVALID_INPUT, "MASK_GEOMETRY_INVALID",
            )
        }

        val scratch = FloatArray(PAD_SIZE * PAD_SIZE)
        val restore = RestorePlan.create(roi, resizedWidth, resizedHeight, photoWidth, photoHeight)
        val diagnostics = ArrayList<SlimSamCandidateDiagnostic>(SlimSamDecoderOutput.CANDIDATE_COUNT)
        var bestOverallIndex = -1
        var bestOverallScore = -Float.MAX_VALUE
        var bestTrustedIndex = -1
        var bestTrustedScore = -Float.MAX_VALUE
        var bestTrustedMask: ByteArray? = null
        var bestTrustedIou: Float? = null
        var bestTrustedRawCount = 0
        var bestTrustedTermination = BoundaryTerminationReason.INVALID_INPUT

        for (index in 0 until SlimSamDecoderOutput.CANDIDATE_COUNT) {
            val binaryRoi = postProcessCandidateLogits(
                logits256 = decoderOutput.predMasksLogits,
                logitOffset = index * SlimSamDecoderOutput.MASK_PLANE_ELEMENTS,
                resizedWidth = resizedWidth,
                resizedHeight = resizedHeight,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                roi = roi,
                scratch1024 = scratch,
                restore = restore,
            )
            val lcc = SlimSamContourGeometry.getLargestConnectedComponent(binaryRoi, roi.width, roi.height)
            binaryRoi.fill(0)
            val candidateBounds = if (lcc.pixelCount == 0) {
                SlimSamMaskQualityGate.PixelRect(0, 0, 0, 0)
            } else {
                SlimSamMaskQualityGate.PixelRect(
                    left = roi.left + lcc.bbox.x1,
                    top = roi.top + lcc.bbox.y1,
                    // Candidate spans use the reference PixelBox coordinate difference (x2 - x1).
                    rightExclusive = roi.left + lcc.bbox.x2,
                    bottomExclusive = roi.top + lcc.bbox.y2,
                )
            }
            val iou = decoderOutput.iouScores[index]
            val geometry = SlimSamMaskQualityGate.evaluate(
                SlimSamMaskQualityGate.CandidateGeometry(iou, lcc.pixelCount, candidateBounds), detection,
            )
            val trace = if (lcc.pixelCount == 0) {
                BoundaryTraceResult(emptyList(), false, BoundaryTerminationReason.INVALID_INPUT, 0)
            } else {
                SlimSamContourGeometry.extractBoundaryTrace(
                    lcc.cleanMask, roi.width, roi.height, lcc.startX, lcc.startY,
                )
            }
            val contour = SlimSamContourGeometry.contourFromBoundaryTrace(trace)
            val contourTrusted = trace.traceClosed &&
                trace.terminationReason == BoundaryTerminationReason.RETURNED_TO_START &&
                trace.points.size >= SlimSamMaskQualityGate.MIN_CLOSED_CONTOUR_POINTS && contour != null
            val trusted = geometry.geometryTrusted && contourTrusted
            val diagnostic = SlimSamCandidateDiagnostic(
                index = index,
                iou = iou,
                pixelCount = lcc.pixelCount,
                coverage = geometry.coverage,
                majorSpan = geometry.majorSpan,
                rankScore = geometry.rankScore,
                geometryTrusted = geometry.geometryTrusted,
                contourRawPointCount = trace.points.size,
                contourTerminationReason = trace.terminationReason,
                trusted = trusted,
            )
            diagnostics += diagnostic

            if (geometry.rankScore > bestOverallScore) {
                bestOverallScore = geometry.rankScore
                bestOverallIndex = index
            }
            if (trusted && geometry.rankScore > bestTrustedScore) {
                bestTrustedMask?.fill(0)
                bestTrustedMask = lcc.cleanMask
                bestTrustedIndex = index
                bestTrustedScore = geometry.rankScore
                bestTrustedIou = iou
                bestTrustedRawCount = trace.points.size
                bestTrustedTermination = trace.terminationReason
            } else {
                lcc.cleanMask.fill(0)
            }
        }

        val selected = diagnostics.getOrNull(bestTrustedIndex)
        if (selected == null) {
            val diagnostic = diagnostics.getOrNull(bestOverallIndex)
            return SlimSamMaskPostProcessResult(
                roi = roi,
                candidates = diagnostics,
                selectedCandidateIndex = diagnostic?.index,
                selectedMask = null,
                selectedIou = diagnostic?.iou,
                contourRawPointCount = diagnostic?.contourRawPointCount ?: 0,
                contourTerminationReason = diagnostic?.contourTerminationReason ?: BoundaryTerminationReason.INVALID_INPUT,
                failureCode = when {
                    !detection.isBboxTrustworthy -> "DETECTOR_BBOX_UNTRUSTED"
                    diagnostic == null -> "MASK_CANDIDATES_EMPTY"
                    !diagnostic.geometryTrusted -> "MASK_CANDIDATE_GEOMETRY_UNTRUSTED"
                    diagnostic.contourTerminationReason != BoundaryTerminationReason.RETURNED_TO_START -> "MASK_CONTOUR_NOT_CLOSED"
                    diagnostic.contourRawPointCount < SlimSamMaskQualityGate.MIN_CLOSED_CONTOUR_POINTS -> "MASK_CONTOUR_TOO_SHORT"
                    else -> "MASK_CANDIDATE_UNTRUSTED"
                },
            )
        }
        return SlimSamMaskPostProcessResult(
            roi = roi,
            candidates = diagnostics,
            selectedCandidateIndex = bestTrustedIndex,
            selectedMask = bestTrustedMask,
            selectedIou = bestTrustedIou,
            contourRawPointCount = bestTrustedRawCount,
            contourTerminationReason = bestTrustedTermination,
            failureCode = null,
        )
    }

    internal fun postProcessCandidateLogits(
        logits256: FloatArray,
        logitOffset: Int,
        resizedWidth: Int,
        resizedHeight: Int,
        photoWidth: Int,
        photoHeight: Int,
        roi: SlimSamMaskQualityGate.PixelRect,
        scratch1024: FloatArray = FloatArray(PAD_SIZE * PAD_SIZE),
        restore: RestorePlan = RestorePlan.create(roi, resizedWidth, resizedHeight, photoWidth, photoHeight),
    ): ByteArray {
        require(logits256.size >= logitOffset + MODEL_MASK_SIZE * MODEL_MASK_SIZE)
        require(resizedWidth in 1..PAD_SIZE && resizedHeight in 1..PAD_SIZE)
        require(photoWidth > 0 && photoHeight > 0 && roi.isValidWithin(photoWidth, photoHeight))
        require(scratch1024.size >= PAD_SIZE * PAD_SIZE)
        bilinearUpsampleCroppedRegion(
            src = logits256,
            srcOffset = logitOffset,
            srcW = MODEL_MASK_SIZE,
            srcH = MODEL_MASK_SIZE,
            fullDstW = PAD_SIZE,
            fullDstH = PAD_SIZE,
            cropW = resizedWidth,
            cropH = resizedHeight,
            dst = scratch1024,
        )
        val mask = ByteArray(Math.multiplyExact(roi.width, roi.height))
        for (y in 0 until roi.height) {
            val y0 = restore.y0[y]
            val y1 = restore.y1[y]
            val wy = restore.wy[y]
            val inverseWy = 1f - wy
            val row0 = y0 * resizedWidth
            val row1 = y1 * resizedWidth
            val dstRow = y * roi.width
            for (x in 0 until roi.width) {
                val x0 = restore.x0[x]
                val x1 = restore.x1[x]
                val wx = restore.wx[x]
                val inverseWx = 1f - wx
                val logits = (scratch1024[row0 + x0] * inverseWx + scratch1024[row0 + x1] * wx) * inverseWy +
                    (scratch1024[row1 + x0] * inverseWx + scratch1024[row1 + x1] * wx) * wy
                if (logits > 0f) mask[dstRow + x] = 0xff.toByte()
            }
        }
        return mask
    }

    internal data class RestorePlan(
        val x0: IntArray,
        val x1: IntArray,
        val wx: FloatArray,
        val y0: IntArray,
        val y1: IntArray,
        val wy: FloatArray,
    ) {
        companion object {
            fun create(
                roi: SlimSamMaskQualityGate.PixelRect,
                resizedWidth: Int,
                resizedHeight: Int,
                photoWidth: Int,
                photoHeight: Int,
            ): RestorePlan {
                val x0 = IntArray(roi.width)
                val x1 = IntArray(roi.width)
                val wx = FloatArray(roi.width)
                val xScale = resizedWidth.toFloat() / photoWidth
                for (x in 0 until roi.width) {
                    val sourceX = ((roi.left + x + 0.5f) * xScale - 0.5f)
                        .coerceIn(0f, (resizedWidth - 1).toFloat())
                    x0[x] = floor(sourceX).toInt()
                    x1[x] = min(resizedWidth - 1, x0[x] + 1)
                    wx[x] = sourceX - x0[x]
                }
                val y0 = IntArray(roi.height)
                val y1 = IntArray(roi.height)
                val wy = FloatArray(roi.height)
                val yScale = resizedHeight.toFloat() / photoHeight
                for (y in 0 until roi.height) {
                    val sourceY = ((roi.top + y + 0.5f) * yScale - 0.5f)
                        .coerceIn(0f, (resizedHeight - 1).toFloat())
                    y0[y] = floor(sourceY).toInt()
                    y1[y] = min(resizedHeight - 1, y0[y] + 1)
                    wy[y] = sourceY - y0[y]
                }
                return RestorePlan(x0, x1, wx, y0, y1, wy)
            }
        }
    }

    /** Reference align_corners=false resize from the 256 plane into the cropped 1024 grid. */
    internal fun bilinearUpsampleCroppedRegion(
        src: FloatArray,
        srcOffset: Int,
        srcW: Int,
        srcH: Int,
        fullDstW: Int,
        fullDstH: Int,
        cropW: Int,
        cropH: Int,
        dst: FloatArray,
    ) {
        val x0 = IntArray(cropW)
        val x1 = IntArray(cropW)
        val wx = FloatArray(cropW)
        val xScale = srcW.toFloat() / fullDstW
        for (x in 0 until cropW) {
            val sx = ((x + 0.5f) * xScale - 0.5f).coerceIn(0f, (srcW - 1).toFloat())
            x0[x] = floor(sx).toInt()
            x1[x] = min(srcW - 1, x0[x] + 1)
            wx[x] = sx - x0[x]
        }
        val yScale = srcH.toFloat() / fullDstH
        for (y in 0 until cropH) {
            val sy = ((y + 0.5f) * yScale - 0.5f).coerceIn(0f, (srcH - 1).toFloat())
            val sy0 = floor(sy).toInt()
            val sy1 = min(srcH - 1, sy0 + 1)
            val wy = sy - sy0
            val inverseWy = 1f - wy
            val row0 = srcOffset + sy0 * srcW
            val row1 = srcOffset + sy1 * srcW
            val dstRow = y * cropW
            for (x in 0 until cropW) {
                val inverseWx = 1f - wx[x]
                val x0i = x0[x]
                val x1i = x1[x]
                val v00 = src[row0 + x0i]
                val v10 = src[row0 + x1i]
                val v01 = src[row1 + x0i]
                val v11 = src[row1 + x1i]
                dst[dstRow + x] = (v00 * inverseWx + v10 * wx[x]) * inverseWy +
                    (v01 * inverseWx + v11 * wx[x]) * wy
            }
        }
    }
}
