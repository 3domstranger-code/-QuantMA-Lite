package com.quantma.lite.sas

/**
 * Secure Agent Shell — System Prompt Builder.
 *
 * Generates the system prompt that instructs the LLM how to use the SAS tool protocol.
 * The prompt includes:
 * - Role description (backend engine, not user-facing)
 * - JSON command format specification
 * - Available tools with argument descriptions
 * - Usage examples for each tool
 * - Security rules (relative paths, one tool per message, read before write)
 *
 * Also provides formatting for tool results to feed back into the LLM context.
 */
object AgentSystemPrompt {

    /**
     * Build the complete system prompt for the LLM agent.
     *
     * @param workingDir The current working directory path (for context)
     * @param tools List of available tools (defaults to all tools)
     * @return System prompt string ready for use in LLM context
     */
    fun buildSystemPrompt(
        workingDir: String,
        tools: List<ToolType> = ToolType.entries.filter { it != ToolType.DONE }
    ): String = buildString {
        appendLine(ROLE_DESCRIPTION)
        appendLine()
        appendLine("Working directory: $workingDir")
        appendLine()
        appendLine(PROTOCOL_SPEC)
        appendLine()
        appendLine("Available tools:")
        appendLine()

        for (category in ToolType.Category.entries) {
            val categoryTools = tools.filter { it.category == category }
            if (categoryTools.isEmpty()) continue
            appendLine("## ${category.label}")
            for (tool in categoryTools) {
                val desc = TOOL_DESCRIPTIONS[tool] ?: continue
                appendLine("- ${tool.toolName}: ${desc.description}")
            }
            appendLine()
        }

        appendLine(RULES)
        appendLine()
        appendLine(EXAMPLES)
    }.trimEnd()

    /**
     * Format a tool result for insertion into the LLM context.
     *
     * @param toolName Name of the tool that was executed
     * @param result The execution result
     * @return Formatted string for the LLM to process
     */
    fun buildToolResultPrompt(toolName: String, result: ToolResponse): String = buildString {
        append("RESULT of $toolName:\n")
        when (result) {
            is ToolResponse.Success -> append(result.output)
            is ToolResponse.Error -> append("ERROR [${result.code}]: ${result.message}")
            is ToolResponse.NeedsApproval -> {
                append("PENDING_APPROVAL: ${result.description}")
                if (result.preview != null) {
                    append("\n${result.preview}")
                }
            }
        }
    }

    /**
     * Build a compact tool list summary (for constrained context windows).
     */
    fun buildToolListCompact(): String = buildString {
        appendLine("Tools: ${ToolType.entries.filter { it != ToolType.DONE }.joinToString(", ") { it.toolName }}")
        appendLine("Format: {\"tool\":\"name\",\"args\":{...}}")
        appendLine("Done: {\"tool\":\"done\"}")
    }.trimEnd()

    // ═══════════════════════════════════════════════════════════════════════
    // Prompt Components
    // ═══════════════════════════════════════════════════════════════════════

    private const val ROLE_DESCRIPTION = """You are a read-only code agent running on an Android device. You do not talk to the user directly. You only output JSON commands to inspect files and review git repositories.

Your purpose is to help the user with code review, exploration, and analysis tasks by reading source code files and inspecting git status within a sandboxed working directory. You cannot modify files or perform write operations."""

    private const val PROTOCOL_SPEC = """Command format (one JSON object per message):
{"tool": "tool_name", "args": {"key": "value"}}

Optional fields:
{"id": "unique_id", "tool": "tool_name", "args": {...}, "thought": "your reasoning"}

When finished, output:
{"tool": "done"}"""

    private const val RULES = """Rules:
1. Output exactly ONE valid JSON command per message. No markdown, no explanation.
2. Use RELATIVE paths only (e.g. "src/main.kt", not "/storage/emulated/0/...").
3. If you need to think, use the "thought" field inside the JSON.
4. When a command fails (error response), analyze the error and try a different approach.
5. Signal completion with {"tool": "done"} when the task is finished.
6. Do not output multiple commands in a single message.
7. Paths cannot contain ".." — no directory traversal.
8. You are a READ-ONLY agent. You cannot write, delete, or modify files or git state."""

    private const val EXAMPLES = """Common workflows:
Code review: list_dir → read_file → search_grep → done
Git inspect: git_status → git_branch → git_stash → done

Example interaction:

User: "What does utils.py contain?"

Step 1 — List files:
{"tool": "list_dir", "args": {"path": "."}}

Step 2 — Read the file:
{"tool": "read_file", "args": {"path": "utils.py"}}

Step 3 — Done:
{"tool": "done"}"""

    // ─── Tool Descriptions ──────────────────────────────────────────────

    private data class ToolDesc(val description: String, val example: String)

    private val TOOL_DESCRIPTIONS = mapOf(
        ToolType.READ_FILE to ToolDesc(
            description = "Read the contents of a file",
            example = """{"tool": "read_file", "args": {"path": "src/main.kt"}}"""
        ),
        ToolType.LIST_DIR to ToolDesc(
            description = "List files and directories in a path",
            example = """{"tool": "list_dir", "args": {"path": "src"}}"""
        ),
        ToolType.SEARCH_GREP to ToolDesc(
            description = "Search for a text pattern (regex) in files under a directory",
            example = """{"tool": "search_grep", "args": {"pattern": "TODO|FIXME", "path": "src"}}"""
        ),
        ToolType.GIT_STATUS to ToolDesc(
            description = "Show git status of the repository",
            example = """{"tool": "git_status", "args": {"path": "."}}"""
        ),
        ToolType.GET_DIFF to ToolDesc(
            description = "Show git diff of uncommitted changes",
            example = """{"tool": "get_diff", "args": {"path": "."}}"""
        ),
        ToolType.GIT_BRANCH to ToolDesc(
            description = "Show current git branch and list branches",
            example = """{"tool": "git_branch", "args": {"path": "."}}"""
        ),
        ToolType.GIT_STASH to ToolDesc(
            description = "List git stash entries (read-only)",
            example = """{"tool": "git_stash", "args": {"operation": "list"}}"""
        )
    )
}
