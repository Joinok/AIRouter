package com.airouter.data.model

data class ChatResponse(
    val content: String = "",
    val modelId: String = "",
    val usage: TokenUsage = TokenUsage(),
)
