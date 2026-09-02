package com.yujian.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.yujian.ai.account.CatchSubmission
import com.yujian.ai.account.UserRepository
import com.yujian.ai.account.UserSession
import com.yujian.ai.account.UserSessionManager
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.feedback.FeedbackRepository
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.inference.InferenceRecorder
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeDetail
import com.yujian.ai.knowledge.FishKnowledgeRepository
import com.yujian.ai.model.*
import com.yujian.ai.ui.screens.*
import com.yujian.ai.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class BottomItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun YujianApp() {
    val context = LocalContext.current.applicationContext
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val recognitionPipeline = remember { FishRecognitionPipeline(context) }
    val feedbackRepository = remember { FeedbackRepository(context) }
    val inferenceRecorder = remember { InferenceRecorder(context) }
    val fishKnowledgeRepository = remember { FishKnowledgeRepository() }
    val userSessionManager = remember { UserSessionManager(context) }
    val userRepository = remember { UserRepository() }
    var sessionImage by remember { mutableStateOf<SelectedImage?>(null) }
    var productionResult by remember { mutableStateOf<ProductionRecognitionResult?>(null) }
    var prediction by remember { mutableStateOf<RecognitionPrediction?>(null) }
    var inferenceAsset by remember { mutableStateOf<InferenceAsset?>(null) }
    var catchRecord by remember { mutableStateOf(DemoData.catch) }
    var session by remember { mutableStateOf(userSessionManager.current()) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var catchItems by remember { mutableStateOf(emptyList<com.yujian.ai.account.RemoteCatch>()) }
    var catchStatistics by remember { mutableStateOf<com.yujian.ai.account.CatchStatistics?>(null) }
    var catchLoading by remember { mutableStateOf(false) }
    var catchError by remember { mutableStateOf<String?>(null) }
    var catchRefresh by remember { mutableStateOf(0) }
    var guideSpecies by remember { mutableStateOf(emptyList<FishGuideItem>()) }
    var guideLoading by remember { mutableStateOf(true) }
    var guideOfflinePreview by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var guideRetry by remember { mutableStateOf(0) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    DisposableEffect(Unit) { onDispose { recognitionPipeline.close() } }
    LaunchedEffect(Unit) { feedbackRepository.flushQueued() }
    LaunchedEffect(session?.userId, catchRefresh) {
        val active = session
        if (active == null) {
            catchItems = emptyList()
            catchStatistics = null
            catchError = null
            catchLoading = false
        } else {
            catchLoading = true
            catchError = null
            runCatching {
                val stats = userRepository.statistics(active)
                val items = userRepository.listCatches(active)
                stats to items
            }.onSuccess { (stats, items) ->
                catchStatistics = stats
                catchItems = items
            }.onFailure { error ->
                catchError = error.message ?: "鱼获数据暂不可用"
            }
            catchLoading = false
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

    val bottomItems = listOf(
        BottomItem("home", "首页") { Icon(Icons.Rounded.Home, null) },
        BottomItem("identify", "识鱼") { Icon(Icons.Rounded.PhotoCamera, null) },
        BottomItem("guide", "图鉴") { Icon(Icons.Rounded.Style, null) },
        BottomItem("my_catches", "鱼获") { Icon(Icons.Rounded.Water, null) },
        BottomItem("my", "我的") { Icon(Icons.Rounded.Person, null) },
    )
    val bottomVisibleRoutes = setOf("home", "guide", "my_catches", "my")

    Scaffold(containerColor = WarmBackground, bottomBar = {
        if (currentRoute in bottomVisibleRoutes) {
            NavigationBar(containerColor = Color.White) {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { nav.navigate(item.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = item.icon, label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WaterTeal, selectedTextColor = WaterTeal,
                            indicatorColor = WaterTeal.copy(alpha = .14f), unselectedIconColor = MutedInk, unselectedTextColor = MutedInk,
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
                            authLoading = true
                            authError = null
                            scope.launch {
                                runCatching { userRepository.login(username, password) }
                                    .onSuccess { loggedIn ->
                                        userSessionManager.save(loggedIn)
                                        session = loggedIn
                                        nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                    }
                                    .onFailure { error -> authError = error.message ?: "登录失败" }
                                authLoading = false
                            }
                        },
                        onRegister = {
                            authError = null
                            nav.navigate("register")
                        },
                    )
                }
                composable("register") {
                    RegisterScreen(
                        loading = authLoading,
                        error = authError,
                        onRegister = { username, password, nickname ->
                            authLoading = true
                            authError = null
                            scope.launch {
                                runCatching {
                                    userRepository.register(username, password, nickname)
                                    userRepository.login(username, password)
                                }.onSuccess { loggedIn ->
                                    userSessionManager.save(loggedIn)
                                    session = loggedIn
                                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                                }.onFailure { error -> authError = error.message ?: "注册失败" }
                                authLoading = false
                            }
                        },
                        onBack = {
                            authError = null
                            nav.popBackStack()
                        },
                    )
                }
                composable("home") {
                    HomeScreen(catchRecord, { nav.navigate("identify") }, { nav.navigate("guide") }, { nav.navigate("my_catches") })
                }
                composable("identify") {
                    IdentifyScreen(
                        image = sessionImage, onBack = { nav.popBackStack() },
                        onImageSelected = {
                            sessionImage = it
                            productionResult = null
                            prediction = null
                            inferenceAsset = null
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
                                nav.navigate("identify") {
                                    popUpTo("identify") { inclusive = false }
                                    launchSingleTop = true
                                }
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
                            onSave = save@{ saved, feedback ->
                                val active = session ?: return@save Result.failure(IOException("请先登录后再保存鱼获"))
                                val selected = sessionImage ?: return@save Result.failure(IOException("找不到原始照片"))
                                runCatching {
                                    val uploadedUrl = userRepository.uploadCatchImage(File(selected.filePath), active)
                                    val catchId = userRepository.createCatch(
                                        active,
                                        CatchSubmission(
                                            imageUrl = uploadedUrl,
                                            speciesId = saved.speciesKey,
                                            speciesName = saved.speciesName,
                                            confidence = (saved.confidence / 100f).coerceIn(0f, 1f),
                                            modelVersion = saved.modelVersion.orEmpty(),
                                            imageId = selected.imageId,
                                        ),
                                    )
                                    catchRecord = saved.copy(id = catchId)
                                    catchRefresh += 1
                                    runCatching {
                                        val asset = inferenceAsset
                                        val updated = asset?.let { inferenceRecorder.attachFeedback(it, feedback) }
                                        if (updated != null) {
                                            feedbackRepository.submitInferenceOrQueue(updated)
                                        } else {
                                            feedbackRepository.submitOrQueue(
                                                File(selected.filePath),
                                                feedback.copy(imageId = selected.imageId),
                                            )
                                        }
                                    }
                                    nav.navigate("my_catches") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
                                }
                            },
                            onOpenKnowledge = {
                                nav.navigate("species/${Uri.encode(currentPrediction.top1.speciesKey)}")
                            },
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
                    var detailRetry by remember(key) { mutableStateOf(0) }
                    LaunchedEffect(key, detailRetry) {
                        detailLoading = true
                        detailError = null
                        runCatching { fishKnowledgeRepository.getDetail(key) }
                            .onSuccess {
                                detail = it
                                detailOfflinePreview = false
                            }
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
                composable("my_catches") {
                    if (session == null) {
                        LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("my_catches") { inclusive = true } } }
                    } else {
                        MyCatchesScreen(
                            session = session,
                            statistics = catchStatistics,
                            catches = catchItems,
                            loading = catchLoading,
                            error = catchError,
                            resolveImageUrl = userRepository::resolveAssetUrl,
                            onBack = { nav.popBackStack() },
                            onRetry = { catchRefresh += 1 },
                            onOpenKnowledge = { speciesId -> nav.navigate("species/${Uri.encode(speciesId)}") },
                        )
                    }
                }
                composable("catch") { CatchDetailScreen(catchRecord, { nav.popBackStack() }, { nav.navigate("share") }) }
                composable("share") { ShareCenterScreen(catchRecord) { nav.popBackStack() } }
                composable("my") {
                    MyScreen(
                        onGuide = { nav.navigate("guide") },
                        onCatch = { nav.navigate("my_catches") },
                        session = session,
                        statistics = catchStatistics,
                        recentCatches = catchItems,
                        loading = catchLoading,
                        error = catchError,
                        resolveImageUrl = userRepository::resolveAssetUrl,
                        onLogin = { nav.navigate("login") },
                        onLogout = {
                            userSessionManager.clear()
                            session = null
                            catchItems = emptyList()
                            catchStatistics = null
                            nav.navigate("login") { popUpTo("home") { inclusive = true } }
                        },
                        onCatchClick = { item -> nav.navigate("species/${Uri.encode(item.speciesId)}") },
                    )
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
