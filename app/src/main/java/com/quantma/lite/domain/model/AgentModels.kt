package com.quantma.lite.domain.model

/**
 * Represents a tool call parsed from LLM output.
 * The agent uses simple text-based ACTION: protocol.
 */
sealed class ToolCall {
    data class ReadFile(val path: String) : ToolCall()
    data class ListFiles(val path: String) : ToolCall()
    data class WriteFile(val path: String, val content: String) : ToolCall()
    data class GitStatus(val path: String) : ToolCall()
    data class GitDiff(val path: String) : ToolCall()
    data class GitBranch(val path: String) : ToolCall()
    // Phase 4 (v1.3.0)
    data class GitClone(val url: String, val path: String) : ToolCall()
    data class GitPull(val path: String) : ToolCall()
    data class GitPush(val path: String) : ToolCall()
    data class GitCommit(val path: String, val message: String) : ToolCall()
    // Phase 3 (v1.2.0) — Branch / Merge / Stash
    data class GitCreateBranch(val path: String, val name: String) : ToolCall()
    data class GitCheckout(val path: String, val name: String) : ToolCall()
    data class GitDeleteBranch(val path: String, val name: String) : ToolCall()
    data class GitMerge(val path: String, val branchName: String) : ToolCall()
    data class GitStashSave(val path: String, val message: String = "") : ToolCall()
    data class GitStashPop(val path: String) : ToolCall()
    data class GitStashList(val path: String) : ToolCall()
    // Phase 8 (v1.8.0)
    data class RunCommand(val command: String) : ToolCall()
    data class SearchFiles(val pattern: String, val path: String) : ToolCall()
    data class DeleteFile(val path: String) : ToolCall()
    data object Done : ToolCall()
}

/**
 * Result of executing a tool call.
 */
data class ToolResult(
    val toolCall: ToolCall,
    val success: Boolean,
    val output: String
)

/**
 * One round of the agent loop for UI display.
 */
data class AgentStep(
    val roundIndex: Int,
    val response: String,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val status: AgentStepStatus = AgentStepStatus.GENERATING
)

enum class AgentStepStatus {
    GENERATING,
    EXECUTING,
    WAITING_USER,
    COMPLETE
}

/**
 * Pending file write awaiting user confirmation.
 */
data class PendingWrite(
    val path: String,
    val newContent: String,
    val oldContent: String,
    val roundIndex: Int
)

/**
 * Pending file deletion awaiting user confirmation. (Phase 8 v1.8.0)
 */
data class PendingDelete(
    val path: String,
    val preview: String,   // first ~10 lines of file content
    val roundIndex: Int
)

/**
 * Pending git action (commit/push) awaiting user confirmation. (v2.6.0)
 */
data class PendingGitAction(
    val action: String,       // "commit" | "push"
    val description: String,  // human-readable description
    val preview: String,      // details: commit message or push target
    val toolCall: ToolCall
)
