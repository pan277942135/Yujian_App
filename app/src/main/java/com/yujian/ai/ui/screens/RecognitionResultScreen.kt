package com.yujian.ai.ui.screens

import com.yujian.ai.BuildConfig
import com.yujian.ai.ui.recognition.RecognitionDiagnosticExport
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.FishInputStatus
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.ui.recognition.reveal.RecognitionGeometryTrustState
import com.yujian.ai.catches.CatchSaveDraft
import com.yujian.ai.catches.CatchSaveMetadataParser
import com.yujian.ai.feedback.FeedbackDraft
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.model.DemoData
import com.yujian.ai.model.RecognitionCandidate
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.FishPhotoBitmapImage
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
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.imagery.PhotoCropRect
import com.yujian.ai.ui.recognition.rememberRecognitionMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealClock
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealEligibility
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealDecisionState
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealFrame
import com.yujian.ai.ui.recognition.reveal.RecognitionSpeciesGlyphIdentity
import com.yujian.ai.ui.recognition.reveal.recognitionOrbGlyphTransition
import com.yujian.ai.ui.recognition.reveal.recognitionSpeciesGlyphIdentity
import com.yujian.ai.ai.RecognitionPhase
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightEnergy
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
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
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolver
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealGeometryResolution
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHandoff
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealHeroSnapshot
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealKey
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealMotionPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealOpticalOverlay
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPhotoGeometry
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPhase
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealPolicy
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealTitleMode
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.reveal.RecognitionResultCommitIdentity
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffCommitGuard
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffTransition
import com.yujian.ai.ui.recognition.reveal.RecognitionResultHandoffSubstep
import com.yujian.ai.ui.recognition.RecognitionStepDiagnosticControls
import com.yujian.ai.ui.recognition.reveal.resolveRecognitionScientificName
import com.yujian.ai.ui.recognition.result.RecognitionPlace
import com.yujian.ai.ui.recognition.result.RecognitionPlaceRecentStore
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.recognition.result.RecognitionResultInputValidation
import com.yujian.ai.ui.recognition.result.RecognitionResultVisualState
import com.yujian.ai.ui.recognition.result.resultNumericEditorInitialValue
import com.yujian.ai.ui.recognition.result.resultSpeciesDisplayName
import com.yujian.ai.ui.recognition.result.resolveCurrentRecognitionPlace
import com.yujian.ai.ui.imagery.FishBoxCoordinateSpace
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.recognition.result.SpeciesSelectorEntryContext
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
    saveRequiresLogin: Boolean = false,
    onLoginRequired: () -> Unit = {},
    availableSpecies: List<FishGuideItem> = emptyList(),
    speciesCoverUrlFor: (String?) -> String? = { it },
    revealHandoff: RecognitionRevealHandoff? = null,
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
    onSave: (CatchSaveDraft, FeedbackDraft, RecognitionSaveDestination) -> Unit,
) {
    var rootBounds by remember { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var heroSnapshot by remember(image?.imageId) { mutableStateOf<RecognitionRevealHeroSnapshot?>(null) }
    var revealTitleBounds by remember(image?.imageId) { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var diagnosticUserPaused by remember(revealHandoff?.key) { mutableStateOf(false) }
    val resultFirstPresentedAtMs = remember(image?.imageId) { mutableStateOf<Long?>(null) }
    val revealStartAllowed = remember(image?.imageId) { mutableStateOf(false) }
    var diagnosticNowMs by remember(revealHandoff?.key) { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val resultFirstFrameCallbackPosted = remember(image?.imageId) { AtomicBoolean(false) }
    val resultFrameHandler = remember { Handler(Looper.getMainLooper()) }
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
    val resultAssessment = productionResult?.assessment
    val bbox = resultAssessment?.primary?.let { detection ->
        FishPhotoBoundingBox(
            box = detection.box,
            confidence = detection.confidence,
            coordinateSpace = FishBoxCoordinateSpace.DISPLAY_ORIENTED,
            version = "${productionResult?.detectorRun?.modelVersion.orEmpty()}-${resultAssessment.status.wireName}",
        )
    }
    val primaryFishAmbiguous = resultAssessment?.let {
        it.status == FishInputStatus.MULTIPLE_FISH || it.strongDetections.size > 1
    } == true

    // Production handoffs carry the one completed Classifier × SlimSAM decision.
    // Older isolated Result previews without that field keep their existing preview behavior.
    val decisionHandoff = when (revealHandoff?.revealDecision) {
        RecognitionRevealDecisionState.FISH_FOCUS_READY -> revealHandoff
        RecognitionRevealDecisionState.ORB_ONLY_READY -> revealHandoff?.let { handoff ->
            if (handoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL) {
                handoff.copy(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY)
            } else handoff
        }
        null -> revealHandoff
        else -> null
    }

    val photoTransitionGeometry = remember(decisionHandoff, heroSnapshot, rootBounds) {
        if (decisionHandoff == null || heroSnapshot == null || rootBounds == null) null
        else RecognitionRevealGeometryResolver.resolvePhoto(
            handoff = decisionHandoff,
            hero = requireNotNull(heroSnapshot),
            rootBoundsInWindow = requireNotNull(rootBounds),
        )
    }
    val revealGeometryResolution = remember(decisionHandoff, heroSnapshot, revealTitleBounds, rootBounds) {
        if (decisionHandoff == null) null
        else when {
            rootBounds == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_ROOT_PENDING")
            heroSnapshot == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_HERO_PENDING")
            revealTitleBounds == null -> RecognitionRevealGeometryResolution(null, "LAYOUT_TITLE_PENDING")
            else -> {
                val root = requireNotNull(rootBounds)
                val hero = requireNotNull(heroSnapshot)
                val title = requireNotNull(revealTitleBounds)
                val primary = RecognitionRevealGeometryResolver.resolveDetailed(decisionHandoff, hero, title, root)
                val hasTrustedContour = primary.geometry?.sourceContourPath?.isEmpty() == false
                if (decisionHandoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL && !hasTrustedContour) {
                    val degraded = RecognitionRevealGeometryResolver.resolveDetailed(
                        handoff = decisionHandoff.copy(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY),
                        hero = hero,
                        titleBoundsInWindow = title,
                        rootBoundsInWindow = root,
                    )
                    if (degraded.geometry != null) degraded.copy(
                        failureReason = "FULL_REVEAL_DEGRADED:${primary.failureReason ?: "TRUSTED_CONTOUR_EMPTY"}",
                    ) else primary.copy(
                        failureReason = "FULL_REVEAL_DEGRADED:${primary.failureReason ?: "TRUSTED_CONTOUR_EMPTY"};" +
                            "TITLE_ONLY:${degraded.failureReason}",
                    )
                } else primary
            }
        }
    }
    val revealGeometry = revealGeometryResolution?.geometry
    SideEffect {
        if (revealHandoff != null) {
            revealGeometryResolutionObserver?.invoke(revealGeometryResolution)
        }
    }
    val visualHandoff = when {
        decisionHandoff != null &&
            decisionHandoff.eligibility == RecognitionRevealEligibility.FULL_REVEAL &&
            revealGeometry?.sourceContourPath?.isEmpty() != false ->
            decisionHandoff.copy(eligibility = RecognitionRevealEligibility.TITLE_FADE_ONLY)
        else -> decisionHandoff
    }
    val revealPhotoIdentityMatches = revealHandoff != null && image != null &&
        image.imageId == revealHandoff.imageId &&
        image.bitmap.width == revealHandoff.normalizedSourceWidth &&
        image.bitmap.height == revealHandoff.normalizedSourceHeight &&
        image.photoSha256.isNotBlank() &&
        revealHandoff.photoSourceIdentity.endsWith("|sha256=${image.photoSha256}") &&
        prediction.top1.speciesKey == revealHandoff.resultIdentity
    val revealStartReady = RecognitionRevealPolicy.canStartOnFirstResultFrame(
        eligibility = visualHandoff?.eligibility ?: RecognitionRevealEligibility.STATIC_RESULT,
        photoIdentityMatches = revealPhotoIdentityMatches,
        photoGeometryReady = photoTransitionGeometry != null,
        heroReady = heroSnapshot?.renderReady == true,
        titleBoundsReady = revealTitleBounds?.isValid() == true,
        revealGeometryReady = revealGeometry != null,
        reduceMotion = revealMotionPolicy.reduceMotion,
    )
    val resultGeometryReady = revealPhotoIdentityMatches && photoTransitionGeometry != null &&
        heroSnapshot?.renderReady == true && revealTitleBounds?.isValid() == true && revealGeometry != null
    val diagnosticUiAvailable = BuildConfig.DEBUG && diagnosticPlaybackController != null
    val diagnosticManualActive = diagnosticUiAvailable &&
        diagnosticPlaybackController?.mode == ScenePlaybackMode.MANUAL
    val resultSceneIdentity = remember(revealHandoff?.key, image?.imageId) {
        RecognitionSceneIdentity(
            sessionId = revealHandoff?.key?.recognitionSessionId.orEmpty(),
            attemptId = revealHandoff?.key?.recognitionAttemptId.orEmpty(),
            imageId = image?.imageId.orEmpty(),
            photoSha256 = image?.photoSha256,
            photoContentVersion = image?.bitmap?.generationId?.toLong()?.coerceAtLeast(0L) ?: 0L,
        )
    }
    val resultSceneStage = sceneFrameOverrideForTest?.stage
        ?: diagnosticPlaybackController?.currentStage
        ?: RecognitionSceneStage.PHOTO_STABLE
    val resultHostBounds = if (resultSceneStage == RecognitionSceneStage.RESIDUE_SETTLE) {
        photoTransitionGeometry?.heroPhotoClip
    } else photoTransitionGeometry?.processingPhotoClip
    val resultGeometryVersion = photoTransitionGeometry?.heroSourceTransform?.let { transform ->
        val base = WaterLightGeometryVersion.of(transform)
        resultHostBounds?.let { WaterLightGeometryVersion.withHostBounds(base, it.left, it.top, it.right, it.bottom) } ?: base
    } ?: 0L
    val resultGeometryVersions = remember(photoTransitionGeometry) {
        val photoGeometry = photoTransitionGeometry
        val base = photoGeometry?.heroSourceTransform?.let(WaterLightGeometryVersion::of)
        if (photoGeometry == null || base == null) emptySet() else buildSet {
            photoGeometry.processingPhotoClip.let { bounds ->
                add(WaterLightGeometryVersion.withHostBounds(base, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            photoGeometry.heroPhotoClip.let { bounds ->
                add(WaterLightGeometryVersion.withHostBounds(base, bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
        }
    }
    val diagnosticReferenceMs = diagnosticPlaybackController?.opticalReferenceElapsedMs(diagnosticNowMs)
    val diagnosticPreviewElapsedMs = diagnosticPlaybackController?.previewElapsedMs(diagnosticNowMs) ?: 0L
    val diagnosticPreviewCompleted = diagnosticPlaybackController?.previewCompleted == true
    val diagnosticPaused = diagnosticPlaybackController?.paused == true
    LaunchedEffect(diagnosticPlaybackController, revealHandoff?.key, uiState) {
        val playback = diagnosticPlaybackController ?: return@LaunchedEffect
        if (revealHandoff == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { it }
            val now = SystemClock.uptimeMillis()
            playback.currentSceneStage(
                nowMs = now,
                trustedMask = revealHandoff.geometryTrustState == RecognitionGeometryTrustState.TRUSTED_MASK,
                subjectTerminal = revealHandoff.geometryTrustState != RecognitionGeometryTrustState.MISSING,
                successfulRevealAllowed = uiState == RecognitionUiState.RESULT_HIGH,
            )
            diagnosticNowMs = now
        }
    }
    val diagnosticFrame = if (diagnosticPlaybackController != null && revealHandoff != null && image != null) {
        diagnosticPlaybackController.currentFrame(resultSceneIdentity, diagnosticNowMs, resultGeometryVersion, revealMotionPolicy.reduceMotion)
    } else null
    val diagnosticStage = diagnosticFrame?.stage ?: diagnosticPlaybackController?.currentStage ?: RecognitionSceneStage.ORB_RETURN
    val diagnosticGlyphSubstep = diagnosticFrame?.let {
        recognitionOrbGlyphTransition(it.stage, it.stageProgress)?.substep?.id
    }
    LaunchedEffect(revealHandoff?.key, diagnosticStage, diagnosticDisplayMode, diagnosticPreviewCompleted, diagnosticUiAvailable) {
        if (diagnosticUiAvailable && diagnosticFrame != null) {
            Log.i(
                "RecognitionReveal",
                "DIAGNOSTIC_PRESENT session=${revealHandoff?.key?.recognitionSessionId.orEmpty()} " +
                    "attempt=${revealHandoff?.key?.recognitionAttemptId.orEmpty()} image=${image?.imageId.orEmpty()} " +
                    "sha256=${image?.photoSha256.orEmpty()} stage=${diagnosticStage.ordinal + 1}/13 name=${diagnosticStage.name} " +
                    "reference_ms=${diagnosticFrame.opticalReferenceElapsedMs} preview_ms=$diagnosticPreviewElapsedMs " +
                    "glyph_substep=${diagnosticGlyphSubstep ?: "-"} " +
                    "remaining_ms=${DIAGNOSTIC_STEP_DURATION_MS - diagnosticPreviewElapsedMs} " +
                    "completed=$diagnosticPreviewCompleted paused=$diagnosticPaused " +
                    "geometry_ready=$revealStartReady eligibility=${revealHandoff?.eligibility} " +
                    "mask=${revealHandoff?.geometryTrustState} result=${prediction.top1.speciesKey}",
            )
        }
    }
    fun changeDiagnosticDisplayMode(mode: RecognitionProgressDisplayMode) {
        if (mode == diagnosticDisplayMode) return
        // Switching the two display modes must not seek or restart the shared scene clock.
        onDiagnosticDisplayModeChange(mode)
    }
    var geometryWaitExpired by remember(revealHandoff?.key) { mutableStateOf(false) }
    LaunchedEffect(revealHandoff?.key, resultGeometryReady, visualHandoff?.eligibility) {
        if (visualHandoff?.eligibility in setOf(RecognitionRevealEligibility.FULL_REVEAL, RecognitionRevealEligibility.TITLE_FADE_ONLY) &&
            !resultGeometryReady
        ) {
            delay(350L)
            geometryWaitExpired = true
        }
    }
    val geometryResolutionReason = revealGeometryResolution?.failureReason
    val geometryStillMeasuring = geometryResolutionReason in setOf(
        "LAYOUT_ROOT_PENDING", "LAYOUT_HERO_PENDING", "LAYOUT_TITLE_PENDING",
    )
    // 350ms is an advisory threshold only. Keep the shared scene clock held while
    // Compose measures; only an explicit geometry rejection enters static fallback.
    val geometryInvalid = !resultGeometryReady && geometryResolutionReason != null && !geometryStillMeasuring
    LaunchedEffect(revealHandoff?.key, resultGeometryReady, geometryInvalid, resultGeometryVersions) {
        val playback = diagnosticPlaybackController ?: return@LaunchedEffect
        if (revealHandoff == null) return@LaunchedEffect
        val state = when {
            resultGeometryReady -> SceneGeometryState.READY
            geometryInvalid -> SceneGeometryState.INVALID
            else -> SceneGeometryState.PENDING
        }
        playback.setResultGeometryState(
            state,
            SystemClock.uptimeMillis(),
            resultSceneIdentity,
            geometryVersions = resultGeometryVersions.takeIf { state == SceneGeometryState.READY }.orEmpty(),
        )
        playback.onGlyphAvailable(resultGeometryReady && prediction.top1.speciesName.isNotBlank(), resultSceneIdentity)
        diagnosticNowMs = SystemClock.uptimeMillis()
    }
    DisposableEffect(diagnosticPlaybackController, revealHandoff?.key, resultSceneIdentity) {
        onDispose {
            diagnosticPlaybackController?.setResultGeometryState(
                SceneGeometryState.PENDING,
                SystemClock.uptimeMillis(),
                resultSceneIdentity,
            )
        }
    }
    val latestRevealKey = rememberUpdatedState(revealHandoff?.key)
    val latestImageId = rememberUpdatedState(image?.imageId)
    val latestRevealFinished = rememberUpdatedState(onRevealFinished)
    var revealClaimResolved by remember(revealHandoff?.key) { mutableStateOf(revealAlreadyPlayed) }
    LaunchedEffect(revealHandoff?.key, resultFirstPresentedAtMs.value, revealStartReady, revealAlreadyPlayed) {
        val key = revealHandoff?.key ?: return@LaunchedEffect
        if (resultFirstPresentedAtMs.value != null && revealStartReady && !revealAlreadyPlayed && !revealStartAllowed.value &&
            latestRevealKey.value == key
        ) {
            revealStartAllowed.value = onClaimReveal(key)
            revealClaimResolved = true
        }
    }
    val revealPresentationReady = resultFirstPresentedAtMs.value != null && resultGeometryReady &&
        (revealMotionPolicy.reduceMotion || revealStartAllowed.value || revealAlreadyPlayed)
    val revealPending = visualHandoff?.eligibility in setOf(RecognitionRevealEligibility.FULL_REVEAL, RecognitionRevealEligibility.TITLE_FADE_ONLY) &&
        uiState == RecognitionUiState.RESULT_HIGH && !revealAlreadyPlayed && !geometryInvalid &&
        !(resultFirstPresentedAtMs.value != null && resultGeometryReady &&
            (revealMotionPolicy.reduceMotion || revealClaimResolved))
    val presentedHandoff = visualHandoff.takeIf { revealPresentationReady || revealPending }
    val frameMotionPolicy = if (revealPending && !revealPresentationReady) revealMotionPolicy.copy(reduceMotion = false) else revealMotionPolicy
    val activeRevealSceneFrame = diagnosticFrame ?: sceneFrameOverrideForTest ?: RecognitionSceneCoordinator.frameForStage(
        identity = resultSceneIdentity,
        sceneElapsedMs = 0L,
        opticalReferenceElapsedMs = 0L,
        geometryVersion = resultGeometryVersion,
        stage = RecognitionSceneStage.PHOTO_STABLE,
        stageElapsedMs = 0L,
        stageProgress = 0f,
        playbackMode = ScenePlaybackMode.AUTO,
        reducedMotion = revealMotionPolicy.reduceMotion,
    )
    val resultPhotoIdentityMatches = image != null && revealHandoff != null &&
        revealHandoff.key.recognitionSessionId == resultSceneIdentity.sessionId &&
        revealHandoff.key.recognitionAttemptId == resultSceneIdentity.attemptId &&
        revealHandoff.imageId == image.imageId &&
        image.photoSha256.isNotBlank() &&
        image.photoSha256 == resultSceneIdentity.photoSha256 &&
        revealHandoff.photoSourceIdentity.endsWith("|sha256=${image.photoSha256}") &&
        activeRevealSceneFrame.identity == resultSceneIdentity &&
        activeRevealSceneFrame.geometryVersion in resultGeometryVersions &&
        resultSceneIdentity.photoContentVersion == image.bitmap.generationId.toLong().coerceAtLeast(0L) &&
        photoTransitionGeometry?.sourceWidth == image.bitmap.width &&
        photoTransitionGeometry?.sourceHeight == image.bitmap.height
    val resultInformationReady = uiState == RecognitionUiState.RESULT_HIGH &&
        prediction.top1.speciesName.isNotBlank() && selectedKey == prediction.top1.speciesKey &&
        selectedName == prediction.top1.speciesName
    val dynamicRevealActive = presentedHandoff?.eligibility in setOf(
        RecognitionRevealEligibility.FULL_REVEAL,
        RecognitionRevealEligibility.TITLE_FADE_ONLY,
    ) && !frameMotionPolicy.reduceMotion && !revealAlreadyPlayed
    val resultCommitIdentity = RecognitionResultCommitIdentity(
        sessionId = resultSceneIdentity.sessionId,
        attemptId = resultSceneIdentity.attemptId,
        imageId = resultSceneIdentity.imageId,
        photoSha256 = resultSceneIdentity.photoSha256.orEmpty(),
    )
    val resultHandoffCommitGuard = remember { RecognitionResultHandoffCommitGuard() }
    var resultPhotoHandoffComplete by remember(resultCommitIdentity) { mutableStateOf(false) }
    var resultInformationArrived by remember(resultCommitIdentity) { mutableStateOf(false) }
    LaunchedEffect(
        resultCommitIdentity,
        dynamicRevealActive,
        activeRevealSceneFrame.stage,
        activeRevealSceneFrame.stageProgress,
        resultPhotoIdentityMatches,
        resultGeometryReady,
    ) {
        if (dynamicRevealActive && activeRevealSceneFrame.stage == RecognitionSceneStage.RESIDUE_SETTLE &&
            activeRevealSceneFrame.stageProgress >= 1f && resultPhotoIdentityMatches && resultGeometryReady
        ) {
            // Geometry reaches the Result clip using the shared 450ms parent frame;
            // no child-stage progress window or timer is introduced.
            resultPhotoHandoffComplete = true
        }
    }
    LaunchedEffect(resultCommitIdentity, resultPhotoHandoffComplete, resultInformationReady, resultGeometryReady) {
        if (resultPhotoHandoffComplete && resultGeometryReady && resultInformationReady) {
            // The real Classifier name is released only after the same-photo geometry handoff.
            resultInformationArrived = true
        }
    }
    val resultHandoffProjection = RecognitionResultHandoffTransition.project(
        scene = activeRevealSceneFrame,
        dynamicRevealActive = dynamicRevealActive,
        photoIdentityMatches = resultPhotoIdentityMatches,
        geometryReady = resultGeometryReady,
        resultInformationReady = resultInformationReady,
        photoHandoffComplete = resultPhotoHandoffComplete,
        resultInformationArrived = resultInformationArrived,
    )
    val revealFrameState = remember(
        presentedHandoff,
        frameMotionPolicy,
        activeRevealSceneFrame,
        revealAlreadyPlayed,
        resultPhotoIdentityMatches,
        resultGeometryReady,
        resultInformationReady,
        dynamicRevealActive,
        resultPhotoHandoffComplete,
        resultInformationArrived,
    ) {
        derivedStateOf {
            val revealFrame = RecognitionRevealClock.evaluate(
                scene = activeRevealSceneFrame,
                handoff = presentedHandoff,
                policy = frameMotionPolicy,
                alreadyPlayed = revealAlreadyPlayed,
            )
            val handoff = RecognitionResultHandoffTransition.project(
                scene = activeRevealSceneFrame,
                dynamicRevealActive = dynamicRevealActive,
                photoIdentityMatches = resultPhotoIdentityMatches,
                geometryReady = resultGeometryReady,
                resultInformationReady = resultInformationReady,
                photoHandoffComplete = resultPhotoHandoffComplete,
                resultInformationArrived = resultInformationArrived,
            )
            revealFrame.copy(
                photoProgress = handoff.photoProgress,
                resultHandoffSubstep = handoff.substep,
                resultInformationAlpha = handoff.resultInformationAlpha,
                resultInteractionEnabled = handoff.interactionEnabled,
            )
        }
    }
    LaunchedEffect(revealHandoff?.key, revealFrameState.value.resultHandoffSubstep) {
        val frame = revealFrameState.value
        val substep = frame.resultHandoffSubstep ?: return@LaunchedEffect
        Log.i(
            "RecognitionResultHandoff",
            "HANDOFF_PRESENT session=${resultSceneIdentity.sessionId} attempt=${resultSceneIdentity.attemptId} " +
                "image=${resultSceneIdentity.imageId} photoSha256=${resultSceneIdentity.photoSha256.orEmpty()} " +
                "substep=${substep.id} photoProgress=${frame.photoProgress} infoAlpha=${frame.resultInformationAlpha} " +
                "geometryReady=$resultGeometryReady identityMatches=$resultPhotoIdentityMatches " +
                "sceneStable=${substep == RecognitionResultHandoffSubstep.RESULT_SCENE_STABLE}",
        )
    }

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
            val handoff = revealHandoff
            val frameImageId = image?.imageId
            if (resultFirstFrameCallbackPosted.compareAndSet(false, true)) {
                resultFrameHandler.post {
                    if (latestImageId.value == frameImageId && resultFirstPresentedAtMs.value == null) {
                        val presentedAtMs = SystemClock.uptimeMillis()
                        resultFirstPresentedAtMs.value = presentedAtMs
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
        BgContentSurface()
        Column(Modifier.fillMaxSize()) {
            YuJianBackCenterTitleTopBar(title = "识别结果", onBack = { requestExit(PendingExitAction.BACK) }, backEnabled = !saving && !saveRequested)
            Column(
                Modifier.weight(1f)
                    .widthIn(max = RecognitionResultGeometryResolver.MAX_CONTENT_WIDTH_DP.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
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
                        primaryAmbiguous = primaryFishAmbiguous,
                        widthDp = geometry.heroWidthDp,
                        heightDp = geometry.heroHeightDp,
                        evidenceFirst = uiState == RecognitionUiState.ERROR_NO_FISH ||
                            uiState == RecognitionUiState.ERROR_IMAGE_QUALITY,
                        revealFrameState = revealFrameState,
                        sharedPhotoGeometryReady = photoTransitionGeometry != null && revealPresentationReady,
                        holdPhotoUntilReady = heroSnapshot?.renderReady == false,
                        onHeroSnapshot = { bounds, cropRect, ready ->
                            heroSnapshot = RecognitionRevealHeroSnapshot(bounds, cropRect, ready)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                val sideMargin = geometry.horizontalMarginDp.dp
                when (uiState) {
                    RecognitionUiState.RESULT_HIGH -> {
                        SpeciesIdentityRow(
                            speciesName = selectedName,
                            scientificName = selectedScientificName,
                            sideMargin = sideMargin,
                            widthDp = geometry.heroWidthDp,
                            enabled = !saveBusy && resultHandoffProjection.interactionEnabled,
                            revealFrameState = revealFrameState,
                            supportingInfoAlpha = revealFrameState.value.resultInformationAlpha,
                            glyphIdentity = if (selectedKey == prediction.top1.speciesKey &&
                                selectedName == prediction.top1.speciesName
                            ) recognitionSpeciesGlyphIdentity(
                                resultSceneIdentity,
                                prediction.top1.speciesKey,
                                resultSpeciesDisplayName(prediction.top1.speciesName),
                            ) else null,
                            holdNameUntilHandoff = !revealAlreadyPlayed &&
                                (revealHandoff == null || revealHandoff.revealDecision !in setOf(
                                    RecognitionRevealDecisionState.FISH_FOCUS_READY,
                                    RecognitionRevealDecisionState.ORB_ONLY_READY,
                                )),
                            onTitleBounds = { revealTitleBounds = it },
                            onChange = { openSpeciesSelector() },
                        )
                        if (geometryInvalid || (geometryWaitExpired && !resultGeometryReady)) {
                            Text(
                                text = if (geometryInvalid) {
                                    "Result 几何不可用 · 当前显示静态结果"
                                } else {
                                    "正在等待 Result 几何 · 就绪后继续水光揭晓"
                                },
                                color = Color(0xFF7C6A4B),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin, vertical = 2.dp),
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .graphicsLayer { alpha = revealFrameState.value.resultInformationAlpha }
                                .testTag("recognition-result-information"),
                        ) {
                            Spacer(Modifier.height(8.dp))
                            ResultMetadataStrip(
                                length = lengthText,
                                weight = weightText,
                                location = locationText,
                                resolvingLocation = resolvingLocation,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                enabled = !saveBusy && resultHandoffProjection.interactionEnabled,
                                onField = { editField = it },
                            )
                            Spacer(Modifier.height(8.dp))
                            ResultMemoryNote(
                                value = storyText,
                                onValueChange = { storyText = it.takeUnicodeCodePoints(300) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                                enabled = !saveBusy && resultHandoffProjection.interactionEnabled,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                            )
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(
                                safeInlineErrorMessage,
                                sideMargin,
                                actionLabel = inlineErrorActionLabel,
                                onAction = inlineErrorAction,
                                enabled = !saveBusy && resultHandoffProjection.interactionEnabled,
                            )
                            ResultDualActions(
                                useResultSurfaceActions = true,
                                saving = saveBusy,
                                loadingDestination = activeLoadingDestination,
                                accessibilityFontScale = adaptiveProfile.accessibilityFontScale,
                                compactLayout = compactResultActions,
                                sideMargin = if (compactResultActions) 16.dp else 24.dp,
                                enabled = resultHandoffProjection.interactionEnabled,
                                onContinue = { save(RecognitionSaveDestination.MEMORY) },
                                onSave = { save(RecognitionSaveDestination.HOME) },
                            )
                        }
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
                                    saving = saveBusy,
                                    speciesImages = speciesImages,
                                    onSelect = { candidate -> selectedKey = candidate.speciesKey; selectedName = candidate.speciesName },
                                )
                                YuJianTextAction(
                                    text = "都不是？选择其他鱼种",
                                    onClick = { openSpeciesSelector() },
                                    role = YuJianTextActionRole.MUTED,
                                    enabled = !saveBusy,
                                    showChevron = true,
                                )
                            }
                        }
                        if (selectedKey.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            SpeciesIdentityRow(
                                speciesName = selectedName,
                                sideMargin = sideMargin,
                                widthDp = geometry.heroWidthDp,
                                scientificName = selectedScientificName,
                                enabled = !saveBusy,
                                onChange = { openSpeciesSelector() },
                            )
                            Spacer(Modifier.height(8.dp))
                            ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth().padding(horizontal = sideMargin), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saveBusy) { editField = it }
                            Spacer(Modifier.height(12.dp))
                            ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(300) }, Modifier.fillMaxWidth().padding(horizontal = sideMargin), enabled = !saveBusy, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                            Spacer(Modifier.height(16.dp))
                            ResultInlineError(
                                safeInlineErrorMessage,
                                sideMargin,
                                actionLabel = inlineErrorActionLabel,
                                onAction = inlineErrorAction,
                                enabled = !saveBusy,
                            )
                            ResultDualActions(
                                saving = saveBusy,
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
                                        enabled = !saveBusy,
                                        variant = YuJianActionButtonVariant.RESULT_SAVE,
                                        leadingIcon = { Icon(Icons.Rounded.SetMeal, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                    YuJianPrimaryButton(
                                        text = "重新拍摄", onClick = { requestExit(PendingExitAction.RETAKE) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !saveBusy,
                                        variant = YuJianActionButtonVariant.RESULT_CONTINUE,
                                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(if (compactResultActions) 16.dp else 18.dp)) },
                                        contentPadding = if (compactResultActions) PaddingValues(horizontal = 8.dp) else PaddingValues(horizontal = 12.dp),
                                        leadingIconSpacing = 4.dp,
                                    )
                                }
                            } else {
                                SpeciesIdentityRow(
                                    speciesName = selectedName,
                                    sideMargin = sideMargin,
                                    widthDp = geometry.heroWidthDp,
                                    scientificName = selectedScientificName,
                                    enabled = !saveBusy,
                                    onChange = { openSpeciesSelector() },
                                )
                                ResultMetadataStrip(lengthText, weightText, locationText, resolvingLocation, Modifier.fillMaxWidth(), accessibilityFontScale = adaptiveProfile.accessibilityFontScale, enabled = !saveBusy) { editField = it }
                                ResultMemoryNote(storyText, { storyText = it.takeUnicodeCodePoints(300) }, Modifier.fillMaxWidth(), enabled = !saveBusy, accessibilityFontScale = adaptiveProfile.accessibilityFontScale)
                                ResultInlineError(
                                    safeInlineErrorMessage,
                                    0.dp,
                                    actionLabel = inlineErrorActionLabel,
                                    onAction = inlineErrorAction,
                                    enabled = !saveBusy,
                                )
                                ResultDualActions(
                                    saving = saveBusy, sideMargin = 0.dp,
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
                if (BuildConfig.DEBUG) TextButton(
                    onClick = { scope.launch { RecognitionDiagnosticExport.share(context) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("导出识别诊断日志") }
                Spacer(Modifier.height(20.dp))
            }
        }
        val resultPhoto = image?.bitmap
        val sharedResultSceneFrame = diagnosticFrame ?: sceneFrameOverrideForTest
        val hostType = if (sharedResultSceneFrame?.stage == RecognitionSceneStage.RESIDUE_SETTLE) {
            WaterLightHost.RESIDUE
        } else WaterLightHost.HANDOFF
        val hostBounds = if (hostType == WaterLightHost.RESIDUE) photoTransitionGeometry?.heroPhotoClip
            else photoTransitionGeometry?.processingPhotoClip
        val geometryVersion = photoTransitionGeometry?.heroSourceTransform?.let { transform ->
            val base = WaterLightGeometryVersion.of(transform)
            hostBounds?.let { WaterLightGeometryVersion.withHostBounds(base, it.left, it.top, it.right, it.bottom) }
                ?: base
        } ?: 0L
        val resultSceneFrame = sharedResultSceneFrame?.copy(geometryVersion = geometryVersion)
        val resultFrame = WaterLightFrame(
            sessionId = presentedHandoff?.key?.recognitionSessionId.orEmpty(),
            photoSha256 = image?.photoSha256,
            elapsedMs = resultSceneFrame?.sceneElapsedMs ?: 0L,
            opticalReferenceElapsedMs = resultSceneFrame?.opticalReferenceElapsedMs ?: 0L,
            phase = if (hostType == WaterLightHost.RESIDUE) WaterLightPhase.RESIDUE else WaterLightPhase.HANDOFF,
            completion = if (revealFrameState.value.isComplete) 1f else 0f,
            reducedMotion = revealMotionPolicy.reduceMotion,
            geometryVersion = geometryVersion,
            attemptId = presentedHandoff?.key?.recognitionAttemptId.orEmpty(),
            imageId = image?.imageId.orEmpty(),
            photoContentVersion = resultPhoto?.generationId?.toLong() ?: 0L,
            eventSequence = resultSceneFrame?.eventSequence ?: 0L,
            sceneFrameOverride = resultSceneFrame,
            sceneStageOverride = resultSceneFrame?.stage,
            sceneStageElapsedMsOverride = resultSceneFrame?.stageElapsedMs,
            sceneStageProgressOverride = resultSceneFrame?.stageProgress,
        )
        val resultWaterEngine = remember(presentedHandoff?.key, image?.imageId) { UnifiedWaterLightEngine() }
        resultWaterEngine.activate(resultFrame)
        DisposableEffect(resultWaterEngine) { onDispose { resultWaterEngine.release() } }
        val resultHost = remember(resultWaterEngine, hostType) {
            WaterLightHostBinding(hostType, WaterLightMaterialProfile.forHost(hostType), resultWaterEngine)
        }
        val activeEnergyCurve = when (revealFrameState.value.phase) {
            RecognitionRevealPhase.PHOTO_CONTINUITY,
            RecognitionRevealPhase.ORB_RETURN -> revealGeometry?.orbReturnPathLocal
            RecognitionRevealPhase.ORB_TO_NAME -> revealGeometry?.orbToGlyphPathLocal
            RecognitionRevealPhase.SPECIES_NAME_REVEAL -> revealGeometry?.glyphResiduePathLocal
            RecognitionRevealPhase.RESIDUE_SETTLE -> revealGeometry?.glyphResiduePathLocal
            RecognitionRevealPhase.COMPLETE -> null
        }
        val resultPathGeometry = activeEnergyCurve?.let { curve ->
            WaterLightPathGeometry(
                start = WaterLightPathPoint(curve.start.x, curve.start.y),
                control1 = WaterLightPathPoint(curve.control1.x, curve.control1.y),
                control2 = WaterLightPathPoint(curve.control2.x, curve.control2.y),
                end = WaterLightPathPoint(curve.end.x, curve.end.y),
            )
        }
        val trustedMaskReveal = visualHandoff?.eligibility == RecognitionRevealEligibility.FULL_REVEAL
        val orbOnlyReveal = visualHandoff != null &&
            visualHandoff.revealDecision == RecognitionRevealDecisionState.ORB_ONLY_READY &&
            visualHandoff.eligibility == RecognitionRevealEligibility.TITLE_FADE_ONLY &&
            revealGeometry?.orbReturnPathLocal != null
        val resultEnergyEvent = if (trustedMaskReveal || orbOnlyReveal) {
            WaterLightEnergy.at(
                resultFrame, trustedMask = trustedMaskReveal, successfulRevealAllowed = true,
                pathGeometry = resultPathGeometry,
                orbOnlyRevealAllowed = orbOnlyReveal,
            )
        } else null
        LaunchedEffect(
            resultFrame.sessionId,
            resultFrame.attemptId,
            resultFrame.imageId,
            resultEnergyEvent?.feedback,
            resultEnergyEvent?.pathId,
            resultEnergyEvent?.eventSequence,
            resultEnergyEvent?.level?.let { it >= .999f },
            resultHandoffProjection.resultCommitReady,
            resultCommitIdentity,
        ) {
            val event = resultEnergyEvent ?: return@LaunchedEffect
            if (event.level >= .999f && resultSceneFrame != null) {
                val mayRecordSettle = event.feedback != EnergyFeedback.SETTLE ||
                    resultHandoffProjection.resultCommitReady
                val accepted = mayRecordSettle &&
                    diagnosticPlaybackController?.recordEnergyEvent(resultSceneFrame, event) == true
                if (accepted && event.feedback == EnergyFeedback.SETTLE &&
                    resultHandoffProjection.resultCommitReady &&
                    resultHandoffCommitGuard.claim(resultCommitIdentity, ready = true)
                ) {
                    latestRevealFinished.value.invoke(RecognitionRevealKey(event.sessionId, event.attemptId))
                }
            }
            Log.i(
                "WaterLightEnergy",
                "ENERGY_EVENT session=${event.sessionId} attempt=${event.attemptId} image=${event.imageId} " +
                    "sha256=${event.photoSha256} feedback=${event.feedback} source=${event.source} target=${event.target} " +
                    "reference_ms=${event.referenceMs} level=${event.level} path=${event.pathId} " +
                    "start=${event.pathGeometry?.start} end=${event.pathGeometry?.end} geometry=${event.geometryVersion}",
            )
        }
        if (hostBounds != null && resultHost.accepts(resultFrame, WaterLightHostGeometry(
                hostType, resultFrame.geometryVersion,
                hostBounds.left, hostBounds.top, hostBounds.right, hostBounds.bottom,
            ))) RecognitionRevealOpticalOverlay(
            modifier = Modifier.fillMaxSize(),
            bitmap = image?.bitmap,
            handoff = presentedHandoff,
            photoGeometry = photoTransitionGeometry,
            geometry = revealGeometry,
            frameState = revealFrameState,
            holdPhotoUntilHeroReady = heroSnapshot?.renderReady == false,
            material = resultHost.material,
            energyEvent = resultEnergyEvent,
            waterLightFrame = resultFrame,
        )
        if (diagnosticUiAvailable) {
            val playback = requireNotNull(diagnosticPlaybackController)
            val stage = diagnosticStage
            val reference = diagnosticReferenceMs ?: 0L
            val stepStatus = when {
                diagnosticFrame?.modelGates?.waitingReason == "WAITING_FOR_RESULT_GEOMETRY" -> "等待 Result / Orb / Glyph 几何"
                diagnosticFrame?.modelGates?.waitingReason == "WAITING_FOR_REAL_CLASSIFIER_GLYPH" -> "等待真实鱼种 Glyph"
                diagnosticFrame?.modelGates?.waitingReason != null -> "等待：${diagnosticFrame?.modelGates?.waitingReason}"
                !revealStartReady && diagnosticDisplayMode == RecognitionProgressDisplayMode.STEP_DIAGNOSTIC -> "等待 Result 几何"
                visualHandoff?.eligibility != RecognitionRevealEligibility.FULL_REVEAL -> "真实HIGH降级/等待 Trusted Mask"
                else -> "进行中"
            }
            RecognitionStepDiagnosticControls(
                displayMode = diagnosticDisplayMode,
                stage = stage,
                playbackMode = playback.mode,
                previewElapsedMs = diagnosticPreviewElapsedMs,
                previewCompleted = diagnosticPreviewCompleted,
                playbackPaused = diagnosticPaused,
                mappedReferenceMs = reference,
                modelStatus = if (productionResult?.ready == true) "真实模型已完成" else "真实模型等待",
                detectorStatus = if (productionResult?.assessment?.primary?.box != null) "就绪" else "失败",
                classifierStatus = when {
                    productionResult?.ready == true -> "就绪"
                    productionResult != null -> "未通过"
                    else -> "等待"
                },
                slimSamStatus = diagnosticFrame?.modelGates?.slimSam?.name ?: "PENDING",
                maskStatus = when {
                    revealHandoff?.geometryTrustState == RecognitionGeometryTrustState.TRUSTED_MASK -> "Mask TRUSTED"
                    subjectResult.status == SubjectStatus.PROCESSING -> "Mask 计算中"
                    subjectResult.status == SubjectStatus.FAILED -> "Mask 失败 · BBOX_ONLY"
                    else -> revealHandoff?.geometryTrustState?.name ?: "Mask不可用"
                },
                geometryStatus = diagnosticFrame?.modelGates?.geometry?.name ?: "PENDING",
                sceneStatus = diagnosticFrame?.modelGates?.runState?.name ?: "WAITING",
                stepStatus = stepStatus,
                normalProgressText = if (diagnosticDisplayMode == RecognitionProgressDisplayMode.NORMAL_PROGRESS) {
                    "真实识别阶段 · ${diagnosticStage.displayName} · ${(diagnosticFrame?.stageProgress ?: 0f).times(100).toInt()}%"
                } else "",
                canGoPrevious = playback.canControl() && stage.ordinal > RecognitionSceneStage.PHOTO_STABLE.ordinal,
                canGoNext = playback.canControl() && stage != RecognitionSceneStage.RESIDUE_SETTLE,
                canControl = playback.canControl(),
                onDisplayModeChange = ::changeDiagnosticDisplayMode,
                onPrevious = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.mode == ScenePlaybackMode.MANUAL) playback.returnToPreviousStage(now)
                    else {
                        val target = RecognitionSceneStage.entries[(stage.ordinal - 1).coerceAtLeast(0)]
                        playback.beginStepPreview(target, now)
                    }
                    diagnosticUserPaused = false
                    diagnosticNowMs = now
                    if (stage == RecognitionSceneStage.ORB_RETURN) onDiagnosticReturnToProcessing()
                },
                onNext = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.mode == ScenePlaybackMode.MANUAL) playback.advanceToNextStage(now)
                    else {
                        val targetIndex = (stage.ordinal + 1).coerceAtMost(RecognitionSceneStage.entries.lastIndex)
                        playback.beginStepPreview(RecognitionSceneStage.entries[targetIndex], now)
                        onDiagnosticDisplayModeChange(RecognitionProgressDisplayMode.STEP_DIAGNOSTIC)
                    }
                    diagnosticUserPaused = false
                    diagnosticNowMs = now
                },
                onPauseResume = {
                    val now = SystemClock.uptimeMillis()
                    if (playback.paused) {
                        diagnosticUserPaused = false
                        playback.resume(reference, now)
                    } else {
                        diagnosticUserPaused = true
                        playback.pause(reference, now)
                    }
                    diagnosticNowMs = now
                },
                onReplay = {
                    val now = SystemClock.uptimeMillis()
                    diagnosticUserPaused = false
                    playback.restartCurrentStage(now)
                    diagnosticNowMs = now
                },
                onAutoPlay = {
                    val now = SystemClock.uptimeMillis()
                    playback.playDiagnosticAuto(reference, now)
                    diagnosticNowMs = now
                },
            )
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

private enum class ResultEditableField { LENGTH, WEIGHT, LOCATION }
private enum class PendingExitAction { BACK, RETAKE }

@Composable
internal fun BgContentSurface() {
    YuJianMorningLakeBackground(
        variant = YuJianMorningLakeVariant.CONTENT,
    )
}

@Composable
fun ResultHeroViewport(
    bitmap: ImageBitmap,
    bbox: FishPhotoBoundingBox?,
    primaryAmbiguous: Boolean = false,
    widthDp: Int,
    heightDp: Int,
    evidenceFirst: Boolean = false,
    revealFrameState: State<RecognitionRevealFrame>? = null,
    sharedPhotoGeometryReady: Boolean = false,
    holdPhotoUntilReady: Boolean = false,
    onHeroSnapshot: ((RecognitionRevealWindowRect, NormalizedSourceRect?, Boolean) -> Unit)? = null,
) {
    val shape = YuJianRadius.resultHero
    val revealFrame = revealFrameState?.value
    val hideForSharedPhotoTransition = sharedPhotoGeometryReady && revealFrame != null &&
        (revealFrame.photoProgress < 1f || holdPhotoUntilReady)
    var heroBounds by remember(bitmap) { mutableStateOf<RecognitionRevealWindowRect?>(null) }
    var plannedCrop by remember(bitmap) { mutableStateOf<PhotoCropRect?>(null) }
    var renderReady by remember(bitmap) { mutableStateOf(false) }
    LaunchedEffect(heroBounds, plannedCrop, renderReady) {
        val bounds = heroBounds ?: return@LaunchedEffect
        onHeroSnapshot?.invoke(
            bounds,
            plannedCrop?.let { NormalizedSourceRect(it.left, it.top, it.right, it.bottom) },
            renderReady,
        )
    }
    Box(
        Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(shape)
            .graphicsLayer(alpha = if (hideForSharedPhotoTransition) 0f else 1f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                heroBounds = RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
            .background(Color(0xFF16242C), shape)
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .testTag("recognition-result-hero"),
    ) {
        FishPhotoBitmapImage(
            bitmap = bitmap,
            modifier = Modifier.fillMaxSize(),
            contentDescription = "识别鱼获照片",
            boundingBox = bbox,
            primaryAmbiguous = primaryAmbiguous,
            allowSubjectCrop = !evidenceFirst,
            onRenderGeometry = { crop, ready ->
                plannedCrop = crop
                renderReady = ready
            },
        )
    }
}

@Composable
private fun SpeciesIdentityRow(
    speciesName: String,
    sideMargin: Dp,
    widthDp: Int,
    scientificName: String? = null,
    enabled: Boolean = true,
    revealFrameState: State<RecognitionRevealFrame>? = null,
    glyphIdentity: RecognitionSpeciesGlyphIdentity? = null,
    holdNameUntilHandoff: Boolean = false,
    supportingInfoAlpha: Float = 1f,
    onTitleBounds: ((RecognitionRevealWindowRect) -> Unit)? = null,
    onChange: () -> Unit,
) {
    val compactTitle = widthDp <= 359
    val revealFrame = revealFrameState?.value
    val revealTitleMode = revealFrame?.titleMode ?: RecognitionRevealTitleMode.VISIBLE
    val revealFormationProgress = revealFrame?.titleFormationProgress ?: 1f
    val revealProgress = revealFrame?.titleRevealProgress ?: 1f
    val speciesDisplayName = resultSpeciesDisplayName(speciesName)
    val activeGlyphIdentity = glyphIdentity?.takeIf { it.displayName == speciesDisplayName }
    val orbGlyphTransition = revealFrame?.let {
        recognitionOrbGlyphTransition(it.stage, it.stageProgress)
    }
    var speciesGlyphPath by remember(activeGlyphIdentity, compactTitle, widthDp) { mutableStateOf<android.graphics.Path?>(null) }
    val glyphReveal = remember(activeGlyphIdentity) { com.yujian.ai.ui.recognition.reveal.SpeciesNameReveal() }
    val showScientificName = supportingInfoAlpha > 0f && !holdNameUntilHandoff && (revealFrame == null ||
        revealFrame.isComplete || revealTitleMode == RecognitionRevealTitleMode.VISIBLE ||
        revealTitleMode == RecognitionRevealTitleMode.STATIC)
    val visibleTitleAlpha = if (showScientificName) supportingInfoAlpha.coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = sideMargin)
            .heightIn(min = 44.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onTitleBounds?.invoke(RecognitionRevealWindowRect(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            .testTag("recognition-result-species"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                speciesDisplayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        if (holdNameUntilHandoff) return@drawWithContent
                        val settled = activeGlyphIdentity == null ||
                            revealTitleMode == RecognitionRevealTitleMode.STATIC ||
                            revealTitleMode == RecognitionRevealTitleMode.VISIBLE
                        if (settled) drawContent() else speciesGlyphPath?.let { glyph ->
                            glyphReveal.draw(drawContext.canvas.nativeCanvas, glyph, size.width, size.height,
                                mode = revealTitleMode,
                                formationProgress = revealFormationProgress,
                                revealProgress = revealProgress,
                                sweepProgress = revealFrame?.titleSweepProgress ?: revealProgress,
                                substep = orbGlyphTransition?.substep,
                                substepProgress = orbGlyphTransition?.substepProgress ?: revealProgress,
                                glyphProgress = orbGlyphTransition?.glyphProgress ?: revealProgress,
                            )
                        }
                    }
                    .testTag("recognition-result-species-name"),
                color = DeepInk,
                fontSize = if (compactTitle) 28.sp else 30.sp,
                lineHeight = if (compactTitle) 34.sp else 36.sp,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    if (speciesGlyphPath == null && activeGlyphIdentity != null) {
                        speciesGlyphPath = layout.getPathForRange(0, speciesDisplayName.length).asAndroidPath()
                    }
                },
            )
            scientificName?.takeIf { showScientificName && it.isNotBlank() }?.let { latinName ->
                Text(
                    latinName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(alpha = visibleTitleAlpha)
                        .drawWithContent {
                            val fraction = when (revealTitleMode) {
                                RecognitionRevealTitleMode.HIDDEN -> 0f
                                RecognitionRevealTitleMode.FORMING -> .04f + .38f * revealFormationProgress.coerceIn(0f, 1f)
                                RecognitionRevealTitleMode.REVEALING -> revealProgress.coerceIn(0f, 1f)
                                RecognitionRevealTitleMode.STATIC,
                                RecognitionRevealTitleMode.VISIBLE -> 1f
                            }
                            if (fraction > 0f) {
                                val canvas = drawContext.canvas.nativeCanvas
                                val saveCount = canvas.save()
                                try {
                                    canvas.clipRect(0f, 0f, size.width * fraction, size.height)
                                    drawContent()
                                } finally {
                                    canvas.restoreToCount(saveCount)
                                }
                            }
                        }
                        .testTag("recognition-result-scientific-name"),
                    color = MutedInk,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                )
            }
        }
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
    enabled: Boolean = true,
) {
    // The callbacks are supplied by the enclosing page so metadata and species are validated together.
    val blocked = saving || loadingDestination != null || !enabled
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
            .semantics {
                if (loading) stateDescription = "正在加载"
            }
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
private fun ResultInlineError(
    message: String?,
    sideMargin: Dp,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    enabled: Boolean,
) {
    if (!message.isNullOrBlank()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sideMargin).padding(bottom = 8.dp)) {
            Text(message, color = Color(0xFF9E4035), fontSize = 13.sp, lineHeight = 18.sp)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(actionLabel, color = YuJianColors.DeepLakeBlue, fontSize = 13.sp)
                }
            }
        }
    }
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
