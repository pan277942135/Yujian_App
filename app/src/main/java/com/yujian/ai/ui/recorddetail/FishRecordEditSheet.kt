package com.yujian.ai.ui.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.CatchRecordEditDraft
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ManualSpeciesId = "manual_unknown"
private const val ManualSpeciesName = "手动记录"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishRecordEditSheet(
    record: RemoteCatch,
    speciesCatalog: List<FishGuideItem>,
    onDismiss: () -> Unit,
    onSave: (CatchRecordEditDraft) -> Boolean,
) {
    var speciesId by remember(record.id) { mutableStateOf(record.speciesId.ifBlank { ManualSpeciesId }) }
    var speciesName by remember(record.id) { mutableStateOf(record.speciesName.ifBlank { ManualSpeciesName }) }
    var length by remember(record.id) { mutableStateOf(record.lengthCm?.toString().orEmpty()) }
    var weight by remember(record.id) { mutableStateOf(record.weightKg?.toString().orEmpty()) }
    var capturedAt by remember(record.id) { mutableStateOf(record.capturedAt) }
    var location by remember(record.id) { mutableStateOf(record.location.orEmpty()) }
    var story by remember(record.id) { mutableStateOf(record.story.orEmpty()) }
    var speciesMenu by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val speciesOptions = remember(record.id, record.speciesId, record.speciesName, speciesCatalog) {
        buildList {
            if (record.speciesId.isNotBlank() && record.speciesName.isNotBlank()) {
                add(FishGuideItem(id = record.speciesId, nameCn = record.speciesName))
            }
            addAll(speciesCatalog)
        }.distinctBy { it.id }
    }
    val initial = remember(record.id) {
        listOf(
            record.speciesId.ifBlank { ManualSpeciesId }, record.speciesName.ifBlank { ManualSpeciesName },
            record.lengthCm?.toString().orEmpty(), record.weightKg?.toString().orEmpty(), record.capturedAt,
            record.location.orEmpty(), record.story.orEmpty(),
        )
    }
    val current = listOf(speciesId, speciesName, length, weight, capturedAt, location, story)
    val dirty = current != initial
    val dateError = validRecordTime(capturedAt).not()
    val lengthInvalid = length.isNotBlank() && (length.toFloatOrNull()?.let { it > 0f } != true)
    val weightInvalid = weight.isNotBlank() && (weight.toFloatOrNull()?.let { it > 0f } != true)
    val saveEnabled = speciesName.isNotBlank() && !dateError && !lengthInvalid && !weightInvalid
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    ModalBottomSheet(onDismissRequest = { if (dirty) discardConfirm = true else onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("编辑鱼获信息", style = YuJianTypography.sectionTitle, color = YuJianColors.DeepLakeBlue)
            Text("基础信息", style = YuJianTypography.body, color = YuJianColors.DeepLakeBlue)
            Column {
                OutlinedButton(onClick = { speciesMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("鱼种　$speciesName　›")
                }
                DropdownMenu(expanded = speciesMenu, onDismissRequest = { speciesMenu = false }) {
                    speciesOptions.forEach { species ->
                            DropdownMenuItem(
                                text = { Text(species.nameCn) },
                                onClick = {
                                    speciesId = species.id
                                    speciesName = species.nameCn
                                    speciesMenu = false
                                },
                            )
                        }
                    DropdownMenuItem(
                        text = { Text(ManualSpeciesName) },
                        onClick = {
                            speciesId = ManualSpeciesId
                            speciesName = ManualSpeciesName
                            speciesMenu = false
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    label = { Text("长度") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = lengthInvalid,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    label = { Text("重量") },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = weightInvalid,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Text("环境与记录", style = YuJianTypography.body, color = YuJianColors.DeepLakeBlue)
            OutlinedTextField(
                value = capturedAt,
                onValueChange = { capturedAt = it },
                label = { Text("时间") },
                supportingText = { if (dateError) Text("请输入有效且未超前的日期时间") },
                isError = dateError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("地点") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = story,
                onValueChange = { story = it },
                label = { Text("写下这次鱼获的故事") },
                placeholder = { Text("记录这一刻的感受…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            saveError?.let { Text(it, color = Color(0xFF9E4035)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (dirty) discardConfirm = true else onDismiss() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("取消") }
                YuJianPrimaryButton(
                    text = "保存修改",
                    onClick = {
                        val id = when {
                            speciesId == record.speciesId && speciesName == record.speciesName -> record.speciesId
                            else -> speciesOptions.firstOrNull { it.id == speciesId }?.id ?: ManualSpeciesId
                        }
                        val draft = CatchRecordEditDraft(
                            speciesId = id,
                            speciesName = speciesName.trim(),
                            capturedAt = capturedAt.trim(),
                            lengthCm = length.toFloatOrNull(),
                            weightKg = weight.toFloatOrNull(),
                            location = location.trim(),
                            story = story,
                        )
                        saveError = if (onSave(draft)) null else "保存失败，修改仍保留在这里，请重试。"
                        if (saveError == null) onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = saveEnabled,
                    variant = YuJianActionButtonVariant.PRIMARY,
                )
            }
        }
    }

    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("尚未保存的鱼获信息将被丢弃。") },
            confirmButton = {
                TextButton(onClick = { discardConfirm = false; onDismiss() }) { Text("放弃修改") }
            },
            dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("继续编辑") } },
        )
    }
}

private fun validRecordTime(value: String): Boolean {
    if (value.isBlank()) return false
    val formats = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX")
    val date = formats.firstNotNullOfOrNull { pattern ->
        val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        formatter.parse(value, position)?.takeIf { position.index == value.length }
    } ?: return false
    return date.time <= Date().time + 5 * 60 * 1000L
}
