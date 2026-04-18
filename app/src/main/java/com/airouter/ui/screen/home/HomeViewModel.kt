package com.airouter.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatSession
import com.airouter.data.model.Provider
import com.airouter.data.repository.ProviderRepository
import com.airouter.data.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SelectableModel(
    val provider: Provider,
    val model: AiModel,
    /** 显示用，格式："Provider名 · 模型名" */
    val label: String = "${provider.name} · ${model.displayName}",
)

class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    val sessions: StateFlow<List<ChatSession>> = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasConfiguredProviders: StateFlow<Boolean> = providerRepository.getConfiguredProviders()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 所有可选模型列表（按 Provider 分组） */
    private val _selectableModels = MutableStateFlow<List<SelectableModel>>(emptyList())
    val selectableModels: StateFlow<List<SelectableModel>> = _selectableModels.asStateFlow()

    /** 是否显示模型选择 Sheet */
    private val _showModelPicker = MutableStateFlow(false)
    val showModelPicker: StateFlow<Boolean> = _showModelPicker.asStateFlow()

    /** 一次性创建完成的 Session ID，UI 监听后跳转 */
    private val _createdSessionId = MutableStateFlow<String?>(null)
    val createdSessionId: StateFlow<String?> = _createdSessionId.asStateFlow()

    fun consumeCreatedSession() {
        _createdSessionId.value = null
    }

    fun onFabClick() {
        viewModelScope.launch {
            val providers = providerRepository.getConfiguredProvidersOnce()
            if (providers.isEmpty()) {
                _showModelPicker.value = true
                return@launch
            }
            // 如果只有一个 provider 且只有一个模型，直接创建
            val models = providers.flatMap { p ->
                p.supportedModels.map { m -> SelectableModel(p, m) }
            }
            if (models.size == 1) {
                val session = ChatSession(
                    providerId = models.first().provider.id,
                    modelId = models.first().model.modelId,
                    title = "新对话",
                )
                sessionRepository.createSession(session)
                _createdSessionId.value = session.id
            } else {
                _selectableModels.value = models
                _showModelPicker.value = true
            }
        }
    }

    fun selectModel(selectableModel: SelectableModel) {
        viewModelScope.launch {
            val session = ChatSession(
                providerId = selectableModel.provider.id,
                modelId = selectableModel.model.modelId,
                title = "新对话",
            )
            sessionRepository.createSession(session)
            _showModelPicker.value = false
            _createdSessionId.value = session.id
        }
    }

    fun dismissModelPicker() {
        _showModelPicker.value = false
    }

    fun showModelPickerWithoutProviders() {
        _showModelPicker.value = true
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            sessionRepository.deleteSession(session)
        }
    }

    fun toggleSessionPin(session: ChatSession) {
        viewModelScope.launch {
            sessionRepository.togglePin(session.id, !session.isPinned)
        }
    }
}
