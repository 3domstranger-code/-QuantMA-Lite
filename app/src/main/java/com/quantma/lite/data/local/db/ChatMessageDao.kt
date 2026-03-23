package com.quantma.lite.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quantma.lite.data.local.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateContent(messageId: Long, content: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    @Query("UPDATE chat_messages SET content = :content, isCompressed = 1, originalTokenCount = :originalTokens WHERE id = :messageId")
    suspend fun compressMessage(messageId: Long, content: String, originalTokens: Int)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessageEntity>
}
