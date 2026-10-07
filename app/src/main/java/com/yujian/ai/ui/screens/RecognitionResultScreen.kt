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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Straighten
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yujian.ai.ui.designsystem.components.YuJianBackCenterTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.adaptive.rememberAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.recognitionFeedbackType
import com.yujian.ai.ui.identify.resolveRecognitionResultState
import com.yujian.ai.ui.recognition.result.NormalizedSourceRect
import com.yujian.ai.ui.recognition.result.RecognitionHeroMediaMode
import com.yujian.ai.ui.recognition.result.createRecognitionHeroAmbientBackdrop
import com.yujian.ai.ui.recognition.result.RecognitionHeroMediaPlanner
import com.yujian.ai.ui.recognition.result.RecognitionPlace
import com.yujian.ai.ui.recognition.result.RecognitionPlaceRecentStore
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.RecognitionResultVisualState
import com.yujian.ai.ui.recognition.result.resultNumericEditorInitialValue
import com.yujian.ai.ui.recognition.result.resultSpeciesDisplayName
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

internal fun lowPendingSaveDestination(
    selectedSpeciesKey: String,
    pendingDestination: RecognitionSaveDestination?,
): RecognitionSaveDestination? = pendingDestination.takeIf { selectedSpeciesKey.isNotBlank() }

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
    val visualState = when (uiState) {
        RecognitionUiState.RESULT_HIGH -> RecognitionResultVisualState.HIGH
        RecognitionUiState.RESULT_MEDIUM -> RecognitionResultVisualState.MEDIUM
        RecognitionUiState.RESULT_LOW -> RecognitionResultVisualState.LOW
        RecognitionUiState.ERROR_NO_FISH -> RecognitionResultVisualState.NO_FISH
        RecognitionUiState.ERROR_IMAGE_QUALITY,
        RecognitionUiState.TECHNICAL_FAILURE -> RecognitionResultVisualState.IMAGE_QUALITY
    }
    val configuration = LocalConfiguration.current
    val safeInsets = rememberSafeDrawingInsets()
    val adaptiveProfile = rememberAdaptiveLayoutProfile(
        configuration.screenWidthDp.dp,
        configuration.screenHeightDp.dp,
    )
    val geometry = RecognitionResultGeometryResolver.resolve(adaptiveProfile, visualState)
    val compactResultActions = geometry.heroWidthDp <= 340
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
    var pendingSaveDestination by remember(prediction) { mutableStateOf<RecognitionSaveDestination?>(null) }
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
        if (saving || saveRequested) return
        if (selectedKey.isBlank()) {
            pendingSaveDestination = destination
            selectorVisible = true
            return
        }
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

    LaunchedEffect(selectedKey, pendingSaveDestination) {
        val destination = lowPendingSaveDestination(selectedKey, pendingSaveDestination)
        if (selectedKey.isNotBlank() && destination != null && !selectorVisible) {
            pendingSaveDestination = null
            save(destination)
        }
    }

    fun openSpeciesSelector() {
        selectorVisible = true
    }

    val activeLoadingDestination = requestedSaveDestination.takeIf { saveRequested }
    Box(Modifier.fillMaxSize()) {
        BgContentSurface()
        Column(Modifier.fillMaxSize()) {
            YuJianBackCenterTitleTopBar(title = "识别结果", onBack = onBack, backEnabled = !saving)
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .padding(start = safeInsets.start, end = safeInsets.end)
                    // Result content is allowed to exceed a short viewport and is
                    // reached by natural scrolling. Compose does not draw an
                    // always-visible scrollbar for this scroll container.
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
                        sourceBackdropEnabled = uiState == RecognitionUiState.RESULT_HIGH,
                    )
                }
                Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(8.dp))
                        ResultMetadataStrip(
                            length = lengthText,
                            weight = weightText,
                            location = locationText,
                            resolvingLocation = resolvingLocation,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            enabled = !saving,
                            onField = { editField = it },
                        )
                        Spacer(Modifier.height(8.dp))
                        ResultMemoryNote(
                            value = storyText,
                            onValueChange = { storyText = it.takeUnicodeCodePoints(300) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                            enabled = !saving,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                        )
                        Spacer(Modifier.height(16.dp))
                        ResultInlineError(saveError, sideMargin)
                        ResultDualActions(
                            useResultSurfaceActions = true,
                            saving = saving,
                            loadingDestination = activeLoadingDestination,
                            accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            compactLayout = compactResultActions,
                            sideMargin = if (compactResultActions) 16.dp else 24.dp,
                            onContinue = { save(RecognitionSaveDestination.MEMORY) },
                            onSave = { save(RecognitionSaveDestination.HOME) },
                        )
                    }
                    RecognitionUiState.RESULT_MEDIUM -> {
                        ResultInformationGlass(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "帮我确认一下，这条鱼更像哪一种？",
                                    color = DeepInk,
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (adaptiveProfile.accessibilityFontScale || geometry.heroWidthDp <= 320) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                CandidateRow(
                                    candidates = candidates,
                                    selectedKey = selectedKey,
                                    widthDp = geometry.heroWidthDp,
                                    accessibilityFontScale = candidateFontScale,
                                    saving = saving,
                                    speciesImages = speciesImages,
                                    onSelect = { candidate -> selectedKey = candidate.speciesKey; selectedName = candidate.speciesName },
                                )
                                YuJianTextAction(
                                    text = "都不是？选择其他鱼种",
                                    onClick = { openSpeciesSelector() },
                                    role = YuJianTextActionRole.MUTED,
                                    enabled = !saving,
                                    showChevron = true,
                                )
                            }
                        }
                        if (selectedKey.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            SpeciesIdentityRow(selectedName, sideMargin, widthDp = geometry.heroWidthDp, enabled = !saving, onChange = { openSpeciesSelector() })
                            Spacer(Modifier.height(8.dp))
                            ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth().padding(horizontal = sideMargin), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saving) { editField = it }
                            Spacer(Modifier.height(12.dp))
                            ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(300) }, Modifier.fillMaxWidth().padding(horizontal = sideMargin), enabled = !saving, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(saveError, sideMargin)
                            ResultDualActions(
                                saving = saving,
                                loadingDestination = activeLoadingDestination,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                compactLayout = compactResultActions,
                                sideMargin = if (compactResultActions) 16.dp else 24.dp,
                                onContinue = { save(RecognitionSaveDestination.MEMORY) },
                                onSave = { save(RecognitionSaveDestination.HOME) },
                            )
                        }
                    }
                    RecognitionUiState.RESULT_LOW -> {
                        Column(Modifier.fillMaxWidth().padding(horizontal = sideMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("无法确认是什么鱼", color = DeepInk, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                            if (selectedKey.isBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(if (compactResultActions) 8.dp else 12.dp)) {
                                    YuJianPrimaryButton(
                                        text = "手动选择鱼种", onClick = { openSpeciesSelector() },
                                        modifier = Modifier.weight(1f),
                                        enabled = !saving,
                                        variant = YuJianActionButtonVariant.RESULT_SAVE,
                                        leadingIcon = { Icon(Icons.Rounded.SetMeal, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                    YuJianPrimaryButton(
                                        text = "重新拍摄", onClick = onRetry,
                                        modifier = Modifier.weight(1f),
                                        enabled = !saving,
                                        variant = YuJianActionButtonVariant.RESULT_CONTINUE,
                                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                }
                            } else {
                                SpeciesIdentityRow(selectedName, sideMargin, widthDp = geometry.heroWidthDp, enabled = !saving) { openSpeciesSelector() }
                                ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth(), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saving) { editField = it }
                                ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(300) }, Modifier.fillMaxWidth(), enabled = !saving, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                                ResultInlineError(saveError, 0.dp)
                                ResultDualActions(
                                    saving = saving, sideMargin = 0.dp,
                                    loadingDestination = activeLoadingDestination,
                                    accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                    compactLayout = compactResultActions,
                                    onContinue = { save(RecognitionSaveDestination.MEMORY) },
                                    onSave = { save(RecognitionSaveDestination.HOME) },
                                )
                            }
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
            onBack = {
                pendingSaveDestination = null
                selectorVisible = false
            },
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
    sourceBackdropEnabled: Boolean = false,
) {
    val plan = remember(bitmap, bbox, widthDp, heightDp, evidenceFirst) {
        RecognitionHeroMediaPlanner.plan(
            bitmap.width,
            bitmap.height,
            widthDp.toFloat(),
            heightDp.toFloat(),
            bbox,
            evidenceFirst = evidenceFirst,
        )
    }
    val sourceBackdrop = remember(bitmap, plan.mode, sourceBackdropEnabled) {
        if (sourceBackdropEnabled && plan.requiresSourceBackdrop) {
            createRecognitionHeroAmbientBackdrop(bitmap.asAndroidBitmap()).asImageBitmap()
        } else {
            null
        }
    }
    val shape = YuJianRadius.resultHero
    Box(
        Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(shape)
            .background(Color.Black, shape)
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .testTag("recognition-result-hero"),
    ) {
        sourceBackdrop?.let { ambient ->
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag("recognition-result-hero-source-backdrop"),
            ) {
                val scale = maxOf(size.width / ambient.width, size.height / ambient.height)
                val dstW = ambient.width * scale
                val dstH = ambient.height * scale
                drawImage(
                    image = ambient,
                    dstOffset = IntOffset(((size.width - dstW) / 2f).toInt(), ((size.height - dstH) / 2f).toInt()),
                    dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                    alpha = 0.84f,
                    filterQuality = FilterQuality.Medium,
                )
                drawRect(Color.Black.copy(alpha = 0.10f))
            }
        }
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
            } else {
                minOf(size.width / cropW, size.height / cropH)
            }
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

@Composable
private fun SpeciesIdentityRow(
    speciesName: String,
    sideMargin: Dp,
    widthDp: Int,
    enabled: Boolean = true,
    onChange: () -> Unit,
) {
    val compactTitle = widthDp <= 359
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = sideMargin)
            .heightIn(min = 44.dp)
            .testTag("recognition-result-species"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            resultSpeciesDisplayName(speciesName),
            modifier = Modifier.weight(1f),
            color = DeepInk,
            fontSize = if (compactTitle) 28.sp else 30.sp,
            lineHeight = if (compactTitle) 34.sp else 36.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, role = Role.Button, onClick = onChange)
                .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                .testTag("recognition-result-species-edit"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "修改鱼种",
                color = YuJianColors.DeepLakeBlue,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ResultMetadataStrip(
    length: String,
    weight: String,
    location: String,
    resolvingLocation: Boolean,
    modifier: Modifier,
    accessibilityFontScale: Boolean = false,
    enabled: Boolean = true,
    onField: (ResultEditableField) -> Unit,
) {
    ResultMetadataSurface(
        modifier = modifier.testTag("recognition-result-metadata"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CatchFactRow(Icons.Rounded.Straighten, "长度", if (length.isBlank()) "请输入" else "$length cm", enabled, accessibilityFontScale) { onField(ResultEditableField.LENGTH) }
            CatchFactRow(Icons.Rounded.Scale, "重量", if (weight.isBlank()) "请输入" else "$weight kg", enabled, accessibilityFontScale) { onField(ResultEditableField.WEIGHT) }
            CatchFactRow(Icons.Rounded.LocationOn, "地点", if (resolvingLocation) "正在获取位置…" else location.ifBlank { "请选择" }, enabled, accessibilityFontScale) { onField(ResultEditableField.LOCATION) }
        }
    }
}

@Composable
private fun ResultMetadataSurface(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = YuJianRadius.resultGlass
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.72f)),
                ),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape),
    ) {
        content()
    }
}

