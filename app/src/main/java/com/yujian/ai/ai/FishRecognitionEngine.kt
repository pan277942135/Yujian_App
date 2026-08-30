package com.yujian.ai.ai

import android.content.Context
import android.graphics.Bitmap
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

class FishRecognitionEngine(private val context: Context) : AutoCloseable {
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
        val inputShape = inputTensor.shape()
        require(inputShape.size == 4 && inputShape.last() == 3) { "模型输入需为 NHWC RGB" }
        val resized = prepareModelBitmap(bitmap, inputShape[2], inputShape[1])
        val input = makeInputBuffer(resized, inputTensor.dataType(), inputTensor.quantizationParams().scale, inputTensor.quantizationParams().zeroPoint)
        val outputTensor = interpreter.getOutputTensor(0)
        val count = outputTensor.shape().last()
        require(count == MODEL_LABELS.size) { "模型输出类别数应为 ${MODEL_LABELS.size}，实际为 $count" }
        val bytesPerValue = when (outputTensor.dataType()) {
            DataType.FLOAT32 -> 4
            DataType.UINT8, DataType.INT8 -> 1
            else -> error("暂不支持的模型输出类型：${outputTensor.dataType()}")
        }
        val output = ByteBuffer.allocateDirect(count * bytesPerValue).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val rawScores = when (outputTensor.dataType()) {
            DataType.FLOAT32 -> FloatArray(count) { output.float }
            DataType.UINT8 -> FloatArray(count) {
                val q = output.get().toInt() and 0xFF
                (q - outputTensor.quantizationParams().zeroPoint) * outputTensor.quantizationParams().scale
            }
            DataType.INT8 -> FloatArray(count) {
                (output.get().toInt() - outputTensor.quantizationParams().zeroPoint) * outputTensor.quantizationParams().scale
            }
            else -> error("暂不支持的模型输出类型")
        }
        val probabilities = normalizeScores(rawScores)
        val candidates = MODEL_LABELS.mapIndexed { index, label ->
            RecognitionCandidate(index, label.first, label.second, probabilities[index].coerceIn(0f, 1f))
        }.sortedByDescending { it.confidence }
        val top1 = candidates.first()
        Log.i(LOG_TAG, "Top1 index=${top1.classIndex} species=${top1.speciesKey} confidence=${top1.confidence}")
        RecognitionPrediction(MODEL_VERSION, MODEL_SHA256, top1, candidates, (System.nanoTime() - started) / 1_000_000)
    }

    private fun prepareModelBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val cropSize = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cropSize) / 2, (bitmap.height - cropSize) / 2, cropSize, cropSize)
        return if (cropped.width == width && cropped.height == height) cropped else Bitmap.createScaledBitmap(cropped, width, height, true)
    }

    private fun makeInputBuffer(bitmap: Bitmap, type: DataType, scale: Float, zeroPoint: Int): ByteBuffer {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val buffer = ByteBuffer.allocateDirect(pixels.size * 3 * if (type == DataType.FLOAT32) 4 else 1).order(ByteOrder.nativeOrder())
        pixels.forEach { pixel ->
            intArrayOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF).forEach { channel ->
                when (type) {
                    DataType.FLOAT32 -> buffer.putFloat(channel / 255f)
                    DataType.UINT8 -> buffer.put(channel.coerceIn(0, 255).toByte())
                    DataType.INT8 -> {
                        val safeScale = if (scale == 0f) 1f else scale
                        buffer.put(((channel / 255f) / safeScale + zeroPoint).toInt().coerceIn(-128, 127).toByte())
                    }
                    else -> error("暂不支持的模型输入类型：$type")
                }
            }
        }
        buffer.rewind(); return buffer
    }

    private fun normalizeScores(values: FloatArray): FloatArray {
        val sum = values.sum()
        if (values.all { it in 0f..1f } && sum in 0.9f..1.1f) return values
        val max = values.maxOrNull() ?: 0f
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val denominator = exps.sum().coerceAtLeast(0.0001f)
        return exps.map { it / denominator }.toFloatArray()
    }

    override fun close() { if (interpreterLazy.isInitialized()) interpreterLazy.value.close() }
    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    companion object {
        const val MODEL_FILE = "fish_classifier.tflite"
        const val MODEL_VERSION = "MODEL_M1_LEGACY_V1_1_2_TFLITE"
        const val MODEL_SHA256 = "5bb77f0bea96be2c6d2ace8a0fea36e8907bc9e4076beac05e0c82f44c345459"
        private const val LOG_TAG = "FishRecognitionEngine"
        val MODEL_LABELS = listOf(
            "tilapia" to "罗非鱼", "grass_carp" to "草鱼", "bass" to "鲈鱼", "silver_carp" to "鲢鱼",
            "common_carp" to "鲤鱼", "crucian_carp" to "鲫鱼", "catfish" to "鲶鱼", "bighead_carp" to "鳙鱼",
            "mandarin_fish" to "鳜鱼", "snakehead" to "黑鱼",
        )
    }
}
