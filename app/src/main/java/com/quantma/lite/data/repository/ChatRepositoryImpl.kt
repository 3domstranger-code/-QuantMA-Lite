package com.quantma.lite.data.repository

import com.quantma.lite.data.local.db.ChatMessageDao
import com.quantma.lite.data.local.db.ChatSessionDao
import com.quantma.lite.data.local.db.entity.ChatMessageEntity
import com.quantma.lite.data.local.db.entity.ChatSessionEntity
import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.ChatSession
import com.quantma.lite.domain.model.Role
import com.quantma.lite.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao
) : ChatRepository {

    override fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertMessage(message: ChatMessage): Long {
        val id = chatMessageDao.insert(message.toEntity())
        chatSessionDao.onNewMessage(message.sessionId, message.timestamp)
        return id
    }

    override suspend fun updateMessageContent(messageId: Long, content: String) {
        chatMessageDao.updateContent(messageId, content)
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatMessageDao.deleteById(messageId)
    }

    override suspend fun deleteMessages(ids: Set<Long>) {
        if (ids.isNotEmpty()) chatMessageDao.deleteByIds(ids.toList())
    }

    override suspend fun deleteMessagesForSession(sessionId: Long) {
        chatMessageDao.deleteMessagesForSession(sessionId)
    }

    override suspend fun createSession(title: String): Long {
        val now = System.currentTimeMillis()
        return chatSessionDao.insert(
            ChatSessionEntity(
                title = title,
                createdAt = now,
                lastMessageAt = now
            )
        )
    }

    override suspend fun getSession(sessionId: Long): ChatSession? {
        return chatSessionDao.getById(sessionId)?.toDomain()
    }

    override fun getActiveSessions(): Flow<List<ChatSession>> {
        return chatSessionDao.getActiveSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getArchivedSessions(): Flow<List<ChatSession>> {
        return chatSessionDao.getArchivedSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatSessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getActiveSessionCount(): Int {
        return chatSessionDao.getActiveSessionCount()
    }

    override suspend fun updateSessionTitle(sessionId: Long, title: String) {
        chatSessionDao.updateTitle(sessionId, title)
    }

    override suspend fun archiveSession(sessionId: Long) {
        chatSessionDao.setArchived(sessionId, true)
    }

    override suspend fun unarchiveSession(sessionId: Long) {
        chatSessionDao.setArchived(sessionId, false)
    }

    override suspend fun deleteSession(sessionId: Long) {
        chatMessageDao.deleteMessagesForSession(sessionId)
        chatSessionDao.delete(sessionId)
    }

    override suspend fun compressMessage(messageId: Long, compressedContent: String, originalTokenCount: Int) {
        chatMessageDao.compressMessage(messageId, compressedContent, originalTokenCount)
    }

    override suspend fun getMessagesForSessionOnce(sessionId: Long): List<ChatMessage> {
        return chatMessageDao.getMessagesForSessionOnce(sessionId).map { it.toDomain() }
    }

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = Role.valueOf(role),
        content = content,
        timestamp = timestamp,
        isCompressed = isCompressed,
        originalTokenCount = originalTokenCount
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = if (id == 0L) 0 else id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        timestamp = timestamp,
        isCompressed = isCompressed,
        originalTokenCount = originalTokenCount
    )

    private fun ChatSessionEntity.toDomain() = ChatSession(
        id = id,
        title = title,
        createdAt = createdAt,
        isArchived = isArchived,
        lastMessageAt = lastMessageAt,
        messageCount = messageCount
    )
}
