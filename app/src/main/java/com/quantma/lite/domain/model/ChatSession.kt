package com.quantma.lite.domain.model

data class ChatSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val lastMessageAt: Long = 0,
    val messageCount: Int = 0
)
