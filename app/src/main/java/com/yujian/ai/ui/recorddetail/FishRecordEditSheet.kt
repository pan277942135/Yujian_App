package com.yujian.ai.ui.recorddetail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchRecordEditDraft
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import java.util.Calendar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val ManualSpeciesId = "manual_unknown"
private const val ManualSpeciesName = "手动记录"
private val EditCardShape = RoundedCornerShape(18.dp)
private val EditSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

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
    var locationDialog by remember { mutableStateOf(false) }
    var locationDraft by remember(record.id) { mutableStateOf(record.location.orEmpty()) }
    var discardConfirm by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locationFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
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
    val dateError = !FishRecordEditTime.isValid(capturedAt)
    val lengthInvalid = length.isNotBlank() && (length.toFloatOrNull()?.let { it > 0f } != true)
    val weightInvalid = weight.isNotBlank() && (weight.toFloatOrNull()?.let { it > 0f } != true)
    val speciesInvalid = speciesName.isBlank() ||
        (speciesId != ManualSpeciesId && speciesOptions.none { it.id == speciesId })
    val saveEnabled = !saving && !speciesInvalid && !dateError && !lengthInvalid && !weightInvalid
    val timePresentation = FishRecordEditTime.presentation(capturedAt)
    val requestDismiss = {
        if (dirty) discardConfirm = true else onDismiss()
    }

    LaunchedEffect(locationDialog) {
        if (locationDialog) {
            androidx.compose.runtime.withFrameNanos { }
            locationFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val openTimePicker: () -> Unit = {
        keyboard?.hide()
        val initialTime = FishRecordEditTime.calendar(capturedAt)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        FishRecordEditTime.withDateAndTime(
                            originalValue = capturedAt,
                            year = year,
                            month = month,
                            dayOfMonth = day,
                            hourOfDay = hour,
                            minute = minute,
                        )?.let { capturedAt = it }
                    },
                    initialTime.get(Calendar.HOUR_OF_DAY),
                    initialTime.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            initialTime.get(Calendar.YEAR),
            initialTime.get(Calendar.MONTH),
            initialTime.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        modifier = Modifier.fillMaxHeight(0.94f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = EditSheetShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(50)).background(YuJianColors.MistBlueGray.copy(alpha = 0.46f)),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF2F5F9FC), Color(0xE8F1F7FC), Color(0xECF7FAFC)),
                    ),
                    EditSheetShape,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(48.dp))
                Text(
                    "编辑鱼获信息",
                    modifier = Modifier.weight(1f),
                    style = YuJianTypography.sectionTitle.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = requestDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = YuJianColors.DeepLakeBlue)
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditGroupLabel("基础信息")
                Box(Modifier.fillMaxWidth()) {
                    EditActionRow(
                        icon = Icons.Rounded.SetMeal,
                        label = "鱼种",
                        value = speciesName,
                        onClick = { speciesMenu = true },
                    )
                    DropdownMenu(
                        expanded = speciesMenu,
                        onDismissRequest = { speciesMenu = false },
                    ) {
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
                    MeasurementEditor(
                        label = "长度",
                        value = length,
                        unit = "cm",
                        icon = Icons.Rounded.Straighten,
                        invalid = lengthInvalid,
                        modifier = Modifier.weight(1f),
                        onValueChange = { length = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    )
                    MeasurementEditor(
                        label = "重量",
                        value = weight,
                        unit = "kg",
                        icon = Icons.Rounded.Scale,
                        invalid = weightInvalid,
                        modifier = Modifier.weight(1f),
                        onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    )
                }

                EditGroupLabel("环境与记录", modifier = Modifier.padding(top = 2.dp))
                EditActionRow(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "时间",
                    value = timePresentation?.dateTime ?: "请选择日期和时间",
                    supporting = timePresentation?.timeZoneLabel ?: if (dateError) "原时间格式无效，请重新选择" else null,
                    onClick = openTimePicker,
                    error = dateError,
                )
                EditActionRow(
                    icon = Icons.Rounded.Place,
                    label = "地点",
                    value = location.ifBlank { "添加地点" },
                    onClick = {
                        locationDraft = location
                        locationDialog = true
                    },
                )

                StoryEditor(
                    value = story,
                    onValueChange = { story = it },
                )
            }

            saveError?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    style = YuJianTypography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = Color(0xFF9E4035),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xBFEFF4F7))
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSheetActionButton(
                    label = "取消",
                    primary = false,
                    modifier = Modifier.weight(1f).testTag("fish_record_cancel_button"),
                    onClick = requestDismiss,
                )
                EditSheetActionButton(
                    label = if (saving) "保存中…" else "保存修改",
                    primary = true,
                    enabled = saveEnabled,
                    modifier = Modifier.weight(1f).testTag("fish_record_save_button"),
                    onClick = {
                        if (!saving && saveEnabled) {
                            saving = true
                            saveError = null
                            val id = when {
                                speciesId == record.speciesId && speciesName == record.speciesName -> record.speciesId
                                else -> speciesOptions.firstOrNull { it.id == speciesId }?.id ?: ManualSpeciesId
                            }
                            val draft = CatchRecordEditDraft(
                                speciesId = id,
                                speciesName = speciesName.trim(),
                                capturedAt = capturedAt,
                                lengthCm = length.toFloatOrNull(),
                                weightKg = weight.toFloatOrNull(),
                                location = location.trim(),
                                story = story,
                            )
                            val saved = runCatching { onSave(draft) }.getOrDefault(false)
                            saving = false
                            if (saved) onDismiss() else saveError = "保存失败，修改仍保留在这里，请重试。"
                        }
                    },
                )
            }
        }
    }

    if (locationDialog) {
        AlertDialog(
            onDismissRequest = { locationDialog = false },
            title = { Text("编辑地点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("地点名称", style = YuJianTypography.caption, color = YuJianColors.MistBlueGray)
                    BasicTextField(
                        value = locationDraft,
                        onValueChange = { locationDraft = it },
                        modifier = Modifier.fillMaxWidth()
                            .clip(EditCardShape)
                            .background(Color.White.copy(alpha = 0.76f))
                            .border(1.dp, Color.White, EditCardShape)
                            .focusRequester(locationFocusRequester)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("fish_record_location_input"),
                        textStyle = YuJianTypography.body.copy(color = YuJianColors.DeepInk),
                        cursorBrush = SolidColor(YuJianColors.DeepLakeBlue),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { location = locationDraft.trim(); locationDialog = false }) {
                    Text("完成")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationDialog = false }) { Text("取消") }
            },
        )
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

@Composable
private fun EditGroupLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.padding(start = 2.dp, top = 2.dp, bottom = 1.dp),
        style = YuJianTypography.caption.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
        color = YuJianColors.LakeBlue,
    )
}

