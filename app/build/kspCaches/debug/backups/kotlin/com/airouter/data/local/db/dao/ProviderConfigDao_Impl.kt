package com.airouter.`data`.local.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.airouter.`data`.local.db.entity.ProviderConfigEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProviderConfigDao_Impl(
  __db: RoomDatabase,
) : ProviderConfigDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProviderConfigEntity: EntityInsertAdapter<ProviderConfigEntity>

  private val __deleteAdapterOfProviderConfigEntity:
      EntityDeleteOrUpdateAdapter<ProviderConfigEntity>

  private val __updateAdapterOfProviderConfigEntity:
      EntityDeleteOrUpdateAdapter<ProviderConfigEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProviderConfigEntity = object :
        EntityInsertAdapter<ProviderConfigEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `provider_configs` (`providerId`,`apiKey`,`customBaseUrl`,`enabled`,`fetchedModelsJson`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProviderConfigEntity) {
        statement.bindText(1, entity.providerId)
        statement.bindText(2, entity.apiKey)
        statement.bindText(3, entity.customBaseUrl)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        val _tmpFetchedModelsJson: String? = entity.fetchedModelsJson
        if (_tmpFetchedModelsJson == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpFetchedModelsJson)
        }
      }
    }
    this.__deleteAdapterOfProviderConfigEntity = object :
        EntityDeleteOrUpdateAdapter<ProviderConfigEntity>() {
      protected override fun createQuery(): String =
          "DELETE FROM `provider_configs` WHERE `providerId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ProviderConfigEntity) {
        statement.bindText(1, entity.providerId)
      }
    }
    this.__updateAdapterOfProviderConfigEntity = object :
        EntityDeleteOrUpdateAdapter<ProviderConfigEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `provider_configs` SET `providerId` = ?,`apiKey` = ?,`customBaseUrl` = ?,`enabled` = ?,`fetchedModelsJson` = ? WHERE `providerId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ProviderConfigEntity) {
        statement.bindText(1, entity.providerId)
        statement.bindText(2, entity.apiKey)
        statement.bindText(3, entity.customBaseUrl)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        val _tmpFetchedModelsJson: String? = entity.fetchedModelsJson
        if (_tmpFetchedModelsJson == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpFetchedModelsJson)
        }
        statement.bindText(6, entity.providerId)
      }
    }
  }

  public override suspend fun insertConfig(config: ProviderConfigEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfProviderConfigEntity.insert(_connection, config)
  }

  public override suspend fun deleteConfig(config: ProviderConfigEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfProviderConfigEntity.handle(_connection, config)
  }

  public override suspend fun updateConfig(config: ProviderConfigEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfProviderConfigEntity.handle(_connection, config)
  }

  public override fun getAllConfigs(): Flow<List<ProviderConfigEntity>> {
    val _sql: String = "SELECT * FROM provider_configs"
    return createFlow(__db, false, arrayOf("provider_configs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfApiKey: Int = getColumnIndexOrThrow(_stmt, "apiKey")
        val _cursorIndexOfCustomBaseUrl: Int = getColumnIndexOrThrow(_stmt, "customBaseUrl")
        val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _cursorIndexOfFetchedModelsJson: Int = getColumnIndexOrThrow(_stmt, "fetchedModelsJson")
        val _result: MutableList<ProviderConfigEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ProviderConfigEntity
          val _tmpProviderId: String
          _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          val _tmpApiKey: String
          _tmpApiKey = _stmt.getText(_cursorIndexOfApiKey)
          val _tmpCustomBaseUrl: String
          _tmpCustomBaseUrl = _stmt.getText(_cursorIndexOfCustomBaseUrl)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpFetchedModelsJson: String?
          if (_stmt.isNull(_cursorIndexOfFetchedModelsJson)) {
            _tmpFetchedModelsJson = null
          } else {
            _tmpFetchedModelsJson = _stmt.getText(_cursorIndexOfFetchedModelsJson)
          }
          _item =
              ProviderConfigEntity(_tmpProviderId,_tmpApiKey,_tmpCustomBaseUrl,_tmpEnabled,_tmpFetchedModelsJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getConfig(providerId: String): ProviderConfigEntity? {
    val _sql: String = "SELECT * FROM provider_configs WHERE providerId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, providerId)
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfApiKey: Int = getColumnIndexOrThrow(_stmt, "apiKey")
        val _cursorIndexOfCustomBaseUrl: Int = getColumnIndexOrThrow(_stmt, "customBaseUrl")
        val _cursorIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _cursorIndexOfFetchedModelsJson: Int = getColumnIndexOrThrow(_stmt, "fetchedModelsJson")
        val _result: ProviderConfigEntity?
        if (_stmt.step()) {
          val _tmpProviderId: String
          _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          val _tmpApiKey: String
          _tmpApiKey = _stmt.getText(_cursorIndexOfApiKey)
          val _tmpCustomBaseUrl: String
          _tmpCustomBaseUrl = _stmt.getText(_cursorIndexOfCustomBaseUrl)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpFetchedModelsJson: String?
          if (_stmt.isNull(_cursorIndexOfFetchedModelsJson)) {
            _tmpFetchedModelsJson = null
          } else {
            _tmpFetchedModelsJson = _stmt.getText(_cursorIndexOfFetchedModelsJson)
          }
          _result =
              ProviderConfigEntity(_tmpProviderId,_tmpApiKey,_tmpCustomBaseUrl,_tmpEnabled,_tmpFetchedModelsJson)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
