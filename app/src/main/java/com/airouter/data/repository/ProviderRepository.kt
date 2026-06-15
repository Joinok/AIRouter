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

    fun getAllProviders(): Flow<List<Provider>> {
        return providerConfigDao.getAllConfigs().map { configs ->
            val configMap = configs.associateBy { it.providerId }

            val builtInProviders = BuiltInProviders.all.map { builtIn ->
                val config = configMap[builtIn.id]
                mergeProvider(builtIn, config)
            }

            val builtInIds = BuiltInProviders.all.map { it.id }.toSet()
            val customProviders = configs
                .filter { it.providerId !in builtInIds && it.providerType != null }
                .map { config -> configToProvider(config) }

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
        val existing = providerConfigDao.getConfig(provider.id)
        val entity = if (existing != null) {
            existing.copy(
                apiKey = provider.apiKey,
                customBaseUrl = provider.customBaseUrl,
                enabled = provider.enabled,
            )
        } else {
            ProviderConfigEntity(
                providerId = provider.id,
                apiKey = provider.apiKey,
                customBaseUrl = provider.customBaseUrl,
                enabled = provider.enabled,
            )
        }
        providerConfigDao.insertConfig(entity)
    }

    suspend fun getProviderConfig(providerId: String): ProviderConfigEntity? {
        return providerConfigDao.getConfig(providerId)
    }

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
                extraBodyFieldsJson = ProviderConfigEntity.serializeExtraParams(provider.extraBodyFields),
            )
        )
    }

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
        return configToProvider(config)
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        val existing = providerConfigDao.getConfig(providerId)
        if (existing != null) {
            providerConfigDao.insertConfig(existing.copy(apiKey = apiKey))
        } else {
            providerConfigDao.insertConfig(ProviderConfigEntity(providerId = providerId, apiKey = apiKey, customBaseUrl = "", enabled = true))
        }
    }

    suspend fun saveCustomBaseUrl(providerId: String, url: String) {
        val existing = providerConfigDao.getConfig(providerId)
        if (existing != null) {
            providerConfigDao.insertConfig(existing.copy(customBaseUrl = url))
        } else {
            providerConfigDao.insertConfig(ProviderConfigEntity(providerId = providerId, apiKey = "", customBaseUrl = url, enabled = true))
        }
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val existing = providerConfigDao.getConfig(providerId)
        if (existing != null) {
            providerConfigDao.insertConfig(existing.copy(enabled = enabled))
        } else {
            providerConfigDao.insertConfig(ProviderConfigEntity(providerId = providerId, apiKey = "", customBaseUrl = "", enabled = enabled))
        }
    }

    /**
     * 保存自定义 Provider 的额外请求体参数
     */
    suspend fun saveExtraBodyFields(providerId: String, params: Map<String, String>) {
        val existing = providerConfigDao.getConfig(providerId) ?: return
        providerConfigDao.insertConfig(
            existing.copy(extraBodyFieldsJson = ProviderConfigEntity.serializeExtraParams(params))
        )
    }

    suspend fun fetchModels(provider: Provider, client: OkHttpClient): List<AiModel> {
        return withContext(Dispatchers.IO) {
            doFetchModels(provider, client)
        }
    }

    private suspend fun doFetchModels(provider: Provider, client: OkHttpClient): List<AiModel> {
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
                        extraBodyFieldsJson = existing?.extraBodyFieldsJson,
                    )
                )
            }
            return models
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun mergeProvider(builtIn: Provider, config: ProviderConfigEntity?): Provider {
        val fetchedModels = config?.fetchedModelsJson?.let {
            try {
                json.decodeFromString<List<AiModel>>(it)
            } catch (e: Exception) {
                null
            }
        }

        val finalModels = if (fetchedModels != null && fetchedModels.isNotEmpty()) {
            val fetchedIds = fetchedModels.map { m -> m.modelId }.toSet()
            val merged = fetchedModels.map { fm ->
                val builtInMatch = builtIn.supportedModels.find { it.modelId == fm.modelId }
                if (builtInMatch != null) {
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

    /** 把数据库配置转为自定义 Provider */
    private fun configToProvider(config: ProviderConfigEntity): Provider {
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
            extraBodyFields = ProviderConfigEntity.deserializeExtraParams(config.extraBodyFieldsJson),
        )
    }
}
