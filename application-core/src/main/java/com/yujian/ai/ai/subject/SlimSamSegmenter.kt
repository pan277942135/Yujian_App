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
import com.yujian.ai.ai.RecognitionModelTensorReceipt
import com.yujian.ai.ai.RecognitionRuntimeModelIdentity
import com.yujian.ai.ai.RecognitionTensorContentHash
import com.yujian.ai.ai.RecognitionModelThreadPolicy
import com.yujian.ai.ai.RecognitionAttemptFailureCode
import com.yujian.ai.ai.RecognitionAttemptNativeTimeoutException
import com.yujian.ai.ai.RecognitionNativeRunDeadlineGuard
import com.yujian.ai.recognition.domain.isSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import java.util.concurrent.atomic.AtomicLong
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
    private val threadAllocation = RecognitionModelThreadPolicy.allocate()

    private val sessionOptionsDelegate = lazy {
        OrtSession.SessionOptions().apply {
            val threadCount = threadAllocation.visionEncoderThreads
            setIntraOpNumThreads(threadCount)
            setInterOpNumThreads(1)
            Log.i(
                TAG,
                "SLIMSAM_THREAD_POLICY status=${RecognitionModelThreadPolicy.VALIDATION_STATUS} " +
                    "processors=${threadAllocation.availableProcessors} " +
                    "intra_op=$threadCount inter_op=1 detector_intra_op=${threadAllocation.detectorThreads} " +
                    "classifier_intra_op=${threadAllocation.classifierThreads}",
            )
        }
    }
    private val sessionOptions: OrtSession.SessionOptions get() = sessionOptionsDelegate.value

    private var visionSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private val visionModelSessionId = UUID.randomUUID().toString()
    private val decoderModelSessionId = UUID.randomUUID().toString()
    private val inferenceSequence = AtomicLong(0L)
    private var visionModelSha256: String? = null
    private var decoderModelSha256: String? = null
    private var visionTensorMetadata: String? = null
    private var decoderTensorMetadata: String? = null
    private var verifiedVisionModelBytes: ByteArray? = null
    private var verifiedDecoderModelBytes: ByteArray? = null
    private var visionAssetVerified = false
    private var decoderAssetVerified = false

    private val nativeSessionGate = SlimSamNativeSessionGate {
        synchronized(this) {
            var closeFailure: Throwable? = null
            closeFailure = closeNativeResource(visionSession, "Vision Session", closeFailure)
            visionSession = null
            closeFailure = closeNativeResource(decoderSession, "Decoder Session", closeFailure)
            decoderSession = null
            if (sessionOptionsDelegate.isInitialized()) {
                closeFailure = closeNativeResource(sessionOptionsDelegate.value, "Shared SessionOptions", closeFailure)
            }
            recordTiming(
                identity = null,
                stage = "NATIVE_SESSION_RESOURCES",
                event = if (closeFailure == null) "NATIVE_SESSION_RESOURCES_RELEASED" else "NATIVE_SESSION_RESOURCES_RELEASE_FAILED",
                startedAtNs = SystemClock.elapsedRealtimeNanos(),
                endedAtNs = SystemClock.elapsedRealtimeNanos(),
                result = if (closeFailure == null) "RELEASED" else "FAILED",
                errorCode = closeFailure?.javaClass?.simpleName.orEmpty(),
                errorMessage = closeFailure?.message,
            )
            visionModelSha256 = null
            decoderModelSha256 = null
            visionTensorMetadata = null
            decoderTensorMetadata = null
            verifiedVisionModelBytes = null
            verifiedDecoderModelBytes = null
        }
    }

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
    private val embeddingSingleFlight =
        SlimSamEmbeddingSingleFlight<SlimSamEmbeddingCacheKey, CachedVisionEmbedding>()

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
        intraOpThreads = threadAllocation.visionEncoderThreads,
        embeddingBufferBytes = if (embeddingCache.hasValue()) EMBEDDING_CACHE_BYTES else 0L,
        cachedPhotoCount = if (embeddingCache.hasValue()) 1 else 0,
        activeNativeRunCount = nativeSessionGate.snapshot().activeRunCount,
        nativeSessionPoisoned = nativeSessionGate.snapshot().poisoned,
    )

    fun clearEmbedding(reason: String) {
        val started = SystemClock.elapsedRealtimeNanos()
        val hadEmbedding = embeddingCache.hasValue()
        embeddingCache.clear()
        if (hadEmbedding) {
            val evictedAt = SystemClock.elapsedRealtimeNanos()
            recordTiming(null, "EMBEDDING_CACHE", "EMBEDDING_CACHE_EVICT", started, evictedAt, reason)
        }
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
    suspend fun prepareImageEmbedding(
        source: Bitmap,
        photoSha256: String,
        identity: RecognitionModelIdentity? = null,
    ) {
        val operationJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("SlimSAM embedding run requires a coroutine Job")
        require(source.width > 0 && source.height > 0) { "Bitmap dimensions must be greater than zero" }
        val started = SystemClock.elapsedRealtimeNanos()
        getOrComputeImageEmbedding(source, photoSha256, identity, operationJob)
        val ended = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "PHOTO_EMBEDDING", "VISION_EMBEDDING_READY", started, ended, "READY")
        Log.i(TAG, "SLIMSAM_PREWARM_END duration_ms=${(ended - started) / 1_000_000} " +
            "photoSha256=$photoSha256 source=${source.width}x${source.height}")
    }

    @Synchronized
    private fun getVisionSession(): OrtSession {
        check(nativeSessionGate.snapshot().acceptingRuns) { "SlimSAM Native Sessions are not accepting work" }
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
        check(nativeSessionGate.snapshot().acceptingRuns) { "SlimSAM Native Sessions are not accepting work" }
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
            val operationJob = currentCoroutineContext()[Job]
                ?: throw IllegalStateException("SlimSAM segmentation requires a coroutine Job")
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

            var embeddingLease: SlimSamEmbeddingCache.Lease<CachedVisionEmbedding>? = null
            try {
                // 2. Ensure Vision Encoder embeddings for this photo are computed
                val encoderStarted = SystemClock.uptimeMillis()
                Log.i(TAG, "SLIMSAM_ENCODER_START uptime_ms=$encoderStarted")
                val bboxEmbeddingWaitStartNs = SystemClock.elapsedRealtimeNanos()
                recordTiming(identity, "BBOX_WAIT", "BBOX_EMBEDDING_WAIT_START", bboxEmbeddingWaitStartNs, bboxEmbeddingWaitStartNs, "WAITING")
                val embedding = getOrComputeImageEmbedding(source, photoSha256, identity, operationJob)
                embeddingLease = embeddingCache.lease(embedding.cacheKey, embedding)
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
                val (bestMask256, iouScore) = runMaskDecoder(embedding, points, identity, operationJob)
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
                    photoIdentity = identity?.photoIdentity,
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
                val failureCode = if (error is RecognitionAttemptNativeTimeoutException) {
                    RecognitionAttemptFailureCode.SLIMSAM_TIMEOUT
                } else failure.errorCode
                recordTiming(
                    identity,
                    "SLIMSAM_RESULT",
                    "SLIMSAM_RESULT_FAILED",
                    started,
                    failedAtNs,
                    "FAILED",
                    failureCode,
                    errorMessage = error.message ?: error::class.java.simpleName,
                    exceptionClass = failure.exceptionClass,
                    rootCause = failure.rootCause,
                    tensorMetadata = failure.tensorMetadata,
                )
                failed(
                    code = failureCode,
                    started = started,
                    roiWidth = roiW,
                    roiHeight = roiH,
                    expectedMaskSize = expectedMaskSize,
                    errorMessage = error.message ?: "SlimSAM execution failed",
                    exceptionClass = failure.exceptionClass,
                    rootCause = failure.rootCause,
                    tensorMetadata = failure.tensorMetadata,
                )
            } finally {
                embeddingLease?.close()
            }
        }

    private suspend fun getOrComputeImageEmbedding(
        source: Bitmap,
        photoSha256: String,
        identity: RecognitionModelIdentity?,
        operationJob: Job,
    ): CachedVisionEmbedding {
        operationJob.ensureActive()
        require(photoSha256.isSha256()) { "Canonical photo SHA-256 is required for embedding cache" }
        identity?.photoIdentity?.let { photo ->
            require(photo.isValid() && photo.canonicalPixelSha256 == photoSha256 &&
                photo.photoId == identity.imageId && photo.width == source.width && photo.height == source.height
            ) { "Embedding identity does not match canonical model-input pixels" }
        }
        val photoIdentity = identity?.photoIdentity
        val cacheKey = SlimSamEmbeddingCacheKey.create(
            recognitionSessionId = identity?.sessionId ?: "UNSCOPED_PREWARM",
            attemptId = identity?.attemptId ?: "UNSCOPED_PREWARM",
            photoId = photoIdentity?.photoId ?: identity?.imageId ?: "UNSCOPED_PREWARM",
            sourceBytesSha256 = photoIdentity?.sourceBytesSha256 ?: "UNSCOPED_PREWARM",
            canonicalPixelSha256 = photoSha256,
            width = source.width,
            height = source.height,
            sourceOrientation = photoIdentity?.sourceOrientation ?: 1,
            normalizationVersion = photoIdentity?.normalizationVersion ?: "UNSCOPED_PREWARM",
            photoContentVersion = photoIdentity?.photoContentVersion ?: 0L,
            encoderModelSha256 = requireNotNull(visionModelSha256),
            preprocessVersion = PREPROCESSING_VERSION,
            inputSchemaHash = VISION_INPUT_SCHEMA_HASH,
            inputShape = VISION_INPUT_SHAPE,
        )
        return embeddingSingleFlight.run(cacheKey) {
            computeImageEmbedding(source, photoSha256, identity, operationJob, cacheKey)
        }
    }

    private suspend fun computeImageEmbedding(
        source: Bitmap,
        photoSha256: String,
        identity: RecognitionModelIdentity?,
        operationJob: Job,
        cacheKey: SlimSamEmbeddingCacheKey,
    ): CachedVisionEmbedding {
        operationJob.ensureActive()
        val evicted = embeddingCache.activate(cacheKey)
        if (evicted) {
            val evictedAt = SystemClock.elapsedRealtimeNanos()
            recordTiming(identity, "EMBEDDING_CACHE", "EMBEDDING_CACHE_EVICT", evictedAt, evictedAt, "PHOTO_REPLACED")
        }
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

        val preprocessingMarkerNs = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "PHOTO_PREPROCESS", "PHOTO_PREPROCESS_START", preprocessingMarkerNs, preprocessingMarkerNs, "STARTED")
        val preprocessingStartedNs = SystemClock.elapsedRealtimeNanos()
        val origW = source.width
        val origH = source.height
        val scale = 1024f / max(origW, origH)
        val targetW = (origW * scale).roundToInt().coerceIn(1, 1024)
        val targetH = (origH * scale).roundToInt().coerceIn(1, 1024)

        // Keep the same 1024 FLOAT32 NCHW contract without retaining a 4 MiB full-frame IntArray.
        val pixelBytes = allocateSlimSamPixelBuffer()
        val pixelBuffer = pixelBytes.asFloatBuffer()
        val resized = if (origW == targetW && origH == targetH) source
        else Bitmap.createScaledBitmap(source, targetW, targetH, true)
        try {
            val pixelRow = IntArray(targetW)
            for (y in 0 until 1024) {
                if (y % 32 == 0) operationJob.ensureActive()
                if (y < targetH) {
                    resized.getPixels(pixelRow, 0, targetW, 0, y, targetW, 1)
                    writeNormalizedRgbRow(pixelBuffer, pixelRow, targetW, y, 1024)
                } else {
                    writeNormalizedRgbRow(pixelBuffer, pixelRow, 0, y, 1024)
                }
            }
        } finally {
            if (resized !== source && !resized.isRecycled) resized.recycle()
        }
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

        val nativeLease = nativeSessionGate.acquire()
            ?: throw SlimSamModelException(
                "SLIMSAM_NATIVE_SESSION_UNAVAILABLE",
                "SlimSAM Native Session is closing, poisoned, or still releasing a prior run",
                "VISION_ENCODER",
            )
        var inputTensor: OnnxTensor? = null
        var runOptions: OrtSession.RunOptions? = null
        var runController: SlimSamNativeRunController? = null
        var deadlineGuard: RecognitionNativeRunDeadlineGuard? = null
        var cancellationHandle: kotlinx.coroutines.DisposableHandle? = null
        var runResult: AutoCloseable? = null
        var nativeCallStarted = false
        var operationFailure: Throwable? = null
        val encoderStartedAtUptimeMs = SystemClock.uptimeMillis()
        val encoderInferenceId = "${identity?.attemptId ?: "unbound"}:SLIMSAM_ENCODER:${inferenceSequence.incrementAndGet()}"
        try {
            val tensor = timeEncoderStage(identity, "ONNX_INPUT_TENSOR_CREATE") {
                OnnxTensor.createTensor(requireNotNull(environment), pixelBuffer, shape)
                    .also { inputTensor = it }
            }
            require(tensor.info.type.name == "FLOAT" && tensor.info.shape.contentEquals(shape)) {
                "SLIMSAM_VISION_INPUT_SCHEMA_MISMATCH"
            }
            val encoderModelIdentity = identity?.let {
                // The preprocessor uses absolute writes, so the backing bytes cover the full tensor.
                val contentHash = timeEncoderStage(identity, "INPUT_TENSOR_SHA256") {
                    RecognitionTensorContentHash.sha256(pixelBytes)
                }
                timeEncoderStage(identity, "INPUT_RECEIPT_VALIDATE") {
                    val model = runtimeModelIdentity(
                        modelId = "SLIMSAM_VISION_ENCODER",
                        modelVersion = "VISION_ENCODER_V1",
                        expectedSha256 = VISION_ENCODER_SHA256,
                        actualSha256 = requireNotNull(visionModelSha256),
                        inputSchemaHash = schemaHash(session.inputInfo),
                        outputSchemaHash = schemaHash(session.outputInfo),
                        preprocessVersion = PREPROCESSING_VERSION,
                        sessionId = visionModelSessionId,
                    )
                    recordTensorReceipt(
                        direction = "INPUT",
                        identity = identity,
                        inferenceId = encoderInferenceId,
                        model = model,
                        tensorName = "pixel_values",
                        dataType = tensor.info.type.name,
                        shape = tensor.info.shape.toList(),
                        byteSize = tensor.info.shape.fold(1L) { size, axis -> size * axis } * Float.SIZE_BYTES,
                        contentHash = contentHash,
                        startUptimeMs = encoderStartedAtUptimeMs,
                        endUptimeMs = SystemClock.uptimeMillis(),
                    )
                    model
                }
            }
            operationJob.ensureActive()
            runOptions = OrtSession.RunOptions()
            val runOptionsId = UUID.randomUUID().toString()
            val encoderTensorMetadata = ENCODER_RUN_TENSOR_METADATA
            runController = createNativeRunController(
                identity = identity,
                stage = "VISION_ENCODER_NATIVE",
                runOptionsId = runOptionsId,
                modelSha256 = visionModelSha256,
                runOptions = runOptions,
                tensorMetadata = encoderTensorMetadata,
                knownTensorInputBytes = ENCODER_INPUT_TENSOR_BYTES,
            )
            cancellationHandle = installSlimSamCancellationHandler(operationJob, runController)
            deadlineGuard = identity?.attemptDeadline?.let { deadline ->
                val controller = requireNotNull(runController)
                RecognitionNativeRunDeadlineGuard.create(deadline) { controller.requestTermination(); Unit }
            }
            deadlineGuard?.throwIfExpired()
            operationJob.ensureActive()
            val encoderStart = SystemClock.elapsedRealtimeNanos()
            recordTiming(
                identity,
                "VISION_ENCODER",
                "VISION_ENCODER_START",
                encoderStart,
                encoderStart,
                "STARTED",
                runOptionsId = runOptionsId,
                modelSha256 = visionModelSha256,
                executionBoundary = "OrtSession.run",
                tensorMetadata = encoderTensorMetadata,
                knownTensorInputBytes = ENCODER_INPUT_TENSOR_BYTES,
            )
            runController.nativeStarted()
            nativeCallStarted = true
            val encoderInputs = mapOf("pixel_values" to tensor)
            val nativeStartedNs = SystemClock.elapsedRealtimeNanos()
            var nativeReturnedSuccessfully = false
            val result = try {
                session.run(encoderInputs, runOptions).also {
                    runResult = it
                    nativeReturnedSuccessfully = true
                }
            } finally {
                val nativeEndedNs = SystemClock.elapsedRealtimeNanos()
                runController.nativeReturned()
                recordTiming(
                    identity, "VISION_ENCODER_NATIVE", "VISION_ENCODER_NATIVE_END",
                    nativeStartedNs, nativeEndedNs, if (nativeReturnedSuccessfully) "PASS" else "ERROR",
                    runOptionsId = runOptionsId,
                    executionBoundary = "OrtSession.run",
                    sampleMemory = false,
                )
            }
            deadlineGuard?.throwIfExpired()
            operationJob.ensureActive()

            val embTensor = result["image_embeddings"].get() as OnnxTensor
            val posTensor = result["image_positional_embeddings"].get() as OnnxTensor
            val expectedEmbeddingShape = longArrayOf(1, 256, 64, 64)
            require(embTensor.info.type.name == "FLOAT" && embTensor.info.shape.contentEquals(expectedEmbeddingShape)) {
                "SLIMSAM_VISION_EMBEDDINGS_OUTPUT_SCHEMA_MISMATCH"
            }
            require(posTensor.info.type.name == "FLOAT" && posTensor.info.shape.contentEquals(expectedEmbeddingShape)) {
                "SLIMSAM_VISION_POSITIONAL_OUTPUT_SCHEMA_MISMATCH"
            }
            val embValues = embTensor.floatBuffer
            val posValues = posTensor.floatBuffer
            val outputHashes: Pair<String, String>? = if (identity != null) {
                timeEncoderStage(identity, "OUTPUT_VALIDATE_AND_SHA256") {
                    RecognitionTensorContentHash.sha256AndRequireFinite(
                        embValues,
                        "SLIMSAM_VISION_NON_FINITE_OUTPUT",
                    ) to RecognitionTensorContentHash.sha256AndRequireFinite(
                        posValues,
                        "SLIMSAM_VISION_NON_FINITE_OUTPUT",
                    )
                }
            } else {
                timeEncoderStage(identity, "OUTPUT_FINITE_SCAN") {
                    require(isFiniteFloatBuffer(embValues) && isFiniteFloatBuffer(posValues)) {
                        "SLIMSAM_VISION_NON_FINITE_OUTPUT"
                    }
                }
                null
            }
            if (identity != null) {
                val (embeddingHash, positionalHash) = requireNotNull(outputHashes)
                listOf(
                    Triple("image_embeddings", embTensor, embeddingHash),
                    Triple("image_positional_embeddings", posTensor, positionalHash),
                ).forEach { (name, outputTensor, contentHash) ->
                    recordTensorReceipt(
                        direction = "OUTPUT",
                        identity = identity,
                        inferenceId = encoderInferenceId,
                        model = requireNotNull(encoderModelIdentity),
                        tensorName = name,
                        dataType = outputTensor.info.type.name,
                        shape = outputTensor.info.shape.toList(),
                        byteSize = outputTensor.info.shape.fold(1L) { size, axis -> size * axis } * Float.SIZE_BYTES,
                        contentHash = contentHash,
                        startUptimeMs = encoderStartedAtUptimeMs,
                        endUptimeMs = SystemClock.uptimeMillis(),
                    )
                }
            }
            timeEncoderStage(identity, "EMBEDDING_COPY") {
                embBuffer.put(embValues)
                embBuffer.rewind()
                posEmbBuffer.put(posValues)
                posEmbBuffer.rewind()
            }
            val encoderEndedNs = SystemClock.elapsedRealtimeNanos()
            recordTiming(
                identity,
                "VISION_ENCODER",
                "VISION_ENCODER_END",
                encoderStart,
                encoderEndedNs,
                "PASS",
                runOptionsId = runOptionsId,
                modelSha256 = visionModelSha256,
            )
        } catch (error: Throwable) {
            operationFailure = runCatching { deadlineGuard?.throwIfExpired() }.exceptionOrNull() ?: error
            if (nativeCallStarted) runController?.nativeReturned() else runController?.nativeNotStarted()
        } finally {
            cancellationHandle?.dispose()
            deadlineGuard?.close()
            val cleanupMarkerNs = SystemClock.elapsedRealtimeNanos()
            recordTiming(identity, "NATIVE_RESOURCE_CLEANUP", "RESOURCE_CLEANUP_START", cleanupMarkerNs, cleanupMarkerNs, "STARTED",
                runOptionsId = runController?.snapshot()?.runOptionsId,
                modelSha256 = visionModelSha256,
            )
            val cleanupStartedNs = SystemClock.elapsedRealtimeNanos()
            val cleanupConfirmed: Boolean
            var preCleanupFailure: Throwable? = null
            preCleanupFailure = closeNativeResource(runResult, "Vision Encoder Ort.Result", preCleanupFailure)
            preCleanupFailure = closeNativeResource(inputTensor, "Vision Encoder input tensor", preCleanupFailure)
            val controller = runController
            val options = runOptions
            if (controller != null && options != null) {
                controller.finishResourceCleanup {
                    var cleanupFailure = preCleanupFailure
                    cleanupFailure = closeNativeResource(options, "Vision Encoder RunOptions", cleanupFailure)
                    try {
                        nativeLease.close()
                    } catch (error: Throwable) {
                        cleanupFailure = addCleanupFailure(cleanupFailure, error)
                    }
                    if (cleanupFailure == null) controller.cleanupCompleted()
                    else controller.cleanupFailed(cleanupFailure.message ?: cleanupFailure.javaClass.simpleName)
                }
                cleanupConfirmed = controller.awaitCleanup(SlimSamNativeRunController.DEFAULT_TERMINATION_WAIT_MS)
                if (!cleanupConfirmed) {
                    val snapshot = controller.snapshot()
                    val cleanupEvent = when {
                        snapshot.state == SlimSamNativeRunState.CLEANUP_FAILED -> "RESOURCE_CLEANUP_FAILED"
                        !snapshot.nativeReturned && snapshot.nativeStarted -> "NATIVE_EXECUTION_STILL_RUNNING"
                        snapshot.terminationUnconfirmed -> "NATIVE_TERMINATION_CONTROL_STILL_RUNNING"
                        else -> "RESOURCE_CLEANUP_UNCONFIRMED"
                    }
                    recordTiming(
                        identity,
                        "NATIVE_RESOURCE_CLEANUP",
                        cleanupEvent,
                        cleanupStartedNs,
                        SystemClock.elapsedRealtimeNanos(),
                        "UNCONFIRMED",
                        errorCode = snapshot.failure ?: "NATIVE_CLEANUP_UNCONFIRMED",
                        runOptionsId = snapshot.runOptionsId,
                        modelSha256 = visionModelSha256,
                    )
                    if (snapshot.state == SlimSamNativeRunState.CLEANUP_FAILED && operationFailure == null) {
                        operationFailure = SlimSamModelException(
                            "NATIVE_RESOURCE_CLEANUP_FAILED",
                            snapshot.failure ?: "SlimSAM Native resources did not close cleanly",
                            "VISION_ENCODER_CLEANUP",
                        )
                    } else if (operationFailure == null) {
                        operationFailure = SlimSamModelException(
                            "NATIVE_RESOURCE_CLEANUP_UNCONFIRMED",
                            snapshot.failure ?: "SlimSAM Native resource cleanup is still pending",
                            "VISION_ENCODER_CLEANUP",
                        )
                    }
                }
            } else {
                preCleanupFailure = closeNativeResource(runOptions, "Vision Encoder RunOptions", preCleanupFailure)
                nativeLease.close()
                cleanupConfirmed = preCleanupFailure == null
            }
            if (cleanupConfirmed) {
                recordTiming(
                    identity, "NATIVE_RESOURCE_CLEANUP", "NATIVE_RESOURCE_CLEANUP_END",
                    cleanupStartedNs, SystemClock.elapsedRealtimeNanos(), "PASS",
                    runOptionsId = controller?.snapshot()?.runOptionsId,
                    modelSha256 = visionModelSha256,
                    sampleMemory = false,
                )
            }
        }
        try {
            operationJob.ensureActive()
        } catch (cancelled: CancellationException) {
            operationFailure?.let(cancelled::addSuppressed)
            throw cancelled
        }
        operationFailure?.let { throw it }

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
        operationJob.ensureActive()
        check(embeddingCache.putIfActive(cacheKey, cached)) { "Stale SlimSAM embedding write rejected" }
        val cacheWriteAt = SystemClock.elapsedRealtimeNanos()
        recordTiming(identity, "EMBEDDING_CACHE", "EMBEDDING_CACHE_WRITE", cacheWriteAt, cacheWriteAt, "STORED")
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

    private suspend fun runMaskDecoder(
        embedding: CachedVisionEmbedding,
        points: List<SamPromptPoint>,
        identity: RecognitionModelIdentity?,
        operationJob: Job,
    ): Pair<FloatArray, Float> {
        operationJob.ensureActive()
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
        val decoderStartedAtUptimeMs = SystemClock.uptimeMillis()
        val inferenceId = "${identity?.attemptId ?: "unbound"}:SLIMSAM_DECODER:${inferenceSequence.incrementAndGet()}"
        val runtimeIdentity = identity?.let {
            runtimeModelIdentity(
                modelId = "SLIMSAM_MASK_DECODER",
                modelVersion = "MASK_DECODER_V1",
                expectedSha256 = DECODER_MODEL_SHA256,
                actualSha256 = requireNotNull(decoderModelSha256),
                inputSchemaHash = schemaHash(decoder.inputInfo),
                outputSchemaHash = schemaHash(decoder.outputInfo),
                preprocessVersion = "slimsam-prompt-points-v1",
                sessionId = decoderModelSessionId,
            )
        }

        val nativeLease = nativeSessionGate.acquire()
            ?: throw SlimSamModelException(
                "SLIMSAM_NATIVE_SESSION_UNAVAILABLE",
                "SlimSAM Native Session is closing, poisoned, or still releasing a prior run",
                "MASK_DECODER",
            )
        val inputs = mutableMapOf<String, OnnxTensor>()
        var runOptions: OrtSession.RunOptions? = null
        var runController: SlimSamNativeRunController? = null
        var deadlineGuard: RecognitionNativeRunDeadlineGuard? = null
        var cancellationHandle: kotlinx.coroutines.DisposableHandle? = null
        var runResult: AutoCloseable? = null
        var nativeCallStarted = false
        var output: Pair<FloatArray, Float>? = null
        var operationFailure: Throwable? = null
        try {
            val ortEnvironment = requireNotNull(environment)
            inputs["input_points"] = OnnxTensor.createTensor(ortEnvironment, pointsBuffer, pointsShape)
            inputs["input_labels"] = OnnxTensor.createTensor(ortEnvironment, labelsBuffer, labelsShape)
            inputs["image_embeddings"] = OnnxTensor.createTensor(ortEnvironment, embedding.imageEmbeddings, embShape)
            inputs["image_positional_embeddings"] = OnnxTensor.createTensor(ortEnvironment, embedding.imagePositionalEmbeddings, embShape)
            operationJob.ensureActive()
            val expectedShapes = mapOf(
                "input_points" to pointsShape,
                "input_labels" to labelsShape,
                "image_embeddings" to embShape,
                "image_positional_embeddings" to embShape,
            )
            inputs.forEach { (name, tensor) ->
                val expectedType = if (name == "input_labels") "INT64" else "FLOAT"
                require(tensor.info.type.name == expectedType && tensor.info.shape.contentEquals(expectedShapes.getValue(name))) {
                    "SLIMSAM_DECODER_INPUT_SCHEMA_MISMATCH:$name"
                }
            }
            if (identity != null) {
                inputs.forEach { (name, tensor) ->
                    val contentHash = if (name == "input_labels") {
                        RecognitionTensorContentHash.sha256(tensor.longBuffer)
                    } else RecognitionTensorContentHash.sha256(tensor.floatBuffer)
                    val byteSize = tensor.info.shape.fold(1L) { size, axis -> size * axis } *
                        if (tensor.info.type.name == "INT64") Long.SIZE_BYTES else Float.SIZE_BYTES
                    recordTensorReceipt(
                        direction = "INPUT",
                        identity = identity,
                        inferenceId = inferenceId,
                        model = requireNotNull(runtimeIdentity),
                        tensorName = name,
                        dataType = tensor.info.type.name,
                        shape = tensor.info.shape.toList(),
                        byteSize = byteSize,
                        contentHash = contentHash,
                        startUptimeMs = decoderStartedAtUptimeMs,
                        endUptimeMs = SystemClock.uptimeMillis(),
                    )
                }
            }
            runOptions = OrtSession.RunOptions()
            val runOptionsId = UUID.randomUUID().toString()
            val decoderTensorMetadata = DECODER_RUN_TENSOR_METADATA.replace("<N>", numPoints.toString())
            val decoderInputTensorBytes = DECODER_EMBEDDING_TENSOR_BYTES + numPoints * DECODER_POINT_AND_LABEL_BYTES_PER_POINT
            runController = createNativeRunController(
                identity = identity,
                stage = "MASK_DECODER_NATIVE",
                runOptionsId = runOptionsId,
                modelSha256 = decoderModelSha256,
                runOptions = runOptions,
                tensorMetadata = decoderTensorMetadata,
                knownTensorInputBytes = decoderInputTensorBytes,
            )
            cancellationHandle = installSlimSamCancellationHandler(operationJob, runController)
            deadlineGuard = identity?.attemptDeadline?.let { deadline ->
                val controller = requireNotNull(runController)
                RecognitionNativeRunDeadlineGuard.create(deadline) { controller.requestTermination(); Unit }
            }
            deadlineGuard?.throwIfExpired()
            operationJob.ensureActive()
            val nativeStartNs = SystemClock.elapsedRealtimeNanos()
            recordTiming(
                identity,
                "MASK_DECODER",
                "MASK_DECODER_NATIVE_START",
                nativeStartNs,
                nativeStartNs,
                "STARTED",
                runOptionsId = runOptionsId,
                modelSha256 = decoderModelSha256,
                executionBoundary = "OrtSession.run",
                tensorMetadata = decoderTensorMetadata,
                knownTensorInputBytes = decoderInputTensorBytes,
            )
            runController.nativeStarted()
            nativeCallStarted = true
            val result = decoder.run(inputs, runOptions)
            runResult = result
            runController.nativeReturned()
            deadlineGuard?.throwIfExpired()
            operationJob.ensureActive()

            val iouScoresTensor = result["iou_scores"].get() as OnnxTensor
            val predMasksTensor = result["pred_masks"].get() as OnnxTensor
            require(iouScoresTensor.info.type.name == "FLOAT" &&
                iouScoresTensor.info.shape.contentEquals(longArrayOf(1, 1, 3))) {
                "SLIMSAM_DECODER_IOU_OUTPUT_SCHEMA_MISMATCH"
            }
            require(predMasksTensor.info.type.name == "FLOAT" &&
                predMasksTensor.info.shape.contentEquals(longArrayOf(1, 1, 3, 256, 256))) {
                "SLIMSAM_DECODER_MASK_OUTPUT_SCHEMA_MISMATCH"
            }
            require(isFiniteFloatBuffer(iouScoresTensor.floatBuffer) && isFiniteFloatBuffer(predMasksTensor.floatBuffer)) {
                "SLIMSAM_DECODER_NON_FINITE_OUTPUT"
            }
            if (identity != null) {
                listOf("iou_scores" to iouScoresTensor, "pred_masks" to predMasksTensor).forEach { (name, tensor) ->
                    recordTensorReceipt(
                        direction = "OUTPUT",
                        identity = identity,
                        inferenceId = inferenceId,
                        model = requireNotNull(runtimeIdentity),
                        tensorName = name,
                        dataType = tensor.info.type.name,
                        shape = tensor.info.shape.toList(),
                        byteSize = tensor.info.shape.fold(1L) { size, axis -> size * axis } * Float.SIZE_BYTES,
                        contentHash = RecognitionTensorContentHash.sha256(tensor.floatBuffer),
                        startUptimeMs = decoderStartedAtUptimeMs,
                        endUptimeMs = SystemClock.uptimeMillis(),
                    )
                }
            }
            val iouScores = FloatArray(3)
            iouScoresTensor.floatBuffer.get(iouScores)

            val singleMaskSize = 256 * 256
            val allMasks = FloatArray(3 * singleMaskSize)
            predMasksTensor.floatBuffer.get(allMasks)

            // Select the best mask candidate using the production confidence/area contract.
            var bestCandidate = 0
            var bestScore = -1000f
            for (candidate in 0 until 3) {
                val offset = candidate * singleMaskSize
                var positiveCount = 0
                for (index in 0 until singleMaskSize) {
                    if (allMasks[offset + index] > 0f) positiveCount++
                }
                val score = iouScores[candidate] * 0.7f +
                    (positiveCount.toFloat() / singleMaskSize) * 0.3f
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }

            val bestMask = FloatArray(singleMaskSize)
            System.arraycopy(allMasks, bestCandidate * singleMaskSize, bestMask, 0, singleMaskSize)
            output = bestMask to iouScores[bestCandidate]
        } catch (error: Throwable) {
            operationFailure = runCatching { deadlineGuard?.throwIfExpired() }.exceptionOrNull() ?: error
            if (nativeCallStarted) runController?.nativeReturned() else runController?.nativeNotStarted()
        } finally {
            cancellationHandle?.dispose()
            deadlineGuard?.close()
            val cleanupStartedNs = SystemClock.elapsedRealtimeNanos()
            recordTiming(identity, "NATIVE_RESOURCE_CLEANUP", "RESOURCE_CLEANUP_START", cleanupStartedNs, cleanupStartedNs, "STARTED",
                runOptionsId = runController?.snapshot()?.runOptionsId,
                modelSha256 = decoderModelSha256,
            )
            var preCleanupFailure: Throwable? = null
            preCleanupFailure = closeNativeResource(runResult, "Mask Decoder Ort.Result", preCleanupFailure)
            inputs.values.forEach { tensor ->
                preCleanupFailure = closeNativeResource(tensor, "Mask Decoder input tensor", preCleanupFailure)
            }
            val controller = runController
            val options = runOptions
            if (controller != null && options != null) {
                controller.finishResourceCleanup {
                    var cleanupFailure = preCleanupFailure
                    cleanupFailure = closeNativeResource(options, "Mask Decoder RunOptions", cleanupFailure)
                    try {
                        nativeLease.close()
                    } catch (error: Throwable) {
                        cleanupFailure = addCleanupFailure(cleanupFailure, error)
                    }
                    if (cleanupFailure == null) controller.cleanupCompleted()
                    else controller.cleanupFailed(cleanupFailure.message ?: cleanupFailure.javaClass.simpleName)
                }
                if (!controller.awaitCleanup(SlimSamNativeRunController.DEFAULT_TERMINATION_WAIT_MS)) {
                    val snapshot = controller.snapshot()
                    val cleanupEvent = when {
                        snapshot.state == SlimSamNativeRunState.CLEANUP_FAILED -> "RESOURCE_CLEANUP_FAILED"
                        !snapshot.nativeReturned && snapshot.nativeStarted -> "NATIVE_EXECUTION_STILL_RUNNING"
                        snapshot.terminationUnconfirmed -> "NATIVE_TERMINATION_CONTROL_STILL_RUNNING"
                        else -> "RESOURCE_CLEANUP_UNCONFIRMED"
                    }
                    recordTiming(
                        identity,
                        "NATIVE_RESOURCE_CLEANUP",
                        cleanupEvent,
                        cleanupStartedNs,
                        SystemClock.elapsedRealtimeNanos(),
                        "UNCONFIRMED",
                        errorCode = snapshot.failure ?: "NATIVE_CLEANUP_UNCONFIRMED",
                        runOptionsId = snapshot.runOptionsId,
                        modelSha256 = decoderModelSha256,
                    )
                    if (snapshot.state == SlimSamNativeRunState.CLEANUP_FAILED && operationFailure == null) {
                        operationFailure = SlimSamModelException(
                            "NATIVE_RESOURCE_CLEANUP_FAILED",
                            snapshot.failure ?: "SlimSAM Native resources did not close cleanly",
                            "MASK_DECODER_CLEANUP",
                        )
                    } else if (operationFailure == null) {
                        operationFailure = SlimSamModelException(
                            "NATIVE_RESOURCE_CLEANUP_UNCONFIRMED",
                            snapshot.failure ?: "SlimSAM Native resource cleanup is still pending",
                            "MASK_DECODER_CLEANUP",
                        )
                    }
                }
            } else {
                closeNativeResource(runOptions, "Mask Decoder RunOptions", preCleanupFailure)
                nativeLease.close()
            }
        }
        try {
            operationJob.ensureActive()
        } catch (cancelled: CancellationException) {
            operationFailure?.let(cancelled::addSuppressed)
            throw cancelled
        }
        operationFailure?.let { throw it }
        return requireNotNull(output) { "SlimSAM Mask Decoder completed without a mask result" }
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

    private inline fun <T> timeEncoderStage(
        identity: RecognitionModelIdentity?,
        stage: String,
        block: () -> T,
    ): T {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        var completed = false
        try {
            return block().also { completed = true }
        } finally {
            recordTiming(
                identity, stage, "${stage}_END", startedNs, SystemClock.elapsedRealtimeNanos(),
                if (completed) "PASS" else "ERROR",
                modelSha256 = visionModelSha256,
                sampleMemory = false,
            )
        }
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
        runOptionsId: String? = null,
        nodeName: String? = null,
        executionBoundary: String? = null,
        knownTensorInputBytes: Long? = null,
        modelSha256: String? = when {
            stage.contains("VISION", ignoreCase = true) -> visionModelSha256
            stage.contains("DECODER", ignoreCase = true) || stage.contains("MASK", ignoreCase = true) -> decoderModelSha256
            else -> null
        },
        sampleMemory: Boolean = true,
    ) {
        val activeNativeRuns = nativeSessionGate.snapshot().activeRunCount
        val memory = if (sampleMemory) SlimSamMemorySnapshot.capture() else null
        val memorySampleUptimeNs = memory?.let { SystemClock.elapsedRealtimeNanos() }
        eventSink(
            SlimSamRuntimeEvent(
                recognitionSessionId = identity?.sessionId.orEmpty(),
                recognitionAttemptId = identity?.attemptId.orEmpty(),
                imageId = identity?.imageId.orEmpty(),
                photoSha256 = identity?.photoSha256.orEmpty(),
                modelSha256 = modelSha256,
                ortVersion = ORT_VERSION,
                runOptionsId = runOptionsId,
                activeNativeRunCount = activeNativeRuns,
                nodeName = nodeName,
                executionBoundary = executionBoundary,
                knownTensorInputBytes = knownTensorInputBytes,
                androidNativeHeapAllocatedBytes = memory?.nativeHeapAllocatedBytes,
                androidNativeHeapSizeBytes = memory?.nativeHeapSizeBytes,
                androidNativeHeapFreeBytes = memory?.nativeHeapFreeBytes,
                androidProcessPssKb = memory?.processPssKb,
                androidProcessRssKb = memory?.processRssKb,
                memorySampleUptimeNs = memorySampleUptimeNs,
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

    private fun createNativeRunController(
        identity: RecognitionModelIdentity?,
        stage: String,
        runOptionsId: String,
        modelSha256: String?,
        runOptions: OrtSession.RunOptions,
        tensorMetadata: String,
        knownTensorInputBytes: Long,
    ) = SlimSamNativeRunController(
        runOptionsId = runOptionsId,
        terminationExecutor = SlimSamNativeRunExecutors.termination,
        watchdogScheduler = SlimSamNativeRunExecutors.watchdog,
        terminationWaitMs = SlimSamNativeRunController.DEFAULT_TERMINATION_WAIT_MS,
        setTerminate = { runOptions.setTerminate(true) },
        onEvent = { event, detail ->
            val now = SystemClock.elapsedRealtimeNanos()
            val logEvent = Runnable {
                val unconfirmed = event.contains("UNCONFIRMED")
                recordTiming(
                    identity = identity,
                    stage = stage,
                    event = event,
                    startedAtNs = now,
                    endedAtNs = now,
                    result = if (unconfirmed) "UNCONFIRMED" else "OBSERVED",
                    errorCode = if (unconfirmed) "NATIVE_TERMINATION_UNCONFIRMED" else "",
                    errorMessage = detail,
                    runOptionsId = runOptionsId,
                    modelSha256 = modelSha256,
                    executionBoundary = "OrtSession.run",
                    tensorMetadata = tensorMetadata,
                    knownTensorInputBytes = knownTensorInputBytes,
                )
                Log.i(TAG, "SLIMSAM_NATIVE_LIFECYCLE event=$event runOptionsId=$runOptionsId " +
                    "attemptId=${identity?.attemptId.orEmpty()} sessionId=${identity?.sessionId.orEmpty()} " +
                    "modelSha256=${modelSha256.orEmpty()} ortVersion=$ORT_VERSION detail=${detail.orEmpty()}")
            }
            runCatching { SlimSamNativeRunExecutors.eventLogging.execute(logEvent) }
                .onFailure { logEvent.run() }
        },
        onTerminationUnconfirmed = { reason ->
            nativeSessionGate.poison()
            Log.e(TAG, "NATIVE_TERMINATION_UNCONFIRMED runOptionsId=$runOptionsId stage=$stage reason=$reason")
        },
    )

    private fun closeNativeResource(resource: AutoCloseable?, label: String, previous: Throwable?): Throwable? {
        if (resource == null) return previous
        return try {
            resource.close()
            previous
        } catch (error: Throwable) {
            Log.e(TAG, "SlimSAM resource cleanup failed: $label", error)
            addCleanupFailure(previous, error)
        }
    }

    private fun addCleanupFailure(previous: Throwable?, error: Throwable): Throwable {
        if (previous == null) return error
        if (previous !== error) previous.addSuppressed(error)
        return previous
    }

    private fun schemaHash(nodes: Map<String, NodeInfo>): String {
        val schema = nodes.toSortedMap().entries.joinToString("|") { (name, node) ->
            val tensor = node.info as? TensorInfo
                ?: throw SlimSamModelException("MODEL_TENSOR_SCHEMA_INVALID", "$name is not a tensor", "MODEL_SCHEMA")
            "$name:${tensor.type.name}:${tensor.shape.joinToString(",")}"
        }
        return sha256(schema.toByteArray(Charsets.UTF_8))
    }

    private fun runtimeModelIdentity(
        modelId: String,
        modelVersion: String,
        expectedSha256: String,
        actualSha256: String,
        inputSchemaHash: String,
        outputSchemaHash: String,
        preprocessVersion: String,
        sessionId: String,
    ): RecognitionRuntimeModelIdentity = RecognitionRuntimeModelIdentity(
        modelId = modelId,
        modelVersion = modelVersion,
        expectedModelSha256 = expectedSha256,
        actualAssetSha256 = actualSha256,
        runtimeVerifiedModelSha256 = actualSha256,
        modelFormat = "ONNX",
        inputSchemaHash = inputSchemaHash,
        outputSchemaHash = outputSchemaHash,
        preprocessVersion = preprocessVersion,
        postprocessVersion = SLIMSAM_OUTPUT_VALIDATION_VERSION,
        runtimeName = "ONNX Runtime Android",
        runtimeVersion = ORT_VERSION,
        executionProvider = "CPU",
        modelSessionId = sessionId,
    ).also { require(it.isValid()) { "SLIMSAM_MODEL_IDENTITY_INVALID:$modelId" } }

    private fun recordTensorReceipt(
        direction: String,
        identity: RecognitionModelIdentity,
        inferenceId: String,
        model: RecognitionRuntimeModelIdentity,
        tensorName: String,
        dataType: String,
        shape: List<Long>,
        byteSize: Long,
        contentHash: String,
        startUptimeMs: Long,
        endUptimeMs: Long,
    ) {
        val receipt = RecognitionModelTensorReceipt(
            inferenceId = inferenceId,
            recognitionSessionId = identity.sessionId,
            attemptId = identity.attemptId,
            photoId = identity.imageId,
            canonicalPixelSha256 = identity.photoIdentity?.canonicalPixelSha256 ?: identity.photoSha256,
            modelIdentity = model,
            tensorName = tensorName,
            dataType = dataType,
            shape = shape,
            byteSize = byteSize,
            contentHash = contentHash,
            startUptimeMs = startUptimeMs,
            endUptimeMs = endUptimeMs.coerceAtLeast(startUptimeMs),
            validationStatus = "VALID",
        )
        require(receipt.isValid()) { "SLIMSAM_${direction}_TENSOR_RECEIPT_INVALID:$tensorName" }
        Log.i(
            TAG,
            "MODEL_TENSOR_RECEIPT direction=$direction inferenceId=${receipt.inferenceId} " +
                "recognitionSessionId=${receipt.recognitionSessionId} attemptId=${receipt.attemptId} " +
                "photoId=${receipt.photoId} canonicalPixelSha256=${receipt.canonicalPixelSha256} " +
                "modelId=${model.modelId} modelVersion=${model.modelVersion} " +
                "expectedModelSha256=${model.expectedModelSha256} actualAssetSha256=${model.actualAssetSha256} " +
                "runtimeVerifiedModelSha256=${model.runtimeVerifiedModelSha256} " +
                "inputSchemaHash=${model.inputSchemaHash} outputSchemaHash=${model.outputSchemaHash} " +
                "runtime=${model.runtimeName}/${model.runtimeVersion} provider=${model.executionProvider} " +
                "modelSessionId=${model.modelSessionId} tensor=${receipt.tensorName} type=${receipt.dataType} " +
                "shape=${receipt.shape} byteSize=${receipt.byteSize} contentHash=${receipt.contentHash} " +
                "startUptimeMs=${receipt.startUptimeMs} endUptimeMs=${receipt.endUptimeMs} " +
                "validation=${receipt.validationStatus}",
        )
    }

    private fun isFiniteFloatBuffer(buffer: FloatBuffer): Boolean {
        val copy = buffer.asReadOnlyBuffer()
        while (copy.hasRemaining()) if (!copy.get().isFinite()) return false
        return true
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
        nativeSessionGate.requestClose()
        embeddingCache.clear()
    }

    companion object {
        private const val TAG = "SlimSamSegmenter"
        const val VISION_ENCODER_MODEL = "slimsam/vision_encoder_quantized.onnx"
        const val MASK_DECODER_MODEL = "slimsam/prompt_encoder_mask_decoder_quantized.onnx"
        const val VISION_ENCODER_SHA256 = "cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971"
        const val DECODER_MODEL_SHA256 = "cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2"
        const val VISION_INPUT_SCHEMA_HASH = "be8ebb75a0fd5d253e00ba0628d7b14b4117ea2d39f093bc2ff088cbb20c2047"
        val VISION_INPUT_SHAPE = listOf(1, 3, 1024, 1024)
        const val MODEL_VERSION = "cce23c7b+cb90b279-v1"
        const val ORT_VERSION = "1.29.0"
        const val PREPROCESSING_VERSION = "rgb1024-pad-mean-std-v1"
        const val SLIMSAM_OUTPUT_VALIDATION_VERSION = "slimsam-output-validation-v1"
        const val EMBEDDING_CACHE_BYTES = 8L * 1024L * 1024L
        const val ENCODER_INPUT_TENSOR_BYTES = 12L * 1024L * 1024L
        const val DECODER_EMBEDDING_TENSOR_BYTES = 8L * 1024L * 1024L
        const val DECODER_POINT_AND_LABEL_BYTES_PER_POINT = 16L
        const val ENCODER_RUN_TENSOR_METADATA =
            "input=pixel_values:FLOAT[1,3,1024,1024];outputs=image_embeddings:FLOAT[1,256,64,64],image_positional_embeddings:FLOAT[1,256,64,64]"
        const val DECODER_RUN_TENSOR_METADATA =
            "inputs=input_points:FLOAT[1,1,<N>,2],input_labels:INT64[1,1,<N>],image_embeddings:FLOAT[1,256,64,64],image_positional_embeddings:FLOAT[1,256,64,64];outputs=iou_scores:FLOAT[1,1,3],pred_masks:FLOAT[1,1,3,256,256]"
    }
}
