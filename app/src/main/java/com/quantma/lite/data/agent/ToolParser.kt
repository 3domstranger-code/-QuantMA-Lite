package com.quantma.lite.data.agent

import com.quantma.lite.domain.model.ToolCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses LLM text output for ACTION: tool calls.
 *
 * Protocol:
 *   ACTION: read_file <path>
 *   ACTION: list_files <path>
 *   ACTION: write_file <path>
 *   CONTENT:
 *   <file content>
 *   END_CONTENT
 *   ACTION: git_status <path>
 *
 * Returns null if no action found (normal chat response).
 */
@Singleton
class ToolParser @Inject constructor() {

    fun parse(response: String): ToolCall? {
        val lines = response.lines()

        // Check for DONE keyword
        if (lines.any { it.trim().equals("DONE", ignoreCase = true) }) {
            return ToolCall.Done
        }

        // Find first ACTION: line
        val actionLineIndex = lines.indexOfFirst { it.trimStart().startsWith("ACTION:") }
        if (actionLineIndex == -1) return null

        val actionLine = lines[actionLineIndex].trimStart().removePrefix("ACTION:").trim()

        // Parse tool name and argument
        val spaceIndex = actionLine.indexOf(' ')
        if (spaceIndex == -1) return null

        val toolName = actionLine.substring(0, spaceIndex).lowercase()
        val argument = actionLine.substring(spaceIndex + 1).trim()

        if (argument.isEmpty()) return null

        return when (toolName) {
            "read_file" -> ToolCall.ReadFile(argument)
            "list_files" -> ToolCall.ListFiles(argument)
            "git_status" -> ToolCall.GitStatus(argument)
            "git_diff" -> ToolCall.GitDiff(argument)
            "git_branch" -> ToolCall.GitBranch(argument)
            "git_pull" -> ToolCall.GitPull(argument)
            "git_push" -> ToolCall.GitPush(argument)
            "git_clone" -> {
                // Format: git_clone <url> <path>  (url has no spaces, path is the rest)
                val cloneSpaceIndex = argument.indexOf(' ')
                if (cloneSpaceIndex == -1) return null
                val url = argument.substring(0, cloneSpaceIndex).trim()
                val path = argument.substring(cloneSpaceIndex + 1).trim()
                if (url.isEmpty() || path.isEmpty()) return null
                ToolCall.GitClone(url, path)
            }
            "git_commit" -> {
                val content = extractWriteContent(lines, actionLineIndex)
                ToolCall.GitCommit(argument, content)
            }
            "write_file" -> {
                val content = extractWriteContent(lines, actionLineIndex)
                ToolCall.WriteFile(argument, content)
            }
            // Phase 8 (v1.8.0)
            "run_command" -> ToolCall.RunCommand(argument)
            "search_files" -> {
                // Format: search_files <pattern> <path>  (pattern has no spaces)
                val sfSpaceIndex = argument.indexOf(' ')
                if (sfSpaceIndex == -1) return null
                val pattern = argument.substring(0, sfSpaceIndex).trim()
                val path = argument.substring(sfSpaceIndex + 1).trim()
                if (pattern.isEmpty() || path.isEmpty()) return null
                ToolCall.SearchFiles(pattern, path)
            }
            "delete_file" -> ToolCall.DeleteFile(argument)
            // Phase 3 (v1.2.0) — Branch / Merge / Stash
            "git_create_branch" -> {
                val sp = argument.indexOf(' ')
                if (sp == -1) return null
                val path = argument.substring(0, sp).trim()
                val name = argument.substring(sp + 1).trim()
                if (path.isEmpty() || name.isEmpty()) return null
                ToolCall.GitCreateBranch(path, name)
            }
            "git_checkout" -> {
                val sp = argument.indexOf(' ')
                if (sp == -1) return null
                val path = argument.substring(0, sp).trim()
                val name = argument.substring(sp + 1).trim()
                if (path.isEmpty() || name.isEmpty()) return null
                ToolCall.GitCheckout(path, name)
            }
            "git_delete_branch" -> {
                val sp = argument.indexOf(' ')
                if (sp == -1) return null
                val path = argument.substring(0, sp).trim()
                val name = argument.substring(sp + 1).trim()
                if (path.isEmpty() || name.isEmpty()) return null
                ToolCall.GitDeleteBranch(path, name)
            }
            "git_merge" -> {
                val sp = argument.indexOf(' ')
                if (sp == -1) return null
                val path = argument.substring(0, sp).trim()
                val branchName = argument.substring(sp + 1).trim()
                if (path.isEmpty() || branchName.isEmpty()) return null
                ToolCall.GitMerge(path, branchName)
            }
            "git_stash_save" -> {
                val sp = argument.indexOf(' ')
                if (sp == -1) ToolCall.GitStashSave(argument.trim())
                else ToolCall.GitStashSave(
                    argument.substring(0, sp).trim(),
                    argument.substring(sp + 1).trim()
                )
            }
            "git_stash_pop" -> ToolCall.GitStashPop(argument)
            "git_stash_list" -> ToolCall.GitStashList(argument)
            else -> null
        }
    }

    /**
     * Extract content between CONTENT: and END_CONTENT markers.
     * If END_CONTENT is missing, everything after CONTENT: is used.
     */
    private fun extractWriteContent(lines: List<String>, actionLineIndex: Int): String {
        val contentStartIndex = lines.indexOfFirst(actionLineIndex + 1) {
            it.trimStart().equals("CONTENT:", ignoreCase = true)
        }
        if (contentStartIndex == -1) return ""

        val contentEndIndex = lines.indexOfFirst(contentStartIndex + 1) {
            it.trimStart().equals("END_CONTENT", ignoreCase = true)
        }

        val endIndex = if (contentEndIndex != -1) contentEndIndex else lines.size
        return lines.subList(contentStartIndex + 1, endIndex).joinToString("\n")
    }

    /**
     * Find first matching line starting from [startIndex].
     */
    private fun List<String>.indexOfFirst(startIndex: Int, predicate: (String) -> Boolean): Int {
        for (i in startIndex until size) {
            if (predicate(this[i])) return i
        }
        return -1
    }
}
