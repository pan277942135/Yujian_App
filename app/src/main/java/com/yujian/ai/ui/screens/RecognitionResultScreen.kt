package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.recognitionFeedbackType
import com.yujian.ai.ui.identify.resolveRecognitionResultState
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
    val uiState = remember(prediction) { resolveRecognitionResultState(prediction) }
    var selectedKey by remember(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_LOW) "" else prediction.top1.speciesKey)
    }
    var selectedName by remember(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_LOW) "" else prediction.top1.speciesName)
    }
    var selectorVisible by remember(prediction, uiState) { mutableStateOf(false) }
    var editorVisible by remember(prediction) { mutableStateOf(false) }
    var lengthText by remember(prediction) { mutableStateOf("") }
    var weightText by remember(prediction) { mutableStateOf("") }
    var locationText by remember(prediction) { mutableStateOf("") }
    var storyText by remember(prediction) { mutableStateOf("") }
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }
    val candidates = remember(prediction) { prediction.candidates.distinctBy { it.speciesKey }.take(3) }
    val species = remember {
        FishRecognitionEngine.MODEL_LABELS.mapIndexed { index, label ->
            RecognitionCandidate(index, label.first, label.second, 0f)
        }
    }

    Box(Modifier.fillMaxSize().background(WarmBackground)) {
        image?.let {
            Image(
                bitmap = it.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(26.dp).graphicsLayer { alpha = 0.24f },
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("‹", fontSize = 34.sp, color = DeepInk) }
                Spacer(Modifier.weight(1f))
                Text("识别结果", color = DeepInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            image?.let {
                ResultHeroPhoto(it.bitmap)
            }

            when (uiState) {
                RecognitionUiState.RESULT_HIGH -> {
                    SpeciesHeader(
                        speciesName = selectedName,
                        action = "修改鱼种",
                        onAction = { selectorVisible = true },
                    )
                    FrozenCatchDetails(
                        lengthText = lengthText,
                        weightText = weightText,
                        locationText = locationText,
                        storyText = storyText,
                        onEdit = { editorVisible = true },
                    )
                }
                RecognitionUiState.RESULT_MEDIUM -> {
                    Text("帮我确认一下，这条鱼更像哪一种？", color = DeepInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    CandidateCards(candidates, selectedKey) { candidate ->
                        selectedKey = candidate.speciesKey
                        selectedName = candidate.speciesName
                    }
                    TextButton(onClick = { selectorVisible = true }) {
                        Text("都不是？选择其他鱼种 ›", color = MutedInk, fontSize = 14.sp)
                    }
                    FrozenCatchDetails(
                        lengthText = lengthText,
                        weightText = weightText,
                        locationText = locationText,
                        storyText = storyText,
                        onEdit = { editorVisible = true },
                    )
                }
                RecognitionUiState.RESULT_LOW -> {
                    LowConfidenceCard(
                        onSelectSpecies = { selectorVisible = true },
                        onRetry = onRetry,
                    )
                    if (selectedKey.isNotBlank()) {
                        SpeciesHeader(selectedName, "重新选择", { selectorVisible = true })
                        FrozenCatchDetails(
                            lengthText = lengthText,
                            weightText = weightText,
                            locationText = locationText,
                            storyText = storyText,
                            onEdit = { editorVisible = true },
                        )
                    }
                }
                else -> Unit
            }

            if (selectedKey.isNotBlank()) {
                saveError?.let {
                    Text(it, color = Color(0xFFB24A3A), fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        val corrected = selectedKey != prediction.top1.speciesKey
                        val detector = productionResult?.assessment?.primary?.let {
                            JSONObject()
                                .put("confidence", it.confidence.toDouble())
                                .put("box", JSONObject()
                                    .put("x1", it.box.x1.toDouble())
                                    .put("y1", it.box.y1.toDouble())
                                    .put("x2", it.box.x2.toDouble())
                                    .put("y2", it.box.y2.toDouble()))
                        }
                        val feedback = FeedbackDraft(
                            sourceEventId = "APP_${java.util.UUID.randomUUID()}",
                            imageId = image?.imageId,
                            feedbackType = recognitionFeedbackType(prediction.top1.speciesKey, selectedKey),
                            modelVersion = prediction.modelVersion,
                            predictedSpecies = prediction.top1.speciesName,
                            confidence = prediction.top1.confidence,
                            correctedSpecies = selectedName.takeIf { corrected },
                            userNote = "ui_state=${uiState.name};length_cm=$lengthText;weight_kg=$weightText;location=$locationText;story=$storyText",
                        )
                        val classifier = JSONObject()
                            .put("ui_state", uiState.name)
                            .put("model_version", prediction.modelVersion)
                            .put("prediction_species", prediction.top1.speciesKey)
                            .put("confidence", prediction.top1.confidence.toDouble())
                            .put("user_selected_species", selectedKey)
                            .put("length_cm", lengthText.toDoubleOrNull() ?: JSONObject.NULL)
                            .put("weight_kg", weightText.toDoubleOrNull() ?: JSONObject.NULL)
                            .put("location", locationText.ifBlank { JSONObject.NULL })
                            .put("story", storyText.ifBlank { JSONObject.NULL })
                            .put("created_at", currentTime)
                            .put("photo_url", image?.filePath ?: "")
                        onSave(
                            CatchSaveDraft(
                                speciesId = selectedKey,
                                speciesName = selectedName,
                                confidence = prediction.top1.confidence,
                                modelVersion = prediction.modelVersion,
                                detectorResult = detector,
                                classifierResult = classifier,
                            ),
                            feedback,
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
                ) { Text(if (saving) "正在保存…" else "保存本次鱼获", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { onViewGuide(selectedKey) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("查看鱼鉴", color = WaterTeal)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (selectorVisible) {
        FishSpeciesSelectorDialog(
            species = species,
            selectedKey = selectedKey,
            onSelect = { candidate ->
                selectedKey = candidate.speciesKey
                selectedName = candidate.speciesName
                selectorVisible = false
            },
            onDismiss = { selectorVisible = false },
        )
    }
    if (editorVisible) {
        MetadataEditorDialog(
            lengthText = lengthText,
            weightText = weightText,
            locationText = locationText,
            storyText = storyText,
            onDone = { length, weight, location, story ->
                lengthText = length
                weightText = weight
                locationText = location
                storyText = story
                editorVisible = false
            },
            onDismiss = { editorVisible = false },
        )
    }
}

@Composable
private fun ResultHeroPhoto(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "本次鱼获照片",
        modifier = Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(26.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun SpeciesHeader(speciesName: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(speciesName, color = DeepInk, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) { Text("$action ›", color = WaterTeal, fontSize = 15.sp) }
    }
}

@Composable
private fun CandidateCards(
    candidates: List<RecognitionCandidate>,
    selectedKey: String,
    onSelect: (RecognitionCandidate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        candidates.forEach { candidate ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selectedKey == candidate.speciesKey) Color(0xFFFFF2D3) else Color(0xCCFFFFFF))
                    .border(
                        1.dp,
                        if (selectedKey == candidate.speciesKey) Color(0xFFE4A72E) else Color(0x77D1DCE5),
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelect(candidate) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFD6E7E5)), contentAlignment = Alignment.Center) {
                    Text("鱼", color = WaterTeal, fontSize = 22.sp)
                }
                Text(candidate.speciesName, color = DeepInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LowConfidenceCard(onSelectSpecies: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xE6F4F8FC), RoundedCornerShape(24.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("无法确认是什么鱼", color = DeepInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSelectSpecies, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Text("手动选择鱼种", color = DeepInk)
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Text("重新拍摄", color = DeepInk)
            }
        }
    }
}

@Composable
private fun FrozenCatchDetails(
    lengthText: String,
    weightText: String,
    locationText: String,
    storyText: String,
    onEdit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xDDF4F8FC), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailRow("长度", lengthText.ifBlank { "请输入" })
        DetailRow("重量", weightText.ifBlank { "请输入" })
        DetailRow("地点", locationText.ifBlank { "请输入" })
        Text("这次鱼获的故事", color = DeepInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(storyText.ifBlank { "记录这一刻的感受……" }, color = MutedInk, fontSize = 14.sp)
        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) { Text("编辑记录", color = WaterTeal) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DeepInk, fontSize = 17.sp, modifier = Modifier.width(72.dp))
        Spacer(Modifier.weight(1f))
        Text(value, color = if (value == "请输入") MutedInk else DeepInk, fontSize = 15.sp)
        Text(" ›", color = MutedInk, fontSize = 22.sp)
    }
}

@Composable
private fun FishSpeciesSelectorDialog(
    species: List<RecognitionCandidate>,
    selectedKey: String,
    onSelect: (RecognitionCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择鱼种", color = DeepInk) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                species.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedKey == candidate.speciesKey) SoftWater else Color.Transparent,
                        ),
                    ) { Text(candidate.speciesName, color = DeepInk) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MetadataEditorDialog(
    lengthText: String,
    weightText: String,
    locationText: String,
    storyText: String,
    onDone: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var length by remember { mutableStateOf(lengthText) }
    var weight by remember { mutableStateOf(weightText) }
    var location by remember { mutableStateOf(locationText) }
    var story by remember { mutableStateOf(storyText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑鱼获记录", color = DeepInk) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(length, { length = it.take(8) }, label = { Text("长度") }, singleLine = true)
                OutlinedTextField(weight, { weight = it.take(8) }, label = { Text("重量") }, singleLine = true)
                OutlinedTextField(location, { location = it.take(40) }, label = { Text("地点") }, singleLine = true)
                OutlinedTextField(story, { story = it.take(300) }, label = { Text("鱼获故事") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(length, weight, location, story) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