@Composable
private fun ResultInformationGlass(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ResultContentDrivenSurface(
        modifier = modifier,
        fill = Color.White.copy(alpha = 0.86f),
        border = Color.White.copy(alpha = 0.78f),
        content = content,
    )
}

/**
 * Recovery needs a stronger, readable substrate than the ordinary content
 * glass because the lake image remains visible behind the Result page.
 */
@Composable
internal fun ResultRecoverySurface(
    modifier: Modifier,
    fillAlpha: Float = 0.91f,
    borderAlpha: Float = 0.84f,
    content: @Composable () -> Unit,
) {
    ResultContentDrivenSurface(
        modifier = modifier,
        fill = Color.White.copy(alpha = fillAlpha),
        border = Color.White.copy(alpha = borderAlpha),
        content = content,
    )
}

@Composable
private fun ResultContentDrivenSurface(
    modifier: Modifier,
    fill: Color,
    border: Color,
    content: @Composable () -> Unit,
) {
    val shape = YuJianRadius.resultGlass
    Box(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(fill, fill.copy(alpha = (fill.alpha - 0.08f).coerceAtLeast(0.70f))),
                ),
                shape,
            )
            .border(1.dp, border, shape),
    ) {
        content()
    }
}

@Composable
private fun CatchFactRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    accessibilityFontScale: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (accessibilityFontScale) 68.dp else 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.78f),
            )
            Text(
                label,
                color = MutedInk,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            val placeholder = value == "请输入" || value == "请选择" || value == "正在获取位置…"
            Text(
                value,
                color = if (placeholder) MutedInk else DeepInk,
                fontSize = if (placeholder) 15.sp else 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
                tint = YuJianColors.DeepLakeBlue.copy(alpha = 0.62f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultMemoryNote(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    accessibilityFontScale: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    ResultInformationGlass(
        modifier = modifier
            .heightIn(min = if (accessibilityFontScale) 144.dp else 132.dp)
            .testTag("recognition-result-story"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "写下这次鱼获的故事",
                color = DeepInk,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.66f))
                    .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("recognition-story-editor"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    if (value.isBlank()) {
                        Text("记录这一刻的感受……", color = MutedInk, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth()
                            .testTag("recognition-story-input")
                            .bringIntoViewRequester(bringIntoViewRequester)
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
                Text(
                    "${value.codePointCount(0, value.length)}/300",
                    modifier = Modifier.fillMaxWidth().testTag("recognition-story-counter"),
                    textAlign = TextAlign.End,
                    color = MutedInk,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
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
            ResultInformationGlass(
                modifier = Modifier.width(cardWidth.dp)
                    .graphicsLayer { alpha = if (saving) 0.42f else 1f }
                    .then(if (selected) Modifier.border(2.dp, YuJianColors.MorningGold, YuJianRadius.resultGlass) else Modifier)
                    .semantics {
                        this.selected = selected
                        if (suggested) stateDescription = "模型建议"
                    }
                    .clickable(enabled = !saving, role = Role.RadioButton) { onSelect(candidate) },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            modifier = Modifier.size(mediaSize),
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
}

@Composable
private fun ResultDualActions(
    saving: Boolean,
    loadingDestination: RecognitionSaveDestination?,
    accessibilityFontScale: Boolean,
    compactLayout: Boolean,
    sideMargin: Dp,
    onContinue: () -> Unit,
    onSave: () -> Unit,
    useResultSurfaceActions: Boolean = false,
) {
    // The callbacks are supplied by the enclosing page so metadata and species are validated together.
    val blocked = saving || loadingDestination != null
    if (accessibilityFontScale) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = sideMargin),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 12.dp),
        ) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = sideMargin),
            horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 12.dp),
        ) {
            ResultDualActionButton(
                destination = RecognitionSaveDestination.MEMORY,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.weight(1f),
                onClick = onContinue,
            )
            ResultDualActionButton(
                destination = RecognitionSaveDestination.HOME,
                loadingDestination = loadingDestination,
                enabled = !blocked,
                compactLayout = compactLayout,
                useResultSurfaceActions = useResultSurfaceActions,
                modifier = Modifier.weight(1f),
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
    compactLayout: Boolean,
    useResultSurfaceActions: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isContinue = destination == RecognitionSaveDestination.MEMORY
    val text = if (isContinue) "继续记忆" else "保存本次鱼获"
    val icon = if (isContinue) Icons.Rounded.PhotoLibrary else Icons.Rounded.Save
    val loading = loadingDestination == destination
    if (useResultSurfaceActions) {
        ResultSurfaceActionButton(
            text = text,
            icon = icon,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            compactLayout = compactLayout,
            emphasized = !isContinue,
            testTag = if (isContinue) "recognition-result-continue" else "recognition-result-save",
            onClick = onClick,
        )
    } else {
        YuJianPrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            variant = if (isContinue) YuJianActionButtonVariant.SECONDARY_STRONG else YuJianActionButtonVariant.PRIMARY,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                )
            },
            contentPadding = if (compactLayout) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
            leadingIconSpacing = 4.dp,
        )
    }
}

@Composable
internal fun ResultSurfaceActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    compactLayout: Boolean,
    emphasized: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val surface = if (emphasized) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.74f)
    val edge = if (emphasized) YuJianColors.MorningGold else YuJianColors.DeepLakeBlue.copy(alpha = 0.24f)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(surface, shape)
            .border(if (emphasized) 1.4.dp else 1.dp, edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = if (compactLayout) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                color = YuJianColors.DeepLakeBlue,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (compactLayout) 16.dp else 18.dp),
                tint = YuJianColors.DeepLakeBlue,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = YuJianColors.DeepLakeBlue,
            fontSize = if (compactLayout) 13.sp else 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    val initialValue = remember(title, value) { resultNumericEditorInitialValue(value) }
    var text by remember(title, value) { mutableStateOf(initialValue) }
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
                if (initialValue.text.isNotBlank()) {
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
