package com.airouter.data.repository

import com.airouter.data.local.db.dao.ProviderConfigDao
import com.airouter.data.local.db.entity.ProviderConfigEntity
import com.airouter.data.local.prefs.BuiltInProviders
import com.airouter.data.model.AiModel
import com.airouter.data.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ProviderRepository(
    private val providerConfigDao: ProviderConfigDao,
) {

    /**
     * 获取所有 Provider（内置 + 用户配置合并）。
     * Flow 会自动响应数据库变化。
     */
    fun getAllProviders(): Flow<List<Provider>> {
        return providerConfigDao.getAllConfigs().map { configs ->
            val configMap = configs.associateBy { it.providerId }
            BuiltInProviders.all.map { builtIn ->
                val config = configMap[builtIn.id]
                builtIn.copy(
                    apiKey = config?.apiKey ?: "",
                    customBaseUrl = config?.customBaseUrl ?: "",
                    enabled = config?.enabled ?: true,
                )
            }
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

    suspend fun getProviderById(providerId: String): Provider? {
        val builtIn = BuiltInProviders.all.find { it.id == providerId } ?: return null
        val config = providerConfigDao.getConfig(providerId)
        return builtIn.copy(
            apiKey = config?.apiKey ?: "",
            customBaseUrl = config?.customBaseUrl ?: "",
            enabled = config?.enabled ?: true,
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
            )
        )
    }
}
