package com.airouter.data.model

import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val providerId: String = "",
    val modelId: String = "",
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
)
