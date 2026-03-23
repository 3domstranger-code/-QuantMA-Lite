package com.quantma.lite.sas

/**
 * Secure Agent Shell — Hook Manager.
 *
 * Manages pre-command and post-command hooks that fire automatically
 * when a specific tool executes. Hooks are internal SAS tool commands —
 * they do NOT invoke shell processes.
 *
 * Anti-recursion: hooks execute at depth 1 and never trigger other hooks.
 *
 * Usage:
 * ```kotlin
 * val hooks = HookManager()
 * hooks.addHook(HookConfig(
 *     hookType = HookType.POST_COMMAND,
 *     triggerTool = ToolType.WRITE_FILE,
 *     hookAction = ToolCommand(tool = ToolType.FORMAT_FILE, args = mapOf("path" to "\$TARGET_PATH")),
 *     description = "Auto-format after write"
 * ))
 * ```
 */
class HookManager {

    private val hooks = mutableListOf<HookConfig>()

    // ─── Registration ───────────────────────────────────────────────────────

    /**
     * Register a new hook.
     */
    fun addHook(config: HookConfig) {
        hooks.add(config)
    }

    /**
     * Remove all hooks matching the trigger tool and hook type.
     */
    fun removeHook(triggerTool: ToolType, hookType: HookType) {
        hooks.removeAll { it.triggerTool == triggerTool && it.hookType == hookType }
    }

    /**
     * Remove a specific hook by identity.
     */
    fun removeHook(config: HookConfig) {
        hooks.remove(config)
    }

    /**
     * Clear all registered hooks.
     */
    fun clearAll() {
        hooks.clear()
    }

    // ─── Lookup ─────────────────────────────────────────────────────────────

    /**
     * Get all enabled pre-command hooks for the given tool that match [triggerCommand].
     * Results are sorted by [HookConfig.priority] (ascending — lower runs first).
     */
    fun getPreHooks(tool: ToolType, triggerCommand: ToolCommand? = null): List<HookConfig> =
        hooks
            .filter { it.enabled && it.hookType == HookType.PRE_COMMAND && it.triggerTool == tool }
            .filter { triggerCommand == null || evaluateCondition(it.condition, triggerCommand) }
            .sortedBy { it.priority }

    /**
     * Get all enabled post-command hooks for the given tool that match [triggerCommand].
     * Results are sorted by [HookConfig.priority] (ascending — lower runs first).
     *
     * @param lastOutput The SUCCESS output from the trigger command (used for [HookCondition.OutputContains]).
     */
    fun getPostHooks(
        tool: ToolType,
        triggerCommand: ToolCommand? = null,
        lastOutput: String = ""
    ): List<HookConfig> =
        hooks
            .filter { it.enabled && it.hookType == HookType.POST_COMMAND && it.triggerTool == tool }
            .filter { triggerCommand == null || evaluateCondition(it.condition, triggerCommand, lastOutput) }
            .sortedBy { it.priority }

    /**
     * Check if any hooks are registered for the given tool.
     */
    fun hasHooks(tool: ToolType): Boolean =
        hooks.any { it.enabled && it.triggerTool == tool }

    // ─── Condition Evaluation ────────────────────────────────────────────────

    /**
     * Evaluate a [HookCondition] against the trigger command and optional output.
     * Returns true if the condition passes (hook should fire), or if [condition] is null.
     */
    fun evaluateCondition(
        condition: HookCondition?,
        triggerCommand: ToolCommand,
        lastOutput: String = ""
    ): Boolean {
        condition ?: return true  // null = always fire

        return when (condition) {
            is HookCondition.PathExtension -> {
                val path = triggerCommand.stringArg("path") ?: return false
                val ext = path.substringAfterLast('.', "").lowercase()
                ext in condition.extensions
            }
            is HookCondition.PathPattern -> {
                val path = triggerCommand.stringArg("path") ?: return false
                condition.pattern.containsMatchIn(path)
            }
            is HookCondition.OutputContains -> {
                lastOutput.contains(condition.substring, ignoreCase = condition.ignoreCase)
            }
        }
    }

    // ─── Placeholder Resolution ─────────────────────────────────────────────

    /**
     * Resolve placeholders in a hook's action command using the trigger command's args.
     *
     * Supported placeholders:
     * - `$TARGET_PATH` → trigger command's `path` arg
     * - `$TARGET_CONTENT` → trigger command's `content` arg
     * - `$TARGET_PATTERN` → trigger command's `pattern` arg
     *
     * @param hookAction The hook's action command template
     * @param triggerCommand The command that triggered this hook
     * @return A new [ToolCommand] with resolved args
     */
    fun resolveHookPlaceholders(hookAction: ToolCommand, triggerCommand: ToolCommand): ToolCommand {
        val resolvedArgs = hookAction.args.mapValues { (_, value) ->
            if (value is String) {
                resolvePlaceholder(value, triggerCommand)
            } else {
                value
            }
        }
        return hookAction.copy(args = resolvedArgs)
    }

    private fun resolvePlaceholder(template: String, trigger: ToolCommand): String {
        var result = template
        PLACEHOLDERS.forEach { (placeholder, argKey) ->
            if (result.contains(placeholder)) {
                val replacement = trigger.stringArg(argKey) ?: ""
                result = result.replace(placeholder, replacement)
            }
        }
        return result
    }

    // ─── Serialization ──────────────────────────────────────────────────────

    /**
     * Export all hook configurations for persistence.
     */
    fun toConfigList(): List<HookConfig> = hooks.toList()

    /**
     * Load hooks from a saved configuration. Replaces current hooks.
     */
    fun loadFrom(configs: List<HookConfig>) {
        hooks.clear()
        hooks.addAll(configs)
    }

    /**
     * Get a human-readable summary of all registered hooks.
     */
    fun summary(): String {
        if (hooks.isEmpty()) return "No hooks registered."
        return hooks.sortedBy { it.priority }.joinToString("\n") { hook ->
            val type = if (hook.hookType == HookType.PRE_COMMAND) "PRE" else "POST"
            val status = if (hook.enabled) "ON" else "OFF"
            val conditionStr = when (val c = hook.condition) {
                null -> ""
                is HookCondition.PathExtension -> " [ext:${c.extensions.joinToString(",")}]"
                is HookCondition.PathPattern -> " [pattern:${c.pattern}]"
                is HookCondition.OutputContains -> " [output:\"${c.substring}\"]"
            }
            val priorityStr = if (hook.priority != 0) " p=${hook.priority}" else ""
            "[$status]$priorityStr $type ${hook.triggerTool.toolName}$conditionStr → ${hook.hookAction.tool.toolName}: ${hook.description}"
        }
    }

    companion object {
        private val PLACEHOLDERS = mapOf(
            "\$TARGET_PATH" to "path",
            "\$TARGET_CONTENT" to "content",
            "\$TARGET_PATTERN" to "pattern"
        )

        /** Maximum hook execution depth (prevents infinite recursion). */
        const val MAX_HOOK_DEPTH = 1
    }
}
