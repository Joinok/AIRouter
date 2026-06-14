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
    /** 自定义 Provider 的名称 */
    val name: String? = null,
    /** 自定义 Provider 的类型字符串（如 "OPENAI_COMPATIBLE"） */
    val providerType: String? = null,
    /** 自定义 Provider 的默认 Base URL */
    val defaultBaseUrl: String? = null,
    /** 自定义 Provider 的模型列表 JSON */
    val builtInModelsJson: String? = null,
)
