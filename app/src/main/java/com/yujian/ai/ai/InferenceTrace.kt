package com.yujian.ai.ai

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

/**
 * Debug trace helper for Android/Python inference parity checks.
 *
 * The latest successful inference is also kept as one copyable text block so the
 * result screen can put it on the clipboard without requiring adb/logcat access.
 */
object InferenceTrace {
    private const val TAG = "YujianInference"

    @Volatile
    var lastReport: String = ""
        private set

    fun bitmap(label: String, bitmap: Bitmap) {
        Log.i(TAG, """
            $label
            width=${bitmap.width}
            height=${bitmap.height}
            config=${bitmap.config}
        """.trimIndent())
    }

    fun tensorHead(values: FloatArray, count: Int = 32) {
        val text = values.take(count).joinToString(",") { formatFloat(it) }
        Log.i(TAG, "tensor_head=$text")
    }

    fun model(sha: String, size: Int) {
        Log.i(TAG, "model_sha=$sha model_bytes=$size")
    }

    fun report(
        modelVersion: String,
        modelSha256: String,
        sourceBitmap: Bitmap,
        preparedBitmap: Bitmap,
        inputShape: IntArray,
        layout: String,
        scale: Float,
        drawWidth: Float,
        drawHeight: Float,
        padLeft: Float,
        padTop: Float,
        paddingRgb: IntArray,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
        inputValues: FloatArray,
        logits: FloatArray,
        probabilities: FloatArray,
        labels: List<Pair<String, String>>,
        latencyMs: Long,
    ) {
        val tensorSha = floatArraySha256Le(inputValues)
        val preparedRgbSha = bitmapRgbSha256(preparedBitmap)
        val tensorHead = inputValues.take(24).joinToString(",") { formatFloat(it) }
        val logitsText = logits.indices.joinToString(",") { index ->
            "$index:${formatFloat(logits[index])}"
        }
        val probabilitiesText = probabilities.indices.joinToString(",") { index ->
            val label = labels.getOrNull(index)?.first ?: "class_$index"
            "$index:$label:${formatFloat(probabilities[index])}"
        }
        val top3 = probabilities.indices
            .sortedByDescending { probabilities[it] }
            .take(3)
            .joinToString(",") { index ->
                val label = labels.getOrNull(index)?.first ?: "class_$index"
                "$index:$label:${formatFloat(probabilities[index])}"
            }

        val report = buildString {
            appendLine("=== YUJIAN_INFERENCE_REPORT_BEGIN ===")
            appendLine("model_version=$modelVersion")
            appendLine("model_sha256=$modelSha256")
            appendLine("source_size=${sourceBitmap.width}x${sourceBitmap.height}")
            appendLine("model_input_size=${preparedBitmap.width}x${preparedBitmap.height}")
            appendLine("input_shape=${inputShape.contentToString()}")
            appendLine("input_dtype=FLOAT32")
            appendLine("input_layout=$layout")
            appendLine("color_order=RGB")
            appendLine("preprocess=WHOLE_IMAGE_LETTERBOX")
            appendLine("interpolation=ANDROID_CANVAS_FILTER_BITMAP")
            appendLine("letterbox_scale=${formatFloat(scale)}")
            appendLine("letterbox_draw_size=${formatFloat(drawWidth)}x${formatFloat(drawHeight)}")
            appendLine("letterbox_pad_left=${formatFloat(padLeft)}")
            appendLine("letterbox_pad_top=${formatFloat(padTop)}")
            appendLine("padding_rgb=${paddingRgb.contentToString()}")
            appendLine("normalization_mean=${normalizationMean.contentToString()}")
            appendLine("normalization_std=${normalizationStd.contentToString()}")
            appendLine("prepared_rgb_sha256=$preparedRgbSha")
            appendLine("tensor_float_count=${inputValues.size}")
            appendLine("tensor_bytes=${inputValues.size * 4}")
            appendLine("tensor_sha256_f32le=$tensorSha")
            appendLine("tensor_head_24=[$tensorHead]")
            appendLine("logits=[$logitsText]")
            appendLine("probabilities=[$probabilitiesText]")
            appendLine("top3=[$top3]")
            appendLine("latency_ms=$latencyMs")
            append("=== YUJIAN_INFERENCE_REPORT_END ===")
        }
        lastReport = report
        Log.i(TAG, report)
    }

    private fun floatArraySha256Le(values: FloatArray): String {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return sha256(buffer.array())
    }

    private fun bitmapRgbSha256(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = ByteArray(pixels.size * 3)
        var offset = 0
        pixels.forEach { pixel ->
            rgb[offset++] = ((pixel shr 16) and 0xFF).toByte()
            rgb[offset++] = ((pixel shr 8) and 0xFF).toByte()
            rgb[offset++] = (pixel and 0xFF).toByte()
        }
        return sha256(rgb)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.8f", value)
}
