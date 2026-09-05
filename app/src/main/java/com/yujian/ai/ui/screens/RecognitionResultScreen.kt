package com.yujian.ai.ui.screens

import android.graphics.Bitmap
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
import com.yujian.ai.ai.FishDetectionQualityGate
import com.yujian.ai.ai.FishQualityLevel
import com.yujian.ai.ai.InferenceTrace
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.BuildConfig
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.model.*
import com.yujian.ai.ui.components.TagChip
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

private data class CorrectionOption(val key: String, val name: String)

@Composable
fun RecognitionResultScreen(
    image: SelectedImage?,
    prediction: RecognitionPrediction,
    productionResult: ProductionRecognitionResult? = null,
    subjectResult: FishSubjectResult = FishSubjectResult(SubjectStatus.IDLE),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    saving: Boolean = false,
    saveError: String? = null,
    onSave: (CatchSaveDraft, FeedbackDraft) -> Unit,
    onViewGuide: (String) -> Unit,
) {
    val context = LocalContext.current
    val debugReport = InferenceTrace.lastReport
    val assessment = productionResult?.assessment
    val qualityLevel = assessment?.qualityLevel ?: FishQualityLevel.GOOD
    val cropPixels = productionResult?.cropPixels
    val cropPixelsKey = cropPixels?.contentToString()
    val cropPreview = remember(image?.bitmap, cropPixelsKey) {
        val source = image?.bitmap
        if (source == null || cropPixels == null || cropPixels.size < 4) {
            null
        } else {
            val left = cropPixels[0].coerceIn(0, source.width - 1)
            val top = cropPixels[1].coerceIn(0, source.height - 1)
            val right = cropPixels[2].coerceIn(left + 1, source.width)
            val bottom = cropPixels[3].coerceIn(top + 1, source.height)
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }
    }
    DisposableEffect(cropPreview) {
        onDispose {
            if (cropPreview != null && cropPreview !== image?.bitmap && !cropPreview.isRecycled) cropPreview.recycle()
        }
    }
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(28.dp)).background(SoftWater),
                    contentAlignment = Alignment.Center,
                ) {
                    image?.let {
                        DetectorOverlayImage(
                            bitmap = it.bitmap,
                            detectorBox = assessment?.primary?.box,
                            cropBox = assessment?.cropBox,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.White.copy(alpha = .92f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(qualityColor(qualityLevel), CircleShape))
                            Text(
                                "本机 AI 识别完成 · ${qualityLevel.name}",
                                color = qualityColor(qualityLevel),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                }
                if (assessment?.primary != null) {
                    DetectorOverlayLegend(
                        showCrop = assessment.cropBox != null,
                        modifier = Modifier.padding(start = 4.dp, top = 7.dp),
                    )
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
                assessment?.let { gate ->
                    Text(
                        "Quality Gate ${gate.qualityLevel.name} · ${gate.qualityReason}",
                        color = MutedInk,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (assessment?.qualityLevel == FishQualityLevel.WARNING) {
            item {
                QualityWarningCard(assessment.qualityReason)
            }
        }
        if (assessment != null && image != null) {
            item {
                DetectorMetadataCard(
                    assessment = assessment,
                    cropPixels = cropPixels,
                    sourceWidth = image.bitmap.width,
                    sourceHeight = image.bitmap.height,
                )
            }
        }
        if (cropPreview != null || prediction.modelInputBitmap != null) {
            item {
                CropPreviewCard(cropPreview = cropPreview, modelInput = prediction.modelInputBitmap)
            }
        }
        if (subjectResult.status != SubjectStatus.IDLE && subjectResult.status != SubjectStatus.FAILED) {
            item { SubjectPreviewContainer(subjectResult) }
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
                        "包含 quality gate、原图 bbox、crop size、预处理、tensor SHA 和完整 logits / probability，复制后直接发给我即可。",
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
            if (!saveError.isNullOrBlank()) {
                Text(
                    saveError,
                    color = Color(0xFFB24A3A),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Color(0xFFFFF4E4), RoundedCornerShape(16.dp)).padding(13.dp),
                )
            }
            Button(
                onClick = {
                    val customUnknown = selectedKey.startsWith("user_")
                    val draft = FeedbackDraft(
                        sourceEventId = "APP_${java.util.UUID.randomUUID()}",
                        imageId = image?.imageId,
                        feedbackType = when { customUnknown -> "new_species_candidate"; corrected -> "corrected"; else -> "confirmed" },
                        modelVersion = prediction.modelVersion,
                        predictedSpecies = prediction.top1.speciesName,
                        confidence = prediction.top1.confidence,
                        correctedSpecies = selectedName.takeIf { corrected || customUnknown },
                        userNote = "model_sha256=${prediction.modelSha256};source=${image?.source ?: "unknown"}",
                    )
                    onSave(
                        CatchSaveDraft(
                            speciesId = selectedKey,
                            speciesName = selectedName,
                            confidence = prediction.top1.confidence,
                            modelVersion = prediction.modelVersion,
                            detectorResult = productionResult?.let { result ->
                                result.assessment.primary?.let { primary ->
                                    org.json.JSONObject()
                                        .put("detector_version", result.detectorRun.modelVersion)
                                        .put("confidence", primary.confidence.toDouble())
                                        .put("quality", result.assessment.qualityLevel.name)
                                        .put("quality_reason", result.assessment.qualityReason)
                                }
                            },
                            classifierResult = org.json.JSONObject()
                                .put("model_version", prediction.modelVersion)
                                .put("prediction_species", prediction.top1.speciesKey)
                                .put("confidence", prediction.top1.confidence.toDouble())
                                .put("user_selected_species", selectedKey),
                        ),
                        draft,
                    )
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp), shape = RoundedCornerShape(27.dp), colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
            ) { Icon(Icons.Rounded.Add, null); Text(if (saving) "正在保存…" else "保存这次鱼获", modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = { onViewGuide(selectedKey) },
                enabled = !saving && !selectedKey.startsWith("user_"),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("查看鱼鉴", color = WaterTeal) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, null, tint = WaterTeal); Text("重新识别", color = WaterTeal, modifier = Modifier.padding(start = 5.dp)) }
                TextButton(onClick = { showCorrection = !showCorrection }) { Text(if (showCorrection) "收起纠正" else "这不是我要的鱼", color = MutedInk) }
            }
        }
    }
}

@Composable
private fun QualityWarningCard(reason: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Color(0xFFFFF4E4), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("画面可用，但需要留意", color = Color(0xFF9A5A16), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            when (reason) {
                "primary_fish_bbox_touches_image_edge" -> "鱼体有一部分贴近或超出画面边缘，已保留这张照片并继续识别。保存前请结合原图确认鱼种。"
                "weak_fish_detection_only" -> "检测置信度偏低，但仍保留候选并继续识别。请结合原图确认结果。"
                else -> "检测存在轻微质量风险，但没有阻断本次识别。保存前请结合原图确认鱼种。"
            },
            color = Color(0xFF7D5A32),
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun DetectorMetadataCard(
    assessment: com.yujian.ai.ai.FishInputAssessment,
    cropPixels: IntArray?,
    sourceWidth: Int,
    sourceHeight: Int,
) {
    val primary = assessment.primary
    val bbox = primary?.box?.normalized()
    val cropSize = cropPixels?.takeIf { it.size >= 4 }?.let { "${it[2] - it[0]}×${it[3] - it[1]} px" } ?: "未生成"
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Detector Overlay", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("原图 ${sourceWidth}×${sourceHeight} · Quality Gate ${assessment.qualityLevel.name}", color = MutedInk, fontSize = 11.sp)
        primary?.let {
            Text(
                "原图 bbox [${format3(bbox?.x1 ?: 0f)}, ${format3(bbox?.y1 ?: 0f)}, ${format3(bbox?.x2 ?: 0f)}, ${format3(bbox?.y2 ?: 0f)}]",
                color = DeepInk,
                fontSize = 12.sp,
            )
            Text(
                "confidence ${format3(it.confidence)} · bbox_area_ratio ${format3(assessment.bboxAreaRatio ?: 0f)}",
                color = MutedInk,
                fontSize = 11.sp,
            )
        }
        Text(
            "crop size $cropSize · expand ratio ${format2(FishDetectionQualityGate.CROP_EXPAND_RATIO)}",
            color = MutedInk,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CropPreviewCard(
    cropPreview: Bitmap?,
    modelInput: Bitmap?,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Crop Preview", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("实际送入 MODEL_M1_v0.2：detector crop → FISH_CROP_LETTERBOX", color = MutedInk, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cropPreview?.let {
                PreviewTile(
                    bitmap = it,
                    label = "detector crop · ${it.width}×${it.height}",
                    modifier = Modifier.weight(1f),
                )
            }
            modelInput?.let {
                PreviewTile(
                    bitmap = it,
                    label = "MODEL_M1 input · ${it.width}×${it.height}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubjectPreviewContainer(result: FishSubjectResult) {
    val bitmap = remember(result.bitmapPath) {
        result.bitmapPath?.let { android.graphics.BitmapFactory.decodeFile(it) }
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(CardWhite, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Fish Subject Preview", color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        when (result.status) {
            SubjectStatus.PROCESSING -> Text("正在整理鱼体", color = MutedInk, fontSize = 12.sp)
            SubjectStatus.READY -> {
                Text("AI 提取出的鱼体主体", color = MutedInk, fontSize = 11.sp)
                Box(
                    Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(SoftWater),
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), "透明鱼体主体", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                if (BuildConfig.DEBUG) {
                    Text(
                        "subject_status=${result.status} · processing_ms=${result.processingMs} · mask_area_ratio=${format3(result.maskAreaRatio)} · quality=${result.quality}",
                        color = MutedInk, fontSize = 10.sp,
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun PreviewTile(bitmap: Bitmap, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(SoftWater),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(label, color = MutedInk, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

private fun qualityColor(level: FishQualityLevel): Color = when (level) {
    FishQualityLevel.GOOD -> WaterTeal
    FishQualityLevel.WARNING -> Color(0xFFE58B2A)
    FishQualityLevel.INVALID -> Color(0xFFB24A3A)
}

private fun format3(value: Float): String = String.format(Locale.US, "%.3f", value)

private fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)
