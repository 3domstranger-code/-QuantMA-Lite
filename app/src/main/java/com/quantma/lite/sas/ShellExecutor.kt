package com.quantma.lite.sas

import timber.log.Timber
import java.io.File
import java.util.ArrayDeque

/**
 * Secure Agent Shell — Shell Executor.
 *
 * Executes [ToolCommand]s within a sandboxed environment defined by [SasConfig].
 * All file paths are validated through [PathSanitizer] before any IO operation.
 *
 * Key security properties:
 * - No path can escape the working directory
 * - Destructive operations (write, create, append, git add/commit) return [ToolResponse.NeedsApproval]
 * - File size and output length are bounded
 * - Only whitelisted file extensions are writable
 * - Rate limiting: at most [SasConfig.maxOpsPerMinute] tool calls per 60-second window
 *
 * All operations are synchronous — the caller is responsible for dispatching to a background thread.
 */
class ShellExecutor(
    private val config: SasConfig,
    private val gitOperations: GitOperations? = null,
    private val historyManager: FileHistoryManager? = null,
    private val codeValidator: CodeValidator? = CodeValidator(),
    private val hookManager: HookManager? = null
) {
    private val sanitizer = PathSanitizer(config)
    private val diffEngine = DiffEngine()
    private val codeQualityTools = CodeQualityTools(config, sanitizer, diffEngine)
    private val extendedGitTools: ExtendedGitTools? =
        gitOperations?.let { ExtendedGitTools(config, it) }

    // ── Rate limiter: sliding-window counter (not thread-safe by design — caller
    //    runs on a single coroutine dispatcher). Uses ArrayDeque for O(1) ops.
    private val opTimestamps = ArrayDeque<Long>(config.maxOpsPerMinute + 1)

    /**
     * Check whether the current call is within the allowed rate limit.
     * Purges timestamps older than 60 seconds before deciding.
     *
     * @return null if allowed, or a [ToolResponse.Error] if the limit is exceeded.
     */
    private fun checkRateLimit(): ToolResponse.Error? {
        val limit = config.maxOpsPerMinute
        if (limit <= 0) return null  // 0 = unlimited

        val now = System.currentTimeMillis()
        val windowStart = now - RATE_WINDOW_MS

        // Evict old timestamps
        while (opTimestamps.isNotEmpty() && opTimestamps.peekFirst()!! < windowStart) {
            opTimestamps.pollFirst()
        }

        return if (opTimestamps.size >= limit) {
            val retryAfterMs = opTimestamps.peekFirst()!! + RATE_WINDOW_MS - now
            ToolResponse.Error(
                "RATE_LIMIT_EXCEEDED",
                "Too many operations: $limit per minute limit reached. " +
                    "Retry after ${retryAfterMs / 1000}s."
            )
        } else {
            opTimestamps.addLast(now)
            null
        }
    }

    /**
     * Execute a tool command and return the result.
     *
     * @param command The parsed [ToolCommand] from LLM output
     * @return [ToolResponse] — Success, Error, or NeedsApproval
     */
    fun execute(command: ToolCommand): ToolResponse {
        // Rate limiting check (skip for DONE — it's a control signal, not a real tool call)
        if (command.tool != ToolType.DONE) {
            checkRateLimit()?.let { return it }
        }

        return try {
            // Pre-hooks (fire-and-forget, errors don't block main command)
            hookManager?.getPreHooks(command.tool, command)?.forEach { hook ->
                val hookCmd = hookManager.resolveHookPlaceholders(hook.hookAction, command)
                try { executeInternal(hookCmd) } catch (e: Exception) { Timber.w(e, "Hook execution failed") }
            }

            val result = executeInternal(command)

            // Post-hooks (only on success; pass output for OutputContains conditions)
            if (result is ToolResponse.Success) {
                hookManager?.getPostHooks(command.tool, command, result.output)?.forEach { hook ->
                    val hookCmd = hookManager.resolveHookPlaceholders(hook.hookAction, command)
                    try { executeInternal(hookCmd) } catch (e: Exception) { Timber.w(e, "Hook execution failed") }
                }
            }

            result
        } catch (e: Exception) {
            ToolResponse.Error("INTERNAL_ERROR", "Unexpected error: ${e.message}")
        }
    }

    /**
     * Internal dispatch — routes command to the appropriate handler.
     * Called by [execute] (with hooks) and directly by hook execution (without hooks).
     */
    private fun executeInternal(command: ToolCommand): ToolResponse {
        return when (command.tool) {
            ToolType.READ_FILE -> executeReadFile(command)
            ToolType.WRITE_FILE -> executeWriteFile(command)
            ToolType.CREATE_FILE -> executeCreateFile(command)
            ToolType.APPEND_FILE -> executeAppendFile(command)
            ToolType.LIST_DIR -> executeListDir(command)
            ToolType.SEARCH_GREP -> executeSearchGrep(command)
            ToolType.GET_DIFF -> executeGetDiff(command)
            ToolType.GIT_STATUS -> executeGitStatus(command)
            ToolType.GIT_ADD -> executeGitAdd(command)
            ToolType.GIT_COMMIT -> executeGitCommit(command)
            ToolType.GIT_LOG -> executeGitLog(command)
            ToolType.GIT_BRANCH -> executeGitBranch(command)

            // v3.1 — File operations
            ToolType.DELETE_FILE -> executeDeleteFile(command)

            // v3.1 — Code quality (delegated)
            ToolType.LINT_FILE -> codeQualityTools.executeLint(command)
            ToolType.FORMAT_FILE -> codeQualityTools.executeFormat(command)
            ToolType.COUNT_LINES -> codeQualityTools.executeCountLines(command)
            ToolType.FIND_TODO -> codeQualityTools.executeFindTodo(command)

            // v3.1 — Extended git (delegated)
            ToolType.GIT_STATUS_DETAILED -> extendedGitTools?.executeGitStatusDetailed(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_COMMIT_AUTO -> extendedGitTools?.executeGitCommitAuto(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_PUSH_SAFE -> extendedGitTools?.executeGitPushSafe(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_STASH -> extendedGitTools?.executeGitStash(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_CREATE_BRANCH -> extendedGitTools?.executeGitCreateBranch(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_CHECKOUT -> extendedGitTools?.executeGitCheckout(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_DELETE_BRANCH -> extendedGitTools?.executeGitDeleteBranch(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")
            ToolType.GIT_MERGE -> extendedGitTools?.executeGitMerge(command)
                ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git operations not configured")

            ToolType.DONE -> ToolResponse.Success("Agent signaled completion.")
        }
    }

    /**
     * Execute a previously approved command (after user confirmation).
     * This bypasses the NeedsApproval check and performs the actual IO.
     *
     * @param command The original [ToolCommand] that was approved
     * @return [ToolResponse.Success] or [ToolResponse.Error]
     */
    fun executeApproved(command: ToolCommand): ToolResponse {
        return try {
            when (command.tool) {
                ToolType.WRITE_FILE -> performWrite(command)
                ToolType.CREATE_FILE -> performCreate(command)
                ToolType.APPEND_FILE -> performAppend(command)
                ToolType.GIT_ADD -> performGitAdd(command)
                ToolType.GIT_COMMIT -> performGitCommit(command)
                // v3.1 approval tools
                ToolType.DELETE_FILE -> performDelete(command)
                ToolType.FORMAT_FILE -> {
                    val path = command.requireStringArg("path")
                    val file = sanitizer.resolve(path).getOrElse {
                        return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
                    }
                    codeQualityTools.performFormat(file, sanitizer.relativePath(file))
                }
                ToolType.GIT_COMMIT_AUTO -> extendedGitTools?.performGitCommitAuto(command)
                    ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git not configured")
                ToolType.GIT_PUSH_SAFE -> extendedGitTools?.performGitPushSafe(command)
                    ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git not configured")
                ToolType.GIT_DELETE_BRANCH -> extendedGitTools?.performGitDeleteBranch(command)
                    ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git not configured")
                ToolType.GIT_MERGE -> extendedGitTools?.performGitMerge(command)
                    ?: ToolResponse.Error("GIT_UNAVAILABLE", "Git not configured")
                else -> execute(command)
            }
        } catch (e: Exception) {
            ToolResponse.Error("INTERNAL_ERROR", "Unexpected error: ${e.message}")
        }
    }

    /**
     * Execute a batch of commands sequentially.
     * Stops on first NeedsApproval or Error.
     *
     * @param commands List of [ToolCommand]s to execute in order
     * @return List of (command, response) pairs for all executed commands
     */
    fun executeBatch(commands: List<ToolCommand>): List<Pair<ToolCommand, ToolResponse>> {
        val results = mutableListOf<Pair<ToolCommand, ToolResponse>>()
        for (command in commands) {
            val response = execute(command)
            results.add(command to response)
            // Stop on approval-needed or error — caller must handle
            if (response is ToolResponse.NeedsApproval || response is ToolResponse.Error) {
                break
            }
        }
        return results
    }

    /**
     * Undo the last change to a file.
     * Delegates to [FileHistoryManager] if available.
     *
     * @param relativePath Relative path from working directory
     * @return Success description or error
     */
    fun undoFileChange(relativePath: String): ToolResponse {
        val manager = historyManager
            ?: return ToolResponse.Error("HISTORY_UNAVAILABLE", "File history manager is not configured")
        return manager.undo(relativePath).fold(
            onSuccess = { ToolResponse.Success(it) },
            onFailure = { ToolResponse.Error("UNDO_FAILED", it.message ?: "Undo failed") }
        )
    }

    /**
     * Redo a previously undone file change.
     */
    fun redoFileChange(relativePath: String): ToolResponse {
        val manager = historyManager
            ?: return ToolResponse.Error("HISTORY_UNAVAILABLE", "File history manager is not configured")
        return manager.redo(relativePath).fold(
            onSuccess = { ToolResponse.Success(it) },
            onFailure = { ToolResponse.Error("REDO_FAILED", it.message ?: "Redo failed") }
        )
    }

    /** Access the diff engine for external use (e.g., SasBridge). */
    val diff: DiffEngine get() = diffEngine

    /** Access the path sanitizer for external use. */
    val paths: PathSanitizer get() = sanitizer

    // ═══════════════════════════════════════════════════════════════════════
    // File Operations
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeReadFile(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: path")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForRead(file).getOrElse {
            return ToolResponse.Error("READ_ERROR", it.message ?: "Cannot read file")
        }

        val content = file.readText()
        val lines = content.lines()
        val relPath = sanitizer.relativePath(file)

        val output = if (lines.size <= config.maxDisplayLines) {
            // File fits within display limit
            content
        } else {
            // Truncate: show head + ... + tail
            val headCount = (config.maxDisplayLines * 0.4).toInt()
            val tailCount = (config.maxDisplayLines * 0.2).toInt()
            val skipped = lines.size - headCount - tailCount

            buildString {
                lines.take(headCount).forEach { appendLine(it) }
                appendLine("... ($skipped lines omitted) ...")
                lines.takeLast(tailCount).forEach { appendLine(it) }
            }.trimEnd()
        }

        // Enforce max output chars
        val finalOutput = if (output.length > config.maxOutputChars) {
            output.take(config.maxOutputChars) + "\n... (output truncated at ${config.maxOutputChars} chars)"
        } else {
            output
        }

        return ToolResponse.Success(
            output = "[$relPath] (${lines.size} lines)\n$finalOutput",
            metadata = mapOf("path" to relPath, "lines" to lines.size, "size" to file.length())
        )
    }

    private fun executeWriteFile(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: path")
        val content = command.stringArg("content")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: content")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForWrite(file).getOrElse {
            return ToolResponse.Error("WRITE_ERROR", it.message ?: "Cannot write file")
        }

        val relPath = sanitizer.relativePath(file)

        // Generate diff preview
        val oldContent = if (file.exists()) file.readText() else ""
        val diffPreview = diffEngine.computeUnifiedDiff(oldContent, content, relPath)
        val stats = diffEngine.computeStats(oldContent, content)

        // Pre-validation hint for approval screen
        val validationHint = codeValidator?.quickCheck(content, relPath, oldContent)?.let { result ->
            if (result.errors.isNotEmpty()) {
                val issues = result.errors.joinToString("\n") { "  ${it.severity}: ${it.message}" }
                "\n\n── Validation ──\n$issues"
            } else null
        } ?: ""

        val description = if (file.exists()) {
            "Overwrite $relPath ($stats)"
        } else {
            "Create and write $relPath (+${content.lines().size} lines)"
        }

        return ToolResponse.NeedsApproval(
            description = description,
            command = command,
            preview = (diffPreview.ifEmpty { "[New file: ${content.lines().size} lines]" }) + validationHint
        )
    }

    private fun executeCreateFile(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: path")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForWrite(file).getOrElse {
            return ToolResponse.Error("WRITE_ERROR", it.message ?: "Cannot create file")
        }

        if (file.exists()) {
            return ToolResponse.Error("FILE_EXISTS", "File already exists: ${sanitizer.relativePath(file)}")
        }

        return ToolResponse.NeedsApproval(
            description = "Create empty file: ${sanitizer.relativePath(file)}",
            command = command
        )
    }

    private fun executeAppendFile(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: path")
        val content = command.stringArg("content")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: content")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForWrite(file).getOrElse {
            return ToolResponse.Error("WRITE_ERROR", it.message ?: "Cannot append to file")
        }

        if (!file.exists()) {
            return ToolResponse.Error("FILE_NOT_FOUND", "File not found: ${sanitizer.relativePath(file)}")
        }

        val relPath = sanitizer.relativePath(file)
        val appendLines = content.lines().size

        return ToolResponse.NeedsApproval(
            description = "Append $appendLines lines to $relPath",
            command = command,
            preview = "[Appending to end of file:]\n$content"
        )
    }

    // ─── Actual IO (post-approval) ──────────────────────────────────────

    private fun performWrite(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")
        var content = command.requireStringArg("content")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        val relPath = sanitizer.relativePath(file)
        val oldContent = if (file.exists()) file.readText() else ""

        // ── File size enforcement ─────────────────────────────────────────
        val contentBytes = content.toByteArray().size.toLong()
        if (contentBytes > config.maxFileSizeBytes) {
            return ToolResponse.Error(
                "FILE_TOO_LARGE",
                "Content size (${formatSize(contentBytes)}) exceeds maximum (${formatSize(config.maxFileSizeBytes)})"
            )
        }

        // ── Pre-write validation ──────────────────────────────────────────
        if (codeValidator != null) {
            val validation = codeValidator.validate(content, oldContent, relPath)

            // Apply auto-fixes (e.g. bracket closure, encoding fix)
            if (validation.autoFixed != null) {
                content = validation.autoFixed
            }

            // Block on critical errors — abort write entirely
            if (validation.hasCritical) {
                val criticals = validation.errors
                    .filter { it.severity == ValidationSeverity.CRITICAL }
                    .joinToString("; ") { it.message }
                return ToolResponse.Error(
                    "VALIDATION_BLOCKED",
                    "Write blocked by validation: $criticals"
                )
            }

            // Warnings are recorded in metadata but don't block
        }

        // ── Save snapshot before modification ─────────────────────────────
        historyManager?.saveSnapshot(file)

        file.parentFile?.mkdirs()
        file.writeText(content)

        // ── Post-write read-back verification ─────────────────────────────
        val written = file.readText()
        if (written != content) {
            // Rollback on verification failure
            historyManager?.undo(relPath)
            return ToolResponse.Error(
                "VERIFY_FAILED",
                "Post-write verification failed: file content doesn't match. Rolled back."
            )
        }

        val stats = diffEngine.computeStats(oldContent, content)

        // Collect warnings for metadata
        val warnings = codeValidator?.validate(content, oldContent, relPath)
            ?.errors?.filter { it.severity == ValidationSeverity.WARNING }
            ?.map { it.message }
            ?: emptyList()

        return ToolResponse.Success(
            output = buildString {
                append("File written: $relPath ($stats)")
                if (warnings.isNotEmpty()) {
                    append("\n⚠ Warnings: ${warnings.joinToString("; ")}")
                }
            },
            metadata = mapOf(
                "path" to relPath,
                "stats" to stats.toString(),
                "can_undo" to (historyManager != null),
                "warnings" to warnings
            )
        )
    }

    private fun performCreate(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        val relPath = sanitizer.relativePath(file)

        // Save snapshot before creation (will record "file didn't exist")
        historyManager?.saveSnapshot(file)

        file.parentFile?.mkdirs()
        file.createNewFile()

        return ToolResponse.Success(
            output = "File created: $relPath",
            metadata = mapOf("path" to relPath, "can_undo" to (historyManager != null))
        )
    }

    private fun performAppend(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")
        val content = command.requireStringArg("content")

        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        val relPath = sanitizer.relativePath(file)
        val oldContent = file.readText()
        val newContent = oldContent + content

        // ── Pre-append validation ─────────────────────────────────────────
        if (codeValidator != null) {
            val validation = codeValidator.quickCheck(newContent, relPath, oldContent)

            if (validation.hasCritical) {
                val criticals = validation.errors
                    .filter { it.severity == ValidationSeverity.CRITICAL }
                    .joinToString("; ") { it.message }
                return ToolResponse.Error(
                    "VALIDATION_BLOCKED",
                    "Append blocked by validation: $criticals"
                )
            }
        }

        // Save snapshot before append
        historyManager?.saveSnapshot(file)

        file.appendText(content)

        return ToolResponse.Success(
            output = "Appended ${content.lines().size} lines to $relPath",
            metadata = mapOf(
                "path" to relPath,
                "appended_lines" to content.lines().size,
                "can_undo" to (historyManager != null)
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Directory & Search Operations
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeListDir(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForList(dir).getOrElse {
            return ToolResponse.Error("LIST_ERROR", it.message ?: "Cannot list directory")
        }

        val files = dir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyArray<File>().toList()

        val entries = files.take(config.maxListEntries)
        val relPath = sanitizer.relativePath(dir)

        val output = buildString {
            appendLine("[$relPath] (${files.size} entries)")
            for (file in entries) {
                val prefix = if (file.isDirectory) "  [DIR]  " else "  [FILE] "
                val name = file.name + if (file.isDirectory) "/" else ""
                val size = if (file.isFile) " (${formatSize(file.length())})" else ""
                appendLine("$prefix$name$size")
            }
            if (files.size > config.maxListEntries) {
                appendLine("  ... and ${files.size - config.maxListEntries} more entries")
            }
        }.trimEnd()

        return ToolResponse.Success(
            output = output,
            metadata = mapOf("path" to relPath, "count" to files.size)
        )
    }

    private fun executeSearchGrep(command: ToolCommand): ToolResponse {
        val pattern = command.stringArg("pattern")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: pattern")
        val path = command.stringArg("path") ?: "."

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        sanitizer.validateForSearch(dir).getOrElse {
            return ToolResponse.Error("SEARCH_ERROR", it.message ?: "Cannot search directory")
        }

        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return ToolResponse.Error("INVALID_PATTERN", "Invalid regex pattern: ${e.message}")
        }

        val matches = mutableListOf<SearchMatch>()
        var filesSearched = 0

        dir.walkTopDown()
            .filter { it.isFile && sanitizer.isExtensionAllowed(it) }
            .filter { it.length() <= config.maxFileSizeBytes }
            .forEach { file ->
                if (matches.size >= config.maxSearchResults) return@forEach
                filesSearched++

                try {
                    file.useLines { lines ->
                        lines.forEachIndexed { lineIdx, line ->
                            if (matches.size < config.maxSearchResults && regex.containsMatchIn(line)) {
                                matches.add(
                                    SearchMatch(
                                        file = sanitizer.relativePath(file),
                                        line = lineIdx + 1,
                                        content = line.take(200) // Truncate long lines
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip files that can't be read (binary, permission issues)
                }
            }

        val output = buildString {
            appendLine("Search: \"$pattern\" in ${sanitizer.relativePath(dir)}")
            appendLine("Found ${matches.size} matches in $filesSearched files")
            appendLine()
            for (match in matches) {
                appendLine("  ${match.file}:${match.line}: ${match.content}")
            }
            if (matches.size >= config.maxSearchResults) {
                appendLine("  ... (results limited to ${config.maxSearchResults})")
            }
        }.trimEnd()

        return ToolResponse.Success(
            output = output,
            metadata = mapOf(
                "matches" to matches.size,
                "files_searched" to filesSearched,
                "pattern" to pattern
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Diff Operation
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeGetDiff(command: ToolCommand): ToolResponse {
        val oldContent = command.stringArg("old_content") ?: ""
        val newContent = command.stringArg("new_content")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: new_content")

        val fileName = command.stringArg("file_name") ?: "file"
        val diff = diffEngine.computeUnifiedDiff(oldContent, newContent, fileName)
        val stats = diffEngine.computeStats(oldContent, newContent)

        return if (diff.isEmpty()) {
            ToolResponse.Success(
                output = "No differences found.",
                metadata = mapOf("changes" to false)
            )
        } else {
            ToolResponse.Success(
                output = "$diff\n\nStats: $stats",
                metadata = mapOf(
                    "changes" to true,
                    "additions" to stats.additions,
                    "deletions" to stats.deletions
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Git Operations
    // ═══════════════════════════════════════════════════════════════════════

    private fun requireGit(): GitOperations {
        return gitOperations
            ?: throw UnsupportedOperationException("Git operations are not available")
    }

    private fun executeGitStatus(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            val git = requireGit()
            val repoPath = dir.canonicalPath
            if (!git.isGitRepo(repoPath)) {
                return ToolResponse.Error("NOT_GIT_REPO", "Not a git repository: ${sanitizer.relativePath(dir)}")
            }
            val status = git.getStatus(repoPath)
            ToolResponse.Success(
                output = status,
                metadata = mapOf("path" to sanitizer.relativePath(dir))
            )
        } catch (e: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNAVAILABLE", e.message ?: "Git not available")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Git status failed: ${e.message}")
        }
    }

    private fun executeGitAdd(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."
        val files = command.stringListArg("files")

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            requireGit()
            val repoPath = dir.canonicalPath
            val description = if (files.isEmpty()) {
                "Git add all changes in ${sanitizer.relativePath(dir)}"
            } else {
                "Git add ${files.size} file(s): ${files.joinToString(", ")}"
            }

            ToolResponse.NeedsApproval(
                description = description,
                command = command
            )
        } catch (e: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNAVAILABLE", e.message ?: "Git not available")
        }
    }

    private fun executeGitCommit(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."
        val message = command.stringArg("message")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: message")

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            requireGit()
            ToolResponse.NeedsApproval(
                description = "Git commit in ${sanitizer.relativePath(dir)}: \"$message\"",
                command = command
            )
        } catch (e: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNAVAILABLE", e.message ?: "Git not available")
        }
    }

    private fun executeGitLog(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."
        val maxCount = command.intArg("max_count", 10)

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            val git = requireGit()
            val repoPath = dir.canonicalPath
            if (!git.isGitRepo(repoPath)) {
                return ToolResponse.Error("NOT_GIT_REPO", "Not a git repository")
            }
            val log = git.getLog(repoPath, maxCount)
            ToolResponse.Success(
                output = log,
                metadata = mapOf("path" to sanitizer.relativePath(dir), "max_count" to maxCount)
            )
        } catch (e: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNAVAILABLE", e.message ?: "Git not available")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Git log failed: ${e.message}")
        }
    }

    private fun executeGitBranch(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            val git = requireGit()
            val repoPath = dir.canonicalPath
            if (!git.isGitRepo(repoPath)) {
                return ToolResponse.Error("NOT_GIT_REPO", "Not a git repository")
            }
            val branch = git.getCurrentBranch(repoPath)
            ToolResponse.Success(
                output = "Current branch: $branch",
                metadata = mapOf("branch" to branch, "path" to sanitizer.relativePath(dir))
            )
        } catch (e: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNAVAILABLE", e.message ?: "Git not available")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Git branch failed: ${e.message}")
        }
    }

    // ─── Git: Actual execution (post-approval) ─────────────────────────

    private fun performGitAdd(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."
        val files = command.stringListArg("files")

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            val git = requireGit()
            val repoPath = dir.canonicalPath
            val result = if (files.isEmpty()) {
                git.addAll(repoPath)
            } else {
                git.addFiles(repoPath, files)
            }
            ToolResponse.Success(
                output = result,
                metadata = mapOf("path" to sanitizer.relativePath(dir))
            )
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Git add failed: ${e.message}")
        }
    }

    private fun performGitCommit(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: "."
        val message = command.requireStringArg("message")

        val dir = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        return try {
            val git = requireGit()
            val repoPath = dir.canonicalPath
            val result = git.commit(repoPath, message)
            ToolResponse.Success(
                output = result,
                metadata = mapOf("path" to sanitizer.relativePath(dir), "message" to message)
            )
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Git commit failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DELETE_FILE (v3.1) — moves to .sas_trash/ instead of permanent delete
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeDeleteFile(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }
        sanitizer.validateForRead(file).getOrElse {
            return ToolResponse.Error("READ_ERROR", it.message ?: "Cannot access")
        }

        if (!file.exists()) {
            return ToolResponse.Error("FILE_NOT_FOUND", "File not found: ${sanitizer.relativePath(file)}")
        }

        val relPath = sanitizer.relativePath(file)
        val sizeStr = formatSize(file.length())

        return ToolResponse.NeedsApproval(
            description = "Delete file: $relPath ($sizeStr) → move to .sas_trash/",
            command = command,
            preview = "File will be moved to .sas_trash/ (recoverable)\nPath: $relPath\nSize: $sizeStr"
        )
    }

    private fun performDelete(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }

        if (!file.exists()) {
            return ToolResponse.Error("FILE_NOT_FOUND", "File not found: ${sanitizer.relativePath(file)}")
        }

        val relPath = sanitizer.relativePath(file)

        // Save snapshot before deletion
        historyManager?.saveSnapshot(file)

        // Move to trash
        val trashDir = File(config.workingDir, TRASH_DIR)
        trashDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val trashName = "${file.nameWithoutExtension}_${timestamp}.${file.extension}"
        val trashFile = File(trashDir, trashName)
        file.renameTo(trashFile)

        return ToolResponse.Success(
            output = "Deleted: $relPath (moved to $TRASH_DIR/$trashName)",
            metadata = mapOf(
                "path" to relPath,
                "trash_path" to "$TRASH_DIR/$trashName",
                "can_undo" to (historyManager != null)
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════════

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }

    companion object {
        const val TRASH_DIR = ".sas_trash"
        /** Sliding-window duration for rate limiting (milliseconds). */
        const val RATE_WINDOW_MS = 60_000L
    }
}
