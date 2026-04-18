package com.airouter.domain.provider

import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import kotlinx.coroutines.flow.Flow

/**
 * 统一 AI Provider 接口。
 * 所有模型提供商都通过这个接口对接，上层 UI 完全解耦。
 */
interface AIProvider {
    val providerId: String
    val providerName: String

    /** 列出所有可用模型 */
    suspend fun listModels(): List<AiModel>

    /** 发送对话（流式） */
    fun chatStream(request: ChatRequest): Flow<ChatChunk>

    /** 发送对话（非流式） */
    suspend fun chat(request: ChatRequest): ChatResponse

    /** 验证 API Key 是否有效 */
    suspend fun validateApiKey(): Boolean
}
