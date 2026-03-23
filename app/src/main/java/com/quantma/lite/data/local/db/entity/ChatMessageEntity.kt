package com.quantma.lite.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val isCompressed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val originalTokenCount: Int = 0
)
