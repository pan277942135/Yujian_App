package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.min

class FishRecognitionEngine(private val context: Context) : AutoCloseable {
    private data class PreparedBitmap(
        val bitmap: Bitmap,
        val scale: Float,
        val drawWidth: Float,
        val drawHeight: Float,
        val padLeft: Float,
        val padTop: Float,
    )

    private data class ModelInput(
        val buffer: ByteBuffer,
        val values: FloatArray,
    )

    private data class ModelReleaseManifest(
        val modelVersion: String,
        val modelSha256: String,
        val modelBytes: Int,
        val classCount: Int,
    )

    private val modelReleaseManifest: ModelReleaseManifest by lazy {
        val json = context.assets.open(MODEL_RELEASE_MANIFEST_FILE)
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        require(json.getString("schema_version") == MODEL_RELEASE_MANIFEST_SCHEMA) {
            "生产模型发布清单版本不匹配"
        }
        require(json.getString("release_repository") == MODEL_RELEASE_REPOSITORY) {
            "生产模型仓库不匹配"
        }
        require(json.getString("release_tag") == MODEL_RELEASE_TAG) {
            "生产模型 Release 通道不匹配"
        }
        val version = json.getString("model_version")
        val sha = json.getString("model_sha256").lowercase()
        val bytes = json.getInt("model_bytes")
        val classCount = json.getInt("class_count")
        require(version.isNotBlank()) { "生产模型版本为空" }
        require(sha.matches(Regex("[0-9a-f]{64}"))) { "生产模型 SHA-256 无效" }
        require(bytes > 0) { "生产模型大小无效" }
        require(classCount > 0) { "生产模型类别数无效" }
        ModelReleaseManifest(version, sha, bytes, classCount)
    }

    private val modelLabelsInternal: List<Pair<String, String>> by lazy {
        val json = context.assets.open(MODEL_CLASS_MAP_FILE)
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val array = json.getJSONArray("classes")
        val rows = (0 until array.length())
            .map { array.getJSONObject(it) }
            .sortedBy { it.getInt("class_index") }
        require(rows.map { it.getInt("class_index") } == rows.indices.toList()) {
            "生产模型 class_map class_index 必须从 0 连续"
        }
        rows.map { row ->
            val key = row.getString("species_key").trim()
            val name = row.optString("common_name_zh")
                .ifBlank { row.optString("species") }
                .ifBlank { key }
            require(key.isNotBlank()) { "生产模型 class_map species_key 为空" }
            key to name
        }.also { labels ->
            require(labels.isNotEmpty()) { "生产模型 class_map 为空" }
            require(labels.map { it.first }.distinct().size == labels.size) {
                "生产模型 class_map species_key 重复"
            }
            require(labels.size == modelReleaseManifest.classCount) {
                "生产模型类别数与发布清单不一致"
            }
        }
    }

    val modelVersion: String get() = modelReleaseManifest.modelVersion
    val modelSha256: String get() = modelReleaseManifest.modelSha256
    val modelLabels: List<Pair<String, String>> get() = modelLabelsInternal
    val modelClassCount: Int get() = modelLabelsInternal.size

    private val modelBytes: ByteArray by lazy {
        context.assets.open(MODEL_FILE).use { input ->
            ByteArrayOutputStream().use { out -> input.copyTo(out); out.toByteArray() }
        }.also { bytes ->
            val manifest = modelReleaseManifest
            val actual = bytes.sha256()
            check(actual == manifest.modelSha256) { "鱼类识别模型校验失败：$actual" }
            check(bytes.size == manifest.modelBytes) { "鱼类识别模型大小异常：${bytes.size}" }
            InferenceTrace.model(actual, bytes.size)
        }
    }

    private val interpreterLazy = lazy {
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(modelBytes).rewind()
        Interpreter(buffer, Interpreter.Options().apply { setNumThreads(4) })
    }
    private val interpreter get() = interpreterLazy.value

    suspend fun recognize(
        bitmap: Bitmap,
        pipelineContext: InferenceTrace.PipelineContext? = null,
    ): RecognitionPrediction = withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val manifest = modelReleaseManifest
        require(inputTensor.dataType() == DataType.FLOAT32) { "生产分类模型输入必须为 FLOAT32" }
        require(inputShape.size == 4) { "生产分类模型输入必须为 4D tensor" }

        val nchw = inputShape[1] == 3
        val nhwc = inputShape[3] == 3
        require(nchw || nhwc) { "模型输入必须包含 3 个 RGB 通道，实际=${inputShape.contentToString()}" }
        val height = if (nchw) inputShape[2] else inputShape[1]
        val width = if (nchw) inputShape[3] else inputShape[2]
        val layout = if (nchw) "NCHW" else "NHWC"

        InferenceTrace.bitmap("classifier_source_bitmap", bitmap)
        val prepared = prepareModelBitmap(bitmap, width, height)
        InferenceTrace.bitmap("model_input_letterbox", prepared.bitmap)
        val input = makeInputBuffer(prepared.bitmap, nchw)

