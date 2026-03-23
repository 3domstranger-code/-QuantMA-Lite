package com.quantma.lite.domain.repository

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun insertMessage(message: ChatMessage): Long
    suspend fun updateMessageContent(messageId: Long, content: String)
    suspend fun deleteMessage(messageId: Long)
    suspend fun deleteMessages(ids: Set<Long>)
    suspend fun deleteMessagesForSession(sessionId: Long)

    suspend fun createSession(title: String): Long
    suspend fun getSession(sessionId: Long): ChatSession?
    fun getActiveSessions(): Flow<List<ChatSession>>
    fun getArchivedSessions(): Flow<List<ChatSession>>
    fun getAllSessions(): Flow<List<ChatSession>>
    suspend fun getActiveSessionCount(): Int
    suspend fun updateSessionTitle(sessionId: Long, title: String)
    suspend fun archiveSession(sessionId: Long)
    suspend fun unarchiveSession(sessionId: Long)
    suspend fun deleteSession(sessionId: Long)

    suspend fun compressMessage(messageId: Long, compressedContent: String, originalTokenCount: Int)
    suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessage>
}
