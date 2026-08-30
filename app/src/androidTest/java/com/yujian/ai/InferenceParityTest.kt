package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

@RunWith(AndroidJUnit4::class)
class InferenceParityTest {

    private enum class Layout { NCHW, NHWC }
    private enum class Channels { RGB, BGR }
    private enum class Norm { IMAGENET, ZERO_ONE }

    private data class Variant(
        val layout: Layout,
        val channels: Channels,
        val norm: Norm,
    ) {
        override fun toString(): String = "${layout}_${channels}_${norm}"
    }

    @Test
    fun diagnoseGoldenYellowCatfishTensorContract() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val bitmap = testContext.assets.open("golden_yellow_catfish_224.jpg").use { input ->
            requireNotNull(BitmapFactory.decodeStream(input)) { "golden image decode failed" }
        }
        require(bitmap.width == 224 && bitmap.height == 224) {
            "golden image must be 224x224, got ${bitmap.width}x${bitmap.height}"
        }

        val modelBytes = targetContext.assets.open("fish_classifier.tflite").use { input ->
            ByteArrayOutputStream().use { out -> input.copyTo(out); out.toByteArray() }
        }
        val model = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).apply {
            put(modelBytes)
            rewind()
        }
        val interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(1) })

        try {
            val input = interpreter.getInputTensor(0)
            val output = interpreter.getOutputTensor(0)
            require(input.shape().contentEquals(intArrayOf(1, 3, 224, 224))) {
                "unexpected input shape=${input.shape().contentToString()}"
            }
            require(output.shape().last() == 9) {
                "unexpected output shape=${output.shape().contentToString()}"
            }

            val variants = listOf(
                Variant(Layout.NCHW, Channels.RGB, Norm.IMAGENET),
                Variant(Layout.NHWC, Channels.RGB, Norm.IMAGENET),
                Variant(Layout.NCHW, Channels.BGR, Norm.IMAGENET),
                Variant(Layout.NHWC, Channels.BGR, Norm.IMAGENET),
                Variant(Layout.NCHW, Channels.RGB, Norm.ZERO_ONE),
                Variant(Layout.NHWC, Channels.RGB, Norm.ZERO_ONE),
            )
            val report = variants.joinToString(separator = " | ") { variant ->
                val logits = runVariant(interpreter, bitmap, variant)
                val probs = softmax(logits)
                val ranked = probs.indices.sortedByDescending { probs[it] }.take(3)
                val top3 = ranked.joinToString(",") { index ->
                    "$index:${LABELS[index]}:${"%.6f".format(java.util.Locale.US, probs[index])}"
                }
                "${variant}=>[$top3]"
            }

            // Intentional diagnostic failure: make the full variant matrix visible in CI output.
            // Once the correct packing is identified, production code and this test are tightened.
            fail(
                "PARITY_DIAGNOSTIC input=${input.shape().contentToString()} " +
                    "output=${output.shape().contentToString()} variants=$report",
            )
        } finally {
            interpreter.close()
            bitmap.recycle()
        }
    }

    private fun runVariant(interpreter: Interpreter, bitmap: Bitmap, variant: Variant): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pixelCount = pixels.size
        val values = FloatArray(pixelCount * 3)

        pixels.forEachIndexed { index, pixel ->
            val rgb = floatArrayOf(
                (pixel shr 16 and 0xFF).toFloat(),
                (pixel shr 8 and 0xFF).toFloat(),
                (pixel and 0xFF).toFloat(),
            )
            val ordered = if (variant.channels == Channels.RGB) rgb else floatArrayOf(rgb[2], rgb[1], rgb[0])
            val normalized = FloatArray(3) { channel ->
                when (variant.norm) {
                    Norm.IMAGENET -> (ordered[channel] / 255f - MEAN[channel]) / STD[channel]
                    Norm.ZERO_ONE -> ordered[channel] / 255f
                }
            }

            when (variant.layout) {
                Layout.NCHW -> {
                    values[index] = normalized[0]
                    values[pixelCount + index] = normalized[1]
                    values[pixelCount * 2 + index] = normalized[2]
                }
                Layout.NHWC -> {
                    val offset = index * 3
                    values[offset] = normalized[0]
                    values[offset + 1] = normalized[1]
                    values[offset + 2] = normalized[2]
                }
            }
        }

        val input = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        input.asFloatBuffer().put(values)
        input.rewind()
        val output = ByteBuffer.allocateDirect(9 * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        return FloatArray(9) { output.float }
    }

    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        val exps = values.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    companion object {
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        private val LABELS = listOf(
            "grass_carp",
            "bighead_carp",
            "silver_carp",
            "common_carp",
            "crucian_carp",
            "largemouth_bass",
            "snakehead",
            "yellow_catfish",
            "black_carp",
        )
    }
}
