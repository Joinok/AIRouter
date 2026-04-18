package com.airouter.ui.screen.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.local.AttachmentStorage
import com.airouter.data.model.AttachmentType
import com.airouter.data.model.ChatMessage
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.MessageAttachment
import com.airouter.data.model.MessageRole
import com.airouter.data.model.TokenUsage
import com.airouter.data.repository.ChatRepository
import com.airouter.data.repository.ProviderRepository
import com.airouter.data.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID


class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val providerRepository: ProviderRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _currentModel = MutableStateFlow<String?>(null)
    val currentModel: StateFlow<String?> = _currentModel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<MessageAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<MessageAttachment>> = _pendingAttachments.asStateFlow()

    // UI 显示用内存 StateFlow，不再依赖 Room Flow
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var attachmentStorage: AttachmentStorage? = null

    fun initStorage(context: Context) {
        attachmentStorage = AttachmentStorage(context)
    }

    init {
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId)
            _currentModel.value = session?.modelId

            // 从数据库加载历史消息到内存
            _messages.value = sessionRepository.getMessagesBySessionOnce(sessionId)
        }
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            sessionRepository.deleteMessage(messageId)
            _messages.update { it.filter { msg -> msg.id != messageId } }
        }
    }

    /**
     * 处理从系统选择器返回的 Uri 列表
     */
    fun handleSelectedUris(uris: List<Uri>, context: Context) {
        val storage = attachmentStorage ?: AttachmentStorage(context)
        val newAttachments = uris.mapNotNull { uri ->
            storage.saveFromUri(uri)?.let { info ->
                MessageAttachment(
                    type = if (info.mimeType.startsWith("image/")) AttachmentType.IMAGE else AttachmentType.FILE,
                    localPath = info.localPath,
                    mimeType = info.mimeType,
                    fileName = info.fileName,
                    fileSize = info.fileSize,
                )
            }
        }
        _pendingAttachments.update { it + newAttachments }
    }

    fun removeAttachment(attachment: MessageAttachment) {
        _pendingAttachments.update { it - attachment }
    }

    fun clearAttachments() {
        _pendingAttachments.value = emptyList()
    }

    fun sendMessage() {
        val content = _inputText.value.trim()
        val currentAttachments = _pendingAttachments.value
        if ((content.isBlank() && currentAttachments.isEmpty()) || _isSending.value) return

        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId) ?: run {
                _errorMessage.value = "会话不存在"
                return@launch
            }

            val provider = providerRepository.getProviderById(session.providerId)
            if (provider == null) {
                _errorMessage.value = "未找到 Provider: ${session.providerId}，请检查配置"
                return@launch
            }

            if (!provider.isConfigured) {
                _errorMessage.value = "${provider.name} 尚未配置 API Key，请先到模型页面填写"
                return@launch
            }

            // 构造用户消息
            val userMessage = ChatMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                attachments = currentAttachments,
            )
            // 写入数据库（一次性）
            sessionRepository.addMessage(userMessage)

            // 清空输入和附件
            _inputText.value = ""
            _pendingAttachments.value = emptyList()

            // 如果是第一条消息，用消息内容作为标题
            if (session.messageCount == 0) {
                val title = content.take(30) + if (content.length > 30) "..." else ""
                sessionRepository.updateSessionTitle(sessionId, title)
            }

            // 创建 AI 占位消息（先写数据库占位，保证消息顺序）
            val assistantMessageId = UUID.randomUUID().toString()
            val assistantMessage = ChatMessage(
                id = assistantMessageId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                modelId = session.modelId,
                providerId = session.providerId,
                isStreaming = true,
            )
            sessionRepository.addMessage(assistantMessage)

            // 更新内存中的消息列表（UI 立即看到）
            _messages.update { it + userMessage + assistantMessage }

            _isSending.value = true

            var fullContent = StringBuilder()
            var lastUsage: TokenUsage? = null
            var hasError = false
            var errorMsgStr: String? = null

            try {
                // 获取历史消息，并在发送前把附件的 localPath 转为 base64 data url
                val storage = attachmentStorage ?: return@launch
                val history = _messages.value
                    .filter { !it.isError }
                    .filterNot { it.role == MessageRole.ASSISTANT && it.content.isBlank() }
                    .map { msg ->
                        msg.copy(
                            id = "",
                            sessionId = "",
                            attachments = msg.attachments.map { att ->
                                att.copy(localPath = "data:${att.mimeType};base64,${storage.readAsBase64(att.localPath)}")
                            }
                        )
                    }
                    .let { fixMessageOrder(it) }

                val request = ChatRequest(
                    apiKey = provider.apiKey,
                    baseUrl = provider.effectiveBaseUrl,
                    modelId = session.modelId,
                    messages = history,
                    temperature = session.temperature,
                    maxTokens = session.maxTokens,
                    systemPrompt = session.systemPrompt,
                    stream = true,
                )

                fullContent = StringBuilder()

                // SSE flow 消费：SseParser 已在回调线程上累积内容，chunk.content 是完整文本
                // 注意：不用 sample 节流。高速短流（如智谱 1ms 内吐完全部 chunk）
                // 会导致 sample 窗口从未触发，fullContent 丢失。Compose 有自己的
                // recomposition 合并机制，直接 collect 即可。
                chatRepository.streamChat(provider, request)
                    .collect { chunk ->
                        // 每个 chunk 都立即同步到 fullContent
                        fullContent.clear()
                        fullContent.append(chunk.content)
                        if (chunk.usage != null) {
                            lastUsage = chunk.usage
                        }
                        // 更新内存中的消息内容（UI 实时刷新）
                        updateMessageInMemory(assistantMessageId, chunk.content, isStreaming = true, usage = lastUsage)
                    }

            } catch (e: CancellationException) {
                // 用户退出页面或取消请求，保存已收到的内容
                if (fullContent.isNotEmpty()) {
                    val saved = assistantMessage.copy(
                        content = fullContent.toString(),
                        isStreaming = false,
                        tokenUsage = lastUsage,
                    )
                    sessionRepository.updateMessage(saved)
                } else {
                    sessionRepository.deleteMessage(assistantMessageId)
                    _messages.update { it.filter { msg -> msg.id != assistantMessageId } }
                }
                throw e
            } catch (e: Exception) {
                hasError = true
                errorMsgStr = e.message
                _errorMessage.value = "请求失败: ${e.message}"
            } finally {
                _isSending.value = false
                // 兜底：无论正常结束还是异常（非取消），都确保 fullContent 写入数据库
                // 这样即使 sample(50ms) 吞了最后一个 chunk，也不会丢内容
                if (fullContent.isNotEmpty()) {
                    val finalMessage = assistantMessage.copy(
                        content = fullContent.toString(),
                        isStreaming = false,
                        tokenUsage = lastUsage,
                        isError = hasError,
                        errorMessage = errorMsgStr,
                    )
                    sessionRepository.updateMessage(finalMessage)
                    updateMessageInMemory(
                        assistantMessageId,
                        finalMessage.content,
                        isStreaming = false,
                        isError = hasError,
                        errorMessage = errorMsgStr,
                        usage = lastUsage,
                    )
                } else if (!hasError) {
                    // 正常结束但没收到任何内容（不是错误也不是取消），删掉空占位
                    sessionRepository.deleteMessage(assistantMessageId)
                    _messages.update { it.filter { msg -> msg.id != assistantMessageId } }
                }
            }
        }
    }

    /**
     * 更新内存中指定消息的内容（不触发数据库 I/O）
     */
    private fun updateMessageInMemory(
        messageId: String,
        content: String,
        isStreaming: Boolean = true,
        usage: TokenUsage? = null,
        isError: Boolean = false,
        errorMessage: String? = null,
    ) {
        _messages.update { messages ->
            messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(
                        content = content,
                        isStreaming = isStreaming,
                        tokenUsage = usage ?: msg.tokenUsage,
                        isError = isError,
                        errorMessage = errorMessage,
                    )
                } else msg
            }
        }
    }

    /**
     * 修复消息 role 交替顺序。
     * 过滤掉 isError 消息后可能导致连续相同 role（如 user→user），
     * 合并连续相同 role 的消息为一个，确保 API 兼容。
     */
    private fun fixMessageOrder(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return emptyList()
        val result = mutableListOf<ChatMessage>()
        for (msg in messages) {
            val last = result.lastOrNull()
            if (last != null && last.role == msg.role) {
                // 合并到上一条同 role 消息
                result[result.lastIndex] = last.copy(
                    content = buildString {
                        append(last.content)
                        if (last.content.isNotBlank() && msg.content.isNotBlank()) append("\n")
                        append(msg.content)
                    },
                    attachments = last.attachments + msg.attachments,
                )
            } else {
                result.add(msg)
            }
        }
        return result
    }
}
