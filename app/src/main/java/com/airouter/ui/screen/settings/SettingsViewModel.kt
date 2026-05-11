package com.airouter.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.airouter.data.local.prefs.AppConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsViewModel(
    private val appConfig: AppConfig,
) : ViewModel() {

    val streamEnabled: StateFlow<Boolean> = MutableStateFlow(appConfig.streamEnabled)
    val showTokenUsage: StateFlow<Boolean> = MutableStateFlow(appConfig.showTokenUsage)
    val debugLogEnabled: StateFlow<Boolean> = MutableStateFlow(appConfig.debugLogEnabled)

    fun setStreamEnabled(enabled: Boolean) {
        appConfig.streamEnabled = enabled
        (streamEnabled as MutableStateFlow).value = enabled
    }

    fun setShowTokenUsage(enabled: Boolean) {
        appConfig.showTokenUsage = enabled
        (showTokenUsage as MutableStateFlow).value = enabled
    }

    fun setDebugLogEnabled(enabled: Boolean) {
        appConfig.debugLogEnabled = enabled
        (debugLogEnabled as MutableStateFlow).value = enabled
    }
}
