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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.yujian.ai.ai.FishRecognitionPipeline
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.feedback.FeedbackRepository
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.inference.InferenceRecorder
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
    var sessionImage by remember { mutableStateOf<SelectedImage?>(null) }
    var productionResult by remember { mutableStateOf<ProductionRecognitionResult?>(null) }
    var prediction by remember { mutableStateOf<RecognitionPrediction?>(null) }
    var inferenceAsset by remember { mutableStateOf<InferenceAsset?>(null) }
    var catchRecord by remember { mutableStateOf(DemoData.catch) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    DisposableEffect(Unit) { onDispose { recognitionPipeline.close() } }
    LaunchedEffect(Unit) { feedbackRepository.flushQueued() }

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
                composable("guide") { FishGuideHomeScreen { fish -> nav.navigate("species/${fish.key}") } }
                composable("species/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                    val key = entry.arguments?.getString("key") ?: "grass_carp"
                    val fish = DemoData.species.firstOrNull { it.key == key } ?: DemoData.species.first()
                    FishSpeciesDetailScreen(fish, { nav.popBackStack() }, { nav.navigate("catch") })
                }
                composable("catch") { CatchDetailScreen(catchRecord, { nav.popBackStack() }, { nav.navigate("share") }) }
                composable("share") { ShareCenterScreen(catchRecord) { nav.popBackStack() } }
                composable("my") { MyScreen({ nav.navigate("guide") }, { nav.navigate("catch") }) }
            }
        }
    }
}
