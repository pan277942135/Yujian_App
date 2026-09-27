package com.yujian.ai

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectPreviewEngine
import com.yujian.ai.ai.subject.FishSubjectModelManager
import com.yujian.ai.ai.subject.SubjectModelState
import com.yujian.ai.ai.subject.SubjectModelStatus
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.auth.ApiException
import com.yujian.ai.auth.AuthRepository
import com.yujian.ai.catches.CatchRepository
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.GuestCatchRepository
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.feedback.FeedbackRepository
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.inference.InferenceRecorder
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.FishKnowledgeRepository
import com.yujian.ai.model.DemoData
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.session.UserSessionManager
import com.yujian.ai.privacy.AccountPrivacyCapabilities
import com.yujian.ai.privacy.PrivacyPromptFrequency
import com.yujian.ai.privacy.PrivacyPromptFrequencyStore
import com.yujian.ai.privacy.canPrompt
import com.yujian.ai.privacy.recordDismissal
import com.yujian.ai.privacy.suppressAfterManualWithdrawal
import com.yujian.ai.ui.screens.FishGuideHomeScreen
import com.yujian.ai.ui.screens.FishSpeciesDetailScreen
import com.yujian.ai.ui.screens.HomeScreen
import com.yujian.ai.ui.screens.IdentifyScreen
import com.yujian.ai.ui.screens.LoginScreen
import com.yujian.ai.ui.screens.MyScreen
import com.yujian.ai.ui.screens.AccountMyScreen
import com.yujian.ai.ui.screens.AccountLoginScreen
import com.yujian.ai.ui.screens.AboutYujianScreen
import com.yujian.ai.ui.screens.ChangePasswordScreen
import com.yujian.ai.ui.screens.ComingSoonKind
import com.yujian.ai.ui.screens.ComingSoonSheet
import com.yujian.ai.ui.screens.CorrectionConsentPromptSheet
import com.yujian.ai.ui.screens.DataPrivacyScreen
import com.yujian.ai.ui.screens.EditProfileScreen
import com.yujian.ai.ui.screens.LegalDocumentScreen
import com.yujian.ai.ui.screens.RecognitionIssueScreen
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.RecognizingScreen
import com.yujian.ai.ui.screens.RegisterScreen
import com.yujian.ai.ui.home.HomeState
import com.yujian.ai.ui.home.resolveHomeState
import com.yujian.ai.ui.components.GuestRegistrationDialog
import com.yujian.ai.ui.recorddetail.FishRecordDetailPresentation
import com.yujian.ai.ui.recorddetail.FishRecordDetailScreen
import com.yujian.ai.ui.recorddetail.FishRecordDetailRoute
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.launch
import java.io.File


