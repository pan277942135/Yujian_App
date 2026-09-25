package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.FishRecognitionEngine
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.IdentifyState
import com.yujian.ai.ui.identify.resolveIdentifyResultState
import com.yujian.ai.ui.recognition.RecognitionPresentation
import com.yujian.ai.ui.recognition.RecognitionPhotoBackdrop
import com.yujian.ai.ui.recognition.RecognitionResultLevel
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun RecognitionResultScreen(
    image: SelectedImage?,
    prediction: RecognitionPrediction,
    productionResult: ProductionRecognitionResult? = null,
    subjectResult: FishSubjectResult = FishSubjectResult(SubjectStatus.IDLE),
    subjectModelState: SubjectModelState = SubjectModelState(),
    onPrepareSubjectModel: () -> Unit = {},
    onGenerateSubject: () -> Unit = {},
    onBack: () -> Unit,
    onRetry: () -> Unit,
    saving: Boolean = false,
    saveError: String? = null,
    onSave: (CatchSaveDraft, FeedbackDraft) -> Unit,
    onViewGuide: (String) -> Unit,
) {
    val state = resolveIdentifyResultState(
        productionResult?.status ?: FishInputStatus.READY,
        prediction,
    )
    val level = productionResult?.let(RecognitionPresentation::resultLevel) ?: when (state) {
        IdentifyState.SUCCESS -> RecognitionResultLevel.HIGH
        IdentifyState.CONFIRM -> RecognitionResultLevel.MEDIUM
        else -> RecognitionResultLevel.LOW
    }
    var selectedKey by remember(prediction) {
        mutableStateOf(if (level == RecognitionResultLevel.LOW) "" else prediction.top1.speciesKey)
    }
    var selectedName by remember(prediction) {
        mutableStateOf(if (level == RecognitionResultLevel.LOW) "" else prediction.top1.speciesName)
    }
    var customName by remember(prediction) { mutableStateOf("") }
    var lengthText by remember(prediction) { mutableStateOf("") }
    var weightText by remember(prediction) { mutableStateOf("") }
    var locationText by remember(prediction) { mutableStateOf("") }
    var pickerVisible by remember(prediction) { mutableStateOf(false) }
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }
    val modelCandidates = remember(prediction) {
        prediction.candidates.distinctBy { it.speciesKey }.take(3)
    }
    val allSpecies = remember {
        FishRecognitionEngine.MODEL_LABELS.mapIndexed { index, label ->
            RecognitionCandidate(index, label.first, label.second, 0f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        image?.let { RecognitionPhotoBackdrop(it.bitmap, Modifier.fillMaxSize()) }
        LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DeepInk)
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("识别结果", color = DeepInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (level) {
                        RecognitionResultLevel.HIGH -> "看起来是"
                        RecognitionResultLevel.MEDIUM -> "帮我确认一下，这条鱼更像哪一种？"
                        RecognitionResultLevel.LOW -> "无法确认是什么鱼"
                    },
                    color = MutedInk,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                }
            }
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(300.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SoftWater),
            ) {
                image?.let {
                    ResultPhoto(bitmap = it.bitmap, modifier = Modifier.fillMaxSize())
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    when (level) {
                        RecognitionResultLevel.HIGH, RecognitionResultLevel.MEDIUM -> selectedName
                        RecognitionResultLevel.LOW -> "选择鱼种"
                    },
                    color = DeepInk,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                )
                InfoRow("时间", formatCaptureTime(currentTime))
            }
        }

        if (level == RecognitionResultLevel.MEDIUM) {
            item {
                CandidateButtons(
                    title = "请选择更接近的一项",
                    candidates = modelCandidates,
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it.speciesKey; selectedName = it.speciesName },
                )
            }
        }

        if (level == RecognitionResultLevel.LOW) {
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("选择鱼种", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = { pickerVisible = !pickerVisible },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(if (pickerVisible) "收起 16 类鱼种" else "打开鱼种选择")
                    }
                    if (pickerVisible) {
                        CandidateButtons(
                            title = "",
                            candidates = allSpecies,
                            selectedKey = selectedKey,
                            onSelect = { selectedKey = it.speciesKey; selectedName = it.speciesName },
                        )
                    }
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it.take(20) },
                        label = { Text("其他鱼种（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    )
                    if (customName.isNotBlank()) {
                        TextButton(onClick = {
                            selectedName = customName.trim()
                            selectedKey = "user_${customName.trim().hashCode().toUInt().toString(16)}"
                        }) {
                            Text("使用“${customName.trim()}”", color = WaterTeal)
                        }
                    }
                }
            }
        }

        item {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(CardWhite.copy(alpha = 0.52f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("补充鱼获信息", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (lengthText.isNotBlank()) InfoRow("长度", lengthText)
                if (weightText.isNotBlank()) InfoRow("重量", weightText)
                if (locationText.isNotBlank()) InfoRow("地点", locationText)
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.take(8) },
                    label = { Text("长度（cm，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.take(8) },
                    label = { Text("重量（kg，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it.take(40) },
                    label = { Text("地点（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }

        item {
            if (!saveError.isNullOrBlank()) {
                Text(
                    saveError,
                    color = Color(0xFFB24A3A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Button(
                onClick = {
                    val customUnknown = selectedKey.startsWith("user_")
                    val corrected = selectedName != prediction.top1.speciesName
                    val feedback = FeedbackDraft(
                        sourceEventId = "APP_${java.util.UUID.randomUUID()}",
                        imageId = image?.imageId,
                        feedbackType = when {
                            customUnknown -> "new_species_candidate"
                            corrected -> "corrected"
                            else -> "confirmed"
                        },
                        modelVersion = prediction.modelVersion,
                        predictedSpecies = prediction.top1.speciesName,
                        confidence = prediction.top1.confidence,
                        correctedSpecies = selectedName.takeIf { corrected || customUnknown },
                        userNote = "recognition_level=${level.name};length_cm=$lengthText;weight_kg=$weightText;location=$locationText",
                    )
                    onSave(
                        CatchSaveDraft(
                            speciesId = selectedKey,
                            speciesName = selectedName,
                            confidence = prediction.top1.confidence,
                            modelVersion = prediction.modelVersion,
                            detectorResult = productionResult?.assessment?.primary?.let {
                                JSONObject()
                                    .put("confidence", it.confidence.toDouble())
                                    .put(
                                        "box",
                                        JSONObject()
                                            .put("x1", it.box.x1.toDouble())
                                            .put("y1", it.box.y1.toDouble())
                                            .put("x2", it.box.x2.toDouble())
                                            .put("y2", it.box.y2.toDouble()),
                                    )
                            },
                            classifierResult = JSONObject()
                                .put("state", level.name)
                                .put("model_version", prediction.modelVersion)
                                .put("prediction_species", prediction.top1.speciesKey)
                                .put("confidence", prediction.top1.confidence.toDouble())
                                .put("user_selected_species", selectedKey)
                                .put("length_cm", lengthText.toDoubleOrNull() ?: JSONObject.NULL)
                                .put("weight_kg", weightText.toDoubleOrNull() ?: JSONObject.NULL)
                                .put("location", locationText.ifBlank { JSONObject.NULL })
                                .put("created_at", currentTime)
                                .put("photo_url", image?.filePath ?: ""),
                        ),
                        feedback,
                    )
                },
                enabled = !saving && selectedName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) {
                Text(
                    if (saving) "正在保存…" else "保存这条鱼获",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) { Text("重新识别") }
                OutlinedButton(
                    onClick = {
                        if (!selectedKey.startsWith("user_")) onViewGuide(selectedKey)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) { Text("查看鱼鉴") }
            }
        }
    }
    }
}

@Composable
private fun CandidateButtons(
    title: String,
    candidates: List<RecognitionCandidate>,
    selectedKey: String,
    onSelect: (RecognitionCandidate) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        candidates.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    OutlinedButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedKey == option.speciesKey) SoftWater else Color.Transparent,
                            contentColor = if (selectedKey == option.speciesKey) WaterTeal else DeepInk,
                        ),
                    ) { Text(option.speciesName, maxLines = 1) }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ResultPhoto(bitmap: Bitmap, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scale = min(widthPx / bitmap.width.toFloat(), heightPx / bitmap.height.toFloat())
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (widthPx - drawnWidth) / 2f
        val top = (heightPx - drawnHeight) / 2f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(18.dp).graphicsLayer {
                alpha = 0.28f
            },
            contentScale = ContentScale.Crop,
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "鱼获原图",
            modifier = Modifier
                .offset(with(density) { left.toDp() }, with(density) { top.toDp() })
                .size(with(density) { drawnWidth.toDp() }, with(density) { drawnHeight.toDp() }),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private fun formatCaptureTime(value: String): String =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .parse(value)
            ?.let { SimpleDateFormat("今天 HH:mm", Locale.CHINA).format(it) }
    }.getOrNull() ?: value.replace('T', ' ').substringBeforeLast(':')

private fun IdentifyState.titleForResult(): String = when (this) {
    IdentifyState.NO_FISH -> "没有找到鱼获主体"
    IdentifyState.TOO_FAR -> "鱼距离太远"
    else -> "识别完成"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MutedInk, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Text(value, color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
