package com.airouter.ui.screen.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.data.repository.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

    // ---- 模型拉取 ----

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    sealed class FetchState {
        object Idle : FetchState()
        object Loading : FetchState()
        data class Success(val models: List<RemoteModel>) : FetchState()
        data class Error(val message: String) : FetchState()
    }

    data class RemoteModel(
        val id: String,
        val ownedBy: String = "",
    )

    /**
     * 从 OpenAI 兼容端点拉取模型列表（GET /v1/models）
     */
    fun fetchModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _fetchState.value = FetchState.Loading
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                // 确保 baseUrl 以 /v1 结尾
                val apiBase = baseUrl.trimEnd('/') + "/models"
                val request = Request.Builder()
                    .url(apiBase)
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    _fetchState.value = FetchState.Error("HTTP ${response.code}: ${body.take(200)}")
                    return@launch
                }

                val json = JSONObject(body)
                val dataArray = json.optJSONArray("data") ?: run {
                    _fetchState.value = FetchState.Error("返回格式异常：未找到 data 字段")
                    return@launch
                }

                val models = (0 until dataArray.length()).map { i ->
                    val obj = dataArray.getJSONObject(i)
                    RemoteModel(
                        id = obj.optString("id", ""),
                        ownedBy = obj.optString("owned_by", ""),
                    )
                }.filter { it.id.isNotBlank() }
                    .sortedBy { it.id.lowercase() }

                if (models.isEmpty()) {
                    _fetchState.value = FetchState.Error("未找到可用模型")
                } else {
                    _fetchState.value = FetchState.Success(models)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.javaClass.simpleName
                _fetchState.value = FetchState.Error("请求失败: $errorMsg")
            }
        }
    }

    fun clearFetchState() {
        _fetchState.value = FetchState.Idle
    }
}
