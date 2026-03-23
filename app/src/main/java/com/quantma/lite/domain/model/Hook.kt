package com.quantma.lite.domain.model

/**
 * A Hook is an event-driven action that fires at specific points in the
 * agent loop (before/after commit, on file change, on error).
 *
 * When a hook's trigger event occurs, its [action] instruction is injected
 * into the next agent round as additional context.
 */
data class Hook(
    val id: Long = 0,
    val trigger: HookTrigger,
    /** Instruction for the agent to execute when triggered. */
    val action: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

enum class HookTrigger {
    PRE_COMMIT,
    POST_COMMIT,
    ON_FILE_CHANGE,
    ON_ERROR
}
