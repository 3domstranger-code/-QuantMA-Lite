package com.quantma.lite.domain.model

/**
 * Domain model for AI agent configuration.
 * Stored in Room DB (agent_configs table).
 */
data class AgentConfig(
    val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val role: AgentRole = AgentRole.ASSISTANT,
    val customInstructions: String = "",
    val restrictions: String = "",
    val isDefault: Boolean = false
)

/**
 * Agent persona presets used for quick configuration.
 */
enum class AgentRole {
    ASSISTANT,   // Code Assistant
    REVIEWER,    // Code Reviewer
    TESTER,      // Test Writer
    DOCUMENTER   // Documenter
}
