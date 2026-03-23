package com.quantma.lite.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quantma.lite.data.local.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions WHERE isArchived = 0 ORDER BY lastMessageAt DESC")
    fun getActiveSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE isArchived = 1 ORDER BY lastMessageAt DESC")
    fun getArchivedSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE isArchived = 0")
    suspend fun getActiveSessionCount(): Int

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): ChatSessionEntity?

    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: Long, title: String)

    @Query("UPDATE chat_sessions SET isArchived = :archived WHERE id = :sessionId")
    suspend fun setArchived(sessionId: Long, archived: Boolean)

    @Query("UPDATE chat_sessions SET lastMessageAt = :timestamp, messageCount = messageCount + 1 WHERE id = :sessionId")
    suspend fun onNewMessage(sessionId: Long, timestamp: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)
}
