package com.airouter.domain.usecase

import com.airouter.data.local.AttachmentStorage
import com.airouter.data.model.*
import com.airouter.data.repository.ChatRepository
import com.airouter.data.repository.ProviderRepository
import com.airouter.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SendChatMessageUseCase(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
    private val providerRepository: ProviderRepository,
    private val attachmentStorage: AttachmentStorage,
) {
    operator fun invoke(
        session: ChatSession,
        userMessage: ChatMessage,
        assistantMessageId: String,
    ): Flow<ChatChunk> = flow {
        val provider = providerRepository.getProviderById(session.providerId)
            ?: throw IllegalStateException("未找到 Provider: ${session.providerId}")

        if (!provider.isConfigured) {
            throw IllegalStateException("${provider.name} 尚未配置 API Key，请先到模型页面填写")
        }

        val history = sessionRepository.getMessagesBySessionOnce(session.id)
            .filter { !it.isError }
            .filterNot { it.role == MessageRole.ASSISTANT && it.content.isBlank() }
            .map { msg ->
                msg.copy(
                    id = "",
                    sessionId = "",
                    attachments = msg.attachments.map { att ->
                        att.copy(
                            localPath = withContext(Dispatchers.IO) {
                                "data:${att.mimeType};base64,${attachmentStorage.readAsBase64(att.localPath)}"
                            }
                        )
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

        chatRepository.streamChat(provider, request).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private fun fixMessageOrder(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return emptyList()
        val result = mutableListOf<ChatMessage>()
        for (msg in messages) {
            val last = result.lastOrNull()
            if (last != null && last.role == msg.role) {
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
