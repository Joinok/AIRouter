package com.airouter.domain.provider

import com.airouter.data.remote.local.LocalLLMProvider
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.domain.provider.impl.ClaudeProvider
import com.airouter.domain.provider.impl.GeminiProvider
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
        val fn = registry[provider.type]
        val result = fn?.invoke(provider, client) ?: OpenAiCompatibleProvider(provider, client)
        android.util.Log.d("AIRouter-PF", "create: type=${provider.type}, registryHit=${fn != null}, instance=${result.hashCode()}, isLocal=${result is com.airouter.data.remote.local.LocalLLMProvider}")
        return result
    }

    /**
     * 初始化默认注册（所有类型默认走 OpenAI 兼容协议）
     */
    fun initDefaults() {
        // OpenAI 兼容协议（默认）
        val defaultFactory = ProviderFactoryFn { p, c ->
            OpenAiCompatibleProvider(p, c)
        }
        ProviderType.values().forEach { type ->
            if (!registry.containsKey(type)) {
                registry[type] = defaultFactory
            }
        }
        
        // Claude - Anthropic 官方 API
        registry[ProviderType.CLAUDE] = ProviderFactoryFn { p, c ->
            ClaudeProvider(p, c)
        }
        
        // Gemini - Google AI Studio
        registry[ProviderType.GEMINI] = ProviderFactoryFn { p, c ->
            GeminiProvider(p, c)
        }
        
        // 注册本地 LLM Provider（单例模式，避免每次创建新实例导致模型重复加载）
        val context = com.airouter.AiRouterApp.INSTANCE
        val localProvider = LocalLLMProvider(context, null)  // modelPath=null，懒加载
        registry[ProviderType.LOCAL] = ProviderFactoryFn { p, c ->
            localProvider
        }
    }
}