package com.yujian.ai.ai.subject

import com.yujian.ai.ai.NormalizedFishBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.min

class SlimSamMaskPostProcessorTest {
    @Test fun roiProcessingIsPixelIdenticalToFullFrameReferenceResizeThenClip() {
        val photoWidth = 480
        val photoHeight = 640
        val resizedWidth = 768
        val resizedHeight = 1024
        val roi = SlimSamMaskQualityGate.PixelRect(111, 173, 359, 533)
        val logits = FloatArray(256 * 256) { index ->
            val x = index % 256
            val y = index / 256
            ((x * 17 + y * 13) % 101 - 50) / 19f
        }

        val expectedFullFrame = fullFrameReference(logits, resizedWidth, resizedHeight, photoWidth, photoHeight)
        val expectedRoi = ByteArray(roi.width * roi.height)
        for (y in 0 until roi.height) {
            expectedFullFrame.copyInto(
                expectedRoi,
                destinationOffset = y * roi.width,
                startIndex = (roi.top + y) * photoWidth + roi.left,
                endIndex = (roi.top + y) * photoWidth + roi.rightExclusive,
            )
        }
        val actualRoi = SlimSamMaskPostProcessor.postProcessCandidateLogits(
            logits256 = logits,
            logitOffset = 0,
            resizedWidth = resizedWidth,
            resizedHeight = resizedHeight,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            roi = roi,
        )

        assertArrayEquals(expectedRoi, actualRoi)
    }

    @Test fun selectsOnlyTheHighestRankedCandidateThatPassesGeometryAndClosedContourGates() {
        val output = SlimSamDecoderOutput(
            iouScores = floatArrayOf(.91f, .99f, .80f),
            predMasksLogits = FloatArray(3 * 256 * 256) { -2f }.also { logits ->
                // Candidate 0 covers a centered 50x50 photo region and is within the detector box.
                for (y in 80 until 180) for (x in 80 until 180) logits[y * 256 + x] = 2f
                // Candidate 1 has a higher IoU but no positive pixels and cannot pass geometry.
            },
            decoderDurationMs = 100L,
        )
        val detection = trustedDetection()

        val result = SlimSamMaskPostProcessor.postProcess(
            decoderOutput = output,
            resizedWidth = 1024,
            resizedHeight = 1024,
            photoWidth = 128,
            photoHeight = 128,
            detection = detection,
        )

        assertTrue(result.trusted)
        assertEquals(0, result.selectedCandidateIndex)
        assertEquals(3, result.candidates.size)
        assertTrue(result.candidates[0].trusted)
        assertTrue(!result.candidates[1].trusted)
        assertNotNull(result.selectedMask)
        assertTrue(requireNotNull(result.selectedMask).count { it.toInt() != 0 } >= SlimSamMaskQualityGate.MIN_MASK_PIXELS)
        assertEquals(BoundaryTerminationReason.RETURNED_TO_START, result.contourTerminationReason)
        assertTrue(result.contourRawPointCount >= SlimSamMaskQualityGate.MIN_CLOSED_CONTOUR_POINTS)
    }

    @Test fun rejectsEveryCandidateWhenDetectorConfidenceIsBelowFrozenGate() {
        val result = SlimSamMaskPostProcessor.postProcess(
            decoderOutput = SlimSamDecoderOutput(
                iouScores = floatArrayOf(.99f, .98f, .97f),
                predMasksLogits = FloatArray(3 * 256 * 256) { 1f },
                decoderDurationMs = 10L,
            ),
            resizedWidth = 1024,
            resizedHeight = 1024,
            photoWidth = 128,
            photoHeight = 128,
            detection = SlimSamMaskQualityGate.detectionGeometry(
                NormalizedFishBox(.25f, .25f, .75f, .75f), .44f, 128, 128,
            ),
        )

        assertTrue(!result.trusted)
        assertNull(result.selectedMask)
        assertEquals("DETECTOR_BBOX_UNTRUSTED", result.failureCode)
    }

    private fun trustedDetection(): SlimSamMaskQualityGate.DetectionGeometry = requireNotNull(
        SlimSamMaskQualityGate.detectionGeometry(
            NormalizedFishBox(.25f, .25f, .75f, .75f), .91f, 128, 128,
        ),
    )

    /** Independent full-frame transcription of the pinned two-stage align_corners=false resize. */
    private fun fullFrameReference(
        logits: FloatArray,
        resizedWidth: Int,
        resizedHeight: Int,
        photoWidth: Int,
        photoHeight: Int,
    ): ByteArray {
        val upsampled = FloatArray(1024 * resizedHeight)
        for (y in 0 until resizedHeight) {
            val sy = ((y + .5f) * (256f / 1024f) - .5f).coerceIn(0f, 255f)
            val sy0 = floor(sy).toInt()
            val sy1 = min(255, sy0 + 1)
            val wy = sy - sy0
            for (x in 0 until resizedWidth) {
                val sx = ((x + .5f) * (256f / 1024f) - .5f).coerceIn(0f, 255f)
                val sx0 = floor(sx).toInt()
                val sx1 = min(255, sx0 + 1)
                val wx = sx - sx0
                val top = logits[sy0 * 256 + sx0] * (1f - wx) + logits[sy0 * 256 + sx1] * wx
                val bottom = logits[sy1 * 256 + sx0] * (1f - wx) + logits[sy1 * 256 + sx1] * wx
                upsampled[y * resizedWidth + x] = top * (1f - wy) + bottom * wy
            }
        }
        val result = ByteArray(photoWidth * photoHeight)
        for (y in 0 until photoHeight) {
            val sy = ((y + .5f) * (resizedHeight.toFloat() / photoHeight) - .5f)
                .coerceIn(0f, (resizedHeight - 1).toFloat())
            val y0 = floor(sy).toInt()
            val y1 = min(resizedHeight - 1, y0 + 1)
            val wy = sy - y0
            for (x in 0 until photoWidth) {
                val sx = ((x + .5f) * (resizedWidth.toFloat() / photoWidth) - .5f)
                    .coerceIn(0f, (resizedWidth - 1).toFloat())
                val x0 = floor(sx).toInt()
                val x1 = min(resizedWidth - 1, x0 + 1)
                val wx = sx - x0
                val top = upsampled[y0 * resizedWidth + x0] * (1f - wx) +
                    upsampled[y0 * resizedWidth + x1] * wx
                val bottom = upsampled[y1 * resizedWidth + x0] * (1f - wx) +
                    upsampled[y1 * resizedWidth + x1] * wx
                if (top * (1f - wy) + bottom * wy > 0f) result[y * photoWidth + x] = 0xff.toByte()
            }
        }
        return result
    }
}
