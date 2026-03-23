package com.quantma.lite.data.agent

import com.quantma.lite.data.inference.PromptFormatter
import com.quantma.lite.domain.model.AgentStep
import com.quantma.lite.domain.model.ToolCall
import com.quantma.lite.sas.AgentSystemPrompt

/**
 * Builds prompts for agent mode with round history.
 * Uses ChatML format exclusively.
 */
object AgentPromptFormatter {

    /**
     * Build the agent prompt with round history (ChatML format).
     *
     * @param userMessage original user request
     * @param rounds completed agent rounds (response + tool result pairs)
     * @param workingDir current working directory for the agent
     * @param contextBudget maximum estimated tokens for the prompt
     * @param systemPrompt custom system prompt from AgentConfig (null = use built-in)
     * @param format kept for API compatibility, always uses ChatML
     */
    @Suppress("UNUSED_PARAMETER")
    fun formatPrompt(
        userMessage: String,
        rounds: List<AgentStep>,
        workingDir: String,
        contextBudget: Int = 2048,
        systemPrompt: String? = null,
        format: PromptFormatter.Format = PromptFormatter.Format.CHATML
    ): String {
        val effectivePrompt = systemPrompt?.let { it + "\n\nWorking directory: $workingDir" }
            ?: AgentSystemPrompt.buildSystemPrompt(workingDir)

        val roundsToInclude = selectRounds(rounds, 0, contextBudget)

        return formatChatML(effectivePrompt, userMessage, roundsToInclude)
    }

    // ── ChatML format ───────────────────────────────────────────────

    private fun formatChatML(
        systemPrompt: String,
        userMessage: String,
        rounds: List<AgentStep>
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(systemPrompt)
        sb.append("<|im_end|>\n")

        sb.append("<|im_start|>user\n")
        sb.append(userMessage)
        sb.append("<|im_end|>\n")

        for (round in rounds) {
            sb.append("<|im_start|>assistant\n")
            sb.append(round.response)
            sb.append("<|im_end|>\n")

            val resultText = round.toolResult?.output ?: ""
            sb.append("<|im_start|>user\n")
            sb.append("RESULT of ${toolLabel(round)}:\n")
            sb.append(resultText)
            sb.append("<|im_end|>\n")
        }

        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun toolLabel(round: AgentStep): String = when (round.toolCall) {
        is ToolCall.ReadFile -> "read_file ${round.toolCall.path}"
        is ToolCall.ListFiles -> "list_files ${round.toolCall.path}"
        is ToolCall.WriteFile -> "write_file ${round.toolCall.path}"
        is ToolCall.GitStatus -> "git_status ${round.toolCall.path}"
        is ToolCall.GitDiff -> "git_diff ${round.toolCall.path}"
        is ToolCall.GitBranch -> "git_branch ${round.toolCall.path}"
        is ToolCall.GitClone -> "git_clone ${round.toolCall.url} ${round.toolCall.path}"
        is ToolCall.GitPull -> "git_pull ${round.toolCall.path}"
        is ToolCall.GitPush -> "git_push ${round.toolCall.path}"
        is ToolCall.GitCommit -> "git_commit ${round.toolCall.path}"
        is ToolCall.GitCreateBranch -> "git_create_branch ${round.toolCall.path} ${round.toolCall.name}"
        is ToolCall.GitCheckout -> "git_checkout ${round.toolCall.path} ${round.toolCall.name}"
        is ToolCall.GitDeleteBranch -> "git_delete_branch ${round.toolCall.path} ${round.toolCall.name}"
        is ToolCall.GitMerge -> "git_merge ${round.toolCall.path} ${round.toolCall.branchName}"
        is ToolCall.GitStashSave -> "git_stash_save ${round.toolCall.path}"
        is ToolCall.GitStashPop -> "git_stash_pop ${round.toolCall.path}"
        is ToolCall.GitStashList -> "git_stash_list ${round.toolCall.path}"
        is ToolCall.RunCommand -> "run_command ${round.toolCall.command}"
        is ToolCall.SearchFiles -> "search_files ${round.toolCall.pattern} ${round.toolCall.path}"
        is ToolCall.DeleteFile -> "delete_file ${round.toolCall.path}"
        is ToolCall.Done -> "done"
        null -> "unknown"
    }

    /**
     * Select which rounds to include within the context budget.
     * Always includes the most recent rounds first.
     */
    private fun selectRounds(
        rounds: List<AgentStep>,
        headerTokens: Int,
        contextBudget: Int
    ): List<AgentStep> {
        if (rounds.isEmpty()) return emptyList()

        val budgetChars = (contextBudget * 3.5).toInt() - headerTokens
        if (budgetChars <= 0) return emptyList()

        var totalChars = 0
        val included = mutableListOf<AgentStep>()

        for (round in rounds.reversed()) {
            val roundChars = round.response.length + (round.toolResult?.output?.length ?: 0) + 80
            if (totalChars + roundChars > budgetChars && included.isNotEmpty()) {
                break
            }
            included.add(0, round)
            totalChars += roundChars
        }

        return included
    }
}
