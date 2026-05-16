package com.airouter.domain.provider

import com.airouter.data.remote.local.LocalLLMProvider
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.domain.provider.impl.OpenAiCompatibleProvider
import okhttp3.OkHttpClient

/**
 * AI Provider 工厂（注册表模式）
 * 支持运行时注册新的 Provider 实现，符合开闭原则。
 */
object ProviderFactory {

    private val registry = mutableMapOf<ProviderType, ProviderFactoryFn>()

    /**
     * Provider 工厂函数类型
     */
    fun interface ProviderFactoryFn {
        operator fun invoke(provider: Provider, client: OkHttpClient): AIProvider
    }

    /**
     * 注册指定类型的 Provider 工厂函数
     */
    fun register(type: ProviderType, factory: ProviderFactoryFn) {
        registry[type] = factory
    }

    /**
     * 根据 Provider 配置创建对应的 AIProvider 实例
     * 如未注册则默认使用 OpenAiCompatibleProvider
     */
    fun create(provider: Provider, client: OkHttpClient): AIProvider {
        return registry[provider.type]?.invoke(provider, client)
            ?: OpenAiCompatibleProvider(provider, client)
    }

    /**
     * 初始化默认注册（所有类型默认走 OpenAI 兼容协议）
     */
    fun initDefaults() {
        val defaultFactory = ProviderFactoryFn { p, c ->
            OpenAiCompatibleProvider(p, c)
        }
        ProviderType.values().forEach { type ->
            if (!registry.containsKey(type)) {
                registry[type] = defaultFactory
            }
        }
        
        // 注册本地 LLM Provider
        registry[ProviderType.LOCAL] = ProviderFactoryFn { p, c ->
            val context = com.airouter.AiRouterApp.INSTANCE
            LocalLLMProvider(context, LocalLLMProvider.getModelPath(context))
        }
    }
}