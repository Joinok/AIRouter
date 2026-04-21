package com.airouter.`data`.local.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.airouter.`data`.local.db.entity.SessionEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
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
public class SessionDao_Impl(
  __db: RoomDatabase,
) : SessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSessionEntity: EntityInsertAdapter<SessionEntity>

  private val __deleteAdapterOfSessionEntity: EntityDeleteOrUpdateAdapter<SessionEntity>

  private val __updateAdapterOfSessionEntity: EntityDeleteOrUpdateAdapter<SessionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSessionEntity = object : EntityInsertAdapter<SessionEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `sessions` (`id`,`title`,`providerId`,`modelId`,`systemPrompt`,`temperature`,`maxTokens`,`createdAt`,`updatedAt`,`messageCount`,`isPinned`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.providerId)
        statement.bindText(4, entity.modelId)
        val _tmpSystemPrompt: String? = entity.systemPrompt
        if (_tmpSystemPrompt == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSystemPrompt)
        }
        statement.bindDouble(6, entity.temperature.toDouble())
        statement.bindLong(7, entity.maxTokens.toLong())
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.messageCount.toLong())
        val _tmp: Int = if (entity.isPinned) 1 else 0
        statement.bindLong(11, _tmp.toLong())
      }
    }
    this.__deleteAdapterOfSessionEntity = object : EntityDeleteOrUpdateAdapter<SessionEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `sessions` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfSessionEntity = object : EntityDeleteOrUpdateAdapter<SessionEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `sessions` SET `id` = ?,`title` = ?,`providerId` = ?,`modelId` = ?,`systemPrompt` = ?,`temperature` = ?,`maxTokens` = ?,`createdAt` = ?,`updatedAt` = ?,`messageCount` = ?,`isPinned` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.providerId)
        statement.bindText(4, entity.modelId)
        val _tmpSystemPrompt: String? = entity.systemPrompt
        if (_tmpSystemPrompt == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpSystemPrompt)
        }
        statement.bindDouble(6, entity.temperature.toDouble())
        statement.bindLong(7, entity.maxTokens.toLong())
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.messageCount.toLong())
        val _tmp: Int = if (entity.isPinned) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindText(12, entity.id)
      }
    }
  }

  public override suspend fun insertSession(session: SessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfSessionEntity.insert(_connection, session)
  }

  public override suspend fun deleteSession(session: SessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfSessionEntity.handle(_connection, session)
  }

  public override suspend fun updateSession(session: SessionEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfSessionEntity.handle(_connection, session)
  }

  public override fun getAllSessions(): Flow<List<SessionEntity>> {
    val _sql: String = "SELECT * FROM sessions ORDER BY isPinned DESC, updatedAt DESC"
    return createFlow(__db, false, arrayOf("sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "modelId")
        val _cursorIndexOfSystemPrompt: Int = getColumnIndexOrThrow(_stmt, "systemPrompt")
        val _cursorIndexOfTemperature: Int = getColumnIndexOrThrow(_stmt, "temperature")
        val _cursorIndexOfMaxTokens: Int = getColumnIndexOrThrow(_stmt, "maxTokens")
        val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _cursorIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "messageCount")
        val _cursorIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _result: MutableList<SessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SessionEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_cursorIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_cursorIndexOfTitle)
          val _tmpProviderId: String
          _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          val _tmpModelId: String
          _tmpModelId = _stmt.getText(_cursorIndexOfModelId)
          val _tmpSystemPrompt: String?
          if (_stmt.isNull(_cursorIndexOfSystemPrompt)) {
            _tmpSystemPrompt = null
          } else {
            _tmpSystemPrompt = _stmt.getText(_cursorIndexOfSystemPrompt)
          }
          val _tmpTemperature: Float
          _tmpTemperature = _stmt.getDouble(_cursorIndexOfTemperature).toFloat()
          val _tmpMaxTokens: Int
          _tmpMaxTokens = _stmt.getLong(_cursorIndexOfMaxTokens).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_cursorIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_cursorIndexOfUpdatedAt)
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_cursorIndexOfMessageCount).toInt()
          val _tmpIsPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfIsPinned).toInt()
          _tmpIsPinned = _tmp != 0
          _item =
              SessionEntity(_tmpId,_tmpTitle,_tmpProviderId,_tmpModelId,_tmpSystemPrompt,_tmpTemperature,_tmpMaxTokens,_tmpCreatedAt,_tmpUpdatedAt,_tmpMessageCount,_tmpIsPinned)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSession(sessionId: String): SessionEntity? {
    val _sql: String = "SELECT * FROM sessions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "modelId")
        val _cursorIndexOfSystemPrompt: Int = getColumnIndexOrThrow(_stmt, "systemPrompt")
        val _cursorIndexOfTemperature: Int = getColumnIndexOrThrow(_stmt, "temperature")
        val _cursorIndexOfMaxTokens: Int = getColumnIndexOrThrow(_stmt, "maxTokens")
        val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _cursorIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _cursorIndexOfMessageCount: Int = getColumnIndexOrThrow(_stmt, "messageCount")
        val _cursorIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _result: SessionEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_cursorIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_cursorIndexOfTitle)
          val _tmpProviderId: String
          _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          val _tmpModelId: String
          _tmpModelId = _stmt.getText(_cursorIndexOfModelId)
          val _tmpSystemPrompt: String?
          if (_stmt.isNull(_cursorIndexOfSystemPrompt)) {
            _tmpSystemPrompt = null
          } else {
            _tmpSystemPrompt = _stmt.getText(_cursorIndexOfSystemPrompt)
          }
          val _tmpTemperature: Float
          _tmpTemperature = _stmt.getDouble(_cursorIndexOfTemperature).toFloat()
          val _tmpMaxTokens: Int
          _tmpMaxTokens = _stmt.getLong(_cursorIndexOfMaxTokens).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_cursorIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_cursorIndexOfUpdatedAt)
          val _tmpMessageCount: Int
          _tmpMessageCount = _stmt.getLong(_cursorIndexOfMessageCount).toInt()
          val _tmpIsPinned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfIsPinned).toInt()
          _tmpIsPinned = _tmp != 0
          _result =
              SessionEntity(_tmpId,_tmpTitle,_tmpProviderId,_tmpModelId,_tmpSystemPrompt,_tmpTemperature,_tmpMaxTokens,_tmpCreatedAt,_tmpUpdatedAt,_tmpMessageCount,_tmpIsPinned)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun incrementMessageCount(sessionId: String, updatedAt: Long) {
    val _sql: String =
        "UPDATE sessions SET messageCount = messageCount + 1, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateTitle(
    sessionId: String,
    title: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE sessions SET title = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, title)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updatePinned(sessionId: String, isPinned: Boolean) {
    val _sql: String = "UPDATE sessions SET isPinned = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isPinned) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
