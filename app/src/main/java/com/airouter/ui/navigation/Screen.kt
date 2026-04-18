package com.airouter.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    data object ProviderList : Screen("providers")
    data object ProviderEdit : Screen("providers/{providerId}") {
        fun createRoute(providerId: String) = "providers/$providerId"
    }
    data object Settings : Screen("settings")
}
