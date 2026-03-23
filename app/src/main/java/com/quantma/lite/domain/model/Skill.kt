package com.quantma.lite.domain.model

/**
 * A Skill is a specialized prompt template that activates when user input
 * matches one of its trigger patterns (keywords/regexes).
 *
 * When triggered, the skill's [promptInjection] is appended to the system prompt,
 * giving the agent domain-specific instructions for the task at hand.
 */
data class Skill(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Comma-separated keywords or regex patterns that activate this skill. */
    val triggerPatterns: List<String> = emptyList(),
    /** Instructions injected into the system prompt when the skill is triggered. */
    val promptInjection: String = "",
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)
