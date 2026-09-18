package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.model.DemoData
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.IdentifyState
import com.yujian.ai.ui.identify.resolveIdentifyResultState
import com.yujian.ai.ui.identify.title
import com.yujian.ai.ui.theme.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var selectedKey by remember(prediction) { mutableStateOf(prediction.top1.speciesKey) }
    var selectedName by remember(prediction) { mutableStateOf(prediction.top1.speciesName) }
    var customName by remember(prediction) { mutableStateOf("") }
    var lengthText by remember(prediction) { mutableStateOf("") }
    var weightText by remember(prediction) { mutableStateOf("") }
    var locationText by remember(prediction) { mutableStateOf("") }
    val currentTime = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()) }
    val options = remember(prediction) {
        (prediction.candidates + DemoData.species.map { fish ->
            RecognitionCandidate(-1, fish.key, fish.name, 0f)
        }).distinctBy { it.speciesKey }.take(16)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("识别结果", color = DeepInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(state.title(), color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(270.dp)
                    .clip(RoundedCornerShape(28.dp)).background(SoftWater),
            ) {
                image?.let {
                    if (productionResult?.assessment?.primary != null) {
                        DetectorOverlayImage(
                            bitmap = it.bitmap,
                            detectorBox = productionResult.assessment.primary.box,
                            cropBox = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(it.bitmap.asImageBitmap(), "识别照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    when (state) {
                        IdentifyState.SUCCESS -> "看起来是"
                        IdentifyState.CONFIRM -> "可能是"
                        IdentifyState.UNKNOWN -> "请手动选择鱼种"
                        else -> "识别结果"
                    },
                    color = MutedInk,
                    fontSize = 13.sp,
                )
                Text(selectedName, color = DeepInk, fontSize = 31.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "置信度 ${(prediction.top1.confidence * 100).toInt().coerceIn(0, 100)}% · ${prediction.modelVersion}",
                    color = MutedInk,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("鱼获信息", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                InfoRow("长度", lengthText.ifBlank { "待测量" })
                InfoRow("重量", weightText.ifBlank { "待补充" })
                InfoRow("时间", currentTime.replace('T', ' ').substringBeforeLast(':'))
                InfoRow("地点", locationText.ifBlank { "待设置" })
            }
        }
        if (state == IdentifyState.CONFIRM || state == IdentifyState.UNKNOWN) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state == IdentifyState.CONFIRM) "请选择更接近的一项" else "手动选择鱼种",
                        color = DeepInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    options.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                OutlinedButton(
                                    onClick = { selectedKey = option.speciesKey; selectedName = option.speciesName },
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
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it.take(20) },
                        label = { Text("没有找到？输入鱼种") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    )
                    if (customName.isNotBlank()) {
                        TextButton(onClick = {
                            selectedName = customName.trim()
                            selectedKey = "user_${customName.trim().hashCode().toUInt().toString(16)}"
                        }) { Text("使用“${customName.trim()}”", color = WaterTeal) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lengthText, { lengthText = it.take(8) }, label = { Text("长度（cm，可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(weightText, { weightText = it.take(8) }, label = { Text("重量（kg，可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(locationText, { locationText = it.take(40) }, label = { Text("地点（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
            }
        }
        item {
            if (!saveError.isNullOrBlank()) {
                Text(saveError, color = Color(0xFFB24A3A), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
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
                        userNote = "identify_state=${state.name};length_cm=$lengthText;weight_kg=$weightText;location=$locationText",
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
                                    .put("box", JSONObject()
                                        .put("x1", it.box.x1.toDouble())
                                        .put("y1", it.box.y1.toDouble())
                                        .put("x2", it.box.x2.toDouble())
                                        .put("y2", it.box.y2.toDouble()))
                            },
                            classifierResult = JSONObject()
                                .put("state", state.name)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Text(if (saving) "正在保存…" else "保存这条鱼获", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("重新识别") }
                OutlinedButton(onClick = { if (!selectedKey.startsWith("user_")) onViewGuide(selectedKey) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("查看鱼鉴") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MutedInk, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Text(value, color = DeepInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
