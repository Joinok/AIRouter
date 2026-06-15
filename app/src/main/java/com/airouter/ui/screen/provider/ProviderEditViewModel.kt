package com.airouter.ui.screen.provider

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.repository.ProviderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class ProviderEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val providerRepository: ProviderRepository,
    private val okHttpClient: OkHttpClient,
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

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _fetchModelsError = MutableStateFlow<String?>(null)
    val fetchModelsError: StateFlow<String?> = _fetchModelsError.asStateFlow()

    private val _fetchedModels = MutableStateFlow<List<AiModel>?>(null)

    /** 额外请求体参数 */
    private val _extraBodyFields = MutableStateFlow<Map<String, String>>(emptyMap())
    val extraBodyFields: StateFlow<Map<String, String>> = _extraBodyFields.asStateFlow()

    val models: StateFlow<List<AiModel>> = combine(_provider, _fetchedModels) { provider, fetched ->
        fetched ?: provider?.supportedModels ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val provider = providerRepository.getProviderById(providerId)
            _provider.value = provider
            _apiKey.value = provider?.apiKey ?: ""
            _customBaseUrl.value = provider?.customBaseUrl ?: ""
            _extraBodyFields.value = provider?.extraBodyFields ?: emptyMap()
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
                _validationResult.value = _apiKey.value.isNotBlank()
            } catch (e: Exception) {
                _validationResult.value = false
            } finally {
                _isValidating.value = false
            }
        }
    }

    /** 添加或更新一个额外参数 */
    fun upsertExtraParam(key: String, value: String) {
        val current = _extraBodyFields.value.toMutableMap()
        if (value.isBlank()) {
            current.remove(key)
        } else {
            current[key] = value
        }
        _extraBodyFields.value = current
    }

    /** 删除一个额外参数 */
    fun removeExtraParam(key: String) {
        val current = _extraBodyFields.value.toMutableMap()
        current.remove(key)
        _extraBodyFields.value = current
    }

    fun save() {
        viewModelScope.launch {
            val provider = _provider.value?.copy(
                apiKey = _apiKey.value,
                customBaseUrl = _customBaseUrl.value,
                enabled = _apiKey.value.isNotBlank(),
                extraBodyFields = _extraBodyFields.value,
            ) ?: return@launch

            providerRepository.updateProviderConfig(provider)
            // 保存自定义参数
            providerRepository.saveExtraBodyFields(providerId, _extraBodyFields.value)
            _saved.value = true
        }
    }

    fun fetchModels() {
        viewModelScope.launch {
            _isFetchingModels.value = true
            _fetchModelsError.value = null
            try {
                val provider = _provider.value?.copy(
                    apiKey = _apiKey.value,
                    customBaseUrl = _customBaseUrl.value,
                ) ?: run {
                    _fetchModelsError.value = "Provider 未找到"
                    return@launch
                }

                if (_apiKey.value.isBlank()) {
                    _fetchModelsError.value = "请先填写 API Key"
                    return@launch
                }

                val result = providerRepository.fetchModels(provider, okHttpClient)
                if (result.isNotEmpty()) {
                    _fetchedModels.value = result
                    val updated = providerRepository.getProviderById(providerId)
                    _provider.value = updated
                } else {
                    val builtInCount = provider.supportedModels.size
                    if (builtInCount > 0) {
                        _fetchedModels.value = emptyList()
                    } else {
                        _fetchModelsError.value = "API 返回模型列表为空，且无内置模型"
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e::class.simpleName ?: "未知错误"
                val builtInCount = _provider.value?.supportedModels?.size ?: 0
                if (builtInCount > 0) {
                    _fetchModelsError.value = "动态拉取失败（$errorMsg），使用内置 ${builtInCount} 个模型"
                } else {
                    _fetchModelsError.value = "拉取失败：$errorMsg"
                }
            } finally {
                _isFetchingModels.value = false
            }
        }
    }
}
