package com.yujian.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.yujian.ai.ai.DetectorInvocationSnapshot
import com.yujian.ai.ai.FishDetectorEngine
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.media.RecognitionImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/** Debug-only manual probe. It invokes the production detector and never uploads results. */
class ObjectnessProbeActivity : ComponentActivity() {
    private lateinit var resultView: TextView
    private lateinit var pickButton: Button
    private lateinit var x1Field: EditText
    private lateinit var y1Field: EditText
    private lateinit var x2Field: EditText
    private lateinit var y2Field: EditText

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runProbe(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        content.addView(TextView(this).apply {
            text = "单图 Android Detector Probe"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "选择 04-181200.jpg。输入已确认鱼框的归一化 xyxy 坐标；推理直接调用生产 FishDetectorEngine.detect。结果只显示在本机，不会上传。"
            textSize = 15f
            setPadding(0, 12, 0, 16)
        })

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        x1Field = coordinateField("x1")
        y1Field = coordinateField("y1")
        x2Field = coordinateField("x2")
        y2Field = coordinateField("y2")
        listOf(x1Field, y1Field, x2Field, y2Field).forEach { field ->
            fields.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(fields)

        pickButton = Button(this).apply {
            text = "选择 Case A 原图并运行"
            setOnClickListener {
                runCatching { imagePicker.launch("image/*") }
                    .onFailure { resultView.text = "无法打开图片选择器：${it.message}" }
            }
        }
        content.addView(pickButton)
        content.addView(Button(this).apply {
            text = "复制结果"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Android detector probe", resultView.text))
                text = "已复制"
            }
        })
        resultView = TextView(this).apply {
            text = "等待选择图片。"
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 16, 0, 0)
        }
        content.addView(resultView)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun coordinateField(label: String): EditText = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        maxLines = 1
        textSize = 14f
        setPadding(6, 8, 6, 8)
    }

    private fun runProbe(uri: Uri) {
        val groundTruth = readGroundTruth() ?: return
        pickButton.isEnabled = false
        resultView.text = "正在使用 Android 生产图片归一化与 detector.detect()…"
        lifecycleScope.launch {
            try {
                val originalSha = withContext(Dispatchers.IO) { sha256(uri) }
                val selected = RecognitionImageStore.normalize(this@ObjectnessProbeActivity, uri, "gallery")
                var originalInvocation: DetectorInvocationSnapshot? = null
                val engine = FishDetectorEngine(applicationContext)
                engine.setDiagnosticObserver { invocation ->
                    if (invocation.orientationAttempt == "ORIGINAL") originalInvocation = invocation
                }
                try {
                    engine.detect(selected.bitmap)
                } finally {
                    engine.close()
                    if (!selected.bitmap.isRecycled) selected.bitmap.recycle()
                }
                val invocation = originalInvocation
                    ?: error("Production detector did not emit an ORIGINAL invocation snapshot")
                val candidate = withContext(Dispatchers.Default) { bestCandidate(invocation, groundTruth) }
                resultView.text = buildString {
                    appendLine("image_sha256=$originalSha")
                    appendLine("onnx_sha256=${invocation.onnxSha256}")
                    appendLine("input_tensor_sha256=${invocation.inputTensorSha256}")
                    appendLine("input_tensor_shape=${invocation.inputTensorShape}")
                    appendLine("input_tensor_dtype=${invocation.inputTensorType}")
                    appendLine("original_candidate_row_index_zero_based=${candidate.rowIndex}")
                    appendLine("candidate_iou_to_confirmed_gt=${fmt(candidate.iou)}")
                    appendLine("candidate_raw_output=${candidate.rawRow.joinToString(prefix = "[", postfix = "]") { fmt(it) }}")
                    appendLine("objectness=${fmt(candidate.rawRow[4])}")
                    appendLine("fish_probability=${fmt(candidate.rawRow[5])}")
                    appendLine("confidence=${fmt(candidate.rawRow[4] * candidate.rawRow[5])}")
                }
            } catch (error: Exception) {
                resultView.text = "PROBE_ERROR=${error.javaClass.simpleName}: ${error.message}"
            } finally {
                pickButton.isEnabled = true
            }
        }
    }

    private fun readGroundTruth(): NormalizedFishBox? {
        val values = listOf(x1Field, y1Field, x2Field, y2Field).map { it.text.toString().toFloatOrNull() }
        if (values.any { it == null }) {
            resultView.text = "请填写已确认鱼框的归一化 x1、y1、x2、y2。"
            return null
        }
        val x1 = requireNotNull(values[0])
        val y1 = requireNotNull(values[1])
        val x2 = requireNotNull(values[2])
        val y2 = requireNotNull(values[3])
        if (listOf(x1, y1, x2, y2).any { !it.isFinite() || it !in 0f..1f } || x1 >= x2 || y1 >= y2) {
            resultView.text = "Ground Truth 坐标必须满足 0≤x1<x2≤1、0≤y1<y2≤1。"
            return null
        }
        return NormalizedFishBox(x1, y1, x2, y2)
    }

    private data class Candidate(
        val rowIndex: Int,
        val rawRow: FloatArray,
        val iou: Float,
    )

    private fun bestCandidate(
        invocation: DetectorInvocationSnapshot,
        groundTruth: NormalizedFishBox,
    ): Candidate {
        return invocation.rawRows.mapIndexedNotNull { index, row ->
            val decoded = FishDetectorEngine.decodeAndNms(
                rows = arrayOf(row),
                scale = invocation.scale,
                sourceWidth = invocation.sourceWidth,
                sourceHeight = invocation.sourceHeight,
                minConfidence = 0f,
                nmsIou = 1f,
            ).singleOrNull() ?: return@mapIndexedNotNull null
            Candidate(index, row.copyOf(), FishDetectorEngine.iou(decoded.box, groundTruth))
        }.maxByOrNull { it.iou } ?: error("No finite raw candidate could be decoded")
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(bytes)
                if (read < 0) break
                digest.update(bytes, 0, read)
            }
        } ?: error("Cannot read selected image URI")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.9f", value)
}
