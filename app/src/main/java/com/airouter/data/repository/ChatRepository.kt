package com.airouter.data.repository

import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.Provider
import com.airouter.domain.provider.AIProvider
import com.airouter.domain.provider.ProviderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retry
import okhttp3.OkHttpClient

class ChatRepository(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * 发送流式对话请求，自动重试 3 次（仅限 IO 异常）。
     */
    fun streamChat(provider: Provider, request: ChatRequest): Flow<ChatChunk> {
        val aiProvider: AIProvider = ProviderFactory.create(provider, okHttpClient)
        return aiProvider.chatStream(request)
            .retry(3) { cause -> cause is java.io.IOException }
    }

    /**
     * 发送非流式对话请求。
     */
    suspend fun chat(provider: Provider, request: ChatRequest): ChatResponse {
        val aiProvider: AIProvider = ProviderFactory.create(provider, okHttpClient)
        return aiProvider.chat(request)
    }
}