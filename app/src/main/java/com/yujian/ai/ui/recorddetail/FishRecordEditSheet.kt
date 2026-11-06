package com.yujian.ai.ui.recorddetail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchRecordEditDraft
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.ui.recognition.result.SpeciesSelectorEntryContext
import com.yujian.ai.ui.screens.RecognitionSpeciesSelectorScreen
import java.util.Calendar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val ManualSpeciesId = "manual_unknown"
private const val ManualSpeciesName = "手动记录"
private const val StoryMaxLength = 2000

// Frozen editing sheet palette (FishRecordDetail Editing V1 / RN parity).
private val EditSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val EditSheetBackground = Color(0xF0ECF3FB) // rgba(236,243,251,0.94)
private val EditHandleColor = Color(0xFFA9BED4)
private val FieldCardBorder = Color(0xFFD6E3F0)
private val FieldCardBackground = Color(0xA6FFFFFF) // rgba(255,255,255,0.65)
private val FieldInputBorder = Color(0x290B2D4B) // rgba(11,45,75,0.16)
private val FieldInputInvalidBorder = Color(0x8CC25B4E) // rgba(194,91,78,0.55)
private val FieldErrorColor = Color(0xFFC25B4E)
private val VoiceActionColor = Color(0xFF2F6FD0)
private val SaveGradientStart = Color(0xFF3E7FE8)
private val SaveGradientEnd = Color(0xFF2A5FC4)
private val SaveDisabledBackground = Color(0x59748897) // rgba(116,136,151,0.35)
private val CancelBorder = Color(0x240B2D4B) // rgba(11,45,75,0.14)
private val StoryBoxBorder = Color(0x80748897) // rgba(116,136,151,0.5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishRecordEditSheet(
    record: RemoteCatch,
    speciesCatalog: List<FishGuideItem>,
    onDismiss: () -> Unit,
    onSave: (CatchRecordEditDraft) -> Boolean,
    resolveCoverUrl: (String?) -> String? = { it },
) {
    var speciesId by remember(record.id) { mutableStateOf(record.speciesId.ifBlank { ManualSpeciesId }) }
    var speciesName by remember(record.id) { mutableStateOf(record.speciesName.ifBlank { ManualSpeciesName }) }
    var length by remember(record.id) { mutableStateOf(record.lengthCm?.toString().orEmpty()) }
    var weight by remember(record.id) { mutableStateOf(record.weightKg?.toString().orEmpty()) }
    var capturedAt by remember(record.id) { mutableStateOf(record.capturedAt) }
    var location by remember(record.id) { mutableStateOf(record.location.orEmpty()) }
    var story by remember(record.id) { mutableStateOf(record.story.orEmpty()) }
    var speciesSelectorVisible by remember { mutableStateOf(false) }
    var locationDialog by remember { mutableStateOf(false) }
    var locationDraft by remember(record.id) { mutableStateOf(record.location.orEmpty()) }
    var discardConfirm by remember { mutableStateOf(false) }
    var voiceHint by remember { mutableStateOf(false) }
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
    // species_selector_v1 EDIT_CONFIRMED: full-screen picker over the sheet, manual entry kept reachable.
    val selectorSpecies = remember(speciesOptions) { speciesOptions + FishGuideItem(id = ManualSpeciesId, nameCn = ManualSpeciesName) }
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

    // The sheet lives in its own window; hide it while the picker is up so the
    // draft state (remember keyed on record.id) survives the round trip.
    if (!speciesSelectorVisible) {
    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = EditSheetShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier.padding(top = 8.dp, bottom = 2.dp).width(30.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(EditHandleColor),
            )
        },
    ) {
        // RN parity: sheet auto-sizes but never exceeds the available height.
        // The scroll body absorbs the overflow so title/error/actions stay visible.
        BoxWithConstraints {
            val scrollMaxHeight = (maxHeight - 180.dp).coerceAtLeast(120.dp)
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().background(EditSheetBackground, EditSheetShape),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
            ) {
                Text(
                    "编辑鱼获信息",
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    style = YuJianTypography.sectionTitle.copy(
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                IconButton(onClick = requestDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = YuJianColors.LakeBlue, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = scrollMaxHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    EditActionRow(
                        icon = Icons.Rounded.SetMeal,
                        label = "鱼种",
                        value = speciesName.ifBlank { "选择鱼种" },
                        onClick = { keyboard?.hide(); speciesSelectorVisible = true },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MeasurementEditor(
                        label = "长度",
                        value = length,
                        unit = "cm",
                        icon = Icons.Rounded.Straighten,
                        invalid = lengthInvalid,
                        modifier = Modifier.weight(1f),
                        onValueChange = { length = it.filter { char -> char.isDigit() || char == '.' }.take(8) },
                    )
                    MeasurementEditor(
                        label = "重量",
                        value = weight,
                        unit = "kg",
                        icon = Icons.Rounded.Scale,
                        invalid = weightInvalid,
                        modifier = Modifier.weight(1f),
                        onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' }.take(8) },
                    )
                }
                if (lengthInvalid || weightInvalid) {
                    Text(
                        "长度和重量需为正数，或留空",
                        // Negative padding is illegal in Compose; offset achieves the same visual lift.
                        modifier = Modifier.padding(start = 2.dp).offset(y = (-4).dp),
                        style = YuJianTypography.caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        color = FieldErrorColor,
                    )
                }

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
                    voiceHint = voiceHint,
                    onShowVoiceHint = { voiceHint = true },
                    onValueChange = { story = it.take(StoryMaxLength) },
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
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSheetActionButton(
                    label = "取消",
                    primary = false,
                    modifier = Modifier.weight(1f).testTag("fish_record_cancel_button"),
                    onClick = requestDismiss,
                )
                EditSheetActionButton(
                    label = "保存修改",
                    primary = true,
                    enabled = saveEnabled,
                    saving = saving,
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
    }
    }

    if (speciesSelectorVisible) {
        RecognitionSpeciesSelectorScreen(
            species = selectorSpecies,
            resolveCoverUrl = resolveCoverUrl,
            selectedSpeciesId = speciesId,
            entryContext = SpeciesSelectorEntryContext.EDIT_CONFIRMED,
            onBack = { speciesSelectorVisible = false },
            onSelect = { picked ->
                speciesId = picked.id
                speciesName = picked.nameCn
                speciesSelectorVisible = false
            },
            onUnconfirmed = { speciesSelectorVisible = false },
        )
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.76f))
                            .border(1.dp, FieldCardBorder, RoundedCornerShape(10.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(FieldCardBackground)
            .border(1.dp, FieldCardBorder, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = YuJianColors.MorningGold, modifier = Modifier.size(17.dp))
        Text(
            label,
            style = YuJianTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp),
            color = YuJianColors.MistBlueGray,
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = YuJianTypography.body.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                color = if (error) FieldErrorColor else YuJianColors.DeepLakeBlue,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            supporting?.let {
                Text(
                    it,
                    style = YuJianTypography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = if (error) FieldErrorColor else YuJianColors.MistBlueGray,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = YuJianColors.MistBlueGray,
            modifier = Modifier.size(15.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .background(FieldCardBackground)
            .border(1.dp, FieldCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = YuJianColors.MorningGold, modifier = Modifier.size(15.dp))
            Text(
                label,
                style = YuJianTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp),
                color = YuJianColors.MistBlueGray,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(41.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(
                    1.dp,
                    if (invalid) FieldInputInvalidBorder else FieldInputBorder,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                    .testTag(if (label == "长度") "fish_record_length_input" else "fish_record_weight_input"),
                textStyle = YuJianTypography.body.copy(
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YuJianColors.DeepLakeBlue,
                ),
                cursorBrush = SolidColor(YuJianColors.DeepLakeBlue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "—",
                                style = YuJianTypography.body.copy(fontSize = 16.sp),
                                color = YuJianColors.MistBlueGray,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                unit,
                modifier = Modifier.padding(start = 4.dp),
                style = YuJianTypography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = YuJianColors.MistBlueGray,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryEditor(
    value: String,
    voiceHint: Boolean,
    onShowVoiceHint: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "写下这次鱼获的故事",
                modifier = Modifier.weight(1f),
                style = YuJianTypography.body.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = YuJianColors.DeepLakeBlue,
            )
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onShowVoiceHint)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "语音录入", tint = VoiceActionColor, modifier = Modifier.size(14.dp))
                Text(
                    "语音录入",
                    style = YuJianTypography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                    color = VoiceActionColor,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                .dashedBorder(StoryBoxBorder, 1.dp, 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x80FFFFFF))
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("fish_record_story_input"),
            textStyle = YuJianTypography.body.copy(fontSize = 13.5.sp, lineHeight = 22.sp, color = YuJianColors.DeepLakeBlue),
            cursorBrush = SolidColor(YuJianColors.DeepLakeBlue),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
            minLines = 4,
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            "记录这一刻的感受…",
                            style = YuJianTypography.body.copy(fontSize = 13.5.sp, lineHeight = 22.sp),
                            color = YuJianColors.MistBlueGray,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (voiceHint) {
            Text(
                "语音转文字马上就来，先写下文字吧",
                style = YuJianTypography.caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = YuJianColors.MistBlueGray,
            )
        }
    }
}

@Composable
private fun EditSheetActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    saving: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(21.dp)
    val background = when {
        primary && enabled -> Brush.linearGradient(
            colors = listOf(SaveGradientStart, SaveGradientEnd),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
        primary -> Brush.linearGradient(listOf(SaveDisabledBackground, SaveDisabledBackground))
        else -> Brush.linearGradient(listOf(Color(0xE6FFFFFF), Color(0xE6FFFFFF)))
    }
    Box(
        modifier = modifier.height(42.dp)
            .clip(shape)
            .background(background, shape)
            .then(if (primary) Modifier else Modifier.border(1.dp, CancelBorder, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (primary && saving) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                label,
                style = YuJianTypography.buttonText.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                ),
                color = if (primary) Color.White else YuJianColors.DeepLakeBlue,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Frozen story box border: 1px dashed hairline around a 10dp rounded rect. */
private fun Modifier.dashedBorder(color: Color, strokeWidth: Dp, cornerRadius: Dp): Modifier =
    drawBehind {
        val stroke = strokeWidth.toPx()
        val radius = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            ),
        )
    }
