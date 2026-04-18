package com.airouter.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val providerId: String,
    val apiKey: String,
    val customBaseUrl: String,
    val enabled: Boolean,
)
