package com.airouter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

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
    /** 自定义额外请求体参数 JSON，格式: {"temperature":"0.7","top_p":"0.9"} */
    val extraBodyFieldsJson: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 将 Map 序列化为 JSON 字符串 */
        fun serializeExtraParams(params: Map<String, String>): String? {
            if (params.isEmpty()) return null
            return json.encodeToString(params)
        }

        /** 将 JSON 字符串反序列化为 Map */
        fun deserializeExtraParams(jsonStr: String?): Map<String, String> {
            if (jsonStr.isNullOrBlank()) return emptyMap()
            return try {
                json.decodeFromString<Map<String, String>>(jsonStr)
            } catch (_: Exception) { emptyMap() }
        }
    }
}
