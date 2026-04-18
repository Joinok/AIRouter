package com.airouter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val systemPrompt: String?,
    val temperature: Float,
    val maxTokens: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val isPinned: Boolean = false,
)
