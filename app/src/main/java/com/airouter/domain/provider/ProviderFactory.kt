package com.airouter.domain.provider

import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.domain.provider.impl.OpenAiCompatibleProvider
import okhttp3.OkHttpClient

/**
 * 根据 Provider 配置创建对应的 AIProvider 实例。
 */
object ProviderFactory {

    fun create(
        provider: Provider,
        client: OkHttpClient,
    ): AIProvider {
        return when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE,
            ProviderType.BAIDU,       // 暂时都用 OpenAI 兼容，后续单独适配
            ProviderType.DOUBAO,
            ProviderType.GEMINI,
            ProviderType.CLAUDE,
            -> OpenAiCompatibleProvider(provider, client)
        }
    }
}
