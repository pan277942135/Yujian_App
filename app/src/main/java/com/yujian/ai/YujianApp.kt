package com.yujian.ai

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.yujian.ai.auth.ApiException
import com.yujian.ai.auth.AuthRepository
import com.yujian.ai.catches.CatchRepository
import com.yujian.ai.catches.CatchStatistics
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
import com.yujian.ai.ui.screens.FishGuideHomeScreen
import com.yujian.ai.ui.screens.FishSpeciesDetailScreen
import com.yujian.ai.ui.screens.HomeScreen
import com.yujian.ai.ui.screens.IdentifyScreen
import com.yujian.ai.ui.screens.LoginScreen
import com.yujian.ai.ui.screens.MyScreen
import com.yujian.ai.ui.screens.RecognitionIssueScreen
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.RecognizingScreen
import com.yujian.ai.ui.screens.RegisterScreen
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import kotlinx.coroutines.launch
import java.io.File

data class BottomItem(val route: String, val label: String, val icon: @Composable () -> Unit)

private data class CatchArchiveState(
    val catches: List<RemoteCatch> = emptyList(),
    val statistics: CatchStatistics = CatchStatistics(),
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
fun YujianApp() {
    val context = LocalContext.current.applicationContext
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionManager = remember { UserSessionManager(context) }
    val authRepository = remember { AuthRepository() }
    val catchRepository = remember { CatchRepository() }
    val recognitionPipeline = remember { FishRecognitionPipeline(context) }
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
    var prediction by remember { mutableStateOf<RecognitionPrediction?>(null) }
    var inferenceAsset by remember { mutableStateOf<InferenceAsset?>(null) }
    var catchSaving by remember { mutableStateOf(false) }
    var catchSaveError by remember { mutableStateOf<String?>(null) }
    var guideSpecies by remember { mutableStateOf(emptyList<FishGuideItem>()) }
    var guideLoading by remember { mutableStateOf(true) }
    var guideOfflinePreview by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var guideRetry by remember { mutableIntStateOf(0) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun logoutToLogin() {
        sessionManager.clear()
        session = null
        catchesState = CatchArchiveState()
        nav.navigate("login") { launchSingleTop = true }
    }

    DisposableEffect(Unit) { onDispose { recognitionPipeline.close() } }
    LaunchedEffect(Unit) { feedbackRepository.flushQueued() }
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
    LaunchedEffect(session?.accessToken, catchReload) {
        val active = session
        if (active == null) {
            catchesState = CatchArchiveState()
        } else {
            catchesState = catchesState.copy(loading = true, error = null)
            runCatching {
                CatchArchiveState(
                    catches = catchRepository.listCatches(active.accessToken),
                    statistics = catchRepository.statistics(active.accessToken),
                )
            }.onSuccess { catchesState = it }
                .onFailure { error ->
                    if ((error as? ApiException)?.statusCode == 401) {
                        logoutToLogin()
                    } else {
                        catchesState = catchesState.copy(loading = false, error = error.message ?: "鱼获数据加载失败")
                    }
                }
        }
    }

    val bottomItems = listOf(
        BottomItem("home", "首页") { Icon(Icons.Rounded.Home, null) },
        BottomItem("identify", "识鱼") { Icon(Icons.Rounded.PhotoCamera, null) },
        BottomItem("guide", "图鉴") { Icon(Icons.Rounded.Style, null) },
        BottomItem("my", "我的") { Icon(Icons.Rounded.Person, null) },
    )
    val bottomVisibleRoutes = setOf("home", "guide", "my")

    Scaffold(containerColor = WarmBackground, bottomBar = {
        if (currentRoute in bottomVisibleRoutes && session != null) {
            NavigationBar(containerColor = Color.White) {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = item.icon,
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WaterTeal, selectedTextColor = WaterTeal,
                            indicatorColor = WaterTeal.copy(alpha = .14f),
                            unselectedIconColor = MutedInk, unselectedTextColor = MutedInk,
                        ),
                    )
                }
            }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).background(WarmBackground)) {
            NavHost(nav, startDestination = if (session == null) "login" else "home") {
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
                                        authLoading = false
                                        nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                    }
                                    .onFailure { error ->
                                        authLoading = false
                                        authError = error.message ?: "登录失败，请稍后重试"
                                    }
                            }
                        },
                        onRegister = { authError = null; nav.navigate("register") },
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
                                    authLoading = false
                                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                }.onFailure { error ->
                                    authLoading = false
                                    authError = error.message ?: "注册失败，请稍后重试"
                                }
                            }
                        },
                        onBackToLogin = { authError = null; nav.popBackStack() },
                    )
                }
                composable("home") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { logoutToLogin() }
                    } else {
                        HomeScreen(
                            nickname = active.nickname,
                            statistics = catchesState.statistics,
                            recentCatch = catchesState.catches.firstOrNull(),
                            resolveImageUrl = catchRepository::resolveUrl,
                            accessToken = active.accessToken,
                            onIdentify = { nav.navigate("identify") },
                            onGuide = { nav.navigate("guide") },
                            onRecentCatch = { nav.navigate("my") },
                        )
                    }
                }
                composable("identify") {
                    IdentifyScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        onImageSelected = {
                            sessionImage = it
                            productionResult = null
                            prediction = null
                            inferenceAsset = null
                            catchSaveError = null
                        },
                        onStartRecognition = { if (sessionImage != null) nav.navigate("recognizing") },
                    )
                }
                composable("recognizing") {
                    RecognizingScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        recognize = {
                            val selected = requireNotNull(sessionImage)
                            val result = recognitionPipeline.recognize(selected.bitmap)
                            inferenceAsset = inferenceRecorder.record(selected, result)
                            result
                        },
                        onFinished = { result ->
                            productionResult = result
                            prediction = result.prediction
                            if (result.ready) {
                                nav.navigate("result") { popUpTo("recognizing") { inclusive = true } }
                            } else {
                                nav.navigate("recognition_issue") { popUpTo("recognizing") { inclusive = true } }
                            }
                        },
                    )
                }
                composable("recognition_issue") {
                    val current = productionResult
                    if (current == null || current.ready) {
                        LaunchedEffect(Unit) { nav.navigate("identify") { popUpTo("recognition_issue") { inclusive = true } } }
                    } else {
                        RecognitionIssueScreen(
                            image = sessionImage,
                            result = current,
                            onBack = { nav.popBackStack() },
                            onChooseAnother = {
                                productionResult = null
                                prediction = null
                                nav.navigate("identify") { popUpTo("identify") { inclusive = false }; launchSingleTop = true }
                            },
                            onRetry = {
                                productionResult = null
                                prediction = null
                                nav.navigate("recognizing") { popUpTo("recognition_issue") { inclusive = true } }
                            },
                        )
                    }
                }
                composable("result") {
                    val currentPrediction = prediction
                    if (currentPrediction == null) {
                        LaunchedEffect(Unit) { nav.navigate("identify") { popUpTo("result") { inclusive = true } } }
                    } else {
                        RecognitionResultScreen(
                            image = sessionImage,
                            prediction = currentPrediction,
                            productionResult = productionResult,
                            onBack = { nav.popBackStack() },
                            onRetry = {
                                productionResult = null
                                prediction = null
                                nav.navigate("recognizing") { popUpTo("result") { inclusive = true } }
                            },
                            saving = catchSaving,
                            saveError = catchSaveError,
                            onSave = { draft, feedback ->
                                val active = session
                                val selected = sessionImage
                                if (active == null || selected == null) {
                                    catchSaveError = "登录状态已失效，请重新登录"
                                } else {
                                    scope.launch {
                                        catchSaving = true
                                        catchSaveError = null
                                        runCatching {
                                            val upload = catchRepository.uploadImage(active.accessToken, File(selected.filePath))
                                            catchRepository.saveCatch(active.accessToken, upload, draft)
                                        }.onSuccess {
                                            catchSaving = false
                                            catchReload++
                                            inferenceAsset?.let { asset ->
                                                scope.launch {
                                                    val updated = inferenceRecorder.attachFeedback(asset, feedback)
                                                    feedbackRepository.submitInferenceOrQueue(updated)
                                                }
                                            }
                                            nav.navigate("my") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
                                        }.onFailure { error ->
                                            catchSaving = false
                                            if ((error as? ApiException)?.statusCode == 401) {
                                                logoutToLogin()
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
                        onOpenCatch = { nav.navigate("my") },
                    )
                }
                composable("my") {
                    val active = session
                    if (active == null) {
                        LaunchedEffect(Unit) { logoutToLogin() }
                    } else {
                        MyScreen(
                            session = active,
                            statistics = catchesState.statistics,
                            catches = catchesState.catches,
                            loading = catchesState.loading,
                            error = catchesState.error,
                            resolveImageUrl = catchRepository::resolveUrl,
                            onGuide = { nav.navigate("guide") },
                            onSpecies = { speciesId -> nav.navigate("species/${Uri.encode(speciesId)}") },
                            onRetry = { catchReload++ },
                            onLogout = { logoutToLogin() },
                        )
                    }
                }
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