@Composable
private fun EditActionRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    supporting: String? = null,
    error: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(EditCardShape)
            .background(Color.White.copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), EditCardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = YuJianColors.DeepLakeBlue, modifier = Modifier.size(24.dp))
        Text(
            label,
            modifier = Modifier.padding(start = 10.dp).width(48.dp),
            style = YuJianTypography.body.copy(fontWeight = FontWeight.Medium),
            color = YuJianColors.DeepLakeBlue,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = YuJianTypography.body.copy(fontWeight = FontWeight.Medium),
                color = if (error) Color(0xFF9E4035) else YuJianColors.DeepInk,
                maxLines = 1,
            )
            supporting?.let {
                Text(
                    it,
                    style = YuJianTypography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = if (error) Color(0xFF9E4035) else YuJianColors.MistBlueGray,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = YuJianColors.MistBlueGray,
            modifier = Modifier.padding(start = 6.dp).size(22.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeasurementEditor(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .clip(EditCardShape)
            .background(Color.White.copy(alpha = 0.76f))
            .border(1.dp, Color.White.copy(alpha = 0.94f), EditCardShape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = YuJianColors.DeepLakeBlue, modifier = Modifier.size(21.dp))
            Text(
                label,
                modifier = Modifier.padding(start = 7.dp),
                style = YuJianTypography.body.copy(fontWeight = FontWeight.Medium),
                color = YuJianColors.DeepLakeBlue,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                    .testTag(if (label == "长度") "fish_record_length_input" else "fish_record_weight_input"),
                textStyle = YuJianTypography.dataNumber.copy(fontSize = 20.sp, lineHeight = 26.sp),
                cursorBrush = SolidColor(YuJianColors.DeepLakeBlue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("输入数值", style = YuJianTypography.caption, color = YuJianColors.MistBlueGray)
                        }
                        innerTextField()
                    }
                },
            )
            Text(unit, style = YuJianTypography.body, color = YuJianColors.MistBlueGray)
        }
        if (invalid) {
            Text("请输入大于 0 的数字", style = YuJianTypography.caption.copy(fontSize = 11.sp), color = Color(0xFF9E4035))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryEditor(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(EditCardShape)
            .background(Color.White.copy(alpha = 0.64f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), EditCardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EditNote, contentDescription = null, tint = YuJianColors.DeepLakeBlue, modifier = Modifier.size(22.dp))
            Text(
                "写下这次鱼获的故事",
                modifier = Modifier.padding(start = 8.dp),
                style = YuJianTypography.body.copy(fontWeight = FontWeight.Medium),
                color = YuJianColors.DeepLakeBlue,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
                }
                .testTag("fish_record_story_input"),
            textStyle = YuJianTypography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, color = YuJianColors.DeepInk),
            cursorBrush = SolidColor(YuJianColors.DeepLakeBlue),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
            minLines = 4,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth().heightIn(min = 104.dp)) {
                    if (value.isEmpty()) {
                        Text("记录这一刻的感受…", style = YuJianTypography.body.copy(fontSize = 15.sp), color = YuJianColors.MistBlueGray)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun EditSheetActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val background = when {
        !enabled -> Color(0xFFB9C7D1)
        primary -> Color.Transparent
        else -> Color.White.copy(alpha = 0.72f)
    }
    val border = if (primary) Color(0xFFC7DDF4) else Color.White.copy(alpha = 0.96f)
    Box(
        modifier = modifier.heightIn(min = 50.dp)
            .clip(shape)
            .background(
                if (primary && enabled) {
                    Brush.horizontalGradient(listOf(Color(0xFF174F9E), Color(0xFF2A75CF)))
                } else {
                    Brush.verticalGradient(listOf(background, background))
                },
                shape,
            )
            .border(1.dp, border.copy(alpha = if (enabled) 0.96f else 0.55f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = YuJianTypography.buttonText.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = if (primary && enabled) Color.White else YuJianColors.DeepLakeBlue,
            textAlign = TextAlign.Center,
        )
    }
}
