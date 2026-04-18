package com.airouter.data.model

data class ChatRequest(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val stream: Boolean = true,
    val systemPrompt: String? = null,
)
