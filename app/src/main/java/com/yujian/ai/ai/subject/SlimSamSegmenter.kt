package com.yujian.ai.ai.subject

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Production SlimSAM on-device inference engine.
 *
 * Implements real end-to-end pixel segmentation:
 * Source Bitmap + Detector BBOX -> SlimSAM Vision Encoder -> Mask Decoder -> Clean Mask Bitmap.
 *
 * Models bundled in Android assets:
 * - slimsam/vision_encoder_quantized.onnx (~8.5MB)
 * - slimsam/prompt_encoder_mask_decoder_quantized.onnx (~4.7MB)
 *
 * Photo Image Encoder is executed once per photo and cached for subsequent BBOX prompts.
 */
class SlimSamSegmenter(
    private val context: Context,
    private val expandRatio: Float = 0.12f,
) : AutoCloseable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val sessionOptions: OrtSession.SessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
    }

    private var visionSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private data class CachedVisionEmbedding(
        val bitmapId: Int,
        val width: Int,
        val height: Int,
        val scale: Float,
        val targetW: Int,
        val targetH: Int,
        val imageEmbeddings: FloatBuffer, // 1 * 256 * 64 * 64
        val imagePositionalEmbeddings: FloatBuffer, // 1 * 256 * 64 * 64
    )

    private var cachedEmbedding: CachedVisionEmbedding? = null

    private val outputDir = File(context.cacheDir, "fish_subject_previews").apply { mkdirs() }

    @Synchronized
    private fun getVisionSession(): OrtSession {
        visionSession?.let { Log.i(TAG, "SLIMSAM_MODEL_REUSE model=vision"); return it }
        val started = SystemClock.uptimeMillis()
        Log.i(TAG, "SLIMSAM_MODEL_LOAD_START model=vision")
        val bytes = readAssetBytes(VISION_ENCODER_MODEL)
        return environment.createSession(bytes, sessionOptions).also {
            visionSession = it
            Log.i(TAG, "SLIMSAM_MODEL_LOAD_END model=vision bytes=${bytes.size} duration_ms=${SystemClock.uptimeMillis() - started}")
        }
    }

    @Synchronized
    private fun getDecoderSession(): OrtSession {
        decoderSession?.let { Log.i(TAG, "SLIMSAM_MODEL_REUSE model=decoder"); return it }
        val started = SystemClock.uptimeMillis()
        Log.i(TAG, "SLIMSAM_MODEL_LOAD_START model=decoder")
        val bytes = readAssetBytes(MASK_DECODER_MODEL)
        return environment.createSession(bytes, sessionOptions).also {
            decoderSession = it
            Log.i(TAG, "SLIMSAM_MODEL_LOAD_END model=decoder bytes=${bytes.size} duration_ms=${SystemClock.uptimeMillis() - started}")
        }
    }

    suspend fun segment(
        source: Bitmap,
        detectorBbox: NormalizedFishBox,
        sessionId: String = "",
        attemptId: String = "",
        imageId: String = "",
        photoSha256: String = "",
    ): FishSubjectResult =
        withContext(Dispatchers.Default) {
            val started = System.nanoTime()
            val origW = source.width
            val origH = source.height
            if (origW <= 0 || origH <= 0) {
                return@withContext failed(
                    code = "INVALID_BITMAP_DIMENSIONS",
                    started = started,
                    errorMessage = "Bitmap dimensions must be greater than zero",
                )
            }

            // 1. Crop bounding box coordinates on original image
            val expandedBox = detectorBbox.expand(expandRatio)
            val cropPixels = FishDetectionQualityGate.cropBoxPixels(expandedBox, origW, origH)
            val roiX1 = cropPixels[0]
            val roiY1 = cropPixels[1]
            val roiX2 = cropPixels[2]
            val roiY2 = cropPixels[3]
            val roiW = max(1, roiX2 - roiX1)
            val roiH = max(1, roiY2 - roiY1)
            val expectedMaskSize = roiW * roiH
            Log.i(TAG, "SLIMSAM_ROI source=${origW}x$origH roi=${roiW}x$roiH bbox=$detectorBbox uptime_ms=${SystemClock.uptimeMillis()}")

            try {
                // 2. Ensure Vision Encoder embeddings for this photo are computed
                val encoderStarted = SystemClock.uptimeMillis()
                Log.i(TAG, "SLIMSAM_ENCODER_START uptime_ms=$encoderStarted")
                val embedding = getOrComputeImageEmbedding(source)
                Log.i(TAG, "SLIMSAM_ENCODER_END duration_ms=${SystemClock.uptimeMillis() - encoderStarted}")

                // 3. Generate prompts from detector BBOX
                val absBx1 = detectorBbox.x1.coerceIn(0f, 1f) * origW
                val absBy1 = detectorBbox.y1.coerceIn(0f, 1f) * origH
                val absBx2 = detectorBbox.x2.coerceIn(0f, 1f) * origW
                val absBy2 = detectorBbox.y2.coerceIn(0f, 1f) * origH

                val points = generateBoxPoints(
                    absBx1, absBy1, absBx2, absBy2,
                    embedding.scale, origW, origH,
                )

                // 4. Run Mask Decoder
                val decoderStarted = SystemClock.uptimeMillis()
                Log.i(TAG, "SLIMSAM_DECODER_START uptime_ms=$decoderStarted")
                val (bestMask256, iouScore) = runMaskDecoder(embedding, points)
                Log.i(TAG, "SLIMSAM_DECODER_END duration_ms=${SystemClock.uptimeMillis() - decoderStarted} iou=$iouScore")

                val gridToPhoto = Matrix().apply { setScale(4f / embedding.scale, 4f / embedding.scale) }
                val geometry = MaskGeometryMetadata(
                    sessionId = sessionId, attemptId = attemptId, imageId = imageId, photoSha256 = photoSha256,
                    canonicalWidth = origW, canonicalHeight = origH,
                    roiPx = Rect(roiX1, roiY1, roiX2, roiY2), modelInputSize = 1024,
                    resizedWidth = embedding.targetW, resizedHeight = embedding.targetH,
                    padLeft = 0, padTop = 0,
                    padRight = 1024 - embedding.targetW, padBottom = 1024 - embedding.targetH,
                    maskGridWidth = 256, maskGridHeight = 256,
                    modelToPhotoTransform = gridToPhoto,
                    pixelCenterConvention = "TOP_LEFT_ZERO_BASED", threshold = FishSubjectQualityGate.MASK_THRESHOLD,
                    contourVersion = "GRID_CONTOUR_V1",
                    modelSha256 = "vision:cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971;decoder:cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2",
                )

                // 5. Bilinearly sample 256x256 logits into the ROI mask
                val rawConfidence = FloatArray(expectedMaskSize)
                val scale = embedding.scale
                for (ry in 0 until roiH) {
                    val origY = roiY1 + ry
                    val maskY = (origY * scale / 4f).coerceIn(0f, 255f)
                    val rowOffset = ry * roiW
                    for (rx in 0 until roiW) {
                        val origX = roiX1 + rx
                        val maskX = (origX * scale / 4f).coerceIn(0f, 255f)
                        val logit = sampleBilinear(bestMask256, maskX, maskY)
                        rawConfidence[rowOffset + rx] = sigmoid(logit)
                    }
                }

                // 6. Clean with Largest Connected Component (LCC) to remove stray artifacts
                val cleanConfidence = filterLargestConnectedComponent(rawConfidence, roiW, roiH)

                // 7. Assess quality
                val quality = FishSubjectQualityGate.assess(cleanConfidence, roiW, roiH)
                val foregroundCount = cleanConfidence.count { it >= FishSubjectQualityGate.MASK_THRESHOLD }
                val areaRatio = foregroundCount.toFloat() / expectedMaskSize
                Log.i(TAG, "SLIMSAM_QUALITY quality=$quality area_ratio=$areaRatio roi=${roiW}x$roiH iou=$iouScore uptime_ms=${SystemClock.uptimeMillis()}")

                if (quality == FishSubjectQuality.INVALID) {
                    return@withContext failed(
                        code = "SUBJECT_QUALITY_INVALID",
                        started = started,
                        roiWidth = roiW,
                        roiHeight = roiH,
                        expectedMaskSize = expectedMaskSize,
                        maskSize = expectedMaskSize,
                        area = areaRatio,
                        quality = quality,
                        maskIouScore = iouScore.takeIf { it.isFinite() },
                        errorMessage = "Segmentation mask did not satisfy quality gate",
                    )
                }

                // 8. Generate transparent PNG subject bitmap
                val roiBitmap = Bitmap.createBitmap(source, roiX1, roiY1, roiW, roiH)
                val colors = IntArray(expectedMaskSize)
                for (i in 0 until expectedMaskSize) {
                    val conf = cleanConfidence[i].coerceIn(0f, 1f)
                    val alpha = (conf * 255f).roundToInt()
                    val px = roiBitmap.getPixel(i % roiW, i / roiW)
                    colors[i] = Color.argb(alpha, Color.red(px), Color.green(px), Color.blue(px))
                }
                roiBitmap.recycle()

                val subjectBitmap = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
                subjectBitmap.setPixels(colors, 0, roiW, 0, 0, roiW, roiH)

                val file = File(outputDir, "slimsam_${UUID.randomUUID()}.png")
                file.outputStream().use { out ->
                    check(subjectBitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                }
                subjectBitmap.recycle()

                val elapsed = (System.nanoTime() - started) / 1_000_000
                Log.d(TAG, "SlimSAM segment success in ${elapsed}ms, iou=$iouScore, area=$areaRatio")

                FishSubjectResult(
                    status = SubjectStatus.READY,
                    bitmapPath = file.absolutePath,
                    width = roiW,
                    height = roiH,
                    processingMs = elapsed,
                    roiWidth = roiW,
                    roiHeight = roiH,
                    maskSize = expectedMaskSize,
                    expectedMaskSize = expectedMaskSize,
                    maskAreaRatio = areaRatio,
                    quality = quality,
                    maskIouScore = iouScore.takeIf { it.isFinite() },
                    geometry = geometry,
                )
            } catch (error: Throwable) {
                Log.e(TAG, "SlimSAM segmentation failure", error)
                failed(
                    code = error::class.java.simpleName.ifBlank { "SLIMSAM_EXECUTION_FAILED" },
                    started = started,
                    roiWidth = roiW,
                    roiHeight = roiH,
                    expectedMaskSize = expectedMaskSize,
                    errorMessage = error.message ?: "SlimSAM execution failed",
                )
            }
        }

    private fun getOrComputeImageEmbedding(source: Bitmap): CachedVisionEmbedding {
        val bitmapId = source.generationId
        val existing = cachedEmbedding
        if (existing != null && existing.bitmapId == bitmapId &&
            existing.width == source.width && existing.height == source.height
        ) {
            existing.imageEmbeddings.rewind()
            existing.imagePositionalEmbeddings.rewind()
            return existing
        }

        val origW = source.width
        val origH = source.height
        val scale = 1024f / max(origW, origH)
        val targetW = (origW * scale).roundToInt().coerceIn(1, 1024)
        val targetH = (origH * scale).roundToInt().coerceIn(1, 1024)

        // Resize bitmap to target dimensions
        val resized = if (origW == targetW && origH == targetH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }

        val pixels = IntArray(targetW * targetH)
        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (resized !== source) {
            resized.recycle()
        }

        // Buffer for pixel_values: 1 * 3 * 1024 * 1024 floats
        val totalElements = 1 * 3 * 1024 * 1024
        val pixelBuffer = ByteBuffer.allocateDirect(totalElements * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val rMean = 0.485f; val rStd = 0.229f
        val gMean = 0.456f; val gStd = 0.224f
        val bMean = 0.406f; val bStd = 0.225f

        val planeSize = 1024 * 1024

        // Channel R
        for (y in 0 until 1024) {
            val hasY = y < targetH
            val yOffset = y * targetW
            for (x in 0 until 1024) {
                if (hasY && x < targetW) {
                    val c = pixels[yOffset + x]
                    val r = (Color.red(c) / 255f - rMean) / rStd
                    pixelBuffer.put(r)
                } else {
                    pixelBuffer.put(0f)
                }
            }
        }
        // Channel G
        for (y in 0 until 1024) {
            val hasY = y < targetH
            val yOffset = y * targetW
            for (x in 0 until 1024) {
                if (hasY && x < targetW) {
                    val c = pixels[yOffset + x]
                    val g = (Color.green(c) / 255f - gMean) / gStd
                    pixelBuffer.put(g)
                } else {
                    pixelBuffer.put(0f)
                }
            }
        }
        // Channel B
        for (y in 0 until 1024) {
            val hasY = y < targetH
            val yOffset = y * targetW
            for (x in 0 until 1024) {
                if (hasY && x < targetW) {
                    val c = pixels[yOffset + x]
                    val b = (Color.blue(c) / 255f - bMean) / bStd
                    pixelBuffer.put(b)
                } else {
                    pixelBuffer.put(0f)
                }
            }
        }
        pixelBuffer.rewind()

        val session = getVisionSession()
        val shape = longArrayOf(1, 3, 1024, 1024)

        val embeddingElements = 1 * 256 * 64 * 64
        val embBuffer = ByteBuffer.allocateDirect(embeddingElements * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val posEmbBuffer = ByteBuffer.allocateDirect(embeddingElements * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        OnnxTensor.createTensor(environment, pixelBuffer, shape).use { inputTensor ->
            session.run(mapOf("pixel_values" to inputTensor)).use { result ->
                val embTensor = result["image_embeddings"].get() as OnnxTensor
                val posTensor = result["image_positional_embeddings"].get() as OnnxTensor

                embTensor.floatBuffer.rewind()
                embBuffer.put(embTensor.floatBuffer)
                embBuffer.rewind()

                posTensor.floatBuffer.rewind()
                posEmbBuffer.put(posTensor.floatBuffer)
                posEmbBuffer.rewind()
            }
        }

        val cached = CachedVisionEmbedding(
            bitmapId = bitmapId,
            width = origW,
            height = origH,
            scale = scale,
            targetW = targetW,
            targetH = targetH,
            imageEmbeddings = embBuffer,
            imagePositionalEmbeddings = posEmbBuffer,
        )
        cachedEmbedding = cached
        return cached
    }

    private data class SamPromptPoint(
        val x: Float,
        val y: Float,
        val label: Long, // 1 = foreground, 0 = background
    )

    private fun generateBoxPoints(
        bx1: Float, by1: Float, bx2: Float, by2: Float,
        scale: Float, imgW: Int, imgH: Int,
    ): List<SamPromptPoint> {
        val bw = max(20f, bx2 - bx1)
        val bh = max(20f, by2 - by1)
        val bcx = (bx1 + bx2) * 0.5f
        val bcy = (by1 + by2) * 0.5f
        val isVertical = bh > bw * 1.15f

        val pts = mutableListOf<SamPromptPoint>()
        if (isVertical) {
            // Fish held vertically: torso and flank points (label 1), top gripper point (label 0)
            pts.add(SamPromptPoint(bcx * scale, (by1 + bh * 0.56f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, (by1 + bh * 0.28f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, (by1 + bh * 0.44f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, (by1 + bh * 0.72f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, (by1 + bh * 0.88f) * scale, 1L))
            pts.add(SamPromptPoint(max(bx1 + 8f, bcx - bw * 0.22f) * scale, (by1 + bh * 0.55f) * scale, 1L))
            pts.add(SamPromptPoint(min(bx2 - 8f, bcx + bw * 0.22f) * scale, (by1 + bh * 0.55f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, max(0f, by1 - 10f) * scale, 0L))
        } else {
            // Fish horizontal: spine and body points
            pts.add(SamPromptPoint(bcx * scale, bcy * scale, 1L))
            pts.add(SamPromptPoint((bx1 + bw * 0.20f) * scale, bcy * scale, 1L))
            pts.add(SamPromptPoint((bx1 + bw * 0.38f) * scale, bcy * scale, 1L))
            pts.add(SamPromptPoint((bx1 + bw * 0.62f) * scale, bcy * scale, 1L))
            pts.add(SamPromptPoint((bx1 + bw * 0.80f) * scale, bcy * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, max(by1 + 5f, bcy - bh * 0.2f) * scale, 1L))
            pts.add(SamPromptPoint(bcx * scale, min(by2 - 5f, bcy + bh * 0.2f) * scale, 1L))
        }
        return pts
    }

    private fun runMaskDecoder(
        embedding: CachedVisionEmbedding,
        points: List<SamPromptPoint>,
    ): Pair<FloatArray, Float> {
        val decoder = getDecoderSession()
        val numPoints = points.size

        val pointsBuffer = ByteBuffer.allocateDirect(1 * 1 * numPoints * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val labelsBuffer = ByteBuffer.allocateDirect(1 * 1 * numPoints * 8)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()

        for (p in points) {
            pointsBuffer.put(p.x)
            pointsBuffer.put(p.y)
            labelsBuffer.put(p.label)
        }
        pointsBuffer.rewind()
        labelsBuffer.rewind()

        embedding.imageEmbeddings.rewind()
        embedding.imagePositionalEmbeddings.rewind()

        val embShape = longArrayOf(1, 256, 64, 64)
        val pointsShape = longArrayOf(1, 1, numPoints.toLong(), 2)
        val labelsShape = longArrayOf(1, 1, numPoints.toLong())

        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs["input_points"] = OnnxTensor.createTensor(environment, pointsBuffer, pointsShape)
            inputs["input_labels"] = OnnxTensor.createTensor(environment, labelsBuffer, labelsShape)
            inputs["image_embeddings"] = OnnxTensor.createTensor(environment, embedding.imageEmbeddings, embShape)
            inputs["image_positional_embeddings"] = OnnxTensor.createTensor(environment, embedding.imagePositionalEmbeddings, embShape)

            decoder.run(inputs).use { result ->
                val iouScoresTensor = result["iou_scores"].get() as OnnxTensor
                val predMasksTensor = result["pred_masks"].get() as OnnxTensor

                val iouScoresBuf = iouScoresTensor.floatBuffer
                val iouScores = FloatArray(3)
                iouScoresBuf.get(iouScores)

                val masksBuf = predMasksTensor.floatBuffer
                val singleMaskSize = 256 * 256
                val allMasks = FloatArray(3 * singleMaskSize)
                masksBuf.get(allMasks)

                // Select the best mask candidate:
                // Candidate 0: whole mask, Candidate 1: part, Candidate 2: subpart
                // Typically Candidate 0 or highest IoU gives whole fish
                var bestCandidate = 0
                var bestScore = -1000f

                for (c in 0 until 3) {
                    val offset = c * singleMaskSize
                    var posCount = 0
                    for (i in 0 until singleMaskSize) {
                        if (allMasks[offset + i] > 0f) posCount++
                    }
                    val iou = iouScores[c]
                    val score = iou * 0.7f + (posCount.toFloat() / singleMaskSize) * 0.3f
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = c
                    }
                }

                val bestMask = FloatArray(singleMaskSize)
                System.arraycopy(allMasks, bestCandidate * singleMaskSize, bestMask, 0, singleMaskSize)
                return bestMask to iouScores[bestCandidate]
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun sampleBilinear(grid: FloatArray, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, 255)
        val y0 = y.toInt().coerceIn(0, 255)
        val x1 = min(255, x0 + 1)
        val y1 = min(255, y0 + 1)

        val fx = x - x0
        val fy = y - y0

        val v00 = grid[y0 * 256 + x0]
        val v10 = grid[y0 * 256 + x1]
        val v01 = grid[y1 * 256 + x0]
        val v11 = grid[y1 * 256 + x1]

        val top = v00 * (1f - fx) + v10 * fx
        val bot = v01 * (1f - fx) + v11 * fx
        return top * (1f - fy) + bot * fy
    }

    private fun sigmoid(x: Float): Float =
        1f / (1f + exp(-x))

    private fun filterLargestConnectedComponent(mask: FloatArray, width: Int, height: Int): FloatArray {
        val size = width * height
        val visited = BooleanArray(size)
        val queue = IntArray(size)

        var largestSize = 0
        var largestIndices: IntArray? = null

        for (start in 0 until size) {
            if (visited[start] || mask[start] < FishSubjectQualityGate.MASK_THRESHOLD) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % width
                val y = idx / width
                if (x > 0 && !visited[idx - 1] && mask[idx - 1] >= FishSubjectQualityGate.MASK_THRESHOLD) {
                    visited[idx - 1] = true; queue[tail++] = idx - 1
                }
                if (x + 1 < width && !visited[idx + 1] && mask[idx + 1] >= FishSubjectQualityGate.MASK_THRESHOLD) {
                    visited[idx + 1] = true; queue[tail++] = idx + 1
                }
                if (y > 0 && !visited[idx - width] && mask[idx - width] >= FishSubjectQualityGate.MASK_THRESHOLD) {
                    visited[idx - width] = true; queue[tail++] = idx - width
                }
                if (y + 1 < height && !visited[idx + width] && mask[idx + width] >= FishSubjectQualityGate.MASK_THRESHOLD) {
                    visited[idx + width] = true; queue[tail++] = idx + width
                }
            }

            if (tail > largestSize) {
                largestSize = tail
                largestIndices = queue.copyOfRange(0, tail)
            }
        }

        val result = FloatArray(size)
        largestIndices?.forEach { idx ->
            result[idx] = mask[idx]
        }
        return result
    }

    private fun readAssetBytes(name: String): ByteArray =
        context.assets.open(name).use { input ->
            ByteArrayOutputStream().use { out -> input.copyTo(out); out.toByteArray() }
        }

    private fun failed(
        code: String,
        started: Long,
        roiWidth: Int = 0,
        roiHeight: Int = 0,
        maskSize: Int = 0,
        expectedMaskSize: Int = 0,
        area: Float = 0f,
        quality: FishSubjectQuality = FishSubjectQuality.INVALID,
        maskIouScore: Float? = null,
        errorMessage: String? = null,
    ) = FishSubjectResult(
        status = SubjectStatus.FAILED,
        processingMs = (System.nanoTime() - started) / 1_000_000,
        roiWidth = roiWidth,
        roiHeight = roiHeight,
        maskSize = maskSize,
        expectedMaskSize = expectedMaskSize,
        maskAreaRatio = area,
        quality = quality,
        maskIouScore = maskIouScore?.takeIf { it.isFinite() },
        errorCode = code,
        errorMessage = errorMessage,
    )

    override fun close() {
        visionSession?.close()
        visionSession = null
        decoderSession?.close()
        decoderSession = null
        cachedEmbedding = null
    }

    companion object {
        private const val TAG = "SlimSamSegmenter"
        const val VISION_ENCODER_MODEL = "slimsam/vision_encoder_quantized.onnx"
        const val MASK_DECODER_MODEL = "slimsam/prompt_encoder_mask_decoder_quantized.onnx"
    }
}
