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
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.min

data class ProductionModelClass(
    val classIndex: Int,
    val speciesKey: String,
    val displayName: String,
)

data class ProductionModelInfo(
    val modelId: String,
    val datasetId: String,
    val releaseTag: String,
    val releaseId: Long,
    val sha256: String,
    val bytes: Int,
    val classCount: Int,
    val inputShape: List<Int>,
    val inputDtype: String,
    val outputShape: List<Int>,
    val outputDtype: String,
    val classMapSha256: String,
    val tensorContractSha256: String,
    val classes: List<ProductionModelClass>,
) {
    val classOrder: List<String> get() = classes.map { it.speciesKey }
}

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

    private val modelInfoLazy = lazy { readProductionModelInfo() }
    val modelInfo: ProductionModelInfo get() = modelInfoLazy.value

    private fun readProductionModelInfo(): ProductionModelInfo {
        fun assetBytes(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }
        fun shape(source: JSONObject, key: String): List<Int> {
            val array = source.getJSONArray(key)
            require(array.length() > 0) { "$key 不能为空" }
            return (0 until array.length()).map { index -> array.getInt(index).also { require(it > 0) } }
        }
        fun sha256(bytes: ByteArray): String = bytes.sha256()

        val snapshotBytes = assetBytes(MODEL_SNAPSHOT_FILE)
        val snapshot = JSONObject(snapshotBytes.toString(Charsets.UTF_8))
        val metadataBytes = assetBytes(MODEL_METADATA_FILE)
        val metadata = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        val classMapBytes = assetBytes(MODEL_CLASS_MAP_FILE)
        val classMap = JSONObject(classMapBytes.toString(Charsets.UTF_8))
        val tensorContractBytes = assetBytes(MODEL_TENSOR_CONTRACT_FILE)
        val tensorContract = JSONObject(tensorContractBytes.toString(Charsets.UTF_8))

        val modelId = snapshot.getString("model_id")
        val datasetId = snapshot.getString("dataset_id")
        val releaseTag = snapshot.getString("release_tag")
        val releaseId = snapshot.getLong("release_id")
        require(snapshot.getInt("schema_version") == 1) { "不支持的正式模型快照版本" }
        require(modelId.isNotBlank() && datasetId.isNotBlank() && releaseId > 0L)
        require(releaseTag == PRODUCTION_RELEASE_TAG) { "正式模型 Release tag 不匹配" }
        require(metadata.getString("model_id") == modelId) { "模型 metadata/model_id 不匹配" }
        require(metadata.getString("dataset_id") == datasetId) { "模型 metadata/dataset_id 不匹配" }
        require(metadata.getString("published_filename") == PRODUCTION_RELEASE_MODEL_FILE)

        val modelSha = snapshot.getString("sha256")
        val modelBytes = snapshot.getInt("bytes")
        require(modelBytes >= 8) { "正式模型快照 bytes 无效" }
        require(modelSha.matches(Regex("[0-9a-f]{64}")))
        require(metadata.getString("sha256") == modelSha) { "正式模型 metadata SHA 与快照不匹配" }
        require(snapshot.getString("metadata_sha256") == sha256(metadataBytes)) {
            "正式模型 metadata 文件与快照不匹配"
        }
        val classMapSha = sha256(classMapBytes)
        val tensorSha = sha256(tensorContractBytes)
        require(snapshot.getString("class_map_sha256") == classMapSha) { "class_map SHA 与快照不匹配" }
        require(snapshot.getString("tensor_contract_sha256") == tensorSha) {
            "tensor_contract SHA 与快照不匹配"
        }

        val classArray = classMap.getJSONArray("classes")
        val classes = (0 until classArray.length()).map { index ->
            val item = classArray.getJSONObject(index)
            ProductionModelClass(
                classIndex = item.getInt("class_index"),
                speciesKey = item.getString("species_key"),
                displayName = item.optString("common_name_zh").ifBlank { item.getString("species_key") },
            )
        }.sortedBy { it.classIndex }
        require(classes.isNotEmpty() && classes.map { it.classIndex } == classes.indices.toList()) {
            "正式 class_map 的 class_index 必须从 0 连续排列"
        }
        require(classes.map { it.speciesKey }.distinct().size == classes.size) { "class_map species_key 重复" }
        require(classes.all { it.speciesKey.isNotBlank() && it.displayName.isNotBlank() })

        val classCount = snapshot.getInt("class_count")
        require(classCount == classes.size && metadata.getInt("num_classes") == classes.size) {
            "正式 model metadata/class_map 类别数不一致"
        }
        val classOrder = snapshot.getJSONArray("class_order")
        require(classOrder.length() == classes.size && (0 until classOrder.length()).all { classOrder.getString(it) == classes[it].speciesKey }) {
            "正式模型快照类别顺序与 class_map 不一致"
        }
        val metadataClassNames = metadata.getJSONArray("class_names")
        require(metadataClassNames.length() == classes.size &&
            (0 until metadataClassNames.length()).all { metadataClassNames.getString(it) == classes[it].speciesKey }) {
            "model metadata 类别顺序与 class_map 不一致"
        }

        val inputs = tensorContract.getJSONArray("inputs")
        val outputs = tensorContract.getJSONArray("outputs")
        require(inputs.length() == 1 && outputs.length() == 1) { "classifier 必须恰有一个输入和一个输出 tensor" }
        val inputTensor = inputs.getJSONObject(0)
        val outputTensor = outputs.getJSONObject(0)
        val inputShape = shape(inputTensor, "shape")
        val outputShape = shape(outputTensor, "shape")
        val snapshotInputShape = shape(snapshot, "input_shape")
        val snapshotOutputShape = shape(snapshot, "output_shape")
        require(inputShape == snapshotInputShape && outputShape == snapshotOutputShape) {
            "tensor_contract shape 与正式模型快照不一致"
        }
        require(inputShape.size == 4 && inputShape[0] == 1 && (inputShape[1] == 3 || inputShape.last() == 3)) {
            "正式模型输入必须为 batch-one RGB 4D tensor: $inputShape"
        }
        require(outputShape.last() == classes.size) { "模型输出类别数与 class_map 不一致" }
        val inputDtype = inputTensor.getString("dtype")
        val outputDtype = outputTensor.getString("dtype")
        require(inputDtype.contains("float32", ignoreCase = true) && outputDtype.contains("float32", ignoreCase = true)) {
            "正式模型 tensor dtype 必须为 FLOAT32"
        }
        require(snapshot.getString("input_dtype") == inputDtype && snapshot.getString("output_dtype") == outputDtype)

        val assets = snapshot.getJSONArray("release_assets")
        val requiredAssetNames = setOf(
            PRODUCTION_RELEASE_MODEL_FILE,
            MODEL_METADATA_FILE,
            MODEL_CLASS_MAP_FILE,
            MODEL_TENSOR_CONTRACT_FILE,
        )
        require(assets.length() == requiredAssetNames.size) { "模型快照缺少正式 Release asset 记录" }
        val assetNames = (0 until assets.length()).map { index ->
            val item = assets.getJSONObject(index)
            require(item.getLong("release_id") == releaseId) { "模型 assets 并非来自同一个 Release" }
            item.getString("name")
        }
        require(assetNames.toSet() == requiredAssetNames && assetNames.distinct().size == assetNames.size) {
            "正式模型快照 asset 集合不完整或重复"
        }

        return ProductionModelInfo(
            modelId = modelId,
            datasetId = datasetId,
            releaseTag = releaseTag,
            releaseId = releaseId,
            sha256 = modelSha,
            bytes = modelBytes,
            classCount = classCount,
            inputShape = inputShape,
            inputDtype = inputDtype,
            outputShape = outputShape,
            outputDtype = outputDtype,
            classMapSha256 = classMapSha,
            tensorContractSha256 = tensorSha,
            classes = classes,
        )
    }

    private fun checkTfliteHeader(bytes: ByteArray) {
        require(bytes.size >= 8 && bytes.copyOfRange(4, 8).contentEquals(byteArrayOf(0x54, 0x46, 0x4c, 0x33))) {
            "正式 classifier 不是有效 TFLite FlatBuffer"
        }
        val rootOffset = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        require(rootOffset in 8 until bytes.size.toLong()) { "正式 classifier FlatBuffer root offset 无效" }
    }

    private val modelBytes: ByteArray by lazy {
        context.assets.open(MODEL_FILE).use { input ->
            ByteArrayOutputStream().use { out -> input.copyTo(out); out.toByteArray() }
        }.also { bytes ->
            val actual = bytes.sha256()
            check(actual == modelInfo.sha256) { "鱼类识别模型校验失败：$actual" }
            check(bytes.size == modelInfo.bytes) { "鱼类识别模型大小异常：${bytes.size}" }
            checkTfliteHeader(bytes)
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
        require(inputTensor.dataType() == DataType.FLOAT32) { "${modelInfo.modelId} 输入必须为 FLOAT32" }
        require(inputShape.contentEquals(modelInfo.inputShape.toIntArray())) {
            "模型实际输入 shape=${inputShape.contentToString()} 与打包合同 ${modelInfo.inputShape} 不符"
        }

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
        require(outputTensor.dataType() == DataType.FLOAT32) { "${modelInfo.modelId} 输出必须为 FLOAT32" }
        val outputShape = outputTensor.shape()
        require(outputShape.contentEquals(modelInfo.outputShape.toIntArray())) {
            "模型实际输出 shape=${outputShape.contentToString()} 与打包合同 ${modelInfo.outputShape} 不符"
        }
        val count = outputTensor.shape().last()
        require(count == modelInfo.classes.size && count == modelInfo.classCount) {
            "模型输出类别数应为 ${modelInfo.classCount}，实际为 $count"
        }

        val output = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input.buffer, output)
        output.rewind()
        val logits = FloatArray(count) { output.float }
        val probabilities = softmax(logits)
        val candidates = modelInfo.classes.map { label ->
            RecognitionCandidate(
                label.classIndex,
                label.speciesKey,
                label.displayName,
                probabilities[label.classIndex].coerceIn(0f, 1f),
            )
        }.sortedByDescending { it.confidence }
        val top1 = candidates.first()
        val latencyMs = (System.nanoTime() - started) / 1_000_000

        InferenceTrace.report(
            modelVersion = modelInfo.modelId,
            modelSha256 = modelInfo.sha256,
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
            labels = modelInfo.classes.map { it.speciesKey to it.displayName },
            latencyMs = latencyMs,
            pipelineContext = pipelineContext,
        )

        Log.i(
            LOG_TAG,
            "model=${modelInfo.modelId} top1=${top1.classIndex}:${top1.speciesKey} confidence=${top1.confidence} latencyMs=$latencyMs",
        )
        Log.i(
            LOG_TAG,
            "top3=" + candidates.take(3).joinToString { "${it.classIndex}:${it.speciesKey}:${it.confidence}" },
        )
        RecognitionPrediction(
            modelVersion = modelInfo.modelId,
            modelSha256 = modelInfo.sha256,
            top1 = top1,
            candidates = candidates,
            latencyMs = latencyMs,
            modelInputBitmap = prepared.bitmap,
        )
    }

    /**
     * Production classifier preprocessing from the packaged tensor contract.
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
        const val MODEL_METADATA_FILE = "fish_classifier_v0_2.metadata.json"
        const val MODEL_CLASS_MAP_FILE = "class_map.json"
        const val MODEL_TENSOR_CONTRACT_FILE = "tensor_contract.json"
        const val MODEL_SNAPSHOT_FILE = "model_release_contract.json"
        const val PRODUCTION_RELEASE_MODEL_FILE = "fish_classifier_v0_2.tflite"
        const val PRODUCTION_RELEASE_TAG = "mobile-model-v0.2"
        private const val LOG_TAG = "FishRecognitionEngine"

        private const val PADDING_R = 124
        private const val PADDING_G = 116
        private const val PADDING_B = 104
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
