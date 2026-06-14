package com.airouter.data.local.prefs

import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType

/**
 * 内置 Provider 列表。
 * 预设只保留 Claude、Gemini、OpenAI 三个，其他通过右上角「+」添加自定义 OpenAI 兼容端点。
 */
object BuiltInProviders {

    val all: List<Provider> = listOf(
        // Claude - Anthropic 官方 API
        Provider(
            id = "claude",
            name = "Claude",
            type = ProviderType.CLAUDE,
            defaultBaseUrl = "https://api.anthropic.com/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("claude-opus-4-20250514", "Claude Opus 4", contextLength = 200000,
                    inputPricePerMToken = 75.0f, outputPricePerMToken = 375.0f, supportsVision = true),
                AiModel("claude-sonnet-4-20250514", "Claude Sonnet 4", contextLength = 200000,
                    inputPricePerMToken = 15.0f, outputPricePerMToken = 75.0f, supportsVision = true),
                AiModel("claude-haiku-4-20250514", "Claude Haiku 4", contextLength = 200000,
                    inputPricePerMToken = 1.25f, outputPricePerMToken = 5.0f, supportsVision = true),
            )
        ),
        // Gemini - Google AI Studio
        Provider(
            id = "gemini",
            name = "Gemini",
            type = ProviderType.GEMINI,
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("gemini-2.5-pro", "Gemini 2.5 Pro", contextLength = 1048576,
                    supportsVision = true),
                AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", contextLength = 1048576,
                    supportsVision = true),
                AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", contextLength = 1048576,
                    supportsVision = true),
            )
        ),
        // OpenAI - GPT 系列
        Provider(
            id = "openai",
            name = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.openai.com/v1",
            isBuiltIn = true,
            supportedModels = listOf(
                AiModel("gpt-5", "GPT-5", contextLength = 1024000,
                    inputPricePerMToken = 10.0f, outputPricePerMToken = 30.0f, supportsVision = true),
                AiModel("o3", "o3", contextLength = 200000,
                    inputPricePerMToken = 10.0f, outputPricePerMToken = 40.0f, supportsVision = true),
                AiModel("o4-mini", "o4-mini", contextLength = 200000,
                    inputPricePerMToken = 1.10f, outputPricePerMToken = 4.40f, supportsVision = true),
            )
        ),
    )
}
