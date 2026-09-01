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
    var sessionImage by remember { mutableStateOf<SelectedImage?>(null) }
    var productionResult by remember { mutableStateOf<ProductionRecognitionResult?>(null) }
    var prediction by remember { mutableStateOf<RecognitionPrediction?>(null) }
    var inferenceAsset by remember { mutableStateOf<InferenceAsset?>(null) }
    var catchRecord by remember { mutableStateOf(DemoData.catch) }
    var guideSpecies by remember { mutableStateOf(emptyList<FishGuideItem>()) }
    var guideLoading by remember { mutableStateOf(true) }
    var guideOfflinePreview by remember { mutableStateOf(false) }
    var guideError by remember { mutableStateOf<String?>(null) }
    var guideRetry by remember { mutableStateOf(0) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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

    val bottomItems = listOf(
        BottomItem("home", "首页") { Icon(Icons.Rounded.Home, null) },
        BottomItem("identify", "识鱼") { Icon(Icons.Rounded.PhotoCamera, null) },
        BottomItem("guide", "图鉴") { Icon(Icons.Rounded.Style, null) },
        BottomItem("catch", "鱼获") { Icon(Icons.Rounded.Water, null) },
        BottomItem("my", "我的") { Icon(Icons.Rounded.Person, null) },
    )
    val bottomVisibleRoutes = setOf("home", "guide", "catch", "my")

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
            NavHost(nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(catchRecord, { nav.navigate("identify") }, { nav.navigate("guide") }, { nav.navigate("catch") })
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
                            onSave = { saved, feedback ->
                                catchRecord = saved
                                sessionImage?.let { selected ->
                                    scope.launch {
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
                                }
                                nav.navigate("catch") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
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
                        onOpenCatch = { nav.navigate("catch") },
                    )
                }
                composable("catch") { CatchDetailScreen(catchRecord, { nav.popBackStack() }, { nav.navigate("share") }) }
                composable("share") { ShareCenterScreen(catchRecord) { nav.popBackStack() } }
                composable("my") { MyScreen({ nav.navigate("guide") }, { nav.navigate("catch") }) }
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
            category = localItem?.category ?: item.category,
            discovered = localItem?.discovered ?: false,
            catches = localItem?.catches ?: 0,
        )
    }
}
