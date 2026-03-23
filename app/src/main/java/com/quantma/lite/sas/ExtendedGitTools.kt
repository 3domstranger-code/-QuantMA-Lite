package com.quantma.lite.sas

import timber.log.Timber

/**
 * Secure Agent Shell — Extended Git Tools.
 *
 * Implements advanced git operations as a delegate for [ShellExecutor]:
 *
 * - **GIT_STATUS_DETAILED**: Enhanced status with modified/staged/untracked sections + ahead/behind
 * - **GIT_COMMIT_AUTO**: Auto-generate commit message from diff, returns [ToolResponse.NeedsApproval]
 * - **GIT_PUSH_SAFE**: Push with ahead/behind safety check, returns [ToolResponse.NeedsApproval]
 * - **GIT_STASH**: Save/pop/list stash operations
 *
 * All operations delegate to the [GitOperations] interface for testability.
 * The extended interface methods have default implementations that throw
 * [UnsupportedOperationException], so legacy implementations fail gracefully.
 */
class ExtendedGitTools(
    private val config: SasConfig,
    private val gitOperations: GitOperations
) {

    // ═════════════════════════════════════════════════════════════════════════
    // GIT_STATUS_DETAILED
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Enhanced git status with structured sections.
     * Falls back to basic status if the implementation doesn't support detailed status.
     */
    fun executeGitStatusDetailed(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }

        return try {
            val detailed = gitOperations.getDetailedStatus(config.workingDir)
            val branch = gitOperations.getCurrentBranch(config.workingDir)
            val aheadBehind = try {
                gitOperations.getAheadBehind(config.workingDir)
            } catch (_: UnsupportedOperationException) {
                null
            }

            val output = buildString {
                appendLine("Branch: $branch")
                if (aheadBehind != null) {
                    val (ahead, behind) = aheadBehind
                    if (ahead > 0 || behind > 0) {
                        appendLine("Ahead: $ahead | Behind: $behind")
                    }
                }
                appendLine()
                append(detailed)
            }

            ToolResponse.Success(
                output = output.trimEnd(),
                metadata = mapOf(
                    "branch" to branch,
                    "ahead" to (aheadBehind?.first ?: 0),
                    "behind" to (aheadBehind?.second ?: 0)
                )
            )
        } catch (_: UnsupportedOperationException) {
            // Fallback to basic status
            val status = gitOperations.getStatus(config.workingDir)
            val branch = gitOperations.getCurrentBranch(config.workingDir)
            ToolResponse.Success(
                output = "Branch: $branch\n\n$status\n\n(Detailed status not available — using basic)",
                metadata = mapOf("branch" to branch, "fallback" to true)
            )
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "git status detailed failed: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GIT_COMMIT_AUTO
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Auto-generate commit message and request approval.
     * Returns [ToolResponse.NeedsApproval] with suggested message.
     *
     * Args: optional `message` override — if provided, uses that instead of auto-generation
     */
    fun executeGitCommitAuto(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }

        val userMessage = command.stringArg("message")

        val commitMessage = if (userMessage != null) {
            userMessage
        } else {
            try {
                gitOperations.generateCommitMessage(config.workingDir)
            } catch (_: UnsupportedOperationException) {
                // Fallback: generate from status
                generateFallbackCommitMessage()
            } catch (e: Exception) {
                return ToolResponse.Error("GIT_ERROR", "Failed to generate commit message: ${e.message}")
            }
        }

        return ToolResponse.NeedsApproval(
            description = "Git commit with message: $commitMessage",
            command = command.copy(args = command.args + ("message" to commitMessage)),
            preview = "Commit message:\n$commitMessage"
        )
    }

    /**
     * Execute the commit after user approval.
     */
    fun performGitCommitAuto(command: ToolCommand): ToolResponse {
        val message = command.requireStringArg("message")

        return try {
            // Stage all changes first
            gitOperations.addAll(config.workingDir)
            val result = gitOperations.commit(config.workingDir, message)
            ToolResponse.Success(
                output = "Committed: $result",
                metadata = mapOf("message" to message)
            )
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Commit failed: ${e.message}")
        }
    }

    private fun generateFallbackCommitMessage(): String {
        val status = gitOperations.getStatus(config.workingDir)
        val lines = status.lines().filter { it.isNotBlank() }

        // Extract changed file names
        val files = lines.mapNotNull { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("modified:") -> trimmed.removePrefix("modified:").trim()
                trimmed.startsWith("new file:") -> trimmed.removePrefix("new file:").trim()
                trimmed.startsWith("deleted:") -> trimmed.removePrefix("deleted:").trim()
                else -> null
            }
        }

        return when {
            files.isEmpty() -> "Update project files"
            files.size == 1 -> "Update ${files.first()}"
            files.size <= 3 -> "Update ${files.joinToString(", ")}"
            else -> "Update ${files.take(2).joinToString(", ")} and ${files.size - 2} more files"
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GIT_PUSH_SAFE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Safe push with ahead/behind check.
     * Returns [ToolResponse.NeedsApproval] if safe, error if behind remote.
     *
     * Args: optional `remote` (default "origin"), optional `branch`
     */
    fun executeGitPushSafe(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }

        val remote = command.stringArg("remote") ?: "origin"
        val branch = command.stringArg("branch")
            ?: try { gitOperations.getCurrentBranch(config.workingDir) } catch (e: Exception) { Timber.w(e, "Failed to get current branch"); "main" }

        // Check ahead/behind
        val aheadBehind = try {
            gitOperations.getAheadBehind(config.workingDir)
        } catch (_: UnsupportedOperationException) {
            null // Can't check, proceed with caution
        } catch (e: Exception) {
            return ToolResponse.Error("GIT_ERROR", "Failed to check remote status: ${e.message}")
        }

        if (aheadBehind != null) {
            val (ahead, behind) = aheadBehind

            if (behind > 0) {
                return ToolResponse.Error(
                    "GIT_BEHIND",
                    "Local branch is $behind commit(s) behind $remote/$branch. Pull first."
                )
            }

            if (ahead == 0) {
                return ToolResponse.Success(
                    output = "Already up to date with $remote/$branch. Nothing to push.",
                    metadata = mapOf("remote" to remote, "branch" to branch, "ahead" to 0)
                )
            }

            return ToolResponse.NeedsApproval(
                description = "Push $ahead commit(s) to $remote/$branch",
                command = command.copy(args = command.args + ("remote" to remote) + ("branch" to branch)),
                preview = "Push $ahead commit(s) to $remote/$branch"
            )
        }

        // Can't determine ahead/behind — ask for approval anyway
        return ToolResponse.NeedsApproval(
            description = "Push to $remote/$branch (ahead/behind status unavailable)",
            command = command.copy(args = command.args + ("remote" to remote) + ("branch" to branch)),
            preview = "Push to $remote/$branch\n(Warning: could not determine ahead/behind status)"
        )
    }

    /**
     * Execute push after user approval.
     */
    fun performGitPushSafe(command: ToolCommand): ToolResponse {
        val remote = command.stringArg("remote") ?: "origin"
        val branch = command.stringArg("branch")

        return try {
            val result = gitOperations.push(config.workingDir, remote, branch)
            ToolResponse.Success(
                output = "Pushed to $remote/$branch: $result",
                metadata = mapOf("remote" to remote, "branch" to (branch ?: ""))
            )
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Push failed: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GIT_STASH
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Stash operations: save, pop, list.
     *
     * Args: `operation` — "save", "pop", or "list"
     *        `message` — optional stash message (for save)
     */
    fun executeGitStash(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }

        val operation = command.stringArg("operation")?.lowercase() ?: "list"

        return try {
            when (operation) {
                "save" -> {
                    val message = command.stringArg("message")
                    val result = gitOperations.stashSave(config.workingDir, message)
                    ToolResponse.Success(
                        output = "Stash saved: $result",
                        metadata = mapOf("operation" to "save")
                    )
                }
                "pop" -> {
                    val result = gitOperations.stashPop(config.workingDir)
                    ToolResponse.Success(
                        output = "Stash popped: $result",
                        metadata = mapOf("operation" to "pop")
                    )
                }
                "list" -> {
                    val result = gitOperations.stashList(config.workingDir)
                    ToolResponse.Success(
                        output = if (result.isBlank()) "Stash is empty" else result,
                        metadata = mapOf("operation" to "list")
                    )
                }
                else -> ToolResponse.Error(
                    "INVALID_ARG",
                    "Unknown stash operation: $operation. Use: save, pop, list"
                )
            }
        } catch (_: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNSUPPORTED", "Stash operations not supported by this git implementation")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Stash $operation failed: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GIT_CREATE_BRANCH / GIT_CHECKOUT / GIT_DELETE_BRANCH / GIT_MERGE
    // ═════════════════════════════════════════════════════════════════════════

    fun executeGitCreateBranch(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }
        val name = command.stringArg("name")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: name")
        return try {
            val result = gitOperations.createBranch(config.workingDir, name)
            ToolResponse.Success(output = result)
        } catch (_: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNSUPPORTED", "createBranch not supported")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Create branch failed: ${e.message}")
        }
    }

    fun executeGitCheckout(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }
        val name = command.stringArg("name")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: name")
        return try {
            val result = gitOperations.checkoutBranch(config.workingDir, name)
            ToolResponse.Success(output = result)
        } catch (_: UnsupportedOperationException) {
            ToolResponse.Error("GIT_UNSUPPORTED", "checkoutBranch not supported")
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Checkout failed: ${e.message}")
        }
    }

    fun executeGitDeleteBranch(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }
        val name = command.stringArg("name")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: name")
        return ToolResponse.NeedsApproval(
            description = "Delete branch '$name'",
            command = command,
            preview = "Branch '$name' will be permanently deleted"
        )
    }

    fun performGitDeleteBranch(command: ToolCommand): ToolResponse {
        val name = command.requireStringArg("name")
        return try {
            val result = gitOperations.deleteBranch(config.workingDir, name)
            ToolResponse.Success(output = result)
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Delete branch failed: ${e.message}")
        }
    }

    fun executeGitMerge(command: ToolCommand): ToolResponse {
        requireGitRepo()?.let { return it }
        val branch = command.stringArg("branch")
            ?: return ToolResponse.Error("MISSING_ARG", "Missing required argument: branch")
        return ToolResponse.NeedsApproval(
            description = "Merge branch '$branch' into current",
            command = command,
            preview = "Branch '$branch' will be merged into the current branch"
        )
    }

    fun performGitMerge(command: ToolCommand): ToolResponse {
        val branch = command.requireStringArg("branch")
        return try {
            val result = gitOperations.mergeBranch(config.workingDir, branch)
            ToolResponse.Success(output = result)
        } catch (e: Exception) {
            ToolResponse.Error("GIT_ERROR", "Merge failed: ${e.message}")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun requireGitRepo(): ToolResponse.Error? {
        return if (!gitOperations.isGitRepo(config.workingDir)) {
            ToolResponse.Error("NOT_GIT_REPO", "Not a git repository: ${config.workingDir}")
        } else null
    }
}
