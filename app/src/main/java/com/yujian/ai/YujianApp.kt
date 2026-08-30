package com.yujian.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yujian.ai.model.DemoData
import com.yujian.ai.ui.screens.CatchDetailScreen
import com.yujian.ai.ui.screens.FishGuideHomeScreen
import com.yujian.ai.ui.screens.FishSpeciesDetailScreen
import com.yujian.ai.ui.screens.ShareCenterScreen
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

data class BottomItem(val label: String, val icon: @Composable () -> Unit)

@Composable
fun YujianApp() {
    val nav = rememberNavController()
    val bottomItems = listOf(
        BottomItem("首页") { Icon(Icons.Rounded.Home, contentDescription = null) },
        BottomItem("识鱼") { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
        BottomItem("图鉴") { Icon(Icons.Rounded.Style, contentDescription = null) },
        BottomItem("鱼获") { Icon(Icons.Rounded.Water, contentDescription = null) },
        BottomItem("我的") { Icon(Icons.Rounded.Person, contentDescription = null) },
    )

    Scaffold(
        containerColor = WarmBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                bottomItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == 2,
                        onClick = {},
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
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).background(WarmBackground)) {
            NavHost(navController = nav, startDestination = "guide") {
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
                        catch = DemoData.catch,
                        onBack = { nav.popBackStack() },
                        onShare = { nav.navigate("share") },
                    )
                }
                composable("share") {
                    ShareCenterScreen(catch = DemoData.catch, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
