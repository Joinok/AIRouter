package com.airouter.ui.screen.provider

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.Provider
import com.airouter.data.repository.ProviderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProviderEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    private val providerId: String = savedStateHandle["providerId"] ?: ""

    private val _provider = MutableStateFlow<Provider?>(null)
    val provider: StateFlow<Provider?> = _provider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _customBaseUrl = MutableStateFlow("")
    val customBaseUrl: StateFlow<String> = _customBaseUrl.asStateFlow()

    private val _showApiKey = MutableStateFlow(false)
    val showApiKey: StateFlow<Boolean> = _showApiKey.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _validationResult = MutableStateFlow<Boolean?>(null)
    val validationResult: StateFlow<Boolean?> = _validationResult.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val models: StateFlow<List<com.airouter.data.model.AiModel>> = _provider
        .map { it?.supportedModels ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val provider = providerRepository.getProviderById(providerId)
            _provider.value = provider
            _apiKey.value = provider?.apiKey ?: ""
            _customBaseUrl.value = provider?.customBaseUrl ?: ""
        }
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
        _validationResult.value = null
    }

    fun updateCustomBaseUrl(url: String) {
        _customBaseUrl.value = url
    }

    fun toggleShowApiKey() {
        _showApiKey.value = !_showApiKey.value
    }

    fun validateApiKey() {
        viewModelScope.launch {
            _isValidating.value = true
            try {
                val provider = _provider.value?.copy(
                    apiKey = _apiKey.value,
                    customBaseUrl = _customBaseUrl.value,
                ) ?: return@launch

                kotlinx.coroutines.delay(1000)
                // TODO: 实际调用 provider.validateApiKey()
                _validationResult.value = _apiKey.value.isNotBlank()
            } catch (e: Exception) {
                _validationResult.value = false
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            val provider = _provider.value?.copy(
                apiKey = _apiKey.value,
                customBaseUrl = _customBaseUrl.value,
                enabled = _apiKey.value.isNotBlank(),
            ) ?: return@launch

            providerRepository.updateProviderConfig(provider)
            _saved.value = true
        }
    }
}
