package com.airouter.ui.screen.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.data.repository.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProviderListViewModel(
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    val providers: StateFlow<List<Provider>> = providerRepository.getAllProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /**
     * 添加自定义 Provider
     */
    fun addCustomProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        displayName: String,
    ) {
        viewModelScope.launch {
            val providerId = "custom_${System.currentTimeMillis()}"
            val provider = Provider(
                id = providerId,
                name = name,
                type = ProviderType.OPENAI_COMPATIBLE,
                defaultBaseUrl = baseUrl,
                isBuiltIn = false,
                isCustom = true,
                apiKey = apiKey,
                supportedModels = listOf(
                    AiModel(
                        modelId = modelId,
                        displayName = displayName,
                    )
                )
            )
            providerRepository.addCustomProvider(provider)
        }
    }
    
    /**
     * 删除自定义 Provider
     */
    fun deleteCustomProvider(providerId: String) {
        viewModelScope.launch {
            providerRepository.deleteCustomProvider(providerId)
        }
    }
}
