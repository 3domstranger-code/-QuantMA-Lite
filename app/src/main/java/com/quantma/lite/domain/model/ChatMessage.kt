package com.quantma.lite.domain.model

data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long = 1,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false,
    val originalTokenCount: Int = 0
)

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}
