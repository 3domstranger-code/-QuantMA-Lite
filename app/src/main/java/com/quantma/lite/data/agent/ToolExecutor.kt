package com.quantma.lite.data.agent

import com.quantma.lite.BuildConfig
import com.quantma.lite.data.local.preferences.GitCredentialsStore
import timber.log.Timber
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.domain.model.GitStatus
import com.quantma.lite.domain.model.ToolCall
import com.quantma.lite.domain.model.ToolResult
import com.quantma.lite.domain.repository.FileRepository
import com.quantma.lite.domain.repository.GitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes tool calls against existing repositories.
 * All paths are validated to be within [workingDir].
 * write_file/delete_file are NOT executed here — they return a "needs approval" result.
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val fileRepository: FileRepository,
    private val gitRepository: GitRepository,
    private val gitCredentialsStore: GitCredentialsStore,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_FILE_LINES = 100
        private const val HEAD_LINES = 40
        private const val TAIL_LINES = 20
        private const val MAX_LIST_ENTRIES = 30
        private const val MAX_OUTPUT_CHARS = 4000
        private const val SEARCH_MAX_MATCHES = 100
        private const val CMD_TIMEOUT_MS = 5000L

        // Phase 8 (v1.8.0) — allowed shell commands whitelist
        private val COMMAND_WHITELIST = setOf(
            "ls", "find", "grep", "cat", "head", "tail",
            "wc", "echo", "pwd", "du", "diff"
        )

        // File extensions to include in search_files
        private val SEARCHABLE_EXTENSIONS = setOf(
            "kt", "java", "py", "js", "ts", "xml", "md", "txt",
            "sh", "cpp", "c", "h", "gradle", "json", "yaml", "yml"
        )
    }

    private val ULTRA_STUB = "⚡ This feature is available in QuantMA Ultra. Visit quantma.app for details."

    /**
     * Returns a smart stub that previews the intent without executing it.
     * Shows the user what WOULD happen — demonstrates the potential, hits the wall.
     */
    private fun writeFileStub(call: ToolCall.WriteFile): ToolResult {
        val preview = call.content.take(300).let {
            if (call.content.length > 300) "$it\n… (${call.content.length - 300} more chars)" else it
        }
        return ToolResult(call, false,
            "📄 **Preview** — write_file would create/overwrite `${call.path}`:\n" +
            "```\n$preview\n```\n\n" +
            "⚡ File writes are disabled in QuantMA Lite. " +
            "The agent correctly determined the content — QuantMA Ultra would execute this write."
        )
    }

    private fun gitWriteStub(call: ToolCall, opName: String, detail: String = ""): ToolResult {
        return ToolResult(call, false,
            "🔒 **$opName** — operation preview${if (detail.isNotBlank()) ": $detail" else ""}.\n\n" +
            "⚡ Git write operations are disabled in QuantMA Lite. " +
            "QuantMA Ultra supports full git workflow: commit, push, branch, merge."
        )
    }

    private fun runCommandStub(call: ToolCall.RunCommand): ToolResult {
        return ToolResult(call, false,
            "💻 **run_command** — would execute: `${call.command}`\n\n" +
            "⚡ Shell execution is disabled in QuantMA Lite. " +
            "QuantMA Ultra can run arbitrary shell commands in a sandboxed environment."
        )
    }

    fun execute(toolCall: ToolCall, workingDir: String): ToolResult {
        // Ultra feature gate — advanced tool routing
        if (BuildConfig.DEBUG && false) {
            // Advanced routing via AgentDecisionEngine (QuantMA Ultra only)
            val strategy = com.quantma.lite.core.AgentDecisionEngine.evaluateToolChain(
                userQuery = "",
                availableTools = setOf()
            )
            timber.log.Timber.d("Ultra routing: ${strategy.toolChain}")
        }

        return when (toolCall) {
            // ── Read-only tools (Lite) ──
            is ToolCall.ReadFile -> executeReadFile(toolCall, workingDir)
            is ToolCall.ListFiles -> executeListFiles(toolCall, workingDir)
            is ToolCall.SearchFiles -> executeSearchFiles(toolCall, workingDir)
            is ToolCall.GitStatus -> executeGitStatus(toolCall)
            is ToolCall.GitDiff -> executeGitDiff(toolCall)
            is ToolCall.GitBranch -> executeGitBranch(toolCall)
            is ToolCall.GitStashList -> executeGitStashList(toolCall)
            is ToolCall.Done -> ToolResult(toolCall, true, "Agent finished.")

            // ── Write/mutating tools — smart preview stubs for Lite ──
            is ToolCall.WriteFile -> writeFileStub(toolCall)
            is ToolCall.DeleteFile -> ToolResult(toolCall, false,
                "🗑 **delete_file** — would delete `${toolCall.path}`.\n\n" +
                "⚡ File deletion is disabled in QuantMA Lite. QuantMA Ultra executes destructive operations with confirmation.")
            is ToolCall.GitClone -> gitWriteStub(toolCall, "git_clone", toolCall.url)
            is ToolCall.GitPull -> gitWriteStub(toolCall, "git_pull", toolCall.path)
            is ToolCall.GitPush -> gitWriteStub(toolCall, "git_push", toolCall.path)
            is ToolCall.GitCommit -> gitWriteStub(toolCall, "git_commit", "\"${toolCall.message.take(60)}\"")
            is ToolCall.GitCreateBranch -> gitWriteStub(toolCall, "git_create_branch", toolCall.name)
            is ToolCall.GitCheckout -> gitWriteStub(toolCall, "git_checkout", toolCall.name)
            is ToolCall.GitDeleteBranch -> gitWriteStub(toolCall, "git_delete_branch", toolCall.name)
            is ToolCall.GitMerge -> gitWriteStub(toolCall, "git_merge", toolCall.branchName)
            is ToolCall.GitStashSave -> gitWriteStub(toolCall, "git_stash_save")
            is ToolCall.GitStashPop -> gitWriteStub(toolCall, "git_stash_pop")
            is ToolCall.RunCommand -> runCommandStub(toolCall)
        }
    }

    private fun executeReadFile(call: ToolCall.ReadFile, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)

        if (!fileRepository.exists(call.path)) {
            return ToolResult(call, false, "Error: file not found: ${call.path}")
        }
        if (fileRepository.isDirectory(call.path)) {
            return ToolResult(call, false, "Error: path is a directory, use list_files instead: ${call.path}")
        }

        val content = fileRepository.readFile(call.path)
        val truncated = truncateFileContent(content)
        return ToolResult(call, true, truncated)
    }

    private fun executeListFiles(call: ToolCall.ListFiles, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)

        if (!fileRepository.exists(call.path)) {
            return ToolResult(call, false, "Error: directory not found: ${call.path}")
        }
        if (!fileRepository.isDirectory(call.path)) {
            return ToolResult(call, false, "Error: not a directory: ${call.path}")
        }

        val files = fileRepository.listDirectory(call.path)
        val sb = StringBuilder()
        val count = minOf(files.size, MAX_LIST_ENTRIES)
        for (i in 0 until count) {
            val f = files[i]
            val suffix = if (f.isDirectory) "/" else " (${formatSize(f.length())})"
            sb.appendLine("${f.name}$suffix")
        }
        if (files.size > MAX_LIST_ENTRIES) {
            sb.appendLine("... and ${files.size - MAX_LIST_ENTRIES} more entries")
        }
        return ToolResult(call, true, sb.toString().trimEnd())
    }

    private fun executeWriteFile(call: ToolCall.WriteFile, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)

        // Don't execute — return signal that approval is needed
        return ToolResult(call, false, "NEEDS_APPROVAL")
    }

    private fun executeGitStatus(call: ToolCall.GitStatus): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")

        val result = gitRepository.getStatus(root)
        return result.fold(
            onSuccess = { status -> ToolResult(call, true, formatGitStatus(status)) },
            onFailure = { e -> ToolResult(call, false, "Error: ${e.message}") }
        )
    }

    private fun executeGitDiff(call: ToolCall.GitDiff): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.getDiff(root).fold(
            onSuccess = { entries ->
                if (entries.isEmpty()) {
                    ToolResult(call, true, "No changes.")
                } else {
                    val text = entries.joinToString("\n---\n") { entry ->
                        "${entry.filePath} (${entry.changeType}):\n${entry.unified.take(2000)}"
                    }
                    ToolResult(call, true, text.take(MAX_OUTPUT_CHARS))
                }
            },
            onFailure = { ToolResult(call, false, "Error: ${it.message}") }
        )
    }

    private fun executeGitBranch(call: ToolCall.GitBranch): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.listBranches(root).fold(
            onSuccess = { branches ->
                val text = branches.joinToString("\n") { b ->
                    if (b.isCurrent) "* ${b.name}" else "  ${b.name}"
                }
                ToolResult(call, true, text.ifEmpty { "No branches." })
            },
            onFailure = { ToolResult(call, false, "Error: ${it.message}") }
        )
    }

    private fun executeGitClone(call: ToolCall.GitClone, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)
        val token = gitCredentialsStore.getToken()
        return gitRepository.clone(call.url, call.path, token).fold(
            onSuccess = { ToolResult(call, true, "Cloned ${call.url} to ${call.path}") },
            onFailure = { ToolResult(call, false, "Clone failed: ${it.message}") }
        )
    }

    private fun executeGitPull(call: ToolCall.GitPull): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        val token = gitCredentialsStore.getToken()
        return gitRepository.pull(root, token).fold(
            onSuccess = { ToolResult(call, true, "Pull result: $it") },
            onFailure = { ToolResult(call, false, "Pull failed: ${it.message}") }
        )
    }

    private fun executeGitPush(call: ToolCall.GitPush): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        val token = gitCredentialsStore.getToken()
        if (token.isBlank()) {
            return ToolResult(call, false, "Error: no access token configured. Set it in Settings → Git.")
        }
        return gitRepository.push(root, token).fold(
            onSuccess = { ToolResult(call, true, "Push successful.") },
            onFailure = { ToolResult(call, false, "Push failed: ${it.message}") }
        )
    }

    private fun executeGitCommit(call: ToolCall.GitCommit): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        if (call.message.isBlank()) {
            return ToolResult(call, false, "Error: commit message is empty.")
        }
        // Stage all + commit
        val addResult = gitRepository.addAll(root)
        if (addResult.isFailure) {
            return ToolResult(call, false, "Stage failed: ${addResult.exceptionOrNull()?.message}")
        }
        val authorName = runBlocking { settingsDataStore.gitAuthorName.first() }
        val authorEmail = runBlocking { settingsDataStore.gitAuthorEmail.first() }
        return gitRepository.commit(root, call.message, authorName, authorEmail).fold(
            onSuccess = { hash -> ToolResult(call, true, "Committed $hash: ${call.message}") },
            onFailure = { ToolResult(call, false, "Commit failed: ${it.message}") }
        )
    }

    // ---- Phase 3 (v1.2.0) — Branch / Merge / Stash ----

    private fun executeGitCreateBranch(call: ToolCall.GitCreateBranch): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.createBranch(root, call.name).fold(
            onSuccess = { ToolResult(call, true, "Branch '${call.name}' created.") },
            onFailure = { ToolResult(call, false, "Create branch failed: ${it.message}") }
        )
    }

    private fun executeGitCheckout(call: ToolCall.GitCheckout): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.checkoutBranch(root, call.name).fold(
            onSuccess = { ToolResult(call, true, "Switched to branch '${call.name}'.") },
            onFailure = { ToolResult(call, false, "Checkout failed: ${it.message}") }
        )
    }

    private fun executeGitDeleteBranch(call: ToolCall.GitDeleteBranch): ToolResult {
        // Destructive — requires approval
        return ToolResult(call, false, "NEEDS_APPROVAL")
    }

    private fun executeGitMerge(call: ToolCall.GitMerge): ToolResult {
        // Destructive — requires approval
        return ToolResult(call, false, "NEEDS_APPROVAL")
    }

    private fun executeGitStashSave(call: ToolCall.GitStashSave): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.stash(root, call.message).fold(
            onSuccess = { ToolResult(call, true, "Changes stashed.") },
            onFailure = { ToolResult(call, false, "Stash failed: ${it.message}") }
        )
    }

    private fun executeGitStashPop(call: ToolCall.GitStashPop): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.stashPop(root).fold(
            onSuccess = { ToolResult(call, true, "Stash popped.") },
            onFailure = { ToolResult(call, false, "Stash pop failed: ${it.message}") }
        )
    }

    private fun executeGitStashList(call: ToolCall.GitStashList): ToolResult {
        val root = gitRepository.findGitRoot(call.path)
            ?: return ToolResult(call, false, "Error: not a git repository: ${call.path}")
        return gitRepository.stashList(root).fold(
            onSuccess = { entries ->
                if (entries.isEmpty()) ToolResult(call, true, "No stash entries.")
                else {
                    val text = entries.joinToString("\n") { "stash@{${it.index}}: ${it.message}" }
                    ToolResult(call, true, text)
                }
            },
            onFailure = { ToolResult(call, false, "Stash list failed: ${it.message}") }
        )
    }

    /** Execute git_delete_branch after user approval. */
    fun executeGitDeleteBranchApproved(path: String, name: String): ToolResult {
        val call = ToolCall.GitDeleteBranch(path, name)
        val root = gitRepository.findGitRoot(path)
            ?: return ToolResult(call, false, "Error: not a git repository: $path")
        return gitRepository.deleteBranch(root, name).fold(
            onSuccess = { ToolResult(call, true, "Branch '$name' deleted.") },
            onFailure = { ToolResult(call, false, "Delete branch failed: ${it.message}") }
        )
    }

    /** Execute git_merge after user approval. */
    fun executeGitMergeApproved(path: String, branchName: String): ToolResult {
        val call = ToolCall.GitMerge(path, branchName)
        val root = gitRepository.findGitRoot(path)
            ?: return ToolResult(call, false, "Error: not a git repository: $path")
        return gitRepository.merge(root, branchName).fold(
            onSuccess = { mergeResult ->
                val status = if (mergeResult.success) "Merge successful" else "Merge conflicts"
                val details = buildString {
                    append("$status: ${mergeResult.mergeStatus}")
                    if (mergeResult.conflicts.isNotEmpty()) {
                        append("\nConflicts: ${mergeResult.conflicts.joinToString(", ")}")
                    }
                }
                ToolResult(call, mergeResult.success, details)
            },
            onFailure = { ToolResult(call, false, "Merge failed: ${it.message}") }
        )
    }

    // ---- Phase 8 (v1.8.0) ----

    private fun executeRunCommand(call: ToolCall.RunCommand, workingDir: String): ToolResult {
        val parts = call.command.trim().split("\\s+".toRegex())
        if (parts.isEmpty()) return ToolResult(call, false, "Error: empty command.")

        val executable = parts[0].substringAfterLast("/")
        if (executable !in COMMAND_WHITELIST) {
            return ToolResult(
                call, false,
                "Command '$executable' not allowed. Allowed commands: ${COMMAND_WHITELIST.sorted().joinToString(", ")}"
            )
        }

        return try {
            val process = ProcessBuilder(parts)
                .directory(File(workingDir))
                .redirectErrorStream(true)
                .start()

            val output = runBlocking {
                withTimeoutOrNull(CMD_TIMEOUT_MS) {
                    process.inputStream.bufferedReader().readText()
                } ?: run {
                    process.destroyForcibly()
                    "Error: command timed out after ${CMD_TIMEOUT_MS / 1000}s."
                }
            }
            process.waitFor()
            ToolResult(call, true, output.take(MAX_OUTPUT_CHARS))
        } catch (e: Exception) {
            ToolResult(call, false, "Error executing command: ${e.message}")
        }
    }

    private fun executeSearchFiles(call: ToolCall.SearchFiles, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)

        val dir = File(call.path)
        if (!dir.exists()) return ToolResult(call, false, "Error: path not found: ${call.path}")
        if (!dir.isDirectory) return ToolResult(call, false, "Error: not a directory: ${call.path}")

        val patternLower = call.pattern.lowercase()
        val sb = StringBuilder()
        var matchCount = 0

        dir.walkTopDown()
            .filter { file ->
                file.isFile &&
                !file.name.startsWith(".") &&
                !file.path.contains("/.") &&
                file.extension.lowercase() in SEARCHABLE_EXTENSIONS
            }
            .forEach { file ->
                if (matchCount >= SEARCH_MAX_MATCHES) return@forEach
                try {
                    file.bufferedReader().use { reader: BufferedReader ->
                        var lineNum = 0
                        var line = reader.readLine()
                        while (line != null && matchCount < SEARCH_MAX_MATCHES) {
                            lineNum++
                            if (line.lowercase().contains(patternLower)) {
                                sb.appendLine("${file.path}:$lineNum: $line")
                                matchCount++
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) { Timber.w(e, "Skipping unreadable file") }
            }

        val result = sb.toString().trimEnd()
        return if (result.isEmpty()) {
            ToolResult(call, true, "No matches found for '${call.pattern}' in ${call.path}")
        } else {
            val truncated = if (result.length > MAX_OUTPUT_CHARS)
                result.take(MAX_OUTPUT_CHARS) + "\n... (truncated)"
            else result
            val header = "Found $matchCount match(es) for '${call.pattern}':\n"
            ToolResult(call, true, header + truncated)
        }
    }

    private fun executeDeleteFile(call: ToolCall.DeleteFile, workingDir: String): ToolResult {
        val validation = validatePath(call.path, workingDir)
        if (validation != null) return ToolResult(call, false, validation)

        // Don't execute — return signal that approval is needed
        return ToolResult(call, false, "NEEDS_APPROVAL")
    }

    private fun validatePath(path: String, workingDir: String): String? {
        val canonical = try {
            File(path).canonicalPath
        } catch (e: Exception) {
            return "Error: invalid path: $path"
        }
        val workDirCanonical = File(workingDir).canonicalPath
        if (!canonical.startsWith(workDirCanonical)) {
            return "Error: path outside working directory. Must be within: $workingDir"
        }
        return null
    }

    private fun truncateFileContent(content: String): String {
        val lines = content.lines()
        if (lines.size <= MAX_FILE_LINES) {
            return if (content.length > MAX_OUTPUT_CHARS) {
                content.take(MAX_OUTPUT_CHARS) + "\n... (truncated, total ${content.length} chars)"
            } else {
                content
            }
        }

        val head = lines.take(HEAD_LINES)
        val tail = lines.takeLast(TAIL_LINES)
        val omitted = lines.size - HEAD_LINES - TAIL_LINES
        return (head + listOf("... ($omitted lines omitted) ...") + tail).joinToString("\n")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatGitStatus(status: GitStatus): String {
        if (status.isClean) return "Working tree clean."

        val sb = StringBuilder()
        if (status.added.isNotEmpty()) {
            sb.appendLine("Staged (new): ${status.added.joinToString(", ")}")
        }
        if (status.changed.isNotEmpty()) {
            sb.appendLine("Staged (modified): ${status.changed.joinToString(", ")}")
        }
        if (status.removed.isNotEmpty()) {
            sb.appendLine("Staged (deleted): ${status.removed.joinToString(", ")}")
        }
        if (status.modified.isNotEmpty()) {
            sb.appendLine("Modified: ${status.modified.joinToString(", ")}")
        }
        if (status.untracked.isNotEmpty()) {
            sb.appendLine("Untracked: ${status.untracked.joinToString(", ")}")
        }
        if (status.missing.isNotEmpty()) {
            sb.appendLine("Missing: ${status.missing.joinToString(", ")}")
        }
        if (status.conflicting.isNotEmpty()) {
            sb.appendLine("Conflicting: ${status.conflicting.joinToString(", ")}")
        }
        return sb.toString().trimEnd()
    }
}
