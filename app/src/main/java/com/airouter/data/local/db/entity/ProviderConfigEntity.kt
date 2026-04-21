package com.airouter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val providerId: String,
    val apiKey: String,
    val customBaseUrl: String,
    val enabled: Boolean,
    /** 从 /models 接口拉取的模型 JSON，格式: [{"modelId":"xxx","displayName":"xxx",...},...] */
    val fetchedModelsJson: String? = null,
)
