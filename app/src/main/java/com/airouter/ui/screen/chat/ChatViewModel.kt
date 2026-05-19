package com.airouter.ui.screen.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.local.AttachmentStorage
import com.airouter.data.model.*
import com.airouter.data.repository.ChatRepository
import com.airouter.data.repository.ProviderRepository
import com.airouter.data.repository.SessionRepository
import com.airouter.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration.Companion.seconds
import java.util.UUID


class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val providerRepository: ProviderRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val attachmentStorage: AttachmentStorage,
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId)
            _currentModel.value = session?.modelId
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

            val userMessage = ChatMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                attachments = currentAttachments,
            )
            sessionRepository.addMessage(userMessage)

            _inputText.value = ""
            _pendingAttachments.value = emptyList()

            if (session.messageCount == 0) {
                val title = content.take(30) + if (content.length > 30) "..." else ""
                sessionRepository.updateSessionTitle(sessionId, title)
            }

            val assistantMessageId = UUID.randomUUID().toString()
            val assistantMessage = ChatMessage(
                id = assistantMessageId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                modelId = session.modelId,
                providerId = session.providerId,
                isStreaming = true,
                isReasoning = false,
            )
            sessionRepository.addMessage(assistantMessage)
            _messages.update { it + userMessage + assistantMessage }

            _isSending.value = true

            var reasoningContent = StringBuilder()
            var fullContent = StringBuilder()
            var lastUsage: TokenUsage? = null
            var hasError = false
            var errorMsgStr: String? = null

            try {
                sendChatMessageUseCase.invoke(session, userMessage, assistantMessageId)
                    .timeout(300.seconds)  // 5分钟超时，多模态模型图片编码耗时长
                    .collect { chunk ->
                        if (chunk.isReasoning) {
                            // 推理过程，先显示给用户
                            reasoningContent.clear()
                            reasoningContent.append(chunk.content)
                            updateMessageInMemory(assistantMessageId, chunk.content, isStreaming = true, isReasoning = true)
                        } else {
                            // 最终答案（覆盖推理过程）
                            fullContent.clear()
                            fullContent.append(chunk.content)
                            if (chunk.usage != null) lastUsage = chunk.usage
                            updateMessageInMemory(assistantMessageId, chunk.content, isStreaming = true, isReasoning = false, usage = lastUsage)
                        }
                    }
            } catch (e: CancellationException) {
                if (fullContent.isNotEmpty()) {
                    sessionRepository.updateMessage(assistantMessage.copy(
                        content = fullContent.toString(),
                        isStreaming = false,
                        tokenUsage = lastUsage,
                    ))
                } else {
                    sessionRepository.deleteMessage(assistantMessageId)
                    _messages.update { it.filter { msg -> msg.id != assistantMessageId } }
                }
                throw e
            } catch (e: TimeoutCancellationException) {
                // 超时保护：flow 120秒未完成
                hasError = true
                errorMsgStr = "响应超时，请重试"
            } catch (e: Exception) {
                hasError = true
                errorMsgStr = e.message ?: "未知错误"
            } finally {
                _isSending.value = false
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
                        isReasoning = false,
                        isError = hasError,
                        errorMessage = errorMsgStr,
                        usage = lastUsage,
                    )
                } else {
                    // 无内容：出错时直接把错误信息作为消息显示，正常时删除空消息
                    val displayContent = if (hasError) "⚠️ ${errorMsgStr}" else ""
                    val finalMessage = if (hasError) {
                        assistantMessage.copy(
                            content = displayContent,
                            isStreaming = false,
                            isError = true,
                            errorMessage = errorMsgStr,
                        )
                    } else {
                        assistantMessage.copy(isStreaming = false)
                    }
                    sessionRepository.updateMessage(finalMessage)
                    updateMessageInMemory(
                        assistantMessageId,
                        displayContent,
                        isStreaming = false,
                        isReasoning = false,
                        isError = hasError,
                        errorMessage = if (hasError) errorMsgStr else null,
                    )
                }
            }
        }
    }

    private fun updateMessageInMemory(
        messageId: String,
        content: String,
        isStreaming: Boolean = true,
        isReasoning: Boolean = false,
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
                        isReasoning = isReasoning,
                        tokenUsage = usage ?: msg.tokenUsage,
                        isError = isError,
                        errorMessage = errorMessage,
                    )
                } else msg
            }
        }
    }
}
