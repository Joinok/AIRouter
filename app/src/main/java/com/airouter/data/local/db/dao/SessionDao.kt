package com.airouter.data.local.db.dao

import androidx.room.*
import com.airouter.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("UPDATE sessions SET messageCount = messageCount + 1, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET isPinned = :isPinned WHERE id = :sessionId")
    suspend fun updatePinned(sessionId: String, isPinned: Boolean)
}
