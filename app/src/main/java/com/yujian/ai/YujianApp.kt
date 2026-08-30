package com.yujian.ai

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yujian.ai.model.DemoData
import com.yujian.ai.ui.screens.CatchDetailScreen
import com.yujian.ai.ui.screens.FishGuideHomeScreen
import com.yujian.ai.ui.screens.FishSpeciesDetailScreen
import com.yujian.ai.ui.screens.HomeScreen
import com.yujian.ai.ui.screens.IdentifyScreen
import com.yujian.ai.ui.screens.MyScreen
import com.yujian.ai.ui.screens.RecognitionResultScreen
import com.yujian.ai.ui.screens.RecognizingScreen
import com.yujian.ai.ui.screens.ShareCenterScreen
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

data class BottomItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun YujianApp() {
    val nav = rememberNavController()
    var sessionImage by remember { mutableStateOf<Bitmap?>(null) }
    var catchRecord by remember { mutableStateOf(DemoData.catch) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem("home", "首页") { Icon(Icons.Rounded.Home, contentDescription = null) },
        BottomItem("identify", "识鱼") { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
        BottomItem("guide", "图鉴") { Icon(Icons.Rounded.Style, contentDescription = null) },
        BottomItem("catch", "鱼获") { Icon(Icons.Rounded.Water, contentDescription = null) },
        BottomItem("my", "我的") { Icon(Icons.Rounded.Person, contentDescription = null) },
    )
    val bottomVisibleRoutes = setOf("home", "guide", "catch", "my")

    Scaffold(
        containerColor = WarmBackground,
        bottomBar = {
            if (currentRoute in bottomVisibleRoutes) {
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
                                selectedIconColor = WaterTeal,
                                selectedTextColor = WaterTeal,
                                indicatorColor = WaterTeal.copy(alpha = .14f),
                                unselectedIconColor = MutedInk,
                                unselectedTextColor = MutedInk,
                            ),
                        )
                    }
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).background(WarmBackground)) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        recentCatch = catchRecord,
                        onIdentify = { nav.navigate("identify") },
                        onGuide = { nav.navigate("guide") },
                        onRecentCatch = { nav.navigate("catch") },
                    )
                }

                composable("identify") {
                    IdentifyScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        onImageSelected = { sessionImage = it },
                        onStartRecognition = {
                            if (sessionImage != null) nav.navigate("recognizing")
                        },
                    )
                }

                composable("recognizing") {
                    RecognizingScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        onFinished = {
                            nav.navigate("result") {
                                popUpTo("recognizing") { inclusive = true }
                            }
                        },
                    )
                }

                composable("result") {
                    RecognitionResultScreen(
                        image = sessionImage,
                        onBack = { nav.popBackStack() },
                        onRetry = {
                            sessionImage = null
                            nav.navigate("identify") {
                                popUpTo("identify") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onSave = { saved ->
                            catchRecord = saved
                            nav.navigate("catch") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable("guide") {
                    FishGuideHomeScreen(onSpeciesClick = { fish -> nav.navigate("species/${fish.key}") })
                }

                composable(
                    route = "species/{key}",
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                ) { entry ->
                    val key = entry.arguments?.getString("key") ?: "grass_carp"
                    val fish = DemoData.species.firstOrNull { it.key == key } ?: DemoData.species.first()
                    FishSpeciesDetailScreen(
                        fish = fish,
                        onBack = { nav.popBackStack() },
                        onOpenCatch = { nav.navigate("catch") },
                    )
                }

                composable("catch") {
                    CatchDetailScreen(
                        catch = catchRecord,
                        onBack = { nav.popBackStack() },
                        onShare = { nav.navigate("share") },
                    )
                }

                composable("share") {
                    ShareCenterScreen(catch = catchRecord, onBack = { nav.popBackStack() })
                }

                composable("my") {
                    MyScreen(
                        onGuide = { nav.navigate("guide") },
                        onCatch = { nav.navigate("catch") },
                    )
                }
            }
        }
    }
}
