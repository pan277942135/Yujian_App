package com.yujian.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.model.DemoData
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeBackground
import com.yujian.ai.ui.designsystem.background.YuJianMorningLakeVariant
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.designsystem.glass.MistGlass
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.adaptive.rememberAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.recognitionFeedbackType
import com.yujian.ai.ui.identify.resolveRecognitionResultState
import com.yujian.ai.ui.recognition.result.NormalizedSourceRect
import com.yujian.ai.ui.recognition.result.RecognitionHeroMediaMode
import com.yujian.ai.ui.recognition.result.RecognitionHeroMediaPlanner
import com.yujian.ai.ui.recognition.result.RecognitionPlace
import com.yujian.ai.ui.recognition.result.RecognitionPlaceRecentStore
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.resolveCurrentRecognitionPlace
import com.yujian.ai.ui.recognition.result.SpeciesSelectorEntryContext
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecognitionSaveDestination { HOME, MEMORY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    availableSpecies: List<FishGuideItem> = emptyList(),
    speciesCoverUrlFor: (String?) -> String? = { it },
    onSave: (CatchSaveDraft, FeedbackDraft, RecognitionSaveDestination) -> Unit,
) {
    val uiState = remember(prediction) { resolveRecognitionResultState(prediction) }
    val configuration = LocalConfiguration.current
    val safeInsets = rememberSafeDrawingInsets()
    val adaptiveProfile = rememberAdaptiveLayoutProfile(
        configuration.screenWidthDp.dp,
        configuration.screenHeightDp.dp,
    )
    val geometry = RecognitionResultGeometryResolver.resolve(adaptiveProfile)
    val candidateFontScale = LocalDensity.current.fontScale
    val candidates = remember(prediction) { prediction.candidates.distinctBy { it.speciesKey }.take(3) }
    val selectorSpecies = remember(prediction, availableSpecies) {
        val localCatalog = DemoData.species.map { fish ->
            FishGuideItem(
                id = fish.key,
                nameCn = fish.name,
                aliases = fish.aliases.split("、").map(String::trim).filter(String::isNotBlank),
            )
        }
        val predictionSpecies = prediction.candidates.map { candidate ->
            FishGuideItem(id = candidate.speciesKey, nameCn = candidate.speciesName)
        }
        (availableSpecies + localCatalog + predictionSpecies).distinctBy { it.id }
    }
    val speciesImages = remember(availableSpecies, speciesCoverUrlFor) {
        availableSpecies.associate { it.id to speciesCoverUrlFor(it.coverImage) }
    }
    var selectedKey by remember(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_HIGH) prediction.top1.speciesKey else "")
    }
    var selectedName by remember(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_HIGH) prediction.top1.speciesName else "")
    }
    var selectorVisible by remember(prediction, uiState) { mutableStateOf(false) }
    val selectorEntryContext = when {
        uiState == RecognitionUiState.RESULT_MEDIUM && selectedKey.isBlank() -> SpeciesSelectorEntryContext.MEDIUM_OTHER
        uiState == RecognitionUiState.RESULT_LOW && selectedKey.isBlank() -> SpeciesSelectorEntryContext.LOW_MANUAL
        else -> SpeciesSelectorEntryContext.EDIT_CONFIRMED
    }
    var editField by remember(prediction) { mutableStateOf<ResultEditableField?>(null) }
    var lengthText by remember(prediction) { mutableStateOf("") }
    var weightText by remember(prediction) { mutableStateOf("") }
    var locationText by remember(prediction) { mutableStateOf("") }
    var storyText by remember(prediction) { mutableStateOf("") }
    var saveRequested by remember(prediction) { mutableStateOf(false) }
    var requestedSaveDestination by remember(prediction) { mutableStateOf<RecognitionSaveDestination?>(null) }
    var purposeVisible by remember(prediction) { mutableStateOf(false) }
    var resolvingLocation by remember(prediction) { mutableStateOf(false) }
    var locationError by remember(prediction) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val placeRecentStore = remember(context) { RecognitionPlaceRecentStore(context) }
    fun resolveCurrentLocation() {
        scope.launch {
            resolvingLocation = true
            locationError = null
            val place = resolveCurrentRecognitionPlace(context)
            if (place != null) {
                locationText = place.name.takeUnicodeCodePoints(40)
                placeRecentStore.commit(place)
                editField = null
            } else {
                locationError = "无法获取当前位置，请稍后重试"
            }
            resolvingLocation = false
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            resolveCurrentLocation()
        } else {
            locationError = "未获得位置权限，仍可搜索地点"
        }
    }
    fun requestCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) resolveCurrentLocation() else purposeVisible = true
    }
    val currentTime = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()) }
    LaunchedEffect(saving, saveError) {
        if (!saving && saveRequested) {
            saveRequested = false
            requestedSaveDestination = null
        }
    }
    val bbox = productionResult?.assessment?.primary?.box?.let {
        NormalizedSourceRect(it.x1, it.y1, it.x2, it.y2)
    }

    fun save(destination: RecognitionSaveDestination) {
        if (selectedKey.isBlank() || saving || saveRequested) return
        saveRequested = true
        requestedSaveDestination = destination
        val corrected = selectedKey != prediction.top1.speciesKey
        val detector = productionResult?.assessment?.primary?.let {
            JSONObject().put("confidence", it.confidence.toDouble()).put("box", JSONObject()
                .put("x1", it.box.x1.toDouble()).put("y1", it.box.y1.toDouble())
                .put("x2", it.box.x2.toDouble()).put("y2", it.box.y2.toDouble()))
        }
        val feedback = FeedbackDraft(
            sourceEventId = "APP_${java.util.UUID.randomUUID()}", imageId = image?.imageId,
            feedbackType = recognitionFeedbackType(prediction.top1.speciesKey, selectedKey),
            modelVersion = prediction.modelVersion, predictedSpecies = prediction.top1.speciesName,
            confidence = prediction.top1.confidence, correctedSpecies = selectedName.takeIf { corrected },
            userNote = "ui_state=${uiState.name};length_cm=$lengthText;weight_kg=$weightText;location=$locationText;story=$storyText",
        )
        val classifier = JSONObject()
            .put("ui_state", uiState.name).put("model_version", prediction.modelVersion)
            .put("prediction_species", prediction.top1.speciesKey).put("confidence", prediction.top1.confidence.toDouble())
            .put("user_selected_species", selectedKey)
            .put("length_cm", lengthText.toDoubleOrNull() ?: JSONObject.NULL)
            .put("weight_kg", weightText.toDoubleOrNull() ?: JSONObject.NULL)
            .put("location", locationText.ifBlank { JSONObject.NULL })
            .put("story", storyText.ifBlank { JSONObject.NULL })
            .put("created_at", currentTime).put("photo_url", image?.filePath ?: "")
        onSave(
            CatchSaveDraft(selectedKey, selectedName, prediction.top1.confidence, prediction.modelVersion, detector, classifier),
            feedback,
            destination,
        )
    }

    fun openSpeciesSelector() {
        selectorVisible = true
    }

    val activeLoadingDestination = requestedSaveDestination.takeIf { saveRequested }
    Box(Modifier.fillMaxSize()) {
        BgContentSurface()
        Column(Modifier.fillMaxSize()) {
            YuJianBackTitleTopBar(title = "识别结果", onBack = onBack, backEnabled = !saving)
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .padding(start = safeInsets.start, end = safeInsets.end)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp + safeInsets.bottom),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                image?.let {
                    ResultHeroViewport(
                        bitmap = it.bitmap.asImageBitmap(),
                        bbox = bbox,
                        widthDp = geometry.heroWidthDp,
                        heightDp = geometry.heroHeightDp,
                        evidenceFirst = uiState == RecognitionUiState.ERROR_NO_FISH ||
                            uiState == RecognitionUiState.ERROR_IMAGE_QUALITY,
                    )
                }
                Spacer(Modifier.height(16.dp))
                val sideMargin = geometry.horizontalMarginDp.dp
                when (uiState) {
                    RecognitionUiState.RESULT_HIGH -> {
                        SpeciesIdentityRow(
                            speciesName = selectedName,
                            sideMargin = sideMargin,
                            widthDp = geometry.heroWidthDp,
                            enabled = !saving,
                            onChange = { openSpeciesSelector() },
                        )
                        Spacer(Modifier.height(12.dp))
                        ResultMetadataStrip(
                            length = lengthText,
                            weight = weightText,
                            location = locationText,
                            resolvingLocation = resolvingLocation,
                            sideMargin = sideMargin,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            enabled = !saving,
                            onField = { editField = it },
                        )
                        Spacer(Modifier.height(12.dp))
                        ResultMemoryNote(
                            value = storyText,
                            onValueChange = { storyText = it.takeUnicodeCodePoints(120) },
                            sideMargin = sideMargin,
                            enabled = !saving,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                        )
                        Spacer(Modifier.height(16.dp))
                        ResultInlineError(saveError, sideMargin)
                        ResultDualActions(
                            saving = saving,
                            loadingDestination = activeLoadingDestination,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            sideMargin = 24.dp,
                            onContinue = { save(RecognitionSaveDestination.MEMORY) },
                            onSave = { save(RecognitionSaveDestination.HOME) },
                        )
                    }
                    RecognitionUiState.RESULT_MEDIUM -> {
                        Text(
                            "帮我确认一下，这条鱼更像哪一种？",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                            color = DeepInk,
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (adaptiveProfile.accessibilityFontScale || geometry.heroWidthDp <= 320) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        CandidateRow(
                            candidates = candidates,
                            selectedKey = selectedKey,
                            widthDp = geometry.heroWidthDp,
                            accessibilityFontScale = candidateFontScale,
                            saving = saving,
                            speciesImages = speciesImages,
                            onSelect = { candidate -> selectedKey = candidate.speciesKey; selectedName = candidate.speciesName },
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().padding(horizontal = sideMargin), contentAlignment = Alignment.CenterStart) {
                            YuJianTextAction(
                                text = "都不是？选择其他鱼种",
                                onClick = { openSpeciesSelector() },
                                role = YuJianTextActionRole.MUTED,
                                enabled = !saving,
                                showChevron = true,
                            )
                        }
                        if (selectedKey.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            SpeciesIdentityRow(selectedName, sideMargin, widthDp = geometry.heroWidthDp, enabled = !saving, onChange = { openSpeciesSelector() })
                            Spacer(Modifier.height(12.dp))
                            ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, sideMargin, accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saving) { editField = it }
                            Spacer(Modifier.height(12.dp))
                            ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(120) }, sideMargin, enabled = !saving, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(saveError, sideMargin)
                            ResultDualActions(
                                saving = saving,
                                loadingDestination = activeLoadingDestination,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                sideMargin = 24.dp,
                                onContinue = { save(RecognitionSaveDestination.MEMORY) },
                                onSave = { save(RecognitionSaveDestination.HOME) },
                            )
                        }
                    }
                    RecognitionUiState.RESULT_LOW -> {
                        if (selectedKey.isBlank()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = sideMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("无法确认是什么鱼", color = DeepInk, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                                if (adaptiveProfile.accessibilityFontScale) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        YuJianPrimaryButton(
                                            text = "手动选择鱼种", onClick = { openSpeciesSelector() },
                                            modifier = Modifier.fillMaxWidth(),
                                            variant = YuJianActionButtonVariant.SECONDARY_STRONG,
                                        )
                                        YuJianPrimaryButton(
                                            text = "重新拍摄", onClick = onRetry,
                                            modifier = Modifier.fillMaxWidth(),
                                            variant = YuJianActionButtonVariant.SECONDARY_MUTED,
                                        )
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        YuJianPrimaryButton(
                                            text = "手动选择鱼种", onClick = { openSpeciesSelector() },
                                            modifier = Modifier.weight(58f),
                                            variant = YuJianActionButtonVariant.SECONDARY_STRONG,
                                        )
                                        YuJianPrimaryButton(
                                            text = "重新拍摄", onClick = onRetry,
                                            modifier = Modifier.weight(42f),
                                            variant = YuJianActionButtonVariant.SECONDARY_MUTED,
                                        )
                                    }
                                }
                            }
                        } else {
                            SpeciesIdentityRow(selectedName, sideMargin, widthDp = geometry.heroWidthDp, enabled = !saving) { openSpeciesSelector() }
                            Spacer(Modifier.height(12.dp))
                            ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, sideMargin, accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saving) { editField = it }
                            Spacer(Modifier.height(12.dp))
                            ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(120) }, sideMargin, enabled = !saving, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(saveError, sideMargin)
                            ResultDualActions(
                                saving = saving, sideMargin = 24.dp,
                                loadingDestination = activeLoadingDestination,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                onContinue = { save(RecognitionSaveDestination.MEMORY) },
                                onSave = { save(RecognitionSaveDestination.HOME) },
                            )
                        }
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (selectorVisible) {
        RecognitionSpeciesSelectorScreen(
            species = selectorSpecies,
            resolveCoverUrl = speciesCoverUrlFor,
            selectedSpeciesId = selectedKey,
            entryContext = selectorEntryContext,
            onBack = { selectorVisible = false },
            onSelect = { selected ->
                selectedKey = selected.id
                selectedName = selected.nameCn
                selectorVisible = false
            },
            onUnconfirmed = { selectorVisible = false },
        )
    }
    editField?.let { field ->
        when (field) {
            ResultEditableField.LENGTH -> ResultFieldEditorSheet(
                title = "鱼获长度", label = "长度", unit = "cm", value = lengthText,
                keyboardType = KeyboardType.Decimal, validation = { RecognitionResultInputValidation.length(it) },
                onDismiss = { editField = null }, onSave = { lengthText = it; editField = null },
            )
            ResultEditableField.WEIGHT -> ResultFieldEditorSheet(
                title = "鱼获重量", label = "重量", unit = "kg", value = weightText,
                keyboardType = KeyboardType.Decimal, validation = { RecognitionResultInputValidation.weight(it) },
                onDismiss = { editField = null }, onSave = { weightText = it; editField = null },
            )
            ResultEditableField.LOCATION -> ResultLocationPickerSheet(
                value = locationText,
                resolving = resolvingLocation,
                currentLocationError = locationError,
                onDismiss = { editField = null },
                onUseCurrentLocation = { requestCurrentLocation() },
                onSelectPlace = { place ->
                    locationText = place.name.takeUnicodeCodePoints(40)
                    locationError = null
                    editField = null
                },
                onClearLocation = { locationText = ""; locationError = null; editField = null },
                onClearCurrentLocationError = { locationError = null },
            )
        }
    }
    if (purposeVisible) {
        AlertDialog(
            onDismissRequest = { purposeVisible = false },
            title = { Text("使用当前位置") },
            text = { Text("只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。拒绝不会影响识鱼和保存鱼获。") },
            confirmButton = {
                TextButton(onClick = {
                    purposeVisible = false
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Text("继续", color = WaterTeal) }
            },
            dismissButton = { TextButton(onClick = { purposeVisible = false }) { Text("暂不使用") } },
        )
    }


}

