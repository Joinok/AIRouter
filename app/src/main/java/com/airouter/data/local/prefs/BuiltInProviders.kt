package com.airouter.data.local.prefs

import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType

/**
 * 内置 Provider 列表。
 * 大部分走 OpenAI 兼容协议，只需要填 API Key 就能用。
 * 模型保持最新，每个 Provider 5-6 个主力模型。
 */
object BuiltInProviders {

    val all: List<Provider> = listOf(
        Provider(
            id = "deepseek",
            name = "DeepSeek",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.deepseek.com/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("deepseek-chat", "DeepSeek V3", contextLength = 131072,
                    inputPricePerMToken = 0.27f, outputPricePerMToken = 1.1f),
                AiModel("deepseek-reasoner", "DeepSeek R1", contextLength = 131072,
                    inputPricePerMToken = 0.55f, outputPricePerMToken = 2.19f),
            )
        ),
        Provider(
            id = "openai",
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.openai.com/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("gpt-5", "GPT-5", contextLength = 1024000,
                    inputPricePerMToken = 10.0f, outputPricePerMToken = 30.0f,
                    supportsVision = true),
                AiModel("o3", "o3", contextLength = 200000,
                    inputPricePerMToken = 10.0f, outputPricePerMToken = 40.0f,
                    supportsVision = true),
                AiModel("o4-mini", "o4-mini", contextLength = 200000,
                    inputPricePerMToken = 1.10f, outputPricePerMToken = 4.40f,
                    supportsVision = true),
            )
        ),
        Provider(
            id = "glm",
            name = "智谱 GLM",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("glm-5.1", "GLM-5.1", contextLength = 200000,
                    inputPricePerMToken = 0.5f, outputPricePerMToken = 0.5f),
                AiModel("glm-5v-turbo", "GLM-5V Turbo", contextLength = 128000,
                    inputPricePerMToken = 0.5f, outputPricePerMToken = 0.5f,
                    supportsVision = true),
                AiModel("glm-4-flash", "GLM-4 Flash", contextLength = 128000,
                    inputPricePerMToken = 0.1f, outputPricePerMToken = 0.1f),
            )
        ),
        Provider(
            id = "kimi",
            name = "Kimi (Moonshot)",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.moonshot.cn/v1",
            isBuiltIn = true,
            supportsStreamOptions = false,
            supportedModels = listOf(
                AiModel("kimi-k2.6", "Kimi K2.6", contextLength = 256000,
                    inputPricePerMToken = 2.0f, outputPricePerMToken = 2.0f,
                    supportsVision = true, fixedTemperature = 1f, fixedTopP = 0.95f, minMaxTokens = 16384),
                AiModel("kimi-k2.5", "Kimi K2.5", contextLength = 256000,
                    inputPricePerMToken = 2.0f, outputPricePerMToken = 2.0f,
                    supportsVision = true, fixedTemperature = 1f, fixedTopP = 0.95f),
                AiModel("kimi-k2-turbo-preview", "Kimi K2 Turbo", contextLength = 256000,
                    inputPricePerMToken = 1.0f, outputPricePerMToken = 1.0f),
                AiModel("kimi-k2-thinking", "Kimi K2 Thinking", contextLength = 256000,
                    inputPricePerMToken = 2.0f, outputPricePerMToken = 2.0f),
            )
        ),
        Provider(
            id = "local",
            name = "本地模型",
            type = ProviderType.LOCAL,
            defaultBaseUrl = "",
            isBuiltIn = true,
            // 模型列表由 LocalLLMProvider.listModels() 运行时动态返回已下载的 GGUF
            supportedModels = emptyList(),
        ),
        Provider(
            id = "qwen",
            name = "通义千问",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("qwen3-max", "Qwen3 Max", contextLength = 131072,
                    inputPricePerMToken = 2.0f, outputPricePerMToken = 6.0f),
                AiModel("qwen3-plus", "Qwen3 Plus", contextLength = 131072,
                    inputPricePerMToken = 0.8f, outputPricePerMToken = 2.0f),
                AiModel("qwen-turbo", "Qwen Turbo", contextLength = 131072,
                    inputPricePerMToken = 0.3f, outputPricePerMToken = 0.6f),
            )
        ),
        Provider(
            id = "siliconflow",
            name = "SiliconFlow",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.siliconflow.cn/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3 (SF)", contextLength = 131072,
                    inputPricePerMToken = 0.27f, outputPricePerMToken = 1.1f),
                AiModel("Qwen/Qwen3-235B-A22B", "Qwen3 235B (SF)", contextLength = 131072),
                AiModel("THUDM/glm-4-9b-chat", "GLM-4 9B (SF)", contextLength = 131072),
                AiModel("meta-llama/Llama-4-Maverick-17B-128E", "Llama 4 Maverick (SF)", contextLength = 131072),
            )
        ),
        Provider(
            id = "minimax",
            name = "MiniMax",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.minimax.chat/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("MiniMax-Text-01", "MiniMax-Text-01", contextLength = 1000000,
                    inputPricePerMToken = 4.0f, outputPricePerMToken = 16.0f),
                AiModel("abab7", "abab7", contextLength = 245760,
                    inputPricePerMToken = 1.0f, outputPricePerMToken = 4.0f),
            )
        ),
        Provider(
            id = "openrouter",
            name = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("deepseek/deepseek-chat-v3-0324", "DeepSeek V3", contextLength = 131072),
                AiModel("anthropic/claude-opus-4-7", "Claude Opus 4.7", contextLength = 200000,
                    supportsVision = true),
                AiModel("openai/gpt-5", "GPT-5", contextLength = 1024000,
                    supportsVision = true),
                AiModel("google/gemini-2.5-pro", "Gemini 2.5 Pro", contextLength = 1048576,
                    supportsVision = true),
                AiModel("meta-llama/llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick", contextLength = 131072),
            )
        ),
    )
}