private data class CatchArchiveState(
    val catches: List<RemoteCatch> = emptyList(),
    val statistics: CatchStatistics = CatchStatistics(),
    val loading: Boolean = false,
    val error: String? = null,
)

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun YujianApp() {
    val context = LocalContext.current.applicationContext
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionManager = remember { UserSessionManager(context) }
    val guestId = remember { sessionManager.guestId() }
    val authRepository = remember { AuthRepository() }
    val promptFrequencyStore = remember { PrivacyPromptFrequencyStore(context) }
    val catchRepository = remember { CatchRepository() }
    val guestCatchRepository = remember(guestId) { GuestCatchRepository(context) }
    val recognitionPipeline = remember { FishRecognitionPipeline(context) }
    val subjectPreviewEngine = remember { FishSubjectPreviewEngine(context) }
    val subjectModelManager = remember { FishSubjectModelManager(context) }
    val feedbackRepository = remember { FeedbackRepository(context) }
    val inferenceRecorder = remember { InferenceRecorder(context) }
    val fishKnowledgeRepository = remember { FishKnowledgeRepository() }
    var session by remember { mutableStateOf(sessionManager.current()) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var catchesState by remember { mutableStateOf(CatchArchiveState()) }
    var catchReload by remember { mutableIntStateOf(0) }
    var sessionImage by remember { mutableStateOf<SelectedImage?>(null) }
    var productionResult by remember { mutableStateOf<ProductionRecognitionResult?>(null) }
    var recognitionTechnicalFailure by remember { mutableStateOf(false) }
    var subjectResult by remember { mutableStateOf(FishSubjectResult(SubjectStatus.IDLE)) }
    var subjectModelState by remember { mutableStateOf(SubjectModelState()) }
    var prediction by remember { mutableStateOf<RecognitionPrediction?>(null) }
    var inferenceAsset by remember { mutableStateOf<InferenceAsset?>(null) }
    var catchSaving by remember { mutableStateOf(false) }
    var catchSaveError by remember { mutableStateOf<String?>(null) }
    var guestRegistrationPromptVisible by remember { mutableStateOf(false) }
    var guestMigrationPending by remember { mutableStateOf(false) }
    var guideSpecies by remember { mutableStateOf(emptyList<FishGuideItem>()) }
    var guideLoading by remember { mutableStateOf(true) }
    var guideOfflinePreview by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var guideRetry by remember { mutableIntStateOf(0) }
    var comingSoon by remember { mutableStateOf<ComingSoonKind?>(null) }
    var promptFrequency by remember { mutableStateOf(promptFrequencyStore.load()) }
    var correctionPromptVisible by remember { mutableStateOf(false) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun logoutToHome() {
        sessionManager.clear()
        session = null
        catchesState = CatchArchiveState()
        nav.navigate("home") { launchSingleTop = true }
    }

    fun applyProfile(profile: com.yujian.ai.auth.AccountProfile) {
        val active = session ?: return
        val updated = active.copy(
            userId = profile.id,
            username = profile.username,
            nickname = profile.nickname,
            avatarUrl = profile.avatarUrl,
        )
        sessionManager.save(updated)
        session = updated
    }

    fun adoptGuestArchive(loggedIn: com.yujian.ai.session.UserSession) {
        if (!guestCatchRepository.hasRecords()) return
        guestMigrationPending = true
        scope.launch {
            runCatching { guestCatchRepository.migrateToRemote(loggedIn.accessToken, catchRepository) }
                .onSuccess {
                    guestMigrationPending = false
                    catchReload++
                }
        }
    }

    fun applyBsideStatus(catchId: String, status: BsideStatus, resultUri: String?) {
        catchesState = catchesState.copy(
            catches = catchesState.catches.map { item ->
                if (item.id == catchId) item.copy(
                    bsideStatus = status,
                    bsideUri = if (status == BsideStatus.READY) resultUri else null,
                ) else item
            },
        )
    }

    fun requestBsideGeneration(record: RemoteCatch) {
        val active = session ?: return
        if (record.bsideStatus == BsideStatus.GENERATING) return
        applyBsideStatus(record.id, BsideStatus.GENERATING, null)
        scope.launch {
            runCatching { catchRepository.createBsideJob(active.accessToken, record.id) }
                .onSuccess { generated -> applyBsideStatus(record.id, generated.status, generated.resultUri) }
                .onFailure { error ->
                    applyBsideStatus(record.id, BsideStatus.FAILED, null)
                    catchesState = catchesState.copy(error = error.message ?: "渔获卡生成请求失败，请重试")
                }
        }
    }

    suspend fun refreshBsideStatus(catchId: String): Boolean {
        val active = session ?: return true
        return runCatching { catchRepository.bsideStatus(active.accessToken, catchId) }
            .onSuccess { generated -> applyBsideStatus(catchId, generated.status, generated.resultUri) }
            .map { it.status != BsideStatus.GENERATING }
            .getOrDefault(false)
    }

    DisposableEffect(Unit) { onDispose { recognitionPipeline.close(); subjectPreviewEngine.close(); subjectModelManager.close() } }
    // Inference uploads are consent-scoped. Existing queued artifacts are not
    // flushed automatically because their original-photo provenance predates
    // the current server consent check.
    LaunchedEffect(session?.accessToken) {
        val active = session ?: return@LaunchedEffect
        runCatching { authRepository.getProfile(active.accessToken) }
            .onSuccess(::applyProfile)
            .onFailure { error ->
                if ((error as? ApiException)?.statusCode == 401) logoutToHome()
            }
    }
    LaunchedEffect(guideRetry) {
        guideLoading = true
        guideError = null
        runCatching { fishKnowledgeRepository.listSpecies() }
            .onSuccess { remote ->
                guideSpecies = mergeGuideItems(remote)
                guideOfflinePreview = false
            }
            .onFailure { error ->
                guideSpecies = localGuideItems()
                guideOfflinePreview = true
                guideError = error.message ?: "Fish Knowledge API 暂不可用"
            }
        guideLoading = false
    }
    LaunchedEffect(session?.accessToken, catchReload, guestMigrationPending) {
        val active = session
        catchesState = catchesState.copy(loading = true, error = null)
        runCatching {
            if (active == null || guestMigrationPending) {
                val local = guestCatchRepository.listCatches()
                CatchArchiveState(
                    catches = local,
                    statistics = guestCatchRepository.statistics(local),
                )
            } else {
                CatchArchiveState(
                    catches = catchRepository.listCatches(active.accessToken),
                    statistics = catchRepository.statistics(active.accessToken),
                )
            }
        }.onSuccess { catchesState = it }
            .onFailure { error ->
                if ((error as? ApiException)?.statusCode == 401) {
                    logoutToHome()
                } else {
                    catchesState = catchesState.copy(loading = false, error = error.message ?: "鱼获数据加载失败")
                }
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        Box(Modifier.fillMaxSize().background(WarmBackground)) {
            // Home is available before authentication. The same route resolves
            // Empty vs Normal from the active local/remote fish archive.
            NavHost(nav, startDestination = "home") {
                composable("login") {
                    LoginScreen(
                        loading = authLoading,
                        error = authError,
                        onLogin = { username, password ->
                            scope.launch {
                                authLoading = true
                                authError = null
                                runCatching { authRepository.login(username, password) }
                                    .onSuccess { loggedIn ->
                                        sessionManager.save(loggedIn)
                                        session = loggedIn
                                        adoptGuestArchive(loggedIn)
                                        authLoading = false
                                        nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                    }
                                    .onFailure { error ->
                                        authLoading = false
                                        authError = authErrorMessage(error, "登录失败，请稍后重试")
                                    }
                            }
                        },
                        onRegister = { authError = null; nav.navigate("register") },
                        onForgotPassword = {
                            if (!AccountPrivacyCapabilities.forgotPasswordEnabled) {
                                comingSoon = ComingSoonKind.FORGOT_PASSWORD
                            }
                        },
                    )
                }
                composable("register") {
                    RegisterScreen(
                        loading = authLoading,
                        error = authError,
                        onRegister = { username, password, nickname ->
                            scope.launch {
                                authLoading = true
                                authError = null
                                runCatching {
                                    authRepository.register(username, password, nickname)
                                    authRepository.login(username, password)
                                }.onSuccess { registered ->
                                    sessionManager.save(registered)
                                    session = registered
                                    adoptGuestArchive(registered)
                                    authLoading = false
                                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                }.onFailure { error ->
                                    authLoading = false
                                    authError = authErrorMessage(error, "注册失败，请稍后重试")
                                }
                            }
                        },
                        onBackToLogin = { authError = null; nav.popBackStack() },
                    )
                }
                composable("home") {
                    val active = session
                    // The Home state is derived only from fish records. Login,
                    // loading, and server statistics never select Empty/Normal.
                    val emptyHome = resolveHomeState(catchesState.catches) == HomeState.EMPTY
                    HomeScreen(
                        nickname = active?.nickname.orEmpty(),
                        statistics = catchesState.statistics,
                        recentCatches = catchesState.catches,
                        resolveImageUrl = { path ->
                            if (path != null && File(path).exists()) "file://$path" else catchRepository.resolveUrl(path)
                        },
                        accessToken = active?.accessToken.orEmpty(),
                        isLoggedIn = active != null,
                        avatarUrl = active?.avatarUrl,
                        showEmptyState = emptyHome,
                        onIdentify = { nav.navigate("identify") },
                        onAlbumClick = { nav.navigate("identify?openGallery=true") },
                        onLoginClick = { nav.navigate("login") { launchSingleTop = true } },
                        onSpeciesClick = { nav.navigate("guide") },
                        onCatchesClick = { nav.navigate("my_catches") },
                        onRecordDaysClick = { },
                        onProfileClick = { if (active == null) nav.navigate("login") else nav.navigate("my") },
                        onCatchClick = { catchId -> nav.navigate("catch/" + Uri.encode(catchId)) },
                    )
                }
                composable(
                    route = FishRecordDetailRoute,
                    arguments = listOf(navArgument("catchId") { type = NavType.StringType }),
                ) { entry ->
                    val catchId = entry.arguments?.getString("catchId").orEmpty()
                    val detailState = FishRecordDetailPresentation.resolve(
                        catchId = catchId,
                        records = catchesState.catches,
                        loading = catchesState.loading,
                        error = catchesState.error,
                    )
                    FishRecordDetailScreen(
                        uiState = detailState,
                        imageUrlFor = { record ->
                            if (File(record.imageUrl).exists()) "file://${record.imageUrl}"
                            else catchRepository.resolveUrl(record.imageUrl)
                        },
                        bsideUrlFor = { record -> catchRepository.resolveUrl(record.bsideUri) },
                        accessToken = session?.accessToken.orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenFishGuide = { record -> nav.navigate("species/${Uri.encode(record.speciesId)}") },
                        onShare = { },
                        onEditRecord = { },
                        onAddMedia = { },
                        onGenerateMemory = if (session != null) {
                            { record -> requestBsideGeneration(record) }
                        } else null,
                    )
                }
                composable(
                    route = "identify?openGallery={openGallery}",
                    arguments = listOf(navArgument("openGallery") {
                        type = NavType.BoolType
                        defaultValue = false
                    }),
                ) { entry ->
                    IdentifyScreen(
                        image = sessionImage,
                        autoOpenGallery = entry.arguments?.getBoolean("openGallery") == true,
                        onBack = { nav.popBackStack() },
                        onImageReady = { selected ->
                            sessionImage = selected
                            productionResult = null
                            recognitionTechnicalFailure = false
                            subjectResult = FishSubjectResult(SubjectStatus.IDLE)
                            prediction = null
                            inferenceAsset = null
                            catchSaveError = null
                            nav.navigate("recognizing") { launchSingleTop = true }
                        },
                    )
                }
                composable("recognizing") {
                    RecognizingScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        recognize = { onProgress ->
                            val selected = requireNotNull(sessionImage)
                            val result = recognitionPipeline.recognize(selected.bitmap, onProgress)
                            inferenceAsset = inferenceRecorder.record(selected, result)
                            result
                        },
                        generateSubject = { selected, box ->
                            subjectPreviewEngine.generate(selected.bitmap, box)
                        },
                        onFinished = { result ->
                            productionResult = result
                            recognitionTechnicalFailure = false
                            subjectResult = FishSubjectResult(SubjectStatus.IDLE)
                            prediction = result.prediction
                            if (result.ready) {
                                nav.navigate("result") { popUpTo("recognizing") { inclusive = true } }
                            } else {
                                nav.navigate("recognition_issue") { popUpTo("recognizing") { inclusive = true } }
                            }
                        },
                        onFailure = {
                            productionResult = null
                            recognitionTechnicalFailure = true
                            nav.navigate("recognition_issue") { popUpTo("recognizing") { inclusive = true } }
                        },
                    )
                }
                composable("recognition_issue") {
                    val current = productionResult
                    if (current == null && !recognitionTechnicalFailure) {
                        LaunchedEffect(Unit) { nav.navigate("identify") { popUpTo("recognition_issue") { inclusive = true } } }
                    } else {
                        RecognitionIssueScreen(
                            image = sessionImage,
                            result = current,
                            technicalFailure = recognitionTechnicalFailure,
                            onBack = { nav.popBackStack() },
                            onChooseAnother = {
                                productionResult = null
                                recognitionTechnicalFailure = false
                                prediction = null
                                nav.navigate("identify") { popUpTo("identify") { inclusive = false }; launchSingleTop = true }
                            },
                            onChooseGallery = {
                                productionResult = null
                                recognitionTechnicalFailure = false
                                prediction = null
                                nav.navigate("identify?openGallery=true") {
                                    popUpTo("recognition_issue") { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onRetry = {
                                productionResult = null
                                recognitionTechnicalFailure = false
                                prediction = null
                                nav.navigate("recognizing") { popUpTo("recognition_issue") { inclusive = true } }
                            },
                        )
                    }
                }
                composable("result") {
                    val currentPrediction = prediction
                    LaunchedEffect(Unit) {
                        subjectModelState = subjectModelManager.checkStatus()
                    }
                    if (currentPrediction == null) {
                        LaunchedEffect(Unit) { nav.navigate("identify") { popUpTo("result") { inclusive = true } } }
                    } else {
                        RecognitionResultScreen(
                            image = sessionImage,
                            prediction = currentPrediction,
                            productionResult = productionResult,
                            subjectResult = subjectResult,
                            subjectModelState = subjectModelState,
                            onPrepareSubjectModel = {
                                scope.launch {
                                    subjectModelState = SubjectModelState(SubjectModelStatus.DOWNLOADING)
                                    subjectModelState = subjectModelManager.prepareSubjectModel()
                                }
                            },
                            onGenerateSubject = {
                                val selected = sessionImage
                                val bbox = productionResult?.assessment?.primary?.box
                                if (selected != null && bbox != null) {
                                    scope.launch {
                                        subjectResult = FishSubjectResult(SubjectStatus.PROCESSING)
                                        subjectResult = subjectPreviewEngine.generate(selected.bitmap, bbox)
                                    }
                                }
                            },
                            onBack = { nav.popBackStack() },
                            onRetry = {
                                productionResult = null
                                recognitionTechnicalFailure = false
                                prediction = null
                                nav.navigate("recognizing") { popUpTo("result") { inclusive = true } }
                            },
                            saving = catchSaving,
                            saveError = catchSaveError,
                            onSave = { draft, feedback ->
                                val active = session
                                val selected = sessionImage
                                if (selected == null) {
                                    catchSaveError = "照片已失效，请重新选择"
                                } else {
                                    scope.launch {
                                        catchSaving = true
                                        catchSaveError = null
                                        runCatching {
                                            if (active == null) {
                                                guestCatchRepository.saveCatch(File(selected.filePath), draft)
                                            } else {
                                                val upload = catchRepository.uploadImage(active.accessToken, File(selected.filePath))
                                                catchRepository.saveCatch(active.accessToken, upload, draft)
                                            }
                                        }.onSuccess {
                                            catchSaving = false
                                            catchReload++
                                            if (active != null) {
                                                scope.launch {
                                                    val privacy = runCatching { authRepository.getPrivacySettings(active.accessToken) }.getOrNull()
                                                    inferenceAsset?.let { asset ->
                                                        val updated = inferenceRecorder.attachFeedback(asset, feedback)
                                                        // Inference/training uploads are denied by default. The server
                                                        // consent value is read after the record is durably saved.
                                                        if (privacy?.enabled == true && feedback.correctedSpecies?.isNotBlank() == true) {
                                                            feedbackRepository.submitConsentCrop(updated)
                                                        }
                                                    }
                                                    val corrected = feedback.correctedSpecies?.isNotBlank() == true &&
                                                        !feedback.correctedSpecies.equals(feedback.predictedSpecies, ignoreCase = true)
                                                    if (corrected && privacy != null && promptFrequency.canPrompt(
                                                            nowMillis = System.currentTimeMillis(),
                                                            consentEnabled = privacy.enabled,
                                                            speciesCorrected = true,
                                                            fishRecordSaved = true,
                                                        )
                                                    ) correctionPromptVisible = true
                                                }
                                            }
                                            if (active == null && !sessionManager.guestRegistrationPromptShown()) {
                                                sessionManager.markGuestRegistrationPromptShown()
                                                guestRegistrationPromptVisible = true
                                            }
                                            nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
                                        }.onFailure { error ->
                                            catchSaving = false
                                            if ((error as? ApiException)?.statusCode == 401) {
                                                logoutToHome()
                                            } else {
                                                catchSaveError = error.message ?: "保存鱼获失败，请重试"
                                            }
                                        }
                                    }
                                }
                            },
                            onViewGuide = { speciesId -> nav.navigate("species/${Uri.encode(speciesId)}") },
                        )
                    }
                }
                composable("guide") {
                    FishGuideHomeScreen(
                        species = guideSpecies,
                        loading = guideLoading,
                        offlinePreview = guideOfflinePreview,
                        error = guideError,
                        resolveAssetUrl = fishKnowledgeRepository::resolveAssetUrl,
                        onRetry = { guideRetry++ },
                        onSpeciesClick = { fish -> nav.navigate("species/${Uri.encode(fish.id)}") },
                    )
                }
                composable("species/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                    val key = entry.arguments?.getString("key") ?: "grass_carp"
                    val fallback = guideSpecies.firstOrNull { it.id == key } ?: localGuideItems().firstOrNull { it.id == key }
                    var detail by remember(key) { mutableStateOf<FishKnowledgeDetail?>(null) }
                    var detailLoading by remember(key) { mutableStateOf(true) }
                    var detailOfflinePreview by remember(key) { mutableStateOf(false) }
                    var detailError by remember(key) { mutableStateOf<String?>(null) }
                    var detailRetry by remember(key) { mutableIntStateOf(0) }
                    LaunchedEffect(key, detailRetry) {
                        detailLoading = true
                        detailError = null
                        runCatching { fishKnowledgeRepository.getDetail(key) }
                            .onSuccess { detail = it; detailOfflinePreview = false }
                            .onFailure { error ->
                                detailOfflinePreview = true
                                detailError = error.message ?: "鱼种详情暂不可用"
                            }
                        detailLoading = false
                    }
                    FishSpeciesDetailScreen(
                        detail = detail,
                        fallback = fallback,
                        loading = detailLoading,
                        offlinePreview = detailOfflinePreview,
                        error = detailError,
                        resolveAssetUrl = fishKnowledgeRepository::resolveAssetUrl,
                        onRetry = { detailRetry++ },
                        onBack = { nav.popBackStack() },
                        onOpenCatch = { nav.navigate("my_catches") },
                    )
                }
                composable("my") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("my") { inclusive = true } } }
                    } else {
                        AccountMyScreen(
                            profile = active,
                            statistics = catchesState.statistics,
                            recordDays = catchesState.catches.map { it.capturedAt.ifBlank { it.createdAt }.take(10) }.filter(String::isNotBlank).distinct().size,
                            onEditProfile = { nav.navigate("edit_profile") },
                            onAccountLogin = { nav.navigate("account_login") },
                            onAbout = { nav.navigate("about") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("my_catches") {
                    val active = session
                    MyScreen(
                        catches = catchesState.catches,
                        loading = catchesState.loading,
                        error = catchesState.error,
                        resolveImageUrl = { path ->
                            if (path != null && File(path).exists()) "file://$path" else catchRepository.resolveUrl(path)
                        },
                        accessToken = active?.accessToken.orEmpty(),
                        onCatch = { catchId -> nav.navigate("catch/${Uri.encode(catchId)}") },
                        onRetry = { catchReload++ },
                        onCapture = { nav.navigate("identify") },
                    )
                }
                composable("edit_profile") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("edit_profile") { inclusive = true } } }
                    } else {
                        EditProfileScreen(
                            profile = active,
                            authRepository = authRepository,
                            onProfileUpdated = ::applyProfile,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("account_login") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("account_login") { inclusive = true } } }
                    } else {
                        AccountLoginScreen(
                            profile = active,
                            onChangePassword = { nav.navigate("change_password") },
                            onDataPrivacy = { nav.navigate("data_privacy") },
                            onLogout = ::logoutToHome,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("change_password") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("change_password") { inclusive = true } } }
                    } else {
                        ChangePasswordScreen(
                            authRepository = authRepository,
                            accessToken = active.accessToken,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("data_privacy") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("data_privacy") { inclusive = true } } }
                    } else {
                        DataPrivacyScreen(
                            authRepository = authRepository,
                            accessToken = active.accessToken,
                            onPrivacyPolicy = { nav.navigate("privacy_policy") },
                            onComingSoon = { kind ->
                                if ((kind == ComingSoonKind.EXPORT_DATA && !AccountPrivacyCapabilities.dataExportEnabled) ||
                                    (kind == ComingSoonKind.DELETE_ACCOUNT && !AccountPrivacyCapabilities.accountDeletionEnabled)
                                ) comingSoon = kind
                            },
                            onManualWithdrawal = { promptFrequency = promptFrequency.suppressAfterManualWithdrawal() },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("about") {
                    AboutYujianScreen(
                        onUserAgreement = { nav.navigate("user_agreement") },
                        onPrivacyPolicy = { nav.navigate("privacy_policy") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("privacy_policy") {
                    LegalDocumentScreen(title = "隐私政策", isPrivacyPolicy = true, onBack = { nav.popBackStack() })
                }
                composable("user_agreement") {
                    LegalDocumentScreen(title = "用户协议", isPrivacyPolicy = false, onBack = { nav.popBackStack() })
                }
            }
            if (guestRegistrationPromptVisible) {
                GuestRegistrationDialog(
                    onRegister = {
                        guestRegistrationPromptVisible = false
                        nav.navigate("login") { launchSingleTop = true }
                    },
                    onLater = { guestRegistrationPromptVisible = false },
                )
            }
            comingSoon?.let { kind -> ComingSoonSheet(kind = kind, onDismiss = { comingSoon = null }) }
            if (correctionPromptVisible) {
                CorrectionConsentPromptSheet(
                    onEnable = {
                        correctionPromptVisible = false
                        scope.launch {
                            runCatching {
                                authRepository.setAiModelImprovementConsent(
                                    requireNotNull(session).accessToken,
                                    enabled = true,
                                    source = "species_correction_prompt",
                                )
                            }
                        }
                    },
                    onDismiss = {
                        correctionPromptVisible = false
                        val updated = promptFrequency.recordDismissal(System.currentTimeMillis())
                        promptFrequency = updated
                        promptFrequencyStore.save(updated)
                    },
                )
            }
        }
    }
}

private fun localGuideItems(): List<FishGuideItem> = DemoData.species.map { fish ->
    FishGuideItem(
        id = fish.key,
        nameCn = fish.name,
        aliases = fish.aliases.split("、").map(String::trim).filter(String::isNotBlank),
        category = fish.category,
        summary = fish.description,
        discovered = fish.discovered,
        catches = fish.catches,
    )
}

private fun mergeGuideItems(remote: List<FishGuideItem>): List<FishGuideItem> {
    val local = localGuideItems().associateBy { it.id }
    return remote.map { item ->
        val localItem = local[item.id]
        item.copy(
            aliases = localItem?.aliases ?: item.aliases,
            category = item.category.ifBlank { localItem?.category.orEmpty() },
            discovered = localItem?.discovered ?: false,
            catches = localItem?.catches ?: 0,
        )
    }
}

private fun authErrorMessage(error: Throwable, fallback: String): String = when ((error as? ApiException)?.statusCode) {
    401 -> "账号或密码错误"
    409 -> "账号已存在"
    422 -> "账号、密码或昵称格式不符合要求"
    else -> error.message?.takeIf(String::isNotBlank) ?: fallback
}
