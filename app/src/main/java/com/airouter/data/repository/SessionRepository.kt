package com.airouter.data.repository

import com.airouter.data.local.db.dao.MessageDao
import com.airouter.data.local.db.dao.SessionDao
import com.airouter.data.local.db.entity.MessageEntity
import com.airouter.data.local.db.entity.SessionEntity
import com.airouter.data.model.ChatMessage
import com.airouter.data.model.ChatSession
import com.airouter.data.model.MessageAttachment
import com.airouter.data.model.MessageRole
import com.airouter.data.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val json: Json,
) {

    fun getAllSessions(): Flow<List<ChatSession>> {
        return sessionDao.getAllSessions().map { entities -> entities.map { it.toDomain() } }
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesBySession(sessionId).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getSession(sessionId: String): ChatSession? {
        return sessionDao.getSession(sessionId)?.toDomain()
    }

    suspend fun createSession(session: ChatSession) {
        sessionDao.insertSession(session.toEntity())
    }

    suspend fun updateSession(session: ChatSession) {
        sessionDao.updateSession(session.toEntity())
    }

    suspend fun deleteSession(session: ChatSession) {
        messageDao.deleteMessagesBySession(session.id)
        sessionDao.deleteSession(session.toEntity())
    }

    suspend fun addMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())
        sessionDao.incrementMessageCount(message.sessionId)
    }

    suspend fun updateMessage(message: ChatMessage) {
        messageDao.updateMessage(message.toEntity())
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun getMessagesBySessionOnce(sessionId: String): List<ChatMessage> {
        return messageDao.getMessagesBySessionOnce(sessionId).map { it.toDomain() }
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        sessionDao.updateTitle(sessionId, title)
    }

    suspend fun togglePin(sessionId: String, isPinned: Boolean) {
        sessionDao.updatePinned(sessionId, isPinned)
    }

    private fun SessionEntity.toDomain() = ChatSession(
        id = id,
        title = title,
        providerId = providerId,
        modelId = modelId,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        isPinned = isPinned,
    )

    private fun ChatSession.toEntity() = SessionEntity(
        id = id,
        title = title,
        providerId = providerId,
        modelId = modelId,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        isPinned = isPinned,
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = MessageRole.valueOf(role),
        content = content,
        modelId = modelId,
        providerId = providerId,
        timestamp = timestamp,
        tokenUsage = TokenUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            estimatedCost = estimatedCost,
        ),
        isError = isError,
        errorMessage = errorMessage,
        attachments = try {
            attachments?.let { json.decodeFromString<List<MessageAttachment>>(it) } ?: emptyList()
        } catch (_: Exception) { emptyList() },
    )

    private fun ChatMessage.toEntity() = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        modelId = modelId,
        providerId = providerId,
        timestamp = timestamp,
        promptTokens = tokenUsage?.promptTokens ?: 0,
        completionTokens = tokenUsage?.completionTokens ?: 0,
        totalTokens = tokenUsage?.totalTokens ?: 0,
        estimatedCost = tokenUsage?.estimatedCost,
        isError = isError,
        errorMessage = errorMessage,
        attachments = if (attachments.isNotEmpty()) json.encodeToString(attachments) else null,
    )
}
