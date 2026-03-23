package com.quantma.lite.data.agent

import timber.log.Timber
import com.quantma.lite.data.local.db.HookDao
import com.quantma.lite.data.local.db.entity.HookEntity
import com.quantma.lite.domain.model.HookTrigger
import com.quantma.lite.domain.model.ToolCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires hook actions at specific points in the agent loop.
 *
 * Hooks are event-driven: when a trigger event occurs (e.g., before commit,
 * after file write), enabled hooks for that trigger return action instructions
 * that get injected into the agent's context.
 */
@Singleton
class HooksEngine @Inject constructor(
    private val hookDao: HookDao
) {
    /**
     * Get action instructions for a specific trigger event.
     * Returns combined action text, or empty string if no hooks match.
     */
    suspend fun fireHooks(trigger: HookTrigger): String {
        val hooks = hookDao.getEnabledByTrigger(trigger.name)
        if (hooks.isEmpty()) return ""

        Timber.d("Firing ${hooks.size} hooks for $trigger")

        return hooks.joinToString("\n") { hook ->
            "HOOK (${trigger.name}): ${hook.action}"
        }
    }

    /**
     * Determine which trigger to fire based on the tool call being executed.
     * Returns null if the tool call doesn't trigger any hooks.
     */
    fun getTriggerForToolCall(toolCall: ToolCall): HookTrigger? {
        return when (toolCall) {
            is ToolCall.GitCommit -> HookTrigger.PRE_COMMIT
            is ToolCall.WriteFile -> HookTrigger.ON_FILE_CHANGE
            else -> null
        }
    }

    /**
     * Determine which post-execution trigger to fire.
     */
    fun getPostTriggerForToolCall(toolCall: ToolCall): HookTrigger? {
        return when (toolCall) {
            is ToolCall.GitCommit -> HookTrigger.POST_COMMIT
            else -> null
        }
    }

    /**
     * Seed default hooks if the database is empty.
     * Called once on app startup.
     */
    suspend fun seedDefaultHooks() {
        if (hookDao.getCount() > 0) return

        val defaults = listOf(
            HookEntity(
                trigger = HookTrigger.PRE_COMMIT.name,
                action = "Before committing: review all staged changes, ensure the commit message accurately describes the changes, and verify no debug code or temporary files are included.",
                isEnabled = true,
                isBuiltIn = true
            ),
            HookEntity(
                trigger = HookTrigger.ON_FILE_CHANGE.name,
                action = "After writing a file: verify the changes are correct and consistent with the project's coding style.",
                isEnabled = true,
                isBuiltIn = true
            ),
            HookEntity(
                trigger = HookTrigger.ON_ERROR.name,
                action = "When an error occurs: analyze the error message, identify the root cause, and suggest a fix.",
                isEnabled = false,
                isBuiltIn = true
            )
        )

        for (hook in defaults) {
            hookDao.insert(hook)
        }
        Timber.i("Seeded ${defaults.size} default hooks")
    }
}
