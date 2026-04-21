package com.airouter.`data`.local.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.airouter.`data`.local.db.dao.MessageDao
import com.airouter.`data`.local.db.dao.MessageDao_Impl
import com.airouter.`data`.local.db.dao.ProviderConfigDao
import com.airouter.`data`.local.db.dao.ProviderConfigDao_Impl
import com.airouter.`data`.local.db.dao.SessionDao
import com.airouter.`data`.local.db.dao.SessionDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _sessionDao: Lazy<SessionDao> = lazy {
    SessionDao_Impl(this)
  }

  private val _messageDao: Lazy<MessageDao> = lazy {
    MessageDao_Impl(this)
  }

  private val _providerConfigDao: Lazy<ProviderConfigDao> = lazy {
    ProviderConfigDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(5,
        "a86355e1e8786c5279dc662606bf1906", "938f2b40fcaa36c261f184b68bac7fb0") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `systemPrompt` TEXT, `temperature` REAL NOT NULL, `maxTokens` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `messageCount` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `modelId` TEXT, `providerId` TEXT, `timestamp` INTEGER NOT NULL, `promptTokens` INTEGER NOT NULL, `completionTokens` INTEGER NOT NULL, `totalTokens` INTEGER NOT NULL, `estimatedCost` REAL, `isError` INTEGER NOT NULL, `errorMessage` TEXT, `attachments` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `provider_configs` (`providerId` TEXT NOT NULL, `apiKey` TEXT NOT NULL, `customBaseUrl` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `fetchedModelsJson` TEXT, PRIMARY KEY(`providerId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a86355e1e8786c5279dc662606bf1906')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `sessions`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `provider_configs`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsSessions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSessions.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("providerId", TableInfo.Column("providerId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("modelId", TableInfo.Column("modelId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("systemPrompt", TableInfo.Column("systemPrompt", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("temperature", TableInfo.Column("temperature", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("maxTokens", TableInfo.Column("maxTokens", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("messageCount", TableInfo.Column("messageCount", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSessions.put("isPinned", TableInfo.Column("isPinned", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSessions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSessions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSessions: TableInfo = TableInfo("sessions", _columnsSessions, _foreignKeysSessions,
            _indicesSessions)
        val _existingSessions: TableInfo = read(connection, "sessions")
        if (!_infoSessions.equals(_existingSessions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sessions(com.airouter.data.local.db.entity.SessionEntity).
              | Expected:
              |""".trimMargin() + _infoSessions + """
              |
              | Found:
              |""".trimMargin() + _existingSessions)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("sessionId", TableInfo.Column("sessionId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("role", TableInfo.Column("role", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("content", TableInfo.Column("content", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("modelId", TableInfo.Column("modelId", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("providerId", TableInfo.Column("providerId", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("promptTokens", TableInfo.Column("promptTokens", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("completionTokens", TableInfo.Column("completionTokens", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("totalTokens", TableInfo.Column("totalTokens", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("estimatedCost", TableInfo.Column("estimatedCost", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("isError", TableInfo.Column("isError", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("errorMessage", TableInfo.Column("errorMessage", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("attachments", TableInfo.Column("attachments", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysMessages.add(TableInfo.ForeignKey("sessions", "CASCADE", "NO ACTION",
            listOf("sessionId"), listOf("id")))
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMessages.add(TableInfo.Index("index_messages_sessionId", false, listOf("sessionId"),
            listOf("ASC")))
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages,
            _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(com.airouter.data.local.db.entity.MessageEntity).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsProviderConfigs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProviderConfigs.put("providerId", TableInfo.Column("providerId", "TEXT", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProviderConfigs.put("apiKey", TableInfo.Column("apiKey", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProviderConfigs.put("customBaseUrl", TableInfo.Column("customBaseUrl", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProviderConfigs.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProviderConfigs.put("fetchedModelsJson", TableInfo.Column("fetchedModelsJson",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProviderConfigs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProviderConfigs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProviderConfigs: TableInfo = TableInfo("provider_configs", _columnsProviderConfigs,
            _foreignKeysProviderConfigs, _indicesProviderConfigs)
        val _existingProviderConfigs: TableInfo = read(connection, "provider_configs")
        if (!_infoProviderConfigs.equals(_existingProviderConfigs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |provider_configs(com.airouter.data.local.db.entity.ProviderConfigEntity).
              | Expected:
              |""".trimMargin() + _infoProviderConfigs + """
              |
              | Found:
              |""".trimMargin() + _existingProviderConfigs)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions", "messages",
        "provider_configs")
  }

  public override fun clearAllTables() {
    super.performClear(true, "sessions", "messages", "provider_configs")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(SessionDao::class, SessionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessageDao::class, MessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ProviderConfigDao::class, ProviderConfigDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun sessionDao(): SessionDao = _sessionDao.value

  public override fun messageDao(): MessageDao = _messageDao.value

  public override fun providerConfigDao(): ProviderConfigDao = _providerConfigDao.value
}
