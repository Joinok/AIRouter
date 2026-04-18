package com.airouter.data.local.db.dao

import androidx.room.*
import com.airouter.data.local.db.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun getAllConfigs(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    suspend fun getConfig(providerId: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ProviderConfigEntity)

    @Update
    suspend fun updateConfig(config: ProviderConfigEntity)

    @Delete
    suspend fun deleteConfig(config: ProviderConfigEntity)
}
