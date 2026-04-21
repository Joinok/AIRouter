package com.airouter.`data`.local.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.airouter.`data`.local.db.entity.MessageEntity
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
public class MessageDao_Impl(
  __db: RoomDatabase,
) : MessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>

  private val __deleteAdapterOfMessageEntity: EntityDeleteOrUpdateAdapter<MessageEntity>

  private val __updateAdapterOfMessageEntity: EntityDeleteOrUpdateAdapter<MessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `messages` (`id`,`sessionId`,`role`,`content`,`modelId`,`providerId`,`timestamp`,`promptTokens`,`completionTokens`,`totalTokens`,`estimatedCost`,`isError`,`errorMessage`,`attachments`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        val _tmpModelId: String? = entity.modelId
        if (_tmpModelId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModelId)
        }
        val _tmpProviderId: String? = entity.providerId
        if (_tmpProviderId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpProviderId)
        }
        statement.bindLong(7, entity.timestamp)
        statement.bindLong(8, entity.promptTokens.toLong())
        statement.bindLong(9, entity.completionTokens.toLong())
        statement.bindLong(10, entity.totalTokens.toLong())
        val _tmpEstimatedCost: Float? = entity.estimatedCost
        if (_tmpEstimatedCost == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpEstimatedCost.toDouble())
        }
        val _tmp: Int = if (entity.isError) 1 else 0
        statement.bindLong(12, _tmp.toLong())
        val _tmpErrorMessage: String? = entity.errorMessage
        if (_tmpErrorMessage == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpErrorMessage)
        }
        val _tmpAttachments: String? = entity.attachments
        if (_tmpAttachments == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpAttachments)
        }
      }
    }
    this.__deleteAdapterOfMessageEntity = object : EntityDeleteOrUpdateAdapter<MessageEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `messages` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.id)
      }
    }
    this.__updateAdapterOfMessageEntity = object : EntityDeleteOrUpdateAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `messages` SET `id` = ?,`sessionId` = ?,`role` = ?,`content` = ?,`modelId` = ?,`providerId` = ?,`timestamp` = ?,`promptTokens` = ?,`completionTokens` = ?,`totalTokens` = ?,`estimatedCost` = ?,`isError` = ?,`errorMessage` = ?,`attachments` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        val _tmpModelId: String? = entity.modelId
        if (_tmpModelId == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpModelId)
        }
        val _tmpProviderId: String? = entity.providerId
        if (_tmpProviderId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpProviderId)
        }
        statement.bindLong(7, entity.timestamp)
        statement.bindLong(8, entity.promptTokens.toLong())
        statement.bindLong(9, entity.completionTokens.toLong())
        statement.bindLong(10, entity.totalTokens.toLong())
        val _tmpEstimatedCost: Float? = entity.estimatedCost
        if (_tmpEstimatedCost == null) {
          statement.bindNull(11)
        } else {
          statement.bindDouble(11, _tmpEstimatedCost.toDouble())
        }
        val _tmp: Int = if (entity.isError) 1 else 0
        statement.bindLong(12, _tmp.toLong())
        val _tmpErrorMessage: String? = entity.errorMessage
        if (_tmpErrorMessage == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpErrorMessage)
        }
        val _tmpAttachments: String? = entity.attachments
        if (_tmpAttachments == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpAttachments)
        }
        statement.bindText(15, entity.id)
      }
    }
  }

  public override suspend fun insertMessage(message: MessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfMessageEntity.insert(_connection, message)
  }

  public override suspend fun deleteMessage(message: MessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfMessageEntity.handle(_connection, message)
  }

  public override suspend fun updateMessage(message: MessageEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfMessageEntity.handle(_connection, message)
  }

  public override fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE sessionId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _cursorIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _cursorIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _cursorIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "modelId")
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _cursorIndexOfPromptTokens: Int = getColumnIndexOrThrow(_stmt, "promptTokens")
        val _cursorIndexOfCompletionTokens: Int = getColumnIndexOrThrow(_stmt, "completionTokens")
        val _cursorIndexOfTotalTokens: Int = getColumnIndexOrThrow(_stmt, "totalTokens")
        val _cursorIndexOfEstimatedCost: Int = getColumnIndexOrThrow(_stmt, "estimatedCost")
        val _cursorIndexOfIsError: Int = getColumnIndexOrThrow(_stmt, "isError")
        val _cursorIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _cursorIndexOfAttachments: Int = getColumnIndexOrThrow(_stmt, "attachments")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_cursorIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_cursorIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_cursorIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_cursorIndexOfContent)
          val _tmpModelId: String?
          if (_stmt.isNull(_cursorIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_cursorIndexOfModelId)
          }
          val _tmpProviderId: String?
          if (_stmt.isNull(_cursorIndexOfProviderId)) {
            _tmpProviderId = null
          } else {
            _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_cursorIndexOfTimestamp)
          val _tmpPromptTokens: Int
          _tmpPromptTokens = _stmt.getLong(_cursorIndexOfPromptTokens).toInt()
          val _tmpCompletionTokens: Int
          _tmpCompletionTokens = _stmt.getLong(_cursorIndexOfCompletionTokens).toInt()
          val _tmpTotalTokens: Int
          _tmpTotalTokens = _stmt.getLong(_cursorIndexOfTotalTokens).toInt()
          val _tmpEstimatedCost: Float?
          if (_stmt.isNull(_cursorIndexOfEstimatedCost)) {
            _tmpEstimatedCost = null
          } else {
            _tmpEstimatedCost = _stmt.getDouble(_cursorIndexOfEstimatedCost).toFloat()
          }
          val _tmpIsError: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfIsError).toInt()
          _tmpIsError = _tmp != 0
          val _tmpErrorMessage: String?
          if (_stmt.isNull(_cursorIndexOfErrorMessage)) {
            _tmpErrorMessage = null
          } else {
            _tmpErrorMessage = _stmt.getText(_cursorIndexOfErrorMessage)
          }
          val _tmpAttachments: String?
          if (_stmt.isNull(_cursorIndexOfAttachments)) {
            _tmpAttachments = null
          } else {
            _tmpAttachments = _stmt.getText(_cursorIndexOfAttachments)
          }
          _item =
              MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpModelId,_tmpProviderId,_tmpTimestamp,_tmpPromptTokens,_tmpCompletionTokens,_tmpTotalTokens,_tmpEstimatedCost,_tmpIsError,_tmpErrorMessage,_tmpAttachments)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMessagesBySessionOnce(sessionId: String): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE sessionId = ? ORDER BY timestamp ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _cursorIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _cursorIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _cursorIndexOfModelId: Int = getColumnIndexOrThrow(_stmt, "modelId")
        val _cursorIndexOfProviderId: Int = getColumnIndexOrThrow(_stmt, "providerId")
        val _cursorIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _cursorIndexOfPromptTokens: Int = getColumnIndexOrThrow(_stmt, "promptTokens")
        val _cursorIndexOfCompletionTokens: Int = getColumnIndexOrThrow(_stmt, "completionTokens")
        val _cursorIndexOfTotalTokens: Int = getColumnIndexOrThrow(_stmt, "totalTokens")
        val _cursorIndexOfEstimatedCost: Int = getColumnIndexOrThrow(_stmt, "estimatedCost")
        val _cursorIndexOfIsError: Int = getColumnIndexOrThrow(_stmt, "isError")
        val _cursorIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _cursorIndexOfAttachments: Int = getColumnIndexOrThrow(_stmt, "attachments")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_cursorIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_cursorIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_cursorIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_cursorIndexOfContent)
          val _tmpModelId: String?
          if (_stmt.isNull(_cursorIndexOfModelId)) {
            _tmpModelId = null
          } else {
            _tmpModelId = _stmt.getText(_cursorIndexOfModelId)
          }
          val _tmpProviderId: String?
          if (_stmt.isNull(_cursorIndexOfProviderId)) {
            _tmpProviderId = null
          } else {
            _tmpProviderId = _stmt.getText(_cursorIndexOfProviderId)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_cursorIndexOfTimestamp)
          val _tmpPromptTokens: Int
          _tmpPromptTokens = _stmt.getLong(_cursorIndexOfPromptTokens).toInt()
          val _tmpCompletionTokens: Int
          _tmpCompletionTokens = _stmt.getLong(_cursorIndexOfCompletionTokens).toInt()
          val _tmpTotalTokens: Int
          _tmpTotalTokens = _stmt.getLong(_cursorIndexOfTotalTokens).toInt()
          val _tmpEstimatedCost: Float?
          if (_stmt.isNull(_cursorIndexOfEstimatedCost)) {
            _tmpEstimatedCost = null
          } else {
            _tmpEstimatedCost = _stmt.getDouble(_cursorIndexOfEstimatedCost).toFloat()
          }
          val _tmpIsError: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_cursorIndexOfIsError).toInt()
          _tmpIsError = _tmp != 0
          val _tmpErrorMessage: String?
          if (_stmt.isNull(_cursorIndexOfErrorMessage)) {
            _tmpErrorMessage = null
          } else {
            _tmpErrorMessage = _stmt.getText(_cursorIndexOfErrorMessage)
          }
          val _tmpAttachments: String?
          if (_stmt.isNull(_cursorIndexOfAttachments)) {
            _tmpAttachments = null
          } else {
            _tmpAttachments = _stmt.getText(_cursorIndexOfAttachments)
          }
          _item =
              MessageEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpModelId,_tmpProviderId,_tmpTimestamp,_tmpPromptTokens,_tmpCompletionTokens,_tmpTotalTokens,_tmpEstimatedCost,_tmpIsError,_tmpErrorMessage,_tmpAttachments)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessageById(id: String) {
    val _sql: String = "DELETE FROM messages WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessagesBySession(sessionId: String) {
    val _sql: String = "DELETE FROM messages WHERE sessionId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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
