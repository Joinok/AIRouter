package com.airouter.data.model

data class ChatChunk(
    val content: String = "",
    val finishReason: String? = null,
    val usage: TokenUsage? = null,
)
