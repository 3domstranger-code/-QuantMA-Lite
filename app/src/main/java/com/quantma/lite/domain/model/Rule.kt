package com.quantma.lite.domain.model

/**
 * A Rule is a persistent code-quality instruction that is always appended
 * to the agent's system prompt when enabled.
 *
 * Rules enforce coding standards (style, architecture, security, comments)
 * across all agent interactions.
 */
data class Rule(
    val id: Long = 0,
    val category: RuleCategory,
    val instruction: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

enum class RuleCategory {
    CODE_STYLE,
    ARCHITECTURE,
    SECURITY,
    COMMENTS
}