private enum class ResultEditableField { LENGTH, WEIGHT, LOCATION }

@Composable
internal fun BgContentSurface() {
    YuJianMorningLakeBackground(
        variant = YuJianMorningLakeVariant.CONTENT,
    )
}

@Composable
fun ResultHeroViewport(
    bitmap: ImageBitmap,
    bbox: NormalizedSourceRect?,
    widthDp: Int,
    heightDp: Int,
    evidenceFirst: Boolean = false,
) {
    val plan = remember(bitmap, bbox, widthDp, heightDp) {
        RecognitionHeroMediaPlanner.plan(bitmap.width, bitmap.height, widthDp.toFloat(), heightDp.toFloat(), bbox, evidenceFirst = evidenceFirst)
    }
    Box(
        Modifier.width(widthDp.dp).height(heightDp.dp),
    ) {
        MistGlass(
            level = YuJianGlassLevel.Light,
            modifier = Modifier.fillMaxSize(),
            shape = YuJianRadius.heroCard,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val src = plan.sourceRect
                val sourceLeft = (src.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val sourceTop = (src.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val sourceRight = (src.right * bitmap.width).toInt().coerceIn(sourceLeft + 1, bitmap.width)
                val sourceBottom = (src.bottom * bitmap.height).toInt().coerceIn(sourceTop + 1, bitmap.height)
                val cropW = sourceRight - sourceLeft
                val cropH = sourceBottom - sourceTop
                val scale = if (plan.mode == RecognitionHeroMediaMode.SUBJECT_CROP_FILL) {
                    maxOf(size.width / cropW, size.height / cropH)
                } else minOf(size.width / cropW, size.height / cropH)
                val dstW = cropW * scale
                val dstH = cropH * scale
                val dstX = (size.width - dstW) / 2f
                val dstY = (size.height - dstH) / 2f
                clipRect {
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(sourceLeft, sourceTop),
                        srcSize = IntSize(cropW, cropH),
                        dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
                        dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeciesIdentityRow(speciesName: String, sideMargin: Dp, widthDp: Int, enabled: Boolean = true, onChange: () -> Unit) {
    val compactTitle = widthDp <= 359
    Row(
        Modifier.fillMaxWidth().padding(horizontal = sideMargin).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(speciesName, modifier = Modifier.weight(1f), color = DeepInk, fontSize = if (compactTitle) 28.sp else 30.sp, lineHeight = if (compactTitle) 34.sp else 36.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        YuJianTextAction(text = "修改鱼种", onClick = onChange, role = YuJianTextActionRole.NORMAL, enabled = enabled, showChevron = true)
    }
}

@Composable
private fun ResultMetadataStrip(
    length: String, weight: String, location: String, resolvingLocation: Boolean,
    sideMargin: Dp, accessibilityFontScale: Boolean = false, enabled: Boolean = true, onField: (ResultEditableField) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = sideMargin).heightIn(min = if (accessibilityFontScale) 84.dp else 72.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0xDDF7FAFB)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetadataField("长度", if (length.isBlank()) "添加" else "$length cm", Modifier.weight(1f), enabled, accessibilityFontScale) { onField(ResultEditableField.LENGTH) }
        HorizontalDivider(modifier = Modifier.size(width = 1.dp, height = 32.dp), color = Color(0x2674898D))
        MetadataField("重量", if (weight.isBlank()) "添加" else "$weight kg", Modifier.weight(1f), enabled, accessibilityFontScale) { onField(ResultEditableField.WEIGHT) }
        HorizontalDivider(modifier = Modifier.size(width = 1.dp, height = 32.dp), color = Color(0x2674898D))
        MetadataField("地点", if (resolvingLocation) "正在获取位置…" else location.ifBlank { "添加地点" }, Modifier.weight(1f), enabled, accessibilityFontScale) { onField(ResultEditableField.LOCATION) }
    }
}

@Composable
private fun MetadataField(label: String, value: String, modifier: Modifier, enabled: Boolean, accessibilityFontScale: Boolean, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = if (accessibilityFontScale) 8.dp else 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MutedInk, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1)
        Text(value, color = if (value == "添加" || value == "添加地点" || value == "正在获取位置…") MutedInk else DeepInk,
            fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultMemoryNote(value: String, onValueChange: (String) -> Unit, sideMargin: Dp, enabled: Boolean = true, accessibilityFontScale: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = sideMargin).heightIn(
            min = if (accessibilityFontScale) 108.dp else 80.dp,
            max = when {
                accessibilityFontScale -> 156.dp
                focused -> 112.dp
                else -> 80.dp
            },
        )
            .clip(RoundedCornerShape(18.dp)).background(Color(0xB8F7FAFB)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("留下本次鱼获感言", color = DeepInk, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (value.isBlank()) Text("记录这一刻的感受…", color = MutedInk, fontSize = 15.sp, lineHeight = 22.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    },
                minLines = 1,
                maxLines = if (focused || accessibilityFontScale) 4 else 2,
                enabled = enabled,
                textStyle = TextStyle(color = DeepInk, fontSize = 15.sp, lineHeight = 22.sp),
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidates: List<RecognitionCandidate>, selectedKey: String, widthDp: Int,
    accessibilityFontScale: Float, saving: Boolean,
    speciesImages: Map<String, String?>,
    onSelect: (RecognitionCandidate) -> Unit,
) {
    val accessibilityScroll = RecognitionResultGeometryResolver.usesScrollableCandidateRow(accessibilityFontScale)
    val cardWidth = if (accessibilityScroll) 104f else if (candidates.size == 2) 136f else ((widthDp - 16f) / 3f).coerceIn(88f, 116f)
    Row(
        Modifier.fillMaxWidth().then(
            if (accessibilityScroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
        ),
        horizontalArrangement = if (accessibilityScroll) {
            Arrangement.spacedBy(8.dp)
        } else {
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        },
    ) {
        candidates.take(3).forEachIndexed { index, candidate ->
            val selected = candidate.speciesKey == selectedKey
            val suggested = index == 0 && !selected
            Column(
                Modifier.width(cardWidth.dp).height(if (widthDp < 300) 108.dp else 112.dp)
                    .graphicsLayer { alpha = if (saving) 0.42f else 1f }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Color(0x140F7A78) else Color(0xDDF7FAFB))
                    .border(if (selected) 2.dp else 1.dp, when { selected -> Color(0xFF0F7A78); suggested -> Color(0x477A9C9A); else -> Color(0x44FFFFFF) }, RoundedCornerShape(16.dp))
                    .semantics {
                        this.selected = selected
                        if (suggested) stateDescription = "模型建议"
                    }
                    .clickable(enabled = !saving, role = Role.RadioButton) { onSelect(candidate) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val mediaSize = if (widthDp <= 320) 44.dp else 48.dp
                Box(
                    Modifier.size(mediaSize).clip(RoundedCornerShape(50)).background(Color(0x337A9C9A)),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageUrl = speciesImages[candidate.speciesKey]
                    if (!imageUrl.isNullOrBlank()) {
                        RemoteImage(
                            url = imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = candidate.speciesName,
                            contentScale = ContentScale.Fit,
                            placeholder = { FishIllustration(modifier = Modifier.size(mediaSize), size = mediaSize, bodyColor = Color(0xFF789795)) },
                        )
                    } else {
                        FishIllustration(modifier = Modifier.size(mediaSize), size = mediaSize, bodyColor = Color(0xFF789795))
                    }
                }
                Text(candidate.speciesName, color = DeepInk, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultDualActions(
    saving: Boolean,
    loadingDestination: RecognitionSaveDestination?,
    accessibilityFontScale: Boolean,
    sideMargin: Dp,
    onContinue: () -> Unit,
    onSave: () -> Unit,
) {
    // The callbacks are supplied by the enclosing page so metadata and species are validated together.
    val blocked = saving || loadingDestination != null
    if (accessibilityFontScale) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sideMargin), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = sideMargin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                modifier = Modifier.weight(50f),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                modifier = Modifier.weight(50f),
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun ResultDualActionButton(
    destination: RecognitionSaveDestination,
    loadingDestination: RecognitionSaveDestination?,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isContinue = destination == RecognitionSaveDestination.MEMORY
    YuJianPrimaryButton(
        text = if (isContinue) "继续记录记忆" else "保存本次鱼获",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loadingDestination == destination,
        variant = if (isContinue) YuJianActionButtonVariant.SECONDARY_STRONG else YuJianActionButtonVariant.PRIMARY,
    )
}

@Composable
private fun ResultInlineError(message: String?, sideMargin: Dp) {
    if (!message.isNullOrBlank()) Text(
        "保存鱼获失败，请重试", modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin).padding(bottom = 8.dp),
        color = Color(0xFF9E4035), fontSize = 13.sp, lineHeight = 18.sp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultFieldEditorSheet(
    title: String, label: String, unit: String, value: String, keyboardType: KeyboardType,
    validation: (String) -> String?, onDismiss: () -> Unit, onSave: (String) -> Unit,
) {
    var text by remember(title, value) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember(title) { FocusRequester() }
    fun finishEditing() {
        val invalid = validation(text.text)
        if (invalid != null) {
            error = invalid
        } else {
            onSave(text.text.trim())
            keyboard?.hide()
        }
    }
    LaunchedEffect(title) {
        delay(220L)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = DeepInk, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text, onValueChange = { next ->
                    val cleaned = next.text.filter { char -> char.isDigit() || char == '.' || char == ',' }.replace(',', '.').take(8)
                    text = TextFieldValue(cleaned, TextRange(cleaned.length))
                    error = null
                },
                modifier = Modifier.widthIn(max = 216.dp).fillMaxWidth().heightIn(min = 64.dp)
                    .align(Alignment.CenterHorizontally).focusRequester(focusRequester)
                    .testTag("recognition-numeric-${label.lowercase()}"), singleLine = true,
                label = { Text(label) }, suffix = { Text(unit) }, isError = error != null,
                supportingText = error?.let { { Text(it) } },
                textStyle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, color = DeepInk),
                trailingIcon = if (text.text.isNotEmpty()) {
                    { IconButton(onClick = { text = TextFieldValue(""); error = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, contentDescription = "清除输入", tint = MutedInk) } }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishEditing() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) {
                    YuJianTextAction(
                        text = "清除", onClick = { onSave(""); keyboard?.hide() },
                        role = YuJianTextActionRole.MUTED,
                    )
                } else Spacer(Modifier.width(64.dp))
                YuJianTextAction(text = "完成", onClick = { finishEditing() }, role = YuJianTextActionRole.NORMAL)
            }
        }
    }
}

private fun String.takeUnicodeCodePoints(limit: Int): String = RecognitionResultInputValidation.takeUnicodeCodePoints(this, limit)
