package com.airouter.data.repository

import com.airouter.data.local.db.dao.ProviderConfigDao
import com.airouter.data.local.db.entity.ProviderConfigEntity
import com.airouter.data.local.prefs.BuiltInProviders
import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import com.airouter.data.model.ProviderType
import com.airouter.domain.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ProviderRepository(
    private val providerConfigDao: ProviderConfigDao,
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 获取所有 Provider（内置 + 用户配置 + 自定义）
     * Flow 会自动响应数据库变化。
     */
    fun getAllProviders(): Flow<List<Provider>> {
        return providerConfigDao.getAllConfigs().map { configs ->
            val configMap = configs.associateBy { it.providerId }
            
            // 内置 Provider 列表
            val builtInProviders = BuiltInProviders.all.map { builtIn ->
                val config = configMap[builtIn.id]
                mergeProvider(builtIn, config)
            }
            
            // 自定义 Provider（数据库中有但不在内置列表中的）
            val builtInIds = BuiltInProviders.all.map { it.id }.toSet()
            val customProviders = configs
                .filter { it.providerId !in builtInIds && it.providerType != null }
                .map { config ->
                    val providerType = try {
                        ProviderType.valueOf(config.providerType!!)
                    } catch (e: Exception) {
                        ProviderType.OPENAI_COMPATIBLE
                    }
                    val models = config.builtInModelsJson?.let {
                        try {
                            json.decodeFromString<List<AiModel>>(it)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()
                    
                    Provider(
                        id = config.providerId,
                        name = config.name ?: config.providerId,
                        type = providerType,
                        defaultBaseUrl = config.defaultBaseUrl ?: "",
                        isBuiltIn = false,
                        isCustom = true,
                        apiKey = config.apiKey,
                        customBaseUrl = config.customBaseUrl,
                        enabled = config.enabled,
                        supportedModels = models,
                    )
                }
            
            builtInProviders + customProviders
        }
    }

    fun getConfiguredProviders(): Flow<List<Provider>> {
        return getAllProviders().map { list -> list.filter { it.isConfigured } }
    }

    suspend fun getConfiguredProvidersOnce(): List<Provider> {
        return getAllProviders().first().filter { it.isConfigured }
    }

    suspend fun updateProviderConfig(provider: Provider) {
        providerConfigDao.insertConfig(
            ProviderConfigEntity(
                providerId = provider.id,
                apiKey = provider.apiKey,
                customBaseUrl = provider.customBaseUrl,
                enabled = provider.enabled,
            )
        )
    }

    suspend fun getProviderConfig(providerId: String): ProviderConfigEntity? {
        return providerConfigDao.getConfig(providerId)
    }

    /**
     * 添加自定义 Provider
     */
    suspend fun addCustomProvider(provider: Provider) {
        val modelsJson = if (provider.supportedModels.isNotEmpty()) {
            try {
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AiModel.serializer()),
                    provider.supportedModels
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        
        providerConfigDao.insertConfig(
            ProviderConfigEntity(
                providerId = provider.id,
                apiKey = provider.apiKey,
                customBaseUrl = provider.customBaseUrl,
                enabled = provider.enabled,
                fetchedModelsJson = null,
                name = provider.name,
                providerType = provider.type.name,
                defaultBaseUrl = provider.defaultBaseUrl,
                builtInModelsJson = modelsJson,
            )
        )
    }

    /**
     * 删除自定义 Provider
     */
    suspend fun deleteCustomProvider(providerId: String) {
        val config = providerConfigDao.getConfig(providerId)
        if (config != null) {
            providerConfigDao.deleteConfig(config)
        }
    }

    suspend fun getProviderById(providerId: String): Provider? {
        // 先查内置
        val builtIn = BuiltInProviders.all.find { it.id == providerId }
        if (builtIn != null) {
            val config = providerConfigDao.getConfig(providerId)
            return mergeProvider(builtIn, config)
        }
        // 再查自定义
        val config = providerConfigDao.getConfig(providerId) ?: return null
        val providerType = try {
            ProviderType.valueOf(config.providerType!!)
        } catch (e: Exception) {
            ProviderType.OPENAI_COMPATIBLE
        }
        val models = config.builtInModelsJson?.let {
            try { json.decodeFromString<List<AiModel>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        return Provider(
            id = config.providerId,
            name = config.name ?: config.providerId,
            type = providerType,
            defaultBaseUrl = config.defaultBaseUrl ?: "",
            isBuiltIn = false,
            isCustom = true,
            apiKey = config.apiKey,
            customBaseUrl = config.customBaseUrl,
            enabled = config.enabled,
            supportedModels = models,
        )
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        val existing = providerConfigDao.getConfig(providerId)
        providerConfigDao.insertConfig(
            ProviderConfigEntity(
                providerId = providerId,
                apiKey = apiKey,
                customBaseUrl = existing?.customBaseUrl ?: "",
                enabled = existing?.enabled ?: true,
                fetchedModelsJson = existing?.fetchedModelsJson,
            )
        )
    }

    suspend fun saveCustomBaseUrl(providerId: String, url: String) {
        val existing = providerConfigDao.getConfig(providerId)
        providerConfigDao.insertConfig(
            ProviderConfigEntity(
                providerId = providerId,
                apiKey = existing?.apiKey ?: "",
                customBaseUrl = url,
                enabled = existing?.enabled ?: true,
                fetchedModelsJson = existing?.fetchedModelsJson,
            )
        )
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val existing = providerConfigDao.getConfig(providerId)
        providerConfigDao.insertConfig(
            ProviderConfigEntity(
                providerId = providerId,
                apiKey = existing?.apiKey ?: "",
                customBaseUrl = existing?.customBaseUrl ?: "",
                enabled = enabled,
                fetchedModelsJson = existing?.fetchedModelsJson,
            )
        )
    }

    /**
     * 从 /models 接口拉取模型列表并保存到数据库。
     * 返回拉取到的模型列表，失败返回 null。
     */
    suspend fun fetchModels(provider: Provider, client: OkHttpClient): List<AiModel> {
        return withContext(Dispatchers.IO) {
            doFetchModels(provider, client)
        }
    }

    private suspend fun doFetchModels(provider: Provider, client: OkHttpClient): List<AiModel> {
        // 清空内置列表，强制走 API 拉取
        val providerWithoutBuiltIn = provider.copy(supportedModels = emptyList())
        val apiProvider = ProviderFactory.create(
            providerWithoutBuiltIn.copy(
                apiKey = provider.apiKey.takeIf { it.isNotBlank() } ?: provider.apiKey,
                customBaseUrl = provider.customBaseUrl.takeIf { it.isNotBlank() } ?: provider.customBaseUrl,
            ),
            client
        )

        try {
            val models = apiProvider.listModels()
            if (models.isNotEmpty()) {
                // 保存到数据库
                val existing = providerConfigDao.getConfig(provider.id)
                val modelsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AiModel.serializer()),
                    models
                )
                providerConfigDao.insertConfig(
                    ProviderConfigEntity(
                        providerId = provider.id,
                        apiKey = existing?.apiKey ?: provider.apiKey,
                        customBaseUrl = existing?.customBaseUrl ?: provider.customBaseUrl,
                        enabled = existing?.enabled ?: true,
                        fetchedModelsJson = modelsJson,
                    )
                )
            }
            return models
        } catch (e: Exception) {
            // /models 接口不支持（404 或其他错误），静默回退到空列表
            // 调用方通过判断 isEmpty() 来决定是否使用内置模型
            return emptyList()
        }
    }

    /**
     * 合并内置 Provider 与数据库配置。
     * 模型列表：优先用 fetchedModels，否则用内置 supportedModels。
     * 自定义 Provider 直接从数据库读取完整配置。
     */
    private fun mergeProvider(builtIn: Provider, config: ProviderConfigEntity?): Provider {
        val fetchedModels = config?.fetchedModelsJson?.let {
            try {
                json.decodeFromString<List<AiModel>>(it)
            } catch (e: Exception) {
                null
            }
        }

        // 合并逻辑：fetchedModels 为主，内置模型作为补充（如果 fetched 中没有对应 modelId 则保留内置的）
        val finalModels = if (fetchedModels != null && fetchedModels.isNotEmpty()) {
            val fetchedIds = fetchedModels.map { m -> m.modelId }.toSet()
            // 把内置模型中有额外信息（价格、上下文长度、视觉支持等）的合并进去
            val merged = fetchedModels.map { fm ->
                val builtInMatch = builtIn.supportedModels.find { it.modelId == fm.modelId }
                if (builtInMatch != null) {
                    // 用内置的信息补充 fetched 的（内置有价格等详细参数）
                    fm.copy(
                        displayName = if (fm.displayName == fm.modelId) builtInMatch.displayName else fm.displayName,
                        contextLength = if (fm.contextLength == 8192 && builtInMatch.contextLength != 8192) builtInMatch.contextLength else fm.contextLength,
                        inputPricePerMToken = fm.inputPricePerMToken ?: builtInMatch.inputPricePerMToken,
                        outputPricePerMToken = fm.outputPricePerMToken ?: builtInMatch.outputPricePerMToken,
                        supportsVision = fm.supportsVision || builtInMatch.supportsVision,
                        fixedTemperature = fm.fixedTemperature ?: builtInMatch.fixedTemperature,
                        fixedTopP = fm.fixedTopP ?: builtInMatch.fixedTopP,
                        minMaxTokens = fm.minMaxTokens ?: builtInMatch.minMaxTokens,
                    )
                } else {
                    fm
                }
            }
            merged
        } else {
            builtIn.supportedModels
        }

        return builtIn.copy(
            apiKey = config?.apiKey ?: "",
            customBaseUrl = config?.customBaseUrl ?: "",
            enabled = config?.enabled ?: true,
            supportedModels = finalModels,
        )
    }
}