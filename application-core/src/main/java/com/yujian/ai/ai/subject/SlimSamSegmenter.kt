package com.yujian.ai.ai.subject

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.NodeInfo
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.RecognitionModelIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
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
    private val eventSink: (SlimSamRuntimeEvent) -> Unit = {},
) : AutoCloseable {

    private var environment: OrtEnvironment? = null

    private val sessionOptions: OrtSession.SessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            setIntraOpNumThreads(threadCount)
            setInterOpNumThreads(1)
            Log.i(TAG, "SLIMSAM_THREAD_POLICY intra_op=$threadCount inter_op=1")
        }
    }

    private var visionSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var visionModelSha256: String? = null
    private var decoderModelSha256: String? = null
    private var visionTensorMetadata: String? = null
    private var decoderTensorMetadata: String? = null
    private var verifiedVisionModelBytes: ByteArray? = null
    private var verifiedDecoderModelBytes: ByteArray? = null
    private var visionAssetVerified = false
    private var decoderAssetVerified = false

    private data class CachedVisionEmbedding(
        val cacheKey: SlimSamEmbeddingCacheKey,
        val photoSha256: String,
        val width: Int,
        val height: Int,
        val scale: Float,
        val targetW: Int,
        val targetH: Int,
        val imageEmbeddings: FloatBuffer, // 1 * 256 * 64 * 64
        val imagePositionalEmbeddings: FloatBuffer, // 1 * 256 * 64 * 64
    )

    private val embeddingCache = SlimSamEmbeddingCache<CachedVisionEmbedding>()

    private val outputDir = File(context.cacheDir, "fish_subject_previews").apply { mkdirs() }

    @Synchronized
    fun verifyModelAssets() {
        if (!visionAssetVerified) {
            val bytes = try {
                readAssetBytes(VISION_ENCODER_MODEL)
            } catch (error: Throwable) {
                throw SlimSamModelException("MODEL_ASSET_MISSING", "$VISION_ENCODER_MODEL is unavailable", "MODEL_VERIFY", error)
            }
            val actual = sha256(bytes)
            if (actual != VISION_ENCODER_SHA256) {
                throw SlimSamModelException("MODEL_SHA256_MISMATCH", "$VISION_ENCODER_MODEL SHA-256 mismatch: $actual", "MODEL_VERIFY")
            }
            visionModelSha256 = actual
            verifiedVisionModelBytes = bytes
            visionAssetVerified = true
        }
        if (!decoderAssetVerified) {
            val bytes = try {
                readAssetBytes(MASK_DECODER_MODEL)
            } catch (error: Throwable) {
                throw SlimSamModelException("MODEL_ASSET_MISSING", "$MASK_DECODER_MODEL is unavailable", "MODEL_VERIFY", error)
            }
            val actual = sha256(bytes)
            if (actual != DECODER_MODEL_SHA256) {
                throw SlimSamModelException("MODEL_SHA256_MISMATCH", "$MASK_DECODER_MODEL SHA-256 mismatch: $actual", "MODEL_VERIFY")
            }
            decoderModelSha256 = actual
            verifiedDecoderModelBytes = bytes
            decoderAssetVerified = true
        }
    }

    @Synchronized
    fun initializeOrtEnvironment() {
        if (environment != null) return
        try {
            environment = OrtEnvironment.getEnvironment()
        } catch (error: Throwable) {
            throw SlimSamModelException("ORT_INITIALIZATION_FAILED", "ONNX Runtime environment initialization failed", "ORT_INITIALIZATION", error)
        }
    }

    fun preloadVisionSession() {
        getVisionSession()
    }

    fun preloadDecoderSession() {
        getDecoderSession()
    }

    @Synchronized
    internal fun runtimeStats() = SlimSamRuntimeStats(
        visionSessionReady = visionSession != null,
        decoderSessionReady = decoderSession != null,
        visionModelSha256 = visionModelSha256,
        decoderModelSha256 = decoderModelSha256,
        visionTensorMetadata = visionTensorMetadata,
        decoderTensorMetadata = decoderTensorMetadata,
        intraOpThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        embeddingBufferBytes = if (embeddingCache.hasValue()) EMBEDDING_CACHE_BYTES else 0L,
        cachedPhotoCount = if (embeddingCache.hasValue()) 1 else 0,
    )

    fun clearEmbedding(reason: String) {
        val started = SystemClock.elapsedRealtimeNanos()
        embeddingCache.clear()
        recordTiming(
            identity = null,
            stage = "EMBEDDING_CACHE",
            event = "EMBEDDING_CACHE_CLEAR",
            startedAtNs = started,
            endedAtNs = SystemClock.elapsedRealtimeNanos(),
            result = reason,
        )
    }

    /** Precomputes the photo-only vision embedding while detector work is in flight. */
    fun prepareImageEmbedding(
        source: Bitmap,
        photoSha256: String,
        identity: RecognitionModelIdentity? = null,
    ) {
        require(source.width > 0 && source.height > 0) { "Bitmap dimensions must be greater than zero" }
        val started = SystemClock.elapsedRealtimeNanos()
        getOrComputeImageEmbedding(source, photoSha256, identity)
        val ended = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "PHOTO_EMBEDDING", "VISION_EMBEDDING_READY", started, ended, "READY")
        Log.i(TAG, "SLIMSAM_PREWARM_END duration_ms=${(ended - started) / 1_000_000} " +
            "photoSha256=$photoSha256 source=${source.width}x${source.height}")
    }

    @Synchronized
    private fun getVisionSession(): OrtSession {
        visionSession?.let { Log.i(TAG, "SLIMSAM_MODEL_REUSE model=vision"); return it }
        val started = SystemClock.elapsedRealtimeNanos()
        Log.i(TAG, "SLIMSAM_MODEL_LOAD_START model=vision")
        initializeOrtEnvironment()
        verifyModelAssets()
        val bytes = requireNotNull(verifiedVisionModelBytes)
        return try {
            val created = requireNotNull(environment).createSession(bytes, sessionOptions)
            try {
                validateVisionSession(created)
            } catch (error: Throwable) {
                created.close()
                throw error
            }
            visionSession = created
            verifiedVisionModelBytes = null
            val ended = SystemClock.elapsedRealtimeNanos()
            recordTiming(null, "VISION_SESSION_CREATE", "VISION_SESSION_CREATE_END", started, ended, "READY")
            Log.i(TAG, "SLIMSAM_MODEL_LOAD_END model=vision bytes=${bytes.size} duration_ms=${(ended - started) / 1_000_000}")
            created
        } catch (error: Throwable) {
            if (error is SlimSamModelException) throw error
            throw SlimSamModelException("VISION_SESSION_CREATE_FAILED", "Failed to create SlimSAM Vision Session", "VISION_SESSION_CREATE", error)
        }
    }

    @Synchronized
    private fun getDecoderSession(): OrtSession {
        decoderSession?.let { Log.i(TAG, "SLIMSAM_MODEL_REUSE model=decoder"); return it }
        val started = SystemClock.elapsedRealtimeNanos()
        Log.i(TAG, "SLIMSAM_MODEL_LOAD_START model=decoder")
        initializeOrtEnvironment()
        verifyModelAssets()
        val bytes = requireNotNull(verifiedDecoderModelBytes)
        return try {
            val created = requireNotNull(environment).createSession(bytes, sessionOptions)
            try {
                validateDecoderSession(created)
            } catch (error: Throwable) {
                created.close()
                throw error
            }
            decoderSession = created
            verifiedDecoderModelBytes = null
            val ended = SystemClock.elapsedRealtimeNanos()
            recordTiming(null, "DECODER_SESSION_CREATE", "DECODER_SESSION_CREATE_END", started, ended, "READY")
            Log.i(TAG, "SLIMSAM_MODEL_LOAD_END model=decoder bytes=${bytes.size} duration_ms=${(ended - started) / 1_000_000}")
            created
        } catch (error: Throwable) {
            if (error is SlimSamModelException) throw error
            throw SlimSamModelException("DECODER_SESSION_CREATE_FAILED", "Failed to create SlimSAM Decoder Session", "DECODER_SESSION_CREATE", error)
        }
    }

    private fun validateVisionSession(session: OrtSession) {
        val inputs = tensorSignatures(session.inputInfo)
        val outputs = tensorSignatures(session.outputInfo)
        val metadata = formatMetadata(session, inputs, outputs)
        val errorCode = SlimSamTensorContract.validateVision(session.inputNames, session.outputNames, inputs, outputs)
        if (errorCode == null) {
            visionTensorMetadata = metadata
            return
        }
        throw SlimSamModelException(
            errorCode,
            "SlimSAM Vision Session metadata rejected: $metadata",
            "VISION_SESSION_VALIDATE",
            tensorMetadata = metadata,
        )
    }

    private fun validateDecoderSession(session: OrtSession) {
        val inputs = tensorSignatures(session.inputInfo)
        val outputs = tensorSignatures(session.outputInfo)
        val metadata = formatMetadata(session, inputs, outputs)
        val errorCode = SlimSamTensorContract.validateDecoder(session.inputNames, session.outputNames, inputs, outputs)
        if (errorCode == null) {
            decoderTensorMetadata = metadata
            return
        }
        throw SlimSamModelException(
            errorCode,
            "SlimSAM Decoder Session metadata rejected: $metadata",
            "DECODER_SESSION_VALIDATE",
            tensorMetadata = metadata,
        )
    }

    private fun tensorSignatures(nodes: Map<String, NodeInfo>): Map<String, SlimSamTensorSignature> =
        nodes.mapValues { (_, node) ->
            val tensor = node.info as? TensorInfo
            if (tensor == null) SlimSamTensorSignature("NON_TENSOR", emptyList())
            else SlimSamTensorSignature(tensor.type.name, tensor.shape.toList())
        }

    private fun formatMetadata(
        session: OrtSession,
        inputs: Map<String, SlimSamTensorSignature>,
        outputs: Map<String, SlimSamTensorSignature>,
    ): String = "inputs=${session.inputNames.sorted()} $inputs outputs=${session.outputNames.sorted()} $outputs"

    suspend fun segment(
        source: Bitmap,
        detectorBbox: NormalizedFishBox,
        sessionId: String = "",
        attemptId: String = "",
        imageId: String = "",
        photoSha256: String = "",
        identity: RecognitionModelIdentity? = null,
    ): FishSubjectResult =
        withContext(Dispatchers.Default) {
            val started = SystemClock.elapsedRealtimeNanos()
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
                val bboxEmbeddingWaitStartNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "BBOX_WAIT", "BBOX_EMBEDDING_WAIT_START", bboxEmbeddingWaitStartNs, bboxEmbeddingWaitStartNs, "WAITING")
                val embedding = getOrComputeImageEmbedding(source, photoSha256, identity)
                val bboxEmbeddingReadyNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "BBOX_WAIT", "BBOX_EMBEDDING_WAIT_END", bboxEmbeddingWaitStartNs, bboxEmbeddingReadyNs, "EMBEDDING_READY")
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
                val decoderStartedNs = SystemClock.elapsedRealtimeNanos()
                val decoderStarted = SystemClock.uptimeMillis()
                Log.i(TAG, "SLIMSAM_DECODER_START uptime_ms=$decoderStarted")
                recordTiming(identity, "MASK_DECODER", "MASK_DECODER_START", decoderStartedNs, decoderStartedNs, "STARTED")
                val (bestMask256, iouScore) = runMaskDecoder(embedding, points)
                val decoderEndedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_DECODER", "MASK_DECODER_END", decoderStartedNs, decoderEndedNs, "PASS")
                Log.i(TAG, "SLIMSAM_DECODER_END duration_ms=${(decoderEndedNs - decoderStartedNs) / 1_000_000} iou=$iouScore")

                val gridToPhoto = Matrix().apply { setScale(4f / embedding.scale, 4f / embedding.scale) }
                var geometry = MaskGeometryMetadata(
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
                    modelSha256 = "vision:${requireNotNull(visionModelSha256)};decoder:${requireNotNull(decoderModelSha256)}",
                )

                // 5. Bilinearly sample 256x256 logits into the ROI mask
                val postprocessStartedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_POSTPROCESS", "MASK_POSTPROCESS_START", postprocessStartedNs, postprocessStartedNs, "STARTED")
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
                val postprocessEndedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_POSTPROCESS", "MASK_POSTPROCESS_END", postprocessStartedNs, postprocessEndedNs, quality.name)
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
                val pngStartedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_PNG_WRITE", "MASK_PNG_WRITE_START", pngStartedNs, pngStartedNs, "STARTED")
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
                val pngEndedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_PNG_WRITE", "MASK_PNG_WRITE_END", pngStartedNs, pngEndedNs, "PASS")
                // Bind this attempt to the bytes actually handed to the UI. The hash is
                // deliberately computed after PNG encoding, rather than from logits or a
                // Bitmap generation ID, so every downstream consumer can reject a stale mask.
                val shaStartedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_SHA_VERIFY", "MASK_SHA_VERIFY_START", shaStartedNs, shaStartedNs, "STARTED")
                val maskSha256 = withContext(Dispatchers.IO) { sha256(file) }
                val shaEndedNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "MASK_SHA_VERIFY", "MASK_SHA_VERIFY_END", shaStartedNs, shaEndedNs, "PASS")
                geometry = geometry.copy(maskSha256 = maskSha256)

                val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
                Log.d(TAG, "SlimSAM segment success in ${elapsed}ms, iou=$iouScore, area=$areaRatio")
                val completedAtNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "SLIMSAM_RESULT", "SLIMSAM_RESULT_READY", started, completedAtNs, "READY")

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
                    maskSha256 = maskSha256,
                    geometry = geometry,
                )
            } catch (cancelled: CancellationException) {
                // Preserve structured cancellation when a screen leaves or a newer Attempt replaces this one.
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "SlimSAM segmentation failure", error)
                val failedAtNs = SystemClock.elapsedRealtimeNanos()
                val failure = error.slimSamFailureDetails()
                recordTiming(
                    identity,
                    "SLIMSAM_RESULT",
                    "SLIMSAM_RESULT_FAILED",
                    started,
                    failedAtNs,
                    "FAILED",
                    failure.errorCode,
                    errorMessage = error.message ?: error::class.java.simpleName,
                    exceptionClass = failure.exceptionClass,
                    rootCause = failure.rootCause,
                    tensorMetadata = failure.tensorMetadata,
                )
                failed(
                    code = failure.errorCode,
                    started = started,
                    roiWidth = roiW,
                    roiHeight = roiH,
                    expectedMaskSize = expectedMaskSize,
                    errorMessage = error.message ?: "SlimSAM execution failed",
                    exceptionClass = failure.exceptionClass,
                    rootCause = failure.rootCause,
                    tensorMetadata = failure.tensorMetadata,
                )
            }
        }

    private fun getOrComputeImageEmbedding(
        source: Bitmap,
        photoSha256: String,
        identity: RecognitionModelIdentity?,
    ): CachedVisionEmbedding {
        val cacheKey = SlimSamEmbeddingCacheKey.create(
            photoSha256 = photoSha256.ifBlank { "bitmap-generation-${source.generationId}" },
            width = source.width,
            height = source.height,
            modelVersion = MODEL_VERSION,
            preprocessingVersion = PREPROCESSING_VERSION,
        )
        embeddingCache.activate(cacheKey)
        val existing = embeddingCache.get(cacheKey)
        if (existing != null) {
            val now = SystemClock.elapsedRealtimeNanos()
            recordTiming(identity, "EMBEDDING_CACHE", "EMBEDDING_CACHE_HIT", now, now, "HIT")
            existing.imageEmbeddings.rewind()
            existing.imagePositionalEmbeddings.rewind()
            return existing
        }
        val cacheMissAt = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "EMBEDDING_CACHE", "EMBEDDING_CACHE_MISS", cacheMissAt, cacheMissAt, "MISS")

        val preprocessingStartedNs = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "PHOTO_PREPROCESS", "PHOTO_PREPROCESS_START", preprocessingStartedNs, preprocessingStartedNs, "STARTED")
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
        val preprocessingEndedNs = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "PHOTO_PREPROCESS", "PHOTO_PREPROCESS_END", preprocessingStartedNs, preprocessingEndedNs, "PASS")

        val session = getVisionSession()
        val shape = longArrayOf(1, 3, 1024, 1024)

        val embeddingElements = 1 * 256 * 64 * 64
        val embBuffer = ByteBuffer.allocateDirect(embeddingElements * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val posEmbBuffer = ByteBuffer.allocateDirect(embeddingElements * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val encoderStartedNs = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "VISION_ENCODER", "VISION_ENCODER_START", encoderStartedNs, encoderStartedNs, "STARTED")
        OnnxTensor.createTensor(requireNotNull(environment), pixelBuffer, shape).use { inputTensor ->
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
        val encoderEndedNs = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "VISION_ENCODER", "VISION_ENCODER_END", encoderStartedNs, encoderEndedNs, "PASS")

        val cached = CachedVisionEmbedding(
            cacheKey = cacheKey,
            photoSha256 = photoSha256,
            width = origW,
            height = origH,
            scale = scale,
            targetW = targetW,
            targetH = targetH,
            imageEmbeddings = embBuffer,
            imagePositionalEmbeddings = posEmbBuffer,
        )
        check(embeddingCache.putIfActive(cacheKey, cached)) { "Stale SlimSAM embedding write rejected" }
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
            val ortEnvironment = requireNotNull(environment)
            inputs["input_points"] = OnnxTensor.createTensor(ortEnvironment, pointsBuffer, pointsShape)
            inputs["input_labels"] = OnnxTensor.createTensor(ortEnvironment, labelsBuffer, labelsShape)
            inputs["image_embeddings"] = OnnxTensor.createTensor(ortEnvironment, embedding.imageEmbeddings, embShape)
            inputs["image_positional_embeddings"] = OnnxTensor.createTensor(ortEnvironment, embedding.imagePositionalEmbeddings, embShape)

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

    private fun recordTiming(
        identity: RecognitionModelIdentity?,
        stage: String,
        event: String,
        startedAtNs: Long,
        endedAtNs: Long,
        result: String,
        errorCode: String = "",
        errorMessage: String? = null,
        exceptionClass: String? = null,
        rootCause: String? = null,
        tensorMetadata: String? = null,
    ) {
        eventSink(
            SlimSamRuntimeEvent(
                recognitionSessionId = identity?.sessionId.orEmpty(),
                recognitionAttemptId = identity?.attemptId.orEmpty(),
                imageId = identity?.imageId.orEmpty(),
                photoSha256 = identity?.photoSha256.orEmpty(),
                stageName = stage,
                eventName = event,
                startUptimeNs = startedAtNs,
                endUptimeNs = endedAtNs,
                durationMs = (endedAtNs - startedAtNs).coerceAtLeast(0L) / 1_000_000.0,
                threadName = Thread.currentThread().name,
                result = result,
                errorCode = errorCode,
                errorMessage = errorMessage,
                exceptionClass = exceptionClass,
                rootCause = rootCause,
                tensorMetadata = tensorMetadata,
            ),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
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
        exceptionClass: String? = null,
        rootCause: String? = null,
        tensorMetadata: String? = null,
    ) = FishSubjectResult(
        status = SubjectStatus.FAILED,
        processingMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000,
        roiWidth = roiWidth,
        roiHeight = roiHeight,
        maskSize = maskSize,
        expectedMaskSize = expectedMaskSize,
        maskAreaRatio = area,
        quality = quality,
        maskIouScore = maskIouScore?.takeIf { it.isFinite() },
        errorCode = code,
        errorMessage = errorMessage,
        exceptionClass = exceptionClass,
        rootCause = rootCause,
        tensorMetadata = tensorMetadata,
    )

    override fun close() {
        visionSession?.close()
        visionSession = null
        decoderSession?.close()
        decoderSession = null
        visionModelSha256 = null
        decoderModelSha256 = null
        visionTensorMetadata = null
        decoderTensorMetadata = null
        verifiedVisionModelBytes = null
        verifiedDecoderModelBytes = null
        embeddingCache.clear()
        runCatching { sessionOptions.close() }
    }

    companion object {
        private const val TAG = "SlimSamSegmenter"
        const val VISION_ENCODER_MODEL = "slimsam/vision_encoder_quantized.onnx"
        const val MASK_DECODER_MODEL = "slimsam/prompt_encoder_mask_decoder_quantized.onnx"
        const val VISION_ENCODER_SHA256 = "cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971"
        const val DECODER_MODEL_SHA256 = "cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2"
        const val MODEL_VERSION = "cce23c7b+cb90b279-v1"
        const val PREPROCESSING_VERSION = "rgb1024-pad-mean-std-v1"
        const val EMBEDDING_CACHE_BYTES = 8L * 1024L * 1024L
    }
}
