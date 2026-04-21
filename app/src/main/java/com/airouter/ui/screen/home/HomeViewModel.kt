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

data class ProviderGroup(
    val provider: Provider,
    val models: List<AiModel>,
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

    /** 按 Provider 分组的模型列表 */
    private val _providerGroups = MutableStateFlow<List<ProviderGroup>>(emptyList())
    val providerGroups: StateFlow<List<ProviderGroup>> = _providerGroups.asStateFlow()

    /** 当前选中展开的 Provider（二级界面） */
    private val _selectedProviderGroup = MutableStateFlow<ProviderGroup?>(null)
    val selectedProviderGroup: StateFlow<ProviderGroup?> = _selectedProviderGroup.asStateFlow()

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
            val groups = providers.map { p ->
                ProviderGroup(p, p.supportedModels)
            }.filter { it.models.isNotEmpty() }
            if (groups.isEmpty()) {
                _showModelPicker.value = true
                return@launch
            }
            // 只有一个 provider 且只有一个模型，直接创建
            if (groups.size == 1 && groups[0].models.size == 1) {
                val p = groups[0].provider
                val m = groups[0].models[0]
                val session = ChatSession(
                    providerId = p.id,
                    modelId = m.modelId,
                    title = "新对话",
                )
                sessionRepository.createSession(session)
                _createdSessionId.value = session.id
            } else {
                _providerGroups.value = groups
                _selectedProviderGroup.value = null
                _showModelPicker.value = true
            }
        }
    }

    /** 展开某个 Provider 的模型列表（二级界面） */
    fun selectProviderGroup(group: ProviderGroup) {
        _selectedProviderGroup.value = group
    }

    /** 返回 Provider 列表（一级界面） */
    fun backToProviderList() {
        _selectedProviderGroup.value = null
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
        _selectedProviderGroup.value = null
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
