package com.airouter.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airouter.ui.screen.chat.ChatScreen
import com.airouter.ui.screen.home.HomeScreen
import com.airouter.ui.screen.home.HomeViewModel
import com.airouter.ui.screen.home.SelectableModel
import com.airouter.ui.screen.provider.ProviderEditScreen
import com.airouter.ui.screen.provider.ProviderListScreen
import com.airouter.ui.screen.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = koinViewModel()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home, "对话", Icons.Filled.Chat, Icons.Outlined.Chat),
        BottomNavItem(Screen.ProviderList, "模型", Icons.Filled.Webhook, Icons.Outlined.Webhook),
        BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { it.screen.route == currentDestination?.route }

    // 模型选择 Sheet 状态
    val showModelPicker by homeViewModel.showModelPicker.collectAsState()
    val selectableModels by homeViewModel.selectableModels.collectAsState()

    // 纯 Box 容器，不使用 Scaffold
    // IME 完全由各页面自己在 View 层处理
    Box(modifier = modifier.fillMaxSize()) {

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToChat = { sessionId ->
                        navController.navigate(Screen.Chat.createRoute(sessionId))
                    },
                    onNavigateToProviders = {
                        navController.navigate(Screen.ProviderList.route)
                    },
                    viewModel = homeViewModel,
                )
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) {
                ChatScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ProviderList.route) {
                ProviderListScreen(
                    onNavigateToEdit = { providerId ->
                        navController.navigate(Screen.ProviderEdit.createRoute(providerId))
                    }
                )
            }

            composable(
                route = Screen.ProviderEdit.route,
                arguments = listOf(navArgument("providerId") { type = NavType.StringType })
            ) {
                ProviderEditScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }

        // 底部导航栏，浮在 NavHost 上面
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }

        // 模型选择 Sheet —— 渲染在 NavigationBar 上方
        if (showModelPicker) {
            ModalBottomSheet(
                onDismissRequest = { homeViewModel.dismissModelPicker() },
                sheetState = rememberModalBottomSheetState(),
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    if (selectableModels.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "还没有配置模型",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请先到模型页面配置至少一个 API Key",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                homeViewModel.dismissModelPicker()
                                navController.navigate(Screen.ProviderList.route)
                            }) {
                                Text("去配置")
                            }
                        }
                    } else {
                        Text(
                            text = "选择模型",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val grouped = selectableModels.groupBy { it.provider.name }
                            grouped.forEach { (providerName, models) ->
                                item {
                                    Text(
                                        text = providerName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    )
                                }
                                items(models) { sm ->
                                    ListItem(
                                        headlineContent = { Text(sm.model.displayName) },
                                        supportingContent = {
                                            val ctxK = sm.model.contextLength / 1024
                                            val suffix = if (sm.model.supportsVision) " · 支持图片" else ""
                                            Text("${sm.model.modelId} · ${ctxK}K 上下文${suffix}")
                                        },
                                        modifier = Modifier.clickable { homeViewModel.selectModel(sm) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)