        val outputTensor = interpreter.getOutputTensor(0)
        require(outputTensor.dataType() == DataType.FLOAT32) { "生产分类模型输出必须为 FLOAT32" }
        val labels = modelLabelsInternal
        val count = outputTensor.shape().last()
        require(count == labels.size) { "模型输出类别数应为 ${labels.size}，实际为 $count" }

        val output = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input.buffer, output)
        output.rewind()
        val logits = FloatArray(count) { output.float }
        val probabilities = softmax(logits)
        val candidates = labels.mapIndexed { index, label ->
            RecognitionCandidate(index, label.first, label.second, probabilities[index].coerceIn(0f, 1f))
        }.sortedByDescending { it.confidence }
        val top1 = candidates.first()
        val latencyMs = (System.nanoTime() - started) / 1_000_000

        InferenceTrace.report(
            modelVersion = manifest.modelVersion,
            modelSha256 = manifest.modelSha256,
            sourceBitmap = bitmap,
            preparedBitmap = prepared.bitmap,
            inputShape = inputShape,
            layout = layout,
            scale = prepared.scale,
            drawWidth = prepared.drawWidth,
            drawHeight = prepared.drawHeight,
            padLeft = prepared.padLeft,
            padTop = prepared.padTop,
            paddingRgb = intArrayOf(PADDING_R, PADDING_G, PADDING_B),
            normalizationMean = IMAGENET_MEAN,
            normalizationStd = IMAGENET_STD,
            inputValues = input.values,
            logits = logits,
            probabilities = probabilities,
            labels = labels,
            latencyMs = latencyMs,
            pipelineContext = pipelineContext,
        )

        Log.i(
            LOG_TAG,
            "model=${manifest.modelVersion} top1=${top1.classIndex}:${top1.speciesKey} confidence=${top1.confidence} latencyMs=$latencyMs",
        )
        Log.i(
            LOG_TAG,
            "top3=" + candidates.take(3).joinToString { "${it.classIndex}:${it.speciesKey}:${it.confidence}" },
        )
        RecognitionPrediction(
            modelVersion = manifest.modelVersion,
            modelSha256 = manifest.modelSha256,
            top1 = top1,
            candidates = candidates,
            latencyMs = latencyMs,
            modelInputBitmap = prepared.bitmap,
        )
    }

    /**
     * Production classifier preprocessing for the mutable mobile-model-v0.2 channel.
     *
     * The caller owns source selection. The production FishRecognitionPipeline passes
     * a detector-expanded fish crop; classifier-only parity tests can pass a direct bitmap.
     * This method preserves the entire supplied bitmap, fits it inside the model square,
     * and pads with ImageNet-mean RGB so padding becomes ~0 after normalization.
     */
    private fun prepareModelBitmap(bitmap: Bitmap, width: Int, height: Int): PreparedBitmap {
        require(bitmap.width > 0 && bitmap.height > 0)
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f

        val prepared = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { out ->
            val canvas = Canvas(out)
            canvas.drawColor(Color.rgb(PADDING_R, PADDING_G, PADDING_B))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        }
        return PreparedBitmap(
            bitmap = prepared,
            scale = scale,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            padLeft = left,
            padTop = top,
        )
    }

    private fun makeInputBuffer(bitmap: Bitmap, nchw: Boolean): ModelInput {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pixelCount = pixels.size
        val values = FloatArray(pixelCount * 3)

        pixels.forEachIndexed { index, pixel ->
            val r = normalize(pixel shr 16 and 0xFF, IMAGENET_MEAN[0], IMAGENET_STD[0])
            val g = normalize(pixel shr 8 and 0xFF, IMAGENET_MEAN[1], IMAGENET_STD[1])
            val b = normalize(pixel and 0xFF, IMAGENET_MEAN[2], IMAGENET_STD[2])
            if (nchw) {
                values[index] = r
                values[pixelCount + index] = g
                values[pixelCount * 2 + index] = b
            } else {
                val offset = index * 3
                values[offset] = r
                values[offset + 1] = g
                values[offset + 2] = b
            }
        }

        InferenceTrace.tensorHead(values)
        val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).also { out ->
            out.asFloatBuffer().put(values)
            out.rewind()
        }
        return ModelInput(buffer = buffer, values = values)
    }

    private fun normalize(channel: Int, mean: Float, std: Float): Float = (channel / 255f - mean) / std

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val denominator = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / denominator }.toFloatArray()
    }

    override fun close() {
        if (interpreterLazy.isInitialized()) interpreterLazy.value.close()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    companion object {
        const val MODEL_FILE = "fish_classifier.tflite"
        const val MODEL_RELEASE_MANIFEST_FILE = "model_release_manifest.json"
        const val MODEL_CLASS_MAP_FILE = "model_class_map.json"
        private const val MODEL_RELEASE_MANIFEST_SCHEMA = "YUJIAN_ANDROID_MODEL_RELEASE_v1"
        private const val MODEL_RELEASE_REPOSITORY = "pan277942135/Yujian"
        private const val MODEL_RELEASE_TAG = "mobile-model-v0.2"
        private const val LOG_TAG = "FishRecognitionEngine"

        private const val PADDING_R = 124
        private const val PADDING_G = 116
        private const val PADDING_B = 104
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    }
}
