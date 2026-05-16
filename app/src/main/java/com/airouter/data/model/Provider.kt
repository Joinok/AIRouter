package com.airouter.data.model

data class Provider(
    val id: String,              // "deepseek", "openai", "glm"
    val name: String,            // "DeepSeek", "OpenAI", "智谱 GLM"
    val type: ProviderType,
    val defaultBaseUrl: String,
    val isBuiltIn: Boolean,
    val enabled: Boolean = true,
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val supportedModels: List<AiModel> = emptyList(),
    /** 是否支持 stream_options.include_usage（部分厂商如 Moonshot 不支持，传了会 400） */
    val supportsStreamOptions: Boolean = true,
) {
    /** 获取实际使用的 base url，优先用用户自定义的 */
    val effectiveBaseUrl: String
        get() = customBaseUrl.ifBlank { defaultBaseUrl }

    /** 是否已配置（本地类型不需要 API Key） */
    val isConfigured: Boolean
        get() = type == ProviderType.LOCAL || apiKey.isNotBlank()
}
