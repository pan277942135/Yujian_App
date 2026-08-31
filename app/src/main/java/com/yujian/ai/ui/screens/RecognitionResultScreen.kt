package com.yujian.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ai.InferenceTrace
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.model.*
import com.yujian.ai.ui.components.TagChip
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.*
import java.util.UUID
import kotlin.math.roundToInt

private data class CorrectionOption(val key: String, val name: String)

@Composable
fun RecognitionResultScreen(
    image: SelectedImage?,
    prediction: RecognitionPrediction,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (CatchRecord, FeedbackDraft) -> Unit,
) {
    val context = LocalContext.current
    val debugReport = InferenceTrace.lastReport
    var selectedKey by remember(prediction) { mutableStateOf(prediction.top1.speciesKey) }
    var selectedName by remember(prediction) { mutableStateOf(prediction.top1.speciesName) }
    var showCorrection by remember(prediction) { mutableStateOf(prediction.lowConfidence) }
    var customName by remember { mutableStateOf("") }
    val confidence = (prediction.top1.confidence * 100).roundToInt().coerceIn(0, 100)
    val guideFish = DemoData.species.firstOrNull { it.key == selectedKey }
    val corrected = selectedName != prediction.top1.speciesName
    val correctionOptions = remember(prediction) {
        (prediction.candidates.map { CorrectionOption(it.speciesKey, it.speciesName) } +
            DemoData.species.map { CorrectionOption(it.key, it.name) })
            .distinctBy { it.name }
    }

    LazyColumn(Modifier.fillMaxSize().background(WarmBackground), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { YujianTopBar(title = "识别结果", onBack = onBack) }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(270.dp).clip(RoundedCornerShape(28.dp)).background(SoftWater), contentAlignment = Alignment.Center) {
                image?.let { Image(it.bitmap.asImageBitmap(), "已识别鱼获照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .9f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(WaterTeal, CircleShape))
                        Text("本机 AI 识别完成", color = WaterTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(if (prediction.lowConfidence) "有几个结果比较接近" else "看起来是", color = MutedInk, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(selectedName, color = DeepInk, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.padding(start = 14.dp).background(SoftWater, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("$confidence% 把握", color = WaterTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (corrected) {
                    Text("AI 原始结果：${prediction.top1.speciesName} · $confidence%", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    guideFish?.description ?: "当前模型将这条鱼识别为“${prediction.top1.speciesName}”。保存前你可以确认或纠正鱼种，原始 AI 结果会被完整保留。",
                    color = DeepInk, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 16.dp),
                )
                Text("模型 ${prediction.modelVersion} · ${prediction.latencyMs} ms", color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (debugReport.isNotBlank()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("YuJian inference debug", debugReport))
                            Toast.makeText(context, "识别调试信息已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("复制识别调试信息", color = WaterTeal)
                    }
                    Text(
                        "包含预处理、tensor SHA、完整 9 维 logits / probability，复制后直接发给我即可。",
                        color = MutedInk,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (prediction.lowConfidence) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(vertical = 16.dp)) {
                    Text("你也可以选一个候选", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(prediction.candidates.take(3), key = { it.classIndex }) { candidate ->
                            Box(Modifier.clickable { selectedKey = candidate.speciesKey; selectedName = candidate.speciesName }) {
                                TagChip("${candidate.speciesName} ${(candidate.confidence * 100).roundToInt()}%", candidate.speciesName == selectedName)
                            }
                        }
                    }
                }
            }
        }
        if (showCorrection) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(24.dp)).padding(vertical = 16.dp)) {
                    Text("纠正鱼种", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(correctionOptions, key = { it.name }) { option ->
                            Box(Modifier.clickable { selectedKey = option.key; selectedName = option.name; customName = "" }) {
                                TagChip(option.name, option.name == selectedName)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it.take(20) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        label = { Text("没找到？输入鱼种名称") },
                        shape = RoundedCornerShape(18.dp),
                    )
                    if (customName.isNotBlank()) {
                        TextButton(onClick = { selectedName = customName.trim(); selectedKey = "user_${customName.trim().hashCode().toUInt().toString(16)}" }) {
                            Text("使用“${customName.trim()}”", color = WaterTeal)
                        }
                    }
                    Text("你的纠正会进入待审核反馈池，不会直接当作训练真值。", color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item {
            Button(
                onClick = {
                    val record = DemoData.catch.copy(
                        id = "catch_${UUID.randomUUID()}", speciesKey = selectedKey, speciesName = selectedName,
                        confidence = confidence, note = "由真实识鱼结果保存。${DemoData.catch.note}",
                        modelVersion = prediction.modelVersion, aiSpeciesKey = prediction.top1.speciesKey,
                        aiSpeciesName = prediction.top1.speciesName, aiConfidence = confidence, userCorrected = corrected,
                    )
                    val customUnknown = selectedKey.startsWith("user_")
                    val draft = FeedbackDraft(
                        sourceEventId = "APP_${UUID.randomUUID()}",
                        feedbackType = when { customUnknown -> "new_species_candidate"; corrected -> "corrected"; else -> "confirmed" },
                        modelVersion = prediction.modelVersion,
                        predictedSpecies = prediction.top1.speciesName,
                        confidence = prediction.top1.confidence,
                        correctedSpecies = selectedName.takeIf { corrected || customUnknown },
                        userNote = "model_sha256=${prediction.modelSha256};source=${image?.source ?: "unknown"}",
                    )
                    onSave(record, draft)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp), shape = RoundedCornerShape(27.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Icon(Icons.Rounded.Add, null); Text("保存这次鱼获", modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, null, tint = WaterTeal); Text("重新识别", color = WaterTeal, modifier = Modifier.padding(start = 5.dp)) }
                TextButton(onClick = { showCorrection = !showCorrection }) { Text(if (showCorrection) "收起纠正" else "这不是我要的鱼", color = MutedInk) }
            }
        }
    }
}
