package com.yujian.ai.ui.screens

import com.yujian.ai.recognition.result.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.ui.recognition.reveal.RecognitionResultSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionGlyphReadiness
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffContract
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffEvent
import com.yujian.ai.ui.recognition.reveal.RecognitionResultInfoRenderReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultStableRenderReceipt
import com.yujian.ai.ui.recognition.reveal.RecognitionResultStableRenderContract
import com.yujian.ai.ui.recognition.reveal.RECOGNITION_RESULT_HANDOFF_EVENT_CONSUMER
import com.yujian.ai.ui.recognition.reveal.RECOGNITION_RESULT_HANDOFF_EVENT_PRODUCER
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffReceipt
import com.yujian.ai.ui.recognition.reveal.ResultHandoffSubstage
import com.yujian.ai.ui.recognition.reveal.ResultStableReadiness
import com.yujian.ai.ui.recognition.diagnostics.RecognitionEvidenceZipExporter
import com.yujian.ai.ui.recognition.diagnostics.RecognitionSessionEvidenceRecorder
import com.yujian.ai.ui.recognition.diagnostics.recognitionEvidenceWindow
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.CatchSaveMetadataParser
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.model.DemoData
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.designsystem.components.YuJianBackCenterTitleTopBar
import com.yujian.ai.ui.adaptive.rememberAdaptiveLayoutProfile
import com.yujian.ai.ui.adaptive.rememberSafeDrawingInsets
import com.yujian.ai.ui.identify.RecognitionUiState
import com.yujian.ai.ui.identify.recognitionFeedbackType
import com.yujian.ai.ui.identify.resolveRecognitionResultState
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealClock
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import com.yujian.ai.ui.waterlight.core.RecognitionProgressDisplayMode
import com.yujian.ai.ui.waterlight.core.DIAGNOSTIC_STEP_DURATION_MS
import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneFrame
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.SceneGeometryState
import com.yujian.ai.ui.waterlight.core.SceneMotionCoordinator
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolver
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolution
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHeroSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.resolveRecognitionScientificName
import com.yujian.ai.ui.recognition.result.RecognitionPlaceRecentStore
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.RecognitionResultVisualState
import com.yujian.ai.ui.recognition.result.resolveCurrentRecognitionPlace
import com.yujian.ai.ui.imagery.FishBoxCoordinateSpace
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.recognition.result.SpeciesSelectorEntryContext
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


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
    saveRequiresLogin: Boolean = false,
    onLoginRequired: () -> Unit = {},
    availableSpecies: List<FishGuideItem> = emptyList(),
    speciesCoverUrlFor: (String?) -> String? = { it },
    revealHandoff: RecognitionRevealHandoff? = null,
    resultSnapshot: RecognitionResultSnapshot? = null,
    initialResultHandoffEventSequence: Long = 0L,
    initialResultHandoffEventTimestampMs: Long = 0L,
    onResultHandoffEvent: (RecognitionResultHandoffEvent) -> RecognitionResultHandoffReceipt = {
        RecognitionResultHandoffReceipt(false, "HANDOFF_EVENT_CONSUMER_UNAVAILABLE")
    },
    recognitionEvidenceRecorder: RecognitionSessionEvidenceRecorder? = null,
    /** Injects one semantic frame in tests; production reads the shared scene controller. */
    sceneFrameOverrideForTest: RecognitionSceneFrame? = null,
    revealAlreadyPlayed: Boolean = false,
    onClaimReveal: (RecognitionRevealKey) -> Boolean = { true },
    onRevealFinished: (RecognitionRevealKey) -> Unit = {},
    diagnosticPlaybackController: SceneMotionCoordinator? = null,
    diagnosticDisplayMode: RecognitionProgressDisplayMode = RecognitionProgressDisplayMode.NORMAL_PROGRESS,
    onDiagnosticDisplayModeChange: (RecognitionProgressDisplayMode) -> Unit = {},
    onDiagnosticReturnToProcessing: () -> Unit = {},
    revealMotionPolicyOverride: RecognitionRevealMotionPolicy? = null,
    revealGeometryResolutionObserver: ((RecognitionRevealGeometryResolution?) -> Unit)? = null,
    onPlaybackStart: (RecognitionRevealKey) -> Boolean = { true },
    onPlaybackProgress: (RecognitionRevealKey, RecognitionSceneStage, Float) -> Unit = { _, _, _ -> },
    onPlaybackInterrupted: (RecognitionRevealKey) -> Unit = {},
    onSave: (CatchSaveDraft, FeedbackDraft, RecognitionSaveDestination) -> Unit,
) {
    var rootBounds by remember { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var heroSnapshot by remember(image?.imageId) { mutableStateOf<RecognitionRevealHeroSnapshot?>(null) }
    var revealTitleBounds by remember(image?.imageId) { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var resultInfoLayoutReady by remember(revealHandoff?.key) { mutableStateOf(false) }
    var resultInfoEntranceStartedAtUptimeMs by remember(revealHandoff?.key) { mutableStateOf<Long?>(null) }
    var resultInfoRenderReceipt by remember(revealHandoff?.key) {
        mutableStateOf<RecognitionResultInfoRenderReceipt?>(null)
    }
    var resultStableRenderReceipt by remember(revealHandoff?.key) {
        mutableStateOf<RecognitionResultStableRenderReceipt?>(null)
    }
    var glyphReadinessState by remember(revealHandoff?.key) {
        mutableStateOf(resultSnapshot?.handoffState?.glyphReadiness ?: RecognitionGlyphReadiness())
    }
    val resultInfoDrawPassSequence = remember(revealHandoff?.key) { AtomicLong(0L) }
    val resultInfoProgressBucket = remember(revealHandoff?.key) { AtomicInteger(-1) }
    val resultInfoReceiptInFlight = remember(revealHandoff?.key) { AtomicBoolean(false) }
    val resultStableDrawPassSequence = remember(revealHandoff?.key) { AtomicLong(0L) }
    val resultStableReceiptInFlight = remember(revealHandoff?.key) { AtomicBoolean(false) }
    var diagnosticUserPaused by remember(revealHandoff?.key) { mutableStateOf(false) }
    val resultFirstPresentedAtMs = remember(image?.imageId) { mutableStateOf<Long?>(null) }
    val revealStartAllowed = remember(image?.imageId) { mutableStateOf(false) }
    var diagnosticNowMs by remember(revealHandoff?.key) { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val resultFirstFrameCallbackPosted = remember(image?.imageId) { AtomicBoolean(false) }
    val resultFrameHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(resultFrameHandler) {
        onDispose { resultFrameHandler.removeCallbacksAndMessages(null) }
    }
    val systemMotionPolicy = rememberRecognitionMotionPolicy()
    val revealMotionPolicy = revealMotionPolicyOverride ?: RecognitionRevealMotionPolicy(
        reduceMotion = systemMotionPolicy.reduceMotion,
        lowPerformance = systemMotionPolicy.lowPerformance,
    )
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
    val resultPageScrollable = RecognitionResultGeometryResolver.usesScrollableResultPage(
        geometry.compactHeightPolicy, adaptiveProfile.accessibilityFontScale,
    )
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
    var selectedKey by rememberSaveable(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_HIGH) prediction.top1.speciesKey else "")
    }
    var selectedName by rememberSaveable(prediction, uiState) {
        mutableStateOf(if (uiState == RecognitionUiState.RESULT_HIGH) prediction.top1.speciesName else "")
    }
    val selectedScientificName = resolveRecognitionScientificName(selectedKey, availableSpecies)
    var selectorVisible by remember(prediction, uiState) { mutableStateOf(false) }
    val selectorEntryContext = when {
        uiState == RecognitionUiState.RESULT_MEDIUM && selectedKey.isBlank() -> SpeciesSelectorEntryContext.MEDIUM_OTHER
        uiState == RecognitionUiState.RESULT_LOW && selectedKey.isBlank() -> SpeciesSelectorEntryContext.LOW_MANUAL
        else -> SpeciesSelectorEntryContext.EDIT_CONFIRMED
    }
    var editField by remember(prediction) { mutableStateOf<ResultEditableField?>(null) }
    var lengthText by rememberSaveable(prediction) { mutableStateOf("") }
    var weightText by rememberSaveable(prediction) { mutableStateOf("") }
    var locationText by rememberSaveable(prediction) { mutableStateOf("") }
    var storyText by rememberSaveable(prediction) { mutableStateOf("") }
    var saveRequested by remember(prediction) { mutableStateOf(false) }
    var metadataValidationError by remember(prediction) { mutableStateOf<String?>(null) }
    val clientRecordId = rememberSaveable(prediction) { java.util.UUID.randomUUID().toString() }
    var requestedSaveDestination by remember(prediction) { mutableStateOf<RecognitionSaveDestination?>(null) }
    var lastSaveDestination by rememberSaveable(prediction) { mutableStateOf<RecognitionSaveDestination?>(null) }
    var pendingSaveDestination by remember(prediction) { mutableStateOf<RecognitionSaveDestination?>(null) }
    var discardConfirmationVisible by remember(prediction) { mutableStateOf(false) }
    var pendingExitAction by remember(prediction) { mutableStateOf<PendingExitAction?>(null) }
    var purposeVisible by remember(prediction) { mutableStateOf(false) }
    var resolvingLocation by remember(prediction) { mutableStateOf(false) }
    var locationError by remember(prediction) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var evidenceExportStatus by remember(revealHandoff?.key) { mutableStateOf("READY") }
    var evidenceExportError by remember(revealHandoff?.key) { mutableStateOf<String?>(null) }
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
    val currentTime = remember(image?.imageId, image?.captureTimestampEpochMs) {
        image?.let {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .format(Date(it.captureTimestampEpochMs))
        }.orEmpty()
    }
    LaunchedEffect(saving, saveError) {
        if (!saving && saveRequested) {
            saveRequested = false
            requestedSaveDestination = null
        }
    }
    // Production handoffs carry the one completed Classifier × SlimSAM decision.
    // Older isolated Result previews without that field keep their existing preview behavior.
    val revealRuntime = RecognitionResultRevealCoordinator(
        RecognitionResultRevealCoordinatorInput(
            image = image,
            prediction = prediction,
            productionResult = productionResult,
            uiState = uiState,
            revealHandoff = revealHandoff,
            resultSnapshot = resultSnapshot,
            initialResultHandoffEventSequence = initialResultHandoffEventSequence,
            initialResultHandoffEventTimestampMs = initialResultHandoffEventTimestampMs,
            onResultHandoffEvent = onResultHandoffEvent,
            evidenceRecorder = recognitionEvidenceRecorder,
            sceneFrameOverrideForTest = sceneFrameOverrideForTest,
            revealAlreadyPlayed = revealAlreadyPlayed,
            onClaimReveal = onClaimReveal,
            onRevealFinished = onRevealFinished,
            diagnosticPlaybackController = diagnosticPlaybackController,
            diagnosticDisplayMode = diagnosticDisplayMode,
            onDiagnosticDisplayModeChange = onDiagnosticDisplayModeChange,
            revealMotionPolicy = revealMotionPolicy,
            revealGeometryResolutionObserver = revealGeometryResolutionObserver,
            onPlaybackStart = onPlaybackStart,
            onPlaybackProgress = onPlaybackProgress,
            onPlaybackInterrupted = onPlaybackInterrupted,
            rootBounds = rootBounds,
            heroSnapshot = heroSnapshot,
            revealTitleBounds = revealTitleBounds,
            resultInfoLayoutReady = resultInfoLayoutReady,
            resultInfoEntranceStartedAtUptimeMs = resultInfoEntranceStartedAtUptimeMs,
            resultInfoRenderReceipt = resultInfoRenderReceipt,
            resultStableRenderReceipt = resultStableRenderReceipt,
            resultFirstPresentedAtMs = resultFirstPresentedAtMs.value,
            revealStartAllowed = revealStartAllowed.value,
            onRevealStartAllowed = { revealStartAllowed.value = it },
            diagnosticNowMs = diagnosticNowMs,
            glyphReadinessState = glyphReadinessState,
            selectedSpeciesKey = selectedKey,
            selectedSpeciesName = selectedName,
            onGlyphReadinessChanged = { glyphReadinessState = it },
            onDiagnosticNowMs = { diagnosticNowMs = it },
            onResultInfoEntranceStarted = { resultInfoEntranceStartedAtUptimeMs = it },
        ),
    )
    val bbox = revealRuntime.bbox
    val primaryFishAmbiguous = revealRuntime.primaryFishAmbiguous
    val photoTransitionGeometry = revealRuntime.photoTransitionGeometry
    val revealGeometry = revealRuntime.revealGeometry
    val visualHandoff = revealRuntime.visualHandoff
    val revealPhotoIdentityMatches = revealRuntime.revealPhotoIdentityMatches
    val revealStartReady = revealRuntime.revealStartReady
    val resultGeometryReady = revealRuntime.resultGeometryReady
    val diagnosticUiAvailable = revealRuntime.diagnosticUiAvailable
    val resultSceneIdentity = revealRuntime.resultSceneIdentity
    val resultSceneStage = revealRuntime.resultSceneStage
    val resultGeometryVersion = revealRuntime.resultGeometryVersion
    val resultHandoffEventSequence = revealRuntime.resultHandoffEventSequence
    val diagnosticReferenceMs = revealRuntime.diagnosticReferenceMs
    val diagnosticPreviewElapsedMs = revealRuntime.diagnosticPreviewElapsedMs
    val diagnosticPreviewCompleted = revealRuntime.diagnosticPreviewCompleted
    val diagnosticPaused = revealRuntime.diagnosticPaused
    val diagnosticFrame = revealRuntime.diagnosticFrame
    val latestEvidenceResultFrame = revealRuntime.latestEvidenceResultFrame
    val diagnosticStage = revealRuntime.diagnosticStage
    val resultHandoffMotionActive = revealRuntime.resultHandoffMotionActive
    val resultSceneHandoffAccepted = revealRuntime.resultSceneHandoffAccepted
    val resultInteractionsEnabled = revealRuntime.resultInteractionsEnabled
    val resultContentAlpha = revealRuntime.resultContentAlpha
    val resultInfoEntranceProgress = revealRuntime.resultInfoEntranceProgress
    val resultInfoAlpha = revealRuntime.resultInfoAlpha
    val resultInfoTranslationY = revealRuntime.resultInfoTranslationY
    val revealPresentationReady = revealRuntime.revealPresentationReady
    val presentedHandoff = revealRuntime.presentedHandoff
    val revealFrameState = revealRuntime.revealFrameState
    val opticalResidueStatus = revealRuntime.opticalResidueStatus
    val resultSceneStableStatus = revealRuntime.resultSceneStableStatus

    fun save(destination: RecognitionSaveDestination) {
        if (saving || saveRequested) return
        if (selectedKey.isBlank()) {
            pendingSaveDestination = destination
            selectorVisible = true
            return
        }
        val metadata = try {
            CatchSaveMetadataParser.parse(lengthText, weightText, locationText, storyText)
        } catch (error: IllegalArgumentException) {
            metadataValidationError = error.message ?: "鱼获数据格式不正确，请检查后重试"
            return
        }
        metadataValidationError = null
        saveRequested = true
        requestedSaveDestination = destination
        lastSaveDestination = destination
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
            userNote = "ui_state=${uiState.name};has_length=${metadata.lengthCm != null};has_weight=${metadata.weightKg != null};has_location=${metadata.location != null};has_story=${metadata.story != null}",
        )
        val classifier = JSONObject()
            .put("ui_state", uiState.name).put("model_version", prediction.modelVersion)
            .put("prediction_species", prediction.top1.speciesKey).put("confidence", prediction.top1.confidence.toDouble())
            .put("user_selected_species", selectedKey)
            .put("length_cm", metadata.lengthCm ?: JSONObject.NULL)
            .put("weight_kg", metadata.weightKg ?: JSONObject.NULL)
            .put("location", metadata.location ?: JSONObject.NULL)
            .put("story", metadata.story ?: JSONObject.NULL)
            .put("created_at", currentTime)
        onSave(
            CatchSaveDraft(
                speciesId = selectedKey,
                speciesName = selectedName,
                confidence = prediction.top1.confidence,
                modelVersion = prediction.modelVersion,
                detectorResult = detector,
                classifierResult = classifier,
                metadata = metadata,
                clientRecordId = clientRecordId,
                capturedAt = currentTime,
            ),
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

    val inlineErrorAction: (() -> Unit)? = when {
        saveRequiresLogin -> onLoginRequired
        !saveError.isNullOrBlank() -> ({ lastSaveDestination?.let { save(it) } })
        else -> null
    }
    val inlineErrorActionLabel = when {
        saveRequiresLogin -> "重新登录"
        !saveError.isNullOrBlank() -> "重试保存"
        else -> null
    }
    val safeInlineErrorMessage = when {
        saveRequiresLogin -> "登录已过期，请重新登录后继续保存。当前鱼获草稿仍保留。"
        !saveError.isNullOrBlank() -> "保存鱼获失败，请重试"
        else -> metadataValidationError
    }

    val baselineSpeciesKey = if (uiState == RecognitionUiState.RESULT_HIGH) prediction.top1.speciesKey else ""
    val needsDiscardConfirmation = recognitionRequiresDiscardConfirmation(
        speciesChanged = selectedKey != baselineSpeciesKey,
        length = lengthText,
        weight = weightText,
        location = locationText,
        story = storyText,
        saveError = saveError,
        saveRequested = saveRequested,
        saving = saving,
    )

    fun requestExit(action: PendingExitAction) {
        if (saving || saveRequested) return
        if (needsDiscardConfirmation) {
            pendingExitAction = action
            discardConfirmationVisible = true
        } else if (action == PendingExitAction.RETAKE) {
            onRetry()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        when {
            selectorVisible -> {
                pendingSaveDestination = null
                selectorVisible = false
            }
            editField != null -> editField = null
            purposeVisible -> purposeVisible = false
            saving || saveRequested -> Unit
            else -> requestExit(PendingExitAction.BACK)
        }
    }

    val saveBusy = saving || saveRequested
    val activeLoadingDestination = requestedSaveDestination.takeIf { saveRequested }
    Box(
        Modifier.fillMaxSize().drawWithContent {
            drawContent()
            if (resultSceneStableStatus?.ready == true && resultGeometryVersion > 0L &&
                resultStableRenderReceipt?.geometryVersion != resultGeometryVersion &&
                resultStableReceiptInFlight.compareAndSet(false, true)
            ) {
                val frame = latestEvidenceResultFrame.value
                val receipt = RecognitionResultStableRenderReceipt(
                    identity = resultSceneIdentity,
                    resultIdentity = prediction.top1.speciesKey,
                    geometryVersion = resultGeometryVersion,
                    completedPrerequisites = resultSnapshot?.handoffState?.completedSubstages.orEmpty(),
                    photoProgress = revealFrameState.value.photoProgress,
                    resultInfoProgress = resultInfoEntranceProgress,
                    opticalResidueComplete = opticalResidueStatus?.complete == true,
                    layoutWidthPx = size.width.toInt(),
                    layoutHeightPx = size.height.toInt(),
                    contentDrawn = true,
                    drawPassSequence = resultStableDrawPassSequence.incrementAndGet(),
                )
                resultFrameHandler.post {
                    if (frame?.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
                        RecognitionResultStableRenderContract.isComplete(
                            expectedIdentity = resultSceneIdentity,
                            expectedResultIdentity = prediction.top1.speciesKey,
                            expectedGeometryVersion = resultGeometryVersion,
                            receipt = receipt,
                        )
                    ) {
                        resultStableRenderReceipt = receipt
                        recognitionEvidenceRecorder?.recordResultInfoEntranceCheckpoint(
                            checkpoint = "RESULT_SCENE_STABLE_RENDER_COMPLETED",
                            identity = receipt.identity,
                            resultIdentity = receipt.resultIdentity,
                            stage = frame.stage,
                            stageProgress = frame.stageProgress,
                            entranceProgress = receipt.resultInfoProgress,
                            geometryVersion = receipt.geometryVersion,
                            drawPassSequence = receipt.drawPassSequence,
                            renderStatus = "DRAW_RECEIPT_READY",
                        )
                    }
                    resultStableReceiptInFlight.set(false)
                }
            }
            val evidenceFrame = latestEvidenceResultFrame.value
            if (evidenceFrame != null) {
                recognitionEvidenceRecorder?.onFramePresented(
                    window = recognitionEvidenceWindow(context),
                    frame = evidenceFrame,
                    latestFrame = { latestEvidenceResultFrame.value },
                    resultSceneStable = evidenceFrame.resultSceneStable,
                    allowPixelCopyCapture = BuildConfig.DEBUG,
                )
            }
            val handoff = revealHandoff
            val frameImageId = image?.imageId
            if (resultFirstFrameCallbackPosted.compareAndSet(false, true)) {
                resultFrameHandler.post {
                    if (latestImageId.value == frameImageId && resultFirstPresentedAtMs.value == null) {
                        val presentedAtMs = SystemClock.uptimeMillis()
                        resultFirstPresentedAtMs.value = presentedAtMs
                        latestEvidenceResultFrame.value?.let { frame ->
                            recognitionEvidenceRecorder?.recordResultFirstPresented(
                                sceneElapsedMs = frame.sceneElapsedMs,
                                geometryVersion = frame.geometryVersion,
                                resultIdentity = resultSnapshot?.glyphSpeciesKey ?: prediction.top1.speciesKey,
                            )
                        }
                        if (handoff == null) {
                            revealStartAllowed.value = false
                            Log.i(
                                "RecognitionReveal",
                                "RESULT_FIRST_PRESENTED image=${frameImageId.orEmpty()} uptimeMs=$presentedAtMs rr09Started=false reason=HANDOFF_NOT_READY",
                            )
                        } else {
                            val sameHandoff = latestRevealKey.value == handoff.key
                            val eligible = visualHandoff?.eligibility == RecognitionRevealEligibility.FULL_REVEAL ||
                                visualHandoff?.eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY
                            if (sameHandoff && eligible) {
                                Log.i(
                                    "RecognitionReveal",
                                    "RESULT_FIRST_PRESENTED session=${handoff.key.recognitionSessionId} attempt=${handoff.key.recognitionAttemptId} image=${handoff.imageId} photoSha256=${handoff.photoSourceIdentity.substringAfterLast("sha256=")} uptimeMs=$presentedAtMs geometryReady=$resultGeometryReady hero=${heroSnapshot?.boundsInWindow} crop=${heroSnapshot?.cropRect} root=${rootBounds}",
                                )
                            } else {
                                val reason = when {
                                    !sameHandoff -> "HANDOFF_CHANGED_BEFORE_FIRST_FRAME"
                                    revealAlreadyPlayed -> "ALREADY_PLAYED"
                                    !eligible -> "INELIGIBLE"
                                    revealMotionPolicy.reduceMotion -> "REDUCE_MOTION"
                                    else -> "STATIC_OR_INELIGIBLE"
                                }
                                Log.i(
                                    "RecognitionReveal",
                                    "RESULT_FIRST_PRESENTED session=${handoff.key.recognitionSessionId} attempt=${handoff.key.recognitionAttemptId} image=${handoff.imageId} uptimeMs=$presentedAtMs rr09Started=false reason=$reason",
                                )
                                latestRevealFinished.value.invoke(handoff.key)
                            }
                        }
                    }
                }
            }
        }.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            rootBounds = RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        },
    ) {
        RecognitionResultScreenContent(
            model = RecognitionResultScreenContentModel(
                title = if (resultHandoffMotionActive && !resultSceneHandoffAccepted) "识别中" else "识别结果",
                backEnabled = !saving && !saveRequested,
                safeInsets = safeInsets,
                resultPageScrollable = resultPageScrollable,
                resultContentAlpha = resultContentAlpha,
                heroBitmap = image?.bitmap?.asImageBitmap(),
                heroBoundingBox = bbox,
                heroPrimaryAmbiguous = primaryFishAmbiguous,
                heroWidthDp = geometry.heroWidthDp,
                heroHeightDp = geometry.heroHeightDp,
                heroEvidenceFirst = uiState == RecognitionUiState.ERROR_NO_FISH ||
                    uiState == RecognitionUiState.ERROR_IMAGE_QUALITY,
                revealFrameState = revealFrameState,
                sharedPhotoGeometryReady = photoTransitionGeometry != null && revealPresentationReady,
                holdPhotoUntilReady = heroSnapshot?.renderReady == false,
                decisionContent = RecognitionResultDecisionContentModel(
                    uiState = uiState,
                    geometry = geometry,
                    adaptiveProfile = adaptiveProfile,
                    candidateFontScale = candidateFontScale,
                    revealFrameState = revealFrameState,
                    resultInteractionsEnabled = resultInteractionsEnabled,
                    revealAlreadyPlayed = revealAlreadyPlayed,
                    revealHandoff = revealHandoff,
                    resultSceneIdentity = resultSceneIdentity,
                    prediction = prediction,
                    resultSnapshot = resultSnapshot,
                    glyphReadinessState = glyphReadinessState,
                    diagnosticPlaybackController = diagnosticPlaybackController,
                    selectedKey = selectedKey,
                    selectedName = selectedName,
                    selectedScientificName = selectedScientificName,
                    resultInfoAlpha = resultInfoAlpha,
                    resultInfoTranslationY = resultInfoTranslationY,
                    resultInfoDrawPassSequence = resultInfoDrawPassSequence,
                    diagnosticFrame = diagnosticFrame,
                    resultSceneHandoffAccepted = resultSceneHandoffAccepted,
                    resultGeometryReady = resultGeometryReady,
                    resultInfoLayoutReady = resultInfoLayoutReady,
                    resultInfoEntranceProgress = resultInfoEntranceProgress,
                    resultInfoProgressBucket = resultInfoProgressBucket,
                    resultInfoRenderReceipt = resultInfoRenderReceipt,
                    resultGeometryVersion = resultGeometryVersion,
                    resultInfoReceiptInFlight = resultInfoReceiptInFlight,
                    resultFrameHandler = resultFrameHandler,
                    recognitionEvidenceRecorder = recognitionEvidenceRecorder,
                    lengthText = lengthText,
                    weightText = weightText,
                    locationText = locationText,
                    resolvingLocation = resolvingLocation,
                    storyText = storyText,
                    safeInlineErrorMessage = safeInlineErrorMessage,
                    inlineErrorActionLabel = inlineErrorActionLabel,
                    inlineErrorAction = inlineErrorAction,
                    saveBusy = saveBusy,
                    activeLoadingDestination = activeLoadingDestination,
                    compactResultActions = compactResultActions,
                    candidates = candidates,
                    speciesImages = speciesImages,
                ),
                opticalLayer = RecognitionResultOpticalLayerModel(
                    image = image,
                    diagnosticFrame = diagnosticFrame,
                    sceneFrameOverrideForTest = sceneFrameOverrideForTest,
                    photoTransitionGeometry = photoTransitionGeometry,
                    presentedHandoff = presentedHandoff,
                    revealFrameState = revealFrameState,
                    revealMotionPolicy = revealMotionPolicy,
                    resultSceneIdentity = resultSceneIdentity,
                    revealGeometry = revealGeometry,
                    diagnosticPlaybackController = diagnosticPlaybackController,
                    recognitionEvidenceRecorder = recognitionEvidenceRecorder,
                    revealPresentationReady = revealPresentationReady,
                    heroRenderReady = heroSnapshot?.renderReady == true,
                    diagnosticUiAvailable = diagnosticUiAvailable,
                    diagnosticStage = diagnosticStage,
                    diagnosticReferenceMs = diagnosticReferenceMs,
                    diagnosticPreviewElapsedMs = diagnosticPreviewElapsedMs,
                    diagnosticPreviewCompleted = diagnosticPreviewCompleted,
                    diagnosticPaused = diagnosticPaused,
                    revealStartReady = revealStartReady,
                    diagnosticDisplayMode = diagnosticDisplayMode,
                    visualHandoff = visualHandoff,
                    productionResult = productionResult,
                    revealHandoff = revealHandoff,
                    subjectResult = subjectResult,
                ),
                evidenceExportEnabled = BuildConfig.DEBUG && recognitionEvidenceRecorder != null,
                evidenceExportStatus = evidenceExportStatus,
                evidenceExportError = evidenceExportError,
            ),
            actions = RecognitionResultScreenContentActions(
                onBack = { requestExit(PendingExitAction.BACK) },
                onHeroSnapshot = { bounds, cropRect, ready ->
                    heroSnapshot = RecognitionRevealHeroSnapshot(bounds, cropRect, ready)
                },
                decisionContent = RecognitionResultDecisionContentActions(
                    onTitleBounds = { revealTitleBounds = it },
                    onGlyphReadinessChanged = { glyphReadinessState = it },
                    onSelectCandidate = { candidate ->
                        selectedKey = candidate.speciesKey
                        selectedName = candidate.speciesName
                    },
                    onStoryTextChange = { storyText = it.takeUnicodeCodePoints(300) },
                    onFieldChange = { editField = it },
                    onInfoLayoutReady = { resultInfoLayoutReady = it },
                    onInfoRenderReceipt = { resultInfoRenderReceipt = it },
                    openSpeciesSelector = ::openSpeciesSelector,
                    save = ::save,
                    requestExit = ::requestExit,
                ),
                opticalLayer = RecognitionResultOpticalLayerActions(
                    setDiagnosticUserPaused = { diagnosticUserPaused = it },
                    setDiagnosticNowMs = { diagnosticNowMs = it },
                    onReturnToProcessing = onDiagnosticReturnToProcessing,
                    onDiagnosticDisplayModeChange = onDiagnosticDisplayModeChange,
                ),
                onExportDiagnostics = {
                    if (evidenceExportStatus != "EXPORTING") {
                        evidenceExportStatus = "EXPORTING"
                        evidenceExportError = null
                        scope.launch {
                            try {
                                val recorder = requireNotNull(recognitionEvidenceRecorder)
                                val exported = RecognitionEvidenceZipExporter.export(context, recorder)
                                val shareOutcome = RecognitionEvidenceZipExporter.share(context, exported)
                                val kb = (exported.sizeBytes / 1024L).coerceAtLeast(1L)
                                evidenceExportStatus = if (shareOutcome.chooserLaunched) {
                                    "EXPORTED · 导出成功 · ${exported.integrityStatus} · ${kb}KB · ${exported.sha256.take(12)}"
                                } else {
                                    "EXPORTED · 已保存(${shareOutcome.errorReason ?: "NO_SHARE_APP"}) · ${exported.file.name} · ${exported.sha256.take(12)}"
                                }
                                Log.i(
                                    "RecognitionEvidenceExport",
                                    "status=EXPORTED sha256=${exported.sha256} sizeBytes=${exported.sizeBytes} " +
                                        "sourceSha=${exported.sourceSha} integrity=${exported.integrityStatus} " +
                                        "chooserLaunched=${shareOutcome.chooserLaunched} degraded=${exported.degradedReasons}",
                                )
                            } catch (failure: Throwable) {
                                if (failure is kotlinx.coroutines.CancellationException) throw failure
                                evidenceExportStatus = "FAILED"
                                evidenceExportError = failure.message ?: failure.javaClass.simpleName
                                Log.e("RecognitionEvidenceExport", "status=FAILED", failure)
                            }
                        }
                    }
                },
            ),
        )
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

    if (discardConfirmationVisible) {
        val exitingByRetake = pendingExitAction == PendingExitAction.RETAKE
        AlertDialog(
            modifier = Modifier.testTag("recognition-discard-confirmation"),
            onDismissRequest = {
                discardConfirmationVisible = false
                pendingExitAction = null
            },
            title = { Text("放弃本次修改？") },
            text = { Text("尚未保存的鱼种与鱼获信息会被清除。") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("recognition-discard-confirm"),
                    onClick = {
                        val action = pendingExitAction
                        discardConfirmationVisible = false
                        pendingExitAction = null
                        if (action == PendingExitAction.RETAKE) onRetry() else onBack()
                    },
                ) { Text(if (exitingByRetake) "放弃并重新拍摄" else "放弃并返回拍摄") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("recognition-discard-continue-editing"),
                    onClick = {
                        discardConfirmationVisible = false
                        pendingExitAction = null
                    },
                ) { Text("继续编辑") }
            },
        )
    }


}
