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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

class FishRecognitionEngine(private val context: Context) : AutoCloseable {
    private enum class InputLayout { NCHW, NHWC }

    private data class InputContract(
        val width: Int,
        val height: Int,
        val layout: InputLayout,
        val dataType: DataType,
    )

    private val modelBytes: ByteArray by lazy {
        context.assets.open(MODEL_FILE).use { input ->
            ByteArrayOutputStream().use { out -> input.copyTo(out); out.toByteArray() }
        }.also { bytes ->
            val actual = bytes.sha256()
            check(actual == MODEL_SHA256) { "鱼类识别模型校验失败：$actual" }
        }
    }

    private val interpreterLazy = lazy {
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(modelBytes).rewind()
        Interpreter(buffer, Interpreter.Options().apply { setNumThreads(4) })
    }
    private val interpreter get() = interpreterLazy.value

    suspend fun recognize(bitmap: Bitmap): RecognitionPrediction = withContext(Dispatchers.Default) {
        val started = System.nanoTime()
        val inputTensor = interpreter.getInputTensor(0)
        val contract = resolveInputContract(inputTensor.shape(), inputTensor.dataType())
        require(contract.dataType == DataType.FLOAT32) {
            "MODEL_M1_v0.2 移动端输入应为 FLOAT32，实际为 ${contract.dataType}"
        }

        val prepared = prepareWholeImageLetterbox(bitmap, contract.width, contract.height)
        val input = makeNormalizedInputBuffer(prepared, contract.layout)

        val outputTensor = interpreter.getOutputTensor(0)
        val count = outputTensor.shape().last()
        require(count == MODEL_LABELS.size) {
            "模型输出类别数应为 ${MODEL_LABELS.size}，实际为 $count"
        }
        require(outputTensor.dataType() == DataType.FLOAT32) {
            "MODEL_M1_v0.2 输出应为 FLOAT32 logits，实际为 ${outputTensor.dataType()}"
        }

        val output = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val logits = FloatArray(count) { output.float }
        val probabilities = softmax(logits)
        val candidates = MODEL_LABELS.mapIndexed { index, label ->
            RecognitionCandidate(index, label.first, label.second, probabilities[index].coerceIn(0f, 1f))
        }.sortedByDescending { it.confidence }
        val top1 = candidates.first()
        val latencyMs = (System.nanoTime() - started) / 1_000_000

        Log.i(
            LOG_TAG,
            "model=$MODEL_VERSION preprocess=$PREPROCESS_MODE layout=${contract.layout} " +
                "input=${contract.width}x${contract.height} Top1 index=${top1.classIndex} " +
                "species=${top1.speciesKey} confidence=${top1.confidence} latencyMs=$latencyMs",
        )
        RecognitionPrediction(MODEL_VERSION, MODEL_SHA256, top1, candidates, latencyMs)
    }

    /**
     * Keeps the complete source image visible to the model. Nothing is center-cropped.
     * The source is scaled proportionally to fit the model canvas and the remaining area is
     * padded with the ImageNet mean RGB. After ImageNet normalization that padding is ~0.
     */
    private fun prepareWholeImageLetterbox(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        require(bitmap.width > 0 && bitmap.height > 0) { "识别照片尺寸无效" }
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceIn(1, width)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceIn(1, height)
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
            val canvas = Canvas(target)
            canvas.drawColor(Color.rgb(PAD_RGB[0], PAD_RGB[1], PAD_RGB[2]))
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left, top, left + scaledWidth, top + scaledHeight),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun resolveInputContract(shape: IntArray, dataType: DataType): InputContract {
        require(shape.size == 4 && shape[0] == 1) {
            "模型输入应为 4D batch=1，实际=${shape.contentToString()}"
        }
        return when {
            shape[1] == 3 -> InputContract(width = shape[3], height = shape[2], layout = InputLayout.NCHW, dataType = dataType)
            shape[3] == 3 -> InputContract(width = shape[2], height = shape[1], layout = InputLayout.NHWC, dataType = dataType)
            else -> error("模型输入必须包含 3 个 RGB 通道，实际=${shape.contentToString()}")
        }.also {
            require(it.width == MODEL_IMAGE_SIZE && it.height == MODEL_IMAGE_SIZE) {
                "模型输入尺寸应为 ${MODEL_IMAGE_SIZE}x$MODEL_IMAGE_SIZE，实际=${it.width}x${it.height}"
            }
        }
    }

    private fun makeNormalizedInputBuffer(bitmap: Bitmap, layout: InputLayout): ByteBuffer {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val buffer = ByteBuffer.allocateDirect(pixels.size * 3 * 4).order(ByteOrder.nativeOrder())

        fun normalized(pixel: Int, channel: Int): Float {
            val raw = when (channel) {
                0 -> Color.red(pixel)
                1 -> Color.green(pixel)
                else -> Color.blue(pixel)
            } / 255f
            return (raw - IMAGENET_MEAN[channel]) / IMAGENET_STD[channel]
        }

        when (layout) {
            InputLayout.NCHW -> {
                for (channel in 0..2) {
                    pixels.forEach { pixel -> buffer.putFloat(normalized(pixel, channel)) }
                }
            }
            InputLayout.NHWC -> {
                pixels.forEach { pixel ->
                    for (channel in 0..2) buffer.putFloat(normalized(pixel, channel))
                }
            }
        }
        buffer.rewind()
        return buffer
    }

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
        const val MODEL_VERSION = "MODEL_M1_v0.2_LITERT_WHOLE_FISH"
        const val MODEL_SHA256 = "9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e"
        const val PREPROCESS_MODE = "whole_image_letterbox_imagenet"
        const val MODEL_IMAGE_SIZE = 224

        private const val LOG_TAG = "FishRecognitionEngine"
        private val PAD_RGB = intArrayOf(124, 116, 104)
        private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        val MODEL_LABELS = listOf(
            "grass_carp" to "草鱼",
            "bighead_carp" to "鳙鱼",
            "silver_carp" to "白鲢",
            "common_carp" to "鲤鱼",
            "crucian_carp" to "鲫鱼",
            "largemouth_bass" to "加州鲈",
            "snakehead" to "黑鱼",
            "yellow_catfish" to "黄骨鱼",
            "black_carp" to "青鱼",
        )
    }
}
